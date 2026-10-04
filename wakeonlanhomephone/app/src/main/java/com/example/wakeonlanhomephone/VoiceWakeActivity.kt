package com.example.wakeonlanhomephone

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VoiceWakeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intentMac = intent?.getStringExtra("EXTRA_MAC")
        val config = MqttConfigManager(this).getConfig()
        val targetMac = when {
            !intentMac.isNullOrBlank() -> intentMac.trim()
            config.targetMac.isNotBlank() -> config.targetMac.trim()
            else -> ""
        }

        if (targetMac.isBlank()) {
            Toast.makeText(this, "尚未設定目標電腦 MAC 地址，請先開啟 App 設定", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val result = WolUtil.sendMagicPacket(targetMac)
            AppLogger.log("Voice/Shortcut WOL: $result (MAC: $targetMac)")

            withContext(Dispatchers.Main) {
                Toast.makeText(this@VoiceWakeActivity, "已發送開機訊號至 $targetMac", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
