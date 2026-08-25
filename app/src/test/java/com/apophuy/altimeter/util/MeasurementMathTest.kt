package com.apophuy.altimeter.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasurementMathTest {
    @Test
    fun `angles cross north by the shortest path`() {
        assertEquals(350f, normalizeDegrees(-10f), 0.0001f)
        assertEquals(20f, shortestAngleDelta(350f, 10f), 0.0001f)
        assertEquals(-20f, shortestAngleDelta(10f, 350f), 0.0001f)
    }

    @Test
    fun `heading filter crosses north without jumping around the dial`() {
        val filter = HeadingFilter(timeConstantMillis = 350.0)
        assertEquals(359f, filter.add(359f, 1_000_000_000L), 0.001f)
        val next = filter.add(1f, 1_350_000_000L)
        assertTrue(next > 359f || next < 1f)
    }

    @Test
    fun `heading filter damps stationary sensor noise`() {
        val filter = HeadingFilter(timeConstantMillis = 350.0)
        filter.add(100f, 1_000_000_000L)
        val filtered = filter.add(120f, 1_020_000_000L)
        assertTrue(filtered > 100f)
        assertTrue(filtered < 102f)
    }

    @Test
    fun `heading filter rejects a single large spike`() {
        val filter = HeadingFilter()
        repeat(5) { index -> filter.add(100f, 1_000_000_000L + index * 20_000_000L) }

        val filtered = filter.add(280f, 1_100_000_000L)

        assertEquals(100f, filtered, 0.1f)
    }

    @Test
    fun `heading filter holds small stationary jitter`() {
        val filter = HeadingFilter()
        repeat(5) { index -> filter.add(100f, 1_000_000_000L + index * 20_000_000L) }

        val filtered = filter.add(100.8f, 1_100_000_000L)

        assertEquals(100f, filtered, 0.1f)
    }

    @Test
    fun `heading filter follows a sustained turn`() {
        val filter = HeadingFilter()
        repeat(5) { index -> filter.add(90f, 1_000_000_000L + index * 20_000_000L) }
        var filtered = 90f
        repeat(60) { index ->
            filtered = filter.add(180f, 1_100_000_000L + index * 20_000_000L)
        }

        assertTrue(filtered > 170f)
        assertTrue(filtered < 181f)
    }

    @Test
    fun `heading filter ignores out of order sensor events`() {
        val filter = HeadingFilter()
        filter.add(100f, 1_000_000_000L)

        assertEquals(100f, filter.add(250f, 999_000_000L), 0.001f)
    }

    @Test
    fun `lower pressure means positive altitude gain`() {
        val delta = barometricDeltaMeters(1013.25f, 1001.25f)
        assertTrue(delta > 90.0)
        assertTrue(delta < 110.0)
    }

    @Test
    fun `recording threshold uses configured altitude step`() {
        assertTrue(shouldStorePoint(null, 100.0, altitudeStepMeters = 5.0))
        assertFalse(shouldStorePoint(100.0, 104.999, altitudeStepMeters = 5.0))
        assertTrue(shouldStorePoint(100.0, 105.0, altitudeStepMeters = 5.0))
        assertTrue(shouldStorePoint(100.0, 95.0, altitudeStepMeters = 5.0))
    }

    @Test
    fun `median filter suppresses a single spike`() {
        val filter = MedianEmaFilter(windowSize = 5, alpha = 0.25)
        repeat(4) { filter.add(100.0) }
        val result = filter.add(500.0)
        assertEquals(100.0, result, 0.001)
    }

    @Test
    fun `unit conversions use standard factors`() {
        assertEquals(328.0839, metersToFeet(100.0), 0.001)
        assertEquals(32.0, celsiusToFahrenheit(0.0), 0.001)
        assertEquals(29.92, hPaToInHg(1013.25), 0.01)
    }
}
