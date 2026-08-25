package com.apophuy.altimeter.location

import com.apophuy.altimeter.model.GnssFixState
import com.apophuy.altimeter.model.GnssSignalWarning
import com.apophuy.altimeter.model.GnssState

/**
 * Reconciles GPS locations with satellite status callbacks.
 *
 * Some vendor GNSS implementations publish intermittent empty satellite frames or never set
 * GnssStatus.usedInFix even while valid GPS locations are being delivered. A fresh GPS location
 * is therefore treated as authoritative fix evidence, while short empty frames are ignored.
 */
internal class GnssStateResolver(
    private val fixEvidenceFreshnessMillis: Long = FIX_EVIDENCE_FRESHNESS_MILLIS,
    private val usedSatellitesFreshnessMillis: Long = USED_SATELLITES_FRESHNESS_MILLIS,
    private val visibleSatellitesWindowMillis: Long = VISIBLE_SATELLITES_WINDOW_MILLIS,
) {
    private var lastFixEvidenceMillis: Long? = null
    private var lastReliableUsedSatellites: Int? = null
    private var lastReliableUsedSatellitesMillis: Long? = null
    private var searchStartedMillis: Long? = null
    private var poorAccuracySinceMillis: Long? = null
    private var goodAccuracySinceMillis: Long? = null
    private val visibleSatelliteSamples = ArrayDeque<VisibleSatelliteSample>()

    fun onStarted(current: GnssState, nowMillis: Long): GnssState {
        val hasFix = hasFreshFixEvidence(nowMillis)
        if (!hasFix && searchStartedMillis == null) searchStartedMillis = nowMillis
        return current.copy(
            state = if (hasFix) GnssFixState.FIXED else GnssFixState.SEARCHING,
            signalWarning = if (hasFix) current.signalWarning else searchWarning(current, nowMillis),
        )
    }

    fun onStopped(current: GnssState): GnssState {
        reset()
        return current.copy(
            state = GnssFixState.DISABLED,
            visibleSatellites = 0,
            usedSatellites = null,
            horizontalAccuracyMeters = null,
            signalWarning = null,
        )
    }

    fun onNoPermission(): GnssState {
        reset()
        return GnssState(state = GnssFixState.NO_PERMISSION)
    }

    fun onSatelliteStatus(
        current: GnssState,
        visibleSatellites: Int,
        usedSatellites: Int,
        nowMillis: Long,
    ): GnssState {
        val visible = visibleSatellites.coerceAtLeast(0)
        val used = usedSatellites.coerceIn(0, visible)
        rememberVisibleSatellites(visible, nowMillis)
        if (used > 0) {
            rememberFixEvidence(nowMillis)
            rememberReliableUsedSatellites(used, nowMillis)
        }

        val hasFix = hasFreshFixEvidence(nowMillis)
        val reliableUsed = when {
            used > 0 -> used
            hasFreshReliableUsedSatellites(nowMillis) -> lastReliableUsedSatellites
            else -> null
        }

        // Some vendor stacks publish partial or alternating empty/populated status frames. A
        // short trailing maximum prevents those transport details from becoming UI flicker.
        val resolvedVisible = maxOf(
            visibleSatelliteSamples.maxOfOrNull { it.count } ?: 0,
            reliableUsed ?: 0,
        )

        val resolved = current.copy(
            state = if (hasFix) GnssFixState.FIXED else GnssFixState.SEARCHING,
            visibleSatellites = resolvedVisible,
            // Preserve "unknown" only while independent evidence says that a fix exists. During
            // search, zero is truthful and lets the UI distinguish 0/N from missing status data.
            usedSatellites = reliableUsed ?: if (hasFix) null else 0,
        )
        return if (hasFix) resolved else resolved.copy(signalWarning = searchWarning(resolved, nowMillis))
    }

    /** A valid NMEA fix may not contain a trustworthy aggregate satellite count. */
    fun onNmeaFixEvidence(current: GnssState, usedSatellites: Int?, nowMillis: Long): GnssState {
        val reliableUsed = usedSatellites?.takeIf { it > 0 }
        rememberFixEvidence(nowMillis)
        reliableUsed?.let { rememberReliableUsedSatellites(it, nowMillis) }
        return current.copy(
            state = GnssFixState.FIXED,
            visibleSatellites = maxOf(current.visibleSatellites, reliableUsed ?: 0),
            usedSatellites = reliableUsed ?: freshReliableUsedSatellites(nowMillis),
        )
    }

    /** Android calls onFirstFix when the GNSS engine has obtained its first position fix. */
    fun onFirstFix(current: GnssState, nowMillis: Long): GnssState {
        rememberFixEvidence(nowMillis)
        return current.copy(
            state = GnssFixState.FIXED,
            usedSatellites = freshReliableUsedSatellites(nowMillis),
        )
    }

    fun onGpsLocation(
        current: GnssState,
        accuracyMeters: Float?,
        nowMillis: Long,
        usedSatellites: Int? = null,
    ): GnssState {
        rememberFixEvidence(nowMillis)
        val reliableUsed = usedSatellites?.takeIf { it > 0 }
        reliableUsed?.let { rememberReliableUsedSatellites(it, nowMillis) }
        updateAccuracyTracking(accuracyMeters, nowMillis)
        return current.copy(
            state = GnssFixState.FIXED,
            visibleSatellites = maxOf(current.visibleSatellites, reliableUsed ?: 0),
            usedSatellites = reliableUsed ?: freshReliableUsedSatellites(nowMillis),
            horizontalAccuracyMeters = accuracyMeters,
            signalWarning = accuracyWarning(current.signalWarning, nowMillis),
        )
    }

    fun onFixEvidenceTimeout(current: GnssState, nowMillis: Long): GnssState {
        if (current.state != GnssFixState.FIXED || hasFreshFixEvidence(nowMillis)) return current
        searchStartedMillis = nowMillis
        poorAccuracySinceMillis = null
        goodAccuracySinceMillis = null
        return current.copy(
            state = GnssFixState.SEARCHING,
            usedSatellites = 0,
            horizontalAccuracyMeters = null,
            signalWarning = GnssSignalWarning.UNAVAILABLE,
        )
    }

    fun onTimeElapsed(current: GnssState, nowMillis: Long): GnssState {
        if (current.state == GnssFixState.FIXED && !hasFreshFixEvidence(nowMillis)) {
            return onFixEvidenceTimeout(current, nowMillis)
        }
        return when (current.state) {
            GnssFixState.SEARCHING -> current.copy(signalWarning = searchWarning(current, nowMillis))
            GnssFixState.FIXED -> current.copy(
                signalWarning = accuracyWarning(current.signalWarning, nowMillis),
            )
            GnssFixState.DISABLED, GnssFixState.NO_PERMISSION -> {
                if (current.signalWarning == null) current else current.copy(signalWarning = null)
            }
        }
    }

    fun reset() {
        lastFixEvidenceMillis = null
        lastReliableUsedSatellites = null
        lastReliableUsedSatellitesMillis = null
        searchStartedMillis = null
        poorAccuracySinceMillis = null
        goodAccuracySinceMillis = null
        visibleSatelliteSamples.clear()
    }

    private fun rememberFixEvidence(nowMillis: Long) {
        lastFixEvidenceMillis = nowMillis
        searchStartedMillis = null
    }

    private fun updateAccuracyTracking(accuracyMeters: Float?, nowMillis: Long) {
        when {
            accuracyMeters != null && accuracyMeters.isFinite() &&
                accuracyMeters >= POOR_HORIZONTAL_ACCURACY_METERS -> {
                if (poorAccuracySinceMillis == null) poorAccuracySinceMillis = nowMillis
                goodAccuracySinceMillis = null
            }
            accuracyMeters != null && accuracyMeters.isFinite() &&
                accuracyMeters <= GOOD_HORIZONTAL_ACCURACY_METERS -> {
                if (goodAccuracySinceMillis == null) goodAccuracySinceMillis = nowMillis
                poorAccuracySinceMillis = null
            }
            else -> {
                poorAccuracySinceMillis = null
                goodAccuracySinceMillis = null
            }
        }
    }

    private fun searchWarning(current: GnssState, nowMillis: Long): GnssSignalWarning? {
        if (current.signalWarning == GnssSignalWarning.UNAVAILABLE) {
            return GnssSignalWarning.UNAVAILABLE
        }
        if (lastFixEvidenceMillis != null && !hasFreshFixEvidence(nowMillis)) {
            return GnssSignalWarning.UNAVAILABLE
        }
        val started = searchStartedMillis ?: nowMillis.also { searchStartedMillis = it }
        return if (nowMillis - started >= INITIAL_SEARCH_WARNING_MILLIS) {
            GnssSignalWarning.UNAVAILABLE
        } else null
    }

    private fun accuracyWarning(
        current: GnssSignalWarning?,
        nowMillis: Long,
    ): GnssSignalWarning? = when {
        poorAccuracySinceMillis?.let { nowMillis - it >= POOR_ACCURACY_WARNING_MILLIS } == true -> {
            GnssSignalWarning.UNSTABLE
        }
        goodAccuracySinceMillis?.let { nowMillis - it >= GOOD_ACCURACY_RECOVERY_MILLIS } == true -> null
        else -> current
    }

    private fun hasFreshFixEvidence(nowMillis: Long): Boolean =
        lastFixEvidenceMillis?.let { nowMillis - it <= fixEvidenceFreshnessMillis } == true

    private fun rememberReliableUsedSatellites(value: Int, nowMillis: Long) {
        lastReliableUsedSatellites = value
        lastReliableUsedSatellitesMillis = nowMillis
    }

    private fun hasFreshReliableUsedSatellites(nowMillis: Long): Boolean =
        lastReliableUsedSatellitesMillis
            ?.let { nowMillis - it <= usedSatellitesFreshnessMillis } == true

    private fun freshReliableUsedSatellites(nowMillis: Long): Int? =
        lastReliableUsedSatellites.takeIf { hasFreshReliableUsedSatellites(nowMillis) }

    private fun rememberVisibleSatellites(value: Int, nowMillis: Long) {
        visibleSatelliteSamples.addLast(VisibleSatelliteSample(nowMillis, value))
        while (
            visibleSatelliteSamples.firstOrNull()
                ?.let { nowMillis - it.timestampMillis > visibleSatellitesWindowMillis } == true
        ) {
            visibleSatelliteSamples.removeFirst()
        }
    }

    private data class VisibleSatelliteSample(
        val timestampMillis: Long,
        val count: Int,
    )

    companion object {
        const val FIX_EVIDENCE_FRESHNESS_MILLIS = 30_000L
        const val USED_SATELLITES_FRESHNESS_MILLIS = 10_000L
        const val VISIBLE_SATELLITES_WINDOW_MILLIS = 10_000L
        const val INITIAL_SEARCH_WARNING_MILLIS = 60_000L
        const val POOR_ACCURACY_WARNING_MILLIS = 15_000L
        const val GOOD_ACCURACY_RECOVERY_MILLIS = 10_000L
        const val POOR_HORIZONTAL_ACCURACY_METERS = 50f
        const val GOOD_HORIZONTAL_ACCURACY_METERS = 30f
    }
}
