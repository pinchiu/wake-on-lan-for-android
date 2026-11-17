package com.example.wakeonwanremotephone

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteNetworkUtilTest {

    @Test
    fun macParsingKeepsLegacyBehaviour() {
        val expected = listOf(0xAA, 0xBB, 0xCC, 0xDD, 0xEE, 0xFF).map { it.toByte() }
        assertEquals(expected, RemoteNetworkUtil.getMacBytes("AA:BB:CC:DD:EE:FF")?.toList())
        assertNull(RemoteNetworkUtil.getMacBytes("not-a-mac"))
        assertNull(RemoteNetworkUtil.getMacBytes("AA:BB:CC"))
    }
}
