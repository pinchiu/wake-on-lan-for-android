package com.example.wakeonlanhomephone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    companion object {
        const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val config = MqttConfigManager(context).getConfig()
        if (!config.autoConnect || config.host.isBlank()) {
            Log.d(TAG, "MQTT auto-connect disabled or host missing; not starting after boot")
            return
        }

        Log.d(TAG, "Boot completed, starting MqttWolService")
        val serviceIntent = Intent(context, MqttWolService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cannot start MqttWolService after boot", e)
            AppLogger.log("Boot: cannot start MQTT service (${e.message})")
        }
    }
}
