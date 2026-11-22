// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.data.model

import dev.ambivalenton.openv.localization.tr

import kotlin.random.Random

val arrivalMessages: List<String>
    get() =
    listOf(
        tr("Navigation beendet: Blick nach unten – du bist da! 📍"),
        tr("Diese Verbindung fährt von hier nach genau hier. Heute sogar pünktlich! 🚉"),
        tr("Ziel erreicht. Die nächste Verspätung musst du dir selbst organisieren. ⏱️"),
        tr("Null Haltestellen, null Umstiege. Dein effizientester Arbeitsweg bisher! 🎯"),
        tr("Du bist schon da. Für eine Rundfahrt reicht einmal um die eigene Achse. 🔄"),
        tr("Der Anschluss wartet bereits: direkt unter deinen Schuhen. 👟"),
        tr("Dein Schrittzähler hat soeben einen Antrag auf Kurzarbeit gestellt. 🚶"),
        tr("Gute Nachricht: Diese Fahrt fällt aus. Du hast dein Ziel schon erreicht! 🎉"),
    )

/** Shuffle without repeats until every message has appeared, including the bag boundary. */
class ArrivalHumorPicker(private val random: Random = Random.Default) {
    private var remaining = mutableListOf<Int>()
    private var last = -1

    fun next(): Int {
        if (remaining.isEmpty()) {
            remaining = arrivalMessages.indices.shuffled(random).toMutableList()
            if (remaining.size > 1 && remaining.last() == last) {
                val temp = remaining[0]
                remaining[0] = remaining.last()
                remaining[remaining.lastIndex] = temp
            }
        }
        return remaining.removeAt(remaining.lastIndex).also { last = it }
    }
}

/** Address centroids may differ slightly from a measured position; never widen station matching. */
fun routingSameLocation(
    origin: Location,
    destination: Location,
    presence: DevicePresence?,
): Boolean {
    if (sameLocation(origin, destination)) return true
    if (
        destination.kind !in setOf(LocationKind.ADDRESS, LocationKind.POI) &&
            origin.kind !in setOf(LocationKind.ADDRESS, LocationKind.POI)
    )
        return false
    if (
        presence == null ||
            !presence.accuracyMeters.isFinite() ||
            presence.accuracyMeters !in 0f..80f
    )
        return false
    if (System.currentTimeMillis() - presence.capturedAt !in 0..PRESENCE_MAX_AGE_MILLIS)
        return false
    val tolerance = (presence.accuracyMeters * 1.5 + 35).coerceIn(50.0, 100.0)
    return distanceMeters(origin, destination)?.let { it < tolerance } == true
}
