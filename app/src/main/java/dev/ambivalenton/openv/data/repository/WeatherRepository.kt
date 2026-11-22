// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.data.repository

import dev.ambivalenton.openv.data.model.ArrivalWeather
import dev.ambivalenton.openv.data.model.Location
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.math.round

fun interface ArrivalWeatherSource {
    suspend fun atArrival(destination: Location, arrival: Long): ArrivalWeather?
}

/** Bounded, per-coordinate cache. No device position or place names are sent to Open-Meteo. */
class WeatherRepository(
    private val network: NetworkClient,
    private val base: HttpUrl = "https://api.open-meteo.com/v1/forecast".toHttpUrl(),
    private val now: () -> Long = System::currentTimeMillis,
) : ArrivalWeatherSource {
    private data class Cached(val data: JsonElement?, val until: Long)
    private val cache = LinkedHashMap<String, Cached>(48, .75f, true)
    private val locks = List(16) { Mutex() }
    private val slots = Semaphore(3)

    override suspend fun atArrival(destination: Location, arrival: Long): ArrivalWeather? {
        val timestamp = now()
        if (!destination.hasCoordinates || arrival !in timestamp - 3_600_000..timestamp + 15 * 86_400_000L)
            return null
        val lat = round(destination.latitude!! * 1000) / 1000
        val lon = round(destination.longitude!! * 1000) / 1000
        val key = "$lat:$lon"
        return locks[Math.floorMod(key.hashCode(), locks.size)].withLock {
            val cached = synchronized(cache) { cache[key]?.takeIf { now() < it.until } }
            val data = if (cached != null) cached.data else {
                val response = try {
                    slots.withPermit {
                        network.get(base.newBuilder()
                            .addQueryParameter("latitude", lat.toString())
                            .addQueryParameter("longitude", lon.toString())
                            .addQueryParameter("hourly", "temperature_2m,apparent_temperature,weather_code,precipitation_probability,precipitation,wind_speed_10m,is_day")
                            .addQueryParameter("timezone", "auto")
                            .addQueryParameter("timeformat", "unixtime")
                            .addQueryParameter("forecast_days", "16")
                            .addQueryParameter("temperature_unit", "celsius")
                            .addQueryParameter("wind_speed_unit", "kmh")
                            .build())
                    }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { null }
                synchronized(cache) {
                    cache[key] = Cached(response, now() + if (response == null) 120_000 else 30 * 60_000)
                    while (cache.size > 48) cache.remove(cache.keys.first())
                }
                response
            }
            data?.let { parseArrivalWeather(it, arrival) }
        }
    }
}

/** Missing/out-of-range hourly cells never become made-up sunshine or a 0 °C forecast. */
internal fun parseArrivalWeather(root: JsonElement, arrival: Long): ArrivalWeather? {
    val hourly = root.field("hourly") ?: return null
    val times = hourly.field("time").elements()
    val index = times.indexOfLast { value ->
        value.text()?.toLongOrNull()?.let { it <= arrival / 1000 } == true
    }
    if (index < 0) return null
    val hour = times[index].text()?.toLongOrNull()?.times(1000) ?: return null
    if (arrival - hour !in 0..3_599_999) return null
    fun cell(name: String) = hourly.field(name).elements().getOrNull(index).text()?.toDoubleOrNull()?.takeIf { it.isFinite() }
    val temperature = cell("temperature_2m")?.takeIf { it in -100.0..70.0 } ?: return null
    val code = cell("weather_code")?.takeIf { it % 1.0 == 0.0 }?.toInt() ?: return null
    if (code !in setOf(0, 1, 2, 3, 45, 48, 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 71, 73, 75, 77, 80, 81, 82, 85, 86, 95, 96, 99)) return null
    return ArrivalWeather(
        hour, temperature,
        cell("apparent_temperature")?.takeIf { it in -120.0..90.0 } ?: temperature,
        code,
        cell("precipitation_probability")?.takeIf { it in 0.0..100.0 }?.toInt(),
        cell("precipitation")?.takeIf { it >= 0 },
        cell("wind_speed_10m")?.takeIf { it >= 0 },
        cell("is_day") == 1.0,
        root.text("timezone") ?: "UTC",
    )
}
