// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import dev.ambivalenton.openv.data.model.reduceMotion
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.AppTheme
import dev.ambivalenton.openv.data.model.ThemeLanguage
import dev.ambivalenton.openv.data.model.themeLanguageOrStandard
import dev.ambivalenton.openv.data.model.DesignLanguage
import dev.ambivalenton.openv.rendering.isOperatingSystem
import dev.ambivalenton.openv.rendering.drawSystemControl
import dev.ambivalenton.openv.ui.theme.*

private enum class ActionStyle{FILLED,TONAL,OUTLINE}

/** Preserve Material actions/semantics; system themes supply their own control silhouette/material. */
@Composable fun ThemedButton(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,
    shape:Shape?=null,colors:ButtonColors?=null,elevation:ButtonElevation?=ButtonDefaults.buttonElevation(),border:BorderStroke?=null,
    contentPadding:PaddingValues=ButtonDefaults.ContentPadding,interactionSource:MutableInteractionSource?=null,content:@Composable RowScope.()->Unit)=
    ThemeActionButton(ActionStyle.FILLED,onClick,modifier,enabled,shape,colors,elevation,border,contentPadding,interactionSource,content)
@Composable fun ThemedTonalButton(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,
    shape:Shape?=null,colors:ButtonColors?=null,elevation:ButtonElevation?=ButtonDefaults.filledTonalButtonElevation(),border:BorderStroke?=null,
    contentPadding:PaddingValues=ButtonDefaults.ContentPadding,interactionSource:MutableInteractionSource?=null,content:@Composable RowScope.()->Unit)=
    ThemeActionButton(ActionStyle.TONAL,onClick,modifier,enabled,shape,colors,elevation,border,contentPadding,interactionSource,content)
@Composable fun ThemedOutlinedButton(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,
    shape:Shape?=null,colors:ButtonColors?=null,elevation:ButtonElevation?=null,border:BorderStroke?=null,
    contentPadding:PaddingValues=ButtonDefaults.ContentPadding,interactionSource:MutableInteractionSource?=null,content:@Composable RowScope.()->Unit)=
    ThemeActionButton(ActionStyle.OUTLINE,onClick,modifier,enabled,shape,colors,elevation,border,contentPadding,interactionSource,content)

