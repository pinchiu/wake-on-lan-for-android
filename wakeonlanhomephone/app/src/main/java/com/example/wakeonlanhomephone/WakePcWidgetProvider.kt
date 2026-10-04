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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 桌面微型小工具 (App Widget) 提供者。
 * 讓使用者可在 Android 桌面上點擊單一按鈕，直接將 Wake-on-LAN 開機封包發送至 App 設定之目標主機。
 */
class WakePcWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_WAKE_PC = "com.example.wakeonlanhomephone.ACTION_WAKE_PC"

        fun createUpdatePendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, WakePcWidgetProvider::class.java).apply {
                action = ACTION_WAKE_PC
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

            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            views.setOnClickPendingIntent(R.id.widget_btn_action, pendingIntent)

            val config = MqttConfigManager(context).getConfig()
            if (config.targetMac.isNotBlank()) {
                views.setTextViewText(R.id.widget_status, "目標: ${config.targetMac}")
            } else {
                views.setTextViewText(R.id.widget_status, context.getString(R.string.widget_default_hint))
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
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val config = MqttConfigManager(context).getConfig()
                    val targetMac = config.targetMac.trim()
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val componentName = ComponentName(context, WakePcWidgetProvider::class.java)
                    val views = RemoteViews(context.packageName, R.layout.widget_wake_pc)
                    val pendingIntent = createUpdatePendingIntent(context)
                    views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
                    views.setOnClickPendingIntent(R.id.widget_btn_action, pendingIntent)

                    if (targetMac.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "未設定目標電腦 MAC，請先進入 App 設定", Toast.LENGTH_LONG).show()
                        }
                        views.setTextViewText(R.id.widget_status, context.getString(R.string.widget_no_mac_hint))
                        appWidgetManager.updateAppWidget(componentName, views)
                        AppLogger.log("[WIDGET] 點擊開機小工具失敗：尚未設定目標 MAC 地址")
                        return@launch
                    }

                    // 透過深模組 PcActionDispatcher 發送開機封包
                    val dispatcher = PcActionDispatcher(
                        defaultMacProvider = { targetMac }
                    )
                    val result = dispatcher.dispatch("WAKE", "Widget")

                    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    views.setTextViewText(R.id.widget_status, "已發送 $timeFormat ($targetMac)")
                    appWidgetManager.updateAppWidget(componentName, views)

                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "已發送開機封包至 $targetMac", Toast.LENGTH_SHORT).show()
                    }
                    AppLogger.log("[WIDGET] 已發送喚醒封包至 $targetMac (${result.toProtocolString()})")
                } catch (e: Exception) {
                    AppLogger.log("[WIDGET] 小工具發送異常: ${e.message}")
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
