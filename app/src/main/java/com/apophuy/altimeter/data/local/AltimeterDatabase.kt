package com.apophuy.altimeter.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TrackSessionEntity::class,
        TrackPointEntity::class,
        WeatherCacheEntity::class,
        TerrainCacheEntity::class,
        WaypointEntity::class,
        AltitudeAlertEntity::class,
        TrackAlertEventEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AltimeterDatabase : RoomDatabase() {
    abstract fun altimeterDao(): AltimeterDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE weather_cache ADD COLUMN forecastJson TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS waypoints (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL,
                        altitudeMetersMsl REAL,
                        horizontalAccuracyMeters REAL,
                        createdAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS altitude_alerts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        altitudeMetersMsl REAL NOT NULL,
                        enabled INTEGER NOT NULL,
                        createdAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS track_alert_events (
                        sessionId INTEGER NOT NULL,
                        alertId INTEGER NOT NULL,
                        direction TEXT NOT NULL,
                        triggeredAtMillis INTEGER NOT NULL,
                        altitudeMetersMsl REAL NOT NULL,
                        PRIMARY KEY(sessionId, alertId, direction),
                        FOREIGN KEY(sessionId) REFERENCES track_sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(alertId) REFERENCES altitude_alerts(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_track_alert_events_sessionId ON track_alert_events(sessionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_track_alert_events_alertId ON track_alert_events(alertId)")
            }
        }

        @Volatile private var instance: AltimeterDatabase? = null

        fun get(context: Context): AltimeterDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AltimeterDatabase::class.java,
                "altimeter.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }
    }
}
