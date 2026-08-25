package com.apophuy.altimeter.data

import com.apophuy.altimeter.data.local.AltimeterDao
import com.apophuy.altimeter.data.local.WaypointEntity
import com.apophuy.altimeter.model.AltitudeReading
import com.apophuy.altimeter.model.Coordinates
import kotlinx.coroutines.flow.Flow

class WaypointRepository(
    private val dao: AltimeterDao,
) {
    val waypoints: Flow<List<WaypointEntity>> = dao.observeWaypoints()

    suspend fun save(name: String, coordinates: Coordinates, altitude: AltitudeReading?): Long =
        dao.insertWaypoint(
            WaypointEntity(
                name = name.trim(),
                latitude = coordinates.latitude,
                longitude = coordinates.longitude,
                altitudeMetersMsl = altitude?.metersMsl,
                horizontalAccuracyMeters = coordinates.horizontalAccuracyMeters,
                createdAtMillis = System.currentTimeMillis(),
            ),
        )

    suspend fun rename(id: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) dao.renameWaypoint(id, trimmed)
    }

    suspend fun delete(id: Long) = dao.deleteWaypoint(id)
}
