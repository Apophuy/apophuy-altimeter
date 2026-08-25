package com.apophuy.altimeter.data

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.apophuy.altimeter.model.AppLanguage
import com.apophuy.altimeter.model.CoordinateFormat
import com.apophuy.altimeter.model.DistanceUnit
import com.apophuy.altimeter.model.DEFAULT_TRACKING_ALTITUDE_STEP_METERS
import com.apophuy.altimeter.model.DEFAULT_TRACKING_COORDINATE_MAX_AGE_MILLIS
import com.apophuy.altimeter.model.DEFAULT_TRACKING_SAMPLE_INTERVAL_MILLIS
import com.apophuy.altimeter.model.PressureUnit
import com.apophuy.altimeter.model.TemperatureUnit
import com.apophuy.altimeter.model.ThemeMode
import com.apophuy.altimeter.model.UserSettings
import com.apophuy.altimeter.model.WindSpeedUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.IOException

private val Context.settingsDataStore by preferencesDataStore("settings")

class SettingsRepository(
    private val context: Context,
    scope: CoroutineScope,
) {
    private object Keys {
        val units = stringPreferencesKey("unit_system")
        val distanceUnit = stringPreferencesKey("distance_unit")
        val pressureUnit = stringPreferencesKey("pressure_unit")
        val temperatureUnit = stringPreferencesKey("temperature_unit")
        val windSpeedUnit = stringPreferencesKey("wind_speed_unit")
        val coordinateFormat = stringPreferencesKey("coordinate_format")
        val language = stringPreferencesKey("language")
        val themeMode = stringPreferencesKey("theme_mode")
        val activeWaypointId = longPreferencesKey("active_waypoint_id")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val calibrationOffset = doublePreferencesKey("calibration_offset_meters")
        val trackingAltitudeStep = doublePreferencesKey("tracking_altitude_step_meters")
        val trackingSampleInterval = longPreferencesKey("tracking_sample_interval_millis")
        val trackingCoordinateMaxAge = longPreferencesKey("tracking_coordinate_max_age_millis")
        val gnssDiagnosticLogging = booleanPreferencesKey("gnss_diagnostic_logging_enabled")
        val hardwareNoticeAcknowledged = booleanPreferencesKey("hardware_notice_acknowledged")
    }

    val settings: StateFlow<UserSettings> = context.settingsDataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw error
        }
        .map { preferences ->
            val legacyImperial = preferences[Keys.units] == "IMPERIAL"
            val hasLegacyUnits = preferences[Keys.units] != null
            UserSettings(
                distanceUnit = preferences.enumValue<DistanceUnit>(Keys.distanceUnit)
                    ?: if (legacyImperial) DistanceUnit.FEET else DistanceUnit.METERS,
                pressureUnit = preferences.enumValue<PressureUnit>(Keys.pressureUnit)
                    ?: when {
                        legacyImperial -> PressureUnit.INCHES_MERCURY
                        hasLegacyUnits -> PressureUnit.HECTOPASCALS
                        else -> PressureUnit.MILLIMETERS_MERCURY
                    },
                temperatureUnit = preferences.enumValue<TemperatureUnit>(Keys.temperatureUnit)
                    ?: if (legacyImperial) TemperatureUnit.FAHRENHEIT else TemperatureUnit.CELSIUS,
                windSpeedUnit = preferences.enumValue<WindSpeedUnit>(Keys.windSpeedUnit)
                    ?: if (legacyImperial) WindSpeedUnit.MILES_PER_HOUR else WindSpeedUnit.METERS_PER_SECOND,
                coordinateFormat = preferences.enumValue<CoordinateFormat>(Keys.coordinateFormat)
                    ?: CoordinateFormat.DECIMAL_DEGREES,
                language = preferences[Keys.language]
                    ?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
                    ?: AppLanguage.SYSTEM,
                themeMode = preferences.enumValue<ThemeMode>(Keys.themeMode) ?: ThemeMode.SYSTEM,
                activeWaypointId = preferences[Keys.activeWaypointId],
                keepScreenOn = preferences[Keys.keepScreenOn] ?: false,
                calibrationOffsetMeters = preferences[Keys.calibrationOffset] ?: 0.0,
                trackingAltitudeStepMeters = preferences[Keys.trackingAltitudeStep]
                    ?.takeIf { it.isFinite() && it > 0.0 }
                    ?: DEFAULT_TRACKING_ALTITUDE_STEP_METERS,
                trackingSampleIntervalMillis = preferences[Keys.trackingSampleInterval]
                    ?.takeIf { it >= MIN_TRACKING_INTERVAL_MILLIS }
                    ?: DEFAULT_TRACKING_SAMPLE_INTERVAL_MILLIS,
                trackingCoordinateMaxAgeMillis = preferences[Keys.trackingCoordinateMaxAge]
                    ?.takeIf { it >= MIN_TRACKING_INTERVAL_MILLIS }
                    ?: DEFAULT_TRACKING_COORDINATE_MAX_AGE_MILLIS,
                gnssDiagnosticLoggingEnabled = preferences[Keys.gnssDiagnosticLogging] ?: false,
                hardwareNoticeAcknowledged = preferences[Keys.hardwareNoticeAcknowledged] ?: false,
            )
        }
        .stateIn(scope, SharingStarted.Eagerly, UserSettings())

    suspend fun setDistanceUnit(value: DistanceUnit) =
        context.settingsDataStore.edit { it[Keys.distanceUnit] = value.name }

    suspend fun setPressureUnit(value: PressureUnit) =
        context.settingsDataStore.edit { it[Keys.pressureUnit] = value.name }

    suspend fun setTemperatureUnit(value: TemperatureUnit) =
        context.settingsDataStore.edit { it[Keys.temperatureUnit] = value.name }

    suspend fun setWindSpeedUnit(value: WindSpeedUnit) =
        context.settingsDataStore.edit { it[Keys.windSpeedUnit] = value.name }

    suspend fun setCoordinateFormat(value: CoordinateFormat) =
        context.settingsDataStore.edit { it[Keys.coordinateFormat] = value.name }

    suspend fun setLanguage(value: AppLanguage) {
        context.settingsDataStore.edit { it[Keys.language] = value.name }
    }

    suspend fun setThemeMode(value: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.themeMode] = value.name }
    }

    suspend fun setActiveWaypointId(value: Long?) {
        context.settingsDataStore.edit { preferences ->
            if (value == null) preferences.remove(Keys.activeWaypointId)
            else preferences[Keys.activeWaypointId] = value
        }
    }

    suspend fun setKeepScreenOn(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.keepScreenOn] = value }
    }

    suspend fun setCalibrationOffset(meters: Double) {
        context.settingsDataStore.edit { it[Keys.calibrationOffset] = meters }
    }

    suspend fun resetCalibration() = setCalibrationOffset(0.0)

    suspend fun setTrackingAltitudeStepMeters(value: Double) {
        if (!value.isFinite() || value <= 0.0) return
        context.settingsDataStore.edit { it[Keys.trackingAltitudeStep] = value }
    }

    suspend fun setTrackingSampleIntervalMillis(value: Long) {
        context.settingsDataStore.edit {
            it[Keys.trackingSampleInterval] = value.coerceAtLeast(MIN_TRACKING_INTERVAL_MILLIS)
        }
    }

    suspend fun setTrackingCoordinateMaxAgeMillis(value: Long) {
        context.settingsDataStore.edit {
            it[Keys.trackingCoordinateMaxAge] = value.coerceAtLeast(MIN_TRACKING_INTERVAL_MILLIS)
        }
    }

    suspend fun setGnssDiagnosticLoggingEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.gnssDiagnosticLogging] = enabled }
    }

    suspend fun acknowledgeHardwareNotice() {
        context.settingsDataStore.edit { it[Keys.hardwareNoticeAcknowledged] = true }
    }

    companion object {
        private const val MIN_TRACKING_INTERVAL_MILLIS = 1_000L
    }
}

private inline fun <reified T : Enum<T>> androidx.datastore.preferences.core.Preferences.enumValue(
    key: androidx.datastore.preferences.core.Preferences.Key<String>,
): T? = this[key]?.let { stored -> runCatching { enumValueOf<T>(stored) }.getOrNull() }
