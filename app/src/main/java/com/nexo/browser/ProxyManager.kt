package com.nexo.browser

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

data class ParsedProxyEntry(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val originalLine: String
) {
    val dedupKey: String get() = "${host.lowercase()}:$port:$username"
}

data class InvalidProxyLine(
    val lineNumber: Int,
    val content: String,
    val reason: String
)

data class BulkProxyParseResult(
    val validEntries: List<ParsedProxyEntry>,
    val duplicateLines: List<String>,
    val invalidLines: List<InvalidProxyLine>,
    val truncatedCount: Int
)

data class BulkImportOutcome(
    val importedIds: List<Long>,
    val importedCount: Int,
    val duplicateCount: Int,
    val invalidCount: Int,
    val assignedCount: Int
)

data class ActiveProxyTunnel(
    val profileId: Long,
    val proxyId: Long,
    val localPort: Int,
    val remoteHost: String,
    val remotePort: Int,
    val publicIp: String?,
    val isConnected: Boolean,
    val lastError: String? = null
)

class ProxyManager(
    private val proxyDao: ProxyDao,
    private val profileDao: ProfileDao,
    private val securityManager: SecurityManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeBridges = ConcurrentHashMap<Long, LocalSocks5Bridge>()
    private val _activeTunnels = MutableStateFlow<Map<Long, ActiveProxyTunnel>>(emptyMap())

    val allProxiesFlow: Flow<List<ProxyEntity>> = proxyDao.observeAllProxies()
    val activeTunnelsFlow: StateFlow<Map<Long, ActiveProxyTunnel>> = _activeTunnels.asStateFlow()

    suspend fun addProxy(
        name: String,
        host: String,
        port: Int,
        username: String,
        password: String
    ): Long = withContext(Dispatchers.IO) {
        val cleanHost = host.trim()
        require(isValidHost(cleanHost)) { "Invalid proxy host or IP address" }
        require(port in 1..65535) { "Port must be between 1 and 65535" }

        val encrypted = securityManager.encrypt(password)
        val displayName = name.trim().ifBlank { "$cleanHost:$port" }
        proxyDao.insertProxy(
            ProxyEntity(
                name = displayName,
                type = "SOCKS5",
                host = cleanHost,
                port = port,
                username = username.trim(),
                encryptedPassword = encrypted,
                status = ProxyStatus.UNTESTED
            )
        )
    }

    suspend fun updateProxy(
        proxyId: Long,
        name: String,
        host: String,
        port: Int,
        username: String,
        newPasswordOrNull: String?
    ) = withContext(Dispatchers.IO) {
        val existing = proxyDao.getProxyById(proxyId) ?: return@withContext
        val cleanHost = host.trim()
        require(isValidHost(cleanHost)) { "Invalid proxy host or IP address" }
        require(port in 1..65535) { "Port must be between 1 and 65535" }

        val encrypted = if (newPasswordOrNull != null) {
            securityManager.encrypt(newPasswordOrNull)
        } else {
            existing.encryptedPassword
        }
        proxyDao.updateProxy(
            existing.copy(
                name = name.trim().ifBlank { "$cleanHost:$port" },
                host = cleanHost,
                port = port,
                username = username.trim(),
                encryptedPassword = encrypted,
                status = ProxyStatus.UNTESTED,
                lastError = null
            )
        )
    }

    suspend fun deleteProxy(proxyId: Long) = withContext(Dispatchers.IO) {
        // Disconnect any active profiles using this proxy first
        _activeTunnels.value.values
            .filter { it.proxyId == proxyId }
            .forEach { disconnectProfileProxy(it.profileId) }
        proxyDao.deleteProxyById(proxyId)
    }

    fun decryptPassword(proxy: ProxyEntity): String {
        return runCatching { securityManager.decrypt(proxy.encryptedPassword) }.getOrDefault("")
    }

    fun toSocks5Config(proxy: ProxyEntity, timeoutMs: Int = 12_000): Socks5Config {
        return Socks5Config(
            host = proxy.host,
            port = proxy.port,
            username = proxy.username,
            password = decryptPassword(proxy),
            connectTimeoutMs = timeoutMs,
            readTimeoutMs = timeoutMs + 3_000
        )
    }

    suspend fun testProxy(proxyId: Long, timeoutMs: Int = 12_000): ProxyCheckResult =
        withContext(Dispatchers.IO) {
            val proxy = proxyDao.getProxyById(proxyId)
                ?: return@withContext ProxyCheckResult.Failure("Proxy not found")

            proxyDao.updateCheckResult(
                proxyId = proxyId,
                status = ProxyStatus.TESTING,
                publicIp = proxy.publicIp,
                countryCode = proxy.countryCode,
                latencyMs = proxy.latencyMs,
                lastError = null,
                checkedAt = System.currentTimeMillis()
            )

            val config = toSocks5Config(proxy, timeoutMs)
            val result = Socks5Client.checkPublicIp(config)
            val now = System.currentTimeMillis()

            when (result) {
                is ProxyCheckResult.Success -> {
                    val isCurrentlyConnected = _activeTunnels.value.values.any {
                        it.proxyId == proxyId && it.isConnected
                    }
                    proxyDao.updateCheckResult(
                        proxyId = proxyId,
                        status = if (isCurrentlyConnected) ProxyStatus.CONNECTED else ProxyStatus.WORKING,
                        publicIp = result.publicIp,
                        countryCode = result.countryCode,
                        latencyMs = result.latencyMs,
                        lastError = null,
                        checkedAt = now
                    )
                    profileDao.updatePublicIpForProxy(proxyId, result.publicIp, result.latencyMs)
                }
                is ProxyCheckResult.Failure -> {
                    proxyDao.updateCheckResult(
                        proxyId = proxyId,
                        status = ProxyStatus.FAILED,
                        publicIp = null,
                        countryCode = null,
                        latencyMs = null,
                        lastError = result.reason,
                        checkedAt = now
                    )
                    profileDao.updatePublicIpForProxy(proxyId, null, null)
                }
            }
            result
        }

    suspend fun testProxies(proxyIds: List<Long>, timeoutMs: Int = 12_000) = coroutineScope {
        val semaphore = Semaphore(6)
        proxyIds.map { id ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    testProxy(id, timeoutMs)
                }
            }
        }.awaitAll()
    }

    suspend fun connectProfileProxy(
        profileId: Long,
        proxyId: Long,
        timeoutMs: Int = 12_000,
        verifyConnection: Boolean = true
    ): ActiveProxyTunnel = withContext(Dispatchers.IO) {
        val proxy = proxyDao.getProxyById(proxyId)
            ?: throw IllegalStateException("Assigned proxy #$proxyId no longer exists")

        disconnectProfileProxy(profileId)

        val config = toSocks5Config(proxy, timeoutMs)
        var verifiedIp = proxy.publicIp

        if (verifyConnection) {
            when (val check = testProxy(proxyId, timeoutMs)) {
                is ProxyCheckResult.Success -> {
                    verifiedIp = check.publicIp
                }
                is ProxyCheckResult.Failure -> {
                    profileDao.updateStatus(profileId, ProfileStatus.PROXY_ERROR, System.currentTimeMillis())
                    val failedTunnel = ActiveProxyTunnel(
                        profileId = profileId,
                        proxyId = proxyId,
                        localPort = -1,
                        remoteHost = proxy.host,
                        remotePort = proxy.port,
                        publicIp = null,
                        isConnected = false,
                        lastError = check.reason
                    )
                    _activeTunnels.update { it + (profileId to failedTunnel) }
                    return@withContext failedTunnel
                }
            }
        }

        val bridge = LocalSocks5Bridge(config) { errorMsg ->
            _activeTunnels.update { current ->
                val existing = current[profileId] ?: return@update current
                current + (profileId to existing.copy(lastError = errorMsg))
            }
        }
        val localPort = bridge.start()
        activeBridges[profileId] = bridge

        proxyDao.updateCheckResult(
            proxyId = proxyId,
            status = ProxyStatus.CONNECTED,
            publicIp = verifiedIp,
            countryCode = proxy.countryCode,
            latencyMs = proxy.latencyMs,
            lastError = null,
            checkedAt = System.currentTimeMillis()
        )
        profileDao.updateStatus(profileId, ProfileStatus.ACTIVE, System.currentTimeMillis())

        val tunnel = ActiveProxyTunnel(
            profileId = profileId,
            proxyId = proxyId,
            localPort = localPort,
            remoteHost = proxy.host,
            remotePort = proxy.port,
            publicIp = verifiedIp,
            isConnected = true,
            lastError = null
        )
        _activeTunnels.update { it + (profileId to tunnel) }
        tunnel
    }

    suspend fun disconnectProfileProxy(profileId: Long) = withContext(Dispatchers.IO) {
        activeBridges.remove(profileId)?.stop()
        val removed = _activeTunnels.value[profileId]
        _activeTunnels.update { it - profileId }

        if (removed != null) {
            val stillUsed = _activeTunnels.value.values.any { it.proxyId == removed.proxyId && it.isConnected }
            if (!stillUsed) {
                val proxy = proxyDao.getProxyById(removed.proxyId)
                if (proxy != null && proxy.status == ProxyStatus.CONNECTED) {
                    proxyDao.updateProxy(proxy.copy(status = ProxyStatus.WORKING))
                }
            }
        }
        val profile = profileDao.getProfileById(profileId)
        if (profile != null && profile.status != ProfileStatus.IDLE) {
            profileDao.updateStatus(profileId, ProfileStatus.IDLE, profile.lastOpenedAt)
        }
    }

    suspend fun parseBulkProxies(
        rawText: String,
        maxImport: Int = MAX_BULK_IMPORT
    ): BulkProxyParseResult = withContext(Dispatchers.Default) {
        val existingKeys = proxyDao.getAllProxies()
            .map { "${it.host.lowercase()}:${it.port}:${it.username}" }
            .toMutableSet()

        val valid = mutableListOf<ParsedProxyEntry>()
        val duplicates = mutableListOf<String>()
        val invalid = mutableListOf<InvalidProxyLine>()
        var truncated = 0

        rawText.lineSequence().forEachIndexed { idx, rawLine ->
            val lineNum = idx + 1
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachIndexed

            when (val parsed = parseProxyLine(trimmed)) {
                is ParseLineResult.Error -> {
                    invalid += InvalidProxyLine(lineNum, trimmed, parsed.reason)
                }
                is ParseLineResult.Ok -> {
                    if (!existingKeys.add(parsed.entry.dedupKey)) {
                        duplicates += trimmed
                    } else if (valid.size >= maxImport) {
                        truncated++
                    } else {
                        valid += parsed.entry
                    }
                }
            }
        }

        BulkProxyParseResult(
            validEntries = valid,
            duplicateLines = duplicates,
            invalidLines = invalid,
            truncatedCount = truncated
        )
    }

    suspend fun importParsedProxies(
        entries: List<ParsedProxyEntry>,
        autoAssignToProfiles: Boolean,
        autoTestAfterImport: Boolean,
        timeoutMs: Int = 12_000
    ): BulkImportOutcome = withContext(Dispatchers.IO) {
        if (entries.isEmpty()) {
            return@withContext BulkImportOutcome(emptyList(), 0, 0, 0, 0)
        }

        val entities = entries.mapIndexed { index, item ->
            ProxyEntity(
                name = "SOCKS5-${index + 1} (${item.host})",
                type = "SOCKS5",
                host = item.host,
                port = item.port,
                username = item.username,
                encryptedPassword = securityManager.encrypt(item.password),
                status = ProxyStatus.UNTESTED
            )
        }

        val insertedIds = proxyDao.insertProxies(entities)
        var assignedCount = 0

        if (autoAssignToProfiles) {
            assignedCount = assignProxiesToProfiles(insertedIds)
        }

        if (autoTestAfterImport) {
            scope.launch {
                testProxies(insertedIds, timeoutMs)
            }
        }

        BulkImportOutcome(
            importedIds = insertedIds,
            importedCount = insertedIds.size,
            duplicateCount = 0,
            invalidCount = 0,
            assignedCount = assignedCount
        )
    }

    suspend fun assignProxiesToProfiles(proxyIds: List<Long>? = null): Int = withContext(Dispatchers.IO) {
        val availableProxies = if (proxyIds != null) {
            proxyIds.mapNotNull { proxyDao.getProxyById(it) }
        } else {
            proxyDao.getAllProxies().sortedByDescending { it.status == ProxyStatus.WORKING }
        }
        if (availableProxies.isEmpty()) return@withContext 0

        val profiles = profileDao.getAllProfiles()
        val unassignedProfiles = profiles.filter { it.proxyId == null }
        val targets = if (unassignedProfiles.isNotEmpty()) unassignedProfiles else profiles

        var assigned = 0
        targets.forEachIndexed { index, profile ->
            if (index < availableProxies.size) {
                profileDao.assignProxy(profile.id, availableProxies[index].id)
                assigned++
            }
        }
        assigned
    }

    sealed class ParseLineResult {
        data class Ok(val entry: ParsedProxyEntry) : ParseLineResult()
        data class Error(val reason: String) : ParseLineResult()
    }

    companion object {
        const val MAX_BULK_IMPORT = 100

        /**
         * Parses:
         * Format 1: socks5://username:password@ip:port (also socks5://ip:port)
         * Format 2: ip:port:username:password (also [ipv6]:port:username:password or ip:port)
         */
        fun parseProxyLine(rawLine: String): ParseLineResult {
            val line = rawLine.trim()
            if (line.isEmpty()) return ParseLineResult.Error("Empty line")

            if (line.contains("://")) {
                if (!line.startsWith("socks5://", ignoreCase = true)) {
                    return ParseLineResult.Error("Only socks5:// scheme is supported")
                }
                val body = line.substringAfter("://").substringBefore("/").trim()
                val atIdx = body.lastIndexOf('@')
                val userInfo = if (atIdx >= 0) body.substring(0, atIdx) else ""
                val hostPortPart = if (atIdx >= 0) body.substring(atIdx + 1) else body

                val (host, port) = parseHostAndPort(hostPortPart)
                    ?: return ParseLineResult.Error("Invalid host:port in URI")

                if (!isValidHost(host)) {
                    return ParseLineResult.Error("Invalid host '$host'")
                }
                if (port !in 1..65535) {
                    return ParseLineResult.Error("Port $port out of range (1-65535)")
                }

                val username: String
                val password: String
                if (userInfo.isNotEmpty()) {
                    val colonIdx = userInfo.indexOf(':')
                    if (colonIdx < 0) {
                        username = decodeUriComponent(userInfo)
                        password = ""
                    } else {
                        username = decodeUriComponent(userInfo.substring(0, colonIdx))
                        password = decodeUriComponent(userInfo.substring(colonIdx + 1))
                    }
                } else {
                    username = ""
                    password = ""
                }

                return ParseLineResult.Ok(
                    ParsedProxyEntry(host, port, username, password, rawLine)
                )
            }

            // Format 2: ip:port:username:password (or [ipv6]:port:username:password)
            if (line.startsWith("[")) {
                val closeIdx = line.indexOf(']')
                if (closeIdx <= 1) return ParseLineResult.Error("Malformed IPv6 bracket")
                val host = line.substring(1, closeIdx)
                val remainder = line.substring(closeIdx + 1).removePrefix(":")
                val segs = remainder.split(":", limit = 3)
                val port = segs.getOrNull(0)?.toIntOrNull()
                    ?: return ParseLineResult.Error("Missing or invalid port")
                if (!isValidHost(host) || port !in 1..65535) {
                    return ParseLineResult.Error("Invalid host or port")
                }
                val user = segs.getOrNull(1).orEmpty()
                val pass = segs.getOrNull(2).orEmpty()
                return ParseLineResult.Ok(ParsedProxyEntry(host, port, user, pass, rawLine))
            }

            val parts = line.split(":", limit = 4)
            if (parts.size < 2) {
                return ParseLineResult.Error("Expected socks5://user:pass@ip:port or ip:port:user:pass")
            }
            val host = parts[0].trim()
            val port = parts[1].trim().toIntOrNull()
                ?: return ParseLineResult.Error("Invalid port '${parts[1]}'")

            if (!isValidHost(host)) {
                return ParseLineResult.Error("Invalid host '$host'")
            }
            if (port !in 1..65535) {
                return ParseLineResult.Error("Port $port out of range (1-65535)")
            }

            val username = parts.getOrNull(2)?.trim().orEmpty()
            val password = parts.getOrNull(3)?.trim().orEmpty()

            return ParseLineResult.Ok(ParsedProxyEntry(host, port, username, password, rawLine))
        }

        private fun parseHostAndPort(hostPort: String): Pair<String, Int>? {
            if (hostPort.startsWith("[")) {
                val close = hostPort.indexOf(']')
                if (close <= 1) return null
                val host = hostPort.substring(1, close)
                val portStr = hostPort.substring(close + 1).removePrefix(":")
                val port = portStr.toIntOrNull() ?: return null
                return host to port
            }
            val colonIdx = hostPort.lastIndexOf(':')
            if (colonIdx <= 0) return null
            val host = hostPort.substring(0, colonIdx).trim()
            val port = hostPort.substring(colonIdx + 1).trim().toIntOrNull() ?: return null
            return host to port
        }

        private fun decodeUriComponent(value: String): String {
            return runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }
                .getOrDefault(value)
        }

        fun isValidHost(host: String): Boolean {
            if (host.isBlank() || host.length > 253 || host.contains(" ")) return false
            val ipv4 = Regex("""^((25[0-5]|2[0-4]\d|[01]?\d\d?)\.){3}(25[0-5]|2[0-4]\d|[01]?\d\d?)$""")
            if (ipv4.matches(host)) return true
            if (host.contains(":")) {
                // Basic IPv6 literal check
                return host.matches(Regex("""^[0-9a-fA-F:]+$"""))
            }
            val domain = Regex("""^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(\.[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$""")
            return domain.matches(host)
        }
    }
}
