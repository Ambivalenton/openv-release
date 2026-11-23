// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.dialogs

import android.animation.ValueAnimator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.components.*
import dev.ambivalenton.openv.ui.theme.LocalAppSettings
import dev.ambivalenton.openv.ui.theme.appCorner
import dev.ambivalenton.openv.ui.theme.favoriteColors
import dev.ambivalenton.openv.ui.theme.favoriteThemeColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/*
 * Build 101 · "Favorit hinzufügen" in three calm beats, the same in the sheet and in the first-launch setup:
 *  1. Ort – search, recent places, or first say what it is ("Arbeit") and then find it.
 *  2. Aussehen – one large live badge, its name, the kind of place and a colour; nothing opens a window.
 *  3. Gespeichert – the badge lands with a small burst, and the next sensible step is offered.
 * The sheet keeps its height; only the content between the beats moves (one spring, no flashing windows).
 */

internal enum class FavoriteStudioPhase { PLACE, LOOK, SAVED }

private val studioJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
internal val favoriteDraftSaver = Saver<FavoriteDraft, String>(
    save = { studioJson.encodeToString(FavoriteDraft.serializer(), it) },
    restore = { runCatching { studioJson.decodeFromString(FavoriteDraft.serializer(), it) }.getOrDefault(FavoriteDraft()) })
private val favoriteSaver = Saver<Favorite?, String>(
    save = { it?.let { f -> studioJson.encodeToString(Favorite.serializer(), f) } ?: "" },
    restore = { raw -> raw.takeIf { it.isNotEmpty() }?.let { runCatching { studioJson.decodeFromString(Favorite.serializer(), it) }.getOrNull() } })

/** Motion is off with "Ruhiger Modus", power saving or Android's own animation switch. */
@Composable internal fun studioQuiet(): Boolean = LocalAppSettings.current.reduceMotion || !ValueAnimator.areAnimatorsEnabled()

/** A lively pop with a little overshoot, and a soft settle for layout. */
internal val StudioPop: SpringSpec<Float> = spring(dampingRatio = .48f, stiffness = 520f)
internal val StudioSettle: SpringSpec<Float> = spring(dampingRatio = .84f, stiffness = 380f)

/** The beats slide sideways (forward to the left), grow in from 96 % and settle with a spring. */
internal fun studioTransition(quiet: Boolean, forward: Boolean, landing: Boolean = false): ContentTransform {
    if (quiet) return EnterTransition.None togetherWith ExitTransition.None
    val enter = if (landing) scaleIn(spring(dampingRatio = .62f, stiffness = 340f), initialScale = .82f) + fadeIn(tween(200, delayMillis = 40))
    else slideInHorizontally(spring(dampingRatio = .82f, stiffness = 380f, visibilityThreshold = IntOffset.VisibilityThreshold)) { w -> (if (forward) w else -w) / 4 } +
        fadeIn(tween(220, delayMillis = 50)) + scaleIn(spring(dampingRatio = .8f, stiffness = 380f), initialScale = .96f)
    val exit = slideOutHorizontally(tween(180)) { w -> (if (forward) -w else w) / 6 } + fadeOut(tween(130)) + scaleOut(tween(180), targetScale = .97f)
    return ContentTransform(enter, exit, sizeTransform = SizeTransform(clip = false) { _, _ ->
        spring(dampingRatio = .88f, stiffness = 360f, visibilityThreshold = IntSize.VisibilityThreshold) })
}

/** The favourite as it will look. It pops and tilts briefly whenever its symbol, colour or form changes. */
@Composable internal fun StudioBadge(draft: FavoriteDraft, size: Dp, modifier: Modifier = Modifier, tag: String = "favorite-hero-badge") {
    val quiet = studioQuiet()
    val pop = remember { Animatable(1f) }
    val haptics = LocalHapticFeedback.current
    val look = Triple(draft.icon, draft.accentSeed, draft.shape)
    var shown by remember { mutableStateOf(look) }
    LaunchedEffect(look) {
        if (look == shown) return@LaunchedEffect
        shown = look
        if (quiet) return@LaunchedEffect
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        pop.snapTo(.8f); pop.animateTo(1f, StudioPop)
    }
    FavoriteBadge(Favorite(label = draft.label.ifBlank { tr("Neuer Favorit") }, location = draft.location ?: Location(tr("Vorschau")),
        icon = draft.icon ?: FavoriteIcon.PLACE, accentSeed = draft.accentSeed, shape = draft.shape), size = size, shadow = true,
        modifier = modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value; rotationZ = (1f - pop.value) * -40f }.testTag(tag))
}

