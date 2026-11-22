// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import dev.ambivalenton.openv.localization.serviceLanguage
import dev.ambivalenton.openv.localization.tr
import kotlinx.serialization.Serializable

/** Facts are sourced; original writing is never attributed to a real historical author. */
@kotlinx.serialization.Serializable
enum class JourneyContentKind {
    FACT, THOUGHT, HAIKU, SATIRE, TIP;
    val label: String get() = when (this) {
        FACT -> tr("Fakt")
        THOUGHT -> tr("Gedanke für unterwegs")
        HAIKU -> tr("Freies Reisehaiku")
        SATIRE -> tr("Bahn-Augenzwinkern")
        TIP -> tr("Bedienungstipp")
    }
}

data class JourneyContent(
    val id: String,
    val kind: JourneyContentKind,
    val german: String,
    val english: String,
    val sourceUrl: String?,
    val author: String?,
    val verifiedOn: String?,
) {
    val text: String get() = if (serviceLanguage() == "de") german else english
}

/** Generated at authoring time: one immutable pool; no assets, parsing, I/O or network on screen. */
val journeyContentCatalog: List<JourneyContent> get() = generatedJourneyContent

@Serializable
data class TransitFact(val id: String, val category: String, val text: String, val sourceUrl: String)

val transitFactCatalog: List<TransitFact> get() = journeyContentCatalog
    .filter { it.kind == JourneyContentKind.FACT }
    .map { TransitFact(it.id, it.kind.label, it.text, requireNotNull(it.sourceUrl)) }
