package com.apophuy.altimeter.weather

import com.apophuy.altimeter.model.WeatherKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class WeatherForecastTest {
    private val response = """
        {
          "timezone":"Asia/Kathmandu", "utc_offset_seconds":20700,
          "current":{"temperature_2m":12.0,"weather_code":2,"is_day":0},
          "hourly":{
            "time":["2026-09-06T23:00","2026-09-07T00:00","2026-09-07T01:00"],
            "temperature_2m":[10.0,null,8.0], "weather_code":[3,61,0],
            "precipitation_probability":[0,70,null], "wind_speed_10m":[2.5,3.0], "is_day":[0,0,0]
          },
          "daily":{
            "time":["2026-09-06","2026-09-07","2026-09-08"],
            "temperature_2m_min":[8.0,7.0,null], "temperature_2m_max":[16.0,14.0,12.0],
            "weather_code":[3,61,0], "precipitation_probability_max":[20,90,0],
            "wind_speed_10m_max":[5.0,6.0,4.0]
          }
        }
    """.trimIndent()

    @Test
    fun `forecast uses location timezone and missing values do not shift hours`() {
        val weather = OpenMeteoParser.parseWeather(response, 27.7, 85.3)
        assertFalse(weather.isDay)
        val forecast = weather.forecast!!
        assertEquals("Asia/Kathmandu", forecast.timeZone.id)
        assertEquals(2, forecast.hourly.size)
        assertEquals(Instant.parse("2026-09-06T17:15:00Z").toEpochMilli(), forecast.hourly[0].timestampMillis)
        assertEquals(0, forecast.hourly[0].precipitationProbability)
        assertEquals(WeatherKind.CLEAR, forecast.hourly[1].kind)
        assertNull(forecast.hourly[1].precipitationProbability)
        assertNull(forecast.hourly[1].windSpeedMetersPerSecond)
        assertEquals(2, forecast.daily.size)
        assertEquals(LocalDate.parse("2026-09-07"), forecast.daily[1].date)
        assertEquals(14.0, forecast.daily[1].maxTemperatureC, 0.001)
    }

    @Test
    fun `past forecast is hidden using current time in location timezone`() {
        val forecast = OpenMeteoParser.parseWeather(response, 27.7, 85.3).forecast!!
        val midnight = Instant.parse("2026-09-06T18:15:00Z").toEpochMilli()
        assertEquals(1, forecast.upcomingHours(midnight).size)
        assertEquals(listOf(LocalDate.parse("2026-09-07")), forecast.upcomingDays(midnight).map { it.date })
        val expired = Instant.parse("2026-09-09T00:00:00Z").toEpochMilli()
        assertTrue(forecast.upcomingHours(expired).isEmpty())
        assertTrue(forecast.upcomingDays(expired).isEmpty())
    }

    @Test
    fun `cache preserves forecast and fetch time after restart`() {
        val original = OpenMeteoParser.parseWeather(response, 27.7, 85.3).copy(updatedAtMillis = 123L)
        val restored = original.toCache(response).toModel(stale = true)
        assertEquals(original.forecast, restored.forecast)
        assertEquals(123L, restored.updatedAtMillis)
        assertTrue(restored.stale)
        assertFalse(restored.isDay)
    }

    @Test
    fun `version one and damaged caches retain current weather`() {
        val original = OpenMeteoParser.parseWeather(response, 27.7, 85.3).toCache(response)
        for (json in listOf(null, "broken json")) {
            val restored = original.copy(forecastJson = json).toModel(stale = true)
            assertEquals(12.0, restored.temperatureC, 0.001)
            assertNull(restored.forecast)
            assertTrue(restored.stale)
        }
    }

    @Test
    fun `malformed times and invalid probability do not fabricate a forecast`() {
        val malformed = response.replace("2026-09-06T23:00", "invalid")
            .replace("[0,70,null]", "[0,70,150]")
        val forecast = OpenMeteoParser.parseWeather(malformed, 0.0, 0.0).forecast!!
        assertEquals(1, forecast.hourly.size)
        assertNull(forecast.hourly.single().precipitationProbability)
    }

    @Test
    fun `epoch times distinguish repeated local hours at daylight saving transition`() {
        val json = """
            {
              "timezone":"Europe/Berlin", "utc_offset_seconds":7200,
              "current":{"temperature_2m":12,"weather_code":0},
              "hourly":{"time":[1792886400,1792890000],"temperature_2m":[11,10]},
              "daily":{"time":[1792879200],"temperature_2m_min":[8],"temperature_2m_max":[14]}
            }
        """.trimIndent()
        val forecast = OpenMeteoParser.parseWeather(json, 52.5, 13.4).forecast!!
        assertEquals(2, forecast.hourly.size)
        assertEquals(3_600_000L, forecast.hourly[1].timestampMillis - forecast.hourly[0].timestampMillis)
        val hours = forecast.hourly.map { Instant.ofEpochMilli(it.timestampMillis).atZone(forecast.timeZone).hour }
        assertEquals(listOf(2, 2), hours)
        assertEquals(LocalDate.parse("2026-10-25"), forecast.daily.single().date)
    }

    @Test
    fun `only the next 24 hourly entries are shown`() {
        val parsed = OpenMeteoParser.parseWeather(response, 27.7, 85.3).forecast!!
        val first = parsed.hourly.first()
        val forecast = parsed.copy(hourly = (0..48).map { first.copy(timestampMillis = first.timestampMillis + it * 3_600_000L) })
        assertEquals(24, forecast.upcomingHours(first.timestampMillis + 30 * 60_000L).size)
        assertEquals(first, forecast.upcomingHours(first.timestampMillis + 30 * 60_000L).first())
        assertEquals(first.timestampMillis + 23 * 3_600_000L, forecast.upcomingHours(first.timestampMillis).last().timestampMillis)
    }
}
