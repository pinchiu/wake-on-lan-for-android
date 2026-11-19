package com.example.wakeonwanremotephone

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
import kotlinx.coroutines.withTimeout
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Remote Wake Widget provider.
 * Allows users to remotely wake up the target computer with a single tap on the Android home screen.
 */
class RemoteWakeWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REMOTE_WAKE = "com.example.wakeonwanremotephone.ACTION_REMOTE_WAKE"
        const val EXTRA_WIDGET_TOKEN = "com.example.wakeonwanremotephone.EXTRA_WIDGET_TOKEN"
        private const val KEY_WIDGET_TOKEN = "remote_widget_token"
        private const val WIDGET_TIMEOUT_MS = 7_500L

        fun createPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, RemoteWakeWidgetProvider::class.java).apply {
                action = ACTION_REMOTE_WAKE
                putExtra(EXTRA_WIDGET_TOKEN, widgetToken(context))
            }
            return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        /**
         * Per-install token that lets only the widget's own PendingIntent trigger
         * a wake. The receiver stays exported for APPWIDGET_UPDATE, but a custom
         * ACTION_REMOTE_WAKE broadcast without this token is ignored.
         */
        @Synchronized
        private fun widgetToken(context: Context): String {
            val prefs = context.getSharedPreferences(
                DeviceProfileManager.PREFS_NAME,
                Context.MODE_PRIVATE
            )
            val existing = prefs.getString(KEY_WIDGET_TOKEN, "").orEmpty().trim()
            if (existing.length == 32 && existing.all { it in "0123456789abcdef" }) {
                return existing
            }
            val token = CommandAuth.generateSecret()
            prefs.edit().putString(KEY_WIDGET_TOKEN, token).apply()
            return token
        }

        /** Constant-time comparison so timing cannot be used to recover the token. */
        private fun tokenMatches(provided: String?, expected: String): Boolean {
            if (provided.isNullOrEmpty()) return false
            return MessageDigest.isEqual(
                provided.toByteArray(StandardCharsets.UTF_8),
                expected.toByteArray(StandardCharsets.UTF_8)
            )
        }

        fun updateWidgetViews(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val localized = AppLocale.wrap(context)
            val views = RemoteViews(context.packageName, R.layout.widget_remote_wake)
            val pendingIntent = createPendingIntent(context)

            views.setOnClickPendingIntent(R.id.widget_remote_root, pendingIntent)
            views.setOnClickPendingIntent(R.id.widget_remote_btn, pendingIntent)

            val manager = DeviceProfileManager(context)
            val device = manager.getSelectedDevice()

            if (device != null && device.mac.isNotBlank()) {
                views.setTextViewText(
                    R.id.widget_remote_title,
                    localized.getString(R.string.widget_remote_device_title, device.name)
                )
                views.setTextViewText(
                    R.id.widget_remote_status,
                    localized.getString(R.string.widget_remote_target_mac, device.mac)
                )
            } else {
                views.setTextViewText(R.id.widget_remote_title, localized.getString(R.string.widget_remote_name))
                views.setTextViewText(R.id.widget_remote_status, localized.getString(R.string.widget_remote_hint))
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        for (id in appWidgetIds) {
            updateWidgetViews(context, appWidgetManager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == ACTION_REMOTE_WAKE) {
            // Reject broadcasts from other apps: only our PendingIntent carries
            // the private per-install token.
            if (!tokenMatches(intent.getStringExtra(EXTRA_WIDGET_TOKEN), widgetToken(context))) {
                return
            }
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                val localized = AppLocale.wrap(context)
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, RemoteWakeWidgetProvider::class.java)
                val views = RemoteViews(context.packageName, R.layout.widget_remote_wake)
                try {
                    val manager = DeviceProfileManager(context)
                    val device = manager.getSelectedDevice()
                    val pendingIntent = createPendingIntent(context)
                    views.setOnClickPendingIntent(R.id.widget_remote_root, pendingIntent)
                    views.setOnClickPendingIntent(R.id.widget_remote_btn, pendingIntent)

                    if (device == null || device.mac.isBlank()) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(localized, localized.getString(R.string.widget_no_target_toast), Toast.LENGTH_LONG).show()
                        }
                        views.setTextViewText(R.id.widget_remote_status, localized.getString(R.string.widget_remote_no_device))
                        appWidgetManager.updateAppWidget(componentName, views)
                        return@launch
                    }

                    val isLocalLan = manager.isLocalLanMode()
                    val helperIpv6 = manager.getHelperIpv6()

                    if (!isLocalLan && helperIpv6.isBlank()) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(localized, localized.getString(R.string.widget_no_helper_ipv6), Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }

                    // Bound the work below Android's ~10 second BroadcastReceiver ANR limit.
                    val resultMsg = withTimeout(WIDGET_TIMEOUT_MS) {
                        if (isLocalLan) {
                            RemoteNetworkUtil.sendLocalMagicPacket(device.mac)
                        } else {
                            // Blank secret = bare command; LAN/Tailscale accepts it directly.
                            val helperSecret = SecuritySettings(context).getHelperSecret()
                            RemoteNetworkUtil.sendTcpCommand(
                                helperIpv6,
                                "WAKE:${device.mac}",
                                helperSecret,
                                connectTimeoutMs = 3000,
                                readTimeoutMs = 3000
                            )
                        }
                    }

                    if (RemoteNetworkUtil.isErrorResult(resultMsg)) {
                        views.setTextViewText(
                            R.id.widget_remote_status,
                            localized.getString(R.string.widget_send_failed, resultMsg)
                        )
                        appWidgetManager.updateAppWidget(componentName, views)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                localized,
                                localized.getString(R.string.widget_send_failed, resultMsg),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        return@launch
                    }

                    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    views.setTextViewText(
                        R.id.widget_remote_title,
                        localized.getString(R.string.widget_remote_device_title, device.name)
                    )
                    views.setTextViewText(
                        R.id.widget_remote_status,
                        localized.getString(R.string.widget_sent_status, timeFormat, device.mac)
                    )
                    appWidgetManager.updateAppWidget(componentName, views)

                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            localized,
                            localized.getString(R.string.widget_wake_sent_toast, device.name, resultMsg),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    val message = e.message ?: ""
                    try {
                        views.setTextViewText(
                            R.id.widget_remote_status,
                            localized.getString(R.string.widget_send_failed, message)
                        )
                        appWidgetManager.updateAppWidget(componentName, views)
                    } catch (_: Exception) {
                        // Widget update is best-effort; still surface the toast.
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            localized,
                            localized.getString(R.string.widget_send_failed, message),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
