package com.example.wakeonlanhomephone

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.wakeonlanhomephone.ui.components.ConfirmPowerDialog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ConfirmPowerDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun confirmingMustNotDismissBeforeTheActionIsSent() {
        var confirmCalls = 0
        var dismissCalls = 0

        composeRule.setContent {
            ConfirmPowerDialog(
                actionName = "電腦關機",
                targetName = "測試電腦",
                targetIp = "192.168.0.11",
                isDestructive = true,
                onConfirm = { confirmCalls++ },
                onDismiss = { dismissCalls++ }
            )
        }

        composeRule.onNodeWithTag("confirm_power_action").performClick()

        assertEquals("確認執行應觸發 onConfirm", 1, confirmCalls)
        assertEquals("確認執行不應先清空父層狀態", 0, dismissCalls)
    }
}
