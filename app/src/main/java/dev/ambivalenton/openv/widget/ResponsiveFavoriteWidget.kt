// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.background
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.layout.*
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.TextAlign
import androidx.glance.unit.ColorProvider
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.rendering.FavoriteBadgeArtwork
import dev.ambivalenton.openv.ui.components.resource
import java.time.ZonedDateTime

/**
 * Build 107 · the favourites widget, rebuilt. Every favourite is a tile in its own colour with its
 * symbol and – wherever the tile allows – its name (wide tiles also name the stop). Highlighted
 * favourites come first in their strong colour. A tap searches the connection there. The grid adapts
 * to every launcher size ([favoritePlan]); what does not fit scrolls.
 */
@Composable internal fun QuickFavoriteContent(state: QuickWidgetState, fontScale: Float, now: ZonedDateTime, preview: Boolean = false) {
    val context = LocalContext.current
    val size = LocalSize.current
    val palette = remember(state.settings) { widgetPalette(context, state.settings) }
    val favorites = visibleFavorites(state.favorites, now, state.presence)
    val plan = favoritePlan(size.width.value, size.height.value, favorites.size, fontScale)
    Box(GlanceModifier.fillMaxSize().appWidgetBackground().widgetFill(WidgetShape.OUTER, palette.surface).padding(plan.padding.dp)) {
        if (favorites.isEmpty()) FavoriteEmpty(state, palette)
        else Column(GlanceModifier.fillMaxSize()) {
            if (plan.header > 0f) {
                FavoriteHeader(plan, palette)
                Spacer(GlanceModifier.height(plan.gap.dp))
            }
            val rows = favorites.chunked(plan.columns).take(plan.rows)
            if (plan.scroll && !preview) LazyColumn(GlanceModifier.fillMaxWidth().defaultWeight()) {
                items(rows.size) { i -> FavoriteRow(rows[i], plan, state, now, last = i == rows.lastIndex) }
            } else Column(GlanceModifier.fillMaxWidth()) {
                val shown = rows.take(plan.visibleRows)
                shown.forEachIndexed { i, row -> FavoriteRow(row, plan, state, now, last = i == shown.lastIndex) }
            }
        }
    }
}

@Composable private fun FavoriteHeader(plan: FavoritePlan, palette: WidgetPalette) {
    val context = LocalContext.current
    Row(GlanceModifier.fillMaxWidth().height(plan.header.dp), verticalAlignment = Alignment.Vertical.CenterVertically) {
        WidgetText(tr("Favoriten"), palette.muted, 13.sp, GlanceModifier.defaultWeight().padding(start = 6.dp), FontWeight.Medium)
        Box(GlanceModifier.size(32.dp).widgetFill(WidgetShape.PILL, palette.tile).clickable(actionStartActivity(searchIntent(context)))
            .semantics { contentDescription = tr("Verbindung suchen") }, contentAlignment = Alignment.Center) {
            Image(ImageProvider(R.drawable.ic_search), null, GlanceModifier.size(18.dp), colorFilter = ColorFilter.tint(palette.onSurface))
        }
    }
}

@Composable private fun FavoriteRow(row: List<Favorite>, plan: FavoritePlan, state: QuickWidgetState, now: ZonedDateTime, last: Boolean) {
    Column {
        Row(GlanceModifier.fillMaxWidth().height(plan.tileHeight.dp)) {
            row.forEachIndexed { i, favorite ->
                if (i > 0) Spacer(GlanceModifier.width(plan.gap.dp))
                FavoriteTileView(favorite, plan, state.settings, highlighted = favorite.rule?.matches(now) == true)
            }
        }
        if (!last) Spacer(GlanceModifier.height(plan.gap.dp))
    }
}

