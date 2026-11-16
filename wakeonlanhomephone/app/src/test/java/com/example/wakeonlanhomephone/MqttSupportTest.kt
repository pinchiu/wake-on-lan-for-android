package com.example.wakeonlanhomephone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MqttSupportTest {

    @Test
    fun parsesBareHostWithConfiguredPort() {
        val endpoint = MqttEndpoint.parse("broker.local", 1883, useSsl = false, protocol = "TCP")!!
        assertEquals("broker.local", endpoint.host)
        assertEquals(1883, endpoint.port)
        assertFalse(endpoint.useSsl)
        assertFalse(endpoint.useWebSocket)
        assertEquals("tcp://broker.local:1883", endpoint.displayUrl)
    }

    @Test
    fun portInsideHostWinsOverConfiguredPort() {
        val endpoint = MqttEndpoint.parse("tcp://10.0.0.5:1884", 1883, useSsl = false, protocol = "TCP")!!
        assertEquals("10.0.0.5", endpoint.host)
        assertEquals(1884, endpoint.port)
    }

    @Test
    fun schemeEnablesSslAndWebSocket() {
        val mqtts = MqttEndpoint.parse("mqtts://io.adafruit.com", 0, useSsl = false, protocol = "TCP")!!
        assertTrue(mqtts.useSsl)
        assertEquals(8883, mqtts.port)

        val wss = MqttEndpoint.parse("wss://cloud.example.com/ws/mqtt", 0, useSsl = false, protocol = "TCP")!!
        assertTrue(wss.useSsl)
        assertTrue(wss.useWebSocket)
        assertEquals(443, wss.port)
        assertEquals("ws/mqtt", wss.webSocketPath)
        assertEquals("wss://cloud.example.com:443/ws/mqtt", wss.displayUrl)
    }

    @Test
    fun webSocketPathIsKeptVerbatim() {
        val root = MqttEndpoint.parse("ws://broker/", 0, useSsl = false, protocol = "TCP")!!
        assertEquals("", root.webSocketPath)
        assertEquals("ws://broker:80/", root.displayUrl)
        val trailing = MqttEndpoint.parse("ws://broker/ws/", 0, useSsl = false, protocol = "TCP")!!
        assertEquals("ws/", trailing.webSocketPath)
    }

    @Test
    fun webSocketProtocolUsesDefaultPath() {
        val endpoint = MqttEndpoint.parse("broker", 8083, useSsl = false, protocol = "WebSocket")!!
        assertTrue(endpoint.useWebSocket)
        assertEquals("mqtt", endpoint.webSocketPath)
        assertEquals(8083, endpoint.port)
    }

    @Test
    fun parsesIpv6Literals() {
        val bracketed = MqttEndpoint.parse("[fd00::10]:1885", 1883, useSsl = false, protocol = "TCP")!!
        assertEquals("fd00::10", bracketed.host)
        assertEquals(1885, bracketed.port)
        assertEquals("tcp://[fd00::10]:1885", bracketed.displayUrl)

        val bare = MqttEndpoint.parse("fd00::10", 1883, useSsl = false, protocol = "TCP")!!
        assertEquals("fd00::10", bare.host)
        assertEquals(1883, bare.port)
    }

    @Test
    fun rejectsMissingHost() {
        assertNull(MqttEndpoint.parse("", 1883, useSsl = false, protocol = "TCP"))
        assertNull(MqttEndpoint.parse("   ", 1883, useSsl = false, protocol = "TCP"))
        assertNull(MqttEndpoint.parse("tcp://", 1883, useSsl = false, protocol = "TCP"))
        assertNull(MqttEndpoint.parse("[::1", 1883, useSsl = false, protocol = "TCP"))
    }

    @Test
    fun invalidConfiguredPortFallsBackToDefault() {
        assertEquals(8883, MqttEndpoint.parse("broker", -1, useSsl = true, protocol = "TCP")!!.port)
        assertEquals(1883, MqttEndpoint.parse("broker", 70000, useSsl = false, protocol = "TCP")!!.port)
    }

    @Test
    fun validatesTopicFilters() {
        assertTrue(MqttTopics.isValidFilter("home/pc/wake"))
        assertTrue(MqttTopics.isValidFilter("home/+/wake"))
        assertTrue(MqttTopics.isValidFilter("home/#"))
        assertTrue(MqttTopics.isValidFilter("#"))
        assertFalse(MqttTopics.isValidFilter(""))
        assertFalse(MqttTopics.isValidFilter("home/#/wake"))
        assertFalse(MqttTopics.isValidFilter("home/pc#"))
        assertFalse(MqttTopics.isValidFilter("home/pc+"))
    }

    @Test
    fun validatesTopicFilterUtf8ByteLength() {
        // 30k CJK characters are < 65535 UTF-16 units but ~90 KB of UTF-8.
        assertFalse(MqttTopics.isValidFilter("電".repeat(30_000)))
        // 20k CJK characters stay under the 65535-byte UTF-8 limit.
        assertTrue(MqttTopics.isValidFilter("電".repeat(20_000)))
    }

    @Test
    fun matchesTopicFilters() {
        assertTrue(MqttTopics.matches("home/pc/wake", "home/pc/wake"))
        assertFalse(MqttTopics.matches("home/pc/wake", "home/pc/wake/extra"))
        assertTrue(MqttTopics.matches("home/+/wake", "home/office/wake"))
        assertFalse(MqttTopics.matches("home/+/wake", "home/office/sleep"))
        assertTrue(MqttTopics.matches("home/#", "home"))
        assertTrue(MqttTopics.matches("home/#", "home/a/b/c"))
        assertFalse(MqttTopics.matches("#", "\$SYS/broker/uptime"))
        assertFalse(MqttTopics.matches("home/#/x", "home/a/x"))
        assertTrue(MqttTopics.matches("\$share/phones/home/pc/wake", "home/pc/wake"))
    }

    @Test
    fun reconnectDelayBacksOffExponentiallyWithCap() {
        assertEquals(1L, MqttReconnectPolicy.delaySeconds(0))
        assertEquals(2L, MqttReconnectPolicy.delaySeconds(1))
        assertEquals(64L, MqttReconnectPolicy.delaySeconds(6))
        assertEquals(MqttReconnectPolicy.MAX_DELAY_SECONDS, MqttReconnectPolicy.delaySeconds(7))
        assertEquals(MqttReconnectPolicy.MAX_DELAY_SECONDS, MqttReconnectPolicy.delaySeconds(1000))
        assertEquals(1L, MqttReconnectPolicy.delaySeconds(-5))
    }

    @Test
    fun generatedClientIdIsPortable() {
        val id = MqttReconnectPolicy.generateClientId()
        assertTrue(id.startsWith("wolhome-"))
        assertTrue(id.length <= MqttReconnectPolicy.MAX_PORTABLE_CLIENT_ID_LENGTH)
        assertEquals("wolhome-abcdef123456", MqttReconnectPolicy.generateClientId { "abcdef12-3456-7890" })
    }
}
