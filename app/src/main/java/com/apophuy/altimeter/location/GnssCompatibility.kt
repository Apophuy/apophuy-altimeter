package com.apophuy.altimeter.location

import java.util.Locale

internal enum class GnssCompatibility {
    STANDARD,
    VIVO_X100_ULTRA;

    companion object {
        private val X100_ULTRA_MODEL = Regex("V2366(?:G|H)?A")
        private val X100_ULTRA_PROJECT = Regex("PD2366(?:[_-].*)?")

        /**
         * Keep vendor workarounds restricted to confirmed hardware. Other supported phones rely
         * on the standard GnssStatus snapshots and must retain the default behavior.
         */
        fun detect(
            manufacturer: String,
            model: String,
            brand: String = manufacturer,
            device: String = "",
            product: String = "",
            fingerprint: String = "",
        ): GnssCompatibility {
            val vivoVendor = listOf(manufacturer, brand)
                .any { it.trim().lowercase(Locale.ROOT) == "vivo" }
            val normalizedModel = model.normalizedBuildValue()
            val projectValues = listOf(device, product).map { it.normalizedBuildValue() }
            val fingerprintSegments = fingerprint.uppercase(Locale.ROOT).split('/')
            val x100Ultra = X100_ULTRA_MODEL.matches(normalizedModel) ||
                normalizedModel == "VIVO X100 ULTRA" ||
                projectValues.any(X100_ULTRA_PROJECT::matches) ||
                fingerprintSegments.any { it == "PD2366" }

            return if (vivoVendor && x100Ultra) {
                VIVO_X100_ULTRA
            } else {
                STANDARD
            }
        }

        private fun String.normalizedBuildValue(): String = trim().uppercase(Locale.ROOT)
    }
}