/** Appears a little later than the one before it: rows rise into place one after another. */
@Composable internal fun Modifier.staggeredRise(index: Int, quiet: Boolean): Modifier {
    val appear = remember { Animatable(if (quiet) 1f else 0f) }
    LaunchedEffect(Unit) { if (!quiet) { delay(40L * index.coerceAtMost(8)); appear.animateTo(1f, spring(dampingRatio = .72f, stiffness = 320f)) } }
    return graphicsLayer { alpha = appear.value.coerceIn(0f, 1f); translationY = (1f - appear.value) * 18.dp.toPx() }
}

// ---------------------------------------------------------------------------------------------------
// 1 · Ort
// ---------------------------------------------------------------------------------------------------

@Composable internal fun FavoritePlaceStep(
    draft: FavoriteDraft,
    providerKey: String,
    search: suspend (String) -> List<Location>,
    suggestions: List<FavoritePlaceSuggestion>,
    roles: List<FavoriteIcon>,
    onRole: (FavoriteIcon?) -> Unit,
    onPlace: (Location) -> Unit,
    autoFocus: Boolean,
    tagPrefix: String,
    enabled: Boolean = true,
) {
    val quiet = studioQuiet()
    var typed by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    // The keyboard rises once the sheet has landed, not while it is still sliding in.
    LaunchedEffect(Unit) { if (autoFocus) { delay(if (quiet) 0 else 360); runCatching { focus.requestFocus() } } }
    val role = draft.icon?.takeIf { draft.iconChosen }
    // Build 102: the search comes first, right under the title, so its results stay above the keyboard.
    // While typing only the results show; roles and frequent places step aside.
    Column(Modifier.fillMaxWidth().testTag("$tagPrefix-step-place"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PlaceSearchField(if (role == null) tr("Adresse oder Haltestelle") else tr("Ort für „%1\$s“", draft.label.ifBlank { role.title }),
            null, providerKey, search, { picked -> if (picked?.routable == true) onPlace(picked) },
            Modifier.fillMaxWidth().testTag("$tagPrefix-place-search"), enabled = enabled, expressive = true,
            // The other favourites are no suggestions for a new one.
            favorites = emptyList(), focusRequester = focus, onTextChange = { typed = it }, chooseHint = false)
        CalmVisibility(typed.isBlank(), enter = fadeIn(tween(180)) + expandVertically(spring(dampingRatio = .86f, stiffness = 380f)),
            exit = fadeOut(tween(110)) + shrinkVertically(tween(160))) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                RoleChips(roles, role, onRole, tagPrefix, enabled)
                if (suggestions.isNotEmpty()) Column(Modifier.fillMaxWidth().testTag("$tagPrefix-suggestions"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(tr("Oft gesucht"), Modifier.padding(bottom = 4.dp), style = MaterialTheme.typography.titleSmall)
                    suggestions.forEachIndexed { index, suggestion ->
                        SuggestionRow(suggestion, Modifier.staggeredRise(index, quiet).testTag("$tagPrefix-suggestion-$index"), enabled) { onPlace(suggestion.place) }
                    }
                }
            }
        }
    }
}

/** "Zuhause", "Arbeit" …: tap one first and the search asks for that place. A second tap lets go. */
@Composable private fun RoleChips(roles: List<FavoriteIcon>, selected: FavoriteIcon?, onRole: (FavoriteIcon?) -> Unit, tagPrefix: String, enabled: Boolean) {
    val quiet = studioQuiet()
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup().testTag("$tagPrefix-roles"),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        roles.forEach { choice ->
            val chosen = choice == selected
            val scale by animateFloatAsState(if (chosen) 1.06f else 1f, if (quiet) snap() else StudioPop, label = "role-${choice.name}")
            FilterChip(chosen, { onRole(if (chosen) null else choice) }, enabled = enabled, label = { Text(choice.title) },
                modifier = Modifier.heightIn(min = 44.dp).graphicsLayer { scaleX = scale; scaleY = scale }.testTag("$tagPrefix-intent-${choice.name}"),
                leadingIcon = { AppIcon(choice.resource(), null, Modifier.size(20.dp)) })
        }
    }
}

