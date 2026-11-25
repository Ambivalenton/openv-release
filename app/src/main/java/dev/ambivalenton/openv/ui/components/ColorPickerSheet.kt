// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.ui.components

import dev.ambivalenton.openv.ui.components.ThemedButton
import dev.ambivalenton.openv.ui.components.ThemedTonalButton

import dev.ambivalenton.openv.ui.components.AppDialog as Dialog
import dev.ambivalenton.openv.ui.components.AppModalBottomSheet as ModalBottomSheet

import dev.ambivalenton.openv.localization.tr

import android.graphics.Color as NativeColor
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.ambivalenton.openv.OpenVApp
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.DesignColorSet
import dev.ambivalenton.openv.data.model.reduceMotion
import dev.ambivalenton.openv.data.model.DesignColorRole
import dev.ambivalenton.openv.color.utilities.Hct
import dev.ambivalenton.openv.ui.ColorPickerViewModel
import dev.ambivalenton.openv.ui.theme.*
import kotlin.math.*

/**
 * Build 112 · one colour surface, no stacked dialogs. Above, always: what is chosen (large, with its
 * name and code) and "Vorher". Then "Auswahl" (the curated colours, recent ones and favourites) or
 * "Mischen" (field, hue and opacity standing still – only the suggestions below them scroll, so
 * scrolling never changes the colour). Below, always: "Übernehmen" in the chosen colour.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ColorPickerSheet(
    title: String,
    seed: Long?,
    inheritLabel: String,
    onSave: (Long?) -> Unit,
    onDismiss: () -> Unit,
    allowAlpha: Boolean = false,
    designColors: DesignColorSet? = null,
    previewContent: (@Composable (Long?) -> Unit)? = null,
) {
    val app = LocalContext.current.applicationContext as? OpenVApp
    val owner = LocalViewModelStoreOwner.current
    val paletteVm: ColorPickerViewModel? =
        if (designColors == null && app != null && owner != null)
            viewModel(factory = remember(app) { viewModelFactory { initializer { app.koin.get<ColorPickerViewModel>() } } })
        else null
    val history = paletteVm?.history?.collectAsStateWithLifecycle()?.value
    val error = paletteVm?.error?.collectAsStateWithLifecycle()?.value
    var chosen by rememberSaveable(seed) { mutableStateOf(seed) }
    var mixing by rememberSaveable { mutableStateOf(false) }
    val fallback = seed ?: designColors?.swatches?.firstOrNull()?.color ?: favoriteColorPresets.first().seed
    val draft = rememberColorDraft(fallback, allowAlpha)
    fun pick(color: Long?) {
        chosen = color?.let { if (allowAlpha) it else it or 0xff000000L }
        color?.let { draft.choose(it, withAlpha = true) }
    }
    fun confirm(color: Long?) {
        if (paletteVm != null) paletteVm.save(color) { onSave(color) } else onSave(color)
    }
    val quiet = LocalAppSettings.current.reduceMotion
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        ReleaseDialogWindowOnDispose()
        Box(Modifier.fillMaxWidth()) {
            val room = LocalConfiguration.current.screenHeightDp.dp * .88f
            Column(Modifier.imePadding().fillMaxWidth().heightIn(max = room).testTag("color-picker-content"),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FittedText(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                    IconButton(onDismiss, Modifier.testTag("color-picker-close")) { AppIcon(R.drawable.ic_close, tr("Farbauswahl schließen")) }
                }
                ColorHero(seed, chosen, allowAlpha, inheritLabel, { pick(seed) }, Modifier.padding(horizontal = 24.dp),
                    onHex = { color, withAlpha -> draft.choose(color, withAlpha); chosen = draft.value })
                ColorModeSwitch(mixing, { mix ->
                    mixing = mix
                    // Mixing starts from what is chosen; the design's own colour starts from its first suggestion.
                    if (mix && chosen == null) chosen = draft.value
                }, Modifier.padding(horizontal = 24.dp))
                Box(Modifier.weight(1f, fill = false)) {
                    AnimatedContent(mixing, label = "color-page", transitionSpec = {
                        if (quiet) EnterTransition.None togetherWith ExitTransition.None
                        else (slideInHorizontally(spring(dampingRatio = .86f, stiffness = 420f)) { w -> if (targetState) w / 5 else -w / 5 } +
                            fadeIn(spring(stiffness = 600f))) togetherWith
                            (slideOutHorizontally(spring(dampingRatio = .86f, stiffness = 420f)) { w -> if (targetState) -w / 5 else w / 5 } +
                                fadeOut(spring(stiffness = 900f)))
                    }) { mix ->
                        // Build 120: one page each, no scrolling: the mixing field takes what is left; the choices shrink to fit.
                        if (mix) ColorMixPage(draft, history?.recent.orEmpty(), designColors?.swatches?.firstOrNull()?.color, { chosen = draft.value },
                            Modifier.padding(horizontal = 24.dp))
                        else ShrinkToFit(Modifier.testTag("color-choices")) { Column(Modifier.padding(horizontal = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            previewContent?.let { preview -> Box(Modifier.fillMaxWidth().testTag("color-role-preview")) { preview(chosen) } }
                            FilterChip(chosen == null, { pick(null) }, label = { Text(inheritLabel) })
                            FittedText(designColors?.let { tr("Farbwelt · %1\$s", it.title) } ?: tr("20 Material-Paletten"),
                                Modifier.testTag("color-palette-context"), style = MaterialTheme.typography.titleSmall)
                            if (designColors != null) {
                                Text(tr(if (designColors.role == DesignColorRole.BACKGROUND || designColors.role == DesignColorRole.SURFACE)
                                    "5 Hintergrundfarben" else if (designColors.role == DesignColorRole.TEXT) "10 Textfarben" else "10 Akzentfarben"),
                                    style = MaterialTheme.typography.labelLarge)
                                DesignColorGrid(designColors, chosen) { pick(it) }
                            } else FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, maxItemsInEachRow = 5) {
                                favoriteColorPresets.forEach { preset ->
                                    ColorSwatch(preset.seed, preset.name, chosen == preset.seed) { pick(preset.seed) }
                                }
                            }
                            history?.let { colors ->
                                if (colors.recent.isNotEmpty())
                                    SwatchRow(tr("Zuletzt verwendet"), colors.recent, chosen, { pick(it) })
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FittedText(tr("Farb-Favoriten"), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                                    TextButton({ chosen?.let { paletteVm.toggleFavorite(it) } }, enabled = chosen != null) {
                                        Text(if (chosen in colors.favorites) tr("★ Entfernen") else tr("☆ Merken"))
                                    }
                                }
                                if (colors.favorites.isNotEmpty()) SwatchRow(tr("Gemerkt"), colors.favorites, chosen, { pick(it) })
                            }
                            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            Spacer(Modifier.height(4.dp))
                        } }
                    }
                }
                ApplyColorButton(chosen, allowAlpha, { confirm(chosen) },
                    Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp).testTag("color-picker-apply"), fillWidth = true)
            }
        }
    }
}

/** Fixed five equal columns. Chunked rows cannot reflow into a 4+4+2 palette on compact screens. */
@Composable private fun DesignColorGrid(colors:DesignColorSet,chosen:Long?,onChoose:(Long)->Unit) {
    Column(Modifier.fillMaxWidth().testTag("design-color-grid").selectableGroup(),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        colors.swatches.chunked(5).forEachIndexed {row,swatches->
            Row(Modifier.fillMaxWidth().testTag("design-color-row-$row")) {
                swatches.forEach {swatch->
                    ColorSwatch(swatch.color,swatch.label(),chosen==swatch.color,Modifier.weight(1f)) {onChoose(swatch.color)}
                }
            }
        }
    }
}

