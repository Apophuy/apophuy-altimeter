package com.apophuy.altimeter.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.apophuy.altimeter.data.TrackRepository
import com.apophuy.altimeter.data.local.TrackPointEntity
import com.apophuy.altimeter.data.local.TrackSessionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class ExportFormat(val extension: String, val mimeType: String) {
    GPX("gpx", "application/gpx+xml"),
    CSV("csv", "text/csv"),
}

class TrackExporter(
    private val context: Context,
    private val trackRepository: TrackRepository,
) {
    suspend fun shareIntent(sessionId: Long, format: ExportFormat): Intent? = withContext(Dispatchers.IO) {
        val session = trackRepository.getSession(sessionId) ?: return@withContext null
        val points = trackRepository.getPoints(sessionId)
        val content = when (format) {
            ExportFormat.GPX -> ExportFormatter.gpx(session, points)
            ExportFormat.CSV -> ExportFormatter.csv(points)
        }
        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        val localDate = Instant.ofEpochMilli(session.startedAtMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm"))
        val file = File(directory, "altimeter_$localDate.${format.extension}")
        file.writeText(content, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        Intent(Intent.ACTION_SEND).apply {
            type = format.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Altimeter $localDate")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}

object ExportFormatter {
    private val isoFormatter = DateTimeFormatter.ISO_INSTANT

    fun gpx(session: TrackSessionEntity, points: List<TrackPointEntity>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<gpx version=\"1.1\" creator=\"Altimeter\" ")
        append("xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        append("  <metadata><time>")
        append(iso(session.startedAtMillis))
        append("</time></metadata>\n")
        append("  <trk><name>Altimeter ")
        append(escapeXml(iso(session.startedAtMillis)))
        append("</name><trkseg>\n")
        points.filter { it.latitude != null && it.longitude != null }.forEach { point ->
            append("    <trkpt lat=\"")
            append(formatDecimal(point.latitude!!, 7))
            append("\" lon=\"")
            append(formatDecimal(point.longitude!!, 7))
            append("\"><ele>")
            append(formatDecimal(point.altitudeMetersMsl, 2))
            append("</ele><time>")
            append(iso(point.timestampMillis))
            append("</time></trkpt>\n")
        }
        append("  </trkseg></trk>\n</gpx>\n")
    }

    fun csv(points: List<TrackPointEntity>): String = buildString {
        append("timestamp_utc,latitude,longitude,altitude_msl_m,pressure_hpa,")
        append("horizontal_accuracy_m,vertical_accuracy_m,altitude_source\n")
        points.forEach { point ->
            append(csvCell(iso(point.timestampMillis))).append(',')
            append(point.latitude?.let { formatDecimal(it, 7) } ?: "").append(',')
            append(point.longitude?.let { formatDecimal(it, 7) } ?: "").append(',')
            append(formatDecimal(point.altitudeMetersMsl, 2)).append(',')
            append(point.pressureHpa?.let { formatDecimal(it.toDouble(), 2) } ?: "").append(',')
            append(point.horizontalAccuracyMeters?.let { formatDecimal(it.toDouble(), 1) } ?: "").append(',')
            append(point.verticalAccuracyMeters?.let { formatDecimal(it.toDouble(), 1) } ?: "").append(',')
            append(csvCell(point.altitudeSource)).append('\n')
        }
    }

    private fun iso(timestampMillis: Long): String =
        isoFormatter.format(Instant.ofEpochMilli(timestampMillis))

    private fun formatDecimal(value: Double, digits: Int): String =
        String.format(Locale.US, "%.${digits}f", value)

    private fun escapeXml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun csvCell(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${value.replace("\"", "\"\"")}\""
        } else value
}
