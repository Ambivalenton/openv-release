// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.data.repository

import dev.ambivalenton.openv.data.model.TimetableProfile

import dev.ambivalenton.openv.localization.tr

import dev.ambivalenton.openv.BuildConfig
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Live tracking validates HTTP caches with the server, while ordinary lookups keep their cache. */
internal object FreshTransitResponse : AbstractCoroutineContextElement(Key) {
    object Key : CoroutineContext.Key<FreshTransitResponse>
}

/** Per user operation, not an alleged provider quota. It counts real shared HTTP requests. */
internal class TransitRequestBudget(limit: Int) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<TransitRequestBudget>
    private val remaining = AtomicInteger(limit.coerceAtLeast(1))
    fun consume() {
        if (remaining.decrementAndGet() < 0)
            throw TransitException(tr("Weitere Verbindungen bitte bei Bedarf nachladen."))
    }
}

/** Request context for a single transit operation. */
internal fun transitRequestBudget(limit: Int): CoroutineContext =
    TimetableProfile.requestContext(limit)

/** Respect delta-seconds and HTTP dates without shortening a long provider pause or overflowing. */
internal fun retryAfterUntil(value: String?, now: Long): Long {
    val seconds = value?.trim()?.toLongOrNull()?.takeIf { it >= 0 }
    if (seconds != null) {
        val millis = if (seconds > Long.MAX_VALUE / 1000) Long.MAX_VALUE else seconds * 1000
        return if (millis > Long.MAX_VALUE - now.coerceAtLeast(0)) Long.MAX_VALUE else now + millis
    }
    val date = value?.let { runCatching {
        ZonedDateTime.parse(it.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
    }.getOrNull() }
    return date?.takeIf { it > now } ?: (now + 30_000)
}

/** Bounded and cancellable transport, shared across geocoding and EFA. */
class NetworkClient(val client: OkHttpClient, private val online: () -> Boolean? = { null }) {
    private val slots = Semaphore(3)
    private val blockedHosts = ConcurrentHashMap<String, Long>()
    private val requestScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private data class Key(val url: HttpUrl, val headers: Map<String,String>, val fresh: Boolean)
    private data class Shared(val response: Deferred<JsonElement>, var readers: Int = 0)
    private val inFlight = mutableMapOf<Key, Shared>()
    private val inFlightLock = Mutex()

    suspend fun get(url: HttpUrl, headers: Map<String,String> = emptyMap()): JsonElement {
        val fresh = currentCoroutineContext()[FreshTransitResponse.Key] != null
        val budget = currentCoroutineContext()[TransitRequestBudget]
        val key = Key(url, headers.toSortedMap(), fresh)
        val shared = inFlightLock.withLock {
            inFlight.getOrPut(key) {
                Shared(requestScope.async(start = CoroutineStart.LAZY) { execute(url, headers, fresh, budget) })
            }.also { it.readers++; it.response.start() }
        }
        try { return shared.response.await() }
        finally { withContext(NonCancellable) {
            inFlightLock.withLock {
                shared.readers--
                if (shared.readers == 0) {
                    if (inFlight[key] === shared) inFlight.remove(key)
                    if (!shared.response.isCompleted) shared.response.cancel()
                }
            }
        } }
    }

    private suspend fun execute(url: HttpUrl, headers: Map<String,String>, fresh: Boolean,
        budget: TransitRequestBudget?): JsonElement =
        slots.withPermit {
            val pause = blockedHosts[url.host] ?: 0
            if (System.currentTimeMillis() < pause)
                throw TransitException(tr("Der Anbieter begrenzt Anfragen. Bitte kurz warten."), FailureKind.RATE_LIMITED, pause)
            budget?.consume()
            val request =
                Request.Builder()
                    .url(url)
                    .header("User-Agent", userAgent)
                    .header("Accept", "application/json")
                    .apply {
                        headers.forEach { (name,value) -> header(name,value) }
                        if (fresh)
                            cacheControl(CacheControl.FORCE_NETWORK)
                    }
                    .build()
            val text =
                suspendCancellableCoroutine<String> { continuation ->
                    val call = client.newCall(request)
                    continuation.invokeOnCancellation { call.cancel() }
                    call.enqueue(
                        object : Callback {
                            override fun onFailure(call: Call, e: IOException) {
                                if (continuation.isActive)
                                    continuation.resumeWithException(
                                        TransitException(
                                            tr("Dienst nicht erreichbar. Internetverbindung prüfen und erneut versuchen."),
                                            transportFailureKind(e, runCatching { online() }.getOrNull()),
                                            cause = e,
                                        )
                                    )
                            }

                            override fun onResponse(call: Call, response: Response) {
                                response.use {
                                    if (!continuation.isActive) return
                                    try {
                                        if (response.code == 429 || response.code == 503 && response.header("Retry-After") != null) {
                                            val instant = System.currentTimeMillis()
                                            val until = retryAfterUntil(response.header("Retry-After"), instant)
                                            blockedHosts.merge(url.host, until) { a, b -> maxOf(a, b) }
                                            val wait = ((until-instant).coerceAtLeast(0) / 1000).coerceAtLeast(1)
                                            throw TransitException(
                                                tr("Anfragelimit erreicht. Bitte in %1\$s Sekunden erneut versuchen.", wait),
                                                if (response.code == 429) FailureKind.RATE_LIMITED else FailureKind.UNAVAILABLE,
                                                until,
                                            )
                                        }
                                        if (!response.isSuccessful)
                                            throw TransitException(
                                                tr("Dienst vorübergehend nicht verfügbar (HTTP %1\$s).", response.code),
                                                httpFailureKind(response.code),
                                            )
                                        val source =
                                            response.body?.source()
                                                ?: throw TransitException(tr("Leere Serverantwort."), FailureKind.INVALID_RESPONSE)
                                        if (source.request(4_000_001))
                                            throw TransitException(tr("Die Serverantwort ist zu groß."), FailureKind.INVALID_RESPONSE)
                                        continuation.resume(source.readUtf8())
                                    } catch (e: Exception) {
                                        if (continuation.isActive)
                                            continuation.resumeWithException(e)
                                    }
                                }
                            }
                        }
                    )
                }
            try {
                withContext(Dispatchers.Default) { Json.parseToJsonElement(text) }
            } catch (e: IllegalArgumentException) {
                throw TransitException(tr("Der Dienst hat ungültige Daten geliefert."), FailureKind.INVALID_RESPONSE)
            }
        }

    companion object {
        val userAgent = "OpenV/${BuildConfig.VERSION_NAME} (Android; ${BuildConfig.APPLICATION_ID}; contact: oepenvapp@gmail.com)"
    }
}

internal fun httpsBase(value: String): HttpUrl =
    (value.trim().trimEnd('/') + "/").toHttpUrl().also {
        require(
            it.isHttps &&
                it.username.isBlank() &&
                it.password.isBlank() &&
                it.query == null &&
                it.fragment == null
        ) {
            tr("Eine HTTPS-Adresse ohne Zugangsdaten, Query oder Fragment eingeben.")
        }
    }

internal fun JsonElement?.elements(): List<JsonElement> =
    when (this) {
        is JsonArray -> toList()
        is JsonObject -> listOf(this)
        else -> emptyList()
    }

internal fun JsonElement?.field(key: String): JsonElement? = (this as? JsonObject)?.get(key)

internal fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.contentOrNull

internal fun JsonElement?.text(key: String): String? = field(key).text()?.takeIf { it.isNotBlank() }

internal fun JsonElement?.number(key: String): Int? = text(key)?.toIntOrNull()

