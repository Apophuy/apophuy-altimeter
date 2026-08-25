package com.apophuy.altimeter.data

import com.apophuy.altimeter.data.local.AltimeterDao
import com.apophuy.altimeter.data.local.TrackPointEntity
import com.apophuy.altimeter.data.local.TrackSessionEntity
import com.apophuy.altimeter.model.LiveReading
import com.apophuy.altimeter.model.TrackingState
import com.apophuy.altimeter.util.shouldStorePoint
import com.apophuy.altimeter.util.TrackProgress
import com.apophuy.altimeter.util.nextTrackProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TrackRepository(
    private val dao: AltimeterDao,
    private val settingsRepository: SettingsRepository,
) {
    val sessions: Flow<List<TrackSessionEntity>> = dao.observeSessions()

    private val _trackingState = MutableStateFlow(TrackingState())
    val trackingState: StateFlow<TrackingState> = _trackingState

    private val mutex = Mutex()
    private var session: TrackSessionEntity? = null
    private var lastStoredAltitude: Double? = null

    suspend fun recoverActive(): TrackingState = mutex.withLock {
        val active = dao.getActiveSession()
        session = active
        lastStoredAltitude = active?.let { dao.getLastPoint(it.id)?.altitudeMetersMsl }
        _trackingState.value = active?.let {
            TrackingState(true, it.id, it.startedAtMillis, it.pointCount)
        } ?: TrackingState()
        _trackingState.value
    }

    suspend fun start(initial: LiveReading): Long = mutex.withLock {
        session?.let { return@withLock it.id }
        dao.getActiveSession()?.let { existing ->
            session = existing
            lastStoredAltitude = dao.getLastPoint(existing.id)?.altitudeMetersMsl
            _trackingState.value = TrackingState(true, existing.id, existing.startedAtMillis, existing.pointCount)
            return@withLock existing.id
        }

        val now = System.currentTimeMillis()
        val altitude = initial.altitude?.metersMsl
        val created = TrackSessionEntity(
            startedAtMillis = now,
            startAltitudeMeters = altitude,
            minAltitudeMeters = altitude,
            maxAltitudeMeters = altitude,
        )
        val id = dao.insertSession(created)
        session = created.copy(id = id)
        lastStoredAltitude = null
        _trackingState.value = TrackingState(true, id, now, 0)
        appendLocked(initial)
        id
    }

    suspend fun append(reading: LiveReading): Boolean = mutex.withLock { appendLocked(reading) }

    private suspend fun appendLocked(reading: LiveReading): Boolean {
        val currentSession = session ?: return false
        val altitude = reading.altitude ?: return false
        val settings = settingsRepository.settings.value
        if (!shouldStorePoint(
                previousAltitudeMeters = lastStoredAltitude,
                currentAltitudeMeters = altitude.metersMsl,
                altitudeStepMeters = settings.trackingAltitudeStepMeters,
            )
        ) return false

        val progress = nextTrackProgress(
            current = TrackProgress(
                minimumMeters = currentSession.minAltitudeMeters,
                maximumMeters = currentSession.maxAltitudeMeters,
                gainMeters = currentSession.elevationGainMeters,
                lossMeters = currentSession.elevationLossMeters,
                pointCount = currentSession.pointCount,
            ),
            previousAltitudeMeters = lastStoredAltitude,
            altitudeMeters = altitude.metersMsl,
        )
        val coordinates = reading.coordinates?.takeIf {
            System.currentTimeMillis() - it.timestampMillis <= settings.trackingCoordinateMaxAgeMillis
        }

        dao.insertPoint(
            TrackPointEntity(
                sessionId = currentSession.id,
                timestampMillis = altitude.timestampMillis,
                latitude = coordinates?.latitude,
                longitude = coordinates?.longitude,
                altitudeMetersMsl = altitude.metersMsl,
                pressureHpa = reading.pressure?.hPa,
                horizontalAccuracyMeters = coordinates?.horizontalAccuracyMeters,
                verticalAccuracyMeters = altitude.accuracyMeters,
                altitudeSource = altitude.source.name,
            ),
        )
        dao.updateProgress(
            sessionId = currentSession.id,
            altitude = altitude.metersMsl,
            minimum = progress.minimumMeters!!,
            maximum = progress.maximumMeters!!,
            gain = progress.gainMeters,
            loss = progress.lossMeters,
            pointCount = progress.pointCount,
        )
        session = currentSession.copy(
            startAltitudeMeters = currentSession.startAltitudeMeters ?: altitude.metersMsl,
            minAltitudeMeters = progress.minimumMeters,
            maxAltitudeMeters = progress.maximumMeters,
            elevationGainMeters = progress.gainMeters,
            elevationLossMeters = progress.lossMeters,
            pointCount = progress.pointCount,
        )
        lastStoredAltitude = altitude.metersMsl
        _trackingState.value = _trackingState.value.copy(pointCount = progress.pointCount)
        return true
    }

    suspend fun stop(finalReading: LiveReading, interrupted: Boolean = false) = mutex.withLock {
        val current = session ?: dao.getActiveSession() ?: return@withLock
        dao.finishSession(
            sessionId = current.id,
            endedAtMillis = System.currentTimeMillis(),
            endAltitudeMeters = finalReading.altitude?.metersMsl ?: lastStoredAltitude,
            interrupted = interrupted,
        )
        session = null
        lastStoredAltitude = null
        _trackingState.value = TrackingState()
    }

    fun observeSession(sessionId: Long): Flow<TrackSessionEntity?> = dao.observeSession(sessionId)

    fun observePoints(sessionId: Long): Flow<List<TrackPointEntity>> = dao.observePoints(sessionId)

    suspend fun getSession(sessionId: Long): TrackSessionEntity? = dao.getSession(sessionId)

    suspend fun getPoints(sessionId: Long): List<TrackPointEntity> = dao.getPoints(sessionId)

    suspend fun deleteSession(sessionId: Long) {
        if (_trackingState.value.sessionId != sessionId) dao.deleteSession(sessionId)
    }
}
