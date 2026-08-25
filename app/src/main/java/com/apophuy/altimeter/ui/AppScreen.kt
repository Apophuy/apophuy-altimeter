package com.apophuy.altimeter.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.apophuy.altimeter.R
import com.apophuy.altimeter.BuildConfig
import com.apophuy.altimeter.data.local.TrackSessionEntity
import com.apophuy.altimeter.data.local.WaypointEntity
import com.apophuy.altimeter.data.local.AltitudeAlertEntity
import com.apophuy.altimeter.export.ExportFormat
import com.apophuy.altimeter.model.AltitudeSource
import com.apophuy.altimeter.model.AppLanguage
import com.apophuy.altimeter.model.CoordinateFormat
import com.apophuy.altimeter.model.DistanceUnit
import com.apophuy.altimeter.model.LiveReading
import com.apophuy.altimeter.model.PressureUnit
import com.apophuy.altimeter.model.TemperatureUnit
import com.apophuy.altimeter.model.ThemeMode
import com.apophuy.altimeter.model.UserSettings
import com.apophuy.altimeter.model.WeatherKind
import com.apophuy.altimeter.model.WindSpeedUnit
import com.apophuy.altimeter.ui.theme.InstrumentAmber
import com.apophuy.altimeter.ui.theme.InstrumentMuted
import com.apophuy.altimeter.ui.theme.InstrumentRed
import com.apophuy.altimeter.ui.theme.InstrumentSurface
import com.apophuy.altimeter.ui.theme.InstrumentSurfaceHigh
import com.apophuy.altimeter.ui.theme.InstrumentTeal
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private enum class AppTab(val glyph: String) {
    DASHBOARD("⌖"), WAYPOINTS("◎"), HISTORY("▥"), WEATHER("☀"), SETTINGS("⚙")
}

private enum class UnitSetting { DISTANCE, PRESSURE, TEMPERATURE, WIND, COORDINATES }

private enum class RecordingSetting { ALTITUDE_STEP, SAMPLE_INTERVAL, COORDINATE_MAX_AGE }

private data class UnitChoice(val label: String, val selected: Boolean, val onClick: () -> Unit)

