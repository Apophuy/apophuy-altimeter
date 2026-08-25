package com.apophuy.altimeter.location

internal data class GnssSatelliteSignal(
    val constellation: Int,
    val svid: Int,
    val usedInFix: Boolean,
)

internal data class GnssSatelliteSummary(
    val visibleSatellites: Int,
    val usedSatellites: Int,
)

/** Counts physical satellites once even when a vendor reports several frequency bands per SVID. */
internal fun summarizeGnssSatellites(
    signals: Iterable<GnssSatelliteSignal>,
): GnssSatelliteSummary {
    val visible = mutableSetOf<Pair<Int, Int>>()
    val used = mutableSetOf<Pair<Int, Int>>()
    signals.forEach { signal ->
        val identity = signal.constellation to signal.svid
        visible += identity
        if (signal.usedInFix) used += identity
    }
    return GnssSatelliteSummary(
        visibleSatellites = visible.size,
        usedSatellites = used.size,
    )
}
