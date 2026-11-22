// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.data.repository

import dev.ambivalenton.openv.data.model.*

class TransitProviderFactory(private val network: NetworkClient) {
    private val providers =
        object : LinkedHashMap<String, TransitRepository>(4, .75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, TransitRepository>?
            ) = size > 4
        }

    @Synchronized
    fun get(settings: AppSettings): TransitRepository {
        // The routing preferences belong to the key; the wrapper reads them when it is built.
        val key = "${settings.efaEndpoint}:${settings.walkingSpeed}:${settings.ignoreWalking}:${settings.overview.routingPreferences.routingIdentity()}"
        val base = providers.getOrPut(key) {
            val base = EfaTransitRepository(network, httpsBase(settings.efaEndpoint))
            WaypointTransitRepository(RoutingPreferencesRepository(base,settings))
        }
        return base
    }
}
