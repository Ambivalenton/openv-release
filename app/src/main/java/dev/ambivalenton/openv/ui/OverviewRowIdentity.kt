// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui

/** Defensive rendering identity: even an old/in-flight batch with repeated IDs may
 * show all rows. Never key by destination or trip, which can legitimately repeat. */
internal fun overviewRowKeys(entries: List<OverviewEntry>): List<String> {
    val occurrences = mutableMapOf<String, Int>()
    return entries.map { entry ->
        val id = entry.favorite.id
        val occurrence = occurrences.getOrDefault(id, 0)
        occurrences[id] = occurrence + 1
        "overview-favorite:${id.length}:$id:$occurrence"
    }
}
