// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.data.model

import dev.ambivalenton.openv.localization.serviceLanguage
import dev.ambivalenton.openv.localization.tr

import kotlin.math.roundToLong
import kotlinx.serialization.Serializable

@Serializable
data class OverviewSettings(
    val showPlatforms: Boolean = false,
    val showDuration: Boolean = false,
    val showTransport: Boolean = false,
    val bufferMinutes: Float = 0f,
    val showDepartureTime: Boolean = true,
    val showArrivalTime: Boolean = true,
    val showOriginStop: Boolean = true,
    val showDestination: Boolean = true,
    val showWaitTimes: Boolean = false,
    val showWalks: Boolean = false,
    val showTransfers: Boolean = false,
    val showQuotes: Boolean = true,
    val showWeather: Boolean = false,
    val showLineNames: Boolean = false,
    val routingPreferences: OverviewRoutingPreferences = OverviewRoutingPreferences(),
    val cards:Boolean=false,
    val quoteDecoration:QuoteDecoration=QuoteDecoration.PLAIN,
)

const val OVERVIEW_NEARBY_METERS = FAVORITE_HIDE_RADIUS_METERS

/** The lead time may be negative (connections due up to 15 minutes ago, e.g. when already at the stop). */
val BUFFER_MINUTES_RANGE = -15f..30f

fun overviewDepartureTime(now: Long, bufferMinutes: Float): Long =
    now +
        ((if (bufferMinutes.isFinite()) bufferMinutes.coerceIn(BUFFER_MINUTES_RANGE) else 0f).toDouble() *
                60_000)
            .roundToLong()

/** Only a fresh actual device measurement can hide a destination, never a manual origin. */
fun isAtOverviewDestination(destination: Location, presence: DevicePresence?, now: Long): Boolean =
    presence?.isFresh(now) == true &&
        distanceMeters(destination, presence.location)?.let { it <= OVERVIEW_NEARBY_METERS } == true

enum class OverviewEmphasis {
    NORMAL,
    PLACE,
    TIME,
    VALUE,
}

enum class OverviewTextRole { TEXT, DEPARTURE_TIME, ARRIVAL_TIME, ORIGIN_PLACE, DESTINATION_PLACE, BOARDING_PLATFORM }

data class OverviewTextPart(
    val text: String,
    val emphasis: OverviewEmphasis = OverviewEmphasis.NORMAL,
    val role: OverviewTextRole = OverviewTextRole.TEXT,
)

/** Pure sentence construction. Disabled fields are never inserted into hidden template slots. */
object OverviewSentenceGenerator {
    const val variantCount = 6