@Composable
fun AltimeterApp(
    state: AppUiState,
    selectedSession: SessionDetails?,
    hasLocationPermission: Boolean,
    hasNotificationPermission: Boolean,
    onRequestLocationPermission: () -> Unit,
    onStartTracking: () -> Unit,
    onStopTracking: () -> Unit,
    onSelectSession: (Long?) -> Unit,
    onDeleteSession: (Long) -> Unit,
    onShare: (Long, ExportFormat) -> Unit,
    onDistanceUnitChanged: (DistanceUnit) -> Unit,
    onPressureUnitChanged: (PressureUnit) -> Unit,
    onTemperatureUnitChanged: (TemperatureUnit) -> Unit,
    onWindSpeedUnitChanged: (WindSpeedUnit) -> Unit,
    onCoordinateFormatChanged: (CoordinateFormat) -> Unit,
    onLanguageChanged: (AppLanguage) -> Unit,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onKeepScreenOnChanged: (Boolean) -> Unit,
    onTrackingAltitudeStepChanged: (Double) -> Unit,
    onTrackingSampleIntervalChanged: (Long) -> Unit,
    onTrackingCoordinateMaxAgeChanged: (Long) -> Unit,
    onGnssDiagnosticLoggingChanged: (Boolean) -> Unit,
    onShareGnssDiagnosticLog: () -> Unit,
    onClearGnssDiagnosticLog: () -> Unit,
    onCalibrate: (Double) -> Unit,
    onCalibrateToTerrain: () -> Unit,
    onResetCalibration: () -> Unit,
    onHardwareNoticeAcknowledged: () -> Unit,
    onSaveWaypoint: (String) -> Unit,
    onRenameWaypoint: (Long, String) -> Unit,
    onDeleteWaypoint: (Long) -> Unit,
    onSelectWaypoint: (Long?) -> Unit,
    onShareWaypoint: (WaypointEntity) -> Unit,
    onAddAltitudeAlert: (Double) -> Unit,
    onReplaceAltitudeAlert: (Long, Double) -> Unit,
    onAltitudeAlertEnabledChanged: (Long, Boolean) -> Unit,
    onDeleteAltitudeAlert: (Long) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(AppTab.DASHBOARD) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(view, lifecycleOwner, state.settings.keepScreenOn, tab) {
        val shouldKeepScreenOn = state.settings.keepScreenOn &&
            (tab == AppTab.DASHBOARD || tab == AppTab.WAYPOINTS)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> view.keepScreenOn = shouldKeepScreenOn
                Lifecycle.Event.ON_STOP -> view.keepScreenOn = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        view.keepScreenOn = shouldKeepScreenOn &&
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            view.keepScreenOn = false
        }
    }

    val missingHardware = state.live.deviceCapabilities?.missingHardware.orEmpty()
    HardwareNotice(missingHardware, state.settings.hardwareNoticeAcknowledged, onHardwareNoticeAcknowledged)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = InstrumentSurface) {
                AppTab.entries.forEach { item ->
                        val label = when (item) {
                            AppTab.DASHBOARD -> stringResource(R.string.tab_dashboard)
                            AppTab.WAYPOINTS -> stringResource(R.string.tab_waypoints)
                            AppTab.HISTORY -> stringResource(R.string.tab_history)
                            AppTab.WEATHER -> stringResource(R.string.tab_weather)
                            AppTab.SETTINGS -> stringResource(R.string.tab_settings)
                        }
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = {
                                tab = item
                                if (item != AppTab.HISTORY) onSelectSession(null)
                            },
                            icon = {
                                if (item == AppTab.WEATHER) WeatherIcon(WeatherKind.CLEAR, Modifier.size(24.dp))
                                else Text(item.glyph, fontSize = 20.sp)
                            },
                            label = { Text(label, fontSize = 10.sp, maxLines = 1, softWrap = false) },
                        )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                AppTab.DASHBOARD -> DashboardScreen(
                    state = state,
                    hasLocationPermission = hasLocationPermission,
                    onRequestLocationPermission = onRequestLocationPermission,
                    onCoordinatesCopied = {
                        scope.launch { snackbar.showSnackbar(it) }
                    },
                )
                AppTab.WAYPOINTS -> WaypointScreen(
                    state = state,
                    onSave = onSaveWaypoint,
                    onRename = onRenameWaypoint,
                    onDelete = onDeleteWaypoint,
                    onSelect = onSelectWaypoint,
                    onShare = onShareWaypoint,
                )
                AppTab.HISTORY -> Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        HistoryScreen(
                            sessions = state.sessions,
                            details = selectedSession,
                            distanceUnit = state.settings.distanceUnit,
                            onSelect = onSelectSession,
                            onDelete = onDeleteSession,
                            onShare = onShare,
                        )
                    }
                    Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)) {
                        RecordingButton(state, onStartTracking, onStopTracking)
                    }
                }
                AppTab.WEATHER -> WeatherScreen(state.live, state.settings)
                AppTab.SETTINGS -> SettingsScreen(
                    settings = state.settings,
                    live = state.live,
                    altitudeAlerts = state.altitudeAlerts,
                    hasNotificationPermission = hasNotificationPermission,
                    onDistanceUnitChanged = onDistanceUnitChanged,
                    onPressureUnitChanged = onPressureUnitChanged,
                    onTemperatureUnitChanged = onTemperatureUnitChanged,
                    onWindSpeedUnitChanged = onWindSpeedUnitChanged,
                    onCoordinateFormatChanged = onCoordinateFormatChanged,
                    onLanguageChanged = onLanguageChanged,
                    onThemeModeChanged = onThemeModeChanged,
                    onKeepScreenOnChanged = onKeepScreenOnChanged,
                    onTrackingAltitudeStepChanged = onTrackingAltitudeStepChanged,
                    onTrackingSampleIntervalChanged = onTrackingSampleIntervalChanged,
                    onTrackingCoordinateMaxAgeChanged = onTrackingCoordinateMaxAgeChanged,
                    onGnssDiagnosticLoggingChanged = onGnssDiagnosticLoggingChanged,
                    onShareGnssDiagnosticLog = onShareGnssDiagnosticLog,
                    onClearGnssDiagnosticLog = onClearGnssDiagnosticLog,
                    onCalibrate = onCalibrate,
                    onCalibrateToTerrain = onCalibrateToTerrain,
                    onResetCalibration = onResetCalibration,
                    onAddAltitudeAlert = onAddAltitudeAlert,
                    onReplaceAltitudeAlert = onReplaceAltitudeAlert,
                    onAltitudeAlertEnabledChanged = onAltitudeAlertEnabledChanged,
                    onDeleteAltitudeAlert = onDeleteAltitudeAlert,
                )
            }
        }
    }
}

