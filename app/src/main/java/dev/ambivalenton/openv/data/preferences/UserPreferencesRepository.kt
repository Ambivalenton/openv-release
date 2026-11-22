// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.data.preferences

import dev.ambivalenton.openv.localization.tr

import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.LocationRepository
import java.text.Normalizer
import java.util.Locale
import kotlin.math.floor
import kotlinx.coroutines.flow.map

/** A typed preferences facade on the same transactional DataStore used by app and widget. */
class UserPreferencesRepository(private val locations: LocationRepository) {
    val preferences = locations.state.map { it.settings }
    val frequentSearches = locations.state.map { mergeSearchHistory(it.history) }
    val boardStops = locations.state.map { it.boardStops }

    suspend fun update(transform: (AppSettings) -> AppSettings) =
        locations.updateSettings(transform)

    suspend fun theme(mode: ThemeMode) = update { it.copy(themeMode = mode) }

    suspend fun accent(seed: Long?) = update { it.copy(accentSeed = seed) }

    suspend fun longDistance(enabled: Boolean) = update {
        it.copy(includeLongDistance = enabled, products = if (enabled) it.products + LineType.ICE else it.products - LineType.ICE)
    }

    suspend fun radius(meters: Int) = update { it.copy(departureRadiusMeters = meters) }

    suspend fun timeline(theme: TimelineTheme) = update { it.selectTimelineTheme(theme) }

    suspend fun recordSearch(query: JourneyQuery) = locations.remember(query)

    suspend fun clearHistory() = locations.clearHistory()

    suspend fun addBoardStop(stop: Location) = locations.addBoardStop(stop)

    suspend fun removeBoardStop(stop: Location) = locations.removeBoardStop(stop)
}

/** GPS fixes within roughly 100 m share an origin bucket; station/provider IDs remain exact. */
internal fun historyLocationKey(location: Location, origin: Boolean): String {
    if (location.id != null && location.provider != "OSM")
        return "id:${location.provider}:${location.id}"
    if (location.hasCoordinates) {
        val latitude = location.latitude!!
        val longitude = location.longitude!!
        val step = if (origin && location.kind == LocationKind.COORDINATE) 1000.0 else 100000.0
        return "geo:${floor(latitude * step + .5).toLong()}:${floor(longitude * step + .5).toLong()}:$step"
    }
    return "name:" +
        Normalizer.normalize(location.name.trim(), Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
}

internal fun historyRouteKey(query: JourneyQuery) =
    historyLocationKey(query.origin, true) + "→" + historyLocationKey(query.destination, false)

fun mergeSearchHistory(rows: List<SearchHistory>): List<SearchHistory> =
    rows
        .groupBy { historyRouteKey(it.query) }
        .values
        .map { group ->
            val newest = group.maxBy { it.searchedAt }
            val count =
                group.fold(0L) { sum, row ->
                    val n = row.count.coerceAtLeast(1)
                    if (Long.MAX_VALUE - sum < n) Long.MAX_VALUE else sum + n
                }
            newest.copy(count = count)
        }
        .sortedWith(
            compareByDescending<SearchHistory> { it.count }.thenByDescending { it.searchedAt }
        )
        .take(50)

internal fun normalizePreferences(settings: AppSettings): AppSettings =
    TimetableProfile.normalize(settings.normalizedQuietAppearance().let {s->val t=normalizedTimelineTheme(s.timelineTheme);if(t==s.timelineTheme)s else s.selectTimelineTheme(t)}).copy(
        searchResultOrder=normalizedSearchOrder(settings.searchResultOrder),
        showTicketPurchase=false,
        footprints=settings.footprints.normalized(),
        readability = settings.readability.normalized(),
        timelineLabelControl = dev.ambivalenton.openv.data.model.normalizedTimelineLabelControl(settings.timelineLabelControl),
        // 2.4: long-distance services are one of the transport-mode tags. The stored long-distance
        // flag stays authoritative, as before, so a saved choice keeps its effect after the update;
        // the settings frame always writes tag and flag together.
        products =
            (settings.products - LineType.WALK - LineType.ICE) +
                if (settings.includeLongDistance) setOf(LineType.ICE) else emptySet(),
        departureRadiusMeters = normalizeRadius(settings.departureRadiusMeters),
        favoriteRadiusMeters = normalizedFavoriteRadius(settings.favoriteRadiusMeters),
        accentSeed = settings.accentSeed?.let { (it and 0xFFFFFFL) or 0xFF000000L },
        overview =
            settings.overview.copy(
                bufferMinutes =
                    if (settings.overview.bufferMinutes.isFinite())
                        settings.overview.bufferMinutes.coerceIn(BUFFER_MINUTES_RANGE)
                    else 0f
            ),
        widget = settings.widget.fixedFavoritePresentation(),
        board =
            settings.board.copy(
                rotationIntervalSeconds=settings.board.rotationSeconds,
                autoRotate=settings.board.rotationSeconds>0,
                design=normalizedBoardDesign(settings.board.design),
                products = settings.board.products - LineType.WALK,
                selectedStop = settings.board.selectedStop?.takeIf { it.routable },
                customColors =
                    settings.board.customColors.mapValues { (_, colors) ->
                        BoardColors(
                            colors.foreground and 0xffffffL or 0xff000000L,
                            colors.background and 0xffffffL or 0xff000000L,
                            colors.accent and 0xffffffL or 0xff000000L,
                        )
                    },
            ),
    )

/** Persist integer metres; a 1,000 m tick must never become 999 m through Float truncation. */
fun normalizeRadius(meters: Int): Int =
    ((meters.coerceIn(500, 3000) + 50) / 100 * 100).coerceIn(500, 3000)

fun formatRadius(meters: Int): String {
    val value = normalizeRadius(meters)
    return if (value < 1000) tr("%1\$s m", value) else tr("%1\$s,%2\$s km", value / 1000, value % 1000 / 100)
}