    /** [originIcon]: icon of the start favourite of a network plan row (null for every other row). */
    fun sentence(
        favorite: Favorite,
        trip: Trip,
        settings: OverviewSettings,
        variant: Int,
        weather: ArrivalWeather? = null,
        originIcon: FavoriteIcon? = null,
    ): List<OverviewTextPart> = buildList {
        fun plain(text: String) {
            add(OverviewTextPart(text))
        }
        fun place(text: String,role:OverviewTextRole=OverviewTextRole.DESTINATION_PLACE) {
            add(OverviewTextPart(text, OverviewEmphasis.PLACE,role))
        }
        fun time(value: Long, role: OverviewTextRole = OverviewTextRole.DEPARTURE_TIME) {
            add(OverviewTextPart(tr("%1\$s Uhr", clockTime(value)), OverviewEmphasis.TIME, role))
        }
        fun value(text: String) {
            add(OverviewTextPart(text, OverviewEmphasis.VALUE))
        }
        fun minutes(duration: Long): String {
            val n = (duration.coerceAtLeast(0) + 59_999) / 60_000
            return "$n ${if (n == 1L) tr("Minute") else tr("Minuten")}"
        }
        val first = trip.segments.firstOrNull { !it.isWalk } ?: trip.segments.firstOrNull()
        val v = Math.floorMod(variant, variantCount)
        val complete =
            settings.showDestination &&
                settings.showOriginStop &&
                settings.showDepartureTime &&
                settings.showArrivalTime
        // Complete rows use a whole sentence chosen by the destination's icon and, in network
        // plan rows, by the start favourite's icon (work → home, home → school, …).
        val template = if (complete && first != null)
            iconRouteTemplate(favorite.icon, originIcon, favorite.counterpole != null, variant) else null
        if (template != null && first != null) {
            routeSentence(template, favorite, first.origin.name, first.startTime, trip.endTime)
        } else if (settings.showDestination && favorite.icon != FavoriteIcon.PLACE) {
            if (favorite.icon == FavoriteIcon.HOME) addAll(homeJourneyIntroduction(favorite, v))
            else {
                plain(iconJourneyLead(favorite.icon, v))
                place(favorite.label)
            }
            plain(".")
            if (settings.showArrivalTime) {
                plain(tr(" Du kommst um "))
                time(trip.endTime, OverviewTextRole.ARRIVAL_TIME)
                plain(tr(" an."))
            }
            if (first != null && (settings.showDepartureTime || settings.showOriginStop)) {
                plain(tr(" Los geht’s"))
                if (settings.showDepartureTime) { plain(tr(" um ")); time(first.startTime) }
                if (settings.showOriginStop) { plain(tr(" ab ")); place(first.origin.name,OverviewTextRole.ORIGIN_PLACE) }
                plain(".")
            }
        } else if (complete && first != null) {
            when (v) {
                0 -> {
                    plain(if (settings.routingPreferences.enabled) tr("Die passende Verbindung zu deinem Ziel ")
                        else tr("Die nächste Verbindung zu deinem Ziel "))
                    place(favorite.label)
                    plain(tr(" startet um "))
                    time(first.startTime)
                    plain(tr(" ab "))
                    place(first.origin.name,OverviewTextRole.ORIGIN_PLACE)
                    plain(tr(". Du kommst um "))
                    time(trip.endTime, OverviewTextRole.ARRIVAL_TIME)
                    plain(tr(" an."))
                }
                1 -> {
                    plain(tr("Um "))
                    time(first.startTime)
                    plain(tr(" geht es ab "))
                    place(first.origin.name,OverviewTextRole.ORIGIN_PLACE)
                    plain(tr(" in Richtung "))
                    place(favorite.label)
                    plain(tr(". Deine Ankunft ist für "))
                    time(trip.endTime, OverviewTextRole.ARRIVAL_TIME)
                    plain(tr(" vorgesehen."))
                }
                2 -> {
                    plain(tr("Dein Ziel "))
                    place(favorite.label)
                    plain(tr(" erreichst du um "))
                    time(trip.endTime, OverviewTextRole.ARRIVAL_TIME)
                    plain(tr(". Die Verbindung startet um "))
                    time(first.startTime)
                    plain(tr(" ab "))
                    place(first.origin.name,OverviewTextRole.ORIGIN_PLACE)
                    plain(".")
                }
                3 -> {
                    plain(tr("Für "))
                    place(favorite.label)
                    plain(if (settings.routingPreferences.enabled) tr(" ist ein passender Start um ")
                        else tr(" ist der nächste Start um "))
                    time(first.startTime)
                    plain(tr(" ab "))
                    place(first.origin.name,OverviewTextRole.ORIGIN_PLACE)
                    plain(tr(". Am Ziel bist du um "))
                    time(trip.endTime, OverviewTextRole.ARRIVAL_TIME)
                    plain(".")
                }
                4 -> {
                    plain(tr("Ab "))
                    place(first.origin.name,OverviewTextRole.ORIGIN_PLACE)
                    plain(tr(" bringt dich die Verbindung um "))
                    time(first.startTime)
                    plain(tr(" zu "))
                    place(favorite.label)
                    plain(tr("; dort kommst du um "))
                    time(trip.endTime, OverviewTextRole.ARRIVAL_TIME)
                    plain(tr(" an."))
                }
                else -> {
                    place(favorite.label)
                    plain(if (settings.routingPreferences.enabled) tr(" ist mit dieser Verbindung um ")
                        else tr(" ist als Nächstes um "))
                    time(trip.endTime, OverviewTextRole.ARRIVAL_TIME)
                    plain(tr(" erreichbar: Los geht’s um "))
                    time(first.startTime)
                    plain(tr(" ab "))
                    place(first.origin.name,OverviewTextRole.ORIGIN_PLACE)
                    plain(".")
                }
            }
        } else {
            if (settings.showDestination) {
                plain(if (v % 2 == 0) tr("Dein Ziel ") else tr("Die Verbindung zu "))
                place(favorite.label)
                if (settings.showArrivalTime) {
                    plain(if (v % 2 == 0) tr(" erreichst du um ") else tr(" kommt um "))
                    time(trip.endTime, OverviewTextRole.ARRIVAL_TIME)
                    plain(if (v % 2 == 0) "." else tr(" an."))
                } else plain(if (v % 2 == 0) tr(" ist erreichbar.") else tr(" ist verfügbar."))
            } else if (settings.showArrivalTime) {
                plain(tr("Du kommst um "))
                time(trip.endTime, OverviewTextRole.ARRIVAL_TIME)
                plain(tr(" an."))
            }
            if (first != null && (settings.showDepartureTime || settings.showOriginStop)) {
                if (isNotEmpty()) plain(" ")
                plain(if (v % 2 == 0) tr("Los geht’s") else tr("Die Verbindung startet"))
                if (settings.showDepartureTime) {
                    plain(tr(" um "))
                    time(first.startTime)
                }
                if (settings.showOriginStop) {
                    plain(tr(" ab "))
                    place(first.origin.name,OverviewTextRole.ORIGIN_PLACE)
                }
                plain(".")
            }
        }
        if (settings.showPlatforms && first != null && !first.isWalk) {
            // Same wording as the journey companion: "U-Bahn Gleis 2" becomes "Gleis 2", never "Gleis U-Bahn Gleis 2".
            platformName(first.departurePlatform, first.lineType)
                ?.let { platform ->
                    plain(if (isEmpty()) tr("Du steigst an ") else tr(" Du steigst an "))
                    place(platform,OverviewTextRole.BOARDING_PLATFORM)
                    plain(tr(" ein."))
                }
        }
        if (settings.showDuration) {
            plain(if (isEmpty()) tr("Du brauchst insgesamt ") else tr(" Du brauchst insgesamt "))
            value(minutes(trip.totalDuration))
            plain(".")
        }
        if (settings.showWaitTimes) {
            val vehicles = trip.segments.withIndex().filterNot { it.value.isWalk }
            vehicles.zipWithNext().forEach { (before, after) ->
                val walking =
                    trip.segments
                        .subList(before.index + 1, after.index)
                        .filter { it.isWalk }
                        .sumOf { (it.endTime - it.startTime).coerceAtLeast(0) }
                val wait = (after.value.startTime - before.value.endTime - walking).coerceAtLeast(0)
                plain(if (isEmpty()) tr("Beim Umstieg in ") else tr(" Beim Umstieg in "))
                place(after.value.origin.name)
                plain(tr(" wartest du "))
                value(minutes(wait))
                plain(".")
            }
        }
        if (settings.showWalks) {
            val walks = trip.segments.filter { it.isWalk }
            if (walks.isNotEmpty()) {
                plain(
                    if (isEmpty()) tr("Zu Fuß gehst du insgesamt ") else tr(" Zu Fuß gehst du insgesamt ")
                )
                value(minutes(walks.sumOf { (it.endTime - it.startTime).coerceAtLeast(0) }))
                if (walks.all { it.distanceMeters != null }) {
                    plain(" (")
                    value(tr("%1\$s m", walks.sumOf { it.distanceMeters!!.coerceAtLeast(0).toLong() }))
                    plain(")")
                }
                plain(".")
            }
        }
        if (settings.showTransfers) {
            plain(if (isEmpty()) tr("Die Verbindung hat ") else tr(" Die Verbindung hat "))
            value("${trip.transfers} ${if (trip.transfers == 1) tr("Umstieg") else tr("Umstiege")}")
            plain(".")
        }
        if (settings.showTransport) {
            val modes =
                trip.segments
                    .filterNot { it.isWalk }
                    .map {
                        if (it.lineType == LineType.REGIONAL) tr("Regionalbahn")
                        else if (it.lineType == LineType.ICE) tr("Fernzug") else it.lineType.label
                    }
                    .distinct()
            plain(if (isEmpty()) tr("Du bist ") else tr(" Du bist "))
            if (modes.isEmpty()) {
                value(tr("zu Fuß"))
                plain(tr(" unterwegs."))
            } else {
                plain(tr("mit "))
                value(
                    modes.dropLast(1).joinToString(", ").let {
                        if (it.isEmpty()) "" else tr("%1\$s und ", it)
                    } + modes.last()
                )
                plain(tr(" unterwegs."))
            }
        }
        if (settings.showLineNames) {
            val lines = trip.segments.filterNot { it.isWalk }.map { it.lineName.trim() }
                .filter { it.isNotBlank() }.fold(emptyList<String>()) { names, name ->
                    if (names.lastOrNull() == name) names else names + name
                }
            if (lines.isNotEmpty()) {
                if (isNotEmpty()) plain(" ")
                plain(if (lines.size == 1) tr("Du nimmst die Linie ") else tr("Du nimmst die Linien "))
                value(lines.joinToString(" → "))
                plain(".")
            }
        }
        if (settings.showWeather && weather?.covers(trip.endTime) == true) {
            if (isNotEmpty()) plain(" ")
            addAll(WeatherSentenceEngine.sentence(favorite, settings, weather, variant,
                city = favorite.location.city ?: trip.segments.lastOrNull()?.destination?.city,
                hasRail = trip.segments.any { it.lineType in setOf(LineType.REGIONAL, LineType.ICE, LineType.SUBURBAN) },
            ))
        }
        if (isEmpty()) plain(tr("Eine Verbindung ist verfügbar."))
    }

