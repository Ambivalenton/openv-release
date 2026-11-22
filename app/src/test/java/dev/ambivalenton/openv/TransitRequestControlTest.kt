// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import dev.ambivalenton.openv.data.repository.*
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class TransitRequestControlTest {
    @Test fun identicalRequestsShareTheTransportEvenWhenOneReaderCancels() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            server.enqueue(MockResponse().setBody("{\"value\":1}").setBodyDelay(200, TimeUnit.MILLISECONDS))
            val network = NetworkClient(OkHttpClient())
            val first = async(start = CoroutineStart.UNDISPATCHED) { network.get(server.url("/same")) }
            val second = async(start = CoroutineStart.UNDISPATCHED) { network.get(server.url("/same")) }
            first.cancelAndJoin()
            assertEquals("{\"value\":1}", second.await().toString())
            assertEquals(1, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun aUserOperationStopsBeforeItsBudgetWouldSendAnotherRequest() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            server.enqueue(MockResponse().setBody("{}"))
            val network = NetworkClient(OkHttpClient())
            val result = runCatching { withContext(TransitRequestBudget(1)) {
                network.get(server.url("/first")); network.get(server.url("/second"))
            } }
            assertTrue(result.exceptionOrNull() is TransitException)
            assertEquals(1, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun concurrentRoutesShareOneOperationBudget() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            repeat(2) { server.enqueue(MockResponse().setBody("{}")) }
            val network = NetworkClient(OkHttpClient())
            val budget = TransitRequestBudget(2)
            val results = (1..8).map { index -> async(budget) {
                runCatching { network.get(server.url("/route/$index")) }
            } }.awaitAll()
            assertEquals(2, results.count { it.isSuccess })
            assertEquals(2, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun retryAfterSupportsDatesLongPausesAndMalformedHeaders() {
        val now = 1_800_000_000_000L
        assertEquals(now + 3_600_000, retryAfterUntil("3600", now))
        val date = DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.ofEpochMilli(now + 120_000).atZone(ZoneOffset.UTC))
        assertEquals(now + 120_000, retryAfterUntil(date, now))
        assertEquals(now + 30_000, retryAfterUntil("invalid", now))
        assertEquals(Long.MAX_VALUE, retryAfterUntil(Long.MAX_VALUE.toString(), now))
    }

    @Test fun providerPauseBlocksTheNextRequestWithoutAnAutomaticRetry() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(429).addHeader("Retry-After", "3600"))
            val network = NetworkClient(OkHttpClient())
            assertTrue(runCatching { network.get(server.url("/first")) }.isFailure)
            assertTrue(runCatching { network.get(server.url("/next")) }.isFailure)
            assertEquals(1, server.requestCount)
        } finally { server.shutdown() }
    }
}
