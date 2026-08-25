package com.apophuy.altimeter.model

enum class MissingHardware { GPS, ACCELEROMETER, MAGNETOMETER, BAROMETER }

/** Hardware availability, independent of permissions, provider switches and sensor accuracy. */
data class DeviceCapabilities(
    val gps: Boolean,
    val rotationVector: Boolean,
    val accelerometer: Boolean,
    val magnetometer: Boolean,
    val barometer: Boolean,
) {
    val compassAvailable: Boolean get() = rotationVector || (accelerometer && magnetometer)

    val missingHardware: List<MissingHardware>
        get() = buildList {
            if (!gps) add(MissingHardware.GPS)
            if (!compassAvailable) {
                if (!accelerometer) add(MissingHardware.ACCELEROMETER)
                if (!magnetometer) add(MissingHardware.MAGNETOMETER)
            }
            if (!barometer) add(MissingHardware.BAROMETER)
        }
}
