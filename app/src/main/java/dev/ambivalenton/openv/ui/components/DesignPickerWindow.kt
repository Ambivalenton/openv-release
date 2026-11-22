// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.material3.FloatingActionButton
import dev.ambivalenton.openv.localization.tr
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** One fixed window and one list owner survive live theme/typography changes.
 * There are no content-dependent Material sheet anchors to interrupt selection. */
@Composable internal fun DesignPickerWindow(title:String,tag:String,onDismiss:()->Unit,
    paletteOpen:Boolean,onPalette:(()->Unit)?,paletteOrigin:PaletteMorphOrigin?=null,content:LazyListScope.()->Unit) {
    val state=rememberLazyListState()
    val target=rememberJourneyScrollTarget(state)
    val scope=rememberCoroutineScope()
    val density=LocalDensity.current
    val view=LocalView.current
    var headerHeight by remember {mutableStateOf(64.dp)}
    DirectModalSheet(onDismiss,target,"$tag-sheet",gesturesEnabled=!paletteOpen,headerHeight=headerHeight) {motion,dismiss->
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal+WindowInsetsSides.Bottom))) {
            Box(Modifier.onSizeChanged {headerHeight=with(density) {it.height.toDp()}}) {
                DesignPickerHeader(title,dismiss)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(state=state,userScrollEnabled=false,
                modifier=Modifier.fillMaxSize().testTag(tag).selectableGroup().semantics {
                    scrollBy {_,y->motion.cancelContentFling();state.dispatchRawDelta(y);true}
                    scrollToIndex {index->
                        motion.cancelContentFling()
                        scope.launch {state.scrollToItem(index)};true
                    }
                },contentPadding=PaddingValues(start=16.dp,end=16.dp,top=16.dp,bottom=104.dp+LocalNavigationClearance.current),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
            if(onPalette!=null) FloatingActionButton(onPalette,Modifier.align(Alignment.BottomEnd).padding(start=20.dp,end=20.dp,top=20.dp,bottom=20.dp+LocalNavigationClearance.current)
                .onGloballyPositioned {coordinates->
                    val screen=IntArray(2);val window=IntArray(2);view.getLocationOnScreen(screen);view.getLocationInWindow(window)
                    paletteOrigin?.screenBounds=coordinates.boundsInWindow().translate((screen[0]-window[0]).toFloat(),(screen[1]-window[1]).toFloat())
                }.graphicsLayer {alpha=if(paletteOpen)0f else 1f}.testTag("design-palette-button")) {
                PaletteAuraIcon(tr("Farbpalette anpassen"))
            }
            }
        }
    }
}
