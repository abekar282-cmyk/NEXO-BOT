package com.nexo.browser.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.browser.ActiveProxyTunnel
import com.nexo.browser.AppSettings
import com.nexo.browser.BulkImportOutcome
import com.nexo.browser.BulkProxyParseResult
import com.nexo.browser.EngineCapabilities
import com.nexo.browser.BrowserEngine
import com.nexo.browser.NexoApplication
import com.nexo.browser.ProfileStatus
import com.nexo.browser.ProfileWithProxy
import com.nexo.browser.ProxyCheckResult
import com.nexo.browser.ProxyEntity
import com.nexo.browser.ProxyStatus
import com.nexo.browser.SearchEngine
import com.nexo.browser.UserAgentPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ProfileSortOrder(val label: String) {
    NUMBER_ASC("# Number"),
    NAME_ASC("Name A-Z"),
    STATUS("Active First"),
    RECENTLY_USED("Recently Used")
}

data class DashboardUiState(
    val profiles: List<ProfileWithProxy> = emptyList(),
    val filteredProfiles: List<ProfileWithProxy> = emptyList(),
    val proxies: List<ProxyEntity> = emptyList(),
    val activeTunnels: Map<Long, ActiveProxyTunnel> = emptyMap(),
    val searchQuery: String = "",
    val sortOrder: ProfileSortOrder = ProfileSortOrder.NUMBER_ASC,
    val totalProfilesCount: Int = 0,
    val activeProfilesCount: Int = 0,
    val workingProxiesCount: Int = 0,
    val statusBanner: String? = null
)

class NexoViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as NexoApplication
    private val profileManager = app.profileManager
    private val proxyManager = app.proxyManager
    private val settingsStore = app.settingsStore

    private val _searchQuery = MutableStateFlow("")
    private val _sortOrder = MutableStateFlow(ProfileSortOrder.NUMBER_ASC)
    private val _statusBanner = MutableStateFlow<String?>(null)
    private val _bulkParseResult = MutableStateFlow<BulkProxyParseResult?>(null)
    private val _isBulkImporting = MutableStateFlow(false)
    private val _bulkImportOutcome = MutableStateFlow<BulkImportOutcome?>(null)

    val bulkParseResult: StateFlow<BulkProxyParseResult?> = _bulkParseResult.asStateFlow()
    val isBulkImporting: StateFlow<Boolean> = _isBulkImporting.asStateFlow()
    val bulkImportOutcome: StateFlow<BulkImportOutcome?> = _bulkImportOutcome.asStateFlow()

    val settings: StateFlow<AppSettings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val engineCapabilities: EngineCapabilities = BrowserEngine.inspectCapabilities(application)

    val dashboardState: StateFlow<DashboardUiState> = combine(
        profileManager.profilesFlow,
        proxyManager.allProxiesFlow,
        proxyManager.activeTunnelsFlow,
        _searchQuery,
        _sortOrder
    ) { profiles, proxies, tunnels, query, sort ->
        val trimmed = query.trim().lowercase()
        val filtered = profiles.filter { item ->
            if (trimmed.isEmpty()) true
            else {
                item.profile.name.lowercase().contains(trimmed) ||
                    "#${item.profile.profileNumber}".contains(trimmed) ||
                    item.profile.profileNumber.toString().contains(trimmed) ||
                    (item.proxy?.host?.lowercase()?.contains(trimmed) == true) ||
                    (item.profile.lastPublicIp?.lowercase()?.contains(trimmed) == true) ||
                    (item.proxy?.publicIp?.lowercase()?.contains(trimmed) == true)
            }
        }

        val sorted = when (sort) {
            ProfileSortOrder.NUMBER_ASC -> filtered.sortedBy { it.profile.profileNumber }
            ProfileSortOrder.NAME_ASC -> filtered.sortedBy { it.profile.name.lowercase() }
            ProfileSortOrder.STATUS -> filtered.sortedWith(
                compareBy<ProfileWithProxy> {
                    when {
                        tunnels[it.profile.id]?.isConnected == true || it.profile.status == ProfileStatus.ACTIVE -> 0
                        it.profile.status == ProfileStatus.PROXY_ERROR -> 1
                        it.proxy != null -> 2
                        else -> 3
                    }
                }.thenBy { it.profile.profileNumber }
            )
            ProfileSortOrder.RECENTLY_USED -> filtered.sortedByDescending { it.profile.lastOpenedAt ?: 0L }
        }

        val activeCount = profiles.count {
            tunnels[it.profile.id]?.isConnected == true || it.profile.status == ProfileStatus.ACTIVE
        }
        val workingCount = proxies.count {
            it.status == ProxyStatus.WORKING || it.status == ProxyStatus.CONNECTED
        }

        DashboardUiState(
            profiles = profiles,
            filteredProfiles = sorted,
            proxies = proxies,
            activeTunnels = tunnels,
            searchQuery = query,
            sortOrder = sort,
            totalProfilesCount = profiles.size,
            activeProfilesCount = activeCount,
            workingProxiesCount = workingCount,
            statusBanner = _statusBanner.value
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOrder(order: ProfileSortOrder) {
        _sortOrder.value = order
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun showBanner(message: String) {
        _statusBanner.value = message
    }

    fun createProfile(
        name: String,
        proxyId: Long?,
        userAgentPreset: UserAgentPreset,
        isIncognitoDefault: Boolean,
        notes: String
    ) {
        viewModelScope.launch {
            runCatching {
                profileManager.createProfile(name, proxyId, userAgentPreset, isIncognitoDefault, notes)
            }.onSuccess {
                _statusBanner.value = "Created isolated browser profile"
            }.onFailure {
                _statusBanner.value = it.message ?: "Failed to create profile"
            }
        }
    }

    fun batchCreateUpTo(targetCount: Int) {
        viewModelScope.launch {
            runCatching {
                profileManager.batchCreateProfilesUpTo(targetCount)
            }.onSuccess { created ->
                _statusBanner.value = if (created > 0) {
                    "Provisioned $created new isolated profiles"
                } else {
                    "Already at $targetCount profiles"
                }
            }.onFailure {
                _statusBanner.value = it.message ?: "Batch provisioning failed"
            }
        }
    }

    fun updateProfile(
        profileId: Long,
        name: String,
        proxyId: Long?,
        userAgentPreset: UserAgentPreset,
        isIncognitoDefault: Boolean,
        notes: String
    ) {
        viewModelScope.launch {
            runCatching {
                profileManager.updateProfileConfig(
                    profileId = profileId,
                    name = name,
                    proxyId = proxyId,
                    userAgentPreset = userAgentPreset,
                    isIncognitoDefault = isIncognitoDefault,
                    notes = notes
                )
            }.onSuccess {
                _statusBanner.value = "Updated profile configuration"
            }.onFailure {
                _statusBanner.value = it.message ?: "Failed to update profile"
            }
        }
    }

    fun cloneProfile(profileId: Long) {
        viewModelScope.launch {
            runCatching {
                profileManager.cloneProfile(profileId)
            }.onSuccess {
                _statusBanner.value = "Cloned profile with fresh isolated storage"
            }.onFailure {
                _statusBanner.value = it.message ?: "Unable to clone profile"
            }
        }
    }

    fun deleteProfile(profileId: Long) {
        viewModelScope.launch {
            runCatching {
                profileManager.deleteProfile(profileId)
            }.onSuccess {
                _statusBanner.value = "Deleted profile and wiped isolated storage"
            }
        }
    }

    fun clearProfileStorage(profileId: Long) {
        viewModelScope.launch {
            profileManager.clearProfileBrowserStorage(profileId)
            _statusBanner.value = "Cleared cookies, cache, and history for profile"
        }
    }

    fun addOrUpdateProxy(
        editingId: Long?,
        name: String,
        host: String,
        port: Int,
        username: String,
        password: String
    ) {
        viewModelScope.launch {
            runCatching {
                if (editingId == null) {
                    proxyManager.addProxy(name, host, port, username, password)
                } else {
                    proxyManager.updateProxy(
                        proxyId = editingId,
                        name = name,
                        host = host,
                        port = port,
                        username = username,
                        newPasswordOrNull = password.ifBlank { null }
                    )
                }
            }.onSuccess {
                _statusBanner.value = if (editingId == null) "SOCKS5 proxy added securely" else "SOCKS5 proxy updated"
            }.onFailure {
                _statusBanner.value = it.message ?: "Invalid proxy configuration"
            }
        }
    }

    fun deleteProxy(proxyId: Long) {
        viewModelScope.launch {
            proxyManager.deleteProxy(proxyId)
            _statusBanner.value = "Proxy removed from encrypted vault"
        }
    }

    fun decryptProxyPassword(proxy: ProxyEntity): String = proxyManager.decryptPassword(proxy)

    fun testProxy(proxyId: Long) {
        viewModelScope.launch {
            val timeout = settings.value.proxyTimeoutMs
            when (val res = proxyManager.testProxy(proxyId, timeout)) {
                is ProxyCheckResult.Success -> {
                    _statusBanner.value = "Proxy verified: ${res.publicIp} (${res.latencyMs} ms)"
                }
                is ProxyCheckResult.Failure -> {
                    _statusBanner.value = "Proxy test failed: ${res.reason}"
                }
            }
        }
    }

    fun testAllProxies() {
        viewModelScope.launch {
            val ids = dashboardState.value.proxies.map { it.id }
            if (ids.isEmpty()) {
                _statusBanner.value = "No proxies in vault to test"
                return@launch
            }
            _statusBanner.value = "Testing ${ids.size} SOCKS5 proxies..."
            proxyManager.testProxies(ids, settings.value.proxyTimeoutMs)
            _statusBanner.value = "Finished testing ${ids.size} proxies"
        }
    }

    fun connectProfileProxy(profileId: Long, proxyId: Long) {
        viewModelScope.launch {
            runCatching {
                proxyManager.connectProfileProxy(
                    profileId = profileId,
                    proxyId = proxyId,
                    timeoutMs = settings.value.proxyTimeoutMs,
                    verifyConnection = true
                )
            }.onSuccess { tunnel ->
                _statusBanner.value = if (tunnel.isConnected) {
                    "Connected profile #$profileId via ${tunnel.publicIp ?: tunnel.remoteHost}"
                } else {
                    "Proxy connection failed: ${tunnel.lastError}"
                }
            }.onFailure {
                _statusBanner.value = it.message ?: "Failed to connect proxy"
            }
        }
    }

    fun disconnectProfileProxy(profileId: Long) {
        viewModelScope.launch {
            proxyManager.disconnectProfileProxy(profileId)
            _statusBanner.value = "Disconnected profile proxy tunnel"
        }
    }

    fun autoAssignProxies() {
        viewModelScope.launch {
            val count = proxyManager.assignProxiesToProfiles()
            _statusBanner.value = if (count > 0) {
                "Auto-assigned proxies to $count profiles"
            } else {
                "Add proxies first before auto-assigning"
            }
        }
    }

    fun parseBulkText(rawText: String) {
        viewModelScope.launch {
            val result = proxyManager.parseBulkProxies(rawText)
            _bulkParseResult.value = result
            _bulkImportOutcome.value = null
        }
    }

    fun executeBulkImport(autoAssign: Boolean, autoTest: Boolean) {
        val parsed = _bulkParseResult.value ?: return
        if (parsed.validEntries.isEmpty()) return
        viewModelScope.launch {
            _isBulkImporting.value = true
            val outcome = proxyManager.importParsedProxies(
                entries = parsed.validEntries,
                autoAssignToProfiles = autoAssign,
                autoTestAfterImport = autoTest,
                timeoutMs = settings.value.proxyTimeoutMs
            )
            _bulkImportOutcome.value = outcome.copy(
                duplicateCount = parsed.duplicateLines.size,
                invalidCount = parsed.invalidLines.size
            )
            _isBulkImporting.value = false
            _statusBanner.value = "Imported ${outcome.importedCount} SOCKS5 proxies"
        }
    }

    fun clearBulkPreview() {
        _bulkParseResult.value = null
        _bulkImportOutcome.value = null
    }

    fun setDefaultSearchEngine(engine: SearchEngine) {
        viewModelScope.launch { settingsStore.setSearchEngine(engine) }
    }

    fun setStrictProxyLock(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setStrictProxyLock(enabled) }
    }

    fun setForceDarkWebContent(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setForceDarkWebContent(enabled) }
    }

    fun setWebRtcProtection(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setWebRtcProtection(enabled) }
    }

    fun setProxyTimeoutSeconds(seconds: Int) {
        viewModelScope.launch { settingsStore.setProxyTimeoutMs(seconds * 1000) }
    }
}
