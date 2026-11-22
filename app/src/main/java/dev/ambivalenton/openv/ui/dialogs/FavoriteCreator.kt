// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.dialogs

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.components.*
import dev.ambivalenton.openv.ui.theme.LocalAppSettings
import dev.ambivalenton.openv.ui.theme.appCorner
import dev.ambivalenton.openv.ui.theme.favoriteColors
import dev.ambivalenton.openv.ui.theme.favoriteThemeColors

/**
 * "Neuer Favorit" – one screen instead of a sequence of fields. On top the favourite as it
 * will look (badge, name, place), below it the search with the keyboard already open, then the
 * suggestions the app knows (recent destinations, the board's stops – never existing favourites),
 * the kind of place and the colour. Every choice already has a sensible default, so the first
 * chosen place is enough to save; everything else is a tap away and shows at once in the badge.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FavoriteCreatorBody(
    location: Location?,
    icon: FavoriteIcon?,
    label: String,
    nameIsSuggested: Boolean,
    accentSeed: Long?,
    shape: IconStyle,
    providerKey: String,
    search: suspend (String) -> List<Location>,
    suggestions: List<FavoritePlaceSuggestion>,
    otherFavorites: List<Favorite>,
    onLocation: (Location?) -> Unit,
    onIcon: (FavoriteIcon) -> Unit,
    onName: (String) -> Unit,
    onColor: (Long) -> Unit,
    onMoreOptions: () -> Unit,
    autoFocus: Boolean,
) {
    var searching by rememberSaveable { mutableStateOf(location?.routable != true) }
    var typed by rememberSaveable { mutableStateOf("") }
    var allIcons by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(location) { if (location?.routable == true) searching = false }
    LaunchedEffect(searching) { if (searching && autoFocus) runCatching { kotlinx.coroutines.delay(120); focus.requestFocus() } }
    FavoriteHeroCard(location, icon, label, nameIsSuggested, accentSeed, shape, onName,
        onChangePlace = { searching = true; typed = "" })
    if (searching) {
        PlaceSearchField(tr("Ort suchen – Adresse oder Haltestelle"), location?.takeIf { false }, providerKey, search,
            { picked -> if (picked != null) onLocation(picked) },
            Modifier.fillMaxWidth().testTag("favorite-place-search"), expressive = true,
            // The other favourites are no suggestions for a new one.
            favorites = emptyList(), focusRequester = focus, onTextChange = { typed = it })
        if (typed.isBlank() && suggestions.isNotEmpty()) FavoriteSuggestionList(suggestions) { onLocation(it) }
    }
    FavoriteIntentChips(icon, onIcon, onAll = { allIcons = true })
    FavoriteColorDots(accentSeed, otherFavorites, onColor, onMoreOptions)
    if (allIcons) FavoriteOptionsDialog(tr("Alle Symbole"), { allIcons = false }) {
        FlowRow(Modifier.fillMaxWidth().selectableGroup().testTag("favorite-all-icons"),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FavoriteIcon.entries.filter { it != FavoriteIcon.HEART }.forEach { choice ->
                FilterChip(icon == choice, { onIcon(choice); allIcons = false }, label = { Text(choice.title) },
                    modifier = Modifier.testTag("favorite-icon-${choice.name}"),
                    leadingIcon = { AppIcon(choice.resource(), null, Modifier.size(20.dp)) })
            }
        }
    }
}

/** The favourite as it will look; the badge pops briefly whenever its symbol or colour changes. */
@Composable
private fun FavoriteHeroCard(location: Location?, icon: FavoriteIcon?, label: String, nameIsSuggested: Boolean,
    accentSeed: Long?, shape: IconStyle, onName: (String) -> Unit, onChangePlace: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val colors = favoriteColors(accentSeed, scheme.background.luminance() < .3f, scheme)
    val calm = LocalAppSettings.current.reduceMotion
    val pop = remember { Animatable(1f) }
    val haptics = LocalHapticFeedback.current
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(icon, accentSeed, location?.routable) {
        if (first) { first = false; return@LaunchedEffect }
        if (calm) return@LaunchedEffect
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        pop.snapTo(.78f); pop.animateTo(1f, spring(dampingRatio = .42f, stiffness = 520f))
    }
    Surface(Modifier.fillMaxWidth().testTag("favorite-hero"), shape = appCorner(28.dp), color = colors.container.copy(alpha = .55f)
        .compositeOverSafe(scheme.surfaceContainerHigh), border = BorderStroke(1.dp, colors.accent.copy(alpha = .35f))) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FavoriteBadge(Favorite(label = label.ifBlank { tr("Neuer Favorit") }, location = location ?: Location(tr("Vorschau")),
                icon = icon ?: FavoriteIcon.PLACE, accentSeed = accentSeed, shape = shape), size = 72.dp, shadow = true,
                modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }.testTag("favorite-hero-badge"))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                FavoriteNameField(label, nameIsSuggested, onName, enabled = location?.routable == true)
                if (location?.routable == true) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(placeLine(location), Modifier.weight(1f).testTag("favorite-hero-place"), style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    TextButton(onChangePlace, Modifier.heightIn(min = 40.dp).testTag("favorite-change-place")) { Text(tr("Ändern")) }
                } else Text(tr("Wähle unten einen Ort – Name, Symbol und Farbe schlägt die App vor."),
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}