@Composable private fun ThemeActionButton(style:ActionStyle,onClick:()->Unit,modifier:Modifier,enabled:Boolean,shape:Shape?,colors:ButtonColors?,
    elevation:ButtonElevation?,border:BorderStroke?,padding:PaddingValues,interaction:MutableInteractionSource?,content:@Composable RowScope.()->Unit){
    val theme=LocalAppVisuals.current.theme;val os=theme?.isOperatingSystem==true;val scheme=MaterialTheme.colorScheme
    val language=theme.themeLanguageOrStandard
    val defaults=when(style){ActionStyle.FILLED->ButtonDefaults.buttonColors();ActionStyle.TONAL->ButtonDefaults.filledTonalButtonColors();ActionStyle.OUTLINE->ButtonDefaults.outlinedButtonColors()}
    // Expressive press: the pill morphs towards a rounded rectangle and the button gives way (build 67).
    val source=interaction?:androidx.compose.runtime.remember {MutableInteractionSource()}
    // Graphic languages keep their corners on buttons instead of the expressive pill.
    val lang=languageInk()
    val graphic=lang.language in setOf(DesignLanguage.SWISS,DesignLanguage.BRUTAL,DesignLanguage.INK,DesignLanguage.PIXEL,DesignLanguage.TERMINAL,DesignLanguage.EDITORIAL)
    val outline=shape?:if(os)appCorner(when(theme){AppTheme.OS_MAC_AQUA,AppTheme.OS_IOS_GLASS->40.dp;AppTheme.OS_WIN_XP->16.dp;else->20.dp})
        else languageButtonShape(language) ?: if(graphic)appCorner(12.dp) else pressMorphShape(source)
    val neutralControl=theme in setOf(AppTheme.OS_WIN_95,AppTheme.OS_AMIGA,AppTheme.OS_ATARI,
        AppTheme.OS_NEXTSTEP,AppTheme.OS_OS2,AppTheme.OS_CDE,AppTheme.OS_MAC_CLASSIC)
    val resolved=colors?:when {
        os&&theme==AppTheme.OS_WIN_XP&&style!=ActionStyle.OUTLINE->defaults.copy(containerColor=scheme.primaryContainer,contentColor=scheme.onPrimaryContainer)
        neutralControl&&style!=ActionStyle.OUTLINE->defaults.copy(containerColor=scheme.surfaceContainer,contentColor=scheme.onSurface)
        language!=ThemeLanguage.STANDARD->languageButtonColors(language,style,defaults,scheme)
        else->defaults
    }
    val languageEdge=when(lang.language) {
        DesignLanguage.BRUTAL,DesignLanguage.PIXEL,DesignLanguage.INK->BorderStroke(2.dp,scheme.onSurface.copy(alpha=if(enabled)1f else .38f))
        DesignLanguage.NEON,DesignLanguage.TERMINAL->BorderStroke(1.5.dp,scheme.primary.copy(alpha=if(enabled)1f else .38f))
        DesignLanguage.AURORA,DesignLanguage.GLASS,DesignLanguage.CHROME->lang.border()
        else->null
    }
    val edge=border?:if(os)BorderStroke(1.dp,if(theme==AppTheme.OS_WIN_XP)scheme.primary.copy(alpha=if(enabled).65f else .2f)else scheme.outlineVariant)
        else if(language!=ThemeLanguage.STANDARD)languageButtonBorder(language,style,scheme,enabled,theme?.dark==true)
        else languageEdge ?: if(style==ActionStyle.OUTLINE)ButtonDefaults.outlinedButtonBorder(enabled)else null
    val bg=if(enabled)resolved.containerColor else resolved.disabledContainerColor
    val base=if(bg.alpha==0f)scheme.surfaceContainer else bg
    // Languages with their own shadow paint fill, rim and shadow on the same (touch-target)
    // bounds, like the system materials, so the shadow can never sit beside a smaller Material surface.
    val selfDrawn=!os && language in selfDrawnLanguages
    val paint=if(selfDrawn)modifier.languageButtonMaterial(language,outline,source,enabled,theme?.dark==true,base,edge)
        else if(!os)modifier.pressScale(.97f).then(if(lang.active && enabled && style!=ActionStyle.OUTLINE)Modifier.languageShadow(outline,lang)else Modifier)
        else modifier.clip(outline).drawWithCache {
        onDrawBehind { if(theme!=null)drawIntoCanvas { drawSystemControl(
            it.nativeCanvas,theme,size.width,size.height,base.toArgb(),enabled,density) } }
    }
    val actual=if(os||selfDrawn)resolved.copy(containerColor=Color.Transparent,disabledContainerColor=Color.Transparent)else resolved
    val rim=if(selfDrawn) null else edge
    // Languages with their own (or no) shadow replace the platform elevation.
    val lift=if(language.ownsShadow || language.flat) null else elevation
    when(style){
        ActionStyle.FILLED->Button(onClick,paint,enabled,outline,actual,if(lang.active)null else lift,rim,padding,source,content)
        ActionStyle.TONAL->FilledTonalButton(onClick,paint,enabled,outline,actual,if(lang.active)null else lift,rim,padding,source,content)
        ActionStyle.OUTLINE->OutlinedButton(onClick,paint,enabled,outline,actual,lift,rim,padding,source,content)
    }
}

/** Rectangular controls for poster, line, glow and Japandi languages; the rest keep the pill. */
@Composable private fun languageButtonShape(language:ThemeLanguage):Shape?=when(language) {
    ThemeLanguage.NEO_BRUTAL->appCorner(16.dp)
    ThemeLanguage.OUTLINE,ThemeLanguage.GLOW_EDGE->appCorner(14.dp)
    ThemeLanguage.JAPANDI->appCorner(12.dp)
    else->null
}

private fun languageButtonColors(language:ThemeLanguage,style:ActionStyle,defaults:ButtonColors,scheme:ColorScheme):ButtonColors=when(language) {
    ThemeLanguage.NEO_BRUTAL->when(style) {
        ActionStyle.FILLED->defaults.copy(containerColor=scheme.primaryContainer,contentColor=scheme.onPrimaryContainer)
        ActionStyle.TONAL->defaults.copy(containerColor=scheme.surfaceContainer,contentColor=scheme.onSurface)
        ActionStyle.OUTLINE->defaults.copy(containerColor=scheme.surfaceContainer,contentColor=scheme.onSurface)
    }
    ThemeLanguage.CLAY,ThemeLanguage.COLOR_BLOCK->when(style) {
        ActionStyle.FILLED->defaults.copy(containerColor=scheme.primaryContainer,contentColor=scheme.onPrimaryContainer)
        ActionStyle.TONAL->defaults.copy(containerColor=scheme.secondaryContainer,contentColor=scheme.onSecondaryContainer)
        ActionStyle.OUTLINE->defaults
    }
    ThemeLanguage.OUTLINE->if(style==ActionStyle.FILLED) defaults else defaults.copy(containerColor=Color.Transparent,contentColor=scheme.onSurface)
    ThemeLanguage.SOFT_RELIEF->if(style==ActionStyle.TONAL) defaults.copy(containerColor=scheme.surfaceContainer,contentColor=scheme.primary) else defaults
    ThemeLanguage.GLOW_EDGE->if(style==ActionStyle.TONAL) defaults.copy(containerColor=scheme.surfaceContainerHigh,contentColor=scheme.onSurface) else defaults
    else->defaults
}

