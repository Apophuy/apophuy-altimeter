package com.apophuy.altimeter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apophuy.altimeter.R
import com.apophuy.altimeter.model.DistanceUnit
import com.apophuy.altimeter.model.CompassAccuracy
import com.apophuy.altimeter.model.GnssFixState
import com.apophuy.altimeter.model.GnssSignalWarning
import com.apophuy.altimeter.model.LiveReading
import com.apophuy.altimeter.model.NorthReference
import com.apophuy.altimeter.data.local.WaypointEntity
import com.apophuy.altimeter.ui.theme.InstrumentAmber
import com.apophuy.altimeter.ui.theme.InstrumentGreen
import com.apophuy.altimeter.ui.theme.InstrumentMuted
import com.apophuy.altimeter.ui.theme.InstrumentRed
import com.apophuy.altimeter.ui.theme.InstrumentSurface
import com.apophuy.altimeter.ui.theme.InstrumentTeal
import com.apophuy.altimeter.util.distanceMeters
import com.apophuy.altimeter.util.initialBearingDegrees
import com.apophuy.altimeter.util.isTimestampFresh
import com.apophuy.altimeter.util.resolveTargetDirection
import com.apophuy.altimeter.util.TargetDirectionReference
import com.apophuy.altimeter.util.TargetNavigationFilter
import kotlin.math.hypot

@Composable
internal fun DashboardScreen(
    state: AppUiState,
    hasLocationPermission: Boolean,
    onRequestLocationPermission: () -> Unit,
    onCoordinatesCopied: (String) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().testTag("dashboard")) {
        val landscape = maxWidth > maxHeight
        val compact = maxHeight < 540.dp
        if (landscape) {
            Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LocationBlock(state, onCoordinatesCopied, hasLocationPermission, onRequestLocationPermission)
                    CompassBlock(state.live, state.settings.distanceUnit, selectedWaypoint(state), Modifier.weight(1f))
                }
                Column(Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AltitudeCard(state.live, state.settings.distanceUnit, compact = true)
                    GnssCard(state.live, state.settings.distanceUnit)
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LocationBlock(state, onCoordinatesCopied, hasLocationPermission, onRequestLocationPermission)
                CompassBlock(state.live, state.settings.distanceUnit, selectedWaypoint(state), Modifier.weight(1f))
                AltitudeCard(state.live, state.settings.distanceUnit, compact = compact)
                GnssCard(state.live, state.settings.distanceUnit)
            }
        }
    }
}

@Composable
private fun LocationBlock(
    state: AppUiState,
    onCoordinatesCopied: (String) -> Unit,
    hasLocationPermission: Boolean,
    onRequestLocationPermission: () -> Unit,
) {
    val live = state.live
    val clipboard = LocalClipboardManager.current
    val copiedText = stringResource(R.string.coordinates_copied)
    val copyLabel = stringResource(R.string.copy_coordinates)
    Column(
        Modifier.fillMaxWidth().testTag("location").clickable(
            enabled = live.coordinates != null,
            onClickLabel = copyLabel,
        ) {
            live.coordinates?.let {
                clipboard.setText(
                    AnnotatedString(
                        formatCoordinates(it.latitude, it.longitude, state.settings.coordinateFormat),
                    ),
                )
                onCoordinatesCopied(copiedText)
            }
        },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                live.placeName ?: stringResource(if (live.coordinates == null) R.string.location_unavailable else R.string.place_unavailable),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (!hasLocationPermission) {
                TextButton(onClick = onRequestLocationPermission) { Text(stringResource(R.string.grant_permission_short)) }
            }
        }
        Text(
            live.coordinates?.let {
                formatCoordinates(it.latitude, it.longitude, state.settings.coordinateFormat)
            } ?: "—",
            color = InstrumentMuted,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            modifier = Modifier.testTag("coordinates"),
        )
    }
}

