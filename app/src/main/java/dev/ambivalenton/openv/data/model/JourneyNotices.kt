// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import dev.ambivalenton.openv.localization.tr
import java.security.MessageDigest
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/*
 * 2.5: provider notices are scanned locally and deterministically with a fixed list of terms (no
 * language model, no external service). A hit explains a possible special case at the boarding or
 * alighting stop of one ride. It never decides that a journey is impossible: that needs a confirmed
 * service or call state (see JourneyFeasibility), and a hidden text cannot undo such a state.
 *
 * The providers deliver notices as plain text without stop references in this data model, so the
 * relation to a call is made by the stop name (city and generic names checked), by a platform named
 * in a ride's own notice, and by direction and date mentioned in the text.
 */

enum class NoticeEffect { STOP_MOVED, STOP_SKIPPED }
enum class NoticeCall { BOARDING, ALIGHTING }

/** One affected call: ride [segmentIndex], its boarding or alighting stop. */
data class NoticeTarget(val segmentIndex: Int, val call: NoticeCall, val stopName: String, val stopKey: String)

/**
 * A notice that names a call of this journey. [text] is the exact original text, [triggers] the
 * character ranges of the words that caused the hit; identical texts are merged into one hit.
 */
data class NoticeHit(val text: String, val triggers: List<IntRange>, val effect: NoticeEffect, val targets: List<NoticeTarget>,
    val provider: String) {
    /** Dismissal keys: exactly this text for this provider and stop. */
    val keys: List<String> get() = targets.map { noticeDismissKey(provider, it.stopKey, text) }.distinct()
    /** Short spoken and printed label, e.g. "Einstieg verlegt". */
    fun label(target: NoticeTarget = targets.first()): String = when (effect) {
        NoticeEffect.STOP_MOVED -> if (target.call == NoticeCall.BOARDING) tr("Einstieg verlegt") else tr("Ausstieg verlegt")
        NoticeEffect.STOP_SKIPPED -> if (target.call == NoticeCall.BOARDING) tr("Einstieg entfällt") else tr("Ausstieg entfällt")
    }
}

/** 2.5: the key under which a hidden notice is remembered (provider, stop, exact text). */
fun noticeDismissKey(provider: String, stopKey: String, text: String): String =
    MessageDigest.getInstance("SHA-256").digest("$provider\u0000$stopKey\u0000${text.trim()}".toByteArray())
        .take(16).joinToString("") { "%02x".format(it) }

private val movedTerms = listOf(
    "haltestelle wird verlegt", "haltestelle ist verlegt", "haltestelle verlegt", "haltestelle vorübergehend verlegt",
    "haltestelle wird vorübergehend verlegt", "haltestelle ist vorübergehend verlegt", "halt verlegt", "halt wird verlegt",
    "haltepunkt verlegt", "bahnsteig verlegt", "steig verlegt", "verlegte haltestelle", "verlegung der haltestelle",
    "haltestellenverlegung", "ersatzhaltestelle", "ersatz-haltestelle", "ersatzhalt", "provisorische haltestelle",
    "stop has been moved", "stop is moved", "stop moved", "stop has been relocated", "stop is relocated", "stop relocated",
    "relocated stop", "replacement stop", "temporary stop", "stop relocation",
)
private val skippedTerms = listOf(
    "halt entfällt", "halte entfallen", "halt fällt aus", "halte fallen aus", "entfällt der halt", "haltestelle entfällt",
    "wird nicht bedient", "werden nicht bedient", "nicht bedient", "wird nicht angefahren", "werden nicht angefahren",
    "nicht angefahren", "hält nicht in", "hält nicht an", "ohne halt in", "kein halt in", "kein halt an",
    "will not stop at", "will not call at", "does not stop at", "does not call at", "not served", "is not served",
    "stop is cancelled", "stop cancelled", "stop is canceled", "stop canceled", "no stop at", "skips",
)
/** Words that turn a hit into its opposite or into a mere possibility (checked in the same sentence). */
private val negations = listOf("nicht mehr", "nicht länger", "wieder", "kein ersatz", "entfällt nicht", "no longer", "again", "not be moved")
private val hypotheticals = listOf("kann ", "können ", "könnte", "eventuell", "evtl.", "möglicherweise", "ggf.", "gegebenenfalls",
    "unter umständen", "falls ", "may ", "might ", "possibly", "could ", "if ")
