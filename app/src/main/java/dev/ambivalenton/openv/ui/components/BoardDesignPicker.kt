// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.rendering.*
import dev.ambivalenton.openv.ui.theme.clipDesignPreview

/** Name-only previews are drawn by the actual board renderer, without requesting departures. */
fun designPreviewPresentation(state: StoredState,scheme: ColorScheme,design: BoardDesign): BoardPresentation {
    val options=state.settings.board.copy(design=design,forceMaterialInApp=false)
    val base=boardPresentation(state.copy(stopColors=emptyMap(),settings=state.settings.copy(board=options)),scheme)
    val stop=Location("preview")
    return base.copy(title="",caption="",emptyMessage="",isDesignPreview=true,
        rows=listOf(Departure("design-preview",stop,"",LineType.BUS,design.title,0,0,DelayInfo())))
}

@Composable fun BoardDesignStrip(state: StoredState,design: BoardDesign,modifier: Modifier=Modifier) {
    val scheme=MaterialTheme.colorScheme;val scale=LocalDensity.current.fontScale
    val model=designPreviewPresentation(state,scheme,design)
    BoxWithConstraints(modifier.fillMaxWidth().clipDesignPreview()) {
        val height=64f*scale.coerceIn(1f,1.4f)
        // The Compose container supplies the complete theme shape; do not bake a second radius into the bitmap.
        val bitmap=remember(model,maxWidth,scale) {BoardRenderer.render(model,maxWidth.value,height,scale,clipCorners=false)}
        Image(bitmap.asImageBitmap(),design.title,Modifier.fillMaxWidth().height(height.dp),contentScale=ContentScale.FillBounds)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun BoardDesignPicker(state: StoredState,onSelect: (BoardDesign)->Unit,onDismiss: ()->Unit,update:(((AppSettings)->AppSettings)->Unit)?=null,
    footer:(@Composable ()->Unit)?=null) {
    var palette by androidx.compose.runtime.saveable.rememberSaveable {mutableStateOf<BoardDesign?>(null)}
    val paletteOrigin=remember {PaletteMorphOrigin()}
    DesignPickerWindow(tr("Anzeigetafel-Design wählen"),"board-design-picker",onDismiss,palette!=null,
        if(update==null||normalizedBoardDesign(state.settings.board.design).followsAppTheme) null else ({palette=state.settings.board.design}),paletteOrigin) {
            if(footer!=null) item(key="display-options") {footer()}
            items(if(state.settings.quietMode) quietBoardDesigns else visibleBoardDesigns,key={it.name}) {design ->
                val selected=normalizedBoardDesign(state.settings.board.design)==design
                Surface(Modifier.fillMaxWidth().testTag("board-design-${design.name}").selectable(
                    selected=selected,role=Role.RadioButton,onClick={onSelect(design)}),
                    shape=MaterialTheme.shapes.medium,border=BorderStroke(if(selected) 2.dp else 1.dp,
                        if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.padding(4.dp)) {
                        BoardDesignStrip(state,design)
                        if(design.description.isNotBlank()) Text(design.description,Modifier.padding(8.dp),style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
    }
    palette?.let {design->if(update!=null) BoardPaletteDialog(state,design,{palette=null},update,paletteOrigin)}
}
