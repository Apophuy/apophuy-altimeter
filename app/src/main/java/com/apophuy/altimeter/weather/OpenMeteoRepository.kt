package com.apophuy.altimeter.weather

import com.apophuy.altimeter.data.local.AltimeterDao
import com.apophuy.altimeter.data.local.TerrainCacheEntity
import com.apophuy.altimeter.data.local.WeatherCacheEntity
import com.apophuy.altimeter.model.TerrainElevation
import com.apophuy.altimeter.model.WeatherKind
import com.apophuy.altimeter.model.WeatherSnapshot
import com.apophuy.altimeter.model.WeatherForecast
import com.apophuy.altimeter.model.HourlyForecast
import com.apophuy.altimeter.model.DailyForecast
import com.apophuy.altimeter.util.distanceMeters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicBoolean

class OpenMeteoRepository(
    private val dao: AltimeterDao,
    private val scope: CoroutineScope,
) {
    private val _weather = MutableStateFlow<WeatherSnapshot?>(null)
    val weather: StateFlow<WeatherSnapshot?> = _weather

    private val _terrain = MutableStateFlow<TerrainElevation?>(null)
    val terrain: StateFlow<TerrainElevation?> = _terrain

    private val refreshing = AtomicBoolean(false)
    private var loadJob: Job? = null
    private val cacheLoadJob: Job
    private var lastAttemptMillis: Long = 0L

    init {
        cacheLoadJob = scope.launch(Dispatchers.IO) {
            dao.getWeatherCache()?.let { _weather.value = it.toModel(stale = isWeatherStale(it.updatedAtMillis)) }
            dao.getTerrainCache()?.let { _terrain.value = it.toModel(stale = isTerrainStale(it.updatedAtMillis)) }
        }
    }

    fun refreshIfNeeded(latitude: Double, longitude: Double, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastAttemptMillis < RETRY_BACKOFF_MS) return
        val weatherNeedsRefresh = _weather.value.let { value ->
            force || value == null || value.forecast == null || isWeatherStale(value.updatedAtMillis) ||
                distanceMeters(latitude, longitude, value.latitude, value.longitude) >= 1_000.0
        }
        val terrainNeedsRefresh = _terrain.value.let { value ->
            force || value == null || isTerrainStale(value.updatedAtMillis) ||
                distanceMeters(latitude, longitude, value.latitude, value.longitude) >= 100.0
        }
        if (!weatherNeedsRefresh && !terrainNeedsRefresh) return
        if (!refreshing.compareAndSet(false, true)) return
        lastAttemptMillis = now
        if (weatherNeedsRefresh) _weather.value = _weather.value?.copy(stale = true)
        if (terrainNeedsRefresh) _terrain.value = _terrain.value?.copy(stale = true)

        loadJob?.cancel()
        loadJob = scope.launch {
            try {
                cacheLoadJob.join()
                if (weatherNeedsRefresh) refreshWeather(latitude, longitude)
                if (terrainNeedsRefresh) refreshTerrain(latitude, longitude)
            } finally {
                refreshing.set(false)
            }
        }
    }

    private suspend fun refreshWeather(latitude: Double, longitude: Double) {
        runCatching {
            val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$latitude&longitude=$longitude" +
                "&current=temperature_2m,apparent_temperature,relative_humidity_2m," +
                "weather_code,wind_speed_10m,surface_pressure,is_day" +
                "&hourly=temperature_2m,weather_code,precipitation_probability,wind_speed_10m,is_day" +
                "&daily=temperature_2m_min,temperature_2m_max,weather_code," +
                "precipitation_probability_max,wind_speed_10m_max" +
                "&forecast_days=7&wind_speed_unit=ms&timezone=auto&timeformat=unixtime"
            val json = fetch(url)
            val parsed = OpenMeteoParser.parseWeather(json, latitude, longitude)
            val cache = parsed.toCache(json)
            withContext(Dispatchers.IO) { dao.putWeatherCache(cache) }
            _weather.value = parsed
        }.onFailure {
            _weather.value = _weather.value?.copy(stale = true)
        }
    }

    private suspend fun refreshTerrain(latitude: Double, longitude: Double) {
        runCatching {
            val url = "https://api.open-meteo.com/v1/elevation?latitude=$latitude&longitude=$longitude"
            val parsed = OpenMeteoParser.parseTerrain(fetch(url), latitude, longitude)
            val cache = parsed.toCache()
            withContext(Dispatchers.IO) { dao.putTerrainCache(cache) }
            _terrain.value = parsed
        }.onFailure {
            _terrain.value = _terrain.value?.copy(stale = true)
        }
    }

    private suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "Altimeter/1.0 (personal Android app)")
            val code = connection.responseCode
            if (code !in 200..299) error("Open-Meteo HTTP $code")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun isWeatherStale(timestamp: Long): Boolean =
        System.currentTimeMillis() - timestamp > WEATHER_MAX_AGE_MS

    private fun isTerrainStale(timestamp: Long): Boolean =
        System.currentTimeMillis() - timestamp > TERRAIN_MAX_AGE_MS

    companion object {
        const val WEATHER_MAX_AGE_MS = 15 * 60 * 1_000L
        const val TERRAIN_MAX_AGE_MS = 24 * 60 * 60 * 1_000L
        const val RETRY_BACKOFF_MS = 5 * 60 * 1_000L
    }
}

