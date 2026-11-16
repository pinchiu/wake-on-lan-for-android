package com.example.wakeonlanhomephone

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

class PcActionDispatcherTest {

    private val validMac = "11:22:33:44:55:66"
    private val validIp = "127.0.0.1"
    private val testPcSecret = "test-pc-secret-0123456789abcdef"

    private fun dispatcherWithSecret(
        port: Int = 9877,
        secret: () -> String = { testPcSecret }
    ) = PcActionDispatcher(
        pcCommandPort = port,
        pcSecretProvider = secret
    )

    @Test
    fun testParseCommandFormats() {
        val dispatcher = PcActionDispatcher()

        assertEquals("WAKE" to "11:22:33:44:55:66", dispatcher.parseCommand("WAKE:11:22:33:44:55:66"))
        assertEquals("WAKE" to "11:22:33:44:55:66", dispatcher.parseCommand("WAKE,11:22:33:44:55:66"))
        assertEquals("SHUTDOWN" to "192.168.1.50", dispatcher.parseCommand("SHUTDOWN:192.168.1.50"))
        assertEquals("REBOOT" to "192.168.1.50", dispatcher.parseCommand("REBOOT,192.168.1.50"))
        assertEquals("LOCK" to "192.168.1.50", dispatcher.parseCommand("lock:192.168.1.50"))
        assertEquals("WAKE" to "11:22:33:44:55:66", dispatcher.parseCommand("11:22:33:44:55:66"))
        assertEquals("WAKE" to "", dispatcher.parseCommand("WAKE"))
        assertEquals("WAKE" to "", dispatcher.parseCommand("ON"))
    }

    @Test
    fun testWakeWithExplicitMac() = runBlocking {
        val dispatcher = PcActionDispatcher()
        val result = dispatcher.dispatch("WAKE:$validMac")

        assertTrue(result.isSuccess)
        assertTrue(result.toProtocolString().startsWith("SUCCESS:"))
        assertTrue(result is PcActionResult.Success)
    }

    @Test
    fun testWakeWithDefaultMac() = runBlocking {
        val dispatcher = PcActionDispatcher(defaultMacProvider = { validMac })
        val result = dispatcher.dispatch("WAKE")

        assertTrue(result.isSuccess)
        assertTrue(result is PcActionResult.Success)
    }

    @Test
    fun testWakeWithoutMacAndNoDefaultFails() = runBlocking {
        val dispatcher = PcActionDispatcher(defaultMacProvider = { "" })
        val result = dispatcher.dispatch("WAKE")

        assertFalse(result.isSuccess)
        assertTrue(result is PcActionResult.Failure)
        assertEquals("ERROR: Target MAC is not configured", result.toProtocolString())
    }

    @Test
    fun testWakeWithInvalidMacFails() = runBlocking {
        val dispatcher = PcActionDispatcher()
        val result = dispatcher.dispatch("WAKE:invalid-mac")

        assertFalse(result.isSuccess)
        assertTrue(result is PcActionResult.Failure)
        assertTrue(result.toProtocolString().contains("Invalid MAC"))
    }

    @Test
    fun testPowerCommandWithValidIp() = runBlocking {
        val dispatcher = dispatcherWithSecret()
        val result = dispatcher.dispatch("SHUTDOWN:$validIp")

        assertTrue(result.isSuccess)
        assertTrue(result.toProtocolString().startsWith("SUCCESS:"))
    }

    @Test
    fun testPowerCommandWithInvalidIpFails() = runBlocking {
        val dispatcher = dispatcherWithSecret()
        val result = dispatcher.dispatch("REBOOT:999.999.999.999")

        assertFalse(result.isSuccess)
        assertTrue(result is PcActionResult.Failure)
        assertTrue(result.toProtocolString().contains("Invalid target IP"))
    }

    @Test
    fun testPowerCommandIsSignedWithPcSecret() = runBlocking {
        DatagramSocket(0, InetAddress.getByName(validIp)).use { receiver ->
            receiver.soTimeout = 2000
            val dispatcher = dispatcherWithSecret(port = receiver.localPort)
            val result = dispatcher.dispatch("SHUTDOWN:$validIp")

            assertTrue(result.isSuccess)

            val packet = DatagramPacket(ByteArray(1024), 1024)
            receiver.receive(packet)
            val payload = String(packet.data, 0, packet.length, Charsets.UTF_8)

            assertEquals(AuthResult.Ok, CommandAuth.verify(payload, testPcSecret))
            assertEquals("shutdown", CommandAuth.commandOf(payload))
            assertTrue(payload, payload.matches(Regex("^shutdown\\|ts=\\d+\\|nonce=[0-9a-f]{16}\\|sig=[0-9a-f]{64}$")))
        }
    }

    @Test
    fun testPowerCommandWithBlankPcSecretFailsAndSendsNothing() = runBlocking {
        DatagramSocket(0, InetAddress.getByName(validIp)).use { receiver ->
            receiver.soTimeout = 400
            val dispatcher = dispatcherWithSecret(port = receiver.localPort) { "" }
            val result = dispatcher.dispatch("SHUTDOWN:$validIp")

            assertFalse(result.isSuccess)
            assertTrue(result is PcActionResult.Failure)
            assertEquals(
                "ERROR: Shared PC secret is not configured",
                result.toProtocolString()
            )

            val packet = DatagramPacket(ByteArray(1024), 1024)
            try {
                receiver.receive(packet)
                fail("No UDP packet should be sent when the PC secret is blank")
            } catch (_: SocketTimeoutException) {
                // Expected: nothing was sent.
            }
        }
    }

    @Test
    fun testWakeStaysUnsignedAndWorksWithoutPcSecret() = runBlocking {
        val dispatcher = PcActionDispatcher(
            defaultMacProvider = { validMac },
            pcSecretProvider = { "" }
        )
        val result = dispatcher.dispatch("WAKE")

        assertTrue(result.isSuccess)
    }

    @Test
    fun testUnknownActionFails() = runBlocking {
        val dispatcher = PcActionDispatcher()
        val result = dispatcher.dispatch("EXPLODE:127.0.0.1")

        assertFalse(result.isSuccess)
        assertTrue(result is PcActionResult.Failure)
        assertEquals("ERROR: Unknown action 'EXPLODE'", result.toProtocolString())
    }

    @Test
    fun testEmptyCommandFails() = runBlocking {
        val dispatcher = PcActionDispatcher()
        val result = dispatcher.dispatch("   ")

        assertFalse(result.isSuccess)
        assertTrue(result is PcActionResult.Failure)
        assertEquals("ERROR: Empty command received", result.toProtocolString())
    }
}
