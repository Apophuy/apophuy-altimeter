package com.apophuy.altimeter.ui

import com.apophuy.altimeter.model.DistanceUnit
import com.apophuy.altimeter.model.CoordinateFormat
import com.apophuy.altimeter.model.PressureUnit
import com.apophuy.altimeter.model.TemperatureUnit
import com.apophuy.altimeter.model.WindSpeedUnit
import com.apophuy.altimeter.util.celsiusToFahrenheit
import com.apophuy.altimeter.util.celsiusToKelvin
import com.apophuy.altimeter.util.currentAppLocale
import com.apophuy.altimeter.util.hPaToInHg
import com.apophuy.altimeter.util.hPaToMillimetersMercury
import com.apophuy.altimeter.util.metersPerSecondToKilometersPerHour
import com.apophuy.altimeter.util.metersPerSecondToKnots
import com.apophuy.altimeter.util.metersPerSecondToMilesPerHour
import com.apophuy.altimeter.util.metersToFeet
import com.apophuy.altimeter.util.metersToKilometers
import com.apophuy.altimeter.util.metersToMiles
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import mil.nga.grid.features.Point
import mil.nga.mgrs.MGRS
import mil.nga.mgrs.grid.GridType
import mil.nga.mgrs.utm.UTM

fun formatAltitude(meters: Double?, unit: DistanceUnit, decimals: Int = 1): String {
    if (meters == null) return "—"
    val value = distanceValueFromMeters(meters, unit)
    val effectiveDecimals = when (unit) {
        DistanceUnit.KILOMETERS, DistanceUnit.MILES -> maxOf(decimals, 3)
        else -> decimals
    }
    return String.format(currentAppLocale(), "%.${effectiveDecimals}f %s", value, distanceUnitSymbol(unit))
}

fun formatTemperature(celsius: Double?, unit: TemperatureUnit): String {
    if (celsius == null) return "—"
    val value = when (unit) {
        TemperatureUnit.CELSIUS -> celsius
        TemperatureUnit.FAHRENHEIT -> celsiusToFahrenheit(celsius)
        TemperatureUnit.KELVIN -> celsiusToKelvin(celsius)
    }
    val suffix = when (unit) {
        TemperatureUnit.CELSIUS -> "°C"
        TemperatureUnit.FAHRENHEIT -> "°F"
        TemperatureUnit.KELVIN -> " K"
    }
    return String.format(currentAppLocale(), "%d%s", value.roundToInt(), suffix)
}

fun formatPressure(hPa: Double?, unit: PressureUnit): String {
    if (hPa == null) return "—"
    val locale = currentAppLocale()
    val russian = locale.language == "ru"
    val (value, decimals, suffix) = when (unit) {
        PressureUnit.MILLIMETERS_MERCURY -> Triple(hPaToMillimetersMercury(hPa), 1, if (russian) "мм рт. ст." else "mmHg")
        PressureUnit.HECTOPASCALS -> Triple(hPa, 1, if (russian) "гПа" else "hPa")
        PressureUnit.MILLIBARS -> Triple(hPa, 1, if (russian) "мбар" else "mbar")
        PressureUnit.KILOPASCALS -> Triple(hPa / 10.0, 2, if (russian) "кПа" else "kPa")
        PressureUnit.BAR -> Triple(hPa / 1_000.0, 3, if (russian) "бар" else "bar")
        PressureUnit.ATMOSPHERES -> Triple(hPa / 1_013.25, 3, if (russian) "атм" else "atm")
        PressureUnit.INCHES_MERCURY -> Triple(hPaToInHg(hPa), 2, if (russian) "дюйм рт. ст." else "inHg")
    }
    return String.format(locale, "%.${decimals}f %s", value, suffix)
}

fun formatWind(metersPerSecond: Double?, unit: WindSpeedUnit): String {
    if (metersPerSecond == null) return "—"
    val (value, suffix) = when (unit) {
        WindSpeedUnit.METERS_PER_SECOND -> metersPerSecond to "m/s"
        WindSpeedUnit.KILOMETERS_PER_HOUR -> metersPerSecondToKilometersPerHour(metersPerSecond) to "km/h"
        WindSpeedUnit.MILES_PER_HOUR -> metersPerSecondToMilesPerHour(metersPerSecond) to "mph"
        WindSpeedUnit.KNOTS -> metersPerSecondToKnots(metersPerSecond) to "kn"
    }
    return String.format(currentAppLocale(), "%.1f %s", value, suffix)
}

fun distanceValueFromMeters(meters: Double, unit: DistanceUnit): Double = when (unit) {
    DistanceUnit.METERS -> meters
    DistanceUnit.FEET -> metersToFeet(meters)
    DistanceUnit.KILOMETERS -> metersToKilometers(meters)
    DistanceUnit.MILES -> metersToMiles(meters)
}

fun distanceValueToMeters(value: Double, unit: DistanceUnit): Double = when (unit) {
    DistanceUnit.METERS -> value
    DistanceUnit.FEET -> value / 3.280839895
    DistanceUnit.KILOMETERS -> value * 1_000.0
    DistanceUnit.MILES -> value * 1_609.344
}

fun distanceUnitSymbol(unit: DistanceUnit): String = when (unit) {
    DistanceUnit.METERS -> "m"
    DistanceUnit.FEET -> "ft"
    DistanceUnit.KILOMETERS -> "km"
    DistanceUnit.MILES -> "mi"
}

fun formatCoordinates(
    latitude: Double,
    longitude: Double,
    format: CoordinateFormat = CoordinateFormat.DECIMAL_DEGREES,
): String {
    if (!latitude.isFinite() || !longitude.isFinite() || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
        return "—"
    }
    return when (format) {
        CoordinateFormat.DECIMAL_DEGREES ->
            String.format(Locale.US, "%.6f, %.6f", latitude, longitude)
        CoordinateFormat.DMS -> "${degreesMinutesSeconds(latitude, true)} ${degreesMinutesSeconds(longitude, false)}"
        CoordinateFormat.UTM -> gridCoordinate(latitude, longitude) { point -> UTM.from(point).toString() }
        CoordinateFormat.MGRS -> gridCoordinate(latitude, longitude) { point ->
            MGRS.from(point).coordinate(GridType.METER)
        }
    }
}

private fun degreesMinutesSeconds(value: Double, latitude: Boolean): String {
    val absolute = abs(value)
    val degrees = floor(absolute).toInt()
    val minutesValue = (absolute - degrees) * 60.0
    val minutes = floor(minutesValue).toInt()
    val seconds = (minutesValue - minutes) * 60.0
    val hemisphere = if (latitude) {
        if (value >= 0.0) "N" else "S"
    } else if (value >= 0.0) "E" else "W"
    return String.format(Locale.US, "%d°%02d′%05.2f″%s", degrees, minutes, seconds, hemisphere)
}

private inline fun gridCoordinate(
    latitude: Double,
    longitude: Double,
    block: (Point) -> String,
): String {
    // NGA mgrs-java implements UTM/MGRS, whose defined latitude range is 80°S–84°N.
    if (latitude !in -80.0..84.0) return "—"
    return runCatching { block(Point.point(longitude, latitude)) }.getOrDefault("—")
}

fun formatDuration(durationMillis: Long): String {
    val totalSeconds = (durationMillis.coerceAtLeast(0L) / 1_000L)
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    val locale = currentAppLocale()
    return if (hours > 0) String.format(locale, "%d:%02d:%02d", hours, minutes, seconds)
    else String.format(locale, "%02d:%02d", minutes, seconds)
}

fun formatDateTime(timestampMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, currentAppLocale())
        .format(Date(timestampMillis))