object OpenMeteoParser {
    fun parseWeather(json: String, latitude: Double, longitude: Double): WeatherSnapshot {
        val root = JSONObject(json)
        val current = root.getJSONObject("current")
        val code = current.getInt("weather_code")
        return WeatherSnapshot(
            temperatureC = current.getDouble("temperature_2m"),
            apparentTemperatureC = current.optionalDouble("apparent_temperature"),
            humidityPercent = current.optionalInt("relative_humidity_2m"),
            windSpeedMetersPerSecond = current.optionalDouble("wind_speed_10m"),
            weatherCode = code,
            kind = weatherKind(code),
            surfacePressureHpa = current.optionalDouble("surface_pressure"),
            latitude = latitude,
            longitude = longitude,
            updatedAtMillis = System.currentTimeMillis(),
            isDay = current.optInt("is_day", 1) != 0,
            forecast = parseForecast(root),
        )
    }

    private fun parseForecast(root: JSONObject): WeatherForecast? {
        val hours = root.optJSONObject("hourly")
        val days = root.optJSONObject("daily")
        if (hours == null && days == null) return null
        val timeZone = runCatching { ZoneId.of(root.getString("timezone")) }.getOrElse {
            ZoneOffset.ofTotalSeconds(root.optInt("utc_offset_seconds", 0))
        }
        val hourly = hours?.let { data ->
            val times = data.optJSONArray("time") ?: return@let emptyList()
            (0 until times.length()).mapNotNull { index ->
                val time = runCatching {
                    when (val value = times.get(index)) {
                        is Number -> value.toLong() * 1_000L
                        // Also read ISO timestamps from caches created before the epoch format.
                        else -> LocalDateTime.parse(value.toString()).atZone(timeZone).toInstant().toEpochMilli()
                    }
                }.getOrNull() ?: return@mapNotNull null
                val temperature = data.arrayDouble("temperature_2m", index) ?: return@mapNotNull null
                HourlyForecast(
                    timestampMillis = time,
                    temperatureC = temperature,
                    kind = weatherKind(data.arrayDouble("weather_code", index)?.toInt() ?: -1),
                    precipitationProbability = data.probability("precipitation_probability", index),
                    windSpeedMetersPerSecond = data.arrayDouble("wind_speed_10m", index),
                    isDay = data.arrayDouble("is_day", index) != 0.0,
                )
            }
        }.orEmpty()
        val daily = days?.let { data ->
            val times = data.optJSONArray("time") ?: return@let emptyList()
            (0 until times.length()).mapNotNull { index ->
                val date = runCatching {
                    when (val value = times.get(index)) {
                        // Open-Meteo daily epochs require the response offset to recover the date.
                        is Number -> Instant.ofEpochSecond(value.toLong() + root.optInt("utc_offset_seconds", 0))
                            .atZone(ZoneOffset.UTC).toLocalDate()
                        else -> LocalDate.parse(value.toString())
                    }
                }.getOrNull()
                    ?: return@mapNotNull null
                val min = data.arrayDouble("temperature_2m_min", index) ?: return@mapNotNull null
                val max = data.arrayDouble("temperature_2m_max", index) ?: return@mapNotNull null
                if (min > max) return@mapNotNull null
                DailyForecast(
                    date = date,
                    minTemperatureC = min,
                    maxTemperatureC = max,
                    kind = weatherKind(data.arrayDouble("weather_code", index)?.toInt() ?: -1),
                    precipitationProbability = data.probability("precipitation_probability_max", index),
                    windSpeedMetersPerSecond = data.arrayDouble("wind_speed_10m_max", index),
                )
            }
        }.orEmpty()
        return WeatherForecast(timeZone, hourly.distinctBy { it.timestampMillis }.sortedBy { it.timestampMillis },
            daily.distinctBy { it.date }.sortedBy { it.date })
    }