private fun languageButtonBorder(language:ThemeLanguage,style:ActionStyle,scheme:ColorScheme,enabled:Boolean,dark:Boolean):BorderStroke?=when(language) {
    ThemeLanguage.NEO_BRUTAL->BorderStroke(2.dp,scheme.outline.copy(alpha=if(enabled) 1f else .38f))
    ThemeLanguage.OUTLINE->if(style==ActionStyle.FILLED) null else BorderStroke(1.25.dp,scheme.outline.copy(alpha=if(enabled) 1f else .38f))
    ThemeLanguage.SOFT_RELIEF,ThemeLanguage.CLAY,ThemeLanguage.GLOW_EDGE->
        if(language==ThemeLanguage.GLOW_EDGE && style==ActionStyle.FILLED) null else appFrameFor(language,dark,scheme).border
    ThemeLanguage.BENTO,ThemeLanguage.JAPANDI->if(style==ActionStyle.FILLED) null else BorderStroke(1.dp,
        (if(language==ThemeLanguage.BENTO) scheme.outlineVariant else scheme.outline).copy(alpha=if(enabled) 1f else .38f))
    else->if(style==ActionStyle.OUTLINE) BorderStroke(1.dp,scheme.outline.copy(alpha=if(enabled) 1f else .38f)) else null
}

private val selfDrawnLanguages=setOf(ThemeLanguage.NEO_BRUTAL,ThemeLanguage.SOFT_RELIEF,ThemeLanguage.CLAY,
    ThemeLanguage.ORGANIC,ThemeLanguage.GLOW_EDGE)

/** Fill, rim, shadow and press feedback of a language behind a real Material button (semantics unchanged). */
@Composable private fun Modifier.languageButtonMaterial(language:ThemeLanguage,shape:Shape,source:InteractionSource,
    enabled:Boolean,dark:Boolean,fill:Color,rim:BorderStroke?):Modifier {
    val scheme=MaterialTheme.colorScheme
    val quiet=LocalAppSettings.current.reduceMotion
    val pressed by source.collectIsPressedAsState()
    // Neo-brutalism: pressing pushes the button into its own hard shadow, which stays in place.
    val depth by animateFloatAsState(if(language==ThemeLanguage.NEO_BRUTAL && pressed && enabled) 1f else 0f,
        if(quiet) snap() else spring(dampingRatio=.8f,stiffness=1600f),label="language-press")
    val shadow:FrameShadow?=if(!enabled) null else when(language) {
        ThemeLanguage.NEO_BRUTAL->HardShadow(3.dp,brutalShadowInk(scheme,dark))
        ThemeLanguage.SOFT_RELIEF->ReliefShadow(3.dp,Color.White.copy(alpha=if(dark).05f else .8f),Color.Black.copy(alpha=if(dark).5f else .16f))
        ThemeLanguage.CLAY,ThemeLanguage.ORGANIC->HaloShadow(5.dp,lerp(scheme.primary,Color.Black,.25f).copy(alpha=if(dark).4f else .22f),.6f)
        ThemeLanguage.GLOW_EDGE->if(fill.alpha>0f && fill.luminance()>.25f) HaloShadow(6.dp,scheme.primary.copy(alpha=.28f),0f) else null
        else->null
    }
    val base=if(language==ThemeLanguage.NEO_BRUTAL) this.graphicsLayer {val o=depth*3.dp.toPx();translationX=o;translationY=o}
        else pressScale(.97f)
    return base.drawWithCache {
        val outline=shape.createOutline(size,layoutDirection,this@drawWithCache)
        val path=androidx.compose.ui.graphics.Path().apply {addOutline(outline)}
        val stroke=rim?.width?.toPx() ?: 0f
        onDrawBehind {
            when(shadow) {
                is HardShadow->{val o=(1f-depth)*shadow.offset.toPx();translate(o,o) {drawPath(path,shadow.color)}}
                null->Unit
                else->drawFrameShadow(path,shadow)
            }
            if(fill.alpha>0f) drawPath(path,fill)
            // Centred stroke of double width, clipped to the shape: exactly the rim width stays visible.
            if(rim!=null && stroke>0f) clipPath(path) {drawPath(path,rim.brush,style=androidx.compose.ui.graphics.drawscope.Stroke(stroke*2f))}
        }
    }.clip(shape)
}
