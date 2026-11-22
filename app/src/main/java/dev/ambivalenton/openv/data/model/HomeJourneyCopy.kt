// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import dev.ambivalenton.openv.localization.tr
import java.text.Normalizer
import java.util.Locale

internal fun isGenericHomeLabel(label: String): Boolean =
    Normalizer.normalize(label.trim(), Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).replace(Regex("\\s+"), " ") in
        setOf("zuhause", "zu hause", "hause", "daheim", "home")

/** A whole grammatical clause, rather than a preposition blindly joined to a label. */
fun homeJourneyIntroduction(favorite: Favorite, variant: Int): List<OverviewTextPart> = buildList {
    fun text(s: String) { add(OverviewTextPart(s)) }
    fun destination(s: String) { add(OverviewTextPart(s, OverviewEmphasis.PLACE)) }
    val v = Math.floorMod(variant, 3)
    if (isGenericHomeLabel(favorite.label)) {
        text(when (v) {
            0 -> tr("Dein Weg nach ")
            1 -> tr("Deine Heimfahrt nach ")
            else -> tr("Auf dem Heimweg nach ")
        })
        destination(tr("Hause"))
    } else when (v) {
        0 -> { text(tr("Dein Weg nach Hause führt dich zum Ziel ")); destination(favorite.label.trim()) }
        1 -> { text(tr("Deine Fahrt nach Hause (")); destination(favorite.label.trim()); text(")") }
        else -> { text(tr("Deine Heimfahrt führt dich zum Ziel ")); destination(favorite.label.trim()) }
    }
}
