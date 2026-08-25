package com.apophuy.altimeter

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.apophuy.altimeter.model.MissingHardware
import com.apophuy.altimeter.ui.HardwareNotice
import com.apophuy.altimeter.ui.theme.AltimeterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HardwareNoticeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun noticeWaitsForSettingsAndDoesNotReturnAfterAcknowledgement() {
        val acknowledged = mutableStateOf<Boolean?>(null)
        val mounted = mutableStateOf(true)
        var acknowledgements = 0
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val title = context.getString(R.string.hardware_notice_title)
        compose.setContent {
            AltimeterTheme {
                if (mounted.value) {
                    HardwareNotice(listOf(MissingHardware.BAROMETER), acknowledged.value) {
                        acknowledgements++
                        acknowledged.value = true
                    }
                }
            }
        }
        compose.onNodeWithText(title).assertDoesNotExist()
        compose.runOnIdle { acknowledged.value = false }
        compose.onNodeWithText(title).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.understood)).performClick()
        compose.onNodeWithText(title).assertDoesNotExist()
        compose.runOnIdle { mounted.value = false }
        compose.runOnIdle { mounted.value = true }
        compose.onNodeWithText(title).assertDoesNotExist()
        assertEquals(1, acknowledgements)
    }
}
