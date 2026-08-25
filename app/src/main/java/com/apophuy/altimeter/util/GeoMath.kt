package com.apophuy.altimeter.util

import com.apophuy.altimeter.model.AlertDirection
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadiusMeters = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
        sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return earthRadiusMeters * c
}

fun initialBearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val startLatitude = Math.toRadians(lat1)
    val endLatitude = Math.toRadians(lat2)
    val longitudeDelta = Math.toRadians(lon2 - lon1)
    val y = sin(longitudeDelta) * cos(endLatitude)
    val x = cos(startLatitude) * sin(endLatitude) -
        sin(startLatitude) * cos(endLatitude) * cos(longitudeDelta)
    return ((Math.toDegrees(atan2(y, x)) % 360.0) + 360.0) % 360.0
}

data class TargetNavigation(
    val bearingDegrees: Float,
    val distanceMeters: Double,
)

enum class TargetDirectionReference { COMPASS, GPS_COURSE, POSITION_UNCERTAIN, NONE }

data class TargetDirection(
    val degreesRelativeToReference: Float?,
    val reference: TargetDirectionReference,
)

fun resolveTargetDirection(
    targetBearingDegrees: Float?,
    positionDirectionReliable: Boolean,
    compassHeadingDegrees: Float?,
    compassReliable: Boolean,
    movementCourseDegrees: Float?,
): TargetDirection {
    if (targetBearingDegrees == null) return TargetDirection(null, TargetDirectionReference.NONE)
    if (!positionDirectionReliable) {
        return TargetDirection(null, TargetDirectionReference.POSITION_UNCERTAIN)
    }
    if (compassReliable && compassHeadingDegrees != null) {
        return TargetDirection(
            normalizeDegrees(targetBearingDegrees - compassHeadingDegrees),
            TargetDirectionReference.COMPASS,
        )
    }
    if (movementCourseDegrees != null) {
        return TargetDirection(
            normalizeDegrees(targetBearingDegrees - movementCourseDegrees),
            TargetDirectionReference.GPS_COURSE,
        )
    }
    return TargetDirection(null, TargetDirectionReference.NONE)
}

/** Smooths GNSS jitter without coupling waypoint navigation to the magnetic compass. */
class TargetNavigationFilter(
    private val bearingAlpha: Float = 0.25f,
    private val distanceAlpha: Double = 0.35,
) {
    private var bearingDegrees: Float? = null
    private var distanceMeters: Double? = null

    fun add(bearingDegrees: Float, distanceMeters: Double): TargetNavigation {
        val normalizedBearing = normalizeDegrees(bearingDegrees)
        val filteredBearing = this.bearingDegrees?.let { previous ->
            normalizeDegrees(previous + bearingAlpha * shortestAngleDelta(previous, normalizedBearing))
        } ?: normalizedBearing
        val filteredDistance = this.distanceMeters?.let { previous ->
            previous + distanceAlpha * (distanceMeters - previous)
        } ?: distanceMeters
        this.bearingDegrees = filteredBearing
        this.distanceMeters = filteredDistance
        return TargetNavigation(filteredBearing, filteredDistance)
    }
}

fun crossedAltitudeThreshold(
    previousMeters: Double,
    currentMeters: Double,
    thresholdMeters: Double,
): AlertDirection? = when {
    previousMeters < thresholdMeters && currentMeters >= thresholdMeters -> AlertDirection.ASCENDING
    previousMeters > thresholdMeters && currentMeters <= thresholdMeters -> AlertDirection.DESCENDING
    else -> null
}

fun isTimestampFresh(timestampMillis: Long, nowMillis: Long, maxAgeMillis: Long): Boolean =
    timestampMillis > 0L && nowMillis - timestampMillis in 0L..maxAgeMillis

class AltitudeAlertTracker {
    private val previousAltitudeByAlert = mutableMapOf<Long, Double>()
    private val triggered = mutableSetOf<Pair<Long, AlertDirection>>()

    fun startSession(persisted: Set<Pair<Long, AlertDirection>> = emptySet()) {
        previousAltitudeByAlert.clear()
        triggered.clear()
        triggered += persisted
    }

    fun resetBaselines() = previousAltitudeByAlert.clear()

    fun resetBaseline(alertId: Long) {
        previousAltitudeByAlert.remove(alertId)
    }

    fun retainAlerts(alertIds: Set<Long>) {
        previousAltitudeByAlert.keys.retainAll(alertIds)
    }

    fun crossingCandidate(alertId: Long, thresholdMeters: Double, altitudeMeters: Double): AlertDirection? {
        val previous = previousAltitudeByAlert.put(alertId, altitudeMeters) ?: return null
        val direction = crossedAltitudeThreshold(previous, altitudeMeters, thresholdMeters) ?: return null
        return direction.takeUnless { alertId to it in triggered }
    }

    fun markTriggered(alertId: Long, direction: AlertDirection) {
        triggered += alertId to direction
    }
}
