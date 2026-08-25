package com.apophuy.altimeter

import android.app.Application
import com.apophuy.altimeter.altitude.AltitudeEngine
import com.apophuy.altimeter.data.AltitudeAlertRepository
import com.apophuy.altimeter.data.SettingsRepository
import com.apophuy.altimeter.data.TrackRepository
import com.apophuy.altimeter.data.WaypointRepository
import com.apophuy.altimeter.data.local.AltimeterDatabase
import com.apophuy.altimeter.location.LiveRepository
import com.apophuy.altimeter.location.GnssDiagnosticLogger
import com.apophuy.altimeter.weather.OpenMeteoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AltimeterApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(application: Application) {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database = AltimeterDatabase.get(application)
    val settingsRepository = SettingsRepository(application, applicationScope)
    val gnssDiagnosticLogger = GnssDiagnosticLogger(
        context = application,
        settings = settingsRepository.settings,
        scope = applicationScope,
    )
    val openMeteoRepository = OpenMeteoRepository(database.altimeterDao(), applicationScope)
    val altitudeEngine = AltitudeEngine(application, settingsRepository, applicationScope)
    val liveRepository = LiveRepository(
        application,
        altitudeEngine,
        openMeteoRepository,
        applicationScope,
        gnssDiagnosticLogger,
    )
    val trackRepository = TrackRepository(database.altimeterDao(), settingsRepository)
    val waypointRepository = WaypointRepository(database.altimeterDao())
    val altitudeAlertRepository = AltitudeAlertRepository(database.altimeterDao())
}
