package com.apophuy.altimeter.weather

import com.apophuy.altimeter.model.WeatherKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenMeteoParserTest {
    @Test
    fun `current weather fields and WMO code are parsed`() {
        val json = """
            {
              "current": {
                "temperature_2m": 12.4,
                "apparent_temperature": 10.1,
                "relative_humidity_2m": 74,
                "weather_code": 61,
                "wind_speed_10m": 3.5,
                "surface_pressure": 995.2
              }
            }
        """.trimIndent()

        val weather = OpenMeteoParser.parseWeather(json, 55.75, 37.61)
        assertEquals(12.4, weather.temperatureC, 0.001)
        assertEquals(WeatherKind.RAIN, weather.kind)
        assertEquals(74, weather.humidityPercent)
        assertEquals(995.2, weather.surfacePressureHpa!!, 0.001)
    }

    @Test
    fun `optional weather fields may be missing`() {
        val weather = OpenMeteoParser.parseWeather(
            """{"current":{"temperature_2m":2.0,"weather_code":45}}""",
            0.0,
            0.0,
        )
        assertEquals(WeatherKind.FOG, weather.kind)
        assertNull(weather.apparentTemperatureC)
        assertNull(weather.windSpeedMetersPerSecond)
    }

    @Test
    fun `terrain response is parsed`() {
        val terrain = OpenMeteoParser.parseTerrain("""{"elevation":[156.0]}""", 1.0, 2.0)
        assertEquals(156.0, terrain.meters, 0.001)
    }
}
