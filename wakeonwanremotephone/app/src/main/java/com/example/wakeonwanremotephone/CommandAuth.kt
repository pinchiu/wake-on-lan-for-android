package com.example.wakeonwanremotephone

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Command authentication envelope (v1).
 *
 * Wire format:
 * ```
 * <command>|ts=<unix-seconds>|nonce=<16 lowercase hex>|sig=<64 lowercase hex>
 * ```
 * `sig` is the lowercase hex HMAC-SHA256 of `<command>|ts=<ts>|nonce=<nonce>`
 * keyed with the shared secret (UTF-8 bytes).
 *
 * This is a pure-JVM, Android-free module so it can be unit tested on the host.
 * The Home Phone app carries an identical copy by design: the two apps are
 * separate Gradle projects and do not share a module.
 */
object CommandAuth {

    const val MAX_ENVELOPE_BYTES = 512
    const val TIMESTAMP_WINDOW_SECONDS = 300L
    const val NONCE_HEX_LENGTH = 16
    const val SIGNATURE_HEX_LENGTH = 64

    private const val TS_PREFIX = "ts="
    private const val NONCE_PREFIX = "nonce="
    private const val SIG_PREFIX = "sig="
    private const val FIELD_SEPARATOR = '|'

    private val NONCE_PATTERN = Regex("^[0-9a-f]{$NONCE_HEX_LENGTH}$")
    private val SIGNATURE_PATTERN = Regex("^[0-9a-f]{$SIGNATURE_HEX_LENGTH}$")

    private val secureRandom = SecureRandom()

    /**
     * Builds a signed envelope for [command].
     *
     * Returns `null` when the secret is blank (signing is never allowed without
     * a secret), when the command is blank or contains the envelope separator,
     * when [nonce] is not a valid 16-character lowercase hex string, or when the
     * resulting envelope would exceed [MAX_ENVELOPE_BYTES] UTF-8 bytes.
     *
     * [nonce] is injectable only so tests can reproduce a fixed vector.
     */
    fun sign(
        command: String,
        secret: String,
        nowSeconds: Long = System.currentTimeMillis() / 1000,
        nonce: String = randomNonce()
    ): String? {
        if (secret.isBlank()) return null
        if (command.isBlank()) return null
        if (command.contains(FIELD_SEPARATOR)) return null
        if (!NONCE_PATTERN.matches(nonce)) return null

        val signInput = "$command$FIELD_SEPARATOR$TS_PREFIX$nowSeconds" +
            "$FIELD_SEPARATOR$NONCE_PREFIX$nonce"
        val signature = hmacSha256Hex(secret.trim(), signInput)
        val envelope = "$signInput$FIELD_SEPARATOR$SIG_PREFIX$signature"

        if (envelope.toByteArray(StandardCharsets.UTF_8).size > MAX_ENVELOPE_BYTES) {
            return null
        }
        return envelope
    }

    /**
     * Verifies [envelope] against [secret].
     *
     * Replay protection is opt-in: pass a [ReplayGuard] to reject a nonce that
     * was already accepted. Replay checks happen only after the HMAC and the
     * timestamp are valid, so unauthenticated traffic cannot poison the cache.
     */
    fun verify(
        envelope: String,
        secret: String,
        nowSeconds: Long = System.currentTimeMillis() / 1000,
        replayGuard: ReplayGuard? = null
    ): AuthResult {
        if (secret.isBlank()) return AuthResult.NotConfigured
        if (envelope.isBlank()) return AuthResult.Missing
        if (envelope.toByteArray(StandardCharsets.UTF_8).size > MAX_ENVELOPE_BYTES) {
            return AuthResult.Missing
        }

        val parsed = parseEnvelope(envelope) ?: return AuthResult.Missing

        val expected = hmacSha256Hex(secret.trim(), parsed.signInput)
        if (!MessageDigest.isEqual(
                expected.toByteArray(StandardCharsets.UTF_8),
                parsed.signature.toByteArray(StandardCharsets.UTF_8)
            )
        ) {
            return AuthResult.BadSignature
        }

        val age = nowSeconds - parsed.timestamp
        if (age > TIMESTAMP_WINDOW_SECONDS || age < -TIMESTAMP_WINDOW_SECONDS) {
            return AuthResult.Expired
        }

        if (replayGuard != null && !replayGuard.checkAndRemember(parsed.nonce, nowSeconds)) {
            return AuthResult.Replayed
        }

        return AuthResult.Ok
    }

