package com.apophuy.altimeter.util

import kotlin.math.abs
import kotlin.math.exp

fun normalizeDegrees(value: Float): Float = ((value % 360f) + 360f) % 360f

fun shortestAngleDelta(from: Float, to: Float): Float {
    val delta = normalizeDegrees(to) - normalizeDegrees(from)
    return when {
        delta > 180f -> delta - 360f
        delta < -180f -> delta + 360f
        else -> delta
    }
}

class HeadingFilter(
    private val timeConstantMillis: Double = 900.0,
    private val fastTimeConstantMillis: Double = 250.0,
    private val fastResponseThresholdDegrees: Float = 12f,
    private val deadbandDegrees: Float = 1f,
    private val medianWindowSize: Int = 5,
) {
    private var filtered: Float? = null
    private var lastTimestampNanos: Long? = null
    private val rawWindow = ArrayDeque<Float>()

    fun add(value: Float, timestampNanos: Long): Float {
        val normalized = normalizeDegrees(value)
        val previous = filtered
        val previousTimestamp = lastTimestampNanos
        if (previous == null || previousTimestamp == null) {
            filtered = normalized
            lastTimestampNanos = timestampNanos
            rawWindow.addLast(normalized)
            return normalized
        }
        if (timestampNanos <= previousTimestamp) return previous

        rawWindow.addLast(normalized)
        while (rawWindow.size > medianWindowSize) rawWindow.removeFirst()
        val robustValue = circularMedian(rawWindow, previous)

        val elapsedMillis = (timestampNanos - previousTimestamp) / 1_000_000.0
        val delta = shortestAngleDelta(previous, robustValue)
        lastTimestampNanos = timestampNanos
        if (kotlin.math.abs(delta) < deadbandDegrees) return previous

        val effectiveTimeConstant = if (kotlin.math.abs(delta) >= fastResponseThresholdDegrees) {
            fastTimeConstantMillis
        } else {
            timeConstantMillis
        }
        val alpha = (1.0 - exp(-elapsedMillis / effectiveTimeConstant)).coerceIn(0.0, 1.0)
        val next = normalizeDegrees(previous + alpha.toFloat() * delta)
        filtered = next
        return next
    }

    fun reset() {
        filtered = null
        lastTimestampNanos = null
        rawWindow.clear()
    }

    private fun circularMedian(values: Collection<Float>, reference: Float): Float {
        val unwrapped = values
            .map { reference + shortestAngleDelta(reference, it) }
            .sorted()
        val middle = unwrapped.size / 2
        val median = if (unwrapped.size % 2 == 0) {
            (unwrapped[middle - 1] + unwrapped[middle]) / 2f
        } else {
            unwrapped[middle]
        }
        return normalizeDegrees(median)
    }
}

fun metersToFeet(meters: Double): Double = meters * 3.280839895

fun metersToKilometers(meters: Double): Double = meters / 1_000.0

fun metersToMiles(meters: Double): Double = meters / 1_609.344

fun celsiusToFahrenheit(celsius: Double): Double = celsius * 9.0 / 5.0 + 32.0

fun celsiusToKelvin(celsius: Double): Double = celsius + 273.15

fun hPaToMillimetersMercury(hPa: Double): Double = hPa * 0.750061683

fun hPaToInHg(hPa: Double): Double = hPa * 0.0295299830714

fun metersPerSecondToMilesPerHour(value: Double): Double = value * 2.2369362921

fun metersPerSecondToKilometersPerHour(value: Double): Double = value * 3.6

fun metersPerSecondToKnots(value: Double): Double = value * 1.9438444924

/** A pressure-only height delta. The unknown sea-level pressure cancels out. */
fun barometricDeltaMeters(anchorPressureHpa: Float, currentPressureHpa: Float): Double {
    val anchorStandardAltitude = pressureAltitudeMeters(anchorPressureHpa)
    val currentStandardAltitude = pressureAltitudeMeters(currentPressureHpa)
    return currentStandardAltitude - anchorStandardAltitude
}

fun pressureAltitudeMeters(pressureHpa: Float): Double =
    44330.0 * (1.0 - Math.pow((pressureHpa / 1013.25f).toDouble(), 1.0 / 5.255))

class MedianEmaFilter(
    private val windowSize: Int = 5,
    private val alpha: Double = 0.25,
) {
    private val window = ArrayDeque<Double>()
    private var ema: Double? = null

    fun add(value: Double): Double {
        window.addLast(value)
        while (window.size > windowSize) window.removeFirst()
        val sorted = window.sorted()
        val median = sorted[sorted.size / 2]
        val filtered = ema?.let { previous -> previous + alpha * (median - previous) } ?: median
        ema = filtered
        return filtered
    }

    fun reset() {
        window.clear()
        ema = null
    }
}

fun shouldStorePoint(
    previousAltitudeMeters: Double?,
    currentAltitudeMeters: Double,
    altitudeStepMeters: Double,
): Boolean = previousAltitudeMeters == null ||
    abs(currentAltitudeMeters - previousAltitudeMeters) >= altitudeStepMeters
