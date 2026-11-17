package com.example.wakeonlanhomephone

import org.junit.Assert.*
import org.junit.Test

class CommandAuthTest {

    private val fixedSecret = "0123456789abcdef0123456789abcdef"
    private val fixedCommand = "SHUTDOWN:192.168.1.10"
    private val fixedTs = 1_700_000_000L
    private val fixedNonce = "0011223344556677"
    private val fixedEnvelope =
        "SHUTDOWN:192.168.1.10|ts=1700000000|nonce=0011223344556677|" +
            "sig=6582b1c23ef97498af3f91ade1a74ce60f335dae38a2aa51f403254505fbfbdc"

    @Test
    fun fixedCrossLanguageVectorMatchesExactEnvelope() {
        val envelope = CommandAuth.signWithNonce(fixedCommand, fixedSecret, fixedTs, fixedNonce)

        assertEquals(
            "SHUTDOWN:192.168.1.10|ts=1700000000|nonce=0011223344556677|" +
                "sig=6582b1c23ef97498af3f91ade1a74ce60f335dae38a2aa51f403254505fbfbdc",
            envelope
        )
        assertEquals(AuthResult.Ok, CommandAuth.verify(fixedEnvelope, fixedSecret, fixedTs))
    }

    @Test
    fun fixedVectorSignatureIsStableAndTimestampWindowBoundaryIsInclusive() {
        assertEquals(AuthResult.Ok, CommandAuth.verify(fixedEnvelope, fixedSecret, fixedTs + 300))
        assertEquals(AuthResult.Ok, CommandAuth.verify(fixedEnvelope, fixedSecret, fixedTs - 300))
        assertEquals(AuthResult.Expired, CommandAuth.verify(fixedEnvelope, fixedSecret, fixedTs + 301))
        assertEquals(AuthResult.Expired, CommandAuth.verify(fixedEnvelope, fixedSecret, fixedTs - 301))
    }

    @Test
    fun tamperedSignatureIsRejected() {
        val tampered = fixedEnvelope.dropLast(1) + if (fixedEnvelope.last() == '0') '1' else '0'
        assertEquals(AuthResult.BadSignature, CommandAuth.verify(tampered, fixedSecret, fixedTs))
    }

    @Test
    fun tamperedCommandOrTimestampIsRejected() {
        val tamperedCommand = fixedEnvelope.replace("SHUTDOWN:", "REBOOT:")
        val tamperedTs = fixedEnvelope.replace("ts=1700000000", "ts=1700000001")

        assertEquals(AuthResult.BadSignature, CommandAuth.verify(tamperedCommand, fixedSecret, fixedTs))
        assertEquals(AuthResult.BadSignature, CommandAuth.verify(tamperedTs, fixedSecret, fixedTs))
    }

    @Test
    fun wrongSecretIsRejected() {
        val otherSecret = "fedcba9876543210fedcba9876543210"
        assertEquals(AuthResult.BadSignature, CommandAuth.verify(fixedEnvelope, otherSecret, fixedTs))
    }

    @Test
    fun blankSecretIsRejectedAndSignerRefuses() {
        assertNull(CommandAuth.sign(fixedCommand, ""))
        assertNull(CommandAuth.sign(fixedCommand, "   "))
        assertNull(CommandAuth.signWithNonce(fixedCommand, "", fixedTs, fixedNonce))
        assertEquals(AuthResult.NotConfigured, CommandAuth.verify(fixedEnvelope, "", fixedTs))
        assertEquals(AuthResult.NotConfigured, CommandAuth.verify(fixedEnvelope, " \t\n ", fixedTs))
    }

    @Test
    fun replayedNonceIsRejected() {
        val guard = ReplayGuard(maxEntries = 1024, ttlSeconds = 600)

        assertEquals(AuthResult.Ok, CommandAuth.verify(fixedEnvelope, fixedSecret, fixedTs, guard))
        assertEquals(AuthResult.Replayed, CommandAuth.verify(fixedEnvelope, fixedSecret, fixedTs, guard))
        assertEquals(AuthResult.Replayed, CommandAuth.verify(fixedEnvelope, fixedSecret, fixedTs + 1, guard))
    }

    @Test
    fun expiredNonceIsPrunedFromGuard() {
        val guard = ReplayGuard(maxEntries = 1024, ttlSeconds = 600)

        assertTrue(guard.checkAndRecord(fixedNonce, fixedTs))
        assertFalse(guard.checkAndRecord(fixedNonce, fixedTs + 599))
        // Entry expires at fixedTs + 600; at fixedTs + 600 it is pruned first and accepted.
        assertTrue(guard.checkAndRecord(fixedNonce, fixedTs + 600))
    }

    @Test
    fun replayGuardIsBounded() {
        val guard = ReplayGuard(maxEntries = 2, ttlSeconds = 600)

        assertTrue(guard.checkAndRecord("0000000000000001", 100))
        assertTrue(guard.checkAndRecord("0000000000000002", 100))
        assertTrue(guard.checkAndRecord("0000000000000003", 100))
        assertEquals(2, guard.size())
        // Oldest entry was evicted, so it is accepted again.
        assertTrue(guard.checkAndRecord("0000000000000001", 100))
    }

