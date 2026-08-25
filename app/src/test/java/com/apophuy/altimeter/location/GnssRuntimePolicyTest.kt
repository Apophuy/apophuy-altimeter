package com.apophuy.altimeter.location

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GnssRuntimePolicyTest {
    @Test
    fun standardDevicesKeepTheSharedAndroidPath() {
        val policy = GnssRuntimePolicy(GnssCompatibility.STANDARD)

        assertFalse(policy.usesDedicatedGpsListener)
        assertFalse(policy.usesLegacySatelliteExtra)
        assertFalse(policy.shouldUseModernGpsRequest(androidSdk = 30))
        assertFalse(policy.shouldUseModernGpsRequest(androidSdk = 31))
        assertFalse(policy.shouldUseModernGpsRequest(androidSdk = 36))
        assertFalse(policy.shouldUsePlatformFusedRecovery(androidSdk = 36))
        assertFalse(policy.shouldUseCurrentGpsProbe(androidSdk = 36))
        assertFalse(policy.requestsGnssAssistance)
        assertFalse(policy.shouldUseFullTracking(androidSdk = 36))
        assertTrue(policy.isGpsLocationFromSharedListener("gps"))
        assertFalse(policy.isGpsLocationFromSharedListener("network"))
        assertFalse(policy.isGpsLocationFromVivoFusedListener("gps"))
    }

    @Test
    fun vivoUsesOnlyItsDedicatedWorkarounds() {
        val policy = GnssRuntimePolicy(GnssCompatibility.VIVO_X100_ULTRA)

        assertTrue(policy.usesDedicatedGpsListener)
        assertTrue(policy.usesLegacySatelliteExtra)
        assertFalse(policy.shouldUseModernGpsRequest(androidSdk = 30))
        assertTrue(policy.shouldUseModernGpsRequest(androidSdk = 31))
        assertFalse(policy.shouldUsePlatformFusedRecovery(androidSdk = 30))
        assertTrue(policy.shouldUsePlatformFusedRecovery(androidSdk = 31))
        assertFalse(policy.shouldUseCurrentGpsProbe(androidSdk = 30))
        assertTrue(policy.shouldUseCurrentGpsProbe(androidSdk = 31))
        assertTrue(policy.requestsGnssAssistance)
        assertFalse(policy.shouldUseFullTracking(androidSdk = 30))
        assertTrue(policy.shouldUseFullTracking(androidSdk = 31))
        assertFalse(policy.isGpsLocationFromSharedListener("gps"))
    }

    @Test
    fun vivoAcceptsOnlyExplicitGpsProviderFromFusedListenerAsFixEvidence() {
        val policy = GnssRuntimePolicy(GnssCompatibility.VIVO_X100_ULTRA)

        assertTrue(policy.isGpsLocationFromVivoFusedListener("gps"))
        assertFalse(policy.isGpsLocationFromVivoFusedListener("network"))
        assertFalse(policy.isGpsLocationFromVivoFusedListener("fused"))
        assertFalse(policy.isGpsLocationFromVivoFusedListener(null))
    }
}
