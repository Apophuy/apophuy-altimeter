package com.apophuy.altimeter.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.apophuy.altimeter.AltimeterApplication
import com.apophuy.altimeter.data.local.TrackPointEntity
import com.apophuy.altimeter.data.local.TrackSessionEntity
import com.apophuy.altimeter.data.local.AltitudeAlertEntity
import com.apophuy.altimeter.data.local.WaypointEntity
import com.apophuy.altimeter.export.ExportFormat
import com.apophuy.altimeter.export.TrackExporter
import com.apophuy.altimeter.model.AppLanguage
import com.apophuy.altimeter.model.CoordinateFormat
import com.apophuy.altimeter.model.DistanceUnit
import com.apophuy.altimeter.model.LiveReading
import com.apophuy.altimeter.model.PressureUnit
import com.apophuy.altimeter.model.TemperatureUnit
import com.apophuy.altimeter.model.ThemeMode
import com.apophuy.altimeter.model.TrackingState
import com.apophuy.altimeter.model.UserSettings
import com.apophuy.altimeter.model.WindSpeedUnit
import com.apophuy.altimeter.service.TrackingService
import com.apophuy.altimeter.util.isWaypointPositionUsable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppUiState(
    val live: LiveReading = LiveReading(),
    val settings: UserSettings = UserSettings(),
    val tracking: TrackingState = TrackingState(),
    val sessions: List<TrackSessionEntity> = emptyList(),
    val waypoints: List<WaypointEntity> = emptyList(),
    val altitudeAlerts: List<AltitudeAlertEntity> = emptyList(),
)

private data class CoreUiState(
    val live: LiveReading,
    val settings: UserSettings,
    val tracking: TrackingState,
    val sessions: List<TrackSessionEntity>,
)

