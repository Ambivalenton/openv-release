// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.semantics.*

val CachedJourneyKey=SemanticsPropertyKey<Boolean>("CachedJourneyAwaitingRefresh")
/** Fade back to actual theme colours only when this connection was individually reconciled. */
@Composable fun Modifier.cachedJourneyTint(stale:Boolean):Modifier {
    val saturation by animateFloatAsState(if(stale) .12f else 1f,calmSpec(tween(420)),label="cached-journey-colour")
    return semantics {this[CachedJourneyKey]=stale}.drawWithCache {
        val paint=Paint().apply {colorFilter=ColorFilter.colorMatrix(ColorMatrix().apply {setToSaturation(saturation)})}
        onDrawWithContent {
            if(saturation>=.999f) drawContent() else drawIntoCanvas {canvas->
                canvas.saveLayer(Rect(Offset.Zero,size),paint);drawContent();canvas.restore()
            }
        }
    }
}
