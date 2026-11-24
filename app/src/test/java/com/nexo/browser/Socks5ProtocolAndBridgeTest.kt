package com.nexo.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Verifies real RFC 1928 / RFC 1929 SOCKS5 protocol handshake, authentication enforcement,
 * LocalSocks5Bridge HTTP tunneling, and strict fail-closed behavior (no direct fallback).
 */
class Socks5ProtocolAndBridgeTest {

    @Test
    fun `socks5 client authenticates via RFC 1929 and relays data through tunnel`() {
        val targetServer = startEchoHttpServer("PROXIED_IP=198.51.100.77")
        val socksServer = startTestSocks5Server(expectedUser = "nexoUser", expectedPass = "nexoPass!")

        try {
            val config = Socks5Config(
                host = "127.0.0.1",
                port = socksServer.port,
                username = "nexoUser",
                password = "nexoPass!"
            )

            val bridge = LocalSocks5Bridge(config)
            val localPort = bridge.start()

            try {
                // Connect as WebView/OkHttp would to the local HTTP proxy bridge
                Socket("127.0.0.1", localPort).use { client ->
                    val out = client.getOutputStream()
                    val req = "GET http://127.0.0.1:${targetServer.port}/ip HTTP/1.1\r\nHost: 127.0.0.1:${targetServer.port}\r\nConnection: close\r\n\r\n"
                    out.write(req.toByteArray(StandardCharsets.US_ASCII))
                    out.flush()

                    val response = client.getInputStream().readBytes().toString(StandardCharsets.UTF_8)
                    assertTrue("Expected 200 OK via SOCKS5 bridge, got: $response", response.contains("200 OK"))
                    assertTrue(response.contains("PROXIED_IP=198.51.100.77"))
                    assertEquals(1, socksServer.authenticatedConnections.get())
                }
            } finally {
                bridge.stop()
            }
        } finally {
            socksServer.stop()
            targetServer.stop()
        }
    }

    @Test
    fun `socks5 client rejects wrong password with Socks5AuthException`() {
        val socksServer = startTestSocks5Server(expectedUser = "validUser", expectedPass = "validPass")
        try {
            val badConfig = Socks5Config(
                host = "127.0.0.1",
                port = socksServer.port,
                username = "validUser",
                password = "wrongPassword"
            )
            try {
                Socks5Client.openTunnel(badConfig, "example.com", 443)
                fail("Expected Socks5AuthException")
            } catch (e: Socks5AuthException) {
                assertTrue(e.message!!.contains("authentication failed", ignoreCase = true))
            }
        } finally {
            socksServer.stop()
        }
    }

    @Test
    fun `local bridge fails closed with 502 and never connects directly when proxy auth fails`() {
        val directTargetHits = AtomicInteger(0)
        val targetServer = startEchoHttpServer("DIRECT_LEAK", onHit = { directTargetHits.incrementAndGet() })
        val socksServer = startTestSocks5Server(expectedUser = "right", expectedPass = "secret")

        try {
            val badConfig = Socks5Config(
                host = "127.0.0.1",
                port = socksServer.port,
                username = "right",
                password = "bad"
            )
            val bridge = LocalSocks5Bridge(badConfig)
            val localPort = bridge.start()

            try {
                Socket("127.0.0.1", localPort).use { client ->
                    val req = "CONNECT 127.0.0.1:${targetServer.port} HTTP/1.1\r\nHost: 127.0.0.1:${targetServer.port}\r\n\r\n"
                    client.getOutputStream().write(req.toByteArray(StandardCharsets.US_ASCII))
                    client.getOutputStream().flush()

                    val response = client.getInputStream().readBytes().toString(StandardCharsets.UTF_8)
                    assertTrue("Expected 502 Bad Gateway, got: $response", response.contains("502 Bad Gateway"))
                    assertEquals("Direct fallback must NEVER occur", 0, directTargetHits.get())
                }
            } finally {
                bridge.stop()
            }
        } finally {
            socksServer.stop()
            targetServer.stop()
        }
    }

    private class TestServerHandle(
        val port: Int,
        val authenticatedConnections: AtomicInteger = AtomicInteger(0),
        private val closeFn: () -> Unit
    ) {
        fun stop() = closeFn()
    }

