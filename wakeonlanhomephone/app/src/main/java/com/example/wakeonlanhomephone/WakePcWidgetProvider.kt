package com.example.wakeonlanhomephone

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Home screen widget (App Widget) provider.
 * Allows users to trigger Wake-on-LAN packets with a single tap on the Android home screen for the currently active target PC.
 *
 * The receiver stays exported for the system APPWIDGET_UPDATE broadcast, but the wake action is
 * guarded by a private per-install token carried in our own explicit PendingIntent. A third-party
 * broadcast with the custom action (or without the token) is ignored.
 */
class WakePcWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val ACTION_WAKE_PC = "com.example.wakeonlanhomephone.ACTION_WAKE_PC"
        private const val EXTRA_WAKE_TOKEN = "com.example.wakeonlanhomephone.extra.WAKE_TOKEN"
        private const val PREFS_NAME = "widget_security"
        private const val KEY_WAKE_TOKEN = "wake_token"

        private val tokenLock = Any()

        private fun getOrCreateWakeToken(context: Context): String {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val existing = prefs.getString(KEY_WAKE_TOKEN, null)
            if (!existing.isNullOrBlank()) return existing

            synchronized(tokenLock) {
                val current = prefs.getString(KEY_WAKE_TOKEN, null)
                if (!current.isNullOrBlank()) return current
                val generated = UUID.randomUUID().toString() + UUID.randomUUID().toString()
                prefs.edit().putString(KEY_WAKE_TOKEN, generated).apply()
                return generated
            }
        }

        private fun hasValidWakeToken(context: Context, intent: Intent): Boolean {
            val provided = intent.getStringExtra(EXTRA_WAKE_TOKEN) ?: return false
            val expected = getOrCreateWakeToken(context)
            return MessageDigest.isEqual(
                provided.toByteArray(Charsets.UTF_8),
                expected.toByteArray(Charsets.UTF_8)
            )
        }

        fun createUpdatePendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, WakePcWidgetProvider::class.java).apply {
                action = ACTION_WAKE_PC
                putExtra(EXTRA_WAKE_TOKEN, getOrCreateWakeToken(context))
            }
            return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        fun updateWidgetViews(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_wake_pc)
            val pendingIntent = createUpdatePendingIntent(context)
            val localized = AppLocale.wrap(context)

            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            views.setOnClickPendingIntent(R.id.widget_btn_action, pendingIntent)

            val deviceManager = DeviceProfileManager(context)
            val selectedDevice = deviceManager.getSelectedDevice()

            if (selectedDevice != null && selectedDevice.mac.isNotBlank()) {
                views.setTextViewText(R.id.widget_title, localized.getString(R.string.widget_wake_title, selectedDevice.name))
                views.setTextViewText(R.id.widget_status, localized.getString(R.string.widget_status_target, selectedDevice.mac))
            } else {
                val config = MqttConfigManager(context).getConfig()
                if (config.targetMac.isNotBlank()) {
                    views.setTextViewText(R.id.widget_title, localized.getString(R.string.shortcut_wake_pc_short))
                    views.setTextViewText(R.id.widget_status, localized.getString(R.string.widget_status_target, config.targetMac))
                } else {
                    views.setTextViewText(R.id.widget_title, localized.getString(R.string.shortcut_wake_pc_short))
                    views.setTextViewText(R.id.widget_status, localized.getString(R.string.widget_default_hint))
                }
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        for (appWidgetId in appWidgetIds) {
            updateWidgetViews(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == ACTION_WAKE_PC) {
            if (!hasValidWakeToken(context, intent)) {
                AppLogger.log("[WIDGET] Ignored wake action without a valid private token")
                return
            }
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val localized = AppLocale.wrap(context)
                    val deviceManager = DeviceProfileManager(context)
                    val selectedDevice = deviceManager.getSelectedDevice()
                    val targetMac = selectedDevice?.mac?.trim()?.ifBlank { null }
                        ?: MqttConfigManager(context).getConfig().targetMac.trim()
                    val deviceName = selectedDevice?.name ?: localized.getString(R.string.widget_default_target_name)

                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val componentName = ComponentName(context, WakePcWidgetProvider::class.java)
                    val views = RemoteViews(context.packageName, R.layout.widget_wake_pc)
                    val pendingIntent = createUpdatePendingIntent(context)
                    views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
                    views.setOnClickPendingIntent(R.id.widget_btn_action, pendingIntent)

                    if (targetMac.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, localized.getString(R.string.widget_no_mac_toast), Toast.LENGTH_LONG).show()
                        }
                        views.setTextViewText(R.id.widget_title, localized.getString(R.string.shortcut_wake_pc_short))
                        views.setTextViewText(R.id.widget_status, localized.getString(R.string.widget_no_mac_hint))
                        appWidgetManager.updateAppWidget(componentName, views)
                        AppLogger.log("[WIDGET] Tap failed: Target MAC address is not configured")
                        return@launch
                    }

                    // Dispatch wake command via the deep module PcActionDispatcher
                    val dispatcher = PcActionDispatcher(
                        defaultMacProvider = { targetMac },
                        pcSecretProvider = { MqttConfigManager(context).getConfig().pcSecret }
                    )
                    val result = dispatcher.dispatch("WAKE:$targetMac", "Widget")

                    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    views.setTextViewText(R.id.widget_title, localized.getString(R.string.widget_wake_title, deviceName))
                    when (result) {
                        is PcActionResult.Success -> {
                            views.setTextViewText(
                                R.id.widget_status,
                                localized.getString(R.string.widget_status_sent, timeFormat, targetMac)
                            )
                            appWidgetManager.updateAppWidget(componentName, views)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    localized.getString(R.string.widget_toast_sent, deviceName, targetMac),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            AppLogger.log("[WIDGET] Sent wake packet to $deviceName ($targetMac) (${result.toProtocolString()})")
                        }
                        is PcActionResult.Failure -> {
                            val message = localized.getString(R.string.widget_toast_failed, result.error)
                            views.setTextViewText(R.id.widget_status, message)
                            appWidgetManager.updateAppWidget(componentName, views)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            }
                            AppLogger.log("[WIDGET] Wake failed for $deviceName ($targetMac): ${result.error}")
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.log("[WIDGET] Widget error: ${e.message}")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, AppLocale.wrap(context).getString(R.string.widget_toast_failed, e.message), Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
