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
            val views = RemoteViews(context.packageName, R.layout.widget_remote_wake)
            val pendingIntent = createPendingIntent(context)

            views.setOnClickPendingIntent(R.id.widget_remote_root, pendingIntent)
            views.setOnClickPendingIntent(R.id.widget_remote_btn, pendingIntent)

            val manager = DeviceProfileManager(context)
            val device = manager.getSelectedDevice()

            if (device != null && device.mac.isNotBlank()) {
                views.setTextViewText(R.id.widget_remote_title, "${device.name} 開機")
                views.setTextViewText(R.id.widget_remote_status, "目標: ${device.mac}")
            } else {
                views.setTextViewText(R.id.widget_remote_title, context.getString(R.string.widget_remote_name))
                views.setTextViewText(R.id.widget_remote_status, context.getString(R.string.widget_remote_hint))
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
                            Toast.makeText(context, "尚未設定目標電腦，請先開啟 App 設定", Toast.LENGTH_LONG).show()
                        }
                        views.setTextViewText(R.id.widget_remote_status, context.getString(R.string.widget_remote_no_device))
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
                                Toast.makeText(context, "尚未設定 Helper IPv6 地址", Toast.LENGTH_LONG).show()
                            }
                            return@launch
                        }
                        resultMsg = RemoteNetworkUtil.sendTcpCommand(helperIpv6, "WAKE:${device.mac}")
                    }

                    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    views.setTextViewText(R.id.widget_remote_title, "${device.name} 開機")
                    views.setTextViewText(R.id.widget_remote_status, "已發送 $timeFormat (${device.mac})")
                    appWidgetManager.updateAppWidget(componentName, views)

                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "已發送喚醒至 ${device.name}: $resultMsg", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "發送失敗: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
