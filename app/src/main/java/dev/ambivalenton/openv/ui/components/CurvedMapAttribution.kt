// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** OSM specifies legibility, not a universal legal pixel minimum. Never shrink below 9 sp;
 * the arc grows with accessibility font scaling and the attribution remains a visible link. */
@Composable fun CurvedMapAttribution(modifier:Modifier=Modifier) {
    val uri=LocalUriHandler.current
    val ink=MaterialTheme.colorScheme.onSurfaceVariant
    val face=dev.ambivalenton.openv.ui.theme.appTypeface()
    val paint=remember(face) {Paint(Paint.ANTI_ALIAS_FLAG).apply {typeface=face?:Typeface.create("sans-serif",Typeface.NORMAL);textAlign=Paint.Align.LEFT}}
    val label="© OpenStreetMap"
    Canvas(modifier.fillMaxWidth().heightIn(min=22.dp).height(22.dp)
        .testTag("walking-map-curved-attribution").semantics {contentDescription=label;role=Role.Button}
        .clickable {uri.openUri("https://www.openstreetmap.org/copyright")}) {
        paint.color=ink.toArgb();paint.textSize=9.sp.toPx()
        val radius=size.width/2+paint.textSize*.95f
        val textWidth=paint.measureText(label)
        val sweep=(textWidth/radius*180/Math.PI).toFloat().coerceAtMost(160f)
        val path=Path().apply {addArc(RectF(size.width/2-radius,-size.width/2-radius,
            size.width/2+radius,-size.width/2+radius),90f+sweep/2,-sweep)}
        paint.style=Paint.Style.STROKE;paint.strokeWidth=1.dp.toPx()
        // Monochrome outline opposite to the theme ink (dark themes get a dark edge).
        paint.color=dev.ambivalenton.openv.ui.theme.contrastOutline(ink).toArgb()
        drawContext.canvas.nativeCanvas.drawTextOnPath(label,path,0f,0f,paint)
        paint.style=Paint.Style.FILL;paint.color=ink.toArgb()
        drawContext.canvas.nativeCanvas.drawTextOnPath(label,path,0f,0f,paint)
    }
}
