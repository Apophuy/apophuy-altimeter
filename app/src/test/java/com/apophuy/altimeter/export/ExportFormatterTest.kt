package com.apophuy.altimeter.export

import com.apophuy.altimeter.data.local.TrackPointEntity
import com.apophuy.altimeter.data.local.TrackSessionEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportFormatterTest {
    private val session = TrackSessionEntity(id = 7, startedAtMillis = 0L)
    private val located = TrackPointEntity(
        id = 1,
        sessionId = 7,
        timestampMillis = 0L,
        latitude = 55.7558,
        longitude = 37.6173,
        altitudeMetersMsl = 156.25,
        pressureHpa = 995.2f,
        horizontalAccuracyMeters = 4.2f,
        verticalAccuracyMeters = 8.0f,
        altitudeSource = "FUSED",
    )
    private val pressureOnly = located.copy(id = 2, timestampMillis = 1_000L, latitude = null, longitude = null)

    @Test
    fun `gpx contains located points and skips coordinate-free points`() {
        val gpx = ExportFormatter.gpx(session, listOf(located, pressureOnly))
        assertTrue(gpx.contains("<gpx version=\"1.1\""))
        assertTrue(gpx.contains("lat=\"55.7558000\""))
        assertTrue(gpx.contains("<ele>156.25</ele>"))
        assertTrue(gpx.contains("1970-01-01T00:00:00Z"))
        assertFalse(gpx.contains("1970-01-01T00:00:01Z"))
    }

    @Test
    fun `csv preserves coordinate-free pressure points`() {
        val csv = ExportFormatter.csv(listOf(located, pressureOnly))
        assertTrue(csv.startsWith("timestamp_utc,latitude,longitude"))
        assertTrue(csv.contains("1970-01-01T00:00:01Z,,,156.25"))
        assertTrue(csv.lines().size >= 3)
    }
}
