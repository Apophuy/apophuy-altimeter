package com.apophuy.altimeter

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.Locales
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.apophuy.altimeter.model.AltitudeReading
import com.apophuy.altimeter.model.AltitudeSource
import com.apophuy.altimeter.model.CompassReading
import com.apophuy.altimeter.model.Coordinates
import com.apophuy.altimeter.model.DeviceCapabilities
import com.apophuy.altimeter.model.GnssFixState
import com.apophuy.altimeter.model.GnssSignalWarning
import com.apophuy.altimeter.model.GnssState
import com.apophuy.altimeter.model.LiveReading
import com.apophuy.altimeter.model.UserSettings
import com.apophuy.altimeter.ui.AltimeterApp
import com.apophuy.altimeter.ui.AppUiState
import com.apophuy.altimeter.ui.theme.AltimeterTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@RunWith(Parameterized::class)
class InstrumentLayoutTest(private val width: Int, private val height: Int, private val fontScale: Float, private val permission: Boolean, private val language: String) {
    @get:Rule val compose = createComposeRule()

    @Test
    fun fiveInstrumentsFitWithoutScrolling() {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(width.dp, height.dp))) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(fontScale) then DeviceConfigurationOverride.Locales(LocaleList(language))) {
                    AltimeterTheme {
                        AltimeterApp(
                            state = AppUiState(
                                live = LiveReading(
                                    deviceCapabilities = DeviceCapabilities(true, true, true, true, false),
                                    coordinates = Coordinates(55.755826, 37.617300),
                                    placeName = "Москва, центральный административный округ",
                                    compass = CompassReading(
                                        headingDegrees = 123f,
                                        accuracy = com.apophuy.altimeter.model.CompassAccuracy.UNRELIABLE,
                                    ),
                                    altitude = AltitudeReading(152.3, 12f, AltitudeSource.GNSS, 0L, false),
                                    gnss = GnssState(
                                        state = GnssFixState.FIXED,
                                        visibleSatellites = 28,
                                        horizontalAccuracyMeters = 60f,
                                        signalWarning = GnssSignalWarning.UNSTABLE,
                                    ),
                                ),
                                settings = UserSettings(hardwareNoticeAcknowledged = true),
                            ),
                            selectedSession = null, hasLocationPermission = permission,
                            hasNotificationPermission = true,
                            onRequestLocationPermission = {}, onStartTracking = {}, onStopTracking = {},
                            onSelectSession = {}, onDeleteSession = {}, onShare = { _, _ -> },
                            onDistanceUnitChanged = {}, onPressureUnitChanged = {}, onTemperatureUnitChanged = {},
                            onWindSpeedUnitChanged = {}, onCoordinateFormatChanged = {},
                            onLanguageChanged = {}, onThemeModeChanged = {}, onKeepScreenOnChanged = {}, onTrackingAltitudeStepChanged = {},
                            onTrackingSampleIntervalChanged = {}, onTrackingCoordinateMaxAgeChanged = {},
                            onGnssDiagnosticLoggingChanged = {}, onShareGnssDiagnosticLog = {}, onClearGnssDiagnosticLog = {},
                            onCalibrate = {}, onCalibrateToTerrain = {}, onResetCalibration = {}, onHardwareNoticeAcknowledged = {},
                            onSaveWaypoint = {}, onRenameWaypoint = { _, _ -> }, onDeleteWaypoint = {},
                            onSelectWaypoint = {}, onShareWaypoint = {}, onAddAltitudeAlert = {},
                            onReplaceAltitudeAlert = { _, _ -> }, onAltitudeAlertEnabledChanged = { _, _ -> },
                            onDeleteAltitudeAlert = {},
                        )
                    }
                }
            }
        }
        val outputDir = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        if (outputDir != null) {
            val file = File(outputDir, "dashboard-${width}x$height-font$fontScale-permission$permission-$language.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        val dashboard = compose.onNodeWithTag("dashboard").getUnclippedBoundsInRoot()
        for (tag in listOf("location", "coordinates", "compass", "altitude", "satellites", "gnss-warning")) {
            val node = compose.onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed()
            val bounds = node.getUnclippedBoundsInRoot()
            assertTrue("$tag extends outside dashboard: $bounds / $dashboard",
                bounds.top >= dashboard.top && bounds.bottom <= dashboard.bottom &&
                    bounds.left >= dashboard.left && bounds.right <= dashboard.right)
            assertTrue("$tag has no usable height", bounds.bottom > bounds.top)
        }

    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}x{1}, font={2}, permission={3}, language={4}")
        fun sizes(): List<Array<Any>> = listOf(
            arrayOf(360, 640, 1f, true, "en"),
            arrayOf(360, 640, 1.3f, false, "en"),
            arrayOf(360, 640, 1.3f, false, "ru"),
            arrayOf(360, 780, 1f, true, "ru"),
            arrayOf(412, 915, 1f, true, "ru"),
            arrayOf(412, 960, 1f, true, "ru"),
            arrayOf(640, 360, 1f, false, "en"),
            arrayOf(640, 360, 1f, false, "ru"),
            arrayOf(360, 640, 1f, true, "de"),
            arrayOf(640, 360, 1f, true, "de"),
            arrayOf(360, 640, 1f, true, "hi"),
            arrayOf(640, 360, 1f, true, "hi"),
            arrayOf(360, 640, 1f, true, "zh-CN"),
            arrayOf(640, 360, 1f, true, "zh-CN"),
            arrayOf(360, 640, 1.3f, true, "ar"),
            arrayOf(640, 360, 1.3f, true, "ar"),
            arrayOf(360, 640, 1.3f, true, "ur"),
            arrayOf(640, 360, 1.3f, true, "ur"),
        )
    }
}
