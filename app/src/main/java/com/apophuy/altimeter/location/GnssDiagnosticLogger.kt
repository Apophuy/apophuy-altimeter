package com.apophuy.altimeter.location

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.core.content.FileProvider
import com.apophuy.altimeter.BuildConfig
import com.apophuy.altimeter.model.UserSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal data class GnssSatelliteDiagnostic(
    val constellation: Int,
    val svid: Int,
    val cn0DbHz: Double?,
    val used: Boolean,
    val ephemeris: Boolean,
    val almanac: Boolean,
    val carrierHz: Double?,
)

/**
 * Opt-in GNSS diagnostics written by one IO coroutine. Platform callback objects are converted to
 * immutable primitive fields before they reach this logger, and coordinates/raw NMEA are excluded.
 */
class GnssDiagnosticLogger(
    private val context: Context,
    settings: StateFlow<UserSettings>,
    scope: CoroutineScope,
) {
    private sealed interface Command {
        data class Event(
            val timestampMillis: Long,
            val elapsedMillis: Long,
            val name: String,
            val fields: Map<String, Any?>,
        ) : Command
        data class Snapshot(val result: CompletableDeferred<File?>) : Command
        data class Clear(val result: CompletableDeferred<Unit>) : Command
    }

    private val commands = Channel<Command>(capacity = CHANNEL_CAPACITY)
    private val enabled = AtomicBoolean(false)
    private val enabledState = MutableStateFlow(false)
    private val droppedEvents = AtomicInteger(0)
    private val sourceDirectory = File(context.filesDir, DIAGNOSTICS_DIRECTORY)
    private val snapshotDirectory = File(context.cacheDir, "exports")

    init {
        scope.launch(Dispatchers.IO) {
            val writer = RotatingLogWriter(sourceDirectory, MAX_FILE_BYTES)
            for (command in commands) {
                try {
                    when (command) {
                        is Command.Event -> {
                            appendDroppedEventIfNeeded(writer)
                            appendEvent(writer, command)
                        }
                        is Command.Snapshot -> {
                            appendDroppedEventIfNeeded(writer)
                            command.result.complete(createSnapshot(writer))
                        }
                        is Command.Clear -> {
                            writer.clear()
                            snapshotDirectory.listFiles()
                                ?.filter { it.name.startsWith(SNAPSHOT_PREFIX) && it.extension == "jsonl" }
                                ?.forEach(File::delete)
                            command.result.complete(Unit)
                        }
                    }
                } catch (error: Throwable) {
                    if (command is Command.Snapshot) command.result.complete(null)
                    if (command is Command.Clear) command.result.completeExceptionally(error)
                }
            }
        }
        scope.launch {
            settings
                .map { it.gnssDiagnosticLoggingEnabled }
                .distinctUntilChanged()
                .collect(::setEnabled)
        }
    }

    val isEnabled: Boolean
        get() = enabled.get()

    val enabledChanges: StateFlow<Boolean> = enabledState

    fun log(event: String, fields: () -> Map<String, Any?> = { emptyMap() }) {
        if (!enabled.get()) return
        val command = runCatching { eventCommand(event, fields()) }.getOrElse { error ->
            eventCommand(
                name = "diagnostic_encoding_error",
                fields = mapOf("source_event" to event, "exception" to error.javaClass.name),
            )
        }
        if (commands.trySend(command).isFailure) droppedEvents.incrementAndGet()
    }

    suspend fun createShareIntent(): Intent? {
        val result = CompletableDeferred<File?>()
        commands.send(Command.Snapshot(result))
        val file = result.await() ?: return null
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Altimeter GNSS diagnostics")
            clipData = ClipData.newRawUri("GNSS diagnostic log", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    suspend fun clear() {
        val result = CompletableDeferred<Unit>()
        commands.send(Command.Clear(result))
        result.await()
    }

    private suspend fun setEnabled(value: Boolean) {
        if (value == enabled.get()) return
        if (value) {
            // Queue the session header before callbacks are allowed to enqueue diagnostic events.
            commands.send(eventCommand("session_start", deviceFields()))
            enabled.set(true)
            enabledState.value = true
        } else {
            enabled.set(false)
            enabledState.value = false
            commands.send(eventCommand("session_stop", emptyMap()))
        }
    }

    private fun appendDroppedEventIfNeeded(writer: RotatingLogWriter) {
        val count = droppedEvents.getAndSet(0)
        if (count > 0) appendEvent(writer, eventCommand("dropped_events", mapOf("count" to count)))
    }

    private fun appendEvent(writer: RotatingLogWriter, event: Command.Event) {
        val line = runCatching { diagnosticJson(event) }.getOrElse { error ->
            diagnosticJson(
                event.copy(
                    name = "diagnostic_encoding_error",
                    fields = mapOf(
                        "source_event" to event.name,
                        "exception" to error.javaClass.name,
                    ),
                ),
            )
        }
        writer.appendLine(line)
    }

    private fun createSnapshot(writer: RotatingLogWriter): File? {
        snapshotDirectory.mkdirs()
        val name = "$SNAPSHOT_PREFIX${SNAPSHOT_TIME.format(Instant.now())}.jsonl"
        val destination = File(snapshotDirectory, name)
        return destination.takeIf { writer.snapshotTo(it) }
    }

    private fun deviceFields(): Map<String, Any?> = mapOf(
        "app_version" to BuildConfig.VERSION_NAME,
        "app_version_code" to BuildConfig.VERSION_CODE,
        "manufacturer" to Build.MANUFACTURER,
        "brand" to Build.BRAND,
        "model" to Build.MODEL,
        "device" to Build.DEVICE,
        "product" to Build.PRODUCT,
        "fingerprint" to Build.FINGERPRINT,
        "android_sdk" to Build.VERSION.SDK_INT,
        "android_release" to Build.VERSION.RELEASE,
        "build_display" to Build.DISPLAY,
        "compatibility" to GnssCompatibility.detect(
            manufacturer = Build.MANUFACTURER,
            brand = Build.BRAND,
            model = Build.MODEL,
            device = Build.DEVICE,
            product = Build.PRODUCT,
            fingerprint = Build.FINGERPRINT,
        ).name,
    )

    private fun eventCommand(name: String, fields: Map<String, Any?>): Command.Event = Command.Event(
        timestampMillis = System.currentTimeMillis(),
        elapsedMillis = SystemClock.elapsedRealtime(),
        name = name,
        fields = fields,
    )

    private fun diagnosticJson(event: Command.Event): String = JSONObject().apply {
        put("ts_utc", Instant.ofEpochMilli(event.timestampMillis).toString())
        put("elapsed_ms", event.elapsedMillis)
        put("event", event.name)
        event.fields.forEach { (key, value) -> put(key, diagnosticJsonValue(value)) }
    }.toString()

    private fun diagnosticJsonValue(value: Any?): Any = when (value) {
        is GnssSatelliteDiagnostic -> JSONObject().apply {
            put("constellation", value.constellation)
            put("svid", value.svid)
            put("cn0_db_hz", JSONObject.wrap(value.cn0DbHz))
            put("used", value.used)
            put("ephemeris", value.ephemeris)
            put("almanac", value.almanac)
            put("carrier_hz", JSONObject.wrap(value.carrierHz))
        }
        is Map<*, *> -> JSONObject().apply {
            value.forEach { (key, nested) -> put(key.toString(), diagnosticJsonValue(nested)) }
        }
        is Iterable<*> -> JSONArray().apply {
            value.forEach { nested -> put(diagnosticJsonValue(nested)) }
        }
        else -> JSONObject.wrap(value)
    }

    companion object {
        private const val CHANNEL_CAPACITY = 256
        private const val DIAGNOSTICS_DIRECTORY = "diagnostics"
        private const val SNAPSHOT_PREFIX = "altimeter_gnss_diagnostic_"
        private const val MAX_FILE_BYTES = 2L * 1024L * 1024L
        private val SNAPSHOT_TIME = DateTimeFormatter
            .ofPattern("yyyy-MM-dd_HH-mm-ss")
            .withZone(ZoneOffset.UTC)
    }
}

/** Keeps at most two bounded source files; a snapshot joins them in chronological order. */
internal class RotatingLogWriter(
    directory: File,
    private val maxFileBytes: Long,
) {
    private val current = File(directory, "current.jsonl")
    private val previous = File(directory, "previous.jsonl")

    init {
        directory.mkdirs()
    }

    fun appendLine(line: String) {
        val bytes = "$line\n".toByteArray(Charsets.UTF_8)
        if (current.isFile && current.length() > 0L && current.length() + bytes.size > maxFileBytes) {
            previous.delete()
            if (!current.renameTo(previous)) {
                current.copyTo(previous, overwrite = true)
                current.delete()
            }
        }
        current.appendBytes(bytes)
    }

    fun snapshotTo(destination: File): Boolean {
        val sources = listOf(previous, current).filter { it.isFile && it.length() > 0L }
        if (sources.isEmpty()) return false
        destination.parentFile?.mkdirs()
        destination.outputStream().buffered().use { output ->
            sources.forEach { source -> source.inputStream().buffered().use { it.copyTo(output) } }
        }
        return true
    }

    fun clear() {
        current.delete()
        previous.delete()
    }
}