    /** Generates a 32-character lowercase hex secret from a CSPRNG. */
    fun generateSecret(): String {
        val bytes = ByteArray(16)
        secureRandom.nextBytes(bytes)
        return bytes.toHexLower()
    }

    private fun randomNonce(): String {
        val bytes = ByteArray(8)
        secureRandom.nextBytes(bytes)
        return bytes.toHexLower()
    }

    private data class ParsedEnvelope(
        val signInput: String,
        val timestamp: Long,
        val nonce: String,
        val signature: String
    )

    private fun parseEnvelope(envelope: String): ParsedEnvelope? {
        val fields = envelope.split(FIELD_SEPARATOR)
        if (fields.size != 4) return null

        val command = fields[0]
        if (command.isBlank()) return null

        val tsText = fields[1].removePrefix(TS_PREFIX)
        if (!fields[1].startsWith(TS_PREFIX) || tsText.isEmpty() || !tsText.all { it.isDigit() }) {
            return null
        }
        val timestamp = tsText.toLongOrNull() ?: return null

        if (!fields[2].startsWith(NONCE_PREFIX)) return null
        val nonce = fields[2].removePrefix(NONCE_PREFIX)
        if (!NONCE_PATTERN.matches(nonce)) return null

        if (!fields[3].startsWith(SIG_PREFIX)) return null
        val signature = fields[3].removePrefix(SIG_PREFIX)
        if (!SIGNATURE_PATTERN.matches(signature)) return null

        val signInput = "$command$FIELD_SEPARATOR$TS_PREFIX$tsText" +
            "$FIELD_SEPARATOR$NONCE_PREFIX$nonce"
        return ParsedEnvelope(signInput, timestamp, nonce, signature)
    }

    private fun hmacSha256Hex(secret: String, data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(data.toByteArray(StandardCharsets.UTF_8)).toHexLower()
    }

    private fun ByteArray.toHexLower(): String {
        val digits = "0123456789abcdef"
        val output = CharArray(size * 2)
        for (i in indices) {
            val value = this[i].toInt() and 0xFF
            output[i * 2] = digits[value ushr 4]
            output[i * 2 + 1] = digits[value and 0x0F]
        }
        return String(output)
    }
}

/** Outcome of [CommandAuth.verify]. */
sealed class AuthResult {
    data object Ok : AuthResult()
    data object Missing : AuthResult()
    data object BadSignature : AuthResult()
    data object Expired : AuthResult()
    data object Replayed : AuthResult()
    data object NotConfigured : AuthResult()
}

/**
 * Bounded nonce replay cache.
 *
 * Remembers each accepted nonce until `now + ttlSeconds`, prunes expired
 * entries on every check, and evicts the oldest entry when [maxEntries] is
 * exceeded. Not persisted: a restart empties the cache, which is acceptable
 * because the timestamp window is shorter than the TTL.
 */
class ReplayGuard(
    private val maxEntries: Int = 1024,
    private val ttlSeconds: Long = 600
) {
    private val seen = LinkedHashMap<String, Long>()

    /** Returns `true` if [nonce] is fresh (and remembers it), `false` if replayed. */
    @Synchronized
    fun checkAndRemember(
        nonce: String,
        nowSeconds: Long = System.currentTimeMillis() / 1000
    ): Boolean {
        pruneExpired(nowSeconds)
        if (seen.containsKey(nonce)) return false

        seen[nonce] = nowSeconds + ttlSeconds

        while (seen.size > maxEntries) {
            val oldest = seen.keys.firstOrNull() ?: break
            seen.remove(oldest)
        }
        return true
    }

    private fun pruneExpired(nowSeconds: Long) {
        val iterator = seen.entries.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().value <= nowSeconds) {
                iterator.remove()
            }
        }
    }
}