private fun Color.compositeOverSafe(background: Color): Color = androidx.compose.ui.graphics.Color(
    androidx.core.graphics.ColorUtils.compositeColors(toArgb(), background.toArgb()))

private fun placeLine(place: Location): String = listOfNotNull(place.name.takeIf { it.isNotBlank() },
    place.city?.takeIf { it.isNotBlank() && !place.name.contains(it, ignoreCase = true) },
    if (place.kind == LocationKind.STOP) tr("Haltestelle") else null).joinToString(" · ")

/** The name in the badge card: a suggestion stays muted until it is changed. */
@Composable
private fun FavoriteNameField(name: String, suggested: Boolean, onChange: (String) -> Unit, enabled: Boolean) {
    val focus = LocalFocusManager.current
    val scheme = MaterialTheme.colorScheme
    TextField(name, { onChange(it.take(80)) }, Modifier.fillMaxWidth().testTag("favorite-name"), enabled = enabled,
        singleLine = true, placeholder = { Text(tr("Neuer Favorit")) },
        // Long names step down a size instead of being cut at the card's edge.
        textStyle = (if (name.length > 16) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge)
            .copy(color = if (suggested) scheme.onSurface.copy(alpha = .78f) else scheme.onSurface),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent, unfocusedIndicatorColor = scheme.outline.copy(alpha = .35f)))
}

