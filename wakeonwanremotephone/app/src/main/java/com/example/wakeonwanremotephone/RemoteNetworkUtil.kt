package com.example.wakeonwanremotephone

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

object RemoteNetworkUtil {

    suspend fun sendTcpCommand(host: String, command: String): String {
        return withContext(Dispatchers.IO) {
            if (host.isBlank() || command.isBlank()) {
                return@withContext "Address or command cannot be empty!"
            }
            try {
                val targetAddr = InetAddress.getByName(host.trim())
                val socket = Socket()
                val socketAddress = InetSocketAddress(targetAddr, 9876)
                socket.connect(socketAddress, 5000)
                socket.soTimeout = 5000
                socket.use { s ->
                    val writer = s.getOutputStream().bufferedWriter()
                    writer.write(command.trim() + "\n")
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

    suspend fun sendDirectCommandToPC(pcIp: String, command: String): String {
        return withContext(Dispatchers.IO) {
            if (pcIp.isBlank() || command.isBlank()) {
                return@withContext "PC IP or command cannot be empty!"
            }
            try {
                val commandBytes = command.toByteArray()
                val packet = DatagramPacket(commandBytes, commandBytes.size, InetAddress.getByName(pcIp), 9877)
                DatagramSocket().use { socket -> socket.send(packet) }
                "Direct command '$command' sent to PC"
            } catch (e: Exception) {
                "Direct send failed: ${e.message}"
            }
        }
    }
}
