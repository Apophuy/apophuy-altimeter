package com.apophuy.altimeter

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.apophuy.altimeter.model.Coordinates
import com.apophuy.altimeter.model.DailyForecast
import com.apophuy.altimeter.model.HourlyForecast
import com.apophuy.altimeter.model.LiveReading
import com.apophuy.altimeter.model.PressureReading
import com.apophuy.altimeter.model.PressureSource
import com.apophuy.altimeter.model.UserSettings
import com.apophuy.altimeter.model.WeatherForecast
import com.apophuy.altimeter.model.WeatherKind
import com.apophuy.altimeter.model.WeatherSnapshot
import com.apophuy.altimeter.ui.WeatherScreen
import com.apophuy.altimeter.ui.theme.AltimeterTheme
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class WeatherScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun currentWeatherPressureAndForecastCanBeRead() {
        val now = System.currentTimeMillis()
        val zone = ZoneId.of("Europe/Moscow")
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val firstHour = Instant.ofEpochMilli(now).truncatedTo(ChronoUnit.HOURS).toEpochMilli()
        val forecast = WeatherForecast(zone,
            hourly = (0..24).map { HourlyForecast(firstHour + it * 3_600_000L, 22.0 - it * 0.3, WeatherKind.PARTLY_CLOUDY, 20, 3.2, true) },
            daily = (0L..6L).map { DailyForecast(today.plusDays(it), 14.0 + it, 22.0 + it, WeatherKind.PARTLY_CLOUDY, 20, 5.0) },
        )
        compose.setContent {
            AltimeterTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                WeatherScreen(LiveReading(
                    coordinates = Coordinates(55.75, 37.61), placeName = "Москва",
                    pressure = PressureReading(995f, PressureSource.SENSOR, now),
                    weather = WeatherSnapshot(22.0, 21.0, 65, 3.2, 2, WeatherKind.PARTLY_CLOUDY,
                        995.0, 55.75, 37.61, now, forecast = forecast),
                ), UserSettings())
                }
            }
        }
        compose.onNodeWithText(context.getString(R.string.weather_now)).assertIsDisplayed()
        screenshot("weather-current.png")
        compose.onNodeWithText(context.getString(R.string.pressure_sensor)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.hourly_forecast)).performScrollTo().assertIsDisplayed()
        screenshot("weather-hourly.png")
        compose.onNodeWithText(context.getString(R.string.today)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.forecast_local_time, zone.id)).performScrollTo().assertIsDisplayed()
        screenshot("weather-daily.png")
    }

    @Test
    fun barometerRemainsVisibleWithoutWeatherOrCoordinates() {
        compose.setContent {
            AltimeterTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                WeatherScreen(LiveReading(pressure = PressureReading(995f, PressureSource.SENSOR, 0L)), UserSettings())
                }
            }
        }
        compose.onNodeWithText(context.getString(R.string.weather_needs_location)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.pressure_sensor)).performScrollTo().assertIsDisplayed()
    }

    private fun screenshot(name: String) {
        val dir = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: return
        val file = File(dir, name)
        file.parentFile?.mkdirs()
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
