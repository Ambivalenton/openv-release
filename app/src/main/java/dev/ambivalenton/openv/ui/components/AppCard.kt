// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.ui.theme.*

/*
 * Cards in the design language of the app theme. Material themes keep the Material card
 * unchanged. Every other language replaces the platform elevation by its own outer effect (soft UI,
 * clay, hard offset, glow, paper layers), sets its fill and outline, and paints its inner material
 * (glass sheen, clay light, Swiss stripe, editorial rules, dashed terminal frame) under the content.
 */

private enum class CardKind {FILLED,ELEVATED,OUTLINED}

@Composable private fun languageCardParts(kind:CardKind,shape:Shape,colors:CardColors,border:BorderStroke?):Triple<Shape,CardColors,BorderStroke?> {
    val ink=languageInk()
    if(!ink.active) return Triple(shape,colors,border)
    val visuals=LocalAppVisuals.current
    // The 8-bit outline is only safe without a platform shadow, which a language card never uses.
    val outline=if(visuals.theme?.shapeStyle==dev.ambivalenton.openv.data.model.ThemeShape.PIXEL) visuals.surfaceShape(24.dp) else shape
    val fill=ink.fill(colors.containerColor)
    return Triple(outline,colors.copy(containerColor=fill,disabledContainerColor=ink.fill(colors.disabledContainerColor)),
        ink.border() ?: border.takeIf {kind==CardKind.OUTLINED})
}

@Composable private fun LanguageCardBody(shape:Shape,content:@Composable ColumnScope.()->Unit) {
    val ink=languageInk()
    if(!ink.active) Column(content=content) else Column(Modifier.languageInnerMaterial(shape,ink),content=content)
}

@Composable private fun AppCardImpl(kind:CardKind,onClick:(()->Unit)?,modifier:Modifier,enabled:Boolean,shape:Shape,colors:CardColors,
    elevation:CardElevation,border:BorderStroke?,interactionSource:MutableInteractionSource?,content:@Composable ColumnScope.()->Unit) {
    // The ten ThemeLanguage collections (Neo-Brutalismus … Nacht & Leuchtkante) bring their own frame:
    // outline, hard or soft shadow instead of the platform elevation. Both systems never overlap.
    val frame=appFrame()
    if(frame.language!=dev.ambivalenton.openv.data.model.ThemeLanguage.STANDARD) {
        val m=modifier.appFrameShadow(frame,shape);val e=frame.cardElevation(elevation);val b=frame.cardBorder(border)
        if(onClick==null) Card(m,shape,colors,e,b,content) else Card(onClick,m,enabled,shape,colors,e,b,interactionSource,content)
        return
    }
    val ink=languageInk()
    val (s,c,b)=languageCardParts(kind,shape,colors,border)
    val e=if(ink.active) CardDefaults.cardElevation(0.dp,0.dp,0.dp,0.dp,0.dp,0.dp) else elevation
    val m=if(ink.active) modifier.languageShadow(s,ink) else modifier
    if(onClick==null) Card(m,s,c,e,b) {LanguageCardBody(s,content)}
    else Card(onClick,m,enabled,s,c,e,b,interactionSource) {LanguageCardBody(s,content)}
}

@Composable fun AppCard(modifier:Modifier=Modifier,shape:Shape=CardDefaults.shape,colors:CardColors=CardDefaults.cardColors(),
    elevation:CardElevation=CardDefaults.cardElevation(),border:BorderStroke?=null,content:@Composable ColumnScope.()->Unit)=
    AppCardImpl(CardKind.FILLED,null,modifier,true,shape,colors,elevation,border,null,content)

@Composable fun AppCard(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,shape:Shape=CardDefaults.shape,
    colors:CardColors=CardDefaults.cardColors(),elevation:CardElevation=CardDefaults.cardElevation(),border:BorderStroke?=null,
    interactionSource:MutableInteractionSource?=null,content:@Composable ColumnScope.()->Unit)=
    AppCardImpl(CardKind.FILLED,onClick,modifier,enabled,shape,colors,elevation,border,interactionSource,content)

@Composable fun AppElevatedCard(modifier:Modifier=Modifier,shape:Shape=CardDefaults.elevatedShape,
    colors:CardColors=CardDefaults.elevatedCardColors(),elevation:CardElevation=CardDefaults.elevatedCardElevation(),
    content:@Composable ColumnScope.()->Unit)=AppCardImpl(CardKind.ELEVATED,null,modifier,true,shape,colors,elevation,null,null,content)

@Composable fun AppElevatedCard(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,shape:Shape=CardDefaults.elevatedShape,
    colors:CardColors=CardDefaults.elevatedCardColors(),elevation:CardElevation=CardDefaults.elevatedCardElevation(),
    interactionSource:MutableInteractionSource?=null,content:@Composable ColumnScope.()->Unit)=
    AppCardImpl(CardKind.ELEVATED,onClick,modifier,enabled,shape,colors,elevation,null,interactionSource,content)

@Composable fun AppOutlinedCard(modifier:Modifier=Modifier,shape:Shape=CardDefaults.outlinedShape,
    colors:CardColors=CardDefaults.outlinedCardColors(),elevation:CardElevation=CardDefaults.outlinedCardElevation(),
    border:BorderStroke=CardDefaults.outlinedCardBorder(),content:@Composable ColumnScope.()->Unit)=
    AppCardImpl(CardKind.OUTLINED,null,modifier,true,shape,colors,elevation,border,null,content)

@Composable fun AppOutlinedCard(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,shape:Shape=CardDefaults.outlinedShape,
    colors:CardColors=CardDefaults.outlinedCardColors(),elevation:CardElevation=CardDefaults.outlinedCardElevation(),
    border:BorderStroke=CardDefaults.outlinedCardBorder(enabled),interactionSource:MutableInteractionSource?=null,
    content:@Composable ColumnScope.()->Unit)=
    AppCardImpl(CardKind.OUTLINED,onClick,modifier,enabled,shape,colors,elevation,border,interactionSource,content)

/** Plain surfaces in list style (settings tiles): same fill, outline and outer effect as a card. */
@Composable fun Modifier.appSurfaceMaterial(shape:Shape):Modifier {
    val frame=appFrame()
    if(frame.language!=dev.ambivalenton.openv.data.model.ThemeLanguage.STANDARD) return appFrameShadow(frame,shape)
    val ink=languageInk()
    if(!ink.active) return this
    return languageShadow(shape,ink)
}
