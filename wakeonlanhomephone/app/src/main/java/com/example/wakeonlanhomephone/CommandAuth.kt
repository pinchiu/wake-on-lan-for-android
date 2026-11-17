package com.example.wakeonlanhomephone

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Result of verifying a command envelope.
 *
 * [Missing] covers both "no envelope" and "malformed envelope"; both are reported on the wire as
 * `ERROR: authentication required`.
 */
sealed class AuthResult {
    object Ok : AuthResult()
    object Missing : AuthResult()
    object BadSignature : AuthResult()
    object Expired : AuthResult()
    object Replayed : AuthResult()
    object NotConfigured : AuthResult()
}

/**
 * HMAC-SHA256 command envelope used by TCP 9876 and the MQTT command topic.
 *
 * Wire format:
 * ```
 * <command>|ts=<unix-seconds>|nonce=<16 lowercase hex>|sig=<64 lowercase hex>
 * ```
 * `sig = HMAC-SHA256(secret, "<command>|ts=<ts>|nonce=<nonce>")`, lowercase hex.
 *
 * There is intentionally no unsigned fallback: a blank secret always fails verification.
 */
object CommandAuth {
    const val MAX_ENVELOPE_BYTES = 512
    const val TIMESTAMP_WINDOW_SECONDS = 300L
    const val NONCE_HEX_LENGTH = 16
    const val SIGNATURE_HEX_LENGTH = 64

    const val WIRE_ERROR_AUTH_REQUIRED = "ERROR: authentication required"
    const val WIRE_ERROR_AUTH_FAILED = "ERROR: authentication failed"
    const val WIRE_ERROR_NOT_CONFIGURED = "ERROR: authentication not configured"

    private const val HMAC_ALGORITHM = "HmacSHA256"
    private const val TS_PREFIX = "ts="
    private const val NONCE_PREFIX = "nonce="
    private const val SIG_PREFIX = "sig="

    private val secureRandom = SecureRandom()
    private val nonceRegex = Regex("^[0-9a-f]{$NONCE_HEX_LENGTH}$")
    private val tsRegex = Regex("^[0-9]+$")

    /** Signs [command] with a fresh random nonce. Returns null when the secret is blank. */
    fun sign(
        command: String,
        secret: String,
        nowSeconds: Long = System.currentTimeMillis() / 1000
    ): String? {
        val nonceBytes = ByteArray(8)
        secureRandom.nextBytes(nonceBytes)
        return signWithNonce(command, secret, nowSeconds, nonceBytes.toHex())
    }

    /**
     * Deterministic signer used by tests (and by the fixed cross-language vector). Returns null
     * for a blank secret, an empty/ambiguous command, or a non-16-hex nonce.
     */
    fun signWithNonce(command: String, secret: String, ts: Long, nonce: String): String? {
        val trimmedSecret = secret.trim()
        if (trimmedSecret.isEmpty()) return null
        if (command.isEmpty() || command.contains('|')) return null
        if (!nonceRegex.matches(nonce)) return null
        val signInput = signInput(command, ts, nonce)
        val signature = hmacSha256Hex(trimmedSecret, signInput) ?: return null
        return "$signInput|$SIG_PREFIX$signature"
    }

    /**
     * Verifies an envelope.
     *
     * Check order: configuration, syntax, signature, timestamp window, replay. Replay is only
     * recorded after all other checks pass, so forged envelopes cannot poison the nonce cache.
     */
    fun verify(
        envelope: String,
        secret: String,
        nowSeconds: Long = System.currentTimeMillis() / 1000,
        replayGuard: ReplayGuard? = null
    ): AuthResult {
        if (secret.isBlank()) return AuthResult.NotConfigured
        val parsed = parse(envelope) ?: return AuthResult.Missing
        val expected = hmacSha256(secret.trim(), parsed.signInput) ?: return AuthResult.Missing
        val provided = parsed.signatureHex.hexToBytes() ?: return AuthResult.Missing
        if (!MessageDigest.isEqual(expected, provided)) return AuthResult.BadSignature

        if (parsed.timestamp < nowSeconds - TIMESTAMP_WINDOW_SECONDS ||
            parsed.timestamp > nowSeconds + TIMESTAMP_WINDOW_SECONDS
        ) {
            return AuthResult.Expired
        }

        if (replayGuard != null && !replayGuard.checkAndRecord(parsed.nonce, nowSeconds)) {
            return AuthResult.Replayed
        }
        return AuthResult.Ok
    }

