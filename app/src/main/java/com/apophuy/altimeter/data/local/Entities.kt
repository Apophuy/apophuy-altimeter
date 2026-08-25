package com.apophuy.altimeter.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "track_sessions")
data class TrackSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAtMillis: Long,
    val endedAtMillis: Long? = null,
    val startAltitudeMeters: Double? = null,
    val endAltitudeMeters: Double? = null,
    val minAltitudeMeters: Double? = null,
    val maxAltitudeMeters: Double? = null,
    val elevationGainMeters: Double = 0.0,
    val elevationLossMeters: Double = 0.0,
    val pointCount: Int = 0,
    val interrupted: Boolean = false,
)

@Entity(
    tableName = "track_points",
    foreignKeys = [
        ForeignKey(
            entity = TrackSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index(value = ["sessionId", "timestampMillis"])],
)
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestampMillis: Long,
    val latitude: Double?,
    val longitude: Double?,
    val altitudeMetersMsl: Double,
    val pressureHpa: Float?,
    val horizontalAccuracyMeters: Float?,
    val verticalAccuracyMeters: Float?,
    val altitudeSource: String,
)

@Entity(tableName = "weather_cache")
data class WeatherCacheEntity(
    @PrimaryKey val id: Int = 1,
    val latitude: Double,
    val longitude: Double,
    val temperatureC: Double,
    val apparentTemperatureC: Double?,
    val humidityPercent: Int?,
    val windSpeedMetersPerSecond: Double?,
    val weatherCode: Int,
    val surfacePressureHpa: Double?,
    val updatedAtMillis: Long,
    val forecastJson: String? = null,
)

@Entity(tableName = "terrain_cache")
data class TerrainCacheEntity(
    @PrimaryKey val id: Int = 1,
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double,
    val updatedAtMillis: Long,
)

@Entity(tableName = "waypoints")
data class WaypointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val altitudeMetersMsl: Double? = null,
    val horizontalAccuracyMeters: Float? = null,
    val createdAtMillis: Long,
)

@Entity(tableName = "altitude_alerts")
data class AltitudeAlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val altitudeMetersMsl: Double,
    val enabled: Boolean = true,
    val createdAtMillis: Long,
)

@Entity(
    tableName = "track_alert_events",
    primaryKeys = ["sessionId", "alertId", "direction"],
    foreignKeys = [
        ForeignKey(
            entity = TrackSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = AltitudeAlertEntity::class,
            parentColumns = ["id"],
            childColumns = ["alertId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index("alertId")],
)
data class TrackAlertEventEntity(
    val sessionId: Long,
    val alertId: Long,
    val direction: String,
    val triggeredAtMillis: Long,
    val altitudeMetersMsl: Double,
)
