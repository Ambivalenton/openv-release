// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import dev.ambivalenton.openv.data.model.reduceMotion

import android.animation.ValueAnimator
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.localization.tr
import kotlin.math.*

@Composable fun WaypointPlusButton(expanded:Boolean,onClick:()->Unit,enabled:Boolean=true,modifier:Modifier=Modifier) {
    val reduced=dev.ambivalenton.openv.ui.theme.LocalAppSettings.current.reduceMotion || !ValueAnimator.areAnimatorsEnabled()
    val p by animateFloatAsState(if(expanded) 1f else 0f,
        animationSpec=if(reduced) snap() else spring(dampingRatio=.68f,stiffness=420f),label="waypoint-plus-morph")
    val ink=MaterialTheme.colorScheme.onSecondaryContainer
    IconButton(onClick,enabled=enabled,modifier=modifier.size(48.dp).testTag("timeline-waypoint-add").semantics {
        contentDescription=tr("Zwischenstopp hinzufügen")
        stateDescription=if(expanded) tr("Stopp-Auswahl geöffnet") else tr("Stopp-Auswahl geschlossen")
    }) {
        Box(Modifier.size(40.dp).graphicsLayer {
            val bulge=.06f*sin(PI*p.coerceIn(0f,1f)).toFloat();scaleX=1f+bulge;scaleY=scaleX
        }.background(MaterialTheme.colorScheme.secondaryContainer,CircleShape),contentAlignment=Alignment.Center) {
            Canvas(Modifier.size(22.dp).graphicsLayer {rotationZ=225f*p}) {
                val half=size.minDimension*.34f
                drawLine(ink,center-Offset(half,0f),center+Offset(half,0f),2.4.dp.toPx(),StrokeCap.Round)
                drawLine(ink,center-Offset(0f,half),center+Offset(0f,half),2.4.dp.toPx(),StrokeCap.Round)
            }
        }
    }
}
