package com.apophuy.altimeter

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.apophuy.altimeter.data.local.AltimeterDatabase
import com.apophuy.altimeter.data.local.AltitudeAlertEntity
import com.apophuy.altimeter.data.local.WaypointEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeatherCacheMigrationTest {
    @Test
    fun versionOneMigrationPreservesTracksAndCachedWeather() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "weather-migration-test.db"
        context.deleteDatabase(name)
        val helper = createLegacyDatabase(context, instrumentation.context, name, 1)
        helper.writableDatabase.apply {
            execSQL("INSERT INTO track_sessions (id,startedAtMillis,elevationGainMeters,elevationLossMeters,pointCount,interrupted) VALUES (1,1000,0,0,1,0)")
            execSQL("INSERT INTO track_points (sessionId,timestampMillis,altitudeMetersMsl,altitudeSource) VALUES (1,1001,123.5,'GNSS')")
            execSQL("INSERT INTO weather_cache (id,latitude,longitude,temperatureC,weatherCode,updatedAtMillis) VALUES (1,55.75,37.61,12.0,3,1000)")
        }
        helper.close()
        val database = Room.databaseBuilder(context, AltimeterDatabase::class.java, name)
            .addMigrations(AltimeterDatabase.MIGRATION_1_2, AltimeterDatabase.MIGRATION_2_3).build()
        try {
            val dao = database.altimeterDao()
            assertEquals(1, dao.getSession(1)!!.pointCount)
            assertEquals(123.5, dao.getPoints(1).single().altitudeMetersMsl, 0.001)
            val cache = dao.getWeatherCache()!!
            assertEquals(12.0, cache.temperatureC, 0.001)
            assertNull(cache.forecastJson)
            dao.putWeatherCache(cache.copy(forecastJson = "{}"))
            assertEquals("{}", dao.getWeatherCache()!!.forecastJson)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun versionTwoMigrationPreservesDataAndCreatesOutdoorTables() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "outdoor-migration-test.db"
        context.deleteDatabase(name)
        val helper = createLegacyDatabase(context, instrumentation.context, name, 2)
        helper.writableDatabase.apply {
            execSQL("INSERT INTO track_sessions (id,startedAtMillis,elevationGainMeters,elevationLossMeters,pointCount,interrupted) VALUES (1,1000,0,0,1,0)")
            execSQL("INSERT INTO track_points (sessionId,timestampMillis,altitudeMetersMsl,altitudeSource) VALUES (1,1001,123.5,'GNSS')")
            execSQL("INSERT INTO weather_cache (id,latitude,longitude,temperatureC,weatherCode,updatedAtMillis,forecastJson) VALUES (1,55.75,37.61,12.0,3,1000,'{}')")
        }
        helper.close()
        val database = Room.databaseBuilder(context, AltimeterDatabase::class.java, name)
            .addMigrations(AltimeterDatabase.MIGRATION_2_3).build()
        try {
            val dao = database.altimeterDao()
            assertEquals(123.5, dao.getPoints(1).single().altitudeMetersMsl, 0.001)
            assertEquals("{}", dao.getWeatherCache()!!.forecastJson)
            dao.insertWaypoint(WaypointEntity(name = "Camp", latitude = 55.75, longitude = 37.61, createdAtMillis = 2_000L))
            dao.insertAltitudeAlert(AltitudeAlertEntity(altitudeMetersMsl = 150.0, createdAtMillis = 2_000L))
            assertEquals("Camp", dao.observeWaypoints().first().single().name)
            assertEquals(150.0, dao.getEnabledAltitudeAlerts().single().altitudeMetersMsl, 0.001)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    private fun createLegacyDatabase(
        context: Context,
        assetsContext: Context,
        name: String,
        version: Int,
    ): SupportSQLiteOpenHelper {
        val schema = JSONObject(assetsContext.assets.open(
            "com.apophuy.altimeter.data.local.AltimeterDatabase/$version.json",
        ).bufferedReader().use { it.readText() }).getJSONObject("database")
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        val entities = schema.getJSONArray("entities")
                        for (index in 0 until entities.length()) {
                            val entity = entities.getJSONObject(index)
                            val table = entity.getString("tableName")
                            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                            entity.optJSONArray("indices")?.let { indices ->
                                for (i in 0 until indices.length()) {
                                    db.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", table))
                                }
                            }
                        }
                        val queries = schema.getJSONArray("setupQueries")
                        for (index in 0 until queries.length()) db.execSQL(queries.getString(index))
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build(),
        )
    }
}
