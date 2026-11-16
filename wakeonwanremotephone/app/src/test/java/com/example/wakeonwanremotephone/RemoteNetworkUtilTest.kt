package com.example.wakeonwanremotephone

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteNetworkUtilTest {

    @Test
    fun tcpCommandWithoutHelperSecretFailsBeforeConnecting() {
        runBlocking {
            assertEquals(
                RemoteNetworkUtil.ERROR_HELPER_SECRET_MISSING,
                RemoteNetworkUtil.sendTcpCommand("fe80::1", "WAKE:AA:BB:CC:DD:EE:FF", "")
            )
            assertEquals(
                RemoteNetworkUtil.ERROR_HELPER_SECRET_MISSING,
                RemoteNetworkUtil.sendTcpCommand("fe80::1", "WAKE:AA:BB:CC:DD:EE:FF", "   ")
            )
        }
    }

    @Test
    fun directCommandWithoutPcSecretFailsBeforeSending() {
        runBlocking {
            assertEquals(
                RemoteNetworkUtil.ERROR_PC_SECRET_MISSING,
                RemoteNetworkUtil.sendDirectCommandToPC("192.168.1.10", "shutdown", "")
            )
            assertEquals(
                RemoteNetworkUtil.ERROR_PC_SECRET_MISSING,
                RemoteNetworkUtil.sendDirectCommandToPC("192.168.1.10", "shutdown", "   ")
            )
        }
    }

    @Test
    fun macParsingKeepsLegacyBehaviour() {
        val expected = listOf(0xAA, 0xBB, 0xCC, 0xDD, 0xEE, 0xFF).map { it.toByte() }
        assertEquals(expected, RemoteNetworkUtil.getMacBytes("AA:BB:CC:DD:EE:FF")?.toList())
        assertNull(RemoteNetworkUtil.getMacBytes("not-a-mac"))
        assertNull(RemoteNetworkUtil.getMacBytes("AA:BB:CC"))
    }
}
