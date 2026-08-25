package com.apophuy.altimeter.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.apophuy.altimeter.R
import com.apophuy.altimeter.data.local.WaypointEntity
import com.apophuy.altimeter.ui.theme.InstrumentMuted
import com.apophuy.altimeter.ui.theme.InstrumentRed
import com.apophuy.altimeter.ui.theme.InstrumentTeal
import com.apophuy.altimeter.util.WAYPOINT_MAX_HORIZONTAL_ACCURACY_METERS
import com.apophuy.altimeter.util.isWaypointPositionUsable
import kotlinx.coroutines.delay

@Composable
internal fun WaypointScreen(
    state: AppUiState,
    onSave: (String) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    onSelect: (Long?) -> Unit,
    onShare: (WaypointEntity) -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var editWaypoint by remember { mutableStateOf<WaypointEntity?>(null) }
    var deleteWaypoint by remember { mutableStateOf<WaypointEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    val coordinates = state.live.coordinates
    val positionUsable = isWaypointPositionUsable(coordinates, now)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.tab_waypoints),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(stringResource(R.string.waypoints_description), color = InstrumentMuted)
        Button(
            onClick = { adding = true },
            enabled = positionUsable,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.save_current_waypoint))
        }
        if (!positionUsable) {
            Text(
                stringResource(
                    R.string.waypoint_waiting_for_accurate_position,
                    formatAltitude(
                        WAYPOINT_MAX_HORIZONTAL_ACCURACY_METERS.toDouble(),
                        state.settings.distanceUnit,
                        0,
                    ),
                ),
                color = InstrumentMuted,
            )
        }

        if (state.waypoints.isEmpty()) {
            InstrumentCard {
                Text(stringResource(R.string.waypoints_empty_title), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.waypoints_empty_text), color = InstrumentMuted)
            }
        } else {
            state.waypoints.forEach { waypoint ->
                val selected = state.settings.activeWaypointId == waypoint.id
                InstrumentCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(waypoint.name, fontWeight = FontWeight.Bold)
                            Text(
                                formatCoordinates(
                                    waypoint.latitude,
                                    waypoint.longitude,
                                    state.settings.coordinateFormat,
                                ),
                                color = InstrumentMuted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(
                            checked = selected,
                            onCheckedChange = { active -> onSelect(if (active) waypoint.id else null) },
                        )
                    }
                    Text(
                        stringResource(
                            R.string.waypoint_saved_details,
                            formatAltitude(waypoint.altitudeMetersMsl, state.settings.distanceUnit, 0),
                            formatDateTime(waypoint.createdAtMillis),
                        ),
                        color = if (selected) InstrumentTeal else InstrumentMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        WaypointActionButton(
                            iconRes = R.drawable.ic_edit,
                            label = stringResource(R.string.rename),
                            onClick = { editWaypoint = waypoint },
                        )
                        WaypointActionButton(
                            iconRes = R.drawable.ic_share,
                            label = stringResource(R.string.share),
                            onClick = { onShare(waypoint) },
                        )
                        WaypointActionButton(
                            iconRes = R.drawable.ic_delete,
                            label = stringResource(R.string.delete),
                            tint = InstrumentRed,
                            onClick = { deleteWaypoint = waypoint },
                        )
                    }
                }
            }
        }
    }

    if (adding) {
        WaypointNameDialog(
            title = stringResource(R.string.new_waypoint_title),
            initialName = state.live.placeName ?: formatDateTime(now),
            onDismiss = { adding = false },
            onApply = {
                onSave(it)
                adding = false
            },
        )
    }
    editWaypoint?.let { waypoint ->
        WaypointNameDialog(
            title = stringResource(R.string.rename_waypoint_title),
            initialName = waypoint.name,
            onDismiss = { editWaypoint = null },
            onApply = {
                onRename(waypoint.id, it)
                editWaypoint = null
            },
        )
    }
    deleteWaypoint?.let { waypoint ->
        AlertDialog(
            onDismissRequest = { deleteWaypoint = null },
            title = { Text(stringResource(R.string.delete_waypoint_title)) },
            text = { Text(stringResource(R.string.delete_waypoint_text, waypoint.name)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(waypoint.id)
                    deleteWaypoint = null
                }) { Text(stringResource(R.string.delete), color = InstrumentRed) }
            },
            dismissButton = {
                TextButton(onClick = { deleteWaypoint = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WaypointActionButton(
    @DrawableRes iconRes: Int,
    label: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        OutlinedIconButton(onClick = onClick) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = label,
                tint = tint,
            )
        }
    }
}

@Composable
private fun WaypointNameDialog(
    title: String,
    initialName: String,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.waypoint_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onApply(name.trim()) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
