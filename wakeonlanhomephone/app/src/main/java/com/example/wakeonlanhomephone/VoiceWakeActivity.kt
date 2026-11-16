package com.example.wakeonlanhomephone

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class VoiceWakeActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Exported for Assistant / shortcuts, but never trust a caller-supplied MAC.
        // Always wake the MAC configured locally in Settings.
        val dispatcher = PcActionDispatcher(
            defaultMacProvider = { MqttConfigManager(this).getConfig().targetMac },
            pcSecretProvider = { MqttConfigManager(this).getConfig().pcSecret }
        )

        lifecycleScope.launch {
            val result = dispatcher.dispatch("WAKE", "Voice/Shortcut")

            when (result) {
                is PcActionResult.Success -> {
                    Toast.makeText(this@VoiceWakeActivity, getString(R.string.toast_wake_signal_sent), Toast.LENGTH_SHORT).show()
                }
                is PcActionResult.Failure -> {
                    Toast.makeText(this@VoiceWakeActivity, result.error, Toast.LENGTH_LONG).show()
                }
            }
            finish()
        }
    }
}
