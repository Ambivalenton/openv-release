// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.*
import kotlin.math.sin

@Composable fun TransferInfoCard(node:TransferNode) {
    Surface(Modifier.fillMaxWidth().padding(vertical=6.dp).testTag("transfer-${node.departingLegId}"),
        shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.secondaryContainer) {
        TransferPlatformDetails(node,Modifier.padding(12.dp))
    }
}

@Composable fun JourneyAlertBanner(alert:JourneyAlert) {
    Surface(Modifier.fillMaxWidth().padding(vertical=8.dp).testTag("journey-alert-banner")
        .semantics {liveRegion=LiveRegionMode.Assertive},shape=MaterialTheme.shapes.medium,
        color=MaterialTheme.colorScheme.errorContainer,contentColor=MaterialTheme.colorScheme.onErrorContainer) {
        Column(Modifier.padding(12.dp)) {
            FittedText(alert.title,style=MaterialTheme.typography.titleMedium)
            Text(alert.message,style=MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Typographic glow propagates through every explicitly styled label in the pinned card. */
@Composable fun AlertTextGlow(active:Boolean,content:@Composable ()->Unit) {
    if(dev.ambivalenton.openv.ui.theme.LocalAppSettings.current.reduceMotion) {content();return}
    val phase=rememberSegmentPhase(active)
    val ink=MaterialTheme.colorScheme.primary
    val t=MaterialTheme.typography
    if(!active) content() else {
        val shadow=Shadow(ink.copy(alpha=.3f+.15f*sin(phase.value)),blurRadius=5f+2f*sin(phase.value))
        MaterialTheme(typography=t.copy(
            displayLarge=t.displayLarge.copy(shadow=shadow),displayMedium=t.displayMedium.copy(shadow=shadow),displaySmall=t.displaySmall.copy(shadow=shadow),
            headlineLarge=t.headlineLarge.copy(shadow=shadow),headlineMedium=t.headlineMedium.copy(shadow=shadow),headlineSmall=t.headlineSmall.copy(shadow=shadow),
            titleLarge=t.titleLarge.copy(shadow=shadow),titleMedium=t.titleMedium.copy(shadow=shadow),titleSmall=t.titleSmall.copy(shadow=shadow),
            bodyLarge=t.bodyLarge.copy(shadow=shadow),bodyMedium=t.bodyMedium.copy(shadow=shadow),bodySmall=t.bodySmall.copy(shadow=shadow),
            labelLarge=t.labelLarge.copy(shadow=shadow),labelMedium=t.labelMedium.copy(shadow=shadow),labelSmall=t.labelSmall.copy(shadow=shadow)),content=content)
    }
}

@Composable fun PaletteAuraIcon(description:String?=null) {
    if(dev.ambivalenton.openv.ui.theme.LocalAppSettings.current.reduceMotion) {AppIcon(R.drawable.ic_palette,description,Modifier.size(24.dp));return}
    val phase=rememberSegmentPhase(true,3_400)
    val ink=MaterialTheme.colorScheme.primary
    Box(Modifier.size(40.dp).drawBehind {
        val pulse=.5f-.5f*kotlin.math.cos(phase.value)
        val radius=size.minDimension/2
        drawCircle(Brush.radialGradient(listOf(ink.copy(alpha=.18f+.34f*pulse),ink.copy(alpha=.08f+.12f*pulse),Color.Transparent),radius=radius))
        drawCircle(Brush.radialGradient(listOf(ink.copy(alpha=.12f+.18f*pulse),Color.Transparent),radius=radius*.68f),radius=radius*.68f)
    },contentAlignment=androidx.compose.ui.Alignment.Center) {
        AppIcon(R.drawable.ic_palette,description,Modifier.size(24.dp).graphicsLayer {
            val pulse=.5f-.5f*kotlin.math.cos(phase.value)
            scaleX=1f+.18f*pulse;scaleY=scaleX
        },tint=ink)
    }
}

/** Separate platform facts remain readable at transfers instead of disappearing in prose. */
/** Centred lines; multi-line text and large fonts wrap centred, the reading order is unchanged. */
@Composable internal fun TransferPlatformDetails(node:TransferNode,modifier:Modifier=Modifier) {
    val centre=androidx.compose.ui.text.style.TextAlign.Center
    Column(modifier.fillMaxWidth().testTag("transfer-details-${node.departingLegId}"),verticalArrangement=Arrangement.spacedBy(5.dp),
        horizontalAlignment=androidx.compose.ui.Alignment.CenterHorizontally) {
        Text(dev.ambivalenton.openv.localization.tr("Weiter mit %1\$s Richtung %2\$s",node.nextLine,node.nextDirection),Modifier.fillMaxWidth(),
            style=MaterialTheme.typography.bodyMedium,textAlign=centre)
        node.arrivalPlatform?.let {Text(dev.ambivalenton.openv.localization.tr("Ankunft %1\$s",it),Modifier.fillMaxWidth(),
            style=MaterialTheme.typography.bodyMedium,textAlign=centre)}
        node.departurePlatform?.let {Text(dev.ambivalenton.openv.localization.tr("Nächster Umstieg"),Modifier.fillMaxWidth(),
            style=MaterialTheme.typography.labelMedium,textAlign=centre)
            Text(it,Modifier.fillMaxWidth().testTag("transfer-platform-${node.departingLegId}"),
            style=MaterialTheme.typography.titleLarge,fontWeight=androidx.compose.ui.text.font.FontWeight.Bold,
            color=MaterialTheme.colorScheme.primary,textAlign=centre)}
    }
}
