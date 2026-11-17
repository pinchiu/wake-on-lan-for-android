package com.example.wakeonlanhomephone

import android.content.Context
import android.content.SharedPreferences

/**
 * Public peers remembered after their first HMAC-authenticated command (TOFU).
 *
 * LAN and Tailscale sources never need an entry. Only a public IPv6/IPv4 source that
 * completed the first authenticated contact is remembered here, so later commands from
 * that source do not need to be signed again.
 */
class TrustedPeers(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        const val PREFS_NAME = "trusted_peers"
        const val KEY_PUBLIC_PEERS = "public_peers"
    }

    fun contains(address: String): Boolean {
        val normalized = address.trim()
        return normalized.isNotEmpty() && prefs.getStringSet(KEY_PUBLIC_PEERS, emptySet())
            ?.contains(normalized) == true
    }

    fun add(address: String) {
        val normalized = address.trim()
        if (normalized.isEmpty()) return
        val current = prefs.getStringSet(KEY_PUBLIC_PEERS, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (current.add(normalized)) {
            prefs.edit().putStringSet(KEY_PUBLIC_PEERS, current).apply()
        }
    }
}
