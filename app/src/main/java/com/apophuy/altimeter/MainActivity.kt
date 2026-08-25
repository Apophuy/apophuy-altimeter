package com.apophuy.altimeter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.apophuy.altimeter.model.AppLanguage
import com.apophuy.altimeter.data.local.WaypointEntity
import com.apophuy.altimeter.ui.formatCoordinates
import com.apophuy.altimeter.util.appLanguageFromTag
import com.apophuy.altimeter.util.toLanguageTags
import com.apophuy.altimeter.ui.AltimeterApp
import com.apophuy.altimeter.ui.MainViewModel
import com.apophuy.altimeter.ui.theme.AltimeterTheme
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()
    private var hasFineLocation by mutableStateOf(false)
    private var hasNotificationPermission by mutableStateOf(false)
    private var startAfterPermission = false

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        hasFineLocation = result[Manifest.permission.ACCESS_FINE_LOCATION] == true || checkFineLocation()
        viewModel.onPermissionChanged()
        if (startAfterPermission && hasFineLocation) requestNotificationThenStart()
        startAfterPermission = false
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasNotificationPermission = granted || checkNotificationPermission()
        viewModel.startTracking()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hasFineLocation = checkFineLocation()
        hasNotificationPermission = checkNotificationPermission()

        AppCompatDelegate.getApplicationLocales().get(0)?.let { locale ->
            appLanguageFromTag(locale.toLanguageTag())?.let(viewModel::setLanguage)
        }

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val selectedSession by viewModel.selectedSession.collectAsStateWithLifecycle()
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_START -> viewModel.acquireLiveData()
                        Lifecycle.Event.ON_STOP -> viewModel.releaseLiveData()
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            AltimeterTheme(themeMode = state.settings.themeMode) {
                AltimeterApp(
                    state = state,
                    selectedSession = selectedSession,
                    hasLocationPermission = hasFineLocation,
                    hasNotificationPermission = hasNotificationPermission,
                    onRequestLocationPermission = { requestLocationPermission(forTracking = false) },
                    onStartTracking = {
                        if (!hasFineLocation) requestLocationPermission(forTracking = true)
                        else requestNotificationThenStart()
                    },
                    onStopTracking = viewModel::stopTracking,
                    onSelectSession = viewModel::selectSession,
                    onDeleteSession = viewModel::deleteSession,
                    onShare = { id, format ->
                        lifecycleScope.launch {
                            viewModel.createShareIntent(id, format)?.let { share ->
                                startActivity(Intent.createChooser(share, getString(R.string.app_name)))
                            }
                        }
                    },
                    onDistanceUnitChanged = viewModel::setDistanceUnit,
                    onPressureUnitChanged = viewModel::setPressureUnit,
                    onTemperatureUnitChanged = viewModel::setTemperatureUnit,
                    onWindSpeedUnitChanged = viewModel::setWindSpeedUnit,
                    onCoordinateFormatChanged = viewModel::setCoordinateFormat,
                    onLanguageChanged = ::changeLanguage,
                    onThemeModeChanged = viewModel::setThemeMode,
                    onKeepScreenOnChanged = viewModel::setKeepScreenOn,
                    onTrackingAltitudeStepChanged = viewModel::setTrackingAltitudeStepMeters,
                    onTrackingSampleIntervalChanged = viewModel::setTrackingSampleIntervalMillis,
                    onTrackingCoordinateMaxAgeChanged = viewModel::setTrackingCoordinateMaxAgeMillis,
                    onGnssDiagnosticLoggingChanged = viewModel::setGnssDiagnosticLoggingEnabled,
                    onShareGnssDiagnosticLog = {
                        lifecycleScope.launch {
                            val share = viewModel.createGnssDiagnosticShareIntent()
                            if (share != null) {
                                startActivity(Intent.createChooser(share, getString(R.string.share_gnss_log)))
                            } else {
                                Toast.makeText(
                                    this@MainActivity,
                                    R.string.gnss_log_not_ready,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    },
                    onClearGnssDiagnosticLog = {
                        viewModel.clearGnssDiagnosticLog()
                        Toast.makeText(this, R.string.gnss_log_deleted, Toast.LENGTH_SHORT).show()
                    },
                    onCalibrate = viewModel::calibrateTo,
                    onCalibrateToTerrain = viewModel::calibrateToTerrain,
                    onResetCalibration = viewModel::resetCalibration,
                    onHardwareNoticeAcknowledged = viewModel::acknowledgeHardwareNotice,
                    onSaveWaypoint = viewModel::saveWaypoint,
                    onRenameWaypoint = viewModel::renameWaypoint,
                    onDeleteWaypoint = viewModel::deleteWaypoint,
                    onSelectWaypoint = viewModel::selectWaypoint,
                    onShareWaypoint = ::shareWaypoint,
                    onAddAltitudeAlert = viewModel::addAltitudeAlert,
                    onReplaceAltitudeAlert = viewModel::replaceAltitudeAlert,
                    onAltitudeAlertEnabledChanged = viewModel::setAltitudeAlertEnabled,
                    onDeleteAltitudeAlert = viewModel::deleteAltitudeAlert,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val current = checkFineLocation()
        if (hasFineLocation != current) {
            hasFineLocation = current
            viewModel.onPermissionChanged()
        }
        hasNotificationPermission = checkNotificationPermission()
    }

    private fun requestLocationPermission(forTracking: Boolean) {
        startAfterPermission = forTracking
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
        )
    }

    private fun requestNotificationThenStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.startTracking()
        }
    }

    private fun changeLanguage(language: AppLanguage) {
        viewModel.setLanguage(language)
        val tags = language.toLanguageTags()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags))
    }

    private fun shareWaypoint(waypoint: WaypointEntity) {
        val format = viewModel.uiState.value.settings.coordinateFormat
        val coordinates = formatCoordinates(waypoint.latitude, waypoint.longitude, format)
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, waypoint.name)
            putExtra(Intent.EXTRA_TEXT, "${waypoint.name}\n$coordinates")
        }
        startActivity(Intent.createChooser(share, getString(R.string.share_waypoint)))
    }

    private fun checkNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun checkFineLocation(): Boolean = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
}