data class SessionDetails(
    val session: TrackSessionEntity,
    val points: List<TrackPointEntity>,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as AltimeterApplication).container
    private val exporter = TrackExporter(application, container.trackRepository)

    private val coreUiState = combine(
        container.liveRepository.reading,
        container.settingsRepository.settings,
        container.trackRepository.trackingState,
        container.trackRepository.sessions,
    ) { live, settings, tracking, sessions ->
        CoreUiState(live, settings, tracking, sessions)
    }

    val uiState: StateFlow<AppUiState> = combine(
        coreUiState,
        container.waypointRepository.waypoints,
        container.altitudeAlertRepository.alerts,
    ) { core, waypoints, alerts ->
        AppUiState(
            live = core.live,
            settings = core.settings,
            tracking = core.tracking,
            sessions = core.sessions,
            waypoints = waypoints,
            altitudeAlerts = alerts,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), AppUiState())

    private val selectedSessionId = MutableStateFlow<Long?>(null)
    val selectedSession: StateFlow<SessionDetails?> = selectedSessionId
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            else combine(
                container.trackRepository.observeSession(id),
                container.trackRepository.observePoints(id),
            ) { session, points -> session?.let { SessionDetails(it, points) } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)

    fun acquireLiveData() = container.liveRepository.acquire(ACTIVITY_OWNER)

    fun releaseLiveData() = container.liveRepository.release(ACTIVITY_OWNER)

    fun onPermissionChanged() = container.liveRepository.onPermissionChanged()

    fun startTracking() = TrackingService.start(getApplication())

    fun stopTracking() = TrackingService.stop(getApplication())

    fun acknowledgeHardwareNotice() {
        viewModelScope.launch { container.settingsRepository.acknowledgeHardwareNotice() }
    }

    fun selectSession(id: Long?) {
        selectedSessionId.value = id
    }

    fun deleteSession(id: Long) {
        viewModelScope.launch {
            container.trackRepository.deleteSession(id)
            if (selectedSessionId.value == id) selectedSessionId.value = null
        }
    }

    suspend fun createShareIntent(id: Long, format: ExportFormat): Intent? =
        exporter.shareIntent(id, format)

    suspend fun createGnssDiagnosticShareIntent(): Intent? =
        container.gnssDiagnosticLogger.createShareIntent()

    fun setDistanceUnit(value: DistanceUnit) {
        viewModelScope.launch { container.settingsRepository.setDistanceUnit(value) }
    }

    fun setPressureUnit(value: PressureUnit) {
        viewModelScope.launch { container.settingsRepository.setPressureUnit(value) }
    }

    fun setTemperatureUnit(value: TemperatureUnit) {
        viewModelScope.launch { container.settingsRepository.setTemperatureUnit(value) }
    }

    fun setWindSpeedUnit(value: WindSpeedUnit) {
        viewModelScope.launch { container.settingsRepository.setWindSpeedUnit(value) }
    }

    fun setCoordinateFormat(value: CoordinateFormat) {
        viewModelScope.launch { container.settingsRepository.setCoordinateFormat(value) }
    }

    fun setLanguage(value: AppLanguage) {
        viewModelScope.launch { container.settingsRepository.setLanguage(value) }
    }

    fun setThemeMode(value: ThemeMode) {
        viewModelScope.launch { container.settingsRepository.setThemeMode(value) }
    }

    fun setKeepScreenOn(value: Boolean) {
        viewModelScope.launch { container.settingsRepository.setKeepScreenOn(value) }
    }

    fun saveWaypoint(name: String) {
        val live = uiState.value.live
        val coordinates = live.coordinates ?: return
        if (!isWaypointPositionUsable(coordinates, System.currentTimeMillis())) return
        viewModelScope.launch {
            container.waypointRepository.save(name, coordinates, live.altitude)
        }
    }

    fun renameWaypoint(id: Long, name: String) {
        viewModelScope.launch { container.waypointRepository.rename(id, name) }
    }

    fun deleteWaypoint(id: Long) {
        viewModelScope.launch {
            container.waypointRepository.delete(id)
            if (uiState.value.settings.activeWaypointId == id) {
                container.settingsRepository.setActiveWaypointId(null)
            }
        }
    }

    fun selectWaypoint(id: Long?) {
        viewModelScope.launch { container.settingsRepository.setActiveWaypointId(id) }
    }

    fun addAltitudeAlert(altitudeMetersMsl: Double) {
        viewModelScope.launch { container.altitudeAlertRepository.add(altitudeMetersMsl) }
    }

    fun replaceAltitudeAlert(id: Long, altitudeMetersMsl: Double) {
        viewModelScope.launch { container.altitudeAlertRepository.replace(id, altitudeMetersMsl) }
    }

    fun setAltitudeAlertEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch { container.altitudeAlertRepository.setEnabled(id, enabled) }
    }

    fun deleteAltitudeAlert(id: Long) {
        viewModelScope.launch { container.altitudeAlertRepository.delete(id) }
    }

    fun setTrackingAltitudeStepMeters(value: Double) {
        viewModelScope.launch { container.settingsRepository.setTrackingAltitudeStepMeters(value) }
    }

    fun setTrackingSampleIntervalMillis(value: Long) {
        viewModelScope.launch { container.settingsRepository.setTrackingSampleIntervalMillis(value) }
    }

    fun setTrackingCoordinateMaxAgeMillis(value: Long) {
        viewModelScope.launch { container.settingsRepository.setTrackingCoordinateMaxAgeMillis(value) }
    }

    fun setGnssDiagnosticLoggingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.setGnssDiagnosticLoggingEnabled(enabled)
        }
    }

    fun clearGnssDiagnosticLog() {
        viewModelScope.launch { container.gnssDiagnosticLogger.clear() }
    }

    fun calibrateTo(targetMeters: Double) {
        val current = uiState.value.live.altitude?.metersMsl ?: return
        val existing = uiState.value.settings.calibrationOffsetMeters
        viewModelScope.launch {
            container.settingsRepository.setCalibrationOffset(existing + targetMeters - current)
        }
    }

    fun calibrateToTerrain() {
        val terrain = uiState.value.live.terrain?.meters ?: return
        calibrateTo(terrain)
    }

    fun resetCalibration() {
        viewModelScope.launch { container.settingsRepository.resetCalibration() }
    }

    companion object {
        private const val ACTIVITY_OWNER = "main-activity"
    }
}
