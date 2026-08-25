package com.apophuy.altimeter.util

import com.apophuy.altimeter.model.AlertDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoMathTest {
    @Test
    fun `initial bearing follows cardinal directions`() {
        assertEquals(0.0, initialBearingDegrees(0.0, 0.0, 1.0, 0.0), 0.001)
        assertEquals(90.0, initialBearingDegrees(0.0, 0.0, 0.0, 1.0), 0.001)
    }

    @Test
    fun `target navigation filter crosses north without circling the dial`() {
        val filter = TargetNavigationFilter(bearingAlpha = 0.5f, distanceAlpha = 0.5)
        filter.add(358f, 300.0)

        val result = filter.add(2f, 298.0)

        assertTrue(result.bearingDegrees > 359f || result.bearingDegrees < 1f)
        assertEquals(299.0, result.distanceMeters, 0.001)
    }

    @Test
    fun `target direction prefers compass and falls back to movement course`() {
        val compass = resolveTargetDirection(
            targetBearingDegrees = 120f,
            positionDirectionReliable = true,
            compassHeadingDegrees = 100f,
            compassReliable = true,
            movementCourseDegrees = 80f,
        )
        assertEquals(TargetDirectionReference.COMPASS, compass.reference)
        assertEquals(20f, compass.degreesRelativeToReference!!, 0.001f)

        val course = resolveTargetDirection(
            targetBearingDegrees = 120f,
            positionDirectionReliable = true,
            compassHeadingDegrees = 100f,
            compassReliable = false,
            movementCourseDegrees = 80f,
        )
        assertEquals(TargetDirectionReference.GPS_COURSE, course.reference)
        assertEquals(40f, course.degreesRelativeToReference!!, 0.001f)
    }

    @Test
    fun `target direction is hidden inside position uncertainty radius`() {
        val direction = resolveTargetDirection(
            targetBearingDegrees = 120f,
            positionDirectionReliable = false,
            compassHeadingDegrees = 100f,
            compassReliable = true,
            movementCourseDegrees = 80f,
        )

        assertEquals(TargetDirectionReference.POSITION_UNCERTAIN, direction.reference)
        assertNull(direction.degreesRelativeToReference)
    }

    @Test
    fun `threshold crossing reports each direction`() {
        assertEquals(AlertDirection.ASCENDING, crossedAltitudeThreshold(99.0, 100.0, 100.0))
        assertEquals(AlertDirection.DESCENDING, crossedAltitudeThreshold(101.0, 100.0, 100.0))
        assertNull(crossedAltitudeThreshold(100.0, 101.0, 100.0))
    }

    @Test
    fun `altitude alert tracker fires each direction once per session`() {
        val tracker = AltitudeAlertTracker()
        tracker.startSession()

        assertNull(tracker.crossingCandidate(1, 100.0, 90.0))
        assertEquals(AlertDirection.ASCENDING, tracker.crossingCandidate(1, 100.0, 110.0))
        tracker.markTriggered(1, AlertDirection.ASCENDING)
        assertEquals(AlertDirection.DESCENDING, tracker.crossingCandidate(1, 100.0, 90.0))
        tracker.markTriggered(1, AlertDirection.DESCENDING)
        assertNull(tracker.crossingCandidate(1, 100.0, 110.0))
    }

    @Test
    fun `baseline reset avoids false crossing after calibration`() {
        val tracker = AltitudeAlertTracker()
        tracker.startSession()
        assertNull(tracker.crossingCandidate(1, 100.0, 90.0))
        tracker.resetBaselines()
        assertNull(tracker.crossingCandidate(1, 100.0, 110.0))
        assertEquals(AlertDirection.DESCENDING, tracker.crossingCandidate(1, 100.0, 90.0))
    }

    @Test
    fun `persisted event suppresses duplicate after service restart`() {
        val tracker = AltitudeAlertTracker()
        tracker.startSession(setOf(1L to AlertDirection.ASCENDING))
        assertNull(tracker.crossingCandidate(1, 100.0, 90.0))
        assertNull(tracker.crossingCandidate(1, 100.0, 110.0))
    }

    @Test
    fun `position freshness uses the ten second boundary`() {
        assertEquals(true, isTimestampFresh(90_000L, 100_000L, 10_000L))
        assertEquals(false, isTimestampFresh(89_999L, 100_000L, 10_000L))
    }
}
