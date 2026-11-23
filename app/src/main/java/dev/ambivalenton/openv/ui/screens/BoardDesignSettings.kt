// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.ui.screens

import dev.ambivalenton.openv.ui.components.FittedText

import dev.ambivalenton.openv.ui.components.AppModalBottomSheet as ModalBottomSheet

import dev.ambivalenton.openv.localization.tr

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.boardCandidates
import dev.ambivalenton.openv.data.repository.boardLocationKey
import dev.ambivalenton.openv.rendering.boardPresentation
import dev.ambivalenton.openv.rendering.BoardRenderer
import dev.ambivalenton.openv.ui.AppViewModel
import dev.ambivalenton.openv.ui.components.ColorPickerSheet
import dev.ambivalenton.openv.ui.components.ColorSelectionButton
import dev.ambivalenton.openv.ui.components.ContinuousSettingSlider
import dev.ambivalenton.openv.ui.components.ToggleRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BoardDesignSheet(state: StoredState, vm: AppViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FittedText(tr("Anzeigetafel"), style = MaterialTheme.typography.headlineSmall)
            BoardDesignSettings(state, vm)
        }
    }
}

@Composable
internal fun BoardDesignSettings(state: StoredState,vm: AppViewModel,
    beforeProducts: @Composable ()->Unit={},extraSwitches: @Composable ()->Unit={}) {
    BoardDesignSettingsContent(state,beforeProducts,extraSwitches) {change->vm.settings(change)}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BoardDesignSettingsContent(state: StoredState,
    beforeProducts: @Composable ()->Unit={},extraSwitches: @Composable ()->Unit={},
    update: ((AppSettings)->AppSettings)->Unit) {
    var picker by rememberSaveable {mutableStateOf(false)}
    dev.ambivalenton.openv.ui.components.SettingsDisclosure(tr("Anzeigeoptionen"),"board-display-options") {
        BoardDisplayOptions(state,beforeProducts,extraSwitches,update)
    }
    dev.ambivalenton.openv.ui.components.BoardDesignSelector(state) {picker=true}
    if(picker) dev.ambivalenton.openv.ui.components.BoardDesignPicker(state,{design ->
        update {it.copy(board=it.board.copy(design=design))}
    },{picker=false},update=update,footer={BoardOptionsDisclosure(state,update)})
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun BoardDisplayOptions(state:StoredState,beforeProducts:@Composable ()->Unit={},
    extraSwitches:@Composable ()->Unit={},update:((AppSettings)->AppSettings)->Unit) {
    val options=state.settings.board
    beforeProducts()
    FittedText(tr("Verkehrsmittel"), style = MaterialTheme.typography.titleSmall)
    FlowRow(Modifier.testTag("board-products"),horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        selectableProducts.forEach { product ->
            FilterChip(
                product in options.products,
                {
                    update { settings ->
                        settings.copy(
                            board =
                                settings.board.copy(
                                    products =
                                        if (product in settings.board.products)
                                            settings.board.products - product
                                        else settings.board.products + product
                                )
                        )
                    }
                },
                label = { Text(product.label) },
            )
        }
    }
    Column(Modifier.testTag("board-visibility-switches")) {
    extraSwitches()
    BoardRotationIntervalSetting(options.rotationSeconds) {seconds->
        update {it.copy(board=it.board.copy(rotationIntervalSeconds=seconds,autoRotate=seconds>0))}
    }
    ToggleRow(tr("Anzeigetafel ausblenden"), tr("Im Reiter Abfahrten"), options.hiddenInApp) { enabled ->
        update { it.copy(board = it.board.copy(hiddenInApp = enabled)) }
    }
    if(!normalizedBoardDesign(options.design).followsAppTheme) ToggleRow(
        tr("Standard Material Design erzwingen"),
        tr("Gilt für die Anzeigetafel innerhalb der App"),
        options.forceMaterialInApp,
    ) { enabled ->
        update { it.copy(board = it.board.copy(forceMaterialInApp = enabled)) }
    }
    ToggleRow(
        tr("Einstellungsbuttons verbergen"),
        tr("Mehr Platz für Abfahrten im Widget"),
        options.hideSettingsButtons,
    ) { enabled ->
        update { it.copy(board = it.board.copy(hideSettingsButtons = enabled)) }
    }
    }
}

/** Save once on release, while the label and thumb follow every pointer movement. */
@Composable internal fun BoardRotationIntervalSetting(seconds:Int,onChange:(Int)->Unit) {
    var draft by remember(seconds) {mutableFloatStateOf(seconds.toFloat())}
    val rounded=draft.roundToInt().coerceIn(0,120)
    val label=tr("Haltestellen automatisch wechseln")
    val value=when(rounded) {
        0->tr("Nicht wechseln")
        1->tr("Jede Sekunde")
        else->tr("Alle %1\$s Sekunden",rounded)
    }
    Column(Modifier.fillMaxWidth().padding(vertical=8.dp)) {
        Text(label,style=MaterialTheme.typography.titleSmall)
        Text(value,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.primary)
        Slider(draft,{draft=it},onValueChangeFinished={onChange(draft.roundToInt().coerceIn(0,120))},valueRange=0f..120f,
            modifier=Modifier.testTag("board-rotation-interval").semantics {contentDescription=label;stateDescription=value})
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text(tr("Nicht wechseln"),style=MaterialTheme.typography.labelSmall)
            Text(tr("120 Sekunden"),style=MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable internal fun BoardOptionsDisclosure(state:StoredState,vm:AppViewModel) {
    dev.ambivalenton.openv.ui.components.SettingsDisclosure(tr("Anzeigeoptionen"),"board-display-options") {
        BoardDisplayOptions(state,beforeProducts={TextButton(vm::updateWidgets) {Text(tr("Anzeigetafel aktualisieren"))}},
            extraSwitches={BoardLocationControls(state.settings, vm::settings)},update=vm::settings)
    }
}
@Composable internal fun BoardOptionsDisclosure(state:StoredState,update:((AppSettings)->AppSettings)->Unit) {
    dev.ambivalenton.openv.ui.components.SettingsDisclosure(tr("Anzeigeoptionen"),"board-display-options") {
        BoardDisplayOptions(state,update=update)
    }
}


@Composable
private fun BoardDesignPreview(state: StoredState) {
    val clock = dev.ambivalenton.openv.ui.components.rememberWallClock().toInstant().toEpochMilli()
    val context = androidx.compose.ui.platform.LocalContext.current
    val live = boardPresentation(state, MaterialTheme.colorScheme, inApp = true, now = clock,typeface=dev.ambivalenton.openv.ui.theme.AppTypefaces.get(context,state.settings.fontId),
        themeTypeface=dev.ambivalenton.openv.ui.theme.AppTypefaces.get(context,state.settings.appTheme?.bodyFace))
    val sampleStop = Location(tr("Öpen-V · Vorschau"), "preview")
    val model = if (live.rows.isNotEmpty()) live else live.copy(
        title = tr("Öpen-V · Vorschau"),
        caption = tr("Designvorschau"),
        rows = listOf(
            Departure("sample-bus", sampleStop, "42", LineType.BUS, tr("Zentrum"), clock + 180_000, clock + 180_000, DelayInfo(), "2"),
            Departure("sample-subway", sampleStop, "U1", LineType.SUBWAY, tr("Stadtpark"), clock + 360_000, clock + 360_000, DelayInfo(), "1"),
            Departure("sample-train", sampleStop, "RE1", LineType.REGIONAL, tr("Hauptbahnhof"), clock + 540_000, clock + 540_000, DelayInfo(), "4"),
        ),
    )
    val font = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val height = 172f * font.coerceIn(1f, 1.6f)
        val pixels = androidx.compose.ui.platform.LocalDensity.current.density
        val bitmap = remember(model, maxWidth, font, pixels) { BoardRenderer.render(model, maxWidth.value, height, font, density = pixels) }
        Image(bitmap.asImageBitmap(), tr("Designvorschau: %1\$s. %2\$s", model.design.title, model.description()),
            Modifier.fillMaxWidth().height(height.dp), contentScale = androidx.compose.ui.layout.ContentScale.FillBounds)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuickDesignSettings(state: StoredState, vm: AppViewModel) {
    val style = state.settings.widget
    var role by rememberSaveable { mutableStateOf<String?>(null) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ColorSelectionButton(tr("Hintergrund"), style.backgroundColor, { role = tr("Hintergrund") })
        ColorSelectionButton(tr("Symbole"), style.iconColor, { role = tr("Symbole") })
    }
    // Build 107: the widget is flat (no shadows to switch); background and symbol colours remain.
    role?.let { selectedRole ->
        ColorPickerSheet(
            tr("Favoriten · %1\$s", selectedRole),
            if (selectedRole == tr("Hintergrund")) style.backgroundColor else style.iconColor,
            tr("App-Farbe / automatisch"),
            { value ->
                vm.quickWidgetSettings {
                    if (selectedRole == tr("Hintergrund")) it.copy(backgroundColor = value)
                    else it.copy(iconColor = value)
                }
                role = null
            },
            { role = null },
            allowAlpha = false,
        )
    }
}
