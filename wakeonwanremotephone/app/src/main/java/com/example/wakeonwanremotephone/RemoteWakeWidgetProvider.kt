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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 外出遙控端桌面小工具 (Remote Wake Widget)。
 * 讓使用者可在 Android 桌面上單鍵遠端喚醒目標電腦。
 */
class RemoteWakeWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REMOTE_WAKE = "com.example.wakeonwanremotephone.ACTION_REMOTE_WAKE"

        fun createPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, RemoteWakeWidgetProvider::class.java).apply {
                action = ACTION_REMOTE_WAKE
            }
            return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
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
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val localized = AppLocale.wrap(context)
                    val manager = DeviceProfileManager(context)
                    val device = manager.getSelectedDevice()
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val componentName = ComponentName(context, RemoteWakeWidgetProvider::class.java)
                    val views = RemoteViews(context.packageName, R.layout.widget_remote_wake)
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

                    val resultMsg: String
                    if (isLocalLan) {
                        resultMsg = RemoteNetworkUtil.sendLocalMagicPacket(device.mac)
                    } else {
                        if (helperIpv6.isBlank()) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(localized, localized.getString(R.string.widget_no_helper_ipv6), Toast.LENGTH_LONG).show()
                            }
                            return@launch
                        }
                        resultMsg = RemoteNetworkUtil.sendTcpCommand(helperIpv6, "WAKE:${device.mac}")
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
                    withContext(Dispatchers.Main) {
                        val localized = AppLocale.wrap(context)
                        Toast.makeText(
                            localized,
                            localized.getString(R.string.widget_send_failed, e.message ?: ""),
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
