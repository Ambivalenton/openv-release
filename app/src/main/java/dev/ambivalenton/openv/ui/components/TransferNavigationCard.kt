// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.theme.appCorner

/** True routed geometry is distinguished from a location-only orientation map. No invented paths. */
@Composable fun TransferNavigationCard(node:TransferNode,route:WalkingDirectionsState?=null) {
    if(!node.changesMode||node.arrivalLocation?.hasCoordinates!=true||node.departureLocation?.hasCoordinates!=true){TransferInfoCard(node);return}
    val parts=remember(node,route) {
        if(route is WalkingDirectionsState.Ready && route.route.shape.isNotEmpty())listOf(WalkingMapPart(route.route.shape))
        else listOf(WalkingMapPart(listOfNotNull(node.arrivalLocation)),WalkingMapPart(listOfNotNull(node.departureLocation)))
    }
    var expanded by remember(node.departingLegId){mutableStateOf(false)}
    Surface(Modifier.fillMaxWidth().padding(vertical=8.dp).testTag("transfer-map-${node.departingLegId}"),shape=appCorner(24.dp),color=MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp),horizontalAlignment=androidx.compose.ui.Alignment.CenterHorizontally) {
            FittedText(tr("Umstieg zu %1\$s",node.nextLine),Modifier.fillMaxWidth(),style=MaterialTheme.typography.titleMedium,
                textAlign=androidx.compose.ui.text.style.TextAlign.Center)
            TransferPlatformDetails(node)
            WalkingRouteMap(parts,Modifier.fillMaxWidth().height(180.dp),onClick={expanded=true},tag="transfer-osm-${node.departingLegId}",compactAttribution=true)
            if(route is WalkingDirectionsState.Ready)Text(tr("Fußweg zum Anschluss"),Modifier.fillMaxWidth(),style=MaterialTheme.typography.labelSmall,
                textAlign=androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
    if(expanded)WalkingRouteMapDialog(tr("Umstieg zu %1\$s",node.nextLine),parts){expanded=false}
}
