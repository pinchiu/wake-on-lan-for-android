package com.example.wakeonwanremotephone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandAuthTest {

    private val secret = "0123456789abcdef0123456789abcdef"
    private val otherSecret = "fedcba9876543210fedcba9876543210"
    private val command = "SHUTDOWN:192.168.1.10"
    private val timestamp = 1_700_000_000L
    private val nonce = "0011223344556677"
    private val signature =
        "6582b1c23ef97498af3f91ade1a74ce60f335dae38a2aa51f403254505fbfbdc"
    private val envelope =
        "SHUTDOWN:192.168.1.10|ts=1700000000|nonce=0011223344556677|sig=$signature"

    @Test
    fun fixedVector_producesExactEnvelopeAndSignature() {
        val signed = CommandAuth.sign(command, secret, timestamp, nonce)

        assertEquals(envelope, signed)
        assertEquals(signature, signed!!.substringAfter("sig="))
        assertEquals(64, signature.length)
    }

    @Test
    fun fixedVector_verifies() {
        assertSame(AuthResult.Ok, CommandAuth.verify(envelope, secret, timestamp))
    }

    @Test
    fun surroundingWhitespaceInSecretIsIgnored() {
        val signed = CommandAuth.sign(command, "  $secret  ", timestamp, nonce)

        assertEquals(envelope, signed)
        assertSame(AuthResult.Ok, CommandAuth.verify(envelope, " $secret ", timestamp))
    }

    @Test
    fun roundTrip_withRealClockVerifies() {
        val signed = CommandAuth.sign("WAKE:AA:BB:CC:DD:EE:FF", secret)
        assertNotNull(signed)
        assertSame(AuthResult.Ok, CommandAuth.verify(signed!!, secret))
    }

    @Test
    fun timestampWindow_acceptsEdgesAndRejectsOutside() {
        assertSame(AuthResult.Ok, CommandAuth.verify(envelope, secret, timestamp + 300))
        assertSame(AuthResult.Ok, CommandAuth.verify(envelope, secret, timestamp - 300))
        assertSame(AuthResult.Expired, CommandAuth.verify(envelope, secret, timestamp + 301))
        assertSame(AuthResult.Expired, CommandAuth.verify(envelope, secret, timestamp - 301))
    }

    @Test
    fun tamperedCommand_isRejected() {
        val tampered = envelope.replace("SHUTDOWN", "REBOOT")
        assertSame(AuthResult.BadSignature, CommandAuth.verify(tampered, secret, timestamp))
    }

    @Test
    fun tamperedTimestamp_isRejectedEvenInsideWindow() {
        val tampered = envelope.replace("ts=1700000000", "ts=1700000100")
        assertSame(AuthResult.BadSignature, CommandAuth.verify(tampered, secret, timestamp))
    }

    @Test
    fun tamperedNonce_isRejected() {
        val tampered = envelope.replace("nonce=0011223344556677", "nonce=0011223344556678")
        assertSame(AuthResult.BadSignature, CommandAuth.verify(tampered, secret, timestamp))
    }

    @Test
    fun tamperedSignature_isRejected() {
        val tampered = envelope.dropLast(1) + (if (signature.last() == 'c') 'd' else 'c')
        assertSame(AuthResult.BadSignature, CommandAuth.verify(tampered, secret, timestamp))
    }

    @Test
    fun wrongSecret_isRejected() {
        assertSame(AuthResult.BadSignature, CommandAuth.verify(envelope, otherSecret, timestamp))
    }

    @Test
    fun blankSecret_isNotConfigured() {
        assertSame(AuthResult.NotConfigured, CommandAuth.verify(envelope, "", timestamp))
        assertSame(AuthResult.NotConfigured, CommandAuth.verify(envelope, "   ", timestamp))
    }

    @Test
    fun signerRefusesMissingInputs() {
        assertNull(CommandAuth.sign(command, "", timestamp, nonce))
        assertNull(CommandAuth.sign(command, "   ", timestamp, nonce))
        assertNull(CommandAuth.sign("   ", secret, timestamp, nonce))
        assertNull(CommandAuth.sign(command, secret, timestamp, "001122334455667"))
        assertNull(CommandAuth.sign(command, secret, timestamp, "00112233445566GG"))
        assertNull(CommandAuth.sign("A".repeat(CommandAuth.MAX_ENVELOPE_BYTES), secret, timestamp, nonce))
    }

    @Test
    fun malformedEnvelopes_returnMissing() {
        val malformed = listOf(
            "",
            "WAKE",
            "WAKE|ts=1700000000|nonce=0011223344556677",
            "$envelope|extra=1",
            "|ts=1700000000|nonce=0011223344556677|sig=$signature",
            "WAKE|ts=abc|nonce=0011223344556677|sig=$signature",
            "WAKE|ts=+1700000000|nonce=0011223344556677|sig=$signature",
            "WAKE|ts=1700000000|nonce=001122334455667|sig=$signature",
            "WAKE|ts=1700000000|nonce=00112233445566GG|sig=$signature",
            "WAKE|ts=1700000000|nonce=0011223344556677|sig=abc",
            "WAKE|ts=1700000000|nonce=0011223344556677|sig=" + "a".repeat(63),
            "WAKE|ts=1700000000|nonce=0011223344556677|sig=" + "A".repeat(64),
            "WAKE|nonce=0011223344556677|ts=1700000000|sig=$signature",
            "WAKE|ts=1700000000|nonce=0011223344556677|sig=$signature|sig=$signature"
        )

        for (candidate in malformed) {
            assertSame(
                "expected Missing for: $candidate",
                AuthResult.Missing,
                CommandAuth.verify(candidate, secret, timestamp)
            )
        }
    }

    @Test
    fun overlongEnvelope_isRejected() {
        val huge = "A".repeat(CommandAuth.MAX_ENVELOPE_BYTES) + "|ts=1700000000|nonce=$nonce|sig=$signature"
        assertSame(AuthResult.Missing, CommandAuth.verify(huge, secret, timestamp))
    }

    @Test
    fun replayGuard_rejectsSecondUseOfNonce() {
        val guard = ReplayGuard()

        assertSame(AuthResult.Ok, CommandAuth.verify(envelope, secret, timestamp, guard))
        assertSame(AuthResult.Replayed, CommandAuth.verify(envelope, secret, timestamp, guard))
    }

    @Test
    fun replayGuard_rejectsNonceReuseAcrossCommands() {
        val guard = ReplayGuard()
        val wake = CommandAuth.sign("WAKE", secret, timestamp, nonce)!!
        val reboot = CommandAuth.sign("REBOOT:192.168.1.10", secret, timestamp, nonce)!!

        assertSame(AuthResult.Ok, CommandAuth.verify(wake, secret, timestamp, guard))
        assertSame(AuthResult.Replayed, CommandAuth.verify(reboot, secret, timestamp, guard))
    }

    @Test
    fun replayGuard_isOptIn() {
        assertSame(AuthResult.Ok, CommandAuth.verify(envelope, secret, timestamp))
        assertSame(AuthResult.Ok, CommandAuth.verify(envelope, secret, timestamp))
    }

    @Test
    fun replayGuard_doesNotRememberFailedSignatures() {
        val guard = ReplayGuard()
        val tampered = envelope.replace("SHUTDOWN", "REBOOT")

        assertSame(AuthResult.BadSignature, CommandAuth.verify(tampered, secret, timestamp, guard))
        assertSame(AuthResult.Ok, CommandAuth.verify(envelope, secret, timestamp, guard))
    }

    @Test
    fun replayGuard_prunesExpiredNonces() {
        val guard = ReplayGuard(ttlSeconds = 600)

        assertTrue(guard.checkAndRemember(nonce, 1_000))
        assertFalse(guard.checkAndRemember(nonce, 1_599))
        assertTrue(guard.checkAndRemember(nonce, 1_600))
    }

    @Test
    fun replayGuard_evictsOldestEntryAtCapacity() {
        val guard = ReplayGuard(maxEntries = 2)

        assertTrue(guard.checkAndRemember("0000000000000001", 1_000))
        assertTrue(guard.checkAndRemember("0000000000000002", 1_000))
        assertTrue(guard.checkAndRemember("0000000000000003", 1_000))

        assertTrue(guard.checkAndRemember("0000000000000001", 1_001))
    }

    @Test
    fun generateSecret_is32LowercaseHexChars() {
        val generated = CommandAuth.generateSecret()

        assertEquals(32, generated.length)
        assertTrue(generated.all { it in "0123456789abcdef" })
        assertEquals(32, CommandAuth.generateSecret().length)
        assertFalse(generated == CommandAuth.generateSecret())
    }
}
