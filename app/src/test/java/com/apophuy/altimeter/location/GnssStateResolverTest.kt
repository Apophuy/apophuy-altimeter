package com.apophuy.altimeter.location

import com.apophuy.altimeter.model.GnssFixState
import com.apophuy.altimeter.model.GnssSignalWarning
import com.apophuy.altimeter.model.GnssState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GnssStateResolverTest {
    @Test
    fun initialSearchWarnsOnlyAfterOneMinute() {
        val resolver = GnssStateResolver()
        val searching = resolver.onStarted(GnssState(), nowMillis = 1_000L)

        val beforeTimeout = resolver.onTimeElapsed(searching, nowMillis = 60_999L)
        val afterTimeout = resolver.onTimeElapsed(beforeTimeout, nowMillis = 61_000L)

        assertNull(beforeTimeout.signalWarning)
        assertEquals(GnssSignalWarning.UNAVAILABLE, afterTimeout.signalWarning)
    }

    @Test
    fun lostEstablishedFixWarnsWhenFixEvidenceExpires() {
        val resolver = GnssStateResolver()
        val fixed = resolver.onGpsLocation(GnssState(), accuracyMeters = 8f, nowMillis = 1_000L)

        val expired = resolver.onFixEvidenceTimeout(fixed, nowMillis = 31_001L)

        assertEquals(GnssFixState.SEARCHING, expired.state)
        assertEquals(GnssSignalWarning.UNAVAILABLE, expired.signalWarning)
    }

    @Test
    fun continuouslyPoorAccuracyWarnsAfterFifteenSeconds() {
        val resolver = GnssStateResolver()
        val poor = resolver.onGpsLocation(GnssState(), accuracyMeters = 60f, nowMillis = 1_000L)

        val beforeTimeout = resolver.onTimeElapsed(poor, nowMillis = 15_999L)
        val afterTimeout = resolver.onTimeElapsed(beforeTimeout, nowMillis = 16_000L)

        assertNull(beforeTimeout.signalWarning)
        assertEquals(GnssSignalWarning.UNSTABLE, afterTimeout.signalWarning)
    }

    @Test
    fun warningClearsOnlyAfterTenSecondsOfGoodAccuracy() {
        val resolver = GnssStateResolver()
        val poor = resolver.onGpsLocation(GnssState(), accuracyMeters = 60f, nowMillis = 1_000L)
        val warned = resolver.onTimeElapsed(poor, nowMillis = 16_000L)
        val recovering = resolver.onGpsLocation(warned, accuracyMeters = 20f, nowMillis = 17_000L)

        val beforeRecovery = resolver.onTimeElapsed(recovering, nowMillis = 26_999L)
        val recovered = resolver.onTimeElapsed(beforeRecovery, nowMillis = 27_000L)

        assertEquals(GnssSignalWarning.UNSTABLE, beforeRecovery.signalWarning)
        assertNull(recovered.signalWarning)
    }

    @Test
    fun freshGpsLocationKeepsFixWhenVendorDoesNotMarkUsedSatellites() {
        val resolver = GnssStateResolver()
        val fixed = resolver.onGpsLocation(GnssState(), accuracyMeters = 4f, nowMillis = 1_000L)

        val afterStatus = resolver.onSatelliteStatus(
            current = fixed,
            visibleSatellites = 16,
            usedSatellites = 0,
            nowMillis = 2_000L,
        )

        assertEquals(GnssFixState.FIXED, afterStatus.state)
        assertEquals(16, afterStatus.visibleSatellites)
        assertNull(afterStatus.usedSatellites)
        assertEquals(4f, afterStatus.horizontalAccuracyMeters)
    }

    @Test
    fun nmeaCountIsUsedWhenVendorDoesNotMarkUsedSatellites() {
        val resolver = GnssStateResolver()
        val nmea = resolver.onNmeaFixEvidence(GnssState(), usedSatellites = 12, nowMillis = 1_000L)

        val afterStatus = resolver.onSatelliteStatus(
            current = nmea,
            visibleSatellites = 16,
            usedSatellites = 0,
            nowMillis = 2_000L,
        )

        assertEquals(GnssFixState.FIXED, afterStatus.state)
        assertEquals(12, afterStatus.usedSatellites)
        assertEquals(16, afterStatus.visibleSatellites)
    }

    @Test
    fun emptyVendorFrameDoesNotClearCountsWhileGpsFixIsFresh() {
        val resolver = GnssStateResolver()
        val nmea = resolver.onNmeaFixEvidence(GnssState(), usedSatellites = 12, nowMillis = 1_000L)
        val visible = resolver.onSatelliteStatus(nmea, 16, 0, nowMillis = 2_000L)
        val refreshedFix = resolver.onGpsLocation(visible, accuracyMeters = 4f, nowMillis = 5_000L)

        val empty = resolver.onSatelliteStatus(refreshedFix, 0, 0, nowMillis = 6_000L)

        assertEquals(GnssFixState.FIXED, empty.state)
        assertEquals(12, empty.usedSatellites)
        assertEquals(16, empty.visibleSatellites)
    }

    @Test
    fun transientEmptyVendorFrameKeepsPreviousSatelliteCounts() {
        val resolver = GnssStateResolver()
        val populated = resolver.onSatelliteStatus(
            current = GnssState(),
            visibleSatellites = 16,
            usedSatellites = 7,
            nowMillis = 1_000L,
        )

        val empty = resolver.onSatelliteStatus(
            current = populated,
            visibleSatellites = 0,
            usedSatellites = 0,
            nowMillis = 2_000L,
        )

        assertEquals(16, empty.visibleSatellites)
        assertEquals(7, empty.usedSatellites)
        assertEquals(GnssFixState.FIXED, empty.state)
    }

    @Test
    fun visibleSatellitePeakExpiresAfterSmoothingWindow() {
        val resolver = GnssStateResolver()
        val populated = resolver.onSatelliteStatus(
            current = GnssState(),
            visibleSatellites = 16,
            usedSatellites = 0,
            nowMillis = 1_000L,
        )

        val empty = resolver.onSatelliteStatus(
            current = populated,
            visibleSatellites = 0,
            usedSatellites = 0,
            nowMillis = 11_001L,
        )

        assertEquals(0, empty.visibleSatellites)
        assertEquals(0, empty.usedSatellites)
        assertEquals(GnssFixState.SEARCHING, empty.state)
    }

    @Test
    fun fixExpiresWithoutNewGpsOrUsedInFixEvidence() {
        val resolver = GnssStateResolver()
        val fixed = resolver.onGpsLocation(GnssState(), accuracyMeters = 5f, nowMillis = 1_000L)

        val expired = resolver.onFixEvidenceTimeout(fixed, nowMillis = 31_001L)

        assertEquals(GnssFixState.SEARCHING, expired.state)
        assertNull(expired.horizontalAccuracyMeters)
    }

    @Test
    fun vivoPublishesZeroUsedCountWhileSearchingWithoutFixEvidence() {
        val resolver = GnssStateResolver()

        val searching = resolver.onSatelliteStatus(
            current = GnssState(),
            visibleSatellites = 16,
            usedSatellites = 0,
            nowMillis = 1_000L,
        )

        assertEquals(GnssFixState.SEARCHING, searching.state)
        assertEquals(16, searching.visibleSatellites)
        assertEquals(0, searching.usedSatellites)
    }

    @Test
    fun vivoPublishesZeroUsedCountWhenFixEvidenceExpires() {
        val resolver = GnssStateResolver()
        val fixed = resolver.onGpsLocation(GnssState(), accuracyMeters = 5f, nowMillis = 1_000L)

        val expired = resolver.onFixEvidenceTimeout(fixed, nowMillis = 31_001L)

        assertEquals(GnssFixState.SEARCHING, expired.state)
        assertEquals(0, expired.usedSatellites)
    }

    @Test
    fun androidFirstFixMovesVivoFromSearchingToFixed() {
        val resolver = GnssStateResolver()
        val searching = resolver.onSatelliteStatus(GnssState(), 18, 0, nowMillis = 1_000L)

        val fixed = resolver.onFirstFix(searching, nowMillis = 2_000L)

        assertEquals(GnssFixState.FIXED, fixed.state)
        assertEquals(18, fixed.visibleSatellites)
        assertNull(fixed.usedSatellites)
    }

    @Test
    fun androidFirstFixExpiresWithoutRepeatedLocationOrNmeaEvidence() {
        val resolver = GnssStateResolver()
        val fixed = resolver.onFirstFix(GnssState(), nowMillis = 1_000L)

        val expired = resolver.onFixEvidenceTimeout(fixed, nowMillis = 31_001L)

        assertEquals(GnssFixState.SEARCHING, expired.state)
        assertEquals(0, expired.usedSatellites)
    }

    @Test
    fun nmeaFixWithoutCountMovesVivoToFixedWithoutFabricatingUsedSatellites() {
        val resolver = GnssStateResolver()
        val searching = resolver.onSatelliteStatus(GnssState(), 18, 0, nowMillis = 1_000L)

        val fixed = resolver.onNmeaFixEvidence(searching, usedSatellites = null, nowMillis = 2_000L)

        assertEquals(GnssFixState.FIXED, fixed.state)
        assertEquals(18, fixed.visibleSatellites)
        assertNull(fixed.usedSatellites)
    }

    @Test
    fun vivoGpsLocationCanUseLegacySatelliteCountExtra() {
        val resolver = GnssStateResolver()

        val fixed = resolver.onGpsLocation(
            current = GnssState(),
            accuracyMeters = 4f,
            nowMillis = 2_000L,
            usedSatellites = 11,
        )

        assertEquals(GnssFixState.FIXED, fixed.state)
        assertEquals(11, fixed.visibleSatellites)
        assertEquals(11, fixed.usedSatellites)
    }

    @Test
    fun partialFramesUseTrailingMaximumForVisibleSatelliteCount() {
        val resolver = GnssStateResolver()
        val first = resolver.onSatelliteStatus(GnssState(), 18, 0, nowMillis = 1_000L)
        val partial = resolver.onSatelliteStatus(first, 6, 0, nowMillis = 5_000L)

        assertEquals(18, partial.visibleSatellites)

        val afterWindow = resolver.onSatelliteStatus(partial, 6, 0, nowMillis = 11_001L)

        assertEquals(6, afterWindow.visibleSatellites)
    }

    @Test
    fun staleUsedCountBecomesUnknownWhileRecentFixRemainsValid() {
        val resolver = GnssStateResolver()
        val fixed = resolver.onSatelliteStatus(GnssState(), 16, 7, nowMillis = 1_000L)

        val staleCount = resolver.onSatelliteStatus(fixed, 12, 0, nowMillis = 11_001L)

        assertEquals(GnssFixState.FIXED, staleCount.state)
        assertNull(staleCount.usedSatellites)
        assertEquals(12, staleCount.visibleSatellites)
    }
}
