package com.apophuy.altimeter.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NmeaFixParserTest {
    @Test
    fun parsesUsedSatellitesFromMultiConstellationGga() {
        val sentence = "\$GNGGA,092725.00,5545.1234,N,03736.1234,E,1,12,0.8,156.2,M,0.0,M,,*62"

        val evidence = NmeaFixParser.inspect(sentence)

        assertEquals(true, evidence?.hasFix)
        assertEquals(12, evidence?.usedSatellites)
    }

    @Test
    fun ignoresGgaWithoutFix() {
        val sentence = "\$GPGGA,092725.00,,,,,0,00,99.9,,,,,,*48"

        val evidence = NmeaFixParser.inspect(sentence)

        assertEquals(false, evidence?.hasFix)
        assertNull(evidence?.usedSatellites)
    }

    @Test
    fun acceptsValidGgaFixEvenWhenVendorUsedCountIsZero() {
        val sentence = "\$GNGGA,092725.00,5545.1234,N,03736.1234,E,1,00,0.8,156.2,M,0.0,M,,*62"

        val evidence = NmeaFixParser.inspect(sentence)

        assertEquals(true, evidence?.hasFix)
        assertEquals("GGA", evidence?.sentenceType)
        assertEquals("GN", evidence?.talker)
        assertNull(evidence?.usedSatellites)
    }

    @Test
    fun parsesGnsFixAndAggregateUsedCount() {
        val sentence = "\$GNGNS,092725.00,5545.1234,N,03736.1234,E,AA,14,0.8,156.2,0.0,,*62"

        val evidence = NmeaFixParser.inspect(sentence)

        assertEquals(true, evidence?.hasFix)
        assertEquals("GNS", evidence?.sentenceType)
        assertEquals(14, evidence?.usedSatellites)
    }

    @Test
    fun acceptsGsaFixWithoutPublishingPartialConstellationCount() {
        val sentence = "\$GPGSA,A,3,03,04,09,16,26,31,,,,,,,1.8,1.0,1.5*33"

        val evidence = NmeaFixParser.inspect(sentence)

        assertEquals(true, evidence?.hasFix)
        assertEquals("GSA", evidence?.sentenceType)
        assertNull(evidence?.usedSatellites)
    }

    @Test
    fun acceptsOnlyGnssBackedRmcFixesWithCoordinates() {
        val fixed = "\$GNRMC,092725.00,A,5545.1234,N,03736.1234,E,0.1,10.0,250826,,,A*62"
        val estimated = "\$GNRMC,092725.00,A,5545.1234,N,03736.1234,E,0.1,10.0,250826,,,E*62"
        val missingCoordinates = "\$GNRMC,092725.00,A,,,,,0.0,0.0,250826,,,A*62"

        val evidence = NmeaFixParser.inspect(fixed)

        assertEquals("RMC", evidence?.sentenceType)
        assertEquals(true, evidence?.hasFix)
        assertNull(evidence?.usedSatellites)
        assertEquals(false, NmeaFixParser.inspect(estimated)?.hasFix)
        assertEquals(false, NmeaFixParser.inspect(missingCoordinates)?.hasFix)
    }

    @Test
    fun rejectsNmeaSentencesThatExplicitlyReportNoGnssFix() {
        val gns = "\$GNGNS,092725.00,5545.1234,N,03736.1234,E,NN,09,99.9,,,,*62"
        val gsa = "\$GPGSA,A,1,,,,,,,,,,,,,99.9,99.9,99.9*33"
        val rmc = "\$GNRMC,092725.00,V,5545.1234,N,03736.1234,E,0.1,10.0,250826,,,N*62"

        assertEquals(false, NmeaFixParser.inspect(gns)?.hasFix)
        assertEquals(false, NmeaFixParser.inspect(gsa)?.hasFix)
        assertEquals(false, NmeaFixParser.inspect(rmc)?.hasFix)
    }

    @Test
    fun ignoresOtherOrMalformedSentences() {
        assertNull(NmeaFixParser.inspect("\$GPGSV,1,1,01,01,45,180,30*00"))
        assertNull(NmeaFixParser.inspect("not-nmea"))
    }
}
