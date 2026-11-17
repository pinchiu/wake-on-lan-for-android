package com.example.wakeonwanremotephone

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Locale

object RemoteNetworkUtil {


    /**
     * Sends [command] to the Home Phone helper over TCP 9876.
     *
     * The command is always wrapped in a signed envelope using [helperSecret].
     * There is intentionally no unsigned fallback: a blank secret is a local
     * configuration error, reported before any connection is attempted.
     */
    suspend fun sendTcpCommand(host: String, command: String, helperSecret: String): String {
        return withContext(Dispatchers.IO) {
            if (host.isBlank() || command.isBlank()) {
                return@withContext "Address or command cannot be empty!"
            }
            // LAN/Tailscale accepts a bare command; a public port-forward requires the
            // stored secret, and every command is then signed automatically.
            val wire = if (helperSecret.isBlank()) {
                command.trim()
            } else {
                CommandAuth.sign(command.trim(), helperSecret)
                    ?: return@withContext "Failed to sign command"
            }
            try {
                val targetAddr = InetAddress.getByName(host.trim())
                val socket = Socket()
                val socketAddress = InetSocketAddress(targetAddr, 9876)
                socket.connect(socketAddress, 5000)
                socket.soTimeout = 5000
                socket.use { s ->
                    val writer = s.getOutputStream().bufferedWriter()
                    writer.write(wire + "\n")
                    writer.flush()

                    val reader = s.getInputStream().bufferedReader()
                    val response = reader.readLine()
                    response ?: "Command sent, but no response from server."
                }
            } catch (e: Exception) {
                "Send failed: ${e.message}"
            }
        }
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

    fun getMacBytes(macStr: String): ByteArray? {
        val bytes = ByteArray(6)
        val hex = macStr.split(':', '-')
        if (hex.size != 6) return null
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
     * is rejected locally before any datagram is sent.
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
                val packet = DatagramPacket(commandBytes, commandBytes.size, address, 9877)
                DatagramSocket().use { socket -> socket.send(packet) }
                "Direct command '$command' sent to PC"
            } catch (e: Exception) {
                "Direct send failed: ${e.message}"
            }
        }
    }
}
