package com.apophuy.altimeter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.apophuy.altimeter.R
import com.apophuy.altimeter.model.DeviceCapabilities
import com.apophuy.altimeter.model.MissingHardware
import com.apophuy.altimeter.ui.theme.InstrumentAmber
import com.apophuy.altimeter.ui.theme.InstrumentMuted

@Composable
internal fun HardwareNotice(missing: List<MissingHardware>, acknowledged: Boolean?, onAcknowledge: () -> Unit) {
    var dismissed by rememberSaveable { mutableStateOf(false) }
    if (missing.isEmpty() || acknowledged != false || dismissed) return
    val dismiss = {
        dismissed = true
        onAcknowledge()
    }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.hardware_notice_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.hardware_notice_intro))
                HardwareDetails(missing)
                Text(stringResource(R.string.hardware_notice_once), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.understood)) } },
    )
}

@Composable
internal fun HardwareSettingsCard(capabilities: DeviceCapabilities?) {
    if (capabilities == null) return
    InstrumentCard {
        Text(stringResource(R.string.hardware_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (capabilities.missingHardware.isEmpty()) {
            Text(stringResource(R.string.hardware_available), color = InstrumentMuted)
        } else {
            HardwareDetails(capabilities.missingHardware)
        }
    }
}

@Composable
private fun HardwareDetails(missing: List<MissingHardware>) {
    missing.forEach { hardware ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(hardwareName(hardware), color = InstrumentAmber, fontWeight = FontWeight.Bold)
            Text(stringResource(when (hardware) {
                MissingHardware.GPS -> R.string.hardware_gps_effect
                MissingHardware.ACCELEROMETER -> R.string.hardware_accelerometer_effect
                MissingHardware.MAGNETOMETER -> R.string.hardware_magnetometer_effect
                MissingHardware.BAROMETER -> R.string.hardware_barometer_effect
            }), style = MaterialTheme.typography.bodyMedium, color = InstrumentMuted)
        }
    }
}

@Composable
private fun hardwareName(hardware: MissingHardware): String = stringResource(when (hardware) {
    MissingHardware.GPS -> R.string.hardware_gps
    MissingHardware.ACCELEROMETER -> R.string.hardware_accelerometer
    MissingHardware.MAGNETOMETER -> R.string.hardware_magnetometer
    MissingHardware.BAROMETER -> R.string.hardware_barometer
})