@Composable private fun FavoriteTileView(favorite: Favorite, plan: FavoritePlan, settings: AppSettings, highlighted: Boolean) {
    val context = LocalContext.current
    val colors = GlanceThemeHelper.favoritePair(context, settings, favorite.accentSeed, highlighted)
    val w = plan.tileWidth; val h = plan.tileHeight
    val description = if (highlighted) tr("%1\$s, hervorgehoben. Verbindung suchen", favorite.label) else tr("%1\$s. Verbindung suchen", favorite.label)
    Box(GlanceModifier.width(w.dp).height(h.dp).widgetFill(WidgetShape.INNER, colors.container)
        .clickable(actionStartActivity(quickLaunchIntent(context, favorite.id, settings.widget)))
        .semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        when (plan.tile) {
            FavoriteTile.MARK -> FavoriteMark(favorite, (minOf(w, h) * .7f).coerceIn(22f, 52f), colors, highlighted)
            FavoriteTile.STACKED -> {
                val label = if (w < 72f) 11f else if (w >= 120f && h >= 120f) 14.5f else 12.5f
                val mark = minOf(w * .5f, h - label * 1.6f - 14f).coerceIn(22f, 76f)
                Column(GlanceModifier.fillMaxSize().padding(horizontal = 4.dp), horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                    verticalAlignment = Alignment.Vertical.CenterVertically) {
                    FavoriteMark(favorite, mark, colors, highlighted)
                    Spacer(GlanceModifier.height(4.dp))
                    WidgetText(favorite.label, colors.content, label.sp, GlanceModifier.fillMaxWidth(), FontWeight.Medium, TextAlign.Center)
                }
            }
            FavoriteTile.WIDE -> Row(GlanceModifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.Vertical.CenterVertically) {
                FavoriteMark(favorite, minOf(h * .64f, 44f).coerceAtLeast(24f), colors, highlighted)
                Spacer(GlanceModifier.width(10.dp))
                Column(GlanceModifier.defaultWeight()) {
                    WidgetText(favorite.label, colors.content, (if (h >= 56f) 14.5f else 13.5f).sp, weight = FontWeight.Medium)
                    if (h >= 56f && favorite.location.name != favorite.label)
                        WidgetText(favorite.location.name, colors.content, 11.5.sp, GlanceModifier.padding(top = 1.dp))
                }
            }
        }
    }
}

/** The favourite's symbol in its own shape. On a highlighted (strong) tile the colours turn around. */
@Composable private fun FavoriteMark(favorite: Favorite, size: Float, colors: WidgetColors, highlighted: Boolean) {
    val shape = favorite.shape
    val fill: ColorProvider = if (highlighted) colors.onAccent else colors.accent
    val ink: ColorProvider = if (highlighted) colors.accent else colors.onAccent
    Box(GlanceModifier.size(size.dp).background(ImageProvider(favoriteSymbolResource(shape, 1)), ContentScale.FillBounds, ColorFilter.tint(fill)),
        contentAlignment = Alignment.Center) {
        Image(ImageProvider(favorite.icon.resource()), null, GlanceModifier.size((size * FavoriteBadgeArtwork.iconFraction(shape)).dp),
            colorFilter = ColorFilter.tint(ink))
    }
}

@Composable private fun FavoriteEmpty(state: QuickWidgetState, palette: WidgetPalette) {
    val context = LocalContext.current
    val size = LocalSize.current
    val none = state.favorites.isEmpty()
    val message = if (none) tr("Favorit hinzufügen") else tr("Favoriten gerade ausgeblendet")
    Column(GlanceModifier.fillMaxSize().clickable(actionStartActivity(searchIntent(context))).semantics { contentDescription = message },
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally, verticalAlignment = Alignment.Vertical.CenterVertically) {
        val mark = (minOf(size.width.value, size.height.value) * .42f).coerceIn(24f, 48f)
        Box(GlanceModifier.size(mark.dp).widgetFill(WidgetShape.PILL, palette.accentContainer), contentAlignment = Alignment.Center) {
            Image(ImageProvider(if (none) R.drawable.ic_add else R.drawable.ic_clock), null, GlanceModifier.size((mark * .55f).dp),
                colorFilter = ColorFilter.tint(palette.onAccentContainer))
        }
        if (size.height.value >= 96f && size.width.value >= 100f) {
            Spacer(GlanceModifier.height(8.dp))
            WidgetText(message, palette.onSurface, 13.sp, GlanceModifier.padding(horizontal = 6.dp), FontWeight.Medium, TextAlign.Center, lines = 2)
        }
    }
}

@Composable internal fun QuickWidgetFallback(message: String) {
    val context = LocalContext.current
    Box(GlanceModifier.fillMaxSize().clickable(actionStartActivity(searchIntent(context))).semantics { contentDescription = message },
        contentAlignment = Alignment.Center) {
        Image(ImageProvider(R.drawable.ic_place), null, GlanceModifier.size(32.dp))
    }
}
