package com.apophuy.altimeter.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.IBinder
import android.os.PowerManager
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.annotation.StringRes
import com.apophuy.altimeter.AltimeterApplication
import com.apophuy.altimeter.MainActivity
import com.apophuy.altimeter.R
import com.apophuy.altimeter.data.local.TrackAlertEventEntity
import com.apophuy.altimeter.model.AlertDirection
import com.apophuy.altimeter.model.DistanceUnit
import com.apophuy.altimeter.model.LiveReading
import com.apophuy.altimeter.ui.distanceUnitSymbol
import com.apophuy.altimeter.ui.distanceValueFromMeters
import com.apophuy.altimeter.util.currentAppLocale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class TrackingService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val container by lazy { (application as AltimeterApplication).container }
    private var collectionJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastNotificationUpdate = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            startInForeground(container.liveRepository.reading.value)
            serviceScope.launch { stopTracking() }
            return START_NOT_STICKY
        }

        startInForeground(container.liveRepository.reading.value)
        container.liveRepository.acquire(SERVICE_OWNER)
        acquireWakeLock()
        if (collectionJob == null) {
            collectionJob = serviceScope.launch {
                val recovered = container.trackRepository.recoverActive()
                val sessionId = recovered.sessionId
                    ?: container.trackRepository.start(container.liveRepository.reading.value)
                container.settingsRepository.settings
                    .map { it.trackingSampleIntervalMillis }
                    .distinctUntilChanged()
                    .flatMapLatest { interval -> container.liveRepository.reading.sample(interval) }
                    .collect { reading ->
                        container.trackRepository.append(reading)
                        reading.altitude?.let { altitude ->
                            val settings = container.settingsRepository.settings.value
                            container.altitudeAlertRepository.evaluate(
                                sessionId = sessionId,
                                altitudeMetersMsl = altitude.metersMsl,
                                currentCalibrationOffsetMeters = settings.calibrationOffsetMeters,
                                timestampMillis = altitude.timestampMillis,
                            ).forEach { event -> showAltitudeAlert(event) }
                        }
                        val now = System.currentTimeMillis()
                        if (now - lastNotificationUpdate >= 10_000L) {
                            updateNotification(reading)
                            lastNotificationUpdate = now
                        }
                    }
            }
        }
        return START_STICKY
    }

    private suspend fun stopTracking() {
        collectionJob?.cancel()
        collectionJob = null
        container.trackRepository.stop(container.liveRepository.reading.value)
        container.altitudeAlertRepository.endSession()
        releaseResources()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        releaseResources()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startInForeground(reading: LiveReading) {
        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else 0
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(reading),
            foregroundType,
        )
    }

    private fun updateNotification(reading: LiveReading) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        runCatching {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(reading))
        }
    }

    private fun buildNotification(reading: LiveReading) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_altimeter)
        .setContentTitle(appString(R.string.tracking_notification_title))
        .setContentText(notificationText(reading))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .addAction(
            R.drawable.ic_stat_altimeter,
            appString(R.string.stop_recording),
            PendingIntent.getService(
                this,
                1,
                Intent(this, TrackingService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .build()

    private fun notificationText(reading: LiveReading): String {
        val altitude = reading.altitude?.metersMsl
        return if (altitude == null) {
            appString(R.string.waiting_for_altitude)
        } else {
            val distanceUnit = container.settingsRepository.settings.value.distanceUnit
            val value = distanceValueFromMeters(altitude, distanceUnit)
            val unit = distanceUnitSymbol(distanceUnit)
            val decimals = when (distanceUnit) {
                DistanceUnit.KILOMETERS, DistanceUnit.MILES -> 3
                DistanceUnit.METERS, DistanceUnit.FEET -> 1
            }
            appString(
                R.string.tracking_notification_value,
                String.format(currentAppLocale(), "%.${decimals}f", value),
                unit,
            )
        }
    }

    private suspend fun showAltitudeAlert(event: TrackAlertEventEntity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val alert = container.altitudeAlertRepository.get(event.alertId) ?: return
        val distanceUnit = container.settingsRepository.settings.value.distanceUnit
        val value = distanceValueFromMeters(alert.altitudeMetersMsl, distanceUnit)
        val decimals = when (distanceUnit) {
            DistanceUnit.KILOMETERS, DistanceUnit.MILES -> 3
            DistanceUnit.METERS, DistanceUnit.FEET -> 1
        }
        val threshold = String.format(
            currentAppLocale(),
            "%.${decimals}f %s",
            value,
            distanceUnitSymbol(distanceUnit),
        )
        val direction = AlertDirection.valueOf(event.direction)
        val title = appString(
            if (direction == AlertDirection.ASCENDING) {
                R.string.altitude_alert_ascending_title
            } else {
                R.string.altitude_alert_descending_title
            },
        )
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_altimeter)
            .setContentTitle(title)
            .setContentText(appString(R.string.altitude_alert_notification_text, threshold))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
        runCatching {
            NotificationManagerCompat.from(this).notify(alertNotificationId(event), notification)
        }
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            appString(R.string.tracking_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = appString(R.string.tracking_channel_description)
            setShowBadge(false)
        }
        val alertChannel = NotificationChannel(
            ALERT_CHANNEL_ID,
            appString(R.string.altitude_alert_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = appString(R.string.altitude_alert_channel_description)
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
        manager.createNotificationChannel(alertChannel)
    }

    private fun alertNotificationId(event: TrackAlertEventEntity): Int =
        ALERT_NOTIFICATION_ID_BASE +
            (31 * event.alertId + event.direction.hashCode()).hashCode().and(0x0fffffff)

    private fun appString(@StringRes id: Int, vararg arguments: Any): String {
        val configuration = Configuration(resources.configuration).apply {
            setLocale(currentAppLocale())
        }
        return createConfigurationContext(configuration).resources.getString(id, *arguments)
    }

    @Suppress("WakelockTimeout")
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:altitude-recording")
            .apply {
                setReferenceCounted(false)
                acquire()
            }
    }

    private fun releaseResources() {
        container.liveRepository.release(SERVICE_OWNER)
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    companion object {
        private const val CHANNEL_ID = "altitude_tracking"
        private const val ALERT_CHANNEL_ID = "altitude_alerts"
        private const val NOTIFICATION_ID = 1042
        private const val ALERT_NOTIFICATION_ID_BASE = 20_000
        private const val SERVICE_OWNER = "tracking-service"
        const val ACTION_START = "com.apophuy.altimeter.action.START_TRACKING"
        const val ACTION_STOP = "com.apophuy.altimeter.action.STOP_TRACKING"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, TrackingService::class.java).setAction(ACTION_START),
            )
        }

        fun stop(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, TrackingService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
