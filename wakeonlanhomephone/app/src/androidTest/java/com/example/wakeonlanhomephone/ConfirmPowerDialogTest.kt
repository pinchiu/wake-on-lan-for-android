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
                actionName = "Shutdown",
                targetName = "Test PC",
                targetIp = "192.168.0.11",
                isDestructive = true,
                onConfirm = { confirmCalls++ },
                onDismiss = { dismissCalls++ }
            )
        }

        composeRule.onNodeWithTag("confirm_power_action").performClick()

        assertEquals("Confirmation click should trigger onConfirm", 1, confirmCalls)
        assertEquals("Confirmation click should not clear parent state first", 0, dismissCalls)
    }
}
