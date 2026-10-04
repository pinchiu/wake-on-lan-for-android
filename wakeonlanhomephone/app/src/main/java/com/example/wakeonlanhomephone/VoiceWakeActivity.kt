package com.example.wakeonlanhomephone

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class VoiceWakeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intentMac = intent?.getStringExtra("EXTRA_MAC")
        val dispatcher = PcActionDispatcher(
            defaultMacProvider = {
                if (!intentMac.isNullOrBlank()) intentMac
                else MqttConfigManager(this).getConfig().targetMac
            }
        )

        lifecycleScope.launch {
            val command = if (!intentMac.isNullOrBlank()) "WAKE:$intentMac" else "WAKE"
            val result = dispatcher.dispatch(command, "Voice/Shortcut")

            when (result) {
                is PcActionResult.Success -> {
                    Toast.makeText(this@VoiceWakeActivity, "已發送開機訊號至電腦", Toast.LENGTH_SHORT).show()
                }
                is PcActionResult.Failure -> {
                    Toast.makeText(this@VoiceWakeActivity, result.error, Toast.LENGTH_LONG).show()
                }
            }
            finish()
        }
    }
}
