// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.localization.tr

/** The required attribution sits on a calm, stable pill. Ink and monochrome outline switch between
 * black and white by contrast to the composed pill colour (transparency over the map included). */
@Composable internal fun OutlinedMapAttribution(modifier:Modifier=Modifier,
    style:TextStyle=MaterialTheme.typography.labelSmall,background:Color=MaterialTheme.colorScheme.surface.copy(alpha=.94f)) {
    val label=tr("© OpenStreetMap")
    val stroke=with(LocalDensity.current){1.dp.toPx()}
    val ink=dev.ambivalenton.openv.ui.theme.contrastInk(background)
    Box(modifier) {
        Text(label,Modifier.clearAndSetSemantics {},style=style.copy(drawStyle=Stroke(stroke)),
            color=dev.ambivalenton.openv.ui.theme.contrastOutline(ink),maxLines=1)
        Text(label,style=style,color=ink,maxLines=1)
    }
}
