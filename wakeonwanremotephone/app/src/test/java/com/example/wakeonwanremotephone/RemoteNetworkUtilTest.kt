package com.example.wakeonwanremotephone

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteNetworkUtilTest {

    @Test
    fun directCommandWithoutSecretToPublicIpIsRejectedBeforeSending() {
        runBlocking {
            val result = RemoteNetworkUtil.sendDirectCommandToPC("8.8.8.8", "shutdown", "")
            assertTrue(result.contains("public IP"))
        }
    }

    @Test
    fun tcpCommandWithoutSecretToPublicIpIsRejectedBeforeConnecting() {
        runBlocking {
            val result = RemoteNetworkUtil.sendTcpCommand("8.8.8.8", "WAKE", "")
            assertTrue(result, result.contains("Helper secret is required"))
        }
    }

    @Test
    fun tcpCommandRejectsLineBreaksAndBlankInputs() {
        runBlocking {
            assertTrue(RemoteNetworkUtil.sendTcpCommand("192.168.1.2", "WAKE\nSHUTDOWN", "").contains("Invalid command format"))
            assertTrue(RemoteNetworkUtil.sendTcpCommand("192.168.1.2", "WAKE\rSHUTDOWN", "").contains("Invalid command format"))
            assertTrue(RemoteNetworkUtil.sendTcpCommand("  ", "WAKE", "").contains("cannot be empty"))
            assertTrue(RemoteNetworkUtil.sendTcpCommand("192.168.1.2", "  ", "").contains("cannot be empty"))
        }
    }

    @Test
    fun macParsingIsStrictButAcceptsSurroundingWhitespace() {
        val expected = listOf(0xAA, 0xBB, 0xCC, 0xDD, 0xEE, 0xFF).map { it.toByte() }
        assertEquals(expected, RemoteNetworkUtil.getMacBytes("AA:BB:CC:DD:EE:FF")?.toList())
        assertEquals(expected, RemoteNetworkUtil.getMacBytes(" AA:BB:CC:DD:EE:FF ")?.toList())
        assertEquals(expected, RemoteNetworkUtil.getMacBytes("aa-bb-cc-dd-ee-ff")?.toList())

        assertNull(RemoteNetworkUtil.getMacBytes("not-a-mac"))
        assertNull(RemoteNetworkUtil.getMacBytes("AA:BB:CC"))
        assertNull(RemoteNetworkUtil.getMacBytes("AA1:BB:CC:DD:EE:FF"))
        assertNull(RemoteNetworkUtil.getMacBytes("AA:BB-CC:DD:EE:FF"))
        assertNull(RemoteNetworkUtil.getMacBytes("1:2:3:4:5:6"))
        assertNull(RemoteNetworkUtil.getMacBytes("AA:BB:CC:DD:EE:GG"))
    }

    @Test
    fun errorResultClassifiesEveryKnownFailure() {
        val failures = listOf(
            "Send failed: timeout",
            "Address or command cannot be empty!",
            "LAN WoL failed: network unreachable",
            "Direct send failed: host unreachable",
            "Shared secret is required",
            "PC secret is required for a public IP; set it once for port forwarding",
            "Helper secret is required for a public IP; configure it in Shared secrets",
            "Failed to sign command",
            "Invalid MAC address format",
            "Invalid command format",
            "Invalid PC IP: unknown host",
            "PC IP or command cannot be empty!",
            "Command sent, but no response from server.",
            "ERROR: unknown command"
        )

        for (failure in failures) {
            assertTrue(failure, RemoteNetworkUtil.isErrorResult(failure))
        }
    }

    @Test
    fun errorResultTreatsServerAndLocalSuccessesAsSuccess() {
        val successes = listOf(
            "SUCCESS: magic packet sent",
            "Direct command 'SHUTDOWN:192.168.1.10' sent to PC",
            "Magic Packet broadcasted directly on LAN",
            "Sent"
        )

        for (success in successes) {
            assertFalse(success, RemoteNetworkUtil.isErrorResult(success))
        }
    }
}
