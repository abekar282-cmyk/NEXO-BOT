package com.nexo.browser

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

data class Socks5Config(
    val host: String,
    val port: Int,
    val username: String = "",
    val password: String = "",
    val connectTimeoutMs: Int = 12_000,
    val readTimeoutMs: Int = 15_000
) {
    val hasCredentials: Boolean get() = username.isNotEmpty() || password.isNotEmpty()
}

sealed class ProxyCheckResult {
    data class Success(
        val publicIp: String,
        val countryCode: String?,
        val latencyMs: Long
    ) : ProxyCheckResult()

    data class Failure(val reason: String) : ProxyCheckResult()
}

open class Socks5Exception(message: String, cause: Throwable? = null) : IOException(message, cause)
class Socks5AuthException(message: String) : Socks5Exception(message)
class Socks5ConnectionException(message: String, cause: Throwable? = null) : Socks5Exception(message, cause)

/**
 * Full RFC 1928 (SOCKS5) & RFC 1929 (Username/Password Auth) client.
 * Domain hostnames are passed using ATYP=0x03 so DNS resolution occurs on the SOCKS5 proxy.
 */
object Socks5Client {

    private const val SOCKS_VERSION: Byte = 0x05
    private const val AUTH_NONE: Byte = 0x00
    private const val AUTH_USER_PASS: Byte = 0x02
    private const val AUTH_NO_ACCEPTABLE: Byte = 0xFF.toByte()
    private const val CMD_CONNECT: Byte = 0x01
    private const val ATYP_IPV4: Byte = 0x01
    private const val ATYP_DOMAIN: Byte = 0x03
    private const val ATYP_IPV6: Byte = 0x04

    fun openTunnel(config: Socks5Config, targetHost: String, targetPort: Int): Socket {
        require(targetPort in 1..65535) { "Invalid target port: $targetPort" }
        val socket = Socket()
        try {
            socket.tcpNoDelay = true
            socket.soTimeout = config.readTimeoutMs
            socket.connect(InetSocketAddress(config.host, config.port), config.connectTimeoutMs)

            val input = BufferedInputStream(socket.getInputStream())
            val output = socket.getOutputStream()

            // 1. Method negotiation
            if (config.hasCredentials) {
                output.write(byteArrayOf(SOCKS_VERSION, 0x02, AUTH_NONE, AUTH_USER_PASS))
            } else {
                output.write(byteArrayOf(SOCKS_VERSION, 0x01, AUTH_NONE))
            }
            output.flush()

            val greetingReply = readExact(input, 2)
            if (greetingReply[0] != SOCKS_VERSION) {
                throw Socks5ConnectionException("Invalid SOCKS version from proxy: ${greetingReply[0]}")
            }

            when (greetingReply[1]) {
                AUTH_NONE -> Unit
                AUTH_USER_PASS -> performUserPassAuth(input, output, config.username, config.password)
                AUTH_NO_ACCEPTABLE -> throw Socks5AuthException("Proxy rejected authentication methods")
                else -> throw Socks5AuthException("Unsupported SOCKS5 auth method: ${greetingReply[1]}")
            }

            // 2. CONNECT command
            val request = buildConnectRequest(targetHost, targetPort)
            output.write(request)
            output.flush()

            // 3. Parse CONNECT reply
            val header = readExact(input, 4)
            if (header[0] != SOCKS_VERSION) {
                throw Socks5ConnectionException("Unexpected SOCKS reply version: ${header[0]}")
            }
            val replyCode = header[1].toInt() and 0xFF
            if (replyCode != 0x00) {
                throw Socks5ConnectionException("SOCKS5 CONNECT failed: ${mapReplyCode(replyCode)}")
            }

            // Consume BND.ADDR + BND.PORT
            when (header[3]) {
                ATYP_IPV4 -> readExact(input, 4 + 2)
                ATYP_IPV6 -> readExact(input, 16 + 2)
                ATYP_DOMAIN -> {
                    val len = readExact(input, 1)[0].toInt() and 0xFF
                    readExact(input, len + 2)
                }
                else -> throw Socks5ConnectionException("Invalid SOCKS5 BND ATYP: ${header[3]}")
            }

            return socket
        } catch (e: Socks5Exception) {
            runCatching { socket.close() }
            throw e
        } catch (e: Throwable) {
            runCatching { socket.close() }
            throw Socks5ConnectionException(e.message ?: "SOCKS5 connection failed", e)
        }
    }

