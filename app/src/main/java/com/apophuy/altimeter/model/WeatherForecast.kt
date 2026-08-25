package com.apophuy.altimeter.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class HourlyForecast(
    val timestampMillis: Long,
    val temperatureC: Double,
    val kind: WeatherKind,
    val precipitationProbability: Int?,
    val windSpeedMetersPerSecond: Double?,
    val isDay: Boolean,
)

data class DailyForecast(
    val date: LocalDate,
    val minTemperatureC: Double,
    val maxTemperatureC: Double,
    val kind: WeatherKind,
    val precipitationProbability: Int?,
    val windSpeedMetersPerSecond: Double?,
)

data class WeatherForecast(
    val timeZone: ZoneId,
    val hourly: List<HourlyForecast>,
    val daily: List<DailyForecast>,
) {
    fun upcomingHours(nowMillis: Long): List<HourlyForecast> = hourly
        .filter { it.timestampMillis + 3_600_000L > nowMillis && it.timestampMillis < nowMillis + 24 * 3_600_000L }
        .take(24)

    fun upcomingDays(nowMillis: Long): List<DailyForecast> {
        val today = Instant.ofEpochMilli(nowMillis).atZone(timeZone).toLocalDate()
        return daily.filter { !it.date.isBefore(today) && it.date.isBefore(today.plusDays(7)) }.take(7)
    }
}