    /** Extracts the legacy command field. Call only after [verify] returned [AuthResult.Ok]. */
    fun commandOf(envelope: String): String? = parse(envelope)?.command

    /** Exact TCP wire error for a failed result, or null for [AuthResult.Ok]. */
    fun errorFor(result: AuthResult): String? = when (result) {
        AuthResult.Ok -> null
        AuthResult.Missing -> WIRE_ERROR_AUTH_REQUIRED
        AuthResult.BadSignature, AuthResult.Expired, AuthResult.Replayed -> WIRE_ERROR_AUTH_FAILED
        AuthResult.NotConfigured -> WIRE_ERROR_NOT_CONFIGURED
    }

    /** Stable, secret-free rejection reason suitable for logs. */
    fun reasonFor(result: AuthResult): String = when (result) {
        AuthResult.Ok -> "ok"
        AuthResult.Missing -> "missing or malformed envelope"
        AuthResult.BadSignature -> "bad signature"
        AuthResult.Expired -> "expired timestamp"
        AuthResult.Replayed -> "replayed nonce"
        AuthResult.NotConfigured -> "secret not configured"
    }

    /**
     * True for loopback, RFC1918, link-local, IPv6 ULA and the Tailscale/CGNAT range.
     * Private LAN and Tailscale commands do not need an HMAC envelope.
     */
    fun isTrustedAddress(address: java.net.InetAddress): Boolean {
        if (address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress) {
            return true
        }
        val bytes = address.address
        if (bytes.size == 4) {
            val first = bytes[0].toInt() and 0xFF
            val second = bytes[1].toInt() and 0xFF
            return first == 100 && second in 64..127
        }
        if (bytes.size == 16) {
            // IPv4-mapped IPv6 (::ffff:a.b.c.d) is common on dual-stack sockets.
            val mapped = bytes.take(10).all { it == 0.toByte() } &&
                bytes[10] == 0xFF.toByte() && bytes[11] == 0xFF.toByte()
            if (mapped) {
                val first = bytes[12].toInt() and 0xFF
                val second = bytes[13].toInt() and 0xFF
                return first == 100 && second in 64..127 ||
                    first == 10 || first == 192 && second == 168 ||
                    first == 172 && second in 16..31 ||
                    first == 169 && second == 254 ||
                    first == 127
            }
            val first = bytes[0].toInt() and 0xFF
            // fc00::/7 unique-local, which includes Tailscale's fd7a:115c:a1e0::/48.
            return (first and 0xFE) == 0xFC
        }
        return false
    }

    /** 32 lowercase hex characters from a CSPRNG (16 random bytes). */
    fun generateSecret(): String {
        val bytes = ByteArray(16)
        secureRandom.nextBytes(bytes)
        return bytes.toHex()
    }

    private data class ParsedEnvelope(
        val command: String,
        val timestamp: Long,
        val nonce: String,
        val signatureHex: String,
        val signInput: String
    )

