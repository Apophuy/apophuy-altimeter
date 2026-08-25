package com.apophuy.altimeter.ui

import com.apophuy.altimeter.model.DistanceUnit
import com.apophuy.altimeter.model.CoordinateFormat
import com.apophuy.altimeter.model.PressureUnit
import com.apophuy.altimeter.model.TemperatureUnit
import com.apophuy.altimeter.model.UserSettings
import com.apophuy.altimeter.model.WindSpeedUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

class FormattersTest {
    private lateinit var previousLocale: Locale

    @Before
    fun useStableLocale() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(previousLocale)
    }

    @Test
    fun `formats standard pressure in every supported unit`() {
        val pressure = 1_013.25

        assertEquals("760.0 mmHg", formatPressure(pressure, PressureUnit.MILLIMETERS_MERCURY))
        assertEquals("1013.3 hPa", formatPressure(pressure, PressureUnit.HECTOPASCALS))
        assertEquals("1013.3 mbar", formatPressure(pressure, PressureUnit.MILLIBARS))
        assertEquals("101.33 kPa", formatPressure(pressure, PressureUnit.KILOPASCALS))
        assertEquals("1.013 bar", formatPressure(pressure, PressureUnit.BAR))
        assertEquals("1.000 atm", formatPressure(pressure, PressureUnit.ATMOSPHERES))
        assertEquals("29.92 inHg", formatPressure(pressure, PressureUnit.INCHES_MERCURY))
    }

    @Test
    fun `new settings default to millimeters of mercury`() {
        assertEquals(PressureUnit.MILLIMETERS_MERCURY, UserSettings().pressureUnit)
    }

    @Test
    fun `diagnostic logging is opt in`() {
        assertFalse(UserSettings().gnssDiagnosticLoggingEnabled)
    }

    @Test
    fun `uses localized Russian pressure suffix`() {
        Locale.setDefault(Locale.forLanguageTag("ru"))

        assertEquals(
            "760,0 мм рт. ст.",
            formatPressure(1_013.25, PressureUnit.MILLIMETERS_MERCURY),
        )
    }

    @Test
    fun `formats distance temperature and wind independently`() {
        assertEquals("328 ft", formatAltitude(100.0, DistanceUnit.FEET, 0))
        assertEquals("0.100 km", formatAltitude(100.0, DistanceUnit.KILOMETERS, 0))
        assertEquals("32°F", formatTemperature(0.0, TemperatureUnit.FAHRENHEIT))
        assertEquals("273 K", formatTemperature(0.0, TemperatureUnit.KELVIN))
        assertEquals("36.0 km/h", formatWind(10.0, WindSpeedUnit.KILOMETERS_PER_HOUR))
        assertEquals("19.4 kn", formatWind(10.0, WindSpeedUnit.KNOTS))
    }

    @Test
    fun `calibration distance conversion round trips`() {
        DistanceUnit.entries.forEach { unit ->
            val displayed = distanceValueFromMeters(1_234.5, unit)
            assertEquals(1_234.5, distanceValueToMeters(displayed, unit), 0.000_001)
        }
    }

    @Test
    fun `formats decimal dms utm and mgrs coordinates offline`() {
        assertEquals(
            "55.750000, 37.625000",
            formatCoordinates(55.75, 37.625, CoordinateFormat.DECIMAL_DEGREES),
        )
        assertEquals(
            "55°45′00.00″N 37°37′30.00″E",
            formatCoordinates(55.75, 37.625, CoordinateFormat.DMS),
        )
        assertTrue(formatCoordinates(55.75, 37.625, CoordinateFormat.UTM).startsWith("37 N "))
        assertTrue(formatCoordinates(55.75, 37.625, CoordinateFormat.MGRS).startsWith("37U"))
    }

    @Test
    fun `does not invent utm or mgrs coordinates outside library range`() {
        assertEquals("—", formatCoordinates(85.0, 0.0, CoordinateFormat.UTM))
        assertEquals("—", formatCoordinates(-81.0, 0.0, CoordinateFormat.MGRS))
    }
}
