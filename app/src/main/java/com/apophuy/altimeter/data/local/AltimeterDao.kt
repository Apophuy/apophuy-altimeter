package com.apophuy.altimeter.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AltimeterDao {
    @Insert
    suspend fun insertSession(session: TrackSessionEntity): Long

    @Insert
    suspend fun insertPoint(point: TrackPointEntity): Long

    @Query("SELECT * FROM track_sessions ORDER BY startedAtMillis DESC")
    fun observeSessions(): Flow<List<TrackSessionEntity>>

    @Query("SELECT * FROM track_sessions WHERE id = :sessionId")
    fun observeSession(sessionId: Long): Flow<TrackSessionEntity?>

    @Query("SELECT * FROM track_sessions WHERE id = :sessionId")
    suspend fun getSession(sessionId: Long): TrackSessionEntity?

    @Query("SELECT * FROM track_sessions WHERE endedAtMillis IS NULL ORDER BY startedAtMillis DESC LIMIT 1")
    suspend fun getActiveSession(): TrackSessionEntity?

    @Query("SELECT * FROM track_points WHERE sessionId = :sessionId ORDER BY timestampMillis ASC")
    fun observePoints(sessionId: Long): Flow<List<TrackPointEntity>>

    @Query("SELECT * FROM track_points WHERE sessionId = :sessionId ORDER BY timestampMillis ASC")
    suspend fun getPoints(sessionId: Long): List<TrackPointEntity>

    @Query("SELECT * FROM track_points WHERE sessionId = :sessionId ORDER BY timestampMillis DESC LIMIT 1")
    suspend fun getLastPoint(sessionId: Long): TrackPointEntity?

    @Query(
        """
        UPDATE track_sessions SET
            startAltitudeMeters = COALESCE(startAltitudeMeters, :altitude),
            minAltitudeMeters = :minimum,
            maxAltitudeMeters = :maximum,
            elevationGainMeters = :gain,
            elevationLossMeters = :loss,
            pointCount = :pointCount
        WHERE id = :sessionId
        """,
    )
    suspend fun updateProgress(
        sessionId: Long,
        altitude: Double,
        minimum: Double,
        maximum: Double,
        gain: Double,
        loss: Double,
        pointCount: Int,
    )

    @Query(
        """
        UPDATE track_sessions SET
            endedAtMillis = :endedAtMillis,
            endAltitudeMeters = :endAltitudeMeters,
            interrupted = :interrupted
        WHERE id = :sessionId
        """,
    )
    suspend fun finishSession(
        sessionId: Long,
        endedAtMillis: Long,
        endAltitudeMeters: Double?,
        interrupted: Boolean,
    )

    @Query("DELETE FROM track_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putWeatherCache(cache: WeatherCacheEntity)

    @Query("SELECT * FROM weather_cache WHERE id = 1")
    suspend fun getWeatherCache(): WeatherCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putTerrainCache(cache: TerrainCacheEntity)

    @Query("SELECT * FROM terrain_cache WHERE id = 1")
    suspend fun getTerrainCache(): TerrainCacheEntity?

    @Query("SELECT * FROM waypoints ORDER BY createdAtMillis DESC")
    fun observeWaypoints(): Flow<List<WaypointEntity>>

    @Insert
    suspend fun insertWaypoint(waypoint: WaypointEntity): Long

    @Query("UPDATE waypoints SET name = :name WHERE id = :waypointId")
    suspend fun renameWaypoint(waypointId: Long, name: String)

    @Query("DELETE FROM waypoints WHERE id = :waypointId")
    suspend fun deleteWaypoint(waypointId: Long)

    @Query("SELECT * FROM altitude_alerts ORDER BY altitudeMetersMsl ASC")
    fun observeAltitudeAlerts(): Flow<List<AltitudeAlertEntity>>

    @Query("SELECT * FROM altitude_alerts WHERE enabled = 1 ORDER BY altitudeMetersMsl ASC")
    suspend fun getEnabledAltitudeAlerts(): List<AltitudeAlertEntity>

    @Query("SELECT * FROM altitude_alerts WHERE id = :alertId")
    suspend fun getAltitudeAlert(alertId: Long): AltitudeAlertEntity?

    @Insert
    suspend fun insertAltitudeAlert(alert: AltitudeAlertEntity): Long

    @Query("UPDATE altitude_alerts SET enabled = :enabled WHERE id = :alertId")
    suspend fun setAltitudeAlertEnabled(alertId: Long, enabled: Boolean)

    @Query("UPDATE altitude_alerts SET altitudeMetersMsl = :altitudeMetersMsl WHERE id = :alertId")
    suspend fun updateAltitudeAlert(alertId: Long, altitudeMetersMsl: Double)

    @Query("DELETE FROM altitude_alerts WHERE id = :alertId")
    suspend fun deleteAltitudeAlert(alertId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrackAlertEvent(event: TrackAlertEventEntity): Long

    @Query("SELECT * FROM track_alert_events WHERE sessionId = :sessionId")
    suspend fun getTrackAlertEvents(sessionId: Long): List<TrackAlertEventEntity>
}