@Composable
private fun RecordingButton(
    state: AppUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.tracking.active) {
        while (state.tracking.active) {
            now = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    val active = state.tracking.active
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Button(
            onClick = {
                if (active) onStop() else onStart()
            },
            modifier = Modifier.fillMaxWidth().height(58.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (active) InstrumentRed else InstrumentTeal,
                contentColor = if (active) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(
                stringResource(if (active) R.string.stop_recording else R.string.start_recording),
                fontWeight = FontWeight.Bold,
            )
        }
        if (active) {
            Text(
                stringResource(R.string.recording_active,
                    formatDuration(now - (state.tracking.startedAtMillis ?: now)), state.tracking.pointCount),
                color = InstrumentTeal,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun HistoryScreen(
    sessions: List<TrackSessionEntity>,
    details: SessionDetails?,
    distanceUnit: DistanceUnit,
    onSelect: (Long?) -> Unit,
    onDelete: (Long) -> Unit,
    onShare: (Long, ExportFormat) -> Unit,
) {
    if (details != null) {
        SessionDetailScreen(details, distanceUnit, onBack = { onSelect(null) }, onDelete, onShare)
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.tab_history), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (sessions.isEmpty()) {
            InstrumentCard {
                Text(stringResource(R.string.history_empty_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.history_empty_text), color = InstrumentMuted)
            }
        } else {
            sessions.forEach { session ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(session.id) },
                    colors = CardDefaults.cardColors(containerColor = InstrumentSurface),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.session_title, formatDateTime(session.startedAtMillis)), fontWeight = FontWeight.Bold)
                            Text(formatAltitude(session.maxAltitudeMeters, distanceUnit, 0), color = InstrumentTeal)
                        }
                        val end = session.endedAtMillis ?: System.currentTimeMillis()
                        Text(stringResource(R.string.session_points, session.pointCount, formatDuration(end - session.startedAtMillis)), color = InstrumentMuted)
                        if (session.interrupted) Text(stringResource(R.string.session_interrupted), color = InstrumentAmber)
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionDetailScreen(
    details: SessionDetails,
    distanceUnit: DistanceUnit,
    onBack: () -> Unit,
    onDelete: (Long) -> Unit,
    onShare: (Long, ExportFormat) -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val session = details.session
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("‹ ${stringResource(R.string.back)}") }
        Text(stringResource(R.string.session_title, formatDateTime(session.startedAtMillis)), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (session.endedAtMillis == null) {
            Text(stringResource(R.string.session_active), color = InstrumentTeal, fontWeight = FontWeight.Bold)
        }
        InstrumentCard {
            AltitudeChart(details.points, Modifier.fillMaxWidth().height(210.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatAltitude(session.minAltitudeMeters, distanceUnit, 0), color = InstrumentMuted)
                Text(formatAltitude(session.maxAltitudeMeters, distanceUnit, 0), color = InstrumentTeal)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(stringResource(R.string.minimum), session.minAltitudeMeters, distanceUnit, Modifier.weight(1f))
            StatCard(stringResource(R.string.maximum), session.maxAltitudeMeters, distanceUnit, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(stringResource(R.string.gain), session.elevationGainMeters, distanceUnit, Modifier.weight(1f))
            StatCard(stringResource(R.string.loss), session.elevationLossMeters, distanceUnit, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { onShare(session.id, ExportFormat.GPX) },
                enabled = session.endedAtMillis != null,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.export_gpx)) }
            OutlinedButton(
                onClick = { onShare(session.id, ExportFormat.CSV) },
                enabled = session.endedAtMillis != null,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.export_csv)) }
        }
        TextButton(
            onClick = { confirmDelete = true },
            enabled = session.endedAtMillis != null,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(stringResource(R.string.delete), color = InstrumentRed)
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_session_title)) },
            text = { Text(stringResource(R.string.delete_session_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete(session.id)
                }) { Text(stringResource(R.string.delete), color = InstrumentRed) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun StatCard(label: String, value: Double?, distanceUnit: DistanceUnit, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = InstrumentSurface), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(label, color = InstrumentMuted, style = MaterialTheme.typography.labelMedium)
            Text(formatAltitude(value, distanceUnit, 0), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SettingsScreen(
    settings: UserSettings,
    live: LiveReading,
    altitudeAlerts: List<AltitudeAlertEntity>,
    hasNotificationPermission: Boolean,
    onDistanceUnitChanged: (DistanceUnit) -> Unit,
    onPressureUnitChanged: (PressureUnit) -> Unit,
    onTemperatureUnitChanged: (TemperatureUnit) -> Unit,
    onWindSpeedUnitChanged: (WindSpeedUnit) -> Unit,
    onCoordinateFormatChanged: (CoordinateFormat) -> Unit,
    onLanguageChanged: (AppLanguage) -> Unit,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onKeepScreenOnChanged: (Boolean) -> Unit,
    onTrackingAltitudeStepChanged: (Double) -> Unit,
    onTrackingSampleIntervalChanged: (Long) -> Unit,
    onTrackingCoordinateMaxAgeChanged: (Long) -> Unit,
    onGnssDiagnosticLoggingChanged: (Boolean) -> Unit,
    onShareGnssDiagnosticLog: () -> Unit,
    onClearGnssDiagnosticLog: () -> Unit,
    onCalibrate: (Double) -> Unit,
    onCalibrateToTerrain: () -> Unit,
    onResetCalibration: () -> Unit,
    onAddAltitudeAlert: (Double) -> Unit,
    onReplaceAltitudeAlert: (Long, Double) -> Unit,
    onAltitudeAlertEnabledChanged: (Long, Boolean) -> Unit,
    onDeleteAltitudeAlert: (Long) -> Unit,
) {
    var calibrationDialog by remember { mutableStateOf(false) }
    var compassCalibrationDialog by remember { mutableStateOf(false) }
    var languageDialog by remember { mutableStateOf(false) }
    var unitSetting by remember { mutableStateOf<UnitSetting?>(null) }
    var recordingSetting by remember { mutableStateOf<RecordingSetting?>(null) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.tab_settings), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        InstrumentCard {
            Text(stringResource(R.string.theme_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            SelectableRow(stringResource(R.string.theme_system), settings.themeMode == ThemeMode.SYSTEM) { onThemeModeChanged(ThemeMode.SYSTEM) }
            SelectableRow(stringResource(R.string.theme_light), settings.themeMode == ThemeMode.LIGHT) { onThemeModeChanged(ThemeMode.LIGHT) }
            SelectableRow(stringResource(R.string.theme_dark), settings.themeMode == ThemeMode.DARK) { onThemeModeChanged(ThemeMode.DARK) }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.keep_screen_on))
                    Text(stringResource(R.string.keep_screen_on_description), color = InstrumentMuted, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = settings.keepScreenOn, onCheckedChange = onKeepScreenOnChanged)
            }
        }
        HardwareSettingsCard(live.deviceCapabilities)
        InstrumentCard {
            Text(stringResource(R.string.compass_settings_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.compass_accuracy_value, compassAccuracyText(live.compass.accuracy)),
                color = InstrumentMuted,
            )
            Text(
                stringResource(
                    if (live.compass.northReference == com.apophuy.altimeter.model.NorthReference.TRUE) {
                        R.string.true_north_description
                    } else {
                        R.string.magnetic_north_description
                    },
                ),
                color = InstrumentMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            live.compass.magneticFieldMicroTesla?.let {
                Text(stringResource(R.string.magnetic_field_value, it), color = InstrumentMuted)
            }
            OutlinedButton(
                onClick = { compassCalibrationDialog = true },
                enabled = live.compass.sensorAvailable,
            ) {
                Text(stringResource(R.string.calibrate_compass))
            }
        }
        InstrumentCard {
            Text(stringResource(R.string.units), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            UnitValueRow(
                label = stringResource(R.string.distance_unit),
                value = distanceUnitText(settings.distanceUnit),
                onClick = { unitSetting = UnitSetting.DISTANCE },
            )
            UnitValueRow(
                label = stringResource(R.string.pressure_unit),
                value = pressureUnitText(settings.pressureUnit),
                onClick = { unitSetting = UnitSetting.PRESSURE },
            )
            UnitValueRow(
                label = stringResource(R.string.temperature_unit),
                value = temperatureUnitText(settings.temperatureUnit),
                onClick = { unitSetting = UnitSetting.TEMPERATURE },
            )
            UnitValueRow(
                label = stringResource(R.string.wind_speed_unit),
                value = windSpeedUnitText(settings.windSpeedUnit),
                onClick = { unitSetting = UnitSetting.WIND },
            )
            UnitValueRow(
                label = stringResource(R.string.coordinate_format),
                value = coordinateFormatText(settings.coordinateFormat),
                onClick = { unitSetting = UnitSetting.COORDINATES },
            )
        }
        InstrumentCard {
            Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            UnitValueRow(
                label = stringResource(R.string.app_language),
                value = appLanguageName(settings.language),
                onClick = { languageDialog = true },
            )
        }
        AltitudeAlertsCard(
            alerts = altitudeAlerts,
            distanceUnit = settings.distanceUnit,
            hasNotificationPermission = hasNotificationPermission,
            onAdd = onAddAltitudeAlert,
            onReplace = onReplaceAltitudeAlert,
            onEnabledChanged = onAltitudeAlertEnabledChanged,
            onDelete = onDeleteAltitudeAlert,
        )
        InstrumentCard {
            Text(stringResource(R.string.recording_settings), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.recording_settings_description), color = InstrumentMuted)
            UnitValueRow(
                label = stringResource(R.string.altitude_step),
                value = formatAltitude(settings.trackingAltitudeStepMeters, settings.distanceUnit),
                onClick = { recordingSetting = RecordingSetting.ALTITUDE_STEP },
            )
            UnitValueRow(
                label = stringResource(R.string.sample_interval),
                value = stringResource(R.string.seconds_value, settings.trackingSampleIntervalMillis / 1_000L),
                onClick = { recordingSetting = RecordingSetting.SAMPLE_INTERVAL },
            )
            UnitValueRow(
                label = stringResource(R.string.coordinate_max_age),
                value = stringResource(R.string.seconds_value, settings.trackingCoordinateMaxAgeMillis / 1_000L),
                onClick = { recordingSetting = RecordingSetting.COORDINATE_MAX_AGE },
            )
        }
        InstrumentCard {
            Text(stringResource(R.string.calibration), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.calibration_description), color = InstrumentMuted)
            if (abs(settings.calibrationOffsetMeters) > 0.0001) {
                Text(
                    stringResource(R.string.calibration_active, formatAltitude(settings.calibrationOffsetMeters, settings.distanceUnit)),
                    color = InstrumentAmber,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { calibrationDialog = true }, enabled = live.altitude != null) { Text(stringResource(R.string.calibrate)) }
                if (abs(settings.calibrationOffsetMeters) > 0.0001) {
                    OutlinedButton(onClick = onResetCalibration) { Text(stringResource(R.string.reset)) }
                }
            }
        }
        InstrumentCard {
            Text(
                stringResource(R.string.gnss_diagnostics),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(stringResource(R.string.gnss_logging_description), color = InstrumentMuted)
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.gnss_logging_enabled),
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = settings.gnssDiagnosticLoggingEnabled,
                    onCheckedChange = onGnssDiagnosticLoggingChanged,
                )
            }
            if (settings.gnssDiagnosticLoggingEnabled) {
                Text(stringResource(R.string.gnss_logging_instruction), color = InstrumentAmber)
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(onClick = onShareGnssDiagnosticLog, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.share_gnss_log))
                }
                TextButton(onClick = onClearGnssDiagnosticLog, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.delete_gnss_log), color = InstrumentRed)
                }
            }
        }
        InstrumentCard {
            Text(stringResource(R.string.about), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.about_text), color = InstrumentMuted)
            Text(
                stringResource(R.string.version_value, BuildConfig.VERSION_NAME),
                color = InstrumentMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            HorizontalDivider(color = InstrumentSurfaceHigh)
            Text(stringResource(R.string.attribution), color = InstrumentMuted, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))
    }
    if (calibrationDialog) {
        CalibrationDialog(
            distanceUnit = settings.distanceUnit,
            currentMeters = live.altitude?.metersMsl,
            terrainMeters = live.terrain?.meters,
            onDismiss = { calibrationDialog = false },
            onApplyMeters = {
                calibrationDialog = false
                onCalibrate(it)
            },
            onUseTerrain = {
                calibrationDialog = false
                onCalibrateToTerrain()
            },
        )
    }
    if (compassCalibrationDialog) {
        CompassCalibrationDialog(live.compass, onDismiss = { compassCalibrationDialog = false })
    }
    if (languageDialog) {
        UnitSelectionDialog(
            title = stringResource(R.string.language),
            choices = AppLanguage.entries.map { language ->
                UnitChoice(appLanguageName(language), language == settings.language) {
                    onLanguageChanged(language)
                }
            },
            onDismiss = { languageDialog = false },
            onSelected = { languageDialog = false },
        )
    }
    unitSetting?.let { selectedSetting ->
        val title: String
        val choices: List<UnitChoice>
        when (selectedSetting) {
            UnitSetting.DISTANCE -> {
                title = stringResource(R.string.distance_unit)
                choices = DistanceUnit.entries.map { unit ->
                    UnitChoice(distanceUnitText(unit), unit == settings.distanceUnit) {
                        onDistanceUnitChanged(unit)
                    }
                }
            }
            UnitSetting.PRESSURE -> {
                title = stringResource(R.string.pressure_unit)
                choices = PressureUnit.entries.map { unit ->
                    UnitChoice(pressureUnitText(unit), unit == settings.pressureUnit) {
                        onPressureUnitChanged(unit)
                    }
                }
            }
            UnitSetting.TEMPERATURE -> {
                title = stringResource(R.string.temperature_unit)
                choices = TemperatureUnit.entries.map { unit ->
                    UnitChoice(temperatureUnitText(unit), unit == settings.temperatureUnit) {
                        onTemperatureUnitChanged(unit)
                    }
                }
            }
            UnitSetting.WIND -> {
                title = stringResource(R.string.wind_speed_unit)
                choices = WindSpeedUnit.entries.map { unit ->
                    UnitChoice(windSpeedUnitText(unit), unit == settings.windSpeedUnit) {
                        onWindSpeedUnitChanged(unit)
                    }
                }
            }
            UnitSetting.COORDINATES -> {
                title = stringResource(R.string.coordinate_format)
                choices = CoordinateFormat.entries.map { format ->
                    UnitChoice(coordinateFormatText(format), format == settings.coordinateFormat) {
                        onCoordinateFormatChanged(format)
                    }
                }
            }
        }
        UnitSelectionDialog(
            title = title,
            choices = choices,
            onDismiss = { unitSetting = null },
            onSelected = { unitSetting = null },
        )
    }
    recordingSetting?.let { selectedSetting ->
        when (selectedSetting) {
            RecordingSetting.ALTITUDE_STEP -> PositiveNumberSettingDialog(
                title = stringResource(R.string.altitude_step),
                initialText = distanceValueFromMeters(
                    settings.trackingAltitudeStepMeters,
                    settings.distanceUnit,
                ).toString(),
                suffix = distanceUnitSymbol(settings.distanceUnit),
                wholeNumber = false,
                onDismiss = { recordingSetting = null },
                onApply = { value ->
                    onTrackingAltitudeStepChanged(distanceValueToMeters(value, settings.distanceUnit))
                    recordingSetting = null
                },
            )
            RecordingSetting.SAMPLE_INTERVAL -> PositiveNumberSettingDialog(
                title = stringResource(R.string.sample_interval),
                initialText = (settings.trackingSampleIntervalMillis / 1_000L).toString(),
                suffix = stringResource(R.string.seconds_short),
                wholeNumber = true,
                onDismiss = { recordingSetting = null },
                onApply = { value ->
                    onTrackingSampleIntervalChanged(value.toLong() * 1_000L)
                    recordingSetting = null
                },
            )
            RecordingSetting.COORDINATE_MAX_AGE -> PositiveNumberSettingDialog(
                title = stringResource(R.string.coordinate_max_age),
                initialText = (settings.trackingCoordinateMaxAgeMillis / 1_000L).toString(),
                suffix = stringResource(R.string.seconds_short),
                wholeNumber = true,
                onDismiss = { recordingSetting = null },
                onApply = { value ->
                    onTrackingCoordinateMaxAgeChanged(value.toLong() * 1_000L)
                    recordingSetting = null
                },
            )
        }
    }
}

