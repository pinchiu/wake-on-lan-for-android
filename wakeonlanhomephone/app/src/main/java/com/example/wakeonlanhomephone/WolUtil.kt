package com.example.wakeonlanhomephone

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object WolUtil {
    private const val TAG = "WolUtil"

    fun sendMagicPacket(macAddress: String): String {
        try {
            val macBytes = getMacBytes(macAddress) ?: return "Invalid MAC address format"
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
            return "Magic Packet broadcasted to $broadcastAddr:9"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send Magic Packet", e)
            return "Send failed: ${e.message}"
        }
    }

    private fun getMacBytes(macStr: String): ByteArray? {
        val bytes = ByteArray(6)
        val hex = macStr.split(':', '-')
        if (hex.size != 6) return null
        try {
            for (i in 0..5) { bytes[i] = hex[i].toInt(16).toByte() }
        } catch (e: NumberFormatException) { return null }
        return bytes
    }
}
