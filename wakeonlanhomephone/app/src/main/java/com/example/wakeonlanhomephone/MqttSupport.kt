package com.example.wakeonlanhomephone

/**
 * Pure (Android-free) helpers for [MqttWolService]: broker address parsing, topic
 * filter validation/matching and reconnect back-off. Kept separate so they can be unit tested.
 */
data class MqttEndpoint(
    val host: String,
    val port: Int,
    val useSsl: Boolean,
    val useWebSocket: Boolean,
    val webSocketPath: String
) {
    val displayUrl: String
        get() {
            val scheme = when {
                useWebSocket && useSsl -> "wss"
                useWebSocket -> "ws"
                useSsl -> "ssl"
                else -> "tcp"
            }
            val printableHost = if (host.contains(':')) "[$host]" else host
            val path = if (useWebSocket) "/$webSocketPath" else ""
            return "$scheme://$printableHost:$port$path"
        }

    companion object {
        private const val DEFAULT_WEBSOCKET_PATH = "mqtt"

        /**
         * Parses the user-entered broker address. Accepts bare hosts (`broker.local`),
         * `host:port`, IPv6 literals (`[::1]:1883`) and URLs (`ssl://`, `mqtts://`, `ws://`,
         * `wss://host:443/mqtt`). A scheme can only switch SSL / WebSocket on, never off.
         * A port inside [rawHost] wins over [configuredPort]. Returns null when no host is present.
         */
        fun parse(rawHost: String, configuredPort: Int, useSsl: Boolean, protocol: String): MqttEndpoint? {
            var rest = rawHost.trim()
            var ssl = useSsl
            var webSocket = protocol.equals("WebSocket", ignoreCase = true) ||
                protocol.equals("WS", ignoreCase = true) ||
                protocol.equals("WSS", ignoreCase = true)

            val schemeMatch = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*)://").find(rest)
            if (schemeMatch != null) {
                when (schemeMatch.groupValues[1].lowercase()) {
                    "ssl", "tls", "mqtts" -> ssl = true
                    "ws" -> webSocket = true
                    "wss" -> {
                        ssl = true
                        webSocket = true
                    }
                }
                rest = rest.substring(schemeMatch.value.length)
            }

            val slash = rest.indexOf('/')
            val authority = if (slash >= 0) rest.substring(0, slash) else rest
            val path = if (slash >= 0) rest.substring(slash + 1).trim('/') else ""
            val credentialsFree = authority.substringAfterLast('@')

            val host: String
            var urlPort: Int? = null
            if (credentialsFree.startsWith("[")) {
                val end = credentialsFree.indexOf(']')
                if (end < 0) return null
                host = credentialsFree.substring(1, end)
                urlPort = credentialsFree.substring(end + 1).removePrefix(":").toIntOrNull()
            } else if (credentialsFree.count { it == ':' } == 1) {
                host = credentialsFree.substringBefore(':')
                urlPort = credentialsFree.substringAfter(':').toIntOrNull()
            } else {
                host = credentialsFree
            }
            if (host.isBlank()) return null

            val port = urlPort?.takeIf { it in 1..65535 }
                ?: configuredPort.takeIf { it in 1..65535 }
                ?: defaultPort(ssl, webSocket)

            return MqttEndpoint(
                host = host.trim(),
                port = port,
                useSsl = ssl,
                useWebSocket = webSocket,
                webSocketPath = path.ifEmpty { DEFAULT_WEBSOCKET_PATH }
            )
        }

        fun defaultPort(useSsl: Boolean, useWebSocket: Boolean): Int = when {
            useWebSocket && useSsl -> 443
            useWebSocket -> 80
            useSsl -> 8883
            else -> 1883
        }
    }
}

object MqttTopics {
    /** MQTT 3.1.1 §4.7 topic filter rules: `+` fills a whole level, `#` only as the last level. */
    fun isValidFilter(filter: String): Boolean {
        if (filter.isEmpty() || filter.length > 65535 || filter.contains('\u0000')) return false
        val levels = filter.split('/')
        levels.forEachIndexed { index, level ->
            if (level.contains('#') && (level != "#" || index != levels.lastIndex)) return false
            if (level.contains('+') && level != "+") return false
        }
        return true
    }

    fun matches(filter: String, topic: String): Boolean {
        if (!isValidFilter(filter) || topic.isEmpty()) return false
        val filterLevels = filter.split('/')
        val topicLevels = topic.split('/')
        // Wildcards at the first level must not match topics starting with '$' (e.g. $SYS).
        if (topic.startsWith("$") && (filterLevels[0] == "#" || filterLevels[0] == "+")) return false
        filterLevels.forEachIndexed { index, level ->
            when {
                level == "#" -> return true
                index >= topicLevels.size -> return false
                level != "+" && level != topicLevels[index] -> return false
            }
        }
        return filterLevels.size == topicLevels.size
    }
}

object MqttReconnectPolicy {
    const val MAX_DELAY_SECONDS = 120L

    /** Exponential back-off: 1, 2, 4, … seconds, capped at [MAX_DELAY_SECONDS]. */
    fun delaySeconds(attempt: Int): Long {
        val safeAttempt = attempt.coerceIn(0, 16)
        return (1L shl safeAttempt).coerceAtMost(MAX_DELAY_SECONDS)
    }

    /** MQTT 3.1.1 brokers may reject longer client identifiers. */
    const val MAX_PORTABLE_CLIENT_ID_LENGTH = 23

    fun generateClientId(random: () -> String = { java.util.UUID.randomUUID().toString() }): String {
        val suffix = random().replace("-", "").take(12)
        return "wolhome-$suffix".take(MAX_PORTABLE_CLIENT_ID_LENGTH)
    }
}
