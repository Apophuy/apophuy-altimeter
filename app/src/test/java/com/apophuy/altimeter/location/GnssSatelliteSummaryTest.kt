package com.apophuy.altimeter.location

import org.junit.Assert.assertEquals
import org.junit.Test

class GnssSatelliteSummaryTest {
    @Test
    fun repeatedFrequencyBandsCountAsOnePhysicalSatellite() {
        val summary = summarizeGnssSatellites(
            listOf(
                GnssSatelliteSignal(constellation = 5, svid = 32, usedInFix = true),
                GnssSatelliteSignal(constellation = 5, svid = 32, usedInFix = true),
                GnssSatelliteSignal(constellation = 5, svid = 32, usedInFix = false),
                GnssSatelliteSignal(constellation = 1, svid = 32, usedInFix = false),
                GnssSatelliteSignal(constellation = 1, svid = 10, usedInFix = true),
            ),
        )

        assertEquals(3, summary.visibleSatellites)
        assertEquals(2, summary.usedSatellites)
    }
}
