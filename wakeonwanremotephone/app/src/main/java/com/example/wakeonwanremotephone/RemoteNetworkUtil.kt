package com.example.wakeonwanremotephone

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Locale

object RemoteNetworkUtil {

    private const val TCP_PORT = 9876
    private const val UDP_PORT = 9877
    private const val CONNECT_TIMEOUT_MS = 5000
    private const val READ_TIMEOUT_MS = 5000
    private const val MAX_RESPONSE_BYTES = 1024

    private val MAC_COLON_PATTERN = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")
    private val MAC_DASH_PATTERN = Regex("^([0-9A-Fa-f]{2}-){5}[0-9A-Fa-f]{2}$")

    /**
     * Every failure string returned by this utility (or by the helper over TCP).
     * Anything else is treated as a success by the UI and the widget.
     */
    private val ERROR_PREFIXES = listOf(
        "Send failed",
        "Address",
        "LAN WoL failed",
        "Direct send failed",
        "Shared ",
        "PC secret is required",
        "Helper secret is required",
        "Failed to sign",
        "Invalid ",
        "PC IP or command cannot be empty",
        "Command sent, but no response",
        "ERROR:"
    )

    /**
     * Sends [command] to the Home Phone helper over TCP 9876.
     *
     * The command is wrapped in a signed envelope when [helperSecret] is set.
     * LAN/Tailscale bare commands remain accepted; a public target without a
     * secret is rejected locally, before any connection is attempted.
     */
    suspend fun sendTcpCommand(
        host: String,
        command: String,
        helperSecret: String,
        connectTimeoutMs: Int = CONNECT_TIMEOUT_MS,
        readTimeoutMs: Int = READ_TIMEOUT_MS
    ): String {
        return withContext(Dispatchers.IO) {
            if (command.contains('\r') || command.contains('\n')) {
                return@withContext "Invalid command format"
            }
            val trimmedHost = host.trim()
            val trimmedCommand = command.trim()
            if (trimmedHost.isEmpty() || trimmedCommand.isEmpty()) {
                return@withContext "Address or command cannot be empty!"
            }

            val targetAddr = try {
                InetAddress.getByName(trimmedHost)
            } catch (e: Exception) {
                return@withContext "Invalid helper address: ${e.message}"
            }
            if (helperSecret.isBlank() && !CommandAuth.isTrustedAddress(targetAddr)) {
                return@withContext "Helper secret is required for a public IP; configure it in Shared secrets"
            }

            val wire = if (helperSecret.isBlank()) {
                trimmedCommand
            } else {
                CommandAuth.sign(trimmedCommand, helperSecret)
                    ?: return@withContext "Failed to sign command"
            }

            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(targetAddr, TCP_PORT), connectTimeoutMs)
                    socket.soTimeout = readTimeoutMs

                    val writer = socket.getOutputStream().bufferedWriter(StandardCharsets.UTF_8)
                    writer.write(wire + "\n")
                    writer.flush()

                    readBoundedLine(socket.getInputStream())
                        ?: "Command sent, but no response from server."
                }
            } catch (e: Exception) {
                "Send failed: ${e.message}"
            }
        }
    }

    /**
     * Reads one response line without trusting the peer for a length limit:
     * at most [MAX_RESPONSE_BYTES] UTF-8 bytes, stopping on `\n` and dropping
     * a trailing `\r`.
     */
    private fun readBoundedLine(input: InputStream): String? {
        val buffer = ByteArrayOutputStream()
        while (buffer.size() < MAX_RESPONSE_BYTES) {
            val next = input.read()
            if (next == -1 || next == '\n'.code) break
            buffer.write(next)
        }
        val bytes = buffer.toByteArray()
        if (bytes.isEmpty()) return null
        val length = if (bytes.last() == '\r'.code.toByte()) bytes.size - 1 else bytes.size
        if (length == 0) return null
        return String(bytes, 0, length, StandardCharsets.UTF_8)
    }

    /**
     * Sends a local LAN Wake-on-LAN magic packet.
     *
     * This stays an unsigned UDP broadcast on purpose: local WoL has no
     * verifier and no secret.
     */
    suspend fun sendLocalMagicPacket(macAddress: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val macBytes = getMacBytes(macAddress) ?: return@withContext "Invalid MAC address format"
                val magicPacket = ByteArray(102).apply {
                    (0..5).forEach { this[it] = 0xFF.toByte() }
                    for (i in 1..16) {
                        macBytes.copyInto(this, i * 6)
                    }
                }

                val broadcastAddr = "255.255.255.255"
                val packet = DatagramPacket(magicPacket, magicPacket.size, InetAddress.getByName(broadcastAddr), 9)
                DatagramSocket().use { socket ->
                    socket.broadcast = true
                    socket.send(packet)
                }
                "Magic Packet broadcasted directly on LAN"
            } catch (e: Exception) {
                "LAN WoL failed: ${e.message}"
            }
        }
    }

    /**
     * Parses a strict six-octet MAC address. Surrounding whitespace is ignored;
     * mixed separators, shorthand octets and stray characters are rejected.
     */
    fun getMacBytes(macStr: String): ByteArray? {
        val trimmed = macStr.trim()
        if (!MAC_COLON_PATTERN.matches(trimmed) && !MAC_DASH_PATTERN.matches(trimmed)) {
            return null
        }
        val bytes = ByteArray(6)
        val hex = trimmed.split(':', '-')
        try {
            for (i in 0..5) {
                bytes[i] = hex[i].toInt(16).toByte()
            }
        } catch (e: NumberFormatException) {
            return null
        }
        return bytes
    }

    /**
     * Sends a power action directly to the PC daemon over UDP 9877.
     *
     * The signed command is the legacy `ACTION:IP` string, wrapped in a signed
     * envelope using [pcSecret] (the daemon's Command Secret). A blank secret
     * is rejected locally for public targets before any datagram is sent.
     */
    suspend fun sendDirectCommandToPC(pcIp: String, action: String, pcSecret: String): String {
        return withContext(Dispatchers.IO) {
            if (pcIp.isBlank() || action.isBlank()) {
                return@withContext "PC IP or command cannot be empty!"
            }
            val command = "${action.trim().uppercase(Locale.ROOT)}:${pcIp.trim()}"
            val address = try {
                InetAddress.getByName(pcIp.trim())
            } catch (e: Exception) {
                return@withContext "Invalid PC IP: ${e.message}"
            }
            if (pcSecret.isBlank() && !CommandAuth.isTrustedAddress(address)) {
                return@withContext "PC secret is required for a public IP; set it once for port forwarding"
            }
            val wire = if (pcSecret.isBlank()) {
                command
            } else {
                CommandAuth.sign(command, pcSecret)
                    ?: return@withContext "Failed to sign command"
            }
            try {
                val commandBytes = wire.toByteArray(StandardCharsets.UTF_8)
                val packet = DatagramPacket(commandBytes, commandBytes.size, address, UDP_PORT)
                DatagramSocket().use { socket -> socket.send(packet) }
                "Direct command '$command' sent to PC"
            } catch (e: Exception) {
                "Direct send failed: ${e.message}"
            }
        }
    }

    /** Returns `true` when [result] is one of the known failure strings. */
    fun isErrorResult(result: String): Boolean {
        val trimmed = result.trim()
        return ERROR_PREFIXES.any { trimmed.startsWith(it) }
    }
}