    private val TOKEN = Regex("\\{(origin|target|dep|arr)\\}")

    /** Fills a sentence template; places and times keep their roles and highlighting. A generic
     * home label ("Zuhause", "Home") reads as "nach Hause"/"zu Hause" or "home", never "nach Zuhause". */
    private fun MutableList<OverviewTextPart>.routeSentence(template: String, favorite: Favorite, origin: String, departure: Long, arrival: Long) {
        val home = favorite.icon == FavoriteIcon.HOME && isGenericHomeLabel(favorite.label)
        val german = serviceLanguage() == "de"
        var last = 0
        TOKEN.findAll(template).forEach { match ->
            var literal = template.substring(last, match.range.first)
            last = match.range.last + 1
            var target = favorite.label
            if (match.groupValues[1] == "target" && home) {
                target = when {
                    german && literal.endsWith("nach ") -> "Hause"
                    german && (literal.endsWith("bei ") || literal.endsWith(" in ")) -> {
                        literal = literal.dropLast(if (literal.endsWith("bei ")) 4 else 3) + "zu "; "Hause"
                    }
                    german -> favorite.label
                    literal.endsWith(" to ") -> { literal = literal.dropLast(3); "home" }
                    literal.isEmpty() -> "Home"
                    else -> "home"
                }
            }
            if (literal.isNotEmpty()) add(OverviewTextPart(literal))
            when (match.groupValues[1]) {
                "origin" -> add(OverviewTextPart(origin, OverviewEmphasis.PLACE, OverviewTextRole.ORIGIN_PLACE))
                "target" -> add(OverviewTextPart(target, OverviewEmphasis.PLACE, OverviewTextRole.DESTINATION_PLACE))
                "dep" -> add(OverviewTextPart(tr("%1\$s Uhr", clockTime(departure)), OverviewEmphasis.TIME, OverviewTextRole.DEPARTURE_TIME))
                else -> add(OverviewTextPart(tr("%1\$s Uhr", clockTime(arrival)), OverviewEmphasis.TIME, OverviewTextRole.ARRIVAL_TIME))
            }
        }
        template.substring(last).takeIf { it.isNotEmpty() }?.let { add(OverviewTextPart(it)) }
    }
}
