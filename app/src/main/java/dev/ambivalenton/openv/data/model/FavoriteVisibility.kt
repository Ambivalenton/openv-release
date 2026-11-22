// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.data.model

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZonedDateTime
import kotlin.math.*

const val SAME_LOCATION_METERS = 50.0
// UI visibility is intentionally broader than stop identity / routing equality.
const val FAVORITE_HIDE_RADIUS_METERS = 300.0
const val PRESENCE_MAX_AGE_MILLIS = 5 * 60_000L

/** Pure spherical distance, shared by routing, Compose and Glance (no Android dependency). */
fun distanceMeters(a: Location, b: Location): Double? {
    if (!a.hasCoordinates || !b.hasCoordinates) return null
    val latA = Math.toRadians(a.latitude!!)
    val latB = Math.toRadians(b.latitude!!)
    val dLat = latB - latA
    val dLon = Math.toRadians(b.longitude!! - a.longitude!!)
    val h = sin(dLat / 2).pow(2) + cos(latA) * cos(latB) * sin(dLon / 2).pow(2)
    return 6_371_008.8 * 2 * asin(sqrt(h.coerceIn(0.0, 1.0)))
}

fun sameLocation(a: Location, b: Location): Boolean =
    a == b ||
        (!a.id.isNullOrBlank() &&
            a.id == b.id &&
            a.provider == b.provider &&
            a.kind == LocationKind.STOP &&
            b.kind == LocationKind.STOP) ||
        distanceMeters(a, b)?.let { it < SAME_LOCATION_METERS } == true

fun DevicePresence.isFresh(nowMillis: Long): Boolean =
    location.hasCoordinates &&
        accuracyMeters.isFinite() &&
        accuracyMeters in 0f..50f &&
        nowMillis - capturedAt in 0 until PRESENCE_MAX_AGE_MILLIS

enum class FavoriteHiddenReason {
    AT_CURRENT_LOCATION,
    TIME_WINDOW,
}

fun Favorite.hiddenReason(
    now: ZonedDateTime,
    presence: DevicePresence?,
    hideAtCurrentLocation: Boolean = true,
): FavoriteHiddenReason? =
    when {
        // Since build 67 a time window is a time-dependent location: it hides only through the
        // presence it supplies (routingPresence), like any other "you are here".
        counterpole==null && hideAtCurrentLocation &&
            presence?.isFresh(now.toInstant().toEpochMilli()) == true &&
            distanceMeters(location, presence.location)?.let { it <= FAVORITE_HIDE_RADIUS_METERS } ==
                true -> FavoriteHiddenReason.AT_CURRENT_LOCATION
        else -> null
    }

/** While at least one shown row is highlighted by its time rule, the overview shows only
 * the highlighted rows; without an active highlight everything stays as it was. */
fun <T> onlyHighlighted(rows: List<T>, highlighted: (T) -> Boolean): List<T> =
    rows.filter(highlighted).ifEmpty { rows }

/**
 * The time-highlighted rows plus the pinned one. With
 * neither, every row again, but only destinations (no transfer points of the network plan).
 */
fun <T> overviewFocusRows(rows: List<T>, highlighted: (T) -> Boolean, pinned: (T) -> Boolean, destination: (T) -> Boolean): List<T> =
    rows.filter { highlighted(it) || pinned(it) }.ifEmpty { rows.filter(destination) }

/** Hiding always wins over highlighting; ties preserve the user's manual order. */
fun visibleFavorites(
    favorites: List<Favorite>,
    now: ZonedDateTime,
    presence: DevicePresence? = null,
    hideAtCurrentLocation: Boolean = true,
): List<Favorite> =
    favorites
        .filter { it.hiddenReason(now, presence, hideAtCurrentLocation) == null }
        .sortedByDescending { it.rule?.matches(now) == true }

/** Exact rule edges, including both occurrences of a repeated hour after a DST change. */
fun nextFavoriteChange(state: StoredState, now: ZonedDateTime): Instant? {
    val instant = now.toInstant()
    val candidates = mutableListOf<Instant>()
    fun add(local: LocalDateTime) {
        val offsets = now.zone.rules.getValidOffsets(local)
        if (offsets.isEmpty()) now.zone.rules.getTransition(local)?.instant?.let(candidates::add)
        else offsets.forEach { candidates.add(local.toInstant(it)) }
    }
    // Favourites and network lines retain independent schedules.
    (state.favorites.flatMap { listOfNotNull(it.rule) + it.hiddenRules } +
        state.network.edges.flatMap { listOfNotNull(it.rule) + it.hiddenRules })
        .distinct()
        .forEach { rule ->
            // Yesterday's start can own an overnight window which is still active today.
            for (day in -1L..8L) {
                val date = now.toLocalDate().plusDays(day)
                if (date.dayOfWeek.value !in rule.days) continue
                if (rule.fromMinute == rule.untilMinute) {
                    add(date.atStartOfDay())
                    add(date.plusDays(1).atStartOfDay())
                } else {
                    add(date.atStartOfDay().plusMinutes(rule.fromMinute.toLong()))
                    val end = if (rule.untilMinute < rule.fromMinute) date.plusDays(1) else date
                    add(end.atStartOfDay().plusMinutes(rule.untilMinute.toLong()))
                }
            }
        }
    state.presence
        ?.takeIf {
            it.isFresh(instant.toEpochMilli())
        }
        ?.let { candidates.add(Instant.ofEpochMilli(it.capturedAt + PRESENCE_MAX_AGE_MILLIS)) }
    // At a clock transition matches() may change even if neither wall-clock edge exists.
    now.zone.rules
        .nextTransition(instant)
        ?.instant
        ?.takeIf { it < instant.plusSeconds(8 * 86400) }
        ?.let(candidates::add)
    return candidates.filter { it > instant }.minOrNull()
}

/** M03: why the overview has no row to show, so the empty view can name the cause and the step. */
enum class OverviewEmptyKind { NO_FAVORITES, HERE, HIDDEN_BY_TIME }
data class OverviewEmptyState(val kind: OverviewEmptyKind, val place: String? = null)

/**
 * M03: no favourites at all; the person is at the only shown destination ("Hier bist du gerade");
 * or a time window supplies the position and hides the destinations ("Heute sind keine Ziele eingeblendet").
 */
fun StoredState.overviewEmptyState(now: Long): OverviewEmptyState {
    if (favorites.isEmpty()) return OverviewEmptyState(OverviewEmptyKind.NO_FAVORITES)
    scheduledStart(now)?.let { return OverviewEmptyState(OverviewEmptyKind.HIDDEN_BY_TIME, it.label) }
    val zoned = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault())
    val presence = routingPresence(now)
    val here = favorites.firstOrNull { it.hiddenReason(zoned, presence) == FavoriteHiddenReason.AT_CURRENT_LOCATION }
    return if (here != null) OverviewEmptyState(OverviewEmptyKind.HERE, here.label) else OverviewEmptyState(OverviewEmptyKind.HIDDEN_BY_TIME)
}