    private fun performUserPassAuth(
        input: InputStream,
        output: OutputStream,
        username: String,
        password: String
    ) {
        val userBytes = username.toByteArray(StandardCharsets.UTF_8)
        val passBytes = password.toByteArray(StandardCharsets.UTF_8)
        if (userBytes.size > 255 || passBytes.size > 255) {
            throw Socks5AuthException("SOCKS5 credentials exceed 255 bytes")
        }
        val packet = ByteArray(3 + userBytes.size + passBytes.size)
        packet[0] = 0x01 // RFC 1929 subnegotiation version
        packet[1] = userBytes.size.toByte()
        System.arraycopy(userBytes, 0, packet, 2, userBytes.size)
        packet[2 + userBytes.size] = passBytes.size.toByte()
        System.arraycopy(passBytes, 0, packet, 3 + userBytes.size, passBytes.size)

        output.write(packet)
        output.flush()

        val authReply = readExact(input, 2)
        if (authReply[0] != 0x01.toByte()) {
            throw Socks5AuthException("Invalid RFC 1929 auth version: ${authReply[0]}")
        }
        if (authReply[1] != 0x00.toByte()) {
            throw Socks5AuthException("SOCKS5 username/password authentication failed (status=${authReply[1]})")
        }
    }

    internal fun buildConnectRequest(targetHost: String, targetPort: Int): ByteArray {
        val cleanHost = targetHost.trim().removePrefix("[").removeSuffix("]")
        val addressBytes: ByteArray
        val atyp: Byte

        val parsedIp = parseNumericIpOrNull(cleanHost)
        if (parsedIp is Inet4Address) {
            atyp = ATYP_IPV4
            addressBytes = parsedIp.address
        } else if (parsedIp is Inet6Address) {
            atyp = ATYP_IPV6
            addressBytes = parsedIp.address
        } else {
            val domainBytes = cleanHost.toByteArray(StandardCharsets.UTF_8)
            require(domainBytes.size in 1..255) { "Hostname too long for SOCKS5: $cleanHost" }
            atyp = ATYP_DOMAIN
            addressBytes = byteArrayOf(domainBytes.size.toByte()) + domainBytes
        }

        val packet = ByteArray(4 + addressBytes.size + 2)
        packet[0] = SOCKS_VERSION
        packet[1] = CMD_CONNECT
        packet[2] = 0x00
        packet[3] = atyp
        System.arraycopy(addressBytes, 0, packet, 4, addressBytes.size)
        packet[4 + addressBytes.size] = ((targetPort ushr 8) and 0xFF).toByte()
        packet[5 + addressBytes.size] = (targetPort and 0xFF).toByte()
        return packet
    }

    private fun parseNumericIpOrNull(host: String): InetAddress? {
        val isIpv4Literal = host.matches(Regex("""^\d{1,3}(\.\d{1,3}){3}$"""))
        val isIpv6Literal = host.contains(":")
        if (!isIpv4Literal && !isIpv6Literal) return null
        return runCatching { InetAddress.getByName(host) }.getOrNull()
    }

