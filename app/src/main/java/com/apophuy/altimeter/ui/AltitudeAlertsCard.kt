package com.apophuy.altimeter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.apophuy.altimeter.R
import com.apophuy.altimeter.data.local.AltitudeAlertEntity
import com.apophuy.altimeter.model.DistanceUnit
import com.apophuy.altimeter.ui.theme.InstrumentAmber
import com.apophuy.altimeter.ui.theme.InstrumentMuted
import com.apophuy.altimeter.ui.theme.InstrumentRed

@Composable
internal fun AltitudeAlertsCard(
    alerts: List<AltitudeAlertEntity>,
    distanceUnit: DistanceUnit,
    hasNotificationPermission: Boolean,
    onAdd: (Double) -> Unit,
    onReplace: (Long, Double) -> Unit,
    onEnabledChanged: (Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var edited by remember { mutableStateOf<AltitudeAlertEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    InstrumentCard {
        Text(
            stringResource(R.string.altitude_alerts_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(stringResource(R.string.altitude_alerts_description), color = InstrumentMuted)
        if (!hasNotificationPermission && alerts.any { it.enabled }) {
            Text(stringResource(R.string.altitude_alerts_permission_warning), color = InstrumentAmber)
        }
        alerts.forEach { alert ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    formatAltitude(alert.altitudeMetersMsl, distanceUnit),
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                )
                Switch(
                    checked = alert.enabled,
                    onCheckedChange = { onEnabledChanged(alert.id, it) },
                )
                TextButton(onClick = { edited = alert }) { Text(stringResource(R.string.edit)) }
                TextButton(onClick = { onDelete(alert.id) }) {
                    Text(stringResource(R.string.delete), color = InstrumentRed)
                }
            }
        }
        OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.add_altitude_alert))
        }
    }

    if (adding) {
        AltitudeAlertDialog(
            distanceUnit = distanceUnit,
            initialMeters = null,
            onDismiss = { adding = false },
            onApply = {
                onAdd(it)
                adding = false
            },
        )
    }
    edited?.let { alert ->
        AltitudeAlertDialog(
            distanceUnit = distanceUnit,
            initialMeters = alert.altitudeMetersMsl,
            onDismiss = { edited = null },
            onApply = {
                onReplace(alert.id, it)
                edited = null
            },
        )
    }
}

@Composable
private fun AltitudeAlertDialog(
    distanceUnit: DistanceUnit,
    initialMeters: Double?,
    onDismiss: () -> Unit,
    onApply: (Double) -> Unit,
) {
    var text by remember(initialMeters, distanceUnit) {
        mutableStateOf(initialMeters?.let { distanceValueFromMeters(it, distanceUnit).toString() } ?: "")
    }
    val parsed = text.replace(',', '.').toDoubleOrNull()
    val valid = parsed?.isFinite() == true
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.altitude_alert_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.altitude_alert_dialog_description))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.altitude)) },
                    suffix = { Text(distanceUnitSymbol(distanceUnit)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    isError = text.isNotBlank() && !valid,
                    supportingText = if (text.isNotBlank() && !valid) {
                        { Text(stringResource(R.string.invalid_number)) }
                    } else null,
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onApply(distanceValueToMeters(checkNotNull(parsed), distanceUnit)) },
            ) { Text(stringResource(R.string.apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