    private fun startEchoHttpServer(body: String, onHit: () -> Unit = {}): TestServerHandle {
        val server = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        val running = AtomicBoolean(true)
        thread(isDaemon = true) {
            while (running.get() && !server.isClosed) {
                runCatching {
                    val sock = server.accept()
                    thread(isDaemon = true) {
                        sock.use {
                            onHit()
                            val input = BufferedInputStream(it.getInputStream())
                            // Read until blank line
                            val buf = ByteArrayOutputStream()
                            var prev = 0
                            while (true) {
                                val b = input.read()
                                if (b == -1) break
                                buf.write(b)
                                val s = buf.toString(StandardCharsets.ISO_8859_1.name())
                                if (s.endsWith("\r\n\r\n")) break
                                prev = b
                            }
                            val resp = "HTTP/1.1 200 OK\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body"
                            it.getOutputStream().write(resp.toByteArray(StandardCharsets.UTF_8))
                            it.getOutputStream().flush()
                        }
                    }
                }
            }
        }
        return TestServerHandle(server.localPort) {
            running.set(false)
            runCatching { server.close() }
        }
    }

    private fun startTestSocks5Server(expectedUser: String, expectedPass: String): TestServerHandle {
        val server = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        val running = AtomicBoolean(true)
        val authCount = AtomicInteger(0)

        thread(isDaemon = true) {
            while (running.get() && !server.isClosed) {
                runCatching {
                    val client = server.accept()
                    thread(isDaemon = true) {
                        var remote: Socket? = null
                        try {
                            val input = BufferedInputStream(client.getInputStream())
                            val output = client.getOutputStream()

                            val ver = input.read()
                            val nMethods = input.read()
                            val methods = ByteArray(nMethods)
                            readFully(input, methods)

                            if (ver != 0x05 || !methods.contains(0x02.toByte())) {
                                output.write(byteArrayOf(0x05, 0xFF.toByte()))
                                output.flush()
                                return@thread
                            }

                            // Require RFC 1929 Username/Password
                            output.write(byteArrayOf(0x05, 0x02))
                            output.flush()

                            val authVer = input.read()
                            val uLen = input.read()
                            val userBytes = ByteArray(uLen).also { readFully(input, it) }
                            val pLen = input.read()
                            val passBytes = ByteArray(pLen).also { readFully(input, it) }

                            val user = String(userBytes, StandardCharsets.UTF_8)
                            val pass = String(passBytes, StandardCharsets.UTF_8)

                            if (authVer != 0x01 || user != expectedUser || pass != expectedPass) {
                                output.write(byteArrayOf(0x01, 0x01))
                                output.flush()
                                return@thread
                            }

                            output.write(byteArrayOf(0x01, 0x00))
                            output.flush()
                            authCount.incrementAndGet()

                            // Read CONNECT command
                            val reqHead = ByteArray(4).also { readFully(input, it) }
                            val atyp = reqHead[3].toInt() and 0xFF
                            val host = when (atyp) {
                                0x01 -> {
                                    val addr = ByteArray(4).also { readFully(input, it) }
                                    InetAddress.getByAddress(addr).hostAddress!!
                                }
                                0x03 -> {
                                    val len = input.read()
                                    val dom = ByteArray(len).also { readFully(input, it) }
                                    String(dom, StandardCharsets.UTF_8)
                                }
                                else -> return@thread
                            }
                            val portBytes = ByteArray(2).also { readFully(input, it) }
                            val port = ((portBytes[0].toInt() and 0xFF) shl 8) or (portBytes[1].toInt() and 0xFF)

                            remote = Socket(host, port)
                            output.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                            output.flush()

                            val t = thread(isDaemon = true) {
                                copy(input, remote.getOutputStream())
                            }
                            copy(remote.getInputStream(), output)
                            t.join(500)
                        } catch (_: Throwable) {
                        } finally {
                            runCatching { remote?.close() }
                            runCatching { client.close() }
                        }
                    }
                }
            }
        }

        return TestServerHandle(server.localPort, authCount) {
            running.set(false)
            runCatching { server.close() }
        }
    }

    private fun readFully(input: InputStream, dest: ByteArray) {
        var offset = 0
        while (offset < dest.size) {
            val n = input.read(dest, offset, dest.size - offset)
            if (n < 0) throw IllegalStateException("EOF")
            offset += n
        }
    }

    private fun copy(input: InputStream, output: OutputStream) {
        val buf = ByteArray(4096)
        while (true) {
            val n = runCatching { input.read(buf) }.getOrDefault(-1)
            if (n <= 0) break
            runCatching {
                output.write(buf, 0, n)
                output.flush()
            }
        }
    }
}
