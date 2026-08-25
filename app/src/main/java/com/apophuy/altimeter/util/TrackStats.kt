package com.apophuy.altimeter.util

import kotlin.math.max
import kotlin.math.min

data class TrackProgress(
    val minimumMeters: Double? = null,
    val maximumMeters: Double? = null,
    val gainMeters: Double = 0.0,
    val lossMeters: Double = 0.0,
    val pointCount: Int = 0,
)

fun nextTrackProgress(
    current: TrackProgress,
    previousAltitudeMeters: Double?,
    altitudeMeters: Double,
): TrackProgress {
    val delta = previousAltitudeMeters?.let { altitudeMeters - it } ?: 0.0
    return TrackProgress(
        minimumMeters = min(current.minimumMeters ?: altitudeMeters, altitudeMeters),
        maximumMeters = max(current.maximumMeters ?: altitudeMeters, altitudeMeters),
        gainMeters = current.gainMeters + max(delta, 0.0),
        lossMeters = current.lossMeters + max(-delta, 0.0),
        pointCount = current.pointCount + 1,
    )
}