/** Used consistently wherever the app offers a colour action, including timeline roles. */
@Composable
fun ColorSelectionButton(
    label: String,
    color: Long?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fill = color?.let { Color(it) } ?: MaterialTheme.colorScheme.primaryContainer
    ThemedButton(
        onClick,
        modifier.fillMaxWidth().heightIn(min = 52.dp).semantics {
            stateDescription =
                color?.let { colorHex(it, (it ushr 24 and 255) != 255L) } ?: tr("Automatisch")
        },
        colors =
            ButtonDefaults.buttonColors(
                containerColor = fill,
                contentColor = contrastContentColor(fill),
            ),
        border =
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(label)
    }
}

/** Build 108 · the colour studio ([ColorStudioDialog]); kept under this name for every caller. */
@Composable
internal fun AdvancedColorDialog(
    seed: Long,
    allowAlpha: Boolean,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
    previewContent: (@Composable (Long?) -> Unit)? = null,
    themeAccentColor:Long?=null,
    recentColors:List<Long> = emptyList(),
) = ColorStudioDialog(seed, allowAlpha, onConfirm, onDismiss, previewContent, themeAccentColor, recentColors)

@Composable internal fun ColorApplyFab(color:Long?,onClick:()->Unit,modifier:Modifier=Modifier) {
    val fill=color?.let {Color(it)} ?: MaterialTheme.colorScheme.primary
    FloatingActionButton(onClick,modifier.semantics {stateDescription=color?.let {colorHex(it,(it ushr 24 and 255)!=255L)} ?: tr("Designstandard")},containerColor=fill,contentColor=contrastContentColor(fill)) {
        AppIcon(R.drawable.ic_check,tr("Farbe übernehmen"))
    }
}

@Composable
internal fun ColorSwatch(color: Long, label: String, selected: Boolean, modifier:Modifier=Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(48.dp)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = label
                this.selected = selected
            },
        contentAlignment=Alignment.Center,
    ) {
        val fill=Color(color)
        val contrast=if(fill.compositeOver(MaterialTheme.colorScheme.surface).luminance()>.45f) Color.Black else Color.White
        Box(Modifier.size(if(selected) 46.dp else 40.dp)
            .border(if(selected) 2.dp else 1.dp,if(selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,CircleShape)
            .padding(if(selected) 4.dp else 3.dp)
            .background(fill,CircleShape),contentAlignment=Alignment.Center) {
            if(selected) Canvas(Modifier.size(22.dp).testTag("color-swatch-selected-check")) {
                val p=Path().apply {moveTo(size.width*.15f,size.height*.5f);lineTo(size.width*.4f,size.height*.76f);lineTo(size.width*.86f,size.height*.2f)}
                drawPath(p,if(contrast==Color.White) Color.Black else Color.White,style=androidx.compose.ui.graphics.drawscope.Stroke(5.dp.toPx(),cap=StrokeCap.Round,join=StrokeJoin.Round))
                drawPath(p,contrast,style=androidx.compose.ui.graphics.drawscope.Stroke(2.8.dp.toPx(),cap=StrokeCap.Round,join=StrokeJoin.Round))
            }
        }
    }
}
