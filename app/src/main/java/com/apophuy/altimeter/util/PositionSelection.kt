package com.apophuy.altimeter.util

import com.apophuy.altimeter.model.Coordinates
import com.apophuy.altimeter.model.PositionSource

const val WAYPOINT_POSITION_MAX_AGE_MILLIS = 10_000L
const val WAYPOINT_MAX_HORIZONTAL_ACCURACY_METERS = 25f

private const val GNSS_POSITION_PREFERENCE_MILLIS = 10_000L
private const val LOCATION_CALLBACK_MAX_AGE_MILLIS = 30_000L

/**
 * Keeps a recent satellite fix authoritative while still allowing network positioning to provide
 * a fallback before the first GNSS fix or after GNSS updates have stopped.
 */
fun shouldReplacePosition(
    current: Coordinates?,
    candidate: Coordinates,
    nowMillis: Long,
): Boolean {
    if (!isTimestampFresh(candidate.timestampMillis, nowMillis, LOCATION_CALLBACK_MAX_AGE_MILLIS)) {
        return false
    }
    if (current == null) return true

    if (
        candidate.source == PositionSource.GNSS &&
        current.source != PositionSource.GNSS &&
        isTimestampFresh(candidate.timestampMillis, nowMillis, GNSS_POSITION_PREFERENCE_MILLIS)
    ) return true

    if (candidate.timestampMillis < current.timestampMillis) return false

    if (
        current.source == PositionSource.GNSS &&
        candidate.source != PositionSource.GNSS &&
        isTimestampFresh(current.timestampMillis, nowMillis, GNSS_POSITION_PREFERENCE_MILLIS)
    ) return false

    if (candidate.timestampMillis == current.timestampMillis) {
        if (candidate.source != current.source) return candidate.source == PositionSource.GNSS
        val currentAccuracy = current.horizontalAccuracyMeters ?: Float.POSITIVE_INFINITY
        val candidateAccuracy = candidate.horizontalAccuracyMeters ?: Float.POSITIVE_INFINITY
        return candidateAccuracy < currentAccuracy
    }

    return true
}

fun isWaypointPositionUsable(coordinates: Coordinates?, nowMillis: Long): Boolean {
    if (coordinates?.source != PositionSource.GNSS) return false
    val accuracy = coordinates.horizontalAccuracyMeters ?: return false
    return accuracy.isFinite() &&
        accuracy in 0f..WAYPOINT_MAX_HORIZONTAL_ACCURACY_METERS &&
        isTimestampFresh(coordinates.timestampMillis, nowMillis, WAYPOINT_POSITION_MAX_AGE_MILLIS)
}
