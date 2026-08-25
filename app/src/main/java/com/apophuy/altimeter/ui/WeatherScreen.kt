package com.apophuy.altimeter.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apophuy.altimeter.R
import com.apophuy.altimeter.model.DailyForecast
import com.apophuy.altimeter.model.HourlyForecast
import com.apophuy.altimeter.model.LiveReading
import com.apophuy.altimeter.model.PressureSource
import com.apophuy.altimeter.model.UserSettings
import com.apophuy.altimeter.model.WeatherKind
import com.apophuy.altimeter.ui.theme.InstrumentAmber
import com.apophuy.altimeter.ui.theme.InstrumentMuted
import com.apophuy.altimeter.ui.theme.InstrumentSurface
import com.apophuy.altimeter.ui.theme.InstrumentSurfaceHigh
import com.apophuy.altimeter.ui.theme.InstrumentTeal
import com.apophuy.altimeter.util.distanceMeters
import com.apophuy.altimeter.weather.OpenMeteoRepository
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun WeatherScreen(live: LiveReading, settings: UserSettings) {
    val locale = LocalConfiguration.current.locales[0]
    val light = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val heroText = if (light) MaterialTheme.colorScheme.onBackground else Color.White
    val heroMuted = if (light) InstrumentMuted else Color(0xFFC1DAE5)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000L)
        }
    }
    val weather = live.weather
    val forecast = weather?.forecast
    val hours = forecast?.upcomingHours(now).orEmpty()
    val days = forecast?.upcomingDays(now).orEmpty()
    val stale = weather != null && (weather.stale || now - weather.updatedAtMillis > OpenMeteoRepository.WEATHER_MAX_AGE_MS)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.tab_weather), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Card(shape = RoundedCornerShape(28.dp)) {
            val night = weather?.isDay == false
            Column(
                Modifier.fillMaxWidth().background(Brush.verticalGradient(
                    when {
                        light && night -> listOf(Color(0xFFDAE2FF), Color(0xFFEFF1FF))
                        light -> listOf(Color(0xFFCCEBF7), Color(0xFFE7F4F8))
                        night -> listOf(Color(0xFF282C58), Color(0xFF121A2F))
                        else -> listOf(Color(0xFF174F62), Color(0xFF172A3D))
                    },
                )).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val place = if (weather == null || live.coordinates?.let {
                    distanceMeters(it.latitude, it.longitude, weather.latitude, weather.longitude) < 1_000.0
                } == true) live.placeName else null
                Text(place ?: weather?.let { formatCoordinates(it.latitude, it.longitude) }
                    ?: stringResource(R.string.location_unavailable),
                    style = MaterialTheme.typography.titleMedium, color = heroText)
                Text(stringResource(R.string.weather_now), color = heroMuted, style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.text.BasicText(
                        formatTemperature(weather?.temperatureC, settings.temperatureUnit),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.displayLarge.copy(color = heroText, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 28.sp, maxFontSize = 64.sp),
                    )
                    WeatherIcon(weather?.kind ?: WeatherKind.UNKNOWN, Modifier.size(112.dp), isDay = !night)
                }
                Text(weatherKindText(weather?.kind), style = MaterialTheme.typography.titleLarge, color = heroText)
                weather?.apparentTemperatureC?.let {
                    Text(stringResource(R.string.feels_like, formatTemperature(it, settings.temperatureUnit)), color = heroMuted)
                }
                if (weather != null) {
                    Text(stringResource(R.string.weather_updated, formatDateTime(weather.updatedAtMillis)),
                        style = MaterialTheme.typography.bodySmall, color = heroMuted)
                } else {
                    Text(stringResource(if (live.coordinates == null) R.string.weather_needs_location else R.string.weather_unavailable),
                        color = heroMuted, style = MaterialTheme.typography.bodyMedium)
                }
                if (stale) Text(stringResource(R.string.weather_cached), color = InstrumentAmber, style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WeatherMetric(stringResource(R.string.humidity), weather?.humidityPercent?.let { stringResource(R.string.percent_value, it) } ?: "—", Modifier.weight(1f))
            WeatherMetric(stringResource(R.string.wind), formatWind(weather?.windSpeedMetersPerSecond, settings.windSpeedUnit), Modifier.weight(1f))
        }
        InstrumentCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.pressure), color = InstrumentMuted, style = MaterialTheme.typography.labelLarge)
                    Text(stringResource(when (live.pressure?.source) {
                        PressureSource.SENSOR -> R.string.pressure_sensor
                        PressureSource.WEATHER -> R.string.pressure_weather
                        null -> R.string.not_available
                    }), color = InstrumentMuted, style = MaterialTheme.typography.bodySmall)
                }
                Text(formatPressure(live.pressure?.hPa?.toDouble(), settings.pressureUnit),
                    fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            }
            if (stale && live.pressure?.source == PressureSource.WEATHER) {
                Text(stringResource(R.string.weather_cached), color = InstrumentAmber, style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(stringResource(R.string.hourly_forecast), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (hours.isEmpty()) {
            Text(stringResource(R.string.forecast_unavailable), color = InstrumentMuted)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(hours, key = { it.timestampMillis }) { hour -> HourCard(hour, forecast!!.timeZone, settings) }
            }
        }
        Text(stringResource(R.string.daily_forecast), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (days.isEmpty()) {
            Text(stringResource(R.string.forecast_unavailable), color = InstrumentMuted)
        } else {
            InstrumentCard {
                val low = days.minOf { it.minTemperatureC }
                val high = days.maxOf { it.maxTemperatureC }
                val today = Instant.ofEpochMilli(now).atZone(forecast!!.timeZone).toLocalDate()
                days.forEachIndexed { index, day ->
                    val label = if (day.date == today) stringResource(R.string.today)
                        else day.date.format(DateTimeFormatter.ofPattern("EEE, d MMM", locale))
                    DayRow(day, label, low, high, settings)
                    if (index != days.lastIndex) HorizontalDivider(color = InstrumentSurfaceHigh)
                }
            }
        }
        forecast?.let {
            Text(stringResource(R.string.forecast_local_time, it.timeZone.id), style = MaterialTheme.typography.bodySmall, color = InstrumentMuted)
        }
        Text(stringResource(R.string.weather_attribution), style = MaterialTheme.typography.bodySmall, color = InstrumentMuted)
    }
}

@Composable
private fun WeatherMetric(label: String, value: String, modifier: Modifier) {
    InstrumentCard(modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = InstrumentMuted)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HourCard(hour: HourlyForecast, timeZone: ZoneId, settings: UserSettings) {
    val locale = LocalConfiguration.current.locales[0]
    Card(colors = CardDefaults.cardColors(containerColor = InstrumentSurface), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.width(100.dp).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(Instant.ofEpochMilli(hour.timestampMillis).atZone(timeZone)
                .format(DateTimeFormatter.ofPattern("HH:mm", locale)), color = InstrumentMuted)
            val description = weatherKindText(hour.kind)
            WeatherIcon(hour.kind, Modifier.size(48.dp).semantics { contentDescription = description }, hour.isDay)
            Text(formatTemperature(hour.temperatureC, settings.temperatureUnit), fontWeight = FontWeight.Bold)
            Text(hour.precipitationProbability?.let { stringResource(R.string.precipitation_short, it) } ?: "—",
                color = InstrumentTeal, style = MaterialTheme.typography.labelSmall)
            Text(formatWind(hour.windSpeedMetersPerSecond, settings.windSpeedUnit),
                color = InstrumentMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun DayRow(day: DailyForecast, label: String, low: Double, high: Double, settings: UserSettings) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(weatherKindText(day.kind), color = InstrumentMuted, style = MaterialTheme.typography.bodySmall)
            }
            WeatherIcon(day.kind, Modifier.size(40.dp))
            Text(stringResource(R.string.temperature_range,
                formatTemperature(day.minTemperatureC, settings.temperatureUnit),
                formatTemperature(day.maxTemperatureC, settings.temperatureUnit)),
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        val rangeBackground = InstrumentSurfaceHigh
        val rangeLow = InstrumentTeal
        val rangeHigh = InstrumentAmber
        Canvas(Modifier.fillMaxWidth().height(8.dp)) {
            val stroke = size.height
            val usableWidth = (size.width - stroke).coerceAtLeast(0f)
            val range = (high - low).coerceAtLeast(1.0)
            val start = stroke / 2 + ((day.minTemperatureC - low) / range).toFloat() * usableWidth
            val end = stroke / 2 + ((day.maxTemperatureC - low) / range).toFloat() * usableWidth
            drawLine(rangeBackground, Offset(stroke / 2, stroke / 2), Offset(size.width - stroke / 2, stroke / 2), stroke, StrokeCap.Round)
            drawLine(Brush.horizontalGradient(listOf(rangeLow, rangeHigh)),
                Offset(start, stroke / 2), Offset(end, stroke / 2), stroke, StrokeCap.Round)
        }
        Text(stringResource(R.string.forecast_precipitation_wind,
            day.precipitationProbability?.let { stringResource(R.string.percent_value, it) } ?: "—",
            formatWind(day.windSpeedMetersPerSecond, settings.windSpeedUnit)),
            style = MaterialTheme.typography.labelSmall, color = InstrumentMuted)
    }
}
