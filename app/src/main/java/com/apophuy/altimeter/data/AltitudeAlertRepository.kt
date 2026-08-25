package com.apophuy.altimeter.data

import com.apophuy.altimeter.data.local.AltimeterDao
import com.apophuy.altimeter.data.local.AltitudeAlertEntity
import com.apophuy.altimeter.data.local.TrackAlertEventEntity
import com.apophuy.altimeter.model.AlertDirection
import com.apophuy.altimeter.util.AltitudeAlertTracker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AltitudeAlertRepository(
    private val dao: AltimeterDao,
) {
    val alerts: Flow<List<AltitudeAlertEntity>> = dao.observeAltitudeAlerts()

    private val mutex = Mutex()
    private var activeSessionId: Long? = null
    private var calibrationOffsetMeters: Double? = null
    private val tracker = AltitudeAlertTracker()

    suspend fun add(altitudeMetersMsl: Double): Long? = mutex.withLock {
        if (!altitudeMetersMsl.isFinite()) return null
        dao.insertAltitudeAlert(
            AltitudeAlertEntity(
                altitudeMetersMsl = altitudeMetersMsl,
                createdAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun replace(id: Long, altitudeMetersMsl: Double): Long? {
        if (!altitudeMetersMsl.isFinite()) return null
        mutex.withLock {
            dao.updateAltitudeAlert(id, altitudeMetersMsl)
            tracker.resetBaseline(id)
        }
        return id
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) = mutex.withLock {
        dao.setAltitudeAlertEnabled(id, enabled)
        tracker.resetBaseline(id)
    }

    suspend fun delete(id: Long) = mutex.withLock {
        dao.deleteAltitudeAlert(id)
        tracker.resetBaseline(id)
    }

    suspend fun get(id: Long): AltitudeAlertEntity? = dao.getAltitudeAlert(id)

    suspend fun evaluate(
        sessionId: Long,
        altitudeMetersMsl: Double,
        currentCalibrationOffsetMeters: Double,
        timestampMillis: Long,
    ): List<TrackAlertEventEntity> = mutex.withLock {
        if (!altitudeMetersMsl.isFinite()) return@withLock emptyList()
        val sessionChanged = activeSessionId != sessionId
        val calibrationChanged = calibrationOffsetMeters != null &&
            calibrationOffsetMeters != currentCalibrationOffsetMeters
        if (sessionChanged) {
            activeSessionId = sessionId
            tracker.startSession(dao.getTrackAlertEvents(sessionId).mapNotNull { event ->
                runCatching { event.alertId to AlertDirection.valueOf(event.direction) }.getOrNull()
            }.toSet())
        }
        if (calibrationChanged) tracker.resetBaselines()
        calibrationOffsetMeters = currentCalibrationOffsetMeters

        val alerts = dao.getEnabledAltitudeAlerts()
        val activeIds = alerts.mapTo(mutableSetOf()) { it.id }
        tracker.retainAlerts(activeIds)
        buildList {
            for (alert in alerts) {
                val direction = tracker.crossingCandidate(
                    alertId = alert.id,
                    thresholdMeters = alert.altitudeMetersMsl,
                    altitudeMeters = altitudeMetersMsl,
                ) ?: continue
                val event = TrackAlertEventEntity(
                    sessionId = sessionId,
                    alertId = alert.id,
                    direction = direction.name,
                    triggeredAtMillis = timestampMillis,
                    altitudeMetersMsl = altitudeMetersMsl,
                )
                if (dao.insertTrackAlertEvent(event) != -1L) {
                    tracker.markTriggered(alert.id, direction)
                    add(event)
                }
            }
        }
    }

    suspend fun endSession() = mutex.withLock {
        activeSessionId = null
        calibrationOffsetMeters = null
        tracker.startSession()
    }
}