/** Stop names that need their city to be unambiguous. */
private val genericCores = setOf("hbf", "bf", "markt", "rathaus", "kirche", "post", "schule", "friedhof", "zentrum", "mitte",
    "bahnhof", "hauptbahnhof", "busbahnhof", "zob", "dorf", "ort")

private fun fold(text: String): String = Normalizer.normalize(text.lowercase(Locale.GERMAN), Normalizer.Form.NFC)
    .replace("ß", "ss").replace("ä", "ae").replace("ö", "oe").replace("ü", "ue")

/** Normalised words of a stop name or text: "Essen, Rahmstr." → [essen, rahmstr]; Straße → str, Hauptbahnhof → hbf. */
internal fun noticeWords(text: String): List<String> = fold(text).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }.map { w ->
    when {
        w == "hauptbahnhof" -> "hbf"
        w == "bahnhof" -> "bf"
        w.endsWith("strasse") && w.length > 7 -> w.removeSuffix("strasse") + "str"
        w == "strasse" -> "str"
        else -> w
    }
}

private data class StopName(val city: List<String>, val core: List<String>)
private fun stopName(location: Location): StopName {
    val name = location.name
    val parts = name.split(",", limit = 2)
    val explicitCity = location.city?.let(::noticeWords).orEmpty()
    return if (parts.size == 2) StopName(noticeWords(parts[0]).ifEmpty { explicitCity }, noticeWords(parts[1]))
    else StopName(explicitCity, noticeWords(name))
}

/** Index of [needle] as a whole-word sequence in [haystack], or -1. */
private fun indexOfWords(haystack: List<String>, needle: List<String>): Int {
    if (needle.isEmpty() || needle.size > haystack.size) return -1
    for (i in 0..haystack.size - needle.size) if ((needle.indices).all { haystack[i + it] == needle[it] }) return i
    return -1
}

/**
 * Whether [sentence] names [stop]: its core words, and for generic names also its city. A city named
 * right before the core ("Herne, Rathaus" / "in Herne") that is not the stop's city excludes it.
 */
private fun namesStop(sentence: String, stop: Location): Boolean {
    val words = noticeWords(sentence)
    val name = stopName(stop)
    val core = name.core.filterNot { it in setOf("str") && name.core.size > 1 }.ifEmpty { name.core }
    if (core.isEmpty() || core.joinToString("").length < 3) return false
    val at = indexOfWords(words, name.core).takeIf { it >= 0 } ?: indexOfWords(words, core)
    if (at < 0) return false
    val generic = core.size == 1 && core.single() in genericCores
    if (generic && name.city.isNotEmpty() && indexOfWords(words, name.city) < 0) return false
    // "X, Rathaus" or "Rathaus in X" with another city: a stop of the same name elsewhere.
    if (name.city.isNotEmpty()) {
        val before = words.getOrNull(at - 1)
        val afterIn = if (words.getOrNull(at + core.size) == "in") words.getOrNull(at + core.size + 1) else null
        val other = listOfNotNull(before?.takeIf { sentence.contains(",") && it.length > 2 && it !in setOf("haltestelle", "halt", "an", "in", "bei", "der", "die", "das") }, afterIn)
        if (other.any { it !in name.city }) return false
    } else {
        val afterIn = if (words.getOrNull(at + core.size) == "in") words.getOrNull(at + core.size + 1) else null
        if (afterIn != null && stop.city != null && afterIn !in noticeWords(stop.city)) return false
    }
    return true
}

