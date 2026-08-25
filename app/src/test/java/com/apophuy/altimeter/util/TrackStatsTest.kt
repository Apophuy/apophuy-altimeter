package com.apophuy.altimeter.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackStatsTest {
    @Test
    fun `gain loss minimum and maximum accumulate independently`() {
        var progress = TrackProgress()
        var previous: Double? = null
        listOf(100.0, 104.0, 102.0, 109.0, 105.0).forEach { altitude ->
            progress = nextTrackProgress(progress, previous, altitude)
            previous = altitude
        }

        assertEquals(100.0, progress.minimumMeters!!, 0.001)
        assertEquals(109.0, progress.maximumMeters!!, 0.001)
        assertEquals(11.0, progress.gainMeters, 0.001)
        assertEquals(6.0, progress.lossMeters, 0.001)
        assertEquals(5, progress.pointCount)
    }
}
