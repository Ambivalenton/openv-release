// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.theme.appCorner
import kotlin.math.abs

@Composable internal fun SearchOrderSetting(settings:AppSettings,update:((AppSettings)->AppSettings)->Unit) {
    var order by remember(settings.searchResultOrder){mutableStateOf(normalizedSearchOrder(settings.searchResultOrder))}
    var dragging by remember {mutableStateOf<SearchResultGroup?>(null)}
    var offset by remember {mutableFloatStateOf(0f)}
    var start by remember {mutableStateOf(order)}
    val heights=remember {mutableMapOf<SearchResultGroup,Int>()}
    val latestUpdate by rememberUpdatedState(update)
    fun persist(next:List<SearchResultGroup>){order=next;latestUpdate {it.copy(searchResultOrder=next)}}
    Column(Modifier.testTag("search-priority-options"),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text(tr("Deine Trefferreihenfolge"),style=MaterialTheme.typography.titleMedium)
        Text(tr("Ziehe eine Gruppe am Griff oder verschiebe sie mit den Pfeilen. Innerhalb der Haltestellen stehen bei einer Stadtsuche die Bahnhöfe vorn."),style=MaterialTheme.typography.bodySmall)
        order.forEachIndexed {index,group->key(group) {
            Surface(shape=appCorner(18.dp),color=MaterialTheme.colorScheme.surfaceContainerLow,
                border=BorderStroke(1.dp,if(dragging==group)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                modifier=Modifier.fillMaxWidth().testTag("search-order-${group.name}").onSizeChanged {heights[group]=it.height}
                    .zIndex(if(dragging==group)1f else 0f).graphicsLayer {translationY=if(dragging==group)offset else 0f}) {
                Row(Modifier.padding(8.dp).heightIn(min=64.dp),verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).testTag("search-order-drag-${group.name}").semantics {contentDescription=tr("Zum Verschieben gedrückt halten")}
                        .pointerInput(group){detectDragGesturesAfterLongPress(onDragStart={dragging=group;offset=0f;start=order},
                            onDragEnd={val final=order;dragging=null;offset=0f;persist(final)},onDragCancel={order=start;dragging=null;offset=0f},onDrag={change,amount->
                                change.consume();offset+=amount.y;val height=(heights[group]?:72).toFloat()+10.dp.toPx()
                                if(abs(offset)>height*.6f){val direction=if(offset>0)1 else -1;val next=movedSearchGroup(order,group,direction)
                                    if(next!=order){order=next;offset-=direction*height}}
                            })},contentAlignment=Alignment.Center){AppIcon(R.drawable.ic_reorder,null)}
                    Text((index+1).toString(),Modifier.width(22.dp),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
                    Text(group.title,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                    IconButton({persist(movedSearchGroup(order,group,-1))},Modifier.testTag("search-order-up-${group.name}"),enabled=index>0&&dragging==null){AppIcon(R.drawable.ic_expand,tr("Treffergruppe nach oben verschieben"),Modifier.rotate(180f))}
                    IconButton({persist(movedSearchGroup(order,group,1))},Modifier.testTag("search-order-down-${group.name}"),enabled=index<order.lastIndex&&dragging==null){AppIcon(R.drawable.ic_expand,tr("Treffergruppe nach unten verschieben"))}
                }
            }
        }}
    }
}
