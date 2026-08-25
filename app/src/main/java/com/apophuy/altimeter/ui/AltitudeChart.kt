package com.apophuy.altimeter.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.apophuy.altimeter.data.local.TrackPointEntity
import com.apophuy.altimeter.ui.theme.InstrumentMuted
import com.apophuy.altimeter.ui.theme.InstrumentSurfaceHigh
import com.apophuy.altimeter.ui.theme.InstrumentTeal

@Composable
fun AltitudeChart(points: List<TrackPointEntity>, modifier: Modifier = Modifier) {
    val gridColor = InstrumentSurfaceHigh
    val lineColor = InstrumentTeal
    Canvas(modifier) {
        if (points.isEmpty()) return@Canvas
        val left = 10f
        val right = size.width - 10f
        val top = 10f
        val bottom = size.height - 10f
        val minimum = points.minOf { it.altitudeMetersMsl }
        val maximum = points.maxOf { it.altitudeMetersMsl }
        val altitudeRange = (maximum - minimum).coerceAtLeast(1.0)
        val firstTime = points.first().timestampMillis
        val timeRange = (points.last().timestampMillis - firstTime).coerceAtLeast(1L)

        repeat(4) { index ->
            val y = top + (bottom - top) * index / 3f
            drawLine(gridColor, Offset(left, y), Offset(right, y), strokeWidth = 2f)
        }

        val line = Path()
        val fill = Path()
        points.forEachIndexed { index, point ->
            val x = left + (right - left) * (point.timestampMillis - firstTime).toFloat() / timeRange
            val y = bottom - (bottom - top) * ((point.altitudeMetersMsl - minimum) / altitudeRange).toFloat()
            if (index == 0) {
                line.moveTo(x, y)
                fill.moveTo(x, bottom)
                fill.lineTo(x, y)
            } else {
                line.lineTo(x, y)
                fill.lineTo(x, y)
            }
            if (index == points.lastIndex) {
                fill.lineTo(x, bottom)
                fill.close()
            }
        }
        drawPath(fill, lineColor.copy(alpha = 0.12f))
        drawPath(line, lineColor, style = Stroke(width = 5f))
    }
}
