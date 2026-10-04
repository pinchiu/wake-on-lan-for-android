package com.example.wakeonlanhomephone

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

sealed class PcActionResult {
    data class Success(val message: String) : PcActionResult()
    data class Failure(val error: String) : PcActionResult()

    val isSuccess: Boolean
        get() = this is Success

    fun toProtocolString(): String = when (this) {
        is Success -> "SUCCESS: $message"
        is Failure -> "ERROR: $error"
    }
}

class PcActionDispatcher(
    private val defaultMacProvider: () -> String = { "" },
    private val defaultBroadcastIp: String = "255.255.255.255",
    private val pcCommandPort: Int = 9877,
    private val wolPort: Int = 9
) {
    companion object {
        private const val TAG = "PcActionDispatcher"
        private val MAC_REGEX = Regex("^([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}$")
        private val IPV4_REGEX = Regex("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$")
        private val KNOWN_POWER_ACTIONS = setOf("SHUTDOWN", "REBOOT", "SLEEP", "HIBERNATE")
    }

    suspend fun dispatch(rawCommand: String, source: String = "local"): PcActionResult = withContext(Dispatchers.IO) {
        val trimmed = rawCommand.trim()
        if (trimmed.isEmpty()) {
            return@withContext PcActionResult.Failure("Empty command received")
        }

        val (action, payload) = parseCommand(trimmed)
        AppLogger.log("[$source] Received [$action] with payload [$payload]")

        val result = when (action) {
            "WAKE", "ON", "MAC" -> handleWake(payload)
            in KNOWN_POWER_ACTIONS -> handlePowerCommand(action.lowercase(), payload)
            else -> PcActionResult.Failure("Unknown action '$action'")
        }

        when (result) {
            is PcActionResult.Success -> AppLogger.log("Action Success: ${result.message}")
            is PcActionResult.Failure -> AppLogger.log("Action Error: ${result.error}")
        }

        result
    }

    fun parseCommand(raw: String): Pair<String, String> {
        val trimmed = raw.trim()
        val firstColon = trimmed.indexOf(':')
        val firstComma = trimmed.indexOf(',')
        val delimiterIndex = when {
            firstColon != -1 && firstComma != -1 -> minOf(firstColon, firstComma)
            firstColon != -1 -> firstColon
            firstComma != -1 -> firstComma
            else -> -1
        }

        if (delimiterIndex != -1) {
            val potentialAction = trimmed.substring(0, delimiterIndex).trim().uppercase()
            val payload = trimmed.substring(delimiterIndex + 1).trim()
            return if (potentialAction in KNOWN_POWER_ACTIONS || potentialAction == "WAKE" || potentialAction == "ON" || potentialAction == "MAC") {
                potentialAction to payload
            } else if (isValidMac(trimmed)) {
                "WAKE" to trimmed
            } else {
                potentialAction to payload
            }
        }

        return when {
            isValidMac(trimmed) -> "WAKE" to trimmed
            trimmed.equals("WAKE", ignoreCase = true) || trimmed.equals("ON", ignoreCase = true) -> "WAKE" to ""
            else -> trimmed.uppercase() to ""
        }
    }

    private fun handleWake(payload: String): PcActionResult {
        val macToUse = when {
            payload.isNotEmpty() && isValidMac(payload) -> payload
            payload.isEmpty() -> defaultMacProvider().trim()
            else -> return PcActionResult.Failure("Invalid MAC address '$payload'")
        }

        if (macToUse.isEmpty()) {
            return PcActionResult.Failure("Target MAC is not configured")
        }
        if (!isValidMac(macToUse)) {
            return PcActionResult.Failure("Configured default MAC '$macToUse' is invalid")
        }

        val resultMsg = WolUtil.sendMagicPacket(macToUse, defaultBroadcastIp, wolPort)
        return if (resultMsg.startsWith("Send failed") || resultMsg.startsWith("Invalid MAC")) {
            PcActionResult.Failure(resultMsg)
        } else {
            PcActionResult.Success("WoL sent to $macToUse ($resultMsg)")
        }
    }

    private fun handlePowerCommand(command: String, targetIp: String): PcActionResult {
        if (!isValidIpv4(targetIp)) {
            return PcActionResult.Failure("Invalid target IP '$targetIp'")
        }

        return try {
            val commandBytes = command.toByteArray()
            val address = InetAddress.getByName(targetIp)
            val packet = DatagramPacket(commandBytes, commandBytes.size, address, pcCommandPort)
            DatagramSocket().use { socket ->
                socket.send(packet)
            }
            PcActionResult.Success("Command '$command' sent to $targetIp:$pcCommandPort")
        } catch (e: Exception) {
            AppLogger.log("Failed to send command to PC at $targetIp: ${e.message}")
            PcActionResult.Failure("Send failed: ${e.message}")
        }
    }

    fun isValidMac(mac: String): Boolean = MAC_REGEX.matches(mac.trim())

    fun isValidIpv4(ip: String): Boolean = IPV4_REGEX.matches(ip.trim())
}