    private fun parse(envelope: String): ParsedEnvelope? {
        if (envelope.isEmpty()) return null
        if (envelope.toByteArray(StandardCharsets.UTF_8).size > MAX_ENVELOPE_BYTES) return null

        // Exact field set and order: command|ts=..|nonce=..|sig=..
        val parts = envelope.split('|')
        if (parts.size != 4) return null

        val command = parts[0]
        if (command.isEmpty()) return null

        if (!parts[1].startsWith(TS_PREFIX)) return null
        val tsText = parts[1].substring(TS_PREFIX.length)
        if (!tsRegex.matches(tsText)) return null
        val timestamp = tsText.toLongOrNull() ?: return null

        if (!parts[2].startsWith(NONCE_PREFIX)) return null
        val nonce = parts[2].substring(NONCE_PREFIX.length)
        if (!nonceRegex.matches(nonce)) return null

        if (!parts[3].startsWith(SIG_PREFIX)) return null
        val signatureHex = parts[3].substring(SIG_PREFIX.length)
        if (signatureHex.length != SIGNATURE_HEX_LENGTH) return null
        if (!signatureHex.all { it.isHexDigit() }) return null

        return ParsedEnvelope(
            command = command,
            timestamp = timestamp,
            nonce = nonce,
            signatureHex = signatureHex,
            signInput = signInput(command, timestamp, nonce)
        )
    }

    private fun signInput(command: String, ts: Long, nonce: String): String =
        "$command|$TS_PREFIX$ts|$NONCE_PREFIX$nonce"

    private fun hmacSha256Hex(secret: String, input: String): String? {
        val mac = hmacSha256(secret, input) ?: return null
        return mac.toHex()
    }

    private fun hmacSha256(secret: String, input: String): ByteArray? = try {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), HMAC_ALGORITHM))
        mac.doFinal(input.toByteArray(StandardCharsets.UTF_8))
    } catch (_: Exception) {
        null
    }

    private fun Char.isHexDigit(): Boolean =
        this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
}

/**
 * Process-wide replay cache shared by the TCP and MQTT adapters.
 *
 * Both transports verify the same `helperSecret`; a per-service cache would let an attacker
 * capture an MQTT envelope and replay it over TCP within the timestamp window. Services run
 * in the same app process, so one synchronized cache closes that cross-transport gap.
 */
object SharedReplayGuard : ReplayGuard()

/**
 * In-memory nonce replay cache: nonce -> expiry, TTL 600 s, at most [maxEntries] entries.
 * Thread-safe; expired entries are pruned on every check and the oldest entry is evicted when
 * the cache is full.
 */
open class ReplayGuard(
    private val maxEntries: Int = 1024,
    private val ttlSeconds: Long = 600
) {
    private val entries = LinkedHashMap<String, Long>()
    private val lock = Any()
    private val capacity = maxEntries.coerceAtLeast(1)

    /** Returns true when [nonce] is fresh (and records it), false when it is a replay. */
    fun checkAndRecord(
        nonce: String,
        nowSeconds: Long = System.currentTimeMillis() / 1000
    ): Boolean = synchronized(lock) {
        pruneExpired(nowSeconds)
        val key = nonce.lowercase(Locale.ROOT)
        if (entries.containsKey(key)) return false

        while (entries.size >= capacity) {
            val oldest = entries.keys.firstOrNull() ?: break
            entries.remove(oldest)
        }
        entries[key] = nowSeconds + ttlSeconds
        true
    }

    /** Number of live entries (test/diagnostic use). */
    fun size(): Int = synchronized(lock) { entries.size }

    private fun pruneExpired(nowSeconds: Long) {
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().value <= nowSeconds) iterator.remove()
        }
    }
}

private fun ByteArray.toHex(): String {
    val hex = CharArray(size * 2)
    val digits = "0123456789abcdef"
    for (i in indices) {
        val value = this[i].toInt() and 0xFF
        hex[i * 2] = digits[value ushr 4]
        hex[i * 2 + 1] = digits[value and 0x0F]
    }
    return String(hex)
}

private fun String.hexToBytes(): ByteArray? {
    if (length % 2 != 0) return null
    val out = ByteArray(length / 2)
    for (i in out.indices) {
        val high = this[i * 2].hexValue() ?: return null
        val low = this[i * 2 + 1].hexValue() ?: return null
        out[i] = ((high shl 4) or low).toByte()
    }
    return out
}

private fun Char.hexValue(): Int? = when (this) {
    in '0'..'9' -> this - '0'
    in 'a'..'f' -> this - 'a' + 10
    in 'A'..'F' -> this - 'A' + 10
    else -> null
}
