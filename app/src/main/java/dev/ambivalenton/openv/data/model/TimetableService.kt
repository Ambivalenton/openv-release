// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import dev.ambivalenton.openv.BuildConfig

/** Wire format of a timetable backend. */
enum class TimetableProtocol {
    /** Mentz EFA with JSON output (StopFinder, Trip, DM, AddInfo), as offered by VRR OpenService. */
    EFA_JSON,
    /** VDV 431 TRIAS (XML). Prepared; the adapter follows once an access with this interface exists. */
    TRIAS;

    companion object {
        /** Value of the Gradle property `openv.timetable.protocol`: "efa" or "trias". */
        fun of(property: String) = if (property == "trias") TRIAS else EFA_JSON
    }
}

/**
 * One timetable backend Öpen-V can be built for. A build talks to exactly one service, chosen with
 * the Gradle property `openv.timetable` (see docs/timetable-services.md). Everything the app shows
 * about its data source – name, attribution, coverage and the terms link – comes from here.
 */
data class TimetableService(
    val id: String,
    val engine: Engine,
    val name: String,
    val shortName: String,
    /** Visible source notice in notifications and widgets. */
    val attribution: String,
    val coverage: String,
    val protocol: TimetableProtocol,
    val endpoint: String,
    /** Terms of use and attribution page of the data provider. */
    val sourceUrl: String,
    /** Upper bound of requests one search may spend on paging. */
    val pagingRequests: Int,
)

object TimetableServices {
    val vrr = TimetableService(
        id = "vrr",
        engine = Engine.VRR,
        name = "VRR / EFA · Rhein-Ruhr",
        shortName = "VRR",
        attribution = "Fahrplandaten: VRR",
        coverage = "VRR OpenService · Rhein-Ruhr · Echtzeit, soweit vom Betreiber verfügbar.",
        protocol = TimetableProtocol.EFA_JSON,
        endpoint = "https://openservice.vrr.de/openservice/",
        sourceUrl = "https://www.opendata-oepnv.de/ht/de/api",
        pagingRequests = 24,
    )

    /** Germany-wide journey planning. Not active; endpoint and interface come from the granted access. */
    val delfi = TimetableService(
        id = "delfi",
        engine = Engine.DELFI,
        name = "DELFI-Navi · Deutschland",
        shortName = "DELFI",
        attribution = "Fahrplandaten: DELFI",
        coverage = "DELFI-Navi · Deutschland · Echtzeit, soweit von den Betreibern verfügbar.",
        protocol = TimetableProtocol.of(BuildConfig.TIMETABLE_PROTOCOL),
        endpoint = BuildConfig.TIMETABLE_ENDPOINT,
        sourceUrl = "https://www.delfi.de/",
        pagingRequests = 24,
    )

    val all = listOf(vrr, delfi)

    fun byId(id: String): TimetableService = all.firstOrNull { it.id == id } ?: vrr

    fun of(engine: Engine): TimetableService = all.first { it.engine == engine }

    /** The service this build talks to. */
    val active: TimetableService = byId(BuildConfig.TIMETABLE_SERVICE)
}
