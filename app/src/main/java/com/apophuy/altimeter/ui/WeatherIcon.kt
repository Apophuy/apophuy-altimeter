package com.apophuy.altimeter.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.apophuy.altimeter.model.WeatherKind
import com.apophuy.altimeter.ui.theme.InstrumentAmber
import com.apophuy.altimeter.ui.theme.InstrumentMuted
import com.apophuy.altimeter.ui.theme.InstrumentTeal
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun WeatherIcon(kind: WeatherKind, modifier: Modifier = Modifier, isDay: Boolean = true) {
    val light = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val sunColor = if (light) Color(0xFFD28A00) else InstrumentAmber
    val mutedColor = InstrumentMuted
    val rainColor = InstrumentTeal
    val cloudColor = if (light) Color(0xFF708D9A) else Color(0xFFD8E2EC)
    val moonColor = if (light) Color(0xFF6676AD) else Color(0xFFD4DCFF)
    val snowColor = if (light) Color(0xFF597588) else Color.White
    Canvas(modifier) {
        val unit = size.minDimension / 100f
        fun sun(center: Offset = Offset(38 * unit, 38 * unit), radius: Float = 16 * unit) {
            drawCircle(sunColor, radius, center)
            for (i in 0 until 8) {
                val angle = Math.toRadians((i * 45).toDouble())
                drawLine(
                    sunColor,
                    Offset(center.x + cos(angle).toFloat() * radius * 1.35f, center.y + sin(angle).toFloat() * radius * 1.35f),
                    Offset(center.x + cos(angle).toFloat() * radius * 1.75f, center.y + sin(angle).toFloat() * radius * 1.75f),
                    strokeWidth = 4 * unit,
                    cap = StrokeCap.Round,
                )
            }
        }
        fun cloud(color: Color = cloudColor) {
            val path = Path().apply {
                moveTo(20 * unit, 68 * unit)
                cubicTo(8 * unit, 55 * unit, 22 * unit, 43 * unit, 35 * unit, 48 * unit)
                cubicTo(40 * unit, 24 * unit, 73 * unit, 28 * unit, 75 * unit, 51 * unit)
                cubicTo(92 * unit, 50 * unit, 96 * unit, 72 * unit, 78 * unit, 76 * unit)
                lineTo(25 * unit, 76 * unit)
                cubicTo(17 * unit, 76 * unit, 14 * unit, 72 * unit, 20 * unit, 68 * unit)
                close()
            }
            drawPath(path, color)
        }
        fun moon() {
            val path = Path().apply {
                moveTo(60 * unit, 18 * unit)
                cubicTo(8 * unit, 10 * unit, 10 * unit, 86 * unit, 65 * unit, 77 * unit)
                cubicTo(36 * unit, 70 * unit, 31 * unit, 37 * unit, 60 * unit, 18 * unit)
                close()
            }
            drawPath(path, moonColor)
        }
        fun rain(snow: Boolean = false) {
            for (x in listOf(34f, 52f, 70f)) {
                if (snow) {
                    drawCircle(snowColor, 3.3f * unit, Offset(x * unit, 88 * unit))
                } else {
                    drawLine(
                        rainColor,
                        Offset(x * unit, 82 * unit),
                        Offset((x - 4) * unit, 94 * unit),
                        strokeWidth = 4 * unit,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }

        when (kind) {
            WeatherKind.CLEAR -> if (isDay) sun(Offset(50 * unit, 50 * unit), 20 * unit) else moon()
            WeatherKind.PARTLY_CLOUDY -> { if (isDay) sun() else moon(); cloud() }
            WeatherKind.CLOUDY -> cloud()
            WeatherKind.FOG -> {
                cloud(mutedColor)
                for (y in listOf(82f, 92f)) drawLine(mutedColor, Offset(22 * unit, y * unit), Offset(78 * unit, y * unit), 4 * unit, cap = StrokeCap.Round)
            }
            WeatherKind.DRIZZLE, WeatherKind.RAIN, WeatherKind.SHOWERS -> { cloud(); rain() }
            WeatherKind.SNOW -> { cloud(); rain(snow = true) }
            WeatherKind.THUNDERSTORM -> {
                cloud(mutedColor)
                val bolt = Path().apply {
                    moveTo(54 * unit, 76 * unit)
                    lineTo(42 * unit, 91 * unit)
                    lineTo(53 * unit, 89 * unit)
                    lineTo(46 * unit, 100 * unit)
                    lineTo(67 * unit, 83 * unit)
                    lineTo(56 * unit, 85 * unit)
                    close()
                }
                drawPath(bolt, sunColor)
            }
            WeatherKind.UNKNOWN -> drawCircle(mutedColor, 31 * unit, Offset(50 * unit, 50 * unit), style = Stroke(6 * unit))
        }
    }
}
