// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import dev.ambivalenton.openv.data.repository.FreshTransitResponse
import dev.ambivalenton.openv.data.repository.NetworkClient
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class PinnedNetworkRefreshTest {
    @Test fun liveTrackingContactsTheServerEvenWhenAnOlderResponseHasAnUnexpiredCache() = runBlocking {
        val directory = Files.createTempDirectory("pinned-http-test").toFile()
        val cache = Cache(directory, 1024 * 1024)
        val server = MockWebServer()
        server.start()
        try {
            val network = NetworkClient(OkHttpClient.Builder().cache(cache).build())
            server.enqueue(MockResponse().addHeader("Cache-Control", "public, max-age=3600")
                .addHeader("Content-Type", "application/json").setBody("{\"delay\":0}"))
            server.enqueue(MockResponse().addHeader("Content-Type", "application/json").setBody("{\"delay\":12}"))
            val first = network.get(server.url("/journey"))
            assertEquals(first, network.get(server.url("/journey")))
            assertEquals(1, server.requestCount)
            val live = withContext(FreshTransitResponse) { network.get(server.url("/journey")) }
            assertTrue(live.toString().contains("12"))
            assertEquals(2, server.requestCount)
        } finally {
            server.shutdown()
            cache.close()
            directory.deleteRecursively()
        }
    }
}