/** Places worth saving that are not favourites yet; one tap chooses. */
@Composable
private fun FavoriteSuggestionList(suggestions: List<FavoritePlaceSuggestion>, onPick: (Location) -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("favorite-suggestions"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(tr("Vorschläge"), style = MaterialTheme.typography.titleSmall)
        suggestions.forEachIndexed { index, suggestion ->
            val place = suggestion.place
            Row(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClickLabel = tr("Als Favorit wählen")) { onPick(place) }
                .heightIn(min = 52.dp).padding(horizontal = 8.dp).testTag("favorite-suggestion-$index"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppIcon(if (suggestion.recent) R.drawable.ic_clock else if (place.kind == LocationKind.STOP) R.drawable.ic_stop else R.drawable.ic_place,
                    null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f)) {
                    Text(place.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (suggestion.recent) tr("Zuletzt gesucht") else tr("In deiner Anzeigetafel"),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AppIcon(R.drawable.ic_add, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** "Was ist das für ein Ort?": the common kinds in one swipeable row, all symbols behind "Mehr". */
@Composable
private fun FavoriteIntentChips(icon: FavoriteIcon?, onIcon: (FavoriteIcon) -> Unit, onAll: () -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("favorite-intents"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // "Alle Symbole" stands next to the title, always in view (not at the end of the swipeable row).
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Was ist das für ein Ort?"), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            TextButton(onAll, Modifier.heightIn(min = 44.dp).testTag("favorite-intent-more")) { Text(tr("Alle Symbole")) }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val shown = (listOfNotNull(icon?.takeIf { it !in favoriteIntentIcons }) + favoriteIntentIcons)
            shown.forEach { choice ->
                FilterChip(icon == choice, { onIcon(choice) }, label = { Text(choice.title) },
                    modifier = Modifier.heightIn(min = 44.dp).testTag("favorite-intent-${choice.name}"),
                    leadingIcon = { AppIcon(choice.resource(), null, Modifier.size(20.dp)) })
            }
        }
    }
}

/** Six colours of the theme, the most distinct from the other favourites first; "Mehr" opens all options. */
@Composable
private fun FavoriteColorDots(selected: Long?, others: List<Favorite>, onColor: (Long) -> Unit, onMore: () -> Unit) {
    val settings = LocalAppSettings.current
    val accent = MaterialTheme.colorScheme.primary.toArgb().toLong() and 0xffffffffL
    val palette = remember(settings.appTheme, settings.appThemeColors, accent) { favoriteThemeColors(settings, accent).map { it.color } }
    val ordered = remember(palette, others) {
        val first = suggestFavoriteAccent(others, palette)
        (listOfNotNull(first) + palette).distinct().take(6)
    }
    val shown = if (selected != null && selected !in ordered) listOf(selected) + ordered.take(5) else ordered
    Column(Modifier.fillMaxWidth().testTag("favorite-colors"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(tr("Farbe"), style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            shown.forEachIndexed { index, color ->
                val chosen = selected == color
                Box(Modifier.size(44.dp).testTag("favorite-color-dot-$index")
                    .selectable(chosen, role = Role.RadioButton) { onColor(color) }
                    .semantics { contentDescription = tr("Farbe %1\$s", index + 1); stateDescription = if (chosen) tr("Ausgewählt") else "" },
                    contentAlignment = Alignment.Center) {
                    Box(Modifier.size(if (chosen) 34.dp else 28.dp).background(Color(color), CircleShape)
                        .then(if (chosen) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier))
                }
            }
            TextButton(onMore, Modifier.heightIn(min = 44.dp).testTag("favorite-color-more")) { Text(tr("Mehr")) }
        }
    }
}

/**
 * Right after saving, above the plan: the new favourite with the next sensible step – a line
 * to "Zuhause" (or the nearest favourite), which is what turns places into overview connections – or,
 * for the very first one, another favourite. It closes itself after a while.
 */
@Composable
internal fun NewFavoriteBanner(favorite: Favorite, partner: Favorite?, connected: Boolean, onConnect: (Favorite) -> Unit,
    onAddAnother: () -> Unit, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    LaunchedEffect(favorite.id, connected) { kotlinx.coroutines.delay(if (connected) 5_000 else 15_000); onDismiss() }
    Surface(Modifier.fillMaxWidth().testTag("new-favorite-banner"), shape = appCorner(24.dp), color = scheme.secondaryContainer,
        contentColor = scheme.onSecondaryContainer, shadowElevation = 2.dp) {
        Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FavoriteBadge(favorite, size = 40.dp)
                Text(when {
                    connected && partner == null -> tr("Verbunden. Die Übersicht zeigt jetzt die Fahrt zwischen beiden.")
                    else -> tr("„%1\$s“ ist jetzt in deinem Netzplan.", favorite.label)
                }, Modifier.weight(1f).testTag("new-favorite-text"), style = MaterialTheme.typography.bodyLarge)
                IconButton(onDismiss, Modifier.testTag("new-favorite-close")) { AppIcon(R.drawable.ic_close, tr("Schließen")) }
            }
            if (!connected) Row(Modifier.padding(end = 8.dp)) {
                if (partner != null) ThemedButton({ onConnect(partner) }, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("new-favorite-connect")) {
                    FittedText(tr("Mit „%1\$s“ verbinden", partner.label))
                } else ThemedOutlinedButton(onAddAnother, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("new-favorite-add-another"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.onSecondaryContainer),
                    border = BorderStroke(1.5.dp, scheme.onSecondaryContainer.copy(alpha = .6f))) {
                    FittedText(tr("Weiteren Favoriten hinzufügen"))
                }
            }
        }
    }
}
