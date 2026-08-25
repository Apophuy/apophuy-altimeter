package com.apophuy.altimeter.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCapabilitiesTest {
    @Test
    fun `rotation vector replaces the fallback compass sensors`() {
        val device = DeviceCapabilities(true, true, false, false, true)
        assertTrue(device.compassAvailable)
        assertTrue(device.missingHardware.isEmpty())
    }

    @Test
    fun `accelerometer and magnetometer work without rotation vector`() {
        val device = DeviceCapabilities(true, false, true, true, true)
        assertTrue(device.compassAvailable)
        assertTrue(device.missingHardware.isEmpty())
    }

    @Test
    fun `missing barometer limits altitude but keeps compass available`() {
        val device = DeviceCapabilities(true, false, true, true, false)
        assertTrue(device.compassAvailable)
        assertEquals(listOf(MissingHardware.BAROMETER), device.missingHardware)
    }

    @Test
    fun `unavailable compass reports the missing input`() {
        val device = DeviceCapabilities(true, false, true, false, true)
        assertFalse(device.compassAvailable)
        assertEquals(listOf(MissingHardware.MAGNETOMETER), device.missingHardware)
    }

    @Test
    fun `all unavailable instruments are reported together`() {
        val device = DeviceCapabilities(false, false, false, false, false)
        assertFalse(device.compassAvailable)
        assertEquals(MissingHardware.entries, device.missingHardware)
    }
}
