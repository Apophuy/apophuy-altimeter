package com.apophuy.altimeter.util

import com.apophuy.altimeter.model.Coordinates
import com.apophuy.altimeter.model.PositionSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PositionSelectionTest {
    @Test
    fun `recent GNSS position is not replaced by a network update`() {
        val current = coordinates(timestampMillis = 95_000L, source = PositionSource.GNSS, accuracy = 5f)
        val network = coordinates(timestampMillis = 99_000L, source = PositionSource.NETWORK, accuracy = 80f)

        assertFalse(shouldReplacePosition(current, network, nowMillis = 100_000L))
    }

    @Test
    fun `network position can replace stale GNSS fallback`() {
        val current = coordinates(timestampMillis = 80_000L, source = PositionSource.GNSS, accuracy = 5f)
        val network = coordinates(timestampMillis = 99_000L, source = PositionSource.NETWORK, accuracy = 40f)

        assertTrue(shouldReplacePosition(current, network, nowMillis = 100_000L))
    }

    @Test
    fun `fresh GNSS position replaces network position`() {
        val current = coordinates(timestampMillis = 99_000L, source = PositionSource.NETWORK, accuracy = 40f)
        val gnss = coordinates(timestampMillis = 100_000L, source = PositionSource.GNSS, accuracy = 8f)

        assertTrue(shouldReplacePosition(current, gnss, nowMillis = 100_000L))
    }

    @Test
    fun `older network callback never replaces a newer position`() {
        val current = coordinates(timestampMillis = 100_000L, source = PositionSource.NETWORK, accuracy = 40f)
        val olderNetwork = coordinates(timestampMillis = 99_000L, source = PositionSource.NETWORK, accuracy = 8f)

        assertFalse(shouldReplacePosition(current, olderNetwork, nowMillis = 100_000L))
    }

    @Test
    fun `fresh GNSS callback can replace a slightly newer network position`() {
        val current = coordinates(timestampMillis = 100_000L, source = PositionSource.NETWORK, accuracy = 40f)
        val gnss = coordinates(timestampMillis = 99_000L, source = PositionSource.GNSS, accuracy = 8f)

        assertTrue(shouldReplacePosition(current, gnss, nowMillis = 100_000L))
    }

    @Test
    fun `waypoint requires fresh accurate GNSS coordinates`() {
        assertTrue(
            isWaypointPositionUsable(
                coordinates(timestampMillis = 100_000L, source = PositionSource.GNSS, accuracy = 25f),
                nowMillis = 100_000L,
            ),
        )
        assertFalse(
            isWaypointPositionUsable(
                coordinates(timestampMillis = 100_000L, source = PositionSource.GNSS, accuracy = 25.1f),
                nowMillis = 100_000L,
            ),
        )
        assertFalse(
            isWaypointPositionUsable(
                coordinates(timestampMillis = 100_000L, source = PositionSource.NETWORK, accuracy = 5f),
                nowMillis = 100_000L,
            ),
        )
        assertFalse(
            isWaypointPositionUsable(
                coordinates(timestampMillis = 89_999L, source = PositionSource.GNSS, accuracy = 5f),
                nowMillis = 100_000L,
            ),
        )
    }

    @Test
    fun `future timestamp is not treated as fresh`() {
        assertFalse(isTimestampFresh(100_001L, 100_000L, 10_000L))
    }

    private fun coordinates(
        timestampMillis: Long,
        source: PositionSource,
        accuracy: Float,
    ) = Coordinates(
        latitude = 55.75,
        longitude = 37.61,
        horizontalAccuracyMeters = accuracy,
        timestampMillis = timestampMillis,
        source = source,
    )
}