    @Test
    fun malformedEnvelopesAreMissing() {
        val malformed = listOf(
            "",
            "   ",
            "SHUTDOWN:192.168.1.10",
            "SHUTDOWN:192.168.1.10|ts=1700000000|nonce=0011223344556677",
            "SHUTDOWN:192.168.1.10|ts=1700000000|nonce=0011223344556677|sig=deadbeef",
            "SHUTDOWN:192.168.1.10|ts=abc|nonce=0011223344556677|sig=${
                "0".repeat(64)
            }",
            "SHUTDOWN:192.168.1.10|ts=1700000000|nonce=00112233445566|sig=${"0".repeat(64)}",
            "SHUTDOWN:192.168.1.10|ts=1700000000|nonce=001122334455667Z|sig=${"0".repeat(64)}",
            "SHUTDOWN:192.168.1.10|ts=1700000000|nonce=0011223344556677|sig=${"0".repeat(63)}",
            "SHUTDOWN:192.168.1.10|ts=1700000000|nonce=0011223344556677|sig=${"0".repeat(63)}G",
            "SHUTDOWN:192.168.1.10|ts=1700000000|nonce=0011223344556677|sig=${"0".repeat(64)}|extra=1",
            "SHUTDOWN:192.168.1.10|ts=1700000000|ts=1700000000|nonce=0011223344556677|sig=${
                "0".repeat(64)
            }",
            "|ts=1700000000|nonce=0011223344556677|sig=${"0".repeat(64)}"
        )

        malformed.forEach { envelope ->
            assertEquals(
                "expected Missing for: $envelope",
                AuthResult.Missing,
                CommandAuth.verify(envelope, fixedSecret, fixedTs)
            )
        }
    }

    @Test
    fun overlongEnvelopeIsRejected() {
        val longCommand = "WAKE:" + "A".repeat(CommandAuth.MAX_ENVELOPE_BYTES)
        val envelope = "$longCommand|ts=1700000000|nonce=0011223344556677|sig=${"0".repeat(64)}"
        assertTrue(envelope.toByteArray(Charsets.UTF_8).size > CommandAuth.MAX_ENVELOPE_BYTES)
        assertEquals(AuthResult.Missing, CommandAuth.verify(envelope, fixedSecret, fixedTs))
    }

    @Test
    fun generateSecretIs32LowercaseHex() {
        val first = CommandAuth.generateSecret()
        val second = CommandAuth.generateSecret()

        assertTrue(first.matches(Regex("^[0-9a-f]{32}$")))
        assertTrue(second.matches(Regex("^[0-9a-f]{32}$")))
        assertNotEquals(first, second)
    }

    @Test
    fun signProducesVerifiableEnvelopeWith16HexNonce() {
        val envelope = CommandAuth.sign("WAKE", fixedSecret, fixedTs)
        assertNotNull(envelope)
        val parts = envelope!!.split('|')
        assertEquals(4, parts.size)
        assertTrue(parts[2].removePrefix("nonce=").matches(Regex("^[0-9a-f]{16}$")))
        assertTrue(parts[3].removePrefix("sig=").matches(Regex("^[0-9a-f]{64}$")))
        assertEquals(AuthResult.Ok, CommandAuth.verify(envelope, fixedSecret, fixedTs))
        assertEquals("WAKE", CommandAuth.commandOf(envelope))
    }

    @Test
    fun commandOfExtractsLegacyCommand() {
        assertEquals("SHUTDOWN:192.168.1.10", CommandAuth.commandOf(fixedEnvelope))
        assertEquals("WAKE", CommandAuth.commandOf("WAKE|ts=1700000000|nonce=0011223344556677|sig=${"0".repeat(64)}"))
        assertNull(CommandAuth.commandOf("not an envelope"))
    }

    @Test
    fun wireErrorsMatchSpec() {
        assertEquals("ERROR: authentication required", CommandAuth.errorFor(AuthResult.Missing))
        assertEquals("ERROR: authentication failed", CommandAuth.errorFor(AuthResult.BadSignature))
        assertEquals("ERROR: authentication failed", CommandAuth.errorFor(AuthResult.Expired))
        assertEquals("ERROR: authentication failed", CommandAuth.errorFor(AuthResult.Replayed))
        assertEquals("ERROR: authentication not configured", CommandAuth.errorFor(AuthResult.NotConfigured))
        assertNull(CommandAuth.errorFor(AuthResult.Ok))
    }
    @Test
    fun classifiesPrivateAndTailscaleAddresses() {
        for (address in listOf(
            "127.0.0.1", "10.1.2.3", "172.16.4.5", "192.168.0.13",
            "169.254.10.20", "100.64.0.1", "100.127.255.254",
            "::1", "fe80::1", "fd7a:115c:a1e0::1", "fc00::1"
        )) {
            assertTrue(address, CommandAuth.isTrustedAddress(java.net.InetAddress.getByName(address)))
        }
        for (address in listOf("8.8.8.8", "203.0.113.5", "100.128.0.1", "2001:4860:4860::8888")) {
            assertFalse(address, CommandAuth.isTrustedAddress(java.net.InetAddress.getByName(address)))
        }
    }

}
