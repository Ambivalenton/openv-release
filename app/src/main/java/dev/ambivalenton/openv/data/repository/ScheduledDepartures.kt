// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.repository

import dev.ambivalenton.openv.data.model.Departure

/** The same service at the same stop in two answers (live board and timetable). */
internal fun sameDepartureService(a: Departure, b: Departure): Boolean =
    boardLine(a.lineName) == boardLine(b.lineName) && Math.abs(a.plannedTime - b.plannedTime) < 60_000

/**
 * Build 129 · without a live answer the board shows what is known: the last live rows that are still
 * ahead (each with its own stand) and, after them, the person's local timetable ("Nur Fahrplan").
 * Within the span of the live rows the timetable adds nothing: a service missing there was not on the
 * live board, which is no reason to show it as running.
 */
internal fun mergeScheduledDepartures(live: List<Departure>, planned: List<Departure>, now: Long, limit: Int = 30): List<Departure> {
    val ahead = live.filter { it.time >= now - 60_000 }
    val horizon = ahead.maxOfOrNull { it.plannedTime } ?: Long.MIN_VALUE
    val rest = planned.filter { row -> row.time >= now - 60_000 && row.plannedTime > horizon && ahead.none { sameDepartureService(it, row) } }
    return (ahead + rest).sortedBy { it.time }.distinctBy { it.id }.take(limit)
}