    private fun JSONObject.arrayDouble(key: String, index: Int): Double? =
        optJSONArray(key)?.optDouble(index)?.takeIf { it.isFinite() }

    private fun JSONObject.probability(key: String, index: Int): Int? =
        arrayDouble(key, index)?.takeIf { it in 0.0..100.0 }?.toInt()

    fun parseTerrain(json: String, latitude: Double, longitude: Double): TerrainElevation {
        val elevation = JSONObject(json).getJSONArray("elevation").getDouble(0)
        return TerrainElevation(
            meters = elevation,
            latitude = latitude,
            longitude = longitude,
            updatedAtMillis = System.currentTimeMillis(),
        )
    }

    fun weatherKind(code: Int): WeatherKind = when (code) {
        0 -> WeatherKind.CLEAR
        1, 2 -> WeatherKind.PARTLY_CLOUDY
        3 -> WeatherKind.CLOUDY
        45, 48 -> WeatherKind.FOG
        51, 53, 55, 56, 57 -> WeatherKind.DRIZZLE
        61, 63, 65, 66, 67 -> WeatherKind.RAIN
        71, 73, 75, 77 -> WeatherKind.SNOW
        80, 81, 82, 85, 86 -> WeatherKind.SHOWERS
        95, 96, 99 -> WeatherKind.THUNDERSTORM
        else -> WeatherKind.UNKNOWN
    }

    private fun JSONObject.optionalDouble(key: String): Double? =
        if (has(key) && !isNull(key)) getDouble(key).takeIf { it.isFinite() } else null

    private fun JSONObject.optionalInt(key: String): Int? =
        if (has(key) && !isNull(key)) getInt(key) else null
}

internal fun WeatherSnapshot.toCache(forecastJson: String) = WeatherCacheEntity(
    latitude = latitude,
    longitude = longitude,
    temperatureC = temperatureC,
    apparentTemperatureC = apparentTemperatureC,
    humidityPercent = humidityPercent,
    windSpeedMetersPerSecond = windSpeedMetersPerSecond,
    weatherCode = weatherCode,
    surfacePressureHpa = surfacePressureHpa,
    updatedAtMillis = updatedAtMillis,
    forecastJson = forecastJson,
)

internal fun WeatherCacheEntity.toModel(stale: Boolean): WeatherSnapshot {
    // Version 1 caches have no forecast. A damaged forecast must not hide current cached weather.
    val forecastWeather = forecastJson?.let {
        runCatching { OpenMeteoParser.parseWeather(it, latitude, longitude) }.getOrNull()
    }
    return WeatherSnapshot(
        temperatureC = temperatureC,
        apparentTemperatureC = apparentTemperatureC,
        humidityPercent = humidityPercent,
        windSpeedMetersPerSecond = windSpeedMetersPerSecond,
        weatherCode = weatherCode,
        kind = OpenMeteoParser.weatherKind(weatherCode),
        surfacePressureHpa = surfacePressureHpa,
        latitude = latitude,
        longitude = longitude,
        updatedAtMillis = updatedAtMillis,
        stale = stale,
        isDay = forecastWeather?.isDay ?: true,
        forecast = forecastWeather?.forecast,
    )
}

private fun TerrainElevation.toCache() = TerrainCacheEntity(
    latitude = latitude,
    longitude = longitude,
    elevationMeters = meters,
    updatedAtMillis = updatedAtMillis,
)

private fun TerrainCacheEntity.toModel(stale: Boolean) = TerrainElevation(
    meters = elevationMeters,
    latitude = latitude,
    longitude = longitude,
    updatedAtMillis = updatedAtMillis,
    stale = stale,
)
