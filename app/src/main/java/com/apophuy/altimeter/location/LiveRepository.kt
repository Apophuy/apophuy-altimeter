package com.apophuy.altimeter.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.location.GnssMeasurement
import android.location.GnssMeasurementRequest
import android.location.GnssMeasurementsEvent
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import android.location.OnNmeaMessageListener
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Surface
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.apophuy.altimeter.altitude.AltitudeEngine
import com.apophuy.altimeter.model.CompassAccuracy
import com.apophuy.altimeter.model.CompassReading
import com.apophuy.altimeter.model.Coordinates
import com.apophuy.altimeter.model.DeviceCapabilities
import com.apophuy.altimeter.model.GnssFixState
import com.apophuy.altimeter.model.LiveReading
import com.apophuy.altimeter.model.MovementCourse
import com.apophuy.altimeter.model.NorthReference
import com.apophuy.altimeter.model.PositionSource
import com.apophuy.altimeter.model.PressureReading
import com.apophuy.altimeter.model.PressureSource
import com.apophuy.altimeter.util.distanceMeters
import com.apophuy.altimeter.util.HeadingFilter
import com.apophuy.altimeter.util.currentAppLocale
import com.apophuy.altimeter.util.normalizeDegrees
import com.apophuy.altimeter.util.shouldReplacePosition
import com.apophuy.altimeter.weather.OpenMeteoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class LiveRepository(
    private val context: Context,
    private val altitudeEngine: AltitudeEngine,
    private val openMeteoRepository: OpenMeteoRepository,
    private val scope: CoroutineScope,
    private val diagnosticLogger: GnssDiagnosticLogger,
) : SensorEventListener, LocationListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val gnssCompatibility = GnssCompatibility.detect(
        manufacturer = Build.MANUFACTURER,
        brand = Build.BRAND,
        model = Build.MODEL,
        device = Build.DEVICE,
        product = Build.PRODUCT,
        fingerprint = Build.FINGERPRINT,
    )
    private val gnssPolicy = GnssRuntimePolicy(gnssCompatibility)
    private val gnssStateResolver = GnssStateResolver()

    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
    private val deviceCapabilities = DeviceCapabilities(
        gps = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS),
        rotationVector = rotationSensor != null,
        accelerometer = accelerometer != null,
        magnetometer = magnetometer != null,
        barometer = pressureSensor != null,
    )

    private val owners = mutableSetOf<String>()
    private val _reading = MutableStateFlow(
        LiveReading(
            deviceCapabilities = deviceCapabilities,
            compass = CompassReading(
                sensorAvailable = deviceCapabilities.compassAvailable,
            ),
        ),
    )
    val reading: StateFlow<LiveReading> = _reading

    private var lastAccelerometer: FloatArray? = null
    private var lastMagnetometer: FloatArray? = null
    private var pressureFiltered: Float? = null
    private var rotationAccuracy: Int? = null
    private var magnetometerAccuracy: Int? = null
    private var magneticFieldMicroTesla: Float? = null
    private val headingFilter = HeadingFilter()
    private val movementCourseFilter = HeadingFilter(
        timeConstantMillis = 1_500.0,
        fastTimeConstantMillis = 500.0,
        fastResponseThresholdDegrees = 20f,
        deadbandDegrees = 2f,
    )
    private var lastGeocodedLocation: Coordinates? = null
    private var geocodeInFlight = false
    private var vivoFusedLocationRegistered = false
    private var vivoCurrentGpsCancellation: CancellationSignal? = null
    private var gnssMeasurementsRegistered = false
    private var lastGnssMeasurementCallbackMillis: Long? = null
    private var lastGnssMeasurementLogMillis: Long? = null
    private var signalQualityMonitoring = false

    private val vivoCurrentGpsRetry = Runnable { startVivoCurrentGpsProbe() }

    /**
     * VIVO gets a listener dedicated to the GPS request. This makes the request itself authoritative
     * even if OriginOS labels the returned Location with a non-standard provider string. Other
     * devices keep using this repository as the same shared GPS/network listener as before.
     */
    private val vivoGpsLocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            handleLocation(
                location = location,
                requestedSource = "gps",
                isGpsFixEvidence = true,
            )
        }

        override fun onProviderEnabled(provider: String) {
            this@LiveRepository.onProviderEnabled(provider)
        }

        override fun onProviderDisabled(provider: String) {
            this@LiveRepository.onProviderDisabled(provider)
        }

        @Deprecated("Legacy LocationListener callback")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    /**
     * OriginOS primarily exercises its fused path in navigation apps. Keeping a high-accuracy
     * platform fused request next to the direct GPS request makes that system-owned path demand
     * GNSS too; it does not depend on Google Play Services.
     */
    private val vivoFusedLocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val isGpsFixEvidence =
                gnssPolicy.isGpsLocationFromVivoFusedListener(location.provider)
            handleLocation(
                location = location,
                requestedSource = "vivo_fused",
                isGpsFixEvidence = isGpsFixEvidence,
            )
        }

        override fun onProviderEnabled(provider: String) {
            diagnosticLogger.log("provider_enabled") { mapOf("provider" to provider) }
        }

        override fun onProviderDisabled(provider: String) {
            diagnosticLogger.log("provider_disabled") { mapOf("provider" to provider) }
        }

        @Deprecated("Legacy LocationListener callback")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    private val fixEvidenceExpiry = Runnable {
        val before = _reading.value.gnss
        _reading.update { current ->
            current.copy(
                gnss = gnssStateResolver.onFixEvidenceTimeout(
                    current = current.gnss,
                    nowMillis = SystemClock.elapsedRealtime(),
                ),
            )
        }
        val after = _reading.value.gnss
        diagnosticLogger.log("fix_evidence_timeout") {
            mapOf("before" to before.state.name, "after" to after.state.name)
        }
    }

    private val signalQualityEvaluation = object : Runnable {
        override fun run() {
            if (!signalQualityMonitoring) return
            val before = _reading.value.gnss
            val nowMillis = SystemClock.elapsedRealtime()
            _reading.update { current ->
                current.copy(
                    gnss = gnssStateResolver.onTimeElapsed(current.gnss, nowMillis),
                )
            }
            val after = _reading.value.gnss
            if (before.signalWarning != after.signalWarning) {
                diagnosticLogger.log("gnss_signal_warning_changed") {
                    mapOf(
                        "before" to before.signalWarning?.name,
                        "after" to after.signalWarning?.name,
                        "state" to after.state.name,
                        "accuracy_m" to after.horizontalAccuracyMeters?.toFiniteDoubleOrNull(),
                    )
                }
            }
            if (signalQualityMonitoring) {
                mainHandler.postDelayed(this, GNSS_SIGNAL_EVALUATION_INTERVAL_MILLIS)
            }
        }
    }

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onStarted() {
            diagnosticLogger.log("gnss_started")
            updateGnssState(GnssFixState.SEARCHING)
        }

        override fun onStopped() {
            diagnosticLogger.log("gnss_stopped")
            updateGnssState(GnssFixState.DISABLED)
        }

        override fun onFirstFix(ttffMillis: Int) {
            diagnosticLogger.log("gnss_first_fix") { mapOf("ttff_ms" to ttffMillis) }
            _reading.update { current ->
                current.copy(
                    gnss = gnssStateResolver.onFirstFix(
                        current = current.gnss,
                        nowMillis = SystemClock.elapsedRealtime(),
                    ),
                )
            }
            scheduleFixEvidenceExpiry()
        }

        override fun onSatelliteStatusChanged(status: GnssStatus) {
            val nowMillis = SystemClock.elapsedRealtime()
            var rawUsedEntries = 0
            val signals = ArrayList<GnssSatelliteSignal>(status.satelliteCount)
            val diagnosticsEnabled = diagnosticLogger.isEnabled
            val satelliteDetails = if (diagnosticsEnabled) {
                ArrayList<GnssSatelliteDiagnostic>(minOf(status.satelliteCount, MAX_LOGGED_SATELLITES))
            } else null
            val constellationCounts = if (diagnosticsEnabled) mutableMapOf<Int, Int>() else null
            for (index in 0 until status.satelliteCount) {
                val usedInFix = status.usedInFix(index)
                if (usedInFix) rawUsedEntries++
                val constellation = status.getConstellationType(index)
                val svid = status.getSvid(index)
                signals += GnssSatelliteSignal(
                    constellation = constellation,
                    svid = svid,
                    usedInFix = usedInFix,
                )
                satelliteDetails?.let { details ->
                    constellationCounts?.merge(constellation, 1) { first, second -> first + second }
                    if (index < MAX_LOGGED_SATELLITES) {
                        details += GnssSatelliteDiagnostic(
                            constellation = constellation,
                            svid = svid,
                            cn0DbHz = status.getCn0DbHz(index).toFiniteDoubleOrNull(),
                            used = usedInFix,
                            ephemeris = status.hasEphemerisData(index),
                            almanac = status.hasAlmanacData(index),
                            carrierHz = if (status.hasCarrierFrequencyHz(index)) {
                                status.getCarrierFrequencyHz(index).toFiniteDoubleOrNull()
                            } else null,
                        )
                    }
                }
            }
            val satelliteSummary = summarizeGnssSatellites(signals)
            _reading.update { current ->
                current.copy(
                    gnss = gnssStateResolver.onSatelliteStatus(
                        current = current.gnss,
                        visibleSatellites = satelliteSummary.visibleSatellites,
                        usedSatellites = satelliteSummary.usedSatellites,
                        nowMillis = nowMillis,
                    ),
                )
            }
            val resolved = _reading.value.gnss
            diagnosticLogger.log("satellite_frame") {
                mapOf(
                    "raw_visible" to status.satelliteCount,
                    "raw_used" to rawUsedEntries,
                    "unique_visible" to satelliteSummary.visibleSatellites,
                    "unique_used" to satelliteSummary.usedSatellites,
                    "resolved_state" to resolved.state.name,
                    "resolved_visible" to resolved.visibleSatellites,
                    "resolved_used" to resolved.usedSatellites,
                    "constellations" to constellationCounts?.mapKeys { it.key.toString() },
                    "satellites" to satelliteDetails,
                    "truncated" to (status.satelliteCount > MAX_LOGGED_SATELLITES),
                )
            }
            if (satelliteSummary.usedSatellites > 0) scheduleFixEvidenceExpiry()
        }
    }

    /**
     * Keeps full tracking active on VIVO while this repository has an owner because OriginOS can
     * otherwise remain duty-cycled through an entire cold start. Do not register a measurement
     * request on standard devices: Samsung S24 stopped obtaining fixes while even a passive
     * measurement request was present.
     */
    private val gnssMeasurementsCallback = object : GnssMeasurementsEvent.Callback() {
        @Suppress("DEPRECATION")
        override fun onStatusChanged(status: Int) {
            diagnosticLogger.log("gnss_measurements_status") {
                mapOf(
                    "status_code" to status,
                    "status" to gnssMeasurementStatusName(status),
                    "full_tracking_requested" to gnssPolicy.shouldUseFullTracking(Build.VERSION.SDK_INT),
                )
            }
        }

        override fun onGnssMeasurementsReceived(eventArgs: GnssMeasurementsEvent) {
            val nowMillis = SystemClock.elapsedRealtime()
            val callbackIntervalMillis = lastGnssMeasurementCallbackMillis
                ?.let { nowMillis - it }
            lastGnssMeasurementCallbackMillis = nowMillis
            if (!diagnosticLogger.isEnabled) return
            if (
                lastGnssMeasurementLogMillis
                    ?.let { nowMillis - it < GNSS_MEASUREMENT_LOG_INTERVAL_MILLIS } == true
            ) return
            lastGnssMeasurementLogMillis = nowMillis

            val measurements = eventArgs.measurements
            val distinctSatelliteCount = measurements
                .map { measurement -> measurement.constellationType to measurement.svid }
                .toSet()
                .size
            val constellationCounts = measurements
                .groupingBy { it.constellationType.toString() }
                .eachCount()
            val codeLockCount = measurements.count { measurement ->
                measurement.state.hasFlag(GnssMeasurement.STATE_CODE_LOCK)
            }
            val towKnownCount = measurements.count { measurement ->
                measurement.state.hasFlag(GnssMeasurement.STATE_TOW_KNOWN) ||
                    measurement.state.hasFlag(GnssMeasurement.STATE_TOW_DECODED)
            }
            val usableTimeCount = measurements.count { measurement ->
                measurement.state.hasFlag(GnssMeasurement.STATE_CODE_LOCK) &&
                    (
                        measurement.state.hasFlag(GnssMeasurement.STATE_TOW_KNOWN) ||
                            measurement.state.hasFlag(GnssMeasurement.STATE_TOW_DECODED)
                        )
            }
            val cn0Values = measurements
                .mapNotNull { measurement -> measurement.cn0DbHz.takeIf { it.isFinite() } }

            diagnosticLogger.log("gnss_measurement_frame") {
                mapOf(
                    "measurement_count" to measurements.size,
                    "distinct_satellite_count" to distinctSatelliteCount,
                    "constellations" to constellationCounts,
                    "code_lock_count" to codeLockCount,
                    "tow_known_count" to towKnownCount,
                    "usable_time_count" to usableTimeCount,
                    "cn0_max_db_hz" to cn0Values.maxOrNull(),
                    "cn0_avg_db_hz" to cn0Values.takeIf { it.isNotEmpty() }?.average(),
                    "clock_has_full_bias_nanos" to eventArgs.clock.hasFullBiasNanos(),
                    "hardware_clock_discontinuity_count" to
                        eventArgs.clock.hardwareClockDiscontinuityCount,
                    "callback_interval_ms" to callbackIntervalMillis,
                    "full_tracking_requested" to
                        gnssPolicy.shouldUseFullTracking(Build.VERSION.SDK_INT),
                )
            }
        }
    }

    private val nmeaListener = OnNmeaMessageListener { message, _ ->
        val inspected = NmeaFixParser.inspect(message)
        inspected?.let { info ->
            diagnosticLogger.log("nmea_fix_sentence") {
                mapOf(
                    "talker" to info.talker,
                    "sentence_type" to info.sentenceType,
                    "has_fix" to info.hasFix,
                    "used_satellites" to info.usedSatellites,
                )
            }
        }
        val evidence = inspected?.takeIf { it.hasFix } ?: return@OnNmeaMessageListener
        _reading.update { current ->
            current.copy(
                gnss = gnssStateResolver.onNmeaFixEvidence(
                    current = current.gnss,
                    usedSatellites = evidence.usedSatellites,
                    nowMillis = SystemClock.elapsedRealtime(),
                ),
            )
        }
        scheduleFixEvidenceExpiry()
    }

    init {
        scope.launch {
            diagnosticLogger.enabledChanges.collect { enabled ->
                if (enabled) mainHandler.post { onDiagnosticLoggingEnabled() }
            }
        }
        scope.launch {
            altitudeEngine.altitude.collect { altitude ->
                _reading.update { it.copy(altitude = altitude) }
            }
        }
        scope.launch {
            openMeteoRepository.weather.collect { weather ->
                _reading.update { current ->
                    val fallbackPressure = if (pressureSensor == null) {
                        weather?.surfacePressureHpa?.let {
                            PressureReading(it.toFloat(), PressureSource.WEATHER, weather.updatedAtMillis)
                        }
                    } else current.pressure
                    current.copy(weather = weather, pressure = fallbackPressure)
                }
            }
        }
        scope.launch {
            openMeteoRepository.terrain.collect { terrain ->
                _reading.update { it.copy(terrain = terrain) }
            }
        }
    }

    @Synchronized
    fun acquire(owner: String) {
        diagnosticLogger.log("owner_acquire") { mapOf("owner" to owner) }
        if (!owners.add(owner) || owners.size > 1) return
        startSensors()
        startLocation()
    }

    @Synchronized
    fun release(owner: String) {
        diagnosticLogger.log("owner_release") { mapOf("owner" to owner) }
        if (!owners.remove(owner) || owners.isNotEmpty()) return
        sensorManager.unregisterListener(this)
        headingFilter.reset()
        movementCourseFilter.reset()
        lastAccelerometer = null
        lastMagnetometer = null
        rotationAccuracy = null
        magnetometerAccuracy = null
        magneticFieldMicroTesla = null
        stopLocation()
    }

    fun onPermissionChanged() {
        diagnosticLogger.log("permission_changed") { mapOf("fine_location" to hasFineLocation()) }
        if (owners.isNotEmpty()) {
            stopLocation()
            startLocation()
        }
    }

    @Synchronized
    private fun onDiagnosticLoggingEnabled() {
        diagnosticLogger.log("diagnostic_context") {
            mapOf(
                "owner_count" to owners.size,
                "fine_location" to hasFineLocation(),
                "location_enabled" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    runCatching { locationManager.isLocationEnabled }.getOrNull()
                } else null,
                "gps_provider_enabled" to runCatching {
                    locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                }.getOrNull(),
                "compatibility" to gnssCompatibility.name,
                "gps_request_mode" to if (
                    gnssPolicy.shouldUseModernGpsRequest(Build.VERSION.SDK_INT)
                ) "modern" else "legacy",
                "gnss_measurements_registered" to gnssMeasurementsRegistered,
                "full_tracking_requested" to
                    gnssPolicy.shouldUseFullTracking(Build.VERSION.SDK_INT),
            )
        }
    }

    private fun startSensors() {
        headingFilter.reset()
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        } ?: run {
            accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        }
        // Keep the magnetic-field sensor active even with a rotation vector: its magnitude is a
        // useful, device-independent indication that a case or nearby metal may disturb heading.
        magnetometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        pressureSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocation() {
        val fineLocation = hasFineLocation()
        val locationEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { locationManager.isLocationEnabled }.getOrNull()
        } else null
        diagnosticLogger.log("location_start") {
            mapOf(
                "fine_location" to fineLocation,
                "compatibility" to gnssCompatibility.name,
                "location_enabled" to locationEnabled,
            )
        }
        if (!fineLocation) {
            mainHandler.removeCallbacks(fixEvidenceExpiry)
            stopSignalQualityMonitoring()
            _reading.update { it.copy(gnss = gnssStateResolver.onNoPermission()) }
            return
        }
        val gpsEnabled = runCatching { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) }
            .getOrDefault(false)
        if (!gpsEnabled) {
            updateGnssState(GnssFixState.DISABLED)
        } else {
            updateGnssState(GnssFixState.SEARCHING)
        }
        val gpsListener = if (gnssPolicy.usesDedicatedGpsListener) {
            vivoGpsLocationListener
        } else {
            this
        }
        registerGpsUpdates(gpsListener)
        val networkProviderAvailable = runRegistration("network_provider") {
            locationManager.getProvider(LocationManager.NETWORK_PROVIDER) != null
        } == true
        if (networkProviderAvailable) {
            runRegistration("network_updates") {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5_000L, 10f, this)
                true
            }
        }
        runRegistration("gnss_status") {
            locationManager.registerGnssStatusCallback(gnssCallback, mainHandler)
        }
        runRegistration("nmea") {
            locationManager.addNmeaListener(nmeaListener, mainHandler)
        }
        startGnssMeasurements()
        startVivoFusedRecovery()
        requestVivoGnssAssistance()
        startVivoCurrentGpsProbe()
    }

    private fun stopLocation() {
        mainHandler.removeCallbacks(fixEvidenceExpiry)
        stopSignalQualityMonitoring()
        diagnosticLogger.log("location_stop")
        stopVivoCurrentGpsProbe()
        stopGnssMeasurements()
        runStopOperation("location_updates") { locationManager.removeUpdates(this) }
        if (gnssPolicy.usesDedicatedGpsListener) {
            runStopOperation("vivo_gps_updates") { locationManager.removeUpdates(vivoGpsLocationListener) }
        }
        if (vivoFusedLocationRegistered) {
            runStopOperation("vivo_fused_updates") {
                locationManager.removeUpdates(vivoFusedLocationListener)
            }
            vivoFusedLocationRegistered = false
        }
        runStopOperation("gnss_status") { locationManager.unregisterGnssStatusCallback(gnssCallback) }
        runStopOperation("nmea") { locationManager.removeNmeaListener(nmeaListener) }
        gnssStateResolver.reset()
    }

    @SuppressLint("MissingPermission")
    private fun registerGpsUpdates(listener: LocationListener) {
        val modernRegistered = if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            gnssPolicy.shouldUseModernGpsRequest(Build.VERSION.SDK_INT)
        ) {
            val request = LocationRequest.Builder(VIVO_GPS_UPDATE_INTERVAL_MILLIS)
                .setQuality(LocationRequest.QUALITY_HIGH_ACCURACY)
                .setMinUpdateIntervalMillis(VIVO_GPS_UPDATE_INTERVAL_MILLIS)
                .setMinUpdateDistanceMeters(0f)
                .setMaxUpdateDelayMillis(0L)
                .build()
            runRegistration("modern_gps_updates") {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    request,
                    context.mainExecutor,
                    listener,
                )
                true
            } == true
        } else false

        if (modernRegistered) return
        runRegistration("gps_updates") {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                GPS_UPDATE_INTERVAL_MILLIS,
                0f,
                listener,
            )
            true
        }
    }

    @SuppressLint("MissingPermission")
    private fun startVivoFusedRecovery() {
        if (
            !gnssPolicy.shouldUsePlatformFusedRecovery(Build.VERSION.SDK_INT) ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            vivoFusedLocationRegistered ||
            !hasFineLocation()
        ) return

        val providerAvailable = runRegistration("vivo_fused_provider") {
            locationManager.hasProvider(LocationManager.FUSED_PROVIDER)
        } == true
        if (!providerAvailable) return

        val request = LocationRequest.Builder(GPS_UPDATE_INTERVAL_MILLIS)
            .setQuality(LocationRequest.QUALITY_HIGH_ACCURACY)
            .setMinUpdateIntervalMillis(0L)
            .setMinUpdateDistanceMeters(0f)
            .setMaxUpdateDelayMillis(0L)
            .build()
        vivoFusedLocationRegistered = runRegistration("vivo_fused_updates") {
            locationManager.requestLocationUpdates(
                LocationManager.FUSED_PROVIDER,
                request,
                context.mainExecutor,
                vivoFusedLocationListener,
            )
            true
        } == true
    }

    /**
     * Some OriginOS builds do not deliver anything to a continuous GPS listener even though the
     * same provider can answer an explicit current-location request. Keep one such request active
     * alongside the continuous listener and retry after either a result or a timeout.
     */
    @SuppressLint("MissingPermission")
    private fun startVivoCurrentGpsProbe() {
        if (
            !gnssPolicy.shouldUseCurrentGpsProbe(Build.VERSION.SDK_INT) ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            vivoCurrentGpsCancellation != null ||
            !hasFineLocation() ||
            runCatching { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) }
                .getOrDefault(false).not()
        ) return

        mainHandler.removeCallbacks(vivoCurrentGpsRetry)
        val cancellation = CancellationSignal()
        vivoCurrentGpsCancellation = cancellation
        val request = LocationRequest.Builder(VIVO_GPS_UPDATE_INTERVAL_MILLIS)
            .setQuality(LocationRequest.QUALITY_HIGH_ACCURACY)
            .setDurationMillis(VIVO_CURRENT_GPS_TIMEOUT_MILLIS)
            .setMaxUpdates(1)
            .build()
        val registered = runRegistration("vivo_current_gps") {
            locationManager.getCurrentLocation(
                LocationManager.GPS_PROVIDER,
                request,
                cancellation,
                context.mainExecutor,
            ) callback@{ location ->
                if (vivoCurrentGpsCancellation !== cancellation) return@callback
                vivoCurrentGpsCancellation = null

                val ageMillis = location?.let(::locationElapsedAgeMillis)
                val accepted = location != null &&
                    ageMillis != null &&
                    ageMillis <= VIVO_CURRENT_GPS_MAX_AGE_MILLIS
                diagnosticLogger.log("vivo_current_gps_result") {
                    mapOf(
                        "has_location" to (location != null),
                        "reported_provider" to location?.provider,
                        "age_ms" to ageMillis,
                        "accepted" to accepted,
                    )
                }
                if (accepted) {
                    handleLocation(
                        location = checkNotNull(location),
                        requestedSource = "vivo_current_gps",
                        isGpsFixEvidence = true,
                    )
                }
                scheduleVivoCurrentGpsRetry()
            }
            true
        } == true

        if (!registered && vivoCurrentGpsCancellation === cancellation) {
            vivoCurrentGpsCancellation = null
            scheduleVivoCurrentGpsRetry()
        }
    }

    private fun stopVivoCurrentGpsProbe() {
        mainHandler.removeCallbacks(vivoCurrentGpsRetry)
        vivoCurrentGpsCancellation?.let { cancellation ->
            runStopOperation("vivo_current_gps") { cancellation.cancel() }
        }
        vivoCurrentGpsCancellation = null
    }

    private fun scheduleVivoCurrentGpsRetry() {
        mainHandler.removeCallbacks(vivoCurrentGpsRetry)
        if (owners.isNotEmpty() && hasFineLocation()) {
            mainHandler.postDelayed(vivoCurrentGpsRetry, VIVO_CURRENT_GPS_RETRY_DELAY_MILLIS)
        }
    }

    private fun locationElapsedAgeMillis(location: Location): Long? {
        val locationElapsedNanos = location.elapsedRealtimeNanos.takeIf { it > 0L } ?: return null
        return ((SystemClock.elapsedRealtimeNanos() - locationElapsedNanos) / NANOS_PER_MILLISECOND)
            .coerceAtLeast(0L)
    }

    private fun requestVivoGnssAssistance() {
        if (!gnssPolicy.requestsGnssAssistance) return
        VIVO_ASSISTANCE_COMMANDS.forEach { command ->
            runRegistration("vivo_assistance_$command") {
                locationManager.sendExtraCommand(LocationManager.GPS_PROVIDER, command, null)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startGnssMeasurements() {
        val fullTracking = gnssPolicy.shouldUseFullTracking(Build.VERSION.SDK_INT)
        if (
            !fullTracking ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            gnssMeasurementsRegistered ||
            !hasFineLocation()
        ) return

        val request = GnssMeasurementRequest.Builder()
            .setFullTracking(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    setIntervalMillis(VIVO_GNSS_MEASUREMENT_INTERVAL_MILLIS)
                }
            }
            .build()
        gnssMeasurementsRegistered = runRegistration("gnss_measurements") {
            locationManager.registerGnssMeasurementsCallback(
                request,
                context.mainExecutor,
                gnssMeasurementsCallback,
            )
        } == true
    }

    private fun stopGnssMeasurements() {
        if (!gnssMeasurementsRegistered) return
        runStopOperation("gnss_measurements") {
            locationManager.unregisterGnssMeasurementsCallback(gnssMeasurementsCallback)
        }
        gnssMeasurementsRegistered = false
        lastGnssMeasurementCallbackMillis = null
        lastGnssMeasurementLogMillis = null
    }

    override fun onLocationChanged(location: Location) {
        val standardGpsSource = gnssPolicy.isGpsLocationFromSharedListener(location.provider)
        handleLocation(
            location = location,
            requestedSource = if (standardGpsSource) "gps" else "network",
            isGpsFixEvidence = standardGpsSource,
        )
    }

    private fun handleLocation(
        location: Location,
        requestedSource: String,
        isGpsFixEvidence: Boolean,
    ) {
        val nowMillis = System.currentTimeMillis()
        val coordinates = Coordinates(
            latitude = location.latitude,
            longitude = location.longitude,
            horizontalAccuracyMeters = location.accuracy.takeIf { location.hasAccuracy() },
            timestampMillis = location.time.takeIf { it > 0 } ?: nowMillis,
            source = if (isGpsFixEvidence) PositionSource.GNSS else PositionSource.NETWORK,
        )
        val positionAccepted = shouldReplacePosition(
            current = _reading.value.coordinates,
            candidate = coordinates,
            nowMillis = nowMillis,
        )
        val movementCourse = if (positionAccepted && isGpsFixEvidence) {
            movementCourse(location, coordinates.timestampMillis)
        } else null
        val usedSatelliteExtra = if (
            isGpsFixEvidence && gnssPolicy.usesLegacySatelliteExtra
        ) {
            location.extras?.getInt(LEGACY_SATELLITES_EXTRA, 0)?.takeIf { it > 0 }
        } else null
        diagnosticLogger.log("location_update") {
            mapOf(
                "requested_source" to requestedSource,
                "reported_provider" to location.provider,
                "gps_fix_evidence" to isGpsFixEvidence,
                "accuracy_m" to location.accuracy.takeIf { location.hasAccuracy() }?.toFiniteDoubleOrNull(),
                "vertical_accuracy_m" to location.verticalAccuracyMeters
                    .takeIf { location.hasVerticalAccuracy() }
                    ?.toFiniteDoubleOrNull(),
                "age_ms" to (System.currentTimeMillis() - location.time).takeIf { location.time > 0L },
                "has_altitude" to location.hasAltitude(),
                "has_speed" to location.hasSpeed(),
                "coordinates_accepted" to positionAccepted,
                "legacy_satellites" to usedSatelliteExtra,
            )
        }
        _reading.update { current ->
            current.copy(
                coordinates = if (positionAccepted) coordinates else current.coordinates,
                movementCourse = movementCourse ?: current.movementCourse,
                gnss = if (isGpsFixEvidence) {
                    gnssStateResolver.onGpsLocation(
                        current = current.gnss,
                        accuracyMeters = coordinates.horizontalAccuracyMeters,
                        nowMillis = SystemClock.elapsedRealtime(),
                        usedSatellites = usedSatelliteExtra,
                    )
                } else current.gnss,
            )
        }
        if (isGpsFixEvidence) scheduleFixEvidenceExpiry()
        if (isGpsFixEvidence || _reading.value.altitude == null) {
            altitudeEngine.onLocation(location)
        }
        if (positionAccepted) {
            openMeteoRepository.refreshIfNeeded(location.latitude, location.longitude)
            updatePlaceNameIfNeeded(coordinates)
        }
    }

    private fun movementCourse(location: Location, timestampMillis: Long): MovementCourse? {
        if (!location.hasBearing() || !location.hasSpeed()) return null
        val speed = location.speed
        val horizontalAccuracy = location.accuracy.takeIf { location.hasAccuracy() } ?: return null
        if (
            !speed.isFinite() || speed < MIN_MOVEMENT_COURSE_SPEED_METERS_PER_SECOND ||
            !horizontalAccuracy.isFinite() || horizontalAccuracy > MAX_MOVEMENT_COURSE_POSITION_ACCURACY_METERS
        ) return null

        val bearingAccuracy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasBearingAccuracy()) {
            location.bearingAccuracyDegrees.takeIf { it.isFinite() }
        } else null
        if (bearingAccuracy != null && bearingAccuracy > MAX_MOVEMENT_COURSE_BEARING_ACCURACY_DEGREES) {
            return null
        }
        val timestampNanos = location.elapsedRealtimeNanos.takeIf { it > 0L }
            ?: SystemClock.elapsedRealtimeNanos()
        return MovementCourse(
            bearingDegrees = movementCourseFilter.add(normalizeDegrees(location.bearing), timestampNanos),
            accuracyDegrees = bearingAccuracy,
            speedMetersPerSecond = speed,
            timestampMillis = timestampMillis,
        )
    }

    override fun onProviderEnabled(provider: String) {
        diagnosticLogger.log("provider_enabled") { mapOf("provider" to provider) }
        if (provider == LocationManager.GPS_PROVIDER) {
            updateGnssState(GnssFixState.SEARCHING)
            startVivoCurrentGpsProbe()
        }
    }

    override fun onProviderDisabled(provider: String) {
        diagnosticLogger.log("provider_disabled") { mapOf("provider" to provider) }
        if (provider == LocationManager.GPS_PROVIDER) {
            stopVivoCurrentGpsProbe()
            updateGnssState(GnssFixState.DISABLED)
        }
    }

    @Deprecated("Legacy LocationListener callback")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_PRESSURE -> {
                val raw = event.values.firstOrNull() ?: return
                val filtered = pressureFiltered?.let { it + 0.2f * (raw - it) } ?: raw
                pressureFiltered = filtered
                altitudeEngine.onPressure(filtered)
                _reading.update {
                    it.copy(
                        pressure = PressureReading(
                            hPa = filtered,
                            source = PressureSource.SENSOR,
                            timestampMillis = System.currentTimeMillis(),
                        ),
                    )
                }
            }
            Sensor.TYPE_ROTATION_VECTOR -> {
                rotationAccuracy = event.accuracy.takeIf { it >= SensorManager.SENSOR_STATUS_UNRELIABLE }
                updateHeadingFromRotationVector(event.values, event.timestamp)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                lastAccelerometer = event.values.clone()
                updateHeadingFromFallback(event.timestamp)
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                lastMagnetometer = event.values.clone()
                magnetometerAccuracy = event.accuracy.takeIf { it >= SensorManager.SENSOR_STATUS_UNRELIABLE }
                val x = event.values.getOrNull(0) ?: return
                val y = event.values.getOrNull(1) ?: return
                val z = event.values.getOrNull(2) ?: return
                magneticFieldMicroTesla = sqrt(x * x + y * y + z * z)
                if (rotationSensor == null) {
                    updateHeadingFromFallback(event.timestamp)
                } else {
                    updateCompassDiagnostics()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        when (sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> rotationAccuracy = accuracy
            Sensor.TYPE_MAGNETIC_FIELD -> magnetometerAccuracy = accuracy
            else -> return
        }
        updateCompassDiagnostics()
    }

    private fun updateHeadingFromRotationVector(vector: FloatArray, timestampNanos: Long) {
        val matrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(matrix, vector)
        updateHeading(matrix, timestampNanos)
    }

    private fun updateHeadingFromFallback(timestampNanos: Long) {
        val acceleration = lastAccelerometer ?: return
        val magnetic = lastMagnetometer ?: return
        val matrix = FloatArray(9)
        if (SensorManager.getRotationMatrix(matrix, null, acceleration, magnetic)) {
            updateHeading(matrix, timestampNanos)
        }
    }

    @Suppress("DEPRECATION")
    private fun updateHeading(rotationMatrix: FloatArray, timestampNanos: Long) {
        val remapped = FloatArray(9)
        val rotation = windowManager.defaultDisplay.rotation
        val (axisX, axisY) = when (rotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        SensorManager.remapCoordinateSystem(rotationMatrix, axisX, axisY, remapped)
        val magneticHeading = Math.toDegrees(SensorManager.getOrientation(remapped, FloatArray(3))[0].toDouble()).toFloat()
        val coordinates = _reading.value.coordinates
        val altitude = _reading.value.altitude?.metersMsl?.toFloat() ?: 0f
        val northReference = if (coordinates == null) NorthReference.MAGNETIC else NorthReference.TRUE
        val declination = coordinates?.let {
            GeomagneticField(
                it.latitude.toFloat(),
                it.longitude.toFloat(),
                altitude,
                System.currentTimeMillis(),
            ).declination
        } ?: 0f
        _reading.update {
            it.copy(
                compass = CompassReading(
                    headingDegrees = headingFilter.add(
                        normalizeDegrees(magneticHeading + declination),
                        timestampNanos,
                    ),
                    accuracy = effectiveCompassAccuracy(),
                    northReference = northReference,
                    magneticFieldMicroTesla = magneticFieldMicroTesla,
                    sensorAvailable = true,
                ),
            )
        }
    }

    private fun updateCompassDiagnostics() {
        _reading.update { current ->
            current.copy(
                compass = current.compass.copy(
                    accuracy = effectiveCompassAccuracy(),
                    magneticFieldMicroTesla = magneticFieldMicroTesla,
                ),
            )
        }
    }

    private fun effectiveCompassAccuracy(): CompassAccuracy {
        val primary = if (rotationSensor != null) rotationAccuracy else magnetometerAccuracy
        val values = listOfNotNull(primary, magnetometerAccuracy)
        val accuracy = values.minOrNull() ?: return CompassAccuracy.UNKNOWN
        return compassAccuracyFromSensorStatus(accuracy)
    }

    private fun updatePlaceNameIfNeeded(coordinates: Coordinates) {
        val last = lastGeocodedLocation
        if (geocodeInFlight || (last != null && distanceMeters(
                coordinates.latitude,
                coordinates.longitude,
                last.latitude,
                last.longitude,
            ) < 100.0)
        ) return
        geocodeInFlight = true
        scope.launch(Dispatchers.IO) {
            val label = reverseGeocode(coordinates.latitude, coordinates.longitude)
            lastGeocodedLocation = coordinates
            geocodeInFlight = false
            _reading.update { it.copy(placeName = label) }
        }
    }

    @Suppress("DEPRECATION")
    private fun reverseGeocode(latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) return null
        return runCatching {
            val address = Geocoder(context, currentAppLocale())
                .getFromLocation(latitude, longitude, 1)
                ?.firstOrNull()
            address?.locality
                ?: address?.subAdminArea
                ?: address?.adminArea
                ?: address?.countryName
        }.getOrNull()
    }

    private fun updateGnssState(state: GnssFixState) {
        if (state == GnssFixState.DISABLED) {
            mainHandler.removeCallbacks(fixEvidenceExpiry)
            stopSignalQualityMonitoring()
            _reading.update { current -> current.copy(gnss = gnssStateResolver.onStopped(current.gnss)) }
            return
        }
        if (state == GnssFixState.SEARCHING) {
            val nowMillis = SystemClock.elapsedRealtime()
            _reading.update { current ->
                current.copy(gnss = gnssStateResolver.onStarted(current.gnss, nowMillis))
            }
            startSignalQualityMonitoring()
            return
        }
        _reading.update { current ->
            current.copy(
                gnss = current.gnss.copy(state = state),
            )
        }
    }

    private fun scheduleFixEvidenceExpiry() {
        mainHandler.removeCallbacks(fixEvidenceExpiry)
        mainHandler.postDelayed(
            fixEvidenceExpiry,
            GnssStateResolver.FIX_EVIDENCE_FRESHNESS_MILLIS + 1L,
        )
    }

    private fun startSignalQualityMonitoring() {
        signalQualityMonitoring = true
        mainHandler.removeCallbacks(signalQualityEvaluation)
        mainHandler.postDelayed(
            signalQualityEvaluation,
            GNSS_SIGNAL_EVALUATION_INTERVAL_MILLIS,
        )
    }

    private fun stopSignalQualityMonitoring() {
        signalQualityMonitoring = false
        mainHandler.removeCallbacks(signalQualityEvaluation)
    }

    private fun hasFineLocation(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    private inline fun <T> runRegistration(stage: String, operation: () -> T): T? {
        diagnosticLogger.log("registration_attempt") { mapOf("stage" to stage) }
        return runCatching(operation).fold(
            onSuccess = { result ->
                diagnosticLogger.log("registration_result") {
                    mapOf("stage" to stage, "success" to true, "result" to result.toString())
                }
                result
            },
            onFailure = { error ->
                diagnosticLogger.log("registration_result") {
                    mapOf(
                        "stage" to stage,
                        "success" to false,
                        "exception" to error.javaClass.name,
                        "message" to error.message,
                    )
                }
                null
            },
        )
    }

    private inline fun runStopOperation(stage: String, operation: () -> Unit) {
        runCatching(operation).onFailure { error ->
            diagnosticLogger.log("unregistration_error") {
                mapOf("stage" to stage, "exception" to error.javaClass.name, "message" to error.message)
            }
        }
    }

    private fun Float.toFiniteDoubleOrNull(): Double? = takeIf { it.isFinite() }?.toDouble()

    private fun Int.hasFlag(flag: Int): Boolean = this and flag != 0

    private fun gnssMeasurementStatusName(status: Int): String = when (status) {
        GnssMeasurementsEvent.Callback.STATUS_READY -> "READY"
        GnssMeasurementsEvent.Callback.STATUS_LOCATION_DISABLED -> "LOCATION_DISABLED"
        GnssMeasurementsEvent.Callback.STATUS_NOT_ALLOWED -> "NOT_ALLOWED"
        GnssMeasurementsEvent.Callback.STATUS_NOT_SUPPORTED -> "NOT_SUPPORTED"
        else -> "UNKNOWN"
    }

    companion object {
        private const val MAX_LOGGED_SATELLITES = 128
        private const val LEGACY_SATELLITES_EXTRA = "satellites"
        private const val GPS_UPDATE_INTERVAL_MILLIS = 1_000L
        private const val VIVO_GPS_UPDATE_INTERVAL_MILLIS = 0L
        private const val VIVO_GNSS_MEASUREMENT_INTERVAL_MILLIS = 0
        private const val GNSS_MEASUREMENT_LOG_INTERVAL_MILLIS = 5_000L
        private const val GNSS_SIGNAL_EVALUATION_INTERVAL_MILLIS = 1_000L
        private const val VIVO_CURRENT_GPS_TIMEOUT_MILLIS = 30_000L
        private const val VIVO_CURRENT_GPS_RETRY_DELAY_MILLIS = 2_000L
        private const val VIVO_CURRENT_GPS_MAX_AGE_MILLIS = 10_000L
        private const val NANOS_PER_MILLISECOND = 1_000_000L
        private const val MIN_MOVEMENT_COURSE_SPEED_METERS_PER_SECOND = 0.8f
        private const val MAX_MOVEMENT_COURSE_POSITION_ACCURACY_METERS = 25f
        private const val MAX_MOVEMENT_COURSE_BEARING_ACCURACY_DEGREES = 35f
        private val VIVO_ASSISTANCE_COMMANDS = listOf(
            "force_time_injection",
            "force_psds_injection",
            // Kept for the Qualcomm/OriginOS provider, which may still use the legacy command.
            "force_xtra_injection",
        )
    }
}

internal fun compassAccuracyFromSensorStatus(accuracy: Int?): CompassAccuracy = when (accuracy) {
    SensorManager.SENSOR_STATUS_UNRELIABLE -> CompassAccuracy.UNRELIABLE
    SensorManager.SENSOR_STATUS_ACCURACY_LOW -> CompassAccuracy.LOW
    SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> CompassAccuracy.MEDIUM
    SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> CompassAccuracy.HIGH
    else -> CompassAccuracy.UNKNOWN
}
