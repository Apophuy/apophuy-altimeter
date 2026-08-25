package com.apophuy.altimeter.location

/** Keeps every device-specific runtime decision in one testable place. */
internal class GnssRuntimePolicy(
    val compatibility: GnssCompatibility,
) {
    val usesDedicatedGpsListener: Boolean
        get() = compatibility == GnssCompatibility.VIVO_X100_ULTRA

    val usesLegacySatelliteExtra: Boolean
        get() = compatibility == GnssCompatibility.VIVO_X100_ULTRA

    // Samsung S24 / Android 16 accepted the modern request but stopped producing GPS fixes.
    // Keep the proven legacy overload for standard devices; this request is a VIVO workaround.
    fun shouldUseModernGpsRequest(androidSdk: Int): Boolean =
        compatibility == GnssCompatibility.VIVO_X100_ULTRA && androidSdk >= MODERN_REQUEST_MIN_SDK

    fun shouldUsePlatformFusedRecovery(androidSdk: Int): Boolean =
        compatibility == GnssCompatibility.VIVO_X100_ULTRA && androidSdk >= MODERN_REQUEST_MIN_SDK

    fun shouldUseCurrentGpsProbe(androidSdk: Int): Boolean =
        compatibility == GnssCompatibility.VIVO_X100_ULTRA && androidSdk >= MODERN_REQUEST_MIN_SDK

    val requestsGnssAssistance: Boolean
        get() = compatibility == GnssCompatibility.VIVO_X100_ULTRA

    fun shouldUseFullTracking(androidSdk: Int): Boolean =
        compatibility == GnssCompatibility.VIVO_X100_ULTRA && androidSdk >= FULL_TRACKING_MIN_SDK

    fun isGpsLocationFromSharedListener(reportedProvider: String?): Boolean =
        compatibility == GnssCompatibility.STANDARD && reportedProvider == GPS_PROVIDER

    /**
     * OriginOS can return a Location whose provider is "network" even when it was delivered to a
     * listener registered for the platform fused provider. Altitude and vertical accuracy are not
     * proof of a satellite fix: VIVO adds both to network locations. Only an explicitly attributed
     * GPS child is accepted here; unattributed fused locations remain useful coordinates but never
     * turn the GNSS indicator green.
     */
    fun isGpsLocationFromVivoFusedListener(reportedProvider: String?): Boolean =
        compatibility == GnssCompatibility.VIVO_X100_ULTRA && reportedProvider == GPS_PROVIDER

    private companion object {
        const val MODERN_REQUEST_MIN_SDK = 31
        const val FULL_TRACKING_MIN_SDK = 31
        const val GPS_PROVIDER = "gps"
    }
}
