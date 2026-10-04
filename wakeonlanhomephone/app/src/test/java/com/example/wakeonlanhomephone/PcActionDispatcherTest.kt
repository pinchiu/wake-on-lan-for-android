package com.example.wakeonlanhomephone

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PcActionDispatcherTest {

    private val validMac = "11:22:33:44:55:66"
    private val validIp = "127.0.0.1"

    @Test
    fun testParseCommandFormats() {
        val dispatcher = PcActionDispatcher()

        assertEquals("WAKE" to "11:22:33:44:55:66", dispatcher.parseCommand("WAKE:11:22:33:44:55:66"))
        assertEquals("WAKE" to "11:22:33:44:55:66", dispatcher.parseCommand("WAKE,11:22:33:44:55:66"))
        assertEquals("SHUTDOWN" to "192.168.1.50", dispatcher.parseCommand("SHUTDOWN:192.168.1.50"))
        assertEquals("REBOOT" to "192.168.1.50", dispatcher.parseCommand("REBOOT,192.168.1.50"))
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
        val dispatcher = PcActionDispatcher()
        val result = dispatcher.dispatch("SHUTDOWN:$validIp")

        assertTrue(result.isSuccess)
        assertTrue(result.toProtocolString().startsWith("SUCCESS:"))
    }

    @Test
    fun testPowerCommandWithInvalidIpFails() = runBlocking {
        val dispatcher = PcActionDispatcher()
        val result = dispatcher.dispatch("REBOOT:999.999.999.999")

        assertFalse(result.isSuccess)
        assertTrue(result is PcActionResult.Failure)
        assertTrue(result.toProtocolString().contains("Invalid target IP"))
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