@Composable private fun SuggestionRow(suggestion: FavoritePlaceSuggestion, modifier: Modifier, enabled: Boolean, onPick: () -> Unit) {
    val place = suggestion.place
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .clickable(enabled = enabled, role = Role.Button, onClickLabel = tr("Als Favorit wählen"), onClick = onPick)
        .heightIn(min = 56.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape), contentAlignment = Alignment.Center) {
            AppIcon(if (place.kind == LocationKind.STOP) R.drawable.ic_stop else R.drawable.ic_place,
                null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.weight(1f)) {
            Text(place.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(tr("%1\$s-mal gesucht", suggestion.searches),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AppIcon(R.drawable.ic_add, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

// ---------------------------------------------------------------------------------------------------
// 2 · Aussehen
// ---------------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun FavoriteLookStep(
    draft: FavoriteDraft,
    otherFavorites: List<Favorite>,
    onIcon: (FavoriteIcon) -> Unit,
    onName: (String) -> Unit,
    onColor: (Long) -> Unit,
    onShape: (IconStyle) -> Unit,
    onChangePlace: () -> Unit,
    tagPrefix: String,
    enabled: Boolean = true,
) {
    val quiet = studioQuiet()
    var allIcons by rememberSaveable { mutableStateOf(false) }
    var moreColors by rememberSaveable { mutableStateOf(false) }
    var colorPicker by rememberSaveable { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val colors = favoriteColors(draft.accentSeed, scheme.background.luminance() < .3f, scheme)
    // What unfolds scrolls into view once it has opened (above the save bar, the pill and the keyboard).
    val iconsReveal = remember { BringIntoViewRequester() }
    val colorsReveal = remember { BringIntoViewRequester() }
    LaunchedEffect(allIcons) { if (allIcons) { delay(if (quiet) 0 else 360); runCatching { iconsReveal.bringIntoView() } } }
    LaunchedEffect(moreColors) { if (moreColors) { delay(if (quiet) 0 else 360); runCatching { colorsReveal.bringIntoView() } } }
    Column(Modifier.fillMaxWidth().testTag("$tagPrefix-step-look"), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        // The hero: badge, name and place in one card tinted with the chosen colour.
        val tint by animateColorAsState(lerp(scheme.surfaceContainerHigh, colors.container, .55f),
            if (quiet) snap() else spring(stiffness = 300f), label = "favorite-hero-tint")
        Surface(Modifier.fillMaxWidth().testTag("$tagPrefix-hero"), shape = appCorner(28.dp), color = tint,
            border = BorderStroke(1.dp, colors.accent.copy(alpha = .3f))) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StudioBadge(draft, 96.dp, tag = "$tagPrefix-hero-badge")
                StudioNameField(draft.label, draft.nameIsSuggested, onName, enabled,
                    if (tagPrefix == "onboarding") "onboarding-favorite-name" else "$tagPrefix-name")
                draft.location?.takeIf { it.routable }?.let { place ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppIcon(if (place.kind == LocationKind.STOP) R.drawable.ic_stop else R.drawable.ic_place, null, Modifier.size(18.dp),
                            tint = scheme.onSurfaceVariant)
                        Text(placeLine(place), Modifier.weight(1f, fill = false).testTag("$tagPrefix-hero-place"), style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        TextButton(onChangePlace, Modifier.heightIn(min = 40.dp).testTag("$tagPrefix-change-place"), enabled = enabled) { Text(tr("Ändern")) }
                    }
                }
            }
        }
        // Kind of place: eight large tiles, every other symbol unfolds right here.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Was ist das für ein Ort?"), Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleSmall)
                TextButton({ allIcons = !allIcons }, Modifier.heightIn(min = 44.dp).testTag("$tagPrefix-intent-more"), enabled = enabled) {
                    Text(if (allIcons) tr("Weniger") else tr("Alle Symbole"))
                }
            }
            val shown = (listOfNotNull(draft.icon?.takeIf { it !in favoriteIntentIcons }) + favoriteIntentIcons).take(8)
            SymbolTiles(shown, draft.icon, colors.accent, colors.onAccent, enabled, tagPrefix) { onIcon(it) }
            CalmVisibility(allIcons, enter = fadeIn(tween(180)) + expandVertically(spring(dampingRatio = .82f, stiffness = 360f)),
                exit = fadeOut(tween(120)) + shrinkVertically(tween(180))) {
                FlowRow(Modifier.fillMaxWidth().bringIntoViewRequester(iconsReveal).padding(top = 4.dp).selectableGroup().testTag("$tagPrefix-all-icons"),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    FavoriteIcon.entries.filter { it != FavoriteIcon.HEART }.forEach { choice ->
                        FilterChip(draft.icon == choice, { onIcon(choice); allIcons = false }, enabled = enabled, label = { Text(choice.title) },
                            modifier = Modifier.testTag("$tagPrefix-icon-${choice.name}"),
                            leadingIcon = { AppIcon(choice.resource(), null, Modifier.size(20.dp)) })
                    }
                }
            }
        }
        // Colour: six of the theme, the one most apart from the other favourites first; "Mehr" unfolds the rest.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("Farbe"), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall)
            ColorDots(draft.accentSeed, otherFavorites, onColor, moreColors, { moreColors = !moreColors }, enabled, tagPrefix)
            CalmVisibility(moreColors, enter = fadeIn(tween(180)) + expandVertically(spring(dampingRatio = .82f, stiffness = 360f)),
                exit = fadeOut(tween(120)) + shrinkVertically(tween(180))) {
                Column(Modifier.bringIntoViewRequester(colorsReveal), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    FavoriteColorControls(draft.accentSeed, colorPicker, { colorPicker = true }, { colorPicker = false }, onColor)
                    ShapeChoices(draft, colors.accent, onShape, enabled, tagPrefix)
                }
            }
        }
    }
}

