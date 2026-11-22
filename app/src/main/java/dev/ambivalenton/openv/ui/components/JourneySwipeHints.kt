// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.reduceMotion
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.theme.LocalAppSettings

@Composable fun JourneySwipeHints(hasEarlier:Boolean,hasLater:Boolean,modifier:Modifier=Modifier) {
    if(!hasEarlier&&!hasLater)return
    if(!hintsShown)return
    val quiet=LocalAppSettings.current.reduceMotion
    val phase=if(quiet)0f else rememberInfiniteTransition(label="journey-swipe-hints").animateFloat(0f,1f,
        infiniteRepeatable(tween(1400),RepeatMode.Reverse),label="swipe-hint-travel").value
    Row(modifier.fillMaxWidth().height(22.dp).testTag("journey-swipe-hints"),verticalAlignment=Alignment.CenterVertically) {
        if(hasEarlier)AppIcon(R.drawable.ic_chevron_right,tr("Zur vorigen Verbindung wischen"),Modifier.size(18.dp).graphicsLayer{rotationZ=180f;translationX=-phase*3.dp.toPx();alpha=.28f+phase*.15f},tint=MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        if(hasLater)AppIcon(R.drawable.ic_chevron_right,tr("Zur nächsten Verbindung wischen"),Modifier.size(18.dp).graphicsLayer{translationX=phase*3.dp.toPx();alpha=.28f+phase*.15f},tint=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
