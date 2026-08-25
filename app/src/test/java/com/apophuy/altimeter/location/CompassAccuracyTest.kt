package com.apophuy.altimeter.location

import android.hardware.SensorManager
import com.apophuy.altimeter.model.CompassAccuracy
import org.junit.Assert.assertEquals
import org.junit.Test

class CompassAccuracyTest {
    @Test
    fun `maps every Android sensor accuracy level`() {
        assertEquals(CompassAccuracy.UNKNOWN, compassAccuracyFromSensorStatus(null))
        assertEquals(CompassAccuracy.UNRELIABLE, compassAccuracyFromSensorStatus(SensorManager.SENSOR_STATUS_UNRELIABLE))
        assertEquals(CompassAccuracy.LOW, compassAccuracyFromSensorStatus(SensorManager.SENSOR_STATUS_ACCURACY_LOW))
        assertEquals(CompassAccuracy.MEDIUM, compassAccuracyFromSensorStatus(SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM))
        assertEquals(CompassAccuracy.HIGH, compassAccuracyFromSensorStatus(SensorManager.SENSOR_STATUS_ACCURACY_HIGH))
    }
}
