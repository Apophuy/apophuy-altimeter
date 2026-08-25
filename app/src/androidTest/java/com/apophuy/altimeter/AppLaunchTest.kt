package com.apophuy.altimeter

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.view.WindowCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.apophuy.altimeter.model.ThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.io.File
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLaunchTest {
    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun dashboardIsShownWithoutForcingPermissionDialog() {
        dismissHardwareNoticeIfNeeded()
        val label = composeRule.activity.getString(R.string.tab_dashboard)
        composeRule.onNodeWithText(label).assertIsDisplayed()
        listOf("location", "coordinates", "compass", "altitude", "satellites").forEach {
            composeRule.onNodeWithTag(it, useUnmergedTree = true).assertIsDisplayed()
        }
    }

    @Test
    fun recordingStartsAndStopsThroughForegroundService() {
        dismissHardwareNoticeIfNeeded()
        val application = composeRule.activity.application as AltimeterApplication
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.tab_history)).performClick()
        val start = composeRule.activity.getString(R.string.start_recording)
        composeRule.onNodeWithText(start)
            .assertIsDisplayed()
            .performClick()
        composeRule.waitUntil(10_000L) {
            application.container.trackRepository.trackingState.value.active
        }

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.stop_recording)).performClick()
        composeRule.waitUntil(10_000L) {
            !application.container.trackRepository.trackingState.value.active
        }
    }

    @Test
    fun weatherHasItsOwnTabAndRecordingIsOnlyInHistory() {
        dismissHardwareNoticeIfNeeded()
        val activity = composeRule.activity
        composeRule.onNodeWithText(activity.getString(R.string.start_recording)).assertDoesNotExist()
        composeRule.onNodeWithText(activity.getString(R.string.tab_weather)).performClick()
        composeRule.onNodeWithText(activity.getString(R.string.weather_now)).assertIsDisplayed()
        composeRule.onNodeWithText(activity.getString(R.string.start_recording)).assertDoesNotExist()
        composeRule.onNodeWithText(activity.getString(R.string.tab_history)).performClick()
        composeRule.onNodeWithText(activity.getString(R.string.start_recording)).assertIsDisplayed()
        screenshot("history-recording-bottom.png")
    }

    @Test
    fun pointsHaveTheirOwnTabAndHardwareDetailsStayInSettings() {
        dismissHardwareNoticeIfNeeded()
        val activity = composeRule.activity
        composeRule.onNodeWithText(activity.getString(R.string.tab_waypoints)).performClick()
        composeRule.onNodeWithText(activity.getString(R.string.waypoints_description)).assertIsDisplayed()
        composeRule.onNodeWithText(activity.getString(R.string.save_current_waypoint)).assertIsDisplayed()

        composeRule.onNodeWithText(activity.getString(R.string.tab_settings)).performClick()
        composeRule.onNodeWithText(activity.getString(R.string.hardware_title)).assertIsDisplayed()
        composeRule.onNodeWithText(activity.getString(R.string.hardware_missing_summary, ""))
            .assertDoesNotExist()
    }

    @Test
    fun themeSelectionAppliesImmediatelyAndSurvivesActivityRecreation() {
        dismissHardwareNoticeIfNeeded()
        val settings = (composeRule.activity.application as AltimeterApplication).container.settingsRepository
        val previous = settings.settings.value.themeMode
        try {
            composeRule.onNodeWithText(composeRule.activity.getString(R.string.tab_settings)).performClick()
            composeRule.onNodeWithText(composeRule.activity.getString(R.string.theme_dark)).performClick()
            composeRule.waitUntil(5_000L) { settings.settings.value.themeMode == ThemeMode.DARK }
            composeRule.waitForIdle()
            composeRule.runOnIdle {
                val window = composeRule.activity.window
                assertFalse(WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars)
            }
            screenshot("settings-dark.png")

            composeRule.activityRule.scenario.recreate()
            composeRule.waitForIdle()
            composeRule.runOnIdle {
                val window = composeRule.activity.window
                assertFalse(WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars)
            }
            composeRule.onNodeWithText(composeRule.activity.getString(R.string.theme_light)).performClick()
            composeRule.waitUntil(5_000L) { settings.settings.value.themeMode == ThemeMode.LIGHT }
            composeRule.waitForIdle()
            composeRule.runOnIdle {
                val window = composeRule.activity.window
                assertTrue(WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars)
            }
            screenshot("settings-light.png")
            composeRule.onNodeWithText(composeRule.activity.getString(R.string.theme_system)).performClick()
            composeRule.waitUntil(5_000L) { settings.settings.value.themeMode == ThemeMode.SYSTEM }
        } finally {
            runBlocking { settings.setThemeMode(previous) }
        }
    }

    private fun screenshot(name: String) {
        val dir = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: return
        val file = File(dir, name)
        file.parentFile?.mkdirs()
        file.outputStream().use { composeRule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun dismissHardwareNoticeIfNeeded() {
        val container = (composeRule.activity.application as AltimeterApplication).container
        composeRule.waitUntil(5_000L) { container.settingsRepository.settings.value.hardwareNoticeAcknowledged != null }
        composeRule.waitForIdle()
        if (container.liveRepository.reading.value.deviceCapabilities?.missingHardware?.isNotEmpty() == true &&
            container.settingsRepository.settings.value.hardwareNoticeAcknowledged == false
        ) {
            composeRule.onNodeWithText(composeRule.activity.getString(R.string.understood)).performClick()
            composeRule.waitUntil(5_000L) { container.settingsRepository.settings.value.hardwareNoticeAcknowledged == true }
        }
    }
}
