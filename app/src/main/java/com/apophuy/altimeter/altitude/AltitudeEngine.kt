package com.apophuy.altimeter.altitude

import android.content.Context
import android.location.Location
import androidx.core.location.LocationCompat
import androidx.core.location.altitude.AltitudeConverterCompat
import com.apophuy.altimeter.data.SettingsRepository
import com.apophuy.altimeter.model.AltitudeReading
import com.apophuy.altimeter.model.AltitudeSource
import com.apophuy.altimeter.util.MedianEmaFilter
import com.apophuy.altimeter.util.barometricDeltaMeters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class AltitudeEngine(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private data class PressureAnchor(val altitudeMetersMsl: Double, val pressureHpa: Float)

    private val filter = MedianEmaFilter(windowSize = 5, alpha = 0.25)
    private val _altitude = MutableStateFlow<AltitudeReading?>(null)
    val altitude: StateFlow<AltitudeReading?> = _altitude

    private var pressureHpa: Float? = null
    private var anchor: PressureAnchor? = null
    private var lastAccuracy: Float? = null
    private var lastFilteredRaw: Double? = null
    private var lastRawSource: AltitudeSource = AltitudeSource.GNSS
    private val conversionInFlight = AtomicBoolean(false)

    init {
        scope.launch {
            settingsRepository.settings.collect { publishAdjusted() }
        }
    }

    @Synchronized
    fun onPressure(valueHpa: Float, timestampMillis: Long = System.currentTimeMillis()) {
        if (!valueHpa.isFinite() || valueHpa !in 300f..1_100f) return
        pressureHpa = valueHpa
        val currentAnchor = anchor ?: return
        val raw = currentAnchor.altitudeMetersMsl +
            barometricDeltaMeters(currentAnchor.pressureHpa, valueHpa)
        acceptRaw(raw, AltitudeSource.FUSED, timestampMillis)
    }

    fun onLocation(location: Location) {
        if (!location.hasAltitude()) return
        if (!conversionInFlight.compareAndSet(false, true)) return
        val copy = Location(location)
        scope.launch(Dispatchers.IO) {
            try {
                val msl = convertToMsl(copy) ?: return@launch
                val accuracy = copy.verticalAccuracyMeters.takeIf { copy.hasVerticalAccuracy() }
                onMslLocation(msl, accuracy, copy.time.takeIf { it > 0 } ?: System.currentTimeMillis())
            } finally {
                conversionInFlight.set(false)
            }
        }
    }

    @Synchronized
    private fun onMslLocation(mslMeters: Double, accuracy: Float?, timestampMillis: Long) {
        if (!mslMeters.isFinite()) return
        lastAccuracy = accuracy
        val goodAnchor = accuracy == null || accuracy <= 30f
        val pressure = pressureHPaOrNull()

        if (pressure != null && goodAnchor) {
            val currentAnchor = anchor
            anchor = if (currentAnchor == null) {
                PressureAnchor(mslMeters, pressure)
            } else {
                val predicted = currentAnchor.altitudeMetersMsl +
                    barometricDeltaMeters(currentAnchor.pressureHpa, pressure)
                val gentlyCorrected = currentAnchor.altitudeMetersMsl + (mslMeters - predicted) * 0.05
                currentAnchor.copy(altitudeMetersMsl = gentlyCorrected)
            }
            val activeAnchor = anchor!!
            val raw = activeAnchor.altitudeMetersMsl +
                barometricDeltaMeters(activeAnchor.pressureHpa, pressure)
            acceptRaw(raw, AltitudeSource.FUSED, timestampMillis)
        } else if (pressure == null) {
            acceptRaw(mslMeters, AltitudeSource.GNSS, timestampMillis)
        } else if (_altitude.value == null) {
            // A poor first fix is still more useful than an empty dashboard. It is not used
            // to calibrate the pressure channel until a better fix arrives.
            acceptRaw(mslMeters, AltitudeSource.GNSS, timestampMillis)
        }
    }

    @Synchronized
    private fun acceptRaw(rawMeters: Double, source: AltitudeSource, timestampMillis: Long) {
        lastFilteredRaw = filter.add(rawMeters)
        lastRawSource = source
        publishAdjusted(timestampMillis)
    }

    @Synchronized
    private fun publishAdjusted(timestampMillis: Long = System.currentTimeMillis()) {
        val raw = lastFilteredRaw ?: return
        val offset = settingsRepository.settings.value.calibrationOffsetMeters
        _altitude.value = AltitudeReading(
            metersMsl = raw + offset,
            accuracyMeters = lastAccuracy,
            source = if (kotlin.math.abs(offset) > 0.0001) AltitudeSource.MANUAL else lastRawSource,
            timestampMillis = timestampMillis,
            manuallyAdjusted = kotlin.math.abs(offset) > 0.0001,
        )
    }

    @Synchronized
    private fun pressureHPaOrNull(): Float? = pressureHpa

    private fun convertToMsl(location: Location): Double? {
        if (LocationCompat.hasMslAltitude(location)) {
            return LocationCompat.getMslAltitudeMeters(location)
        }
        return try {
            AltitudeConverterCompat.addMslAltitudeToLocation(context, location)
            if (LocationCompat.hasMslAltitude(location)) LocationCompat.getMslAltitudeMeters(location) else null
        } catch (_: IOException) {
            // Raw WGS84 height is a last-resort fallback when the geoid asset cannot load.
            location.altitude.takeIf { location.hasAltitude() }
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