    /**
     * Tests real SOCKS5 connectivity, authentication, and outbound public IP strictly over the proxy.
     * Never falls back to a direct connection.
     */
    fun checkPublicIp(config: Socks5Config): ProxyCheckResult {
        val startedNs = System.nanoTime()

        // Try HTTPS api.ipify.org first, then HTTPS ipwho.is for GeoIP country
        val primary = runCatching {
            fetchHttpsOverSocks5(config, "api.ipify.org", 443, "/?format=json")
        }
        primary.onSuccess { body ->
            val ip = runCatching { JSONObject(body).optString("ip") }.getOrNull().orEmpty()
            if (ip.isNotBlank()) {
                val elapsedMs = (System.nanoTime() - startedNs) / 1_000_000L
                val country = runCatching {
                    val geo = fetchHttpsOverSocks5(config, "ipwho.is", 443, "/$ip")
                    JSONObject(geo).optString("country_code").takeIf { it.isNotBlank() }
                }.getOrNull()
                return ProxyCheckResult.Success(
                    publicIp = ip,
                    countryCode = country,
                    latencyMs = elapsedMs.coerceAtLeast(1L)
                )
            }
        }

        // Secondary HTTPS endpoint purely over SOCKS5
        val secondary = runCatching {
            fetchHttpsOverSocks5(config, "ipwho.is", 443, "/")
        }
        secondary.onSuccess { body ->
            val json = runCatching { JSONObject(body) }.getOrNull()
            val ip = json?.optString("ip").orEmpty()
            if (ip.isNotBlank()) {
                val elapsedMs = (System.nanoTime() - startedNs) / 1_000_000L
                val cc = json?.optString("country_code")?.takeIf { it.isNotBlank() }
                return ProxyCheckResult.Success(
                    publicIp = ip,
                    countryCode = cc,
                    latencyMs = elapsedMs.coerceAtLeast(1L)
                )
            }
        }

        val err = primary.exceptionOrNull() ?: secondary.exceptionOrNull()
        return ProxyCheckResult.Failure(err?.message ?: "Proxy test failed")
    }

    internal fun fetchHttpsOverSocks5(
        config: Socks5Config,
        host: String,
        port: Int,
        path: String
    ): String {
        val rawSocket = openTunnel(config, host, port)
        val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
        val sslSocket = sslFactory.createSocket(rawSocket, host, port, true) as SSLSocket
        sslSocket.soTimeout = config.readTimeoutMs
        return sslSocket.use { sock ->
            sock.startHandshake()
            val verifier = HttpsURLConnection.getDefaultHostnameVerifier()
            if (!verifier.verify(host, sock.session)) {
                throw IOException("TLS hostname verification failed for $host")
            }

            val req = "GET $path HTTP/1.1\r\nHost: $host\r\nUser-Agent: NexoBrowser/1.0\r\nAccept: application/json\r\nConnection: close\r\n\r\n"
            sock.outputStream.write(req.toByteArray(StandardCharsets.US_ASCII))
            sock.outputStream.flush()

            val rawResponse = readAllBytesWithLimit(sock.inputStream, 256 * 1024)
            val text = String(rawResponse, StandardCharsets.UTF_8)
            val separatorIdx = text.indexOf("\r\n\r\n")
            if (separatorIdx < 0) throw IOException("Malformed HTTP response from $host")

            val headers = text.substring(0, separatorIdx)
            val bodyPart = text.substring(separatorIdx + 4)
            if (headers.contains("Transfer-Encoding: chunked", ignoreCase = true)) {
                decodeChunkedBody(bodyPart)
            } else {
                bodyPart.trim()
            }
        }
    }

    private fun decodeChunkedBody(chunked: String): String {
        val out = StringBuilder()
        var index = 0
        while (index < chunked.length) {
            val lineEnd = chunked.indexOf("\r\n", index)
            if (lineEnd < 0) break
            val sizeHex = chunked.substring(index, lineEnd).substringBefore(";").trim()
            val size = sizeHex.toIntOrNull(16) ?: break
            if (size == 0) break
            val dataStart = lineEnd + 2
            val dataEnd = (dataStart + size).coerceAtMost(chunked.length)
            out.append(chunked, dataStart, dataEnd)
            index = dataEnd + 2
        }
        return out.toString().trim()
    }

    private fun readAllBytesWithLimit(input: InputStream, maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        var total = 0
        while (total < maxBytes) {
            val read = input.read(buf)
            if (read == -1) break
            out.write(buf, 0, read)
            total += read
        }
        return out.toByteArray()
    }