/** "Richtung X" in the text must agree with the ride's direction. */
private fun directionAgrees(sentence: String, leg: TripSegment): Boolean {
    val words = noticeWords(sentence)
    val i = words.indexOfFirst { it == "richtung" || it == "towards" || it == "direction" }
    if (i < 0) return true
    val named = words.drop(i + 1).take(2)
    val direction = leg.direction?.let(::noticeWords).orEmpty() + noticeWords(leg.destination.name)
    return named.isEmpty() || named.any { it in direction }
}

private val dateRegex = Regex("(\\d{1,2})\\.(\\d{1,2})\\.(\\d{2,4})?")
/** Dates in the text (single days or "vom … bis …") must include the ride's day. */
private fun dateAgrees(sentence: String, day: LocalDate): Boolean {
    val dates = dateRegex.findAll(sentence).mapNotNull { m ->
        val d = m.groupValues[1].toInt(); val mo = m.groupValues[2].toInt()
        val y = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()?.let { if (it < 100) 2000 + it else it } ?: day.year
        runCatching { LocalDate.of(y, mo, d) }.getOrNull()
    }.toList()
    if (dates.isEmpty()) return true
    val folded = fold(sentence)
    return if (dates.size >= 2 && (folded.contains(" bis ") || folded.contains(" until ") || folded.contains("–") || folded.contains(" - ")))
        !day.isBefore(dates.min()) && !day.isAfter(dates.max())
    else day in dates
}

private fun sentences(text: String): List<IntRange> {
    val out = mutableListOf<IntRange>(); var start = 0
    for (i in text.indices) if ((text[i] == '.' || text[i] == '!' || text[i] == '?' || text[i] == ';') &&
        (i + 1 >= text.length || text[i + 1].isWhitespace()) && !(i > 0 && text[i - 1].isDigit())) {
        out += start..i; start = i + 1
    }
    if (start < text.length) out += start until text.length
    return out.filter { !text.substring(it).isBlank() }
}

/** Trigger ranges of [effect] in [sentence] (absolute in the notice via [offset]), negation and possibility removed. */
private fun triggers(text: String, sentence: IntRange, terms: List<String>): List<IntRange> {
    val raw = text.substring(sentence)
    val lower = raw.lowercase(Locale.GERMAN)
    if (hypotheticals.any { lower.contains(it) }) return emptyList()
    val found = mutableListOf<IntRange>()
    for (term in terms.sortedByDescending { it.length }) {
        var from = 0
        while (true) {
            val at = lower.indexOf(term, from); if (at < 0) break
            val range = at until at + term.length
            from = at + term.length
            if (found.any { it.first <= range.last && range.first <= it.last }) continue
            // Word boundaries keep "Ersatzhaltestellen-Plan" out of "Halt …" and similar.
            if (at > 0 && lower[at - 1].isLetter()) continue
            val before = lower.substring((at - 24).coerceAtLeast(0), at)
            val after = lower.substring(range.last + 1, (range.last + 14).coerceAtMost(lower.length))
            // "nicht mehr verlegt", "wird wieder angefahren", "entfällt nicht": the opposite of a hit.
            if (negations.any { before.contains(it) }) continue
            val next = after.trimStart()
            if (next == "nicht" || next.startsWith("nicht ") || next.startsWith("nicht,") || next.startsWith("nicht.")) continue
            found += (sentence.first + range.first)..(sentence.first + range.last)
        }
    }
    return found.sortedBy { it.first }
}

/**
 * 2.5: the notices of [trip] that name one of its boarding or alighting stops, without those hidden
 * via [dismissed] (keys of [noticeDismissKey]). Identical texts are merged; a changed text or another
 * stop is judged again. Pure and deterministic.
 */
