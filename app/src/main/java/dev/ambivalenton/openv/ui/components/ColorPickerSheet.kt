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
import dev.ambivalenton.openv.data.model.DesignColorRole
import dev.ambivalenton.openv.color.utilities.Hct
import dev.ambivalenton.openv.ui.ColorPickerViewModel
import dev.ambivalenton.openv.ui.theme.*
import kotlin.math.*

/** Presets first. Advanced HSV editing is an independent, cancellable dialog. */
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
            viewModel(
                factory =
                    remember(app) {
                        viewModelFactory { initializer { app.koin.get<ColorPickerViewModel>() } }
                    }
            )
        else null
    val history = paletteVm?.history?.collectAsStateWithLifecycle()?.value
    val error = paletteVm?.error?.collectAsStateWithLifecycle()?.value
    var chosen by rememberSaveable(seed) { mutableStateOf(seed) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var manualColor by rememberSaveable(seed) { mutableStateOf<Long?>(null) }
    fun confirm(color: Long?) {
        if (paletteVm != null) paletteVm.save(color) { onSave(color) }
        else onSave(color)
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        ReleaseDialogWindowOnDispose()
        Box(Modifier.fillMaxWidth()) {
        Column(
            Modifier.imePadding().fillMaxWidth().heightIn(max=LocalConfiguration.current.screenHeightDp.dp*.85f)
                .testTag("color-picker-content"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Keep the picker and close actions reachable while scrolling long colour menus.
            Row(Modifier.fillMaxWidth().padding(start=24.dp,end=8.dp),verticalAlignment=Alignment.CenterVertically) {
                FittedText(title,Modifier.weight(1f),style=MaterialTheme.typography.headlineSmall)
                IconButton(onDismiss,Modifier.testTag("color-picker-close")) {
                    AppIcon(R.drawable.ic_close,tr("Farbauswahl schließen"))
                }
            }
            Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()).padding(start=24.dp,end=24.dp,bottom=104.dp),
                verticalArrangement=Arrangement.spacedBy(12.dp)) {
            previewContent?.let {preview->Box(Modifier.fillMaxWidth().testTag("color-role-preview")) {preview(chosen)}}
            FilterChip(chosen == null, { chosen = null }, label = { Text(inheritLabel) })
            FittedText(designColors?.let {tr("Farbwelt · %1\$s",it.title)} ?: tr("20 Material-Paletten"),
                Modifier.testTag("color-palette-context"),style = MaterialTheme.typography.titleSmall)
            if(designColors!=null) {
                Text(tr(if(designColors.role==DesignColorRole.BACKGROUND || designColors.role==DesignColorRole.SURFACE)
                    "5 Hintergrundfarben" else if(designColors.role==DesignColorRole.TEXT) "10 Textfarben" else "10 Akzentfarben"),
                    style=MaterialTheme.typography.labelLarge)
                DesignColorGrid(designColors,chosen) {chosen=it}
            } else FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                maxItemsInEachRow = 5,
            ) {
                favoriteColorPresets.forEach { preset ->
                    ColorSwatch(preset.seed, preset.name, chosen == preset.seed) {
                        chosen = preset.seed
                    }
                }
            }
            ColorSelectionButton(tr("Eigene Farbe wählen"), manualColor ?: (MaterialTheme.colorScheme.primary.toArgb().toLong() and 0xffffffffL),
                { advanced = true },Modifier.testTag("color-picker-advanced"))
            history?.let { colors ->
                Section(tr("Gespeicherte Farben")) {
                    if (colors.recent.isNotEmpty()) {
                        FittedText(tr("Zuletzt verwendet"), style = MaterialTheme.typography.titleSmall)
                        FlowRow {
                            colors.recent.forEach { color ->
                                ColorSwatch(color, colorHex(color, allowAlpha), chosen == color) {
                                    chosen = if (allowAlpha) color else color or 0xff000000L
                                }
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FittedText(
                            tr("Farb-Favoriten"),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        TextButton(
                            { chosen?.let { paletteVm.toggleFavorite(it) } },
                            enabled = chosen != null,
                        ) {
                            Text(if (chosen in colors.favorites) tr("★ Entfernen") else tr("☆ Merken"))
                        }
                    }
                    FlowRow {
                        colors.favorites.forEach { color ->
                            ColorSwatch(color, colorHex(color, allowAlpha), chosen == color) {
                                chosen = if (allowAlpha) color else color or 0xff000000L
                            }
                        }
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        ColorApplyFab(chosen,{confirm(chosen)},Modifier.align(Alignment.BottomEnd).padding(20.dp).testTag("color-picker-apply"))
        }
    }
    if (advanced)
        AdvancedColorDialog(
            chosen ?: designColors?.swatches?.firstOrNull()?.color ?: favoriteColorPresets.first().seed,
            allowAlpha,
            {
                chosen = it
                manualColor = it
                advanced = false
            },
            { advanced = false },
            previewContent,
        )
}

/** Fixed five equal columns. Chunked rows cannot reflow into a 4+4+2 palette on compact screens. */
@Composable private fun DesignColorGrid(colors:DesignColorSet,chosen:Long?,onChoose:(Long)->Unit) {
    Column(Modifier.fillMaxWidth().testTag("design-color-grid").selectableGroup(),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        colors.swatches.chunked(5).forEachIndexed {row,swatches->
            Row(Modifier.fillMaxWidth().testTag("design-color-row-$row")) {
                swatches.forEach {swatch->
                    ColorSwatch(swatch.color,tr(swatch.nameKey),chosen==swatch.color,Modifier.weight(1f)) {onChoose(swatch.color)}
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

@Composable
internal fun AdvancedColorDialog(
    seed: Long,
    allowAlpha: Boolean,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
    previewContent: (@Composable (Long?) -> Unit)? = null,
    themeAccentColor:Long?=null,
    recentColors:List<Long> = emptyList(),
) {
    var working by rememberSaveable(seed) { mutableLongStateOf(seed) }
    val initialHsv =
        remember(seed) { FloatArray(3).also { NativeColor.colorToHSV(seed.toInt(), it) } }
    var hue by rememberSaveable(seed) { mutableFloatStateOf(initialHsv[0]) }
    var saturation by rememberSaveable(seed) { mutableFloatStateOf(initialHsv[1]) }
    var value by rememberSaveable(seed) { mutableFloatStateOf(initialHsv[2]) }
    var alpha by rememberSaveable(seed) { mutableFloatStateOf((seed ushr 24 and 255) / 255f) }
    var sourceHue by rememberSaveable(seed) { mutableDoubleStateOf(Hct.fromInt(seed.toInt()).hue) }
    var sourceChroma by
        rememberSaveable(seed) { mutableDoubleStateOf(Hct.fromInt(seed.toInt()).chroma) }
    fun choose(color: Long, source: Boolean = true) {
        working = if (allowAlpha) color else color or 0xff000000L
        val hsv = FloatArray(3).also { NativeColor.colorToHSV(working.toInt(), it) }
        hue = hsv[0]
        saturation = hsv[1]
        value = hsv[2]
        alpha = (working ushr 24 and 255) / 255f
        if (source)
            Hct.fromInt(working.toInt()).let {
                sourceHue = it.hue
                sourceChroma = it.chroma
            }
    }
    fun change(h: Float = hue, s: Float = saturation, v: Float = value, a: Float = alpha) {
        choose(
            NativeColor.HSVToColor((a * 255).roundToInt(), floatArrayOf(h, s, v)).toLong() and
                0xffffffffL
        )
        hue = h
        saturation = s
        value = v
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        ReleaseDialogWindowOnDispose()
        Surface(
            Modifier.fillMaxWidth().padding(16.dp).heightIn(max = 760.dp).testTag("advanced-color-dialog"),
            shape = MaterialTheme.shapes.large,
        ) {
            Box(Modifier.fillMaxWidth()) {
            Column(
                Modifier.imePadding().verticalScroll(rememberScrollState()).padding(start=20.dp,end=20.dp,top=20.dp,bottom=104.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    FittedText(tr("Eigene Farbe"),Modifier.weight(1f),style=MaterialTheme.typography.headlineSmall)
                    IconButton(onDismiss) {AppIcon(R.drawable.ic_close,tr("Farbauswahl schließen"))}
                }
                previewContent?.invoke(working)
                themeAccentColor?.let {accent->
                    ThemedTonalButton({choose(accent)},Modifier.fillMaxWidth().testTag("picker-theme-accent")) {
                        Text(tr("Akzentfarbe des App-Designs wählen"))
                    }
                }
                if(recentColors.isNotEmpty()) {
                    FittedText(tr("Zuletzt gewählt"),style=MaterialTheme.typography.titleSmall)
                    FlowRow(Modifier.testTag("picker-recent-colors")) {
                        recentColors.distinct().take(12).forEach {color->
                            ColorSwatch(color,colorHex(color,false),working==color) {choose(color)}
                        }
                    }
                }
                Text(tr("Tippen zum Wählen · Gedrückt halten zum Ziehen · Daneben scrollen"),style=MaterialTheme.typography.bodySmall)
                ColorWheel(hue, saturation, value) { h, s -> change(h = h, s = s) }
                Text(
                    tr("Tonalität · %1\$s", Hct.fromInt(working.toInt()).tone.roundToInt().coerceIn(10, 90)),
                    style = MaterialTheme.typography.labelLarge,
                )
                Slider(
                    Hct.fromInt(working.toInt()).tone.toFloat().coerceIn(10f, 90f),
                    {
                        choose(
                            toneColor(sourceHue, sourceChroma, it.toDouble()) and
                                0xffffffL or
                                ((alpha * 255).roundToInt().toLong() shl 24),
                            false,
                        )
                    },
                    valueRange = 10f..90f,
                    modifier =
                        Modifier.semantics {
                            contentDescription = tr("Tonalität, gleicher Farbton, Tone 10 bis 90")
                        },
                )
                PickerSlider(tr("Farbton"), hue, 0f..359.99f) { change(h = it) }
                PickerSlider(tr("Sättigung"), saturation, 0f..1f) { change(s = it) }
                PickerSlider(tr("Helligkeit"), value, 0f..1f) { change(v = it) }
                if (allowAlpha) PickerSlider(tr("Deckkraft"), alpha, 0f..1f) { change(a = it) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onDismiss) { Text(tr("Abbrechen")) }
                }
            }
            ColorApplyFab(working,{onConfirm(working)},Modifier.align(Alignment.BottomEnd).padding(20.dp).testTag("advanced-color-apply"))
            }
        }
    }
}

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

@Composable
private fun PickerSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column {
        Text(
            tr("%1\$s · %2\$s%3\$s", label, (if (range.endInclusive > 1f) value else value * 100).roundToInt(), if (range.endInclusive > 1f) "°" else "%"),
            style = MaterialTheme.typography.labelLarge,
        )
        Slider(
            value,
            onChange,
            valueRange = range,
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

@Composable
private fun ColorWheel(
    hue: Float,
    saturation: Float,
    value: Float,
    onChange: (Float, Float) -> Unit,
) {
    val callback by rememberUpdatedState(onChange)
    val ring = remember { (0..6).map { Color.hsv((it * 60f).coerceAtMost(359.99f), 1f, 1f) } }
    Canvas(
        Modifier.fillMaxWidth()
            .height(220.dp).testTag("color-wheel")
            .semantics {
                stateDescription=tr("Farbton %1\$s Grad, Sättigung %2\$s Prozent",hue.roundToInt(),(saturation*100).roundToInt())
                contentDescription =
                    tr("Farbrad; alternativ Farbton und Sättigung mit den Schiebereglern einstellen")
            }
            .pointerInput(Unit) {
                fun update(p: Offset) {
                    val dx = p.x - size.width / 2f
                    val dy = p.y - size.height / 2f
                    val h = ((atan2(dy, dx) * 180 / PI + 360) % 360).toFloat()
                    val s = (hypot(dx, dy) / (min(size.width, size.height) / 2f)).coerceIn(0f, 1f)
                    callback(h, s)
                }
                detectTapGestures { if ((it-Offset(size.width/2f,size.height/2f)).getDistance() <= min(size.width,size.height)/2f) update(it) }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down=awaitFirstDown(requireUnconsumed=false)
                    val center=Offset(size.width/2f,size.height/2f)
                    val radius=min(size.width,size.height)/2f
                    // A gesture begun in the surrounding scroll gutter never acquires the wheel.
                    if((down.position-center).getDistance()>radius) return@awaitEachGesture
                    val held=awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                    fun update(position:Offset) {
                        val d=position-center
                        callback(((atan2(d.y,d.x)*180/PI+360)%360).toFloat(),(d.getDistance()/radius).coerceIn(0f,1f))
                    }
                    held.consume();update(held.position)
                    drag(held.id) {change->change.consume();update(change.position)}
                }
            }
    ) {
        val radius = min(size.width, size.height) / 2f
        drawCircle(Brush.sweepGradient(ring, center), radius)
        drawCircle(
            Brush.radialGradient(listOf(Color.White, Color.Transparent), center, radius),
            radius,
        )
        drawCircle(Color.Black.copy(alpha = 1f - value), radius)
        val angle = hue * PI / 180
        val pointer =
            center + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * (saturation * radius)
        drawCircle(Color.Black, 10.dp.toPx(), pointer)
        drawCircle(Color.White, 7.dp.toPx(), pointer)
        drawCircle(Color.hsv(hue, saturation, value), 4.dp.toPx(), pointer)
    }
}
