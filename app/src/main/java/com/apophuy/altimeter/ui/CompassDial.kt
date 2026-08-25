package com.apophuy.altimeter.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import com.apophuy.altimeter.R
import com.apophuy.altimeter.ui.theme.InstrumentDial
import com.apophuy.altimeter.ui.theme.InstrumentAmber
import com.apophuy.altimeter.ui.theme.InstrumentMuted
import com.apophuy.altimeter.ui.theme.InstrumentRed
import com.apophuy.altimeter.ui.theme.InstrumentSurfaceHigh
import com.apophuy.altimeter.ui.theme.InstrumentTeal
import com.apophuy.altimeter.ui.theme.InstrumentText
import com.apophuy.altimeter.util.normalizeDegrees
import com.apophuy.altimeter.util.shortestAngleDelta
import com.apophuy.altimeter.util.currentAppLocale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun CompassDial(
    headingDegrees: Float?,
    northReferenceLabel: String,
    unavailableLabel: String,
    cardinalLabels: List<String>,
    directionLabels: List<String>,
    targetDirectionDegrees: Float? = null,
    modifier: Modifier = Modifier,
) {
    val mutedColor = InstrumentMuted
    val northColor = InstrumentRed
    val ringColor = InstrumentSurfaceHigh
    val accentColor = InstrumentTeal
    val textColor = InstrumentText
    val dialColor = InstrumentDial
    val targetColor = InstrumentAmber
    val initial = headingDegrees ?: 0f
    var continuousTarget by remember { mutableFloatStateOf(initial) }
    LaunchedEffect(headingDegrees) {
        headingDegrees?.let { newHeading ->
            continuousTarget += shortestAngleDelta(normalizeDegrees(continuousTarget), newHeading)
        }
    }
    val animatedHeading by animateFloatAsState(
        targetValue = continuousTarget,
        animationSpec = tween(durationMillis = 260),
        label = "compass-heading",
    )
    var continuousTargetDirection by remember { mutableFloatStateOf(targetDirectionDegrees ?: 0f) }
    LaunchedEffect(targetDirectionDegrees) {
        targetDirectionDegrees?.let { newDirection ->
            continuousTargetDirection += shortestAngleDelta(
                normalizeDegrees(continuousTargetDirection),
                newDirection,
            )
        }
    }
    val animatedTargetDirection by animateFloatAsState(
        targetValue = continuousTargetDirection,
        animationSpec = tween(durationMillis = 260),
        label = "target-direction",
    )
    val accessibility = headingDegrees?.let {
        stringResource(
            R.string.compass_accessibility,
            normalizeDegrees(it).toInt(),
            directionLabel(it, directionLabels),
        )
    } ?: unavailableLabel

    BoxWithConstraints(
        modifier = modifier
            .sizeIn(maxWidth = 390.dp, maxHeight = 390.dp)
            .semantics { contentDescription = accessibility },
        contentAlignment = Alignment.Center,
    ) {
        val compact = minOf(maxWidth, maxHeight) < 180.dp
        Canvas(Modifier.fillMaxSize().padding(10.dp)) {
            val radius = min(size.width, size.height) / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(dialColor, radius, center)
            drawCircle(ringColor, radius * 0.97f, center, style = androidx.compose.ui.graphics.drawscope.Stroke(radius * 0.018f))
            drawCircle(accentColor.copy(alpha = 0.18f), radius * 0.76f, center, style = androidx.compose.ui.graphics.drawscope.Stroke(radius * 0.008f))

            rotate(-animatedHeading, center) {
                for (degree in 0 until 360 step 5) {
                    val radians = Math.toRadians((degree - 90).toDouble())
                    val major = degree % 30 == 0
                    val cardinal = degree % 90 == 0
                    val outer = radius * 0.91f
                    val inner = radius * when {
                        cardinal -> 0.76f
                        major -> 0.80f
                        else -> 0.85f
                    }
                    val color = if (degree == 0) northColor else if (major) textColor else mutedColor
                    drawLine(
                        color = color,
                        start = Offset(
                            center.x + cos(radians).toFloat() * inner,
                            center.y + sin(radians).toFloat() * inner,
                        ),
                        end = Offset(
                            center.x + cos(radians).toFloat() * outer,
                            center.y + sin(radians).toFloat() * outer,
                        ),
                        strokeWidth = radius * if (cardinal) 0.018f else if (major) 0.011f else 0.005f,
                    )
                }

                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textSize = radius * 0.17f
                }
                cardinalLabels.forEachIndexed { index, label ->
                    val radians = Math.toRadians((index * 90 - 90).toDouble())
                    paint.color = (if (index == 0) northColor else textColor).toArgb()
                    val baselineCorrection = -(paint.ascent() + paint.descent()) / 2
                    drawContext.canvas.nativeCanvas.drawText(
                        label,
                        center.x + cos(radians).toFloat() * radius * 0.62f,
                        center.y + sin(radians).toFloat() * radius * 0.62f + baselineCorrection,
                        paint,
                    )
                }

            }

            if (targetDirectionDegrees != null) {
                val radians = Math.toRadians((animatedTargetDirection - 90).toDouble())
                val markerCenter = Offset(
                    center.x + cos(radians).toFloat() * radius * 0.72f,
                    center.y + sin(radians).toFloat() * radius * 0.72f,
                )
                drawCircle(targetColor, radius * 0.055f, markerCenter)
                drawCircle(ringColor, radius * 0.022f, markerCenter)
            }

            val triangle = Path().apply {
                moveTo(center.x, center.y - radius * 0.98f)
                lineTo(center.x - radius * 0.055f, center.y - radius * 0.86f)
                lineTo(center.x + radius * 0.055f, center.y - radius * 0.86f)
                close()
            }
            drawPath(triangle, accentColor)
            drawCircle(ringColor, radius * 0.25f, center)
            drawCircle(accentColor, radius * 0.035f, center)
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = headingDegrees?.let {
                    String.format(currentAppLocale(), "%03d°", normalizeDegrees(it).toInt())
                } ?: "—",
                style = MaterialTheme.typography.headlineMedium,
                fontSize = if (compact) 16.sp else 28.sp,
                lineHeight = if (compact) 20.sp else 32.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = headingDegrees?.let { directionLabel(it, directionLabels) } ?: unavailableLabel,
                style = MaterialTheme.typography.labelLarge,
                fontSize = if (compact) 11.sp else 14.sp,
                lineHeight = if (compact) 13.sp else 20.sp,
                color = mutedColor,
            )
            if (headingDegrees != null && !compact) {
                Text(
                    text = northReferenceLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                )
            }
        }
    }
}

internal fun directionLabel(heading: Float, labels: List<String>): String =
    labels[((normalizeDegrees(heading) + 22.5f) / 45f).toInt() % labels.size]

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(),
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt(),
)