fun scanJourneyNotices(trip: Trip, dismissed: Set<String> = emptySet(), zone: ZoneId = transitZone): List<NoticeHit> {
    data class Source(val text: String, val leg: Int?)
    val sources = trip.segments.flatMapIndexed { index, leg -> if (leg.isWalk) emptyList() else leg.notices.map { Source(it, index) } } +
        trip.notices.map { Source(it, null) }
    val rides = trip.segments.withIndex().filter { !it.value.isWalk }
    val hits = linkedMapOf<String, NoticeHit>()
    for (source in sources) {
        val text = source.text.trim().takeIf { it.isNotEmpty() } ?: continue
        val targets = linkedSetOf<NoticeTarget>(); val ranges = mutableListOf<IntRange>(); var effect: NoticeEffect? = null
        for (sentence in sentences(text)) {
            val part = text.substring(sentence)
            for ((terms, kind) in listOf(movedTerms to NoticeEffect.STOP_MOVED, skippedTerms to NoticeEffect.STOP_SKIPPED)) {
                val found = triggers(text, sentence, terms)
                if (found.isEmpty()) continue
                val candidates = if (source.leg != null) rides.filter { it.index == source.leg } else rides
                var matched = false
                for ((index, leg) in candidates) {
                    val day = Instant.ofEpochMilli(leg.plannedStartTime).atZone(zone).toLocalDate()
                    if (!directionAgrees(part, leg) || !dateAgrees(part, day)) continue
                    fun platformNamed(platform: String?) = source.leg == index && platform != null &&
                        Regex("(gleis|steig|bussteig|bahnsteig|platform|stand)\\s*${Regex.escape(platform.lowercase(Locale.GERMAN))}\\b").containsMatchIn(part.lowercase(Locale.GERMAN))
                    if (namesStop(part, leg.origin) || platformNamed(leg.departurePlatform)) {
                        targets += NoticeTarget(index, NoticeCall.BOARDING, leg.origin.name, stopKey(leg.origin)); matched = true
                    }
                    if (namesStop(part, leg.destination) || platformNamed(leg.arrivalPlatform)) {
                        targets += NoticeTarget(index, NoticeCall.ALIGHTING, leg.destination.name, stopKey(leg.destination)); matched = true
                    }
                }
                if (matched) { ranges += found; effect = effect ?: kind }
            }
        }
        // A transfer stop is the alighting of one ride and the boarding of the next: both are affected.
        val withTransfers = targets.toMutableSet()
        for (t in targets) {
            val nextRide = rides.firstOrNull { it.index > t.segmentIndex }
            val previousRide = rides.lastOrNull { it.index < t.segmentIndex }
            if (t.call == NoticeCall.ALIGHTING && nextRide != null && stopKey(nextRide.value.origin) == t.stopKey)
                withTransfers += NoticeTarget(nextRide.index, NoticeCall.BOARDING, nextRide.value.origin.name, t.stopKey)
            if (t.call == NoticeCall.BOARDING && previousRide != null && stopKey(previousRide.value.destination) == t.stopKey)
                withTransfers += NoticeTarget(previousRide.index, NoticeCall.ALIGHTING, previousRide.value.destination.name, t.stopKey)
        }
        val kind = effect ?: continue
        if (withTransfers.isEmpty()) continue
        val visible = withTransfers.filter { noticeDismissKey(trip.source, it.stopKey, text) !in dismissed }
        if (visible.isEmpty()) continue
        val existing = hits[text]
        hits[text] = if (existing == null) NoticeHit(text, ranges.distinct().sortedBy { it.first }, kind, visible.sortedWith(compareBy({ it.segmentIndex }, { it.call })), trip.source)
            else existing.copy(targets = (existing.targets + visible).distinct().sortedWith(compareBy({ it.segmentIndex }, { it.call })))
    }
    return hits.values.toList()
}

/** Stable key of a stop for notices: provider id where known, else its normalised name. */
internal fun stopKey(location: Location): String =
    location.id?.takeIf { it.isNotBlank() }?.let { "${location.provider.orEmpty()}:$it" } ?: "name:" + noticeWords(location.name).joinToString(" ")

/** 2.5: rides of [trip] whose bar gets the notice mark, with the spoken label. */
fun noticeMarks(hits: List<NoticeHit>): Map<Int, String> =
    hits.flatMap { hit -> hit.targets.map { it.segmentIndex to hit.label(it) } }.groupBy({ it.first }, { it.second })
        .mapValues { (_, labels) -> labels.distinct().joinToString(", ") }
