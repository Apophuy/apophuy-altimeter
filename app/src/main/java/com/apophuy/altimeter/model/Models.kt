package com.apophuy.altimeter.model

enum class AltitudeSource { FUSED, GNSS, BAROMETER, MANUAL }

enum class PressureSource { SENSOR, WEATHER }

enum class GnssFixState { DISABLED, SEARCHING, FIXED, NO_PERMISSION }

enum class GnssSignalWarning { UNSTABLE, UNAVAILABLE }

enum class WeatherKind { CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, SHOWERS, THUNDERSTORM, UNKNOWN }

enum class DistanceUnit { METERS, FEET, KILOMETERS, MILES }

enum class PressureUnit {
    MILLIMETERS_MERCURY,
    HECTOPASCALS,
    MILLIBARS,
    KILOPASCALS,
    BAR,
    ATMOSPHERES,
    INCHES_MERCURY,
}

enum class TemperatureUnit { CELSIUS, FAHRENHEIT, KELVIN }

enum class WindSpeedUnit { METERS_PER_SECOND, KILOMETERS_PER_HOUR, MILES_PER_HOUR, KNOTS }

enum class CoordinateFormat { DECIMAL_DEGREES, DMS, UTM, MGRS }

enum class AppLanguage {
    SYSTEM,
    ENGLISH,
    RUSSIAN,
    CHINESE_SIMPLIFIED,
    HINDI,
    SPANISH,
    ARABIC,
    FRENCH,
    BENGALI,
    PORTUGUESE_BRAZIL,
    INDONESIAN,
    URDU,
    GERMAN,
}

enum class CompassAccuracy { UNKNOWN, UNRELIABLE, LOW, MEDIUM, HIGH }

enum class NorthReference { MAGNETIC, TRUE }

enum class AlertDirection { ASCENDING, DESCENDING }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class PositionSource { GNSS, NETWORK }

const val DEFAULT_TRACKING_ALTITUDE_STEP_METERS = 5.0
const val DEFAULT_TRACKING_SAMPLE_INTERVAL_MILLIS = 1_000L
const val DEFAULT_TRACKING_COORDINATE_MAX_AGE_MILLIS = 10_000L

data class Coordinates(
    val latitude: Double,
    val longitude: Double,
    val horizontalAccuracyMeters: Float? = null,
    val timestampMillis: Long = System.currentTimeMillis(),
    val source: PositionSource = PositionSource.NETWORK,
)

data class MovementCourse(
    val bearingDegrees: Float,
    val accuracyDegrees: Float?,
    val speedMetersPerSecond: Float,
    val timestampMillis: Long,
)

data class AltitudeReading(
    val metersMsl: Double,
    val accuracyMeters: Float?,
    val source: AltitudeSource,
    val timestampMillis: Long,
    val manuallyAdjusted: Boolean,
)

data class CompassReading(
    val headingDegrees: Float? = null,
    val accuracy: CompassAccuracy = CompassAccuracy.UNKNOWN,
    val northReference: NorthReference = NorthReference.MAGNETIC,
    val magneticFieldMicroTesla: Float? = null,
    val sensorAvailable: Boolean = true,
)

data class GnssState(
    val state: GnssFixState = GnssFixState.NO_PERMISSION,
    val visibleSatellites: Int = 0,
    val usedSatellites: Int? = null,
    val horizontalAccuracyMeters: Float? = null,
    val signalWarning: GnssSignalWarning? = null,
)

data class PressureReading(
    val hPa: Float,
    val source: PressureSource,
    val timestampMillis: Long,
)

data class WeatherSnapshot(
    val temperatureC: Double,
    val apparentTemperatureC: Double?,
    val humidityPercent: Int?,
    val windSpeedMetersPerSecond: Double?,
    val weatherCode: Int,
    val kind: WeatherKind,
    val surfacePressureHpa: Double?,
    val latitude: Double,
    val longitude: Double,
    val updatedAtMillis: Long,
    val stale: Boolean = false,
    val isDay: Boolean = true,
    val forecast: WeatherForecast? = null,
)

data class TerrainElevation(
    val meters: Double,
    val latitude: Double,
    val longitude: Double,
    val updatedAtMillis: Long,
    val stale: Boolean = false,
)

data class LiveReading(
    val deviceCapabilities: DeviceCapabilities? = null,
    val coordinates: Coordinates? = null,
    val placeName: String? = null,
    val altitude: AltitudeReading? = null,
    val terrain: TerrainElevation? = null,
    val compass: CompassReading = CompassReading(),
    val movementCourse: MovementCourse? = null,
    val gnss: GnssState = GnssState(),
    val pressure: PressureReading? = null,
    val weather: WeatherSnapshot? = null,
)

data class UserSettings(
    val distanceUnit: DistanceUnit = DistanceUnit.METERS,
    val pressureUnit: PressureUnit = PressureUnit.MILLIMETERS_MERCURY,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val windSpeedUnit: WindSpeedUnit = WindSpeedUnit.METERS_PER_SECOND,
    val coordinateFormat: CoordinateFormat = CoordinateFormat.DECIMAL_DEGREES,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val activeWaypointId: Long? = null,
    val keepScreenOn: Boolean = false,
    val calibrationOffsetMeters: Double = 0.0,
    val trackingAltitudeStepMeters: Double = DEFAULT_TRACKING_ALTITUDE_STEP_METERS,
    val trackingSampleIntervalMillis: Long = DEFAULT_TRACKING_SAMPLE_INTERVAL_MILLIS,
    val trackingCoordinateMaxAgeMillis: Long = DEFAULT_TRACKING_COORDINATE_MAX_AGE_MILLIS,
    val gnssDiagnosticLoggingEnabled: Boolean = false,
    // Null until DataStore has loaded, so a dismissed notice cannot flash on startup.
    val hardwareNoticeAcknowledged: Boolean? = null,
)

data class TrackingState(
    val active: Boolean = false,
    val sessionId: Long? = null,
    val startedAtMillis: Long? = null,
    val pointCount: Int = 0,
)