@Composable
private fun CompassBlock(
    live: LiveReading,
    distanceUnit: DistanceUnit,
    target: WaypointEntity?,
    modifier: Modifier,
) {
    var calibrationDialog by remember { mutableStateOf(false) }
    val coordinates = live.coordinates
    val nowMillis = System.currentTimeMillis()
    val positionFresh = coordinates != null && isTimestampFresh(
        coordinates.timestampMillis,
        nowMillis,
        TARGET_LOCATION_MAX_AGE_MILLIS,
    )
    val navigationFilter = remember(target?.id) { TargetNavigationFilter() }
    val targetNavigation = remember(target?.id, coordinates?.timestampMillis, positionFresh) {
        if (target != null && coordinates != null && positionFresh) {
            navigationFilter.add(
                bearingDegrees = initialBearingDegrees(
                    coordinates.latitude,
                    coordinates.longitude,
                    target.latitude,
                    target.longitude,
                ).toFloat(),
                distanceMeters = distanceMeters(
                    coordinates.latitude,
                    coordinates.longitude,
                    target.latitude,
                    target.longitude,
                ),
            )
        } else null
    }
    val fieldInterference = live.compass.magneticFieldMicroTesla?.let { it !in 20f..70f } == true
    val compassReliable = live.compass.headingDegrees != null &&
        (live.compass.accuracy == CompassAccuracy.MEDIUM || live.compass.accuracy == CompassAccuracy.HIGH) &&
        !fieldInterference
    val movementCourse = live.movementCourse?.takeIf {
        isTimestampFresh(it.timestampMillis, nowMillis, TARGET_COURSE_MAX_AGE_MILLIS)
    }
    val combinedPositionAccuracy = if (coordinates != null && target != null) {
        val current = coordinates.horizontalAccuracyMeters?.toDouble()
        val saved = target.horizontalAccuracyMeters?.toDouble()
        when {
            current != null && saved != null -> hypot(current, saved)
            current != null -> current
            else -> saved
        }
    } else null
    val positionDirectionReliable = targetNavigation?.distanceMeters?.let { distance ->
        distance > maxOf(
            MIN_TARGET_DIRECTION_DISTANCE_METERS,
            (combinedPositionAccuracy ?: 0.0) * TARGET_ACCURACY_DISTANCE_MULTIPLIER,
        )
    } == true
    val targetDirection = resolveTargetDirection(
        targetBearingDegrees = targetNavigation?.bearingDegrees,
        positionDirectionReliable = positionDirectionReliable,
        compassHeadingDegrees = live.compass.headingDegrees,
        compassReliable = compassReliable,
        movementCourseDegrees = movementCourse?.bearingDegrees,
    )
    val cardinalLabels = listOf(
        stringResource(R.string.compass_north_short),
        stringResource(R.string.compass_east_short),
        stringResource(R.string.compass_south_short),
        stringResource(R.string.compass_west_short),
    )
    val directionLabels = listOf(
        stringResource(R.string.direction_north),
        stringResource(R.string.direction_northeast),
        stringResource(R.string.direction_east),
        stringResource(R.string.direction_southeast),
        stringResource(R.string.direction_south),
        stringResource(R.string.direction_southwest),
        stringResource(R.string.direction_west),
        stringResource(R.string.direction_northwest),
    )
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val diameter = minOf(maxWidth, maxHeight, 330.dp)
            CompassDial(
                headingDegrees = live.compass.headingDegrees,
                northReferenceLabel = stringResource(
                    if (live.compass.northReference == NorthReference.TRUE) {
                        R.string.true_north
                    } else {
                        R.string.magnetic_north
                    },
                ),
                unavailableLabel = stringResource(
                    if (live.compass.sensorAvailable) R.string.compass_waiting else R.string.not_available,
                ),
                cardinalLabels = cardinalLabels,
                directionLabels = directionLabels,
                targetDirectionDegrees = targetDirection.degreesRelativeToReference,
                modifier = Modifier.size(diameter).testTag("compass"),
            )
        }
        val compassNeedsCalibration = live.compass.sensorAvailable &&
            live.compass.accuracy != CompassAccuracy.MEDIUM &&
            live.compass.accuracy != CompassAccuracy.HIGH
        if (compassNeedsCalibration || fieldInterference) {
            TextButton(onClick = { calibrationDialog = true }) {
                Text(
                    stringResource(R.string.compass_unreliable),
                    color = InstrumentAmber,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        target?.let {
            val navigationText = if (!positionFresh) {
                stringResource(R.string.target_waiting_for_position, it.name)
            } else {
                val savedAltitude = formatAltitude(it.altitudeMetersMsl, distanceUnit, 0)
                val difference = it.altitudeMetersMsl?.let { saved ->
                    live.altitude?.metersMsl?.let { current -> saved - current }
                }
                stringResource(
                    R.string.target_navigation_summary,
                    it.name,
                    targetNavigation?.bearingDegrees?.toInt() ?: 0,
                    formatAltitude(targetNavigation?.distanceMeters, distanceUnit, 0),
                    savedAltitude,
                    formatAltitude(difference, distanceUnit, 0),
                )
            }
            Text(
                navigationText,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = InstrumentAmber,
                maxLines = 2,
            )
            val directionStatus = when (targetDirection.reference) {
                TargetDirectionReference.GPS_COURSE -> R.string.target_direction_gps_course
                TargetDirectionReference.POSITION_UNCERTAIN -> R.string.target_direction_position_uncertain
                TargetDirectionReference.NONE -> if (positionFresh) R.string.target_direction_unavailable else null
                TargetDirectionReference.COMPASS -> null
            }
            directionStatus?.let { statusRes ->
                Text(
                    stringResource(statusRes),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = InstrumentMuted,
                )
            }
        }
    }
    if (calibrationDialog) {
        CompassCalibrationDialog(live.compass, onDismiss = { calibrationDialog = false })
    }
}

private fun selectedWaypoint(state: AppUiState): WaypointEntity? =
    state.settings.activeWaypointId?.let { id -> state.waypoints.firstOrNull { it.id == id } }

private const val TARGET_LOCATION_MAX_AGE_MILLIS = 10_000L
private const val TARGET_COURSE_MAX_AGE_MILLIS = 10_000L
private const val MIN_TARGET_DIRECTION_DISTANCE_METERS = 15.0
private const val TARGET_ACCURACY_DISTANCE_MULTIPLIER = 2.0

@Composable
private fun AltitudeCard(live: LiveReading, distanceUnit: DistanceUnit, compact: Boolean = false) {
    DashboardCard(Modifier.testTag("altitude")) {
        Text(stringResource(R.string.altitude), color = InstrumentTeal, style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            androidx.compose.foundation.text.BasicText(
                formatAltitude(live.altitude?.metersMsl, distanceUnit),
                style = MaterialTheme.typography.headlineLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
                modifier = Modifier.weight(1f),
                autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = if (compact) 32.sp else 42.sp),
            )
            live.altitude?.accuracyMeters?.let {
                Text(stringResource(R.string.accuracy_value, formatAltitude(it.toDouble(), distanceUnit, 0)),
                    style = MaterialTheme.typography.bodySmall, color = InstrumentMuted)
            }
        }
        Text(
            live.altitude?.let { stringResource(R.string.altitude_msl_source, altitudeSourceText(it.source)) }
                ?: stringResource(R.string.waiting_for_altitude),
            color = InstrumentMuted, style = MaterialTheme.typography.bodySmall,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.terrain_short), style = MaterialTheme.typography.bodySmall, color = InstrumentMuted)
            Text(
                formatAltitude(live.terrain?.meters, distanceUnit, 0) +
                    if (live.terrain?.stale == true) " · " + stringResource(R.string.data_stale) else "",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun GnssCard(live: LiveReading, distanceUnit: DistanceUnit) {
    val connected = live.gnss.state == GnssFixState.FIXED
    val indicator = stringResource(if (connected) R.string.gnss_indicator_connected else R.string.gnss_indicator_disconnected)
    val indicatorColor = when (live.gnss.signalWarning) {
        GnssSignalWarning.UNSTABLE -> InstrumentAmber
        GnssSignalWarning.UNAVAILABLE -> InstrumentRed
        null -> if (connected) InstrumentGreen else InstrumentRed
    }
    val status = stringResource(when {
        live.deviceCapabilities?.gps == false -> R.string.hardware_gps_missing
        else -> when (live.gnss.state) {
            GnssFixState.DISABLED -> R.string.gnss_disabled
            GnssFixState.SEARCHING -> R.string.gnss_searching
            GnssFixState.FIXED -> R.string.gnss_fixed
            GnssFixState.NO_PERMISSION -> R.string.gnss_no_permission
        }
    })
    DashboardCard(Modifier.testTag("satellites")) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(10.dp).background(indicatorColor, CircleShape)
                .semantics { contentDescription = indicator })
            Text(status, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                live.gnss.usedSatellites?.let { stringResource(R.string.satellites_value, it, live.gnss.visibleSatellites) }
                    ?: if (live.gnss.visibleSatellites > 0) stringResource(R.string.satellites_unknown_used, live.gnss.visibleSatellites)
                    else stringResource(R.string.satellites_count_unavailable),
                style = MaterialTheme.typography.bodySmall, color = InstrumentMuted, modifier = Modifier.weight(1f),
            )
            live.gnss.horizontalAccuracyMeters?.let {
                Text(stringResource(R.string.accuracy_value, formatAltitude(it.toDouble(), distanceUnit, 0)),
                    style = MaterialTheme.typography.bodySmall, color = InstrumentMuted)
            }
        }
        live.gnss.signalWarning?.let { warning ->
            Text(
                stringResource(
                    when (warning) {
                        GnssSignalWarning.UNSTABLE -> R.string.gnss_warning_unstable
                        GnssSignalWarning.UNAVAILABLE -> R.string.gnss_warning_unavailable
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = indicatorColor,
                modifier = Modifier.testTag("gnss-warning"),
            )
        }
    }
}

@Composable
private fun DashboardCard(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = InstrumentSurface)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
    }
}