    private fun readExact(input: InputStream, count: Int): ByteArray {
        val buffer = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = input.read(buffer, offset, count - offset)
            if (read == -1) {
                throw EOFException("Unexpected EOF from SOCKS5 server (read $offset/$count bytes)")
            }
            offset += read
        }
        return buffer
    }

    private fun mapReplyCode(code: Int): String = when (code) {
        0x01 -> "General SOCKS server failure"
        0x02 -> "Connection not allowed by ruleset"
        0x03 -> "Network unreachable"
        0x04 -> "Host unreachable"
        0x05 -> "Connection refused by target"
        0x06 -> "TTL expired"
        0x07 -> "Command not supported"
        0x08 -> "Address type not supported"
        else -> "Unknown SOCKS5 error (0x${code.toString(16)})"
    }
}

/**
 * Local loopback HTTP CONNECT / HTTP-to-SOCKS5 proxy server bound strictly to 127.0.0.1.
 * Allows Android WebView (via ProxyController) and OkHttp to route all HTTP & HTTPS traffic
 * through an authenticated SOCKS5 proxy without ever falling back to direct network access.
 */
class LocalSocks5Bridge(
    val config: Socks5Config,
    private val onTunnelError: (String) -> Unit = {}
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null

    var localPort: Int = -1
        private set

    val isRunning: Boolean
        get() = running.get() && serverSocket?.isClosed == false

    fun start(): Int {
        if (isRunning) return localPort
        val server = ServerSocket(0, 64, InetAddress.getByName("127.0.0.1"))
        serverSocket = server
        localPort = server.localPort
        running.set(true)

        scope.launch {
            while (running.get() && !server.isClosed) {
                try {
                    val client = server.accept()
                    launch { handleClient(client) }
                } catch (_: Throwable) {
                    if (!running.get()) break
                }
            }
        }
        return localPort
    }

    fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
        scope.cancel()
    }

    private fun handleClient(client: Socket) {
        var upstream: Socket? = null
        try {
            client.tcpNoDelay = true
            client.soTimeout = config.readTimeoutMs
            val clientIn = BufferedInputStream(client.getInputStream())
            val clientOut = client.getOutputStream()

            val headerBytes = readHttpHeaderBlock(clientIn) ?: return
            val headerText = String(headerBytes, StandardCharsets.ISO_8859_1)
            val lines = headerText.split("\r\n")
            val requestLine = lines.firstOrNull()?.trim().orEmpty()
            if (requestLine.isEmpty()) return

            val parts = requestLine.split(" ")
            if (parts.size < 3) {
                sendBadGateway(clientOut, "Invalid HTTP proxy request")
                return
            }

            val method = parts[0].uppercase()
            val target = parts[1]
            val version = parts[2]

            if (method == "CONNECT") {
                val (host, port) = parseHostPort(target, 443)
                upstream = try {
                    Socks5Client.openTunnel(config, host, port)
                } catch (e: Throwable) {
                    val msg = e.message ?: "SOCKS5 tunnel failed"
                    onTunnelError(msg)
                    sendBadGateway(clientOut, msg)
                    return
                }
                clientOut.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
                clientOut.flush()
                relayBidirectional(clientIn, clientOut, upstream.getInputStream(), upstream.getOutputStream())
            } else {
                // Plain HTTP proxy request: GET http://example.com:80/path HTTP/1.1
                val parsed = parseHttpAbsoluteTarget(target, lines)
                if (parsed == null) {
                    sendBadGateway(clientOut, "Unsupported proxy URL: $target")
                    return
                }
                val (host, port, originPath) = parsed
                upstream = try {
                    Socks5Client.openTunnel(config, host, port)
                } catch (e: Throwable) {
                    val msg = e.message ?: "SOCKS5 tunnel failed"
                    onTunnelError(msg)
                    sendBadGateway(clientOut, msg)
                    return
                }

                val rebuiltHeaders = buildString {
                    append("$method $originPath $version\r\n")
                    for (i in 1 until lines.size) {
                        val line = lines[i]
                        if (line.isBlank()) continue
                        if (line.startsWith("Proxy-Connection:", ignoreCase = true)) continue
                        if (line.startsWith("Proxy-Authorization:", ignoreCase = true)) continue
                        append(line).append("\r\n")
                    }
                    append("\r\n")
                }

                val upOut = upstream.getOutputStream()
                upOut.write(rebuiltHeaders.toByteArray(StandardCharsets.ISO_8859_1))
                upOut.flush()

                relayBidirectional(clientIn, clientOut, upstream.getInputStream(), upOut)
            }
        } catch (_: Throwable) {
            // Connection closed or reset
        } finally {
            runCatching { upstream?.close() }
            runCatching { client.close() }
        }
    }

    private fun relayBidirectional(
        clientIn: InputStream,
        clientOut: OutputStream,
        upstreamIn: InputStream,
        upstreamOut: OutputStream
    ) {
        val t1 = scope.launch {
            runCatching {
                copyStream(clientIn, upstreamOut)
                upstreamOut.flush()
            }
        }
        try {
            copyStream(upstreamIn, clientOut)
            runCatching { clientOut.flush() }
        } finally {
            t1.cancel()
        }
    }

    private fun copyStream(input: InputStream, output: OutputStream) {
        val buf = ByteArray(16 * 1024)
        while (running.get()) {
            val n = input.read(buf)
            if (n == -1) break
            output.write(buf, 0, n)
            output.flush()
        }
    }

    private fun sendBadGateway(out: OutputStream, reason: String) {
        val body = "NEXO BROWSER Proxy Blocked (No Direct Fallback): $reason"
        val resp = "HTTP/1.1 502 Bad Gateway\r\n" +
            "Content-Type: text/plain; charset=utf-8\r\n" +
            "Content-Length: ${body.toByteArray().size}\r\n" +
            "Connection: close\r\n\r\n$body"
        runCatching {
            out.write(resp.toByteArray(StandardCharsets.UTF_8))
            out.flush()
        }
    }

    private fun readHttpHeaderBlock(input: InputStream): ByteArray? {
        val out = ByteArrayOutputStream()
        var state = 0
        while (out.size() < 64 * 1024) {
            val b = input.read()
            if (b == -1) return if (out.size() == 0) null else out.toByteArray()
            out.write(b)
            state = when {
                state == 0 && b == '\r'.code -> 1
                state == 1 && b == '\n'.code -> 2
                state == 2 && b == '\r'.code -> 3
                state == 3 && b == '\n'.code -> return out.toByteArray()
                b == '\r'.code -> 1
                else -> 0
            }
        }
        return out.toByteArray()
    }

    private fun parseHostPort(authority: String, defaultPort: Int): Pair<String, Int> {
        val trimmed = authority.trim()
        if (trimmed.startsWith("[")) {
            val closeIdx = trimmed.indexOf(']')
            if (closeIdx > 0) {
                val host = trimmed.substring(1, closeIdx)
                val port = trimmed.substring(closeIdx + 1).removePrefix(":").toIntOrNull() ?: defaultPort
                return host to port
            }
        }
        val colonIdx = trimmed.lastIndexOf(':')
        return if (colonIdx > 0) {
            val host = trimmed.substring(0, colonIdx)
            val port = trimmed.substring(colonIdx + 1).toIntOrNull() ?: defaultPort
            host to port
        } else {
            trimmed to defaultPort
        }
    }

    private fun parseHttpAbsoluteTarget(target: String, headerLines: List<String>): Triple<String, Int, String>? {
        if (target.startsWith("http://", ignoreCase = true)) {
            val withoutScheme = target.substring(7)
            val slashIdx = withoutScheme.indexOf('/')
            val authority = if (slashIdx >= 0) withoutScheme.substring(0, slashIdx) else withoutScheme
            val path = if (slashIdx >= 0) withoutScheme.substring(slashIdx) else "/"
            val (host, port) = parseHostPort(authority, 80)
            return Triple(host, port, path)
        }
        val hostHeader = headerLines.firstOrNull { it.startsWith("Host:", ignoreCase = true) }
            ?.substringAfter(":")?.trim()
            ?: return null
        val (host, port) = parseHostPort(hostHeader, 80)
        val path = if (target.startsWith("/")) target else "/$target"
        return Triple(host, port, path)
    }
}
