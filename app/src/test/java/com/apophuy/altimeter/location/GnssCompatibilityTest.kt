package com.apophuy.altimeter.location

import org.junit.Assert.assertEquals
import org.junit.Test

class GnssCompatibilityTest {
    @Test
    fun detectsOfficialAndFirmwareVivoX100UltraIdentifiers() {
        assertEquals(
            GnssCompatibility.VIVO_X100_ULTRA,
            GnssCompatibility.detect(manufacturer = "vivo", model = "V2366GA"),
        )
        assertEquals(
            GnssCompatibility.VIVO_X100_ULTRA,
            GnssCompatibility.detect(manufacturer = "VIVO", model = "v2366ha"),
        )
        assertEquals(
            GnssCompatibility.VIVO_X100_ULTRA,
            GnssCompatibility.detect(manufacturer = "vivo", model = "V2366A"),
        )
        assertEquals(
            GnssCompatibility.VIVO_X100_ULTRA,
            GnssCompatibility.detect(
                manufacturer = "vivo",
                model = "X100 Ultra",
                product = "PD2366_A",
            ),
        )
        assertEquals(
            GnssCompatibility.VIVO_X100_ULTRA,
            GnssCompatibility.detect(
                manufacturer = "unknown",
                brand = "vivo",
                model = "vivo X100 Ultra",
            ),
        )
    }

    @Test
    fun doesNotApplyVivoWorkaroundToOtherDevices() {
        assertEquals(
            GnssCompatibility.STANDARD,
            GnssCompatibility.detect(manufacturer = "vivo", model = "V2324A"),
        )
        assertEquals(
            GnssCompatibility.STANDARD,
            GnssCompatibility.detect(manufacturer = "samsung", model = "V2366GA"),
        )
        assertEquals(
            GnssCompatibility.STANDARD,
            GnssCompatibility.detect(manufacturer = "samsung", model = "SM-S921B"),
        )
        assertEquals(
            GnssCompatibility.STANDARD,
            GnssCompatibility.detect(manufacturer = "Sony", model = "XQ-EC54"),
        )
    }
}