private fun placeLine(place: Location): String = listOfNotNull(place.name.takeIf { it.isNotBlank() },
    place.city?.takeIf { it.isNotBlank() && !place.name.contains(it, ignoreCase = true) },
    if (place.kind == LocationKind.STOP) tr("Haltestelle") else null).joinToString(" · ")

/** The name under the badge: centred, a suggestion stays a little muted until it is changed. */
@Composable private fun StudioNameField(name: String, suggested: Boolean, onChange: (String) -> Unit, enabled: Boolean, tag: String) {
    val focus = LocalFocusManager.current
    val scheme = MaterialTheme.colorScheme
    val style = (if (name.length > 18) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge)
        .copy(color = if (suggested) scheme.onSurface.copy(alpha = .8f) else scheme.onSurface, textAlign = TextAlign.Center)
    TextField(name, { onChange(it.take(80)) }, Modifier.fillMaxWidth().testTag(tag), enabled = enabled, singleLine = true,
        placeholder = { Text(tr("Name des Favoriten"), Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        textStyle = style, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent, unfocusedIndicatorColor = scheme.outline.copy(alpha = .35f)))
}

/** Large, evenly spread tiles; the chosen one fills with the favourite's colour and springs a little bigger. */
@Composable private fun SymbolTiles(icons: List<FavoriteIcon>, selected: FavoriteIcon?, accent: Color, onAccent: Color,
    enabled: Boolean, tagPrefix: String, onPick: (FavoriteIcon) -> Unit) {
    val quiet = studioQuiet()
    Column(Modifier.fillMaxWidth().selectableGroup().testTag("$tagPrefix-intents"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        icons.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { choice ->
                    val chosen = choice == selected
                    val scale by animateFloatAsState(if (chosen) 1.08f else 1f, if (quiet) snap() else StudioPop, label = "tile-${choice.name}")
                    val fill by animateColorAsState(if (chosen) accent else MaterialTheme.colorScheme.surfaceContainerHigh,
                        if (quiet) snap() else spring(stiffness = 500f), label = "tile-fill-${choice.name}")
                    val ink by animateColorAsState(if (chosen) onAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                        if (quiet) snap() else spring(stiffness = 500f), label = "tile-ink-${choice.name}")
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                        .selectable(chosen, enabled = enabled, role = Role.RadioButton) { onPick(choice) }
                        .padding(vertical = 6.dp).testTag("$tagPrefix-intent-${choice.name}"),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(52.dp).graphicsLayer { scaleX = scale; scaleY = scale }.background(fill, appCorner(18.dp)),
                            contentAlignment = Alignment.Center) {
                            AppIcon(choice.resource(), null, Modifier.size(26.dp), tint = ink)
                        }
                        Text(choice.title, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center, color = if (chosen) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable private fun ColorDots(selected: Long?, others: List<Favorite>, onColor: (Long) -> Unit, moreOpen: Boolean, onMore: () -> Unit,
    enabled: Boolean, tagPrefix: String) {
    val settings = LocalAppSettings.current
    val quiet = studioQuiet()
    val accent = MaterialTheme.colorScheme.primary.toArgb().toLong() and 0xffffffffL
    val palette = remember(settings.appTheme, settings.appThemeColors, accent) { favoriteThemeColors(settings, accent).map { it.color } }
    // Spread over the whole palette (a grey palette ran from black to dark grey before), the colour most
    // apart from the other favourites first.
    val ordered = remember(palette, others) {
        val spread = if (palette.size <= 6) palette else List(6) { i -> palette[(i * (palette.size - 1) + 2) / 5] }
        (listOfNotNull(suggestFavoriteAccent(others, palette)) + spread + palette).distinct().take(6)
    }
    val paper = MaterialTheme.colorScheme.surfaceContainerLow
    val shown = if (selected != null && selected !in ordered) listOf(selected) + ordered.take(5) else ordered
    Row(Modifier.fillMaxWidth().selectableGroup().testTag("$tagPrefix-colors"), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        shown.forEachIndexed { index, color ->
            val chosen = selected == color
            val size by animateDpAsState(if (chosen) 36.dp else 28.dp,
                if (quiet) snap() else spring(dampingRatio = .5f, stiffness = 520f), label = "color-dot-$index")
            Box(Modifier.size(44.dp).testTag("$tagPrefix-color-dot-$index")
                .selectable(chosen, enabled = enabled, role = Role.RadioButton) { onColor(color) }
                .semantics { contentDescription = tr("Farbe %1\$s", index + 1); stateDescription = if (chosen) tr("Ausgewählt") else "" },
                contentAlignment = Alignment.Center) {
                Box(Modifier.size(size).background(Color(color), CircleShape)
                    .then(if (chosen) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    // A colour close to the sheet (black on AMOLED) keeps a fine rim, so every dot is visible.
                    else if (dev.ambivalenton.openv.ui.theme.contrastRatio(Color(color), paper) < 1.6)
                        Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape) else Modifier))
            }
        }
        TextButton(onMore, Modifier.heightIn(min = 44.dp).testTag("$tagPrefix-color-more"), enabled = enabled) {
            Text(if (moreOpen) tr("Weniger") else tr("Mehr"))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun ShapeChoices(draft: FavoriteDraft, accent: Color, onShape: (IconStyle) -> Unit, enabled: Boolean, tagPrefix: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(tr("Form"), style = MaterialTheme.typography.titleSmall)
        FlowRow(Modifier.fillMaxWidth().selectableGroup().testTag("$tagPrefix-shapes"), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            IconStyle.entries.forEach { choice ->
                FilterChip(draft.shape == choice, { onShape(choice) }, enabled = enabled, label = { Text(choice.title) },
                    leadingIcon = { Box(Modifier.size(22.dp).background(accent, dev.ambivalenton.openv.widget.GlanceThemeHelper.composeShape(choice))) })
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------
// 3 · Gespeichert
// ---------------------------------------------------------------------------------------------------

/**
 * The saved favourite lands: the badge springs in, rings and a few sparks burst from it, a check pops on
 * its corner. Then the next sensible step: a line to "Zuhause" (or the nearest favourite), another
 * favourite, or done.
 */
@Composable internal fun FavoriteSavedStep(favorite: Favorite, partner: Favorite?, connected: Boolean,
    onConnect: (Favorite) -> Unit, onAnother: () -> Unit, onDone: () -> Unit) {
    val quiet = studioQuiet()
    val scheme = MaterialTheme.colorScheme
    val colors = favoriteColors(favorite.accentSeed, scheme.background.luminance() < .3f, scheme)
    val land = remember { Animatable(if (quiet) 1f else .4f) }
    val burst = remember { Animatable(if (quiet) 1f else 0f) }
    val check = remember { Animatable(if (quiet) 1f else 0f) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        if (quiet) return@LaunchedEffect
        launch { land.animateTo(1f, spring(dampingRatio = .42f, stiffness = 300f)) }
        launch { delay(60); burst.animateTo(1f, tween(720, easing = FastOutSlowInEasing)) }
        launch { delay(240); check.animateTo(1f, spring(dampingRatio = .45f, stiffness = 600f)) }
    }
    Column(Modifier.fillMaxWidth().testTag("new-favorite-saved"), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(196.dp), contentAlignment = Alignment.Center) {
            val sparkColors = listOf(colors.accent, scheme.tertiary, scheme.secondary, scheme.primary)
            Canvas(Modifier.matchParentSize()) {
                val p = burst.value
                if (p <= 0f || p >= 1f) return@Canvas
                val c = Offset(size.width / 2, size.height / 2)
                val start = 52.dp.toPx(); val reach = size.minDimension / 2
                for (ring in 0..1) {
                    val t = ((p - ring * .18f) / .82f).coerceIn(0f, 1f)
                    if (t > 0f) drawCircle(colors.accent.copy(alpha = (1f - t) * .55f), start + (reach - start) * t, c,
                        style = Stroke((1f - t) * 6.dp.toPx() + 1.dp.toPx()))
                }
                repeat(12) { i ->
                    val angle = (i / 12f) * 2 * PI + .3
                    val distance = start + (reach - start) * (.35f + .65f * p)
                    val o = Offset(c.x + (cos(angle) * distance).toFloat(), c.y + (sin(angle) * distance).toFloat())
                    drawCircle(sparkColors[i % sparkColors.size].copy(alpha = (1f - p).coerceIn(0f, 1f)), (if (i % 2 == 0) 4f else 2.6f).dp.toPx() * (1f - p * .4f), o)
                }
            }
            Box(Modifier.graphicsLayer { scaleX = land.value; scaleY = land.value; alpha = land.value.coerceIn(0f, 1f) }) {
                FavoriteBadge(favorite, size = 112.dp, shadow = true, modifier = Modifier.testTag("new-favorite-badge"))
                Box(Modifier.align(Alignment.BottomEnd).offset(6.dp, 6.dp).size(36.dp)
                    .graphicsLayer { scaleX = check.value; scaleY = check.value }
                    .background(scheme.primary, CircleShape).border(3.dp, scheme.surfaceContainerLow, CircleShape),
                    contentAlignment = Alignment.Center) {
                    AppIcon(R.drawable.ic_check, null, Modifier.size(20.dp), tint = scheme.onPrimary)
                }
            }
        }
        Text(tr("„%1\$s“ ist gespeichert", favorite.label), Modifier.fillMaxWidth().semantics { heading(); liveRegion = LiveRegionMode.Polite }
            .testTag("new-favorite-text"), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        AnimatedContent(connected, label = "favorite-saved-line", transitionSpec = {
            if (quiet) EnterTransition.None togetherWith ExitTransition.None
            else (fadeIn(tween(200)) + slideInVertically(spring(dampingRatio = .8f, stiffness = 400f)) { it / 2 }) togetherWith fadeOut(tween(120))
        }) { joined ->
            Text(if (joined) tr("Verbunden. Die Übersicht zeigt jetzt die Fahrt zwischen beiden.") else tr("Du findest ihn jetzt in deinem Netzplan."),
                Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = scheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        Column(Modifier.fillMaxWidth().widthIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CalmVisibility(partner != null && !connected, enter = fadeIn(tween(200)) + expandVertically(spring(dampingRatio = .8f, stiffness = 380f)),
                exit = fadeOut(tween(140)) + shrinkVertically(tween(180))) {
                var shownPartner by remember { mutableStateOf(partner) }
                if (partner != null) shownPartner = partner
                shownPartner?.let { other ->
                    ThemedButton({ onConnect(other) }, Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("new-favorite-connect")) {
                        FavoriteBadge(other, size = 26.dp)
                        Spacer(Modifier.width(10.dp))
                        FittedText(tr("Mit „%1\$s“ verbinden", other.label))
                    }
                }
            }
            ThemedTonalButton(onAnother, Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("new-favorite-add-another")) {
                AppIcon(R.drawable.ic_add, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                FittedText(tr("Noch einen Favoriten"))
            }
            TextButton(onDone, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("new-favorite-done")) { Text(tr("Fertig")) }
        }
    }
}

// ---------------------------------------------------------------------------------------------------
// The sheet
// ---------------------------------------------------------------------------------------------------

/** Two dots for the two choosing beats; the active one stretches into a short bar. */
@Composable private fun StudioStepDots(phase: FavoriteStudioPhase) {
    val quiet = studioQuiet()
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.semantics(mergeDescendants = true) {
        stateDescription = when (phase) {
            FavoriteStudioPhase.PLACE -> tr("Schritt %1\$s von %2\$s", 1, 2)
            FavoriteStudioPhase.LOOK -> tr("Schritt %1\$s von %2\$s", 2, 2)
            FavoriteStudioPhase.SAVED -> tr("Gespeichert")
        }
    }.testTag("favorite-studio-steps"), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(FavoriteStudioPhase.PLACE, FavoriteStudioPhase.LOOK).forEach { step ->
            val active = phase == step || phase == FavoriteStudioPhase.SAVED
            val width by animateDpAsState(if (phase == step) 22.dp else 8.dp,
                if (quiet) snap() else spring(dampingRatio = .55f, stiffness = 480f), label = "step-${step.name}")
            val color by animateColorAsState(if (active) scheme.primary else scheme.outlineVariant,
                if (quiet) snap() else spring(stiffness = 400f), label = "step-color-${step.name}")
            Box(Modifier.width(width).height(8.dp).background(color, CircleShape))
        }
    }
}

/**
 * "Favorit hinzufügen" as a sheet. [onSave] stores the favourite right away (closing the sheet afterwards
 * keeps it); the sheet then shows the saved moment until "Fertig", "Noch einen Favoriten" or a swipe.
 */
@Composable internal fun FavoriteCreatorSheet(
    providerKey: String,
    search: suspend (String) -> List<Location>,
    onSave: (Favorite) -> Unit,
    onDismiss: () -> Unit,
    initialLocation: Location?,
    suggestions: List<FavoritePlaceSuggestion>,
    otherFavorites: List<Favorite>,
    partnerFor: (String) -> Favorite?,
    onConnect: (String, String) -> Boolean,
) {
    val settings = LocalAppSettings.current
    val themeAccent = MaterialTheme.colorScheme.primary.toArgb().toLong() and 0xffffffffL
    val palette = remember(settings.appTheme, settings.appThemeColors, themeAccent) { favoriteThemeColors(settings, themeAccent).map { it.color } }
    fun fresh(others: List<Favorite>, place: Location? = null) =
        FavoriteDraft(accentSeed = suggestFavoriteAccent(others, palette)).let { d -> place?.takeIf { it.routable }?.let(d::withPlace) ?: d }
    var draft by rememberSaveable(stateSaver = favoriteDraftSaver) { mutableStateOf(fresh(otherFavorites, initialLocation)) }
    var phase by rememberSaveable { mutableStateOf(if (draft.location?.routable == true) FavoriteStudioPhase.LOOK else FavoriteStudioPhase.PLACE) }
    // The beat a "Zurück" leads to: the search only when the place was searched here.
    var searched by rememberSaveable { mutableStateOf(phase == FavoriteStudioPhase.PLACE) }
    var saved by rememberSaveable(stateSaver = favoriteSaver) { mutableStateOf<Favorite?>(null) }
    var connected by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val haptics = LocalHapticFeedback.current
    val scroll = rememberScrollState()
    val target = rememberJourneyScrollTarget(scroll)
    LaunchedEffect(phase) { scroll.scrollTo(0) }
    fun go(next: FavoriteStudioPhase) { focus.clearFocus(); phase = next }
    fun pick(place: Location) {
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        draft = draft.withPlace(place); searched = true; go(FavoriteStudioPhase.LOOK)
    }
    fun save() {
        if (!draft.ready) return
        val favorite = draft.favorite()
        onSave(favorite)
        saved = favorite; connected = false; go(FavoriteStudioPhase.SAVED)
    }
    fun another() {
        val known = (otherFavorites + listOfNotNull(saved)).distinctBy { it.id }
        draft = fresh(known); saved = null; connected = false; searched = true; go(FavoriteStudioPhase.PLACE)
    }
    val clearance = LocalNavigationClearance.current
    val bottom = if (clearance > 0.dp) clearance else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    DirectModalSheet(onDismiss, target, "favorite-editor-sheet") { motion, dismiss ->
        BackHandler(phase == FavoriteStudioPhase.LOOK && searched) { go(FavoriteStudioPhase.PLACE) }
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)).imePadding()
            .testTag("favorite-studio")) {
            // Build 102: one slim line – the back arrow slides in only on the second beat.
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                val back = phase == FavoriteStudioPhase.LOOK && searched
                CalmVisibility(back, enter = fadeIn(tween(160)) + expandHorizontally(spring(dampingRatio = .8f, stiffness = 420f)),
                    exit = fadeOut(tween(100)) + shrinkHorizontally(tween(160))) {
                    IconButton({ go(FavoriteStudioPhase.PLACE) }, Modifier.testTag("favorite-studio-back")) { AppIcon(R.drawable.ic_back, tr("Zurück")) }
                }
                Text(tr("Favorit hinzufügen"), Modifier.weight(1f).padding(start = 16.dp, end = 8.dp).semantics { heading() },
                    style = MaterialTheme.typography.titleLarge, maxLines = 1, softWrap = false,
                    autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 13.sp,
                        maxFontSize = MaterialTheme.typography.titleLarge.fontSize, stepSize = .5.sp))
                StudioStepDots(phase)
                IconButton(dismiss, Modifier.padding(start = 6.dp).testTag("favorite-editor-close")) { AppIcon(R.drawable.ic_close, tr("Schließen")) }
            }
            Column(Modifier.weight(1f).fillMaxWidth()
                .verticalScroll(scroll, enabled = false, overscrollEffect = null)
                .directSheetScrollSemantics(scroll, motion::cancelContentFling)
                .testTag("favorite-editor-scroll")
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp + if (phase == FavoriteStudioPhase.LOOK) 0.dp else bottom)) {
                AnimatedContent(phase, label = "favorite-studio", transitionSpec = {
                    studioTransition(settings.reduceMotion, targetState.ordinal > initialState.ordinal, targetState == FavoriteStudioPhase.SAVED)
                }) { step ->
                    when (step) {
                        FavoriteStudioPhase.PLACE -> FavoritePlaceStep(draft, providerKey, search, suggestions, favoriteRoleChoices(otherFavorites),
                            onRole = { draft = draft.withIcon(it) }, onPlace = ::pick, autoFocus = initialLocation == null || searched, tagPrefix = "favorite")
                        FavoriteStudioPhase.LOOK -> FavoriteLookStep(draft, otherFavorites, onIcon = { draft = draft.withIcon(it) },
                            onName = { draft = draft.withName(it) }, onColor = { draft = draft.copy(accentSeed = it) },
                            onShape = { draft = draft.copy(shape = it) }, onChangePlace = { searched = true; go(FavoriteStudioPhase.PLACE) }, tagPrefix = "favorite")
                        FavoriteStudioPhase.SAVED -> saved?.let { favorite ->
                            FavoriteSavedStep(favorite, if (connected) null else partnerFor(favorite.id), connected,
                                onConnect = { other -> if (onConnect(favorite.id, other.id)) { connected = true; haptics.performHapticFeedback(HapticFeedbackType.Confirm) } },
                                onAnother = ::another, onDone = dismiss)
                        }
                    }
                }
            }
            // The save action is docked below the choices and springs in with the second beat.
            CalmVisibility(phase == FavoriteStudioPhase.LOOK, enter = fadeIn(tween(160)) + slideInVertically(spring(dampingRatio = .72f, stiffness = 420f)) { it },
                exit = fadeOut(tween(100)) + slideOutVertically(tween(160)) { it }) {
                Box(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 14.dp + bottom)) {
                    ThemedButton(::save, Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("favorite-save"), enabled = draft.ready) {
                        Text(tr("Favorit speichern"), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}
