// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import dev.ambivalenton.openv.localization.serviceLanguage

val transitQuotes: List<String> get() = journeyContentCatalog
    .filter { it.kind == JourneyContentKind.THOUGHT }.map { it.text }
val transitFacts: List<String> get() = transitFactCatalog.map { it.text }
val transitHeaderItems: List<String> get() = journeyContentCatalog.map { it.text }

/** A shuffle bag exhausts the catalog before repeating; even cycle boundaries cannot echo. */
class TransitHeaderDeck(
    private val items: List<String> = transitHeaderItems,
    private val random: kotlin.random.Random = kotlin.random.Random.Default,
) {
    private val remaining = ArrayDeque<String>()
    private var last: String? = null

    init { require(items.isNotEmpty() && items.distinct().size == items.size) }

    @Synchronized
    fun next(): String {
        if (remaining.isEmpty()) {
            val shuffled = items.shuffled(random).toMutableList()
            if (shuffled.size > 1 && shuffled.first() == last) {
                val swap = random.nextInt(1, shuffled.size)
                val first = shuffled[0]
                shuffled[0] = shuffled[swap]
                shuffled[swap] = first
            }
            remaining.addAll(shuffled)
        }
        return remaining.removeFirst().also { last = it }
    }
}

/** Shared across screen recreation; no timer, network access or recomposition-driven rotation. */
object TransitHeaderRotation {
    private val decks = mutableMapOf<String, TransitHeaderDeck>()
    @Synchronized
    fun next(): String = decks.getOrPut(serviceLanguage()) { TransitHeaderDeck() }.next()
}