@Composable
private fun PositiveNumberSettingDialog(
    title: String,
    initialText: String,
    suffix: String,
    wholeNumber: Boolean,
    onDismiss: () -> Unit,
    onApply: (Double) -> Unit,
) {
    var text by remember { mutableStateOf(initialText) }
    val parsed = text.replace(',', '.').toDoubleOrNull()
    val valid = parsed != null && parsed.isFinite() && parsed > 0.0 &&
        (!wholeNumber || parsed % 1.0 == 0.0)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                suffix = { Text(suffix) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (wholeNumber) KeyboardType.Number else KeyboardType.Decimal,
                ),
                isError = text.isNotBlank() && !valid,
                supportingText = if (text.isNotBlank() && !valid) {
                    { Text(stringResource(R.string.invalid_positive_number)) }
                } else null,
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onApply(parsed!!) }) {
                Text(stringResource(R.string.apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun CalibrationDialog(
    distanceUnit: DistanceUnit,
    currentMeters: Double?,
    terrainMeters: Double?,
    onDismiss: () -> Unit,
    onApplyMeters: (Double) -> Unit,
    onUseTerrain: () -> Unit,
) {
    val initial = currentMeters?.let { distanceValueFromMeters(it, distanceUnit) }
    var text by remember { mutableStateOf(initial?.let { "%.1f".format(it) } ?: "") }
    val parsed = text.replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.calibration_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.calibration_input_label)) },
                    suffix = { Text(distanceUnitSymbol(distanceUnit)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = text.isNotBlank() && parsed == null,
                    supportingText = if (text.isNotBlank() && parsed == null) {
                        { Text(stringResource(R.string.invalid_number)) }
                    } else null,
                    singleLine = true,
                )
                terrainMeters?.let {
                    OutlinedButton(onClick = onUseTerrain, Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.use_terrain, formatAltitude(it, distanceUnit)))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsed != null,
                onClick = {
                    onApplyMeters(distanceValueToMeters(parsed!!, distanceUnit))
                },
            ) { Text(stringResource(R.string.apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun UnitValueRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, Modifier.weight(0.9f))
        Text(
            "$value  ›",
            modifier = Modifier.weight(1.5f),
            color = InstrumentTeal,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun UnitSelectionDialog(
    title: String,
    choices: List<UnitChoice>,
    onDismiss: () -> Unit,
    onSelected: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                choices.forEach { choice ->
                    SelectableRow(choice.label, choice.selected) {
                        choice.onClick()
                        onSelected()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun SelectableRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}

@Composable
private fun distanceUnitText(unit: DistanceUnit): String = when (unit) {
    DistanceUnit.METERS -> stringResource(R.string.unit_meters)
    DistanceUnit.FEET -> stringResource(R.string.unit_feet)
    DistanceUnit.KILOMETERS -> stringResource(R.string.unit_kilometers)
    DistanceUnit.MILES -> stringResource(R.string.unit_miles)
}

@Composable
private fun pressureUnitText(unit: PressureUnit): String = when (unit) {
    PressureUnit.MILLIMETERS_MERCURY -> stringResource(R.string.unit_mmhg)
    PressureUnit.HECTOPASCALS -> stringResource(R.string.unit_hpa)
    PressureUnit.MILLIBARS -> stringResource(R.string.unit_mbar)
    PressureUnit.KILOPASCALS -> stringResource(R.string.unit_kpa)
    PressureUnit.BAR -> stringResource(R.string.unit_bar)
    PressureUnit.ATMOSPHERES -> stringResource(R.string.unit_atmospheres)
    PressureUnit.INCHES_MERCURY -> stringResource(R.string.unit_inhg)
}

@Composable
private fun temperatureUnitText(unit: TemperatureUnit): String = when (unit) {
    TemperatureUnit.CELSIUS -> stringResource(R.string.unit_celsius)
    TemperatureUnit.FAHRENHEIT -> stringResource(R.string.unit_fahrenheit)
    TemperatureUnit.KELVIN -> stringResource(R.string.unit_kelvin)
}

@Composable
private fun windSpeedUnitText(unit: WindSpeedUnit): String = when (unit) {
    WindSpeedUnit.METERS_PER_SECOND -> stringResource(R.string.unit_meters_per_second)
    WindSpeedUnit.KILOMETERS_PER_HOUR -> stringResource(R.string.unit_kilometers_per_hour)
    WindSpeedUnit.MILES_PER_HOUR -> stringResource(R.string.unit_miles_per_hour)
    WindSpeedUnit.KNOTS -> stringResource(R.string.unit_knots)
}

@Composable
private fun coordinateFormatText(format: CoordinateFormat): String = when (format) {
    CoordinateFormat.DECIMAL_DEGREES -> stringResource(R.string.coordinate_format_decimal)
    CoordinateFormat.DMS -> stringResource(R.string.coordinate_format_dms)
    CoordinateFormat.UTM -> stringResource(R.string.coordinate_format_utm)
    CoordinateFormat.MGRS -> stringResource(R.string.coordinate_format_mgrs)
}

@Composable
private fun appLanguageName(language: AppLanguage): String = stringResource(
    when (language) {
        AppLanguage.SYSTEM -> R.string.system_language
        AppLanguage.ENGLISH -> R.string.language_english
        AppLanguage.RUSSIAN -> R.string.language_russian
        AppLanguage.CHINESE_SIMPLIFIED -> R.string.language_chinese_simplified
        AppLanguage.HINDI -> R.string.language_hindi
        AppLanguage.SPANISH -> R.string.language_spanish
        AppLanguage.ARABIC -> R.string.language_arabic
        AppLanguage.FRENCH -> R.string.language_french
        AppLanguage.BENGALI -> R.string.language_bengali
        AppLanguage.PORTUGUESE_BRAZIL -> R.string.language_portuguese_brazil
        AppLanguage.INDONESIAN -> R.string.language_indonesian
        AppLanguage.URDU -> R.string.language_urdu
        AppLanguage.GERMAN -> R.string.language_german
    },
)

@Composable
internal fun InstrumentCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = InstrumentSurface),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
internal fun altitudeSourceText(source: AltitudeSource): String = when (source) {
    AltitudeSource.FUSED -> stringResource(R.string.source_fused)
    AltitudeSource.GNSS -> stringResource(R.string.source_gnss)
    AltitudeSource.BAROMETER -> stringResource(R.string.source_barometer)
    AltitudeSource.MANUAL -> stringResource(R.string.source_manual)
}

@Composable
internal fun weatherKindText(kind: WeatherKind?): String = when (kind) {
    WeatherKind.CLEAR -> stringResource(R.string.weather_clear)
    WeatherKind.PARTLY_CLOUDY -> stringResource(R.string.weather_partly_cloudy)
    WeatherKind.CLOUDY -> stringResource(R.string.weather_cloudy)
    WeatherKind.FOG -> stringResource(R.string.weather_fog)
    WeatherKind.DRIZZLE -> stringResource(R.string.weather_drizzle)
    WeatherKind.RAIN -> stringResource(R.string.weather_rain)
    WeatherKind.SNOW -> stringResource(R.string.weather_snow)
    WeatherKind.SHOWERS -> stringResource(R.string.weather_showers)
    WeatherKind.THUNDERSTORM -> stringResource(R.string.weather_thunderstorm)
    WeatherKind.UNKNOWN, null -> stringResource(R.string.weather_unknown)
}
