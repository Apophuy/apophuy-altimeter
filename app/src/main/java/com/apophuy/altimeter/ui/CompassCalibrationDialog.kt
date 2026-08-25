package com.apophuy.altimeter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.apophuy.altimeter.R
import com.apophuy.altimeter.model.CompassAccuracy
import com.apophuy.altimeter.model.CompassReading
import com.apophuy.altimeter.ui.theme.InstrumentAmber
import com.apophuy.altimeter.ui.theme.InstrumentGreen
import com.apophuy.altimeter.ui.theme.InstrumentMuted
import kotlinx.coroutines.delay

@Composable
internal fun CompassCalibrationDialog(
    compass: CompassReading,
    onDismiss: () -> Unit,
) {
    val latestCompass by rememberUpdatedState(compass)
    var calibrated by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        var goodSinceMillis: Long? = null
        while (!calibrated) {
            val current = latestCompass
            val fieldNormal = current.magneticFieldMicroTesla?.let { it in 20f..70f } == true
            val accuracyGood = current.accuracy == CompassAccuracy.MEDIUM ||
                current.accuracy == CompassAccuracy.HIGH
            val now = System.currentTimeMillis()
            goodSinceMillis = if (fieldNormal && accuracyGood) goodSinceMillis ?: now else null
            if (goodSinceMillis != null && now - goodSinceMillis >= 2_000L) calibrated = true
            delay(200L)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.compass_calibration_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.compass_calibration_move_away))
                Text(stringResource(R.string.compass_calibration_figure_eight))
                Text(
                    stringResource(R.string.compass_accuracy_value, compassAccuracyText(compass.accuracy)),
                    color = if (calibrated) InstrumentGreen else InstrumentAmber,
                )
                Text(
                    compass.magneticFieldMicroTesla?.let {
                        stringResource(R.string.magnetic_field_value, it)
                    } ?: stringResource(R.string.magnetic_field_unavailable),
                    color = InstrumentMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    stringResource(
                        if (calibrated) R.string.compass_calibration_success
                        else R.string.compass_calibration_waiting,
                    ),
                    color = if (calibrated) InstrumentGreen else InstrumentMuted,
                )
                Text(
                    stringResource(R.string.compass_calibration_explanation),
                    color = InstrumentMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}

@Composable
internal fun compassAccuracyText(accuracy: CompassAccuracy): String = stringResource(
    when (accuracy) {
        CompassAccuracy.UNKNOWN -> R.string.compass_accuracy_unknown
        CompassAccuracy.UNRELIABLE -> R.string.compass_accuracy_unreliable
        CompassAccuracy.LOW -> R.string.compass_accuracy_low
        CompassAccuracy.MEDIUM -> R.string.compass_accuracy_medium
        CompassAccuracy.HIGH -> R.string.compass_accuracy_high
    },
)
