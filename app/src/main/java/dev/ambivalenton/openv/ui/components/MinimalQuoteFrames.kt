// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.QuoteDecoration

internal val originalMinimalFrames=setOf(QuoteDecoration.APP_DESIGN,QuoteDecoration.MATERIAL_CARD,
    QuoteDecoration.PINNED_CARD,QuoteDecoration.HAIRLINE,QuoteDecoration.ACCENT_RULE)

@Composable internal fun MinimalQuoteFrame(design:QuoteDecoration,modifier:Modifier,content:@Composable (Color)->Unit){
    val colors=MaterialTheme.colorScheme
    val shape=when(design){QuoteDecoration.SQUARE_CARD->RoundedCornerShape(2.dp);QuoteDecoration.CAPSULE_CARD->RoundedCornerShape(50);else->RoundedCornerShape(20.dp)}
    val paper=when(design){QuoteDecoration.TONAL_PLATE->colors.primaryContainer;QuoteDecoration.OFFSET_CARD->colors.surfaceContainerLowest
        QuoteDecoration.FROSTED_CARD->colors.surfaceContainerLow.copy(alpha=.76f);else->colors.surface}
    Surface(modifier,shape=shape,color=paper,shadowElevation=if(design==QuoteDecoration.OFFSET_CARD)4.dp else 0.dp){
        Box(Modifier.drawBehind{drawMinimalQuoteDecoration(design,colors.primary,colors.secondary,colors.outlineVariant)}
            .padding(horizontal=if(design==QuoteDecoration.CAPSULE_CARD)28.dp else 20.dp,vertical=22.dp)){
            content(if(design==QuoteDecoration.TONAL_PLATE)colors.onPrimaryContainer else colors.onSurface)
        }
    }
}

/** Shared small vector vocabulary; colour comes from the active theme in the real frame. */
internal fun DrawScope.drawMinimalQuoteDecoration(d:QuoteDecoration,accent:Color,secondary:Color,outline:Color){
    val w=size.width;val h=size.height;val one=1.dp.toPx();val r=20.dp.toPx().coerceAtMost(size.minDimension/2)
    fun line(a:Offset,b:Offset,color:Color=accent,width:Float=one)=drawLine(color,a,b,width,StrokeCap.Round)
    fun border(inset:Float=one,color:Color=outline,radius:Float=r,width:Float=one,effect:PathEffect?=null)=
        drawRoundRect(color,Offset(inset,inset),Size((w-2*inset).coerceAtLeast(0f),(h-2*inset).coerceAtLeast(0f)),CornerRadius(radius),style=Stroke(width,pathEffect=effect))
    when(d){
        QuoteDecoration.TONAL_PLATE->Unit
        QuoteDecoration.SOFT_OUTLINE->border(color=accent.copy(alpha=.35f),width=1.2f*one)
        QuoteDecoration.TOP_RULE->line(Offset(20*one,one),Offset(w-20*one,one),width=2*one)
        QuoteDecoration.BOTTOM_RULE->line(Offset(w*.28f,h-2*one),Offset(w*.72f,h-2*one))
        QuoteDecoration.SIDE_BRACKETS->{
            for(right in listOf(false,true)){val x=if(right)w-2*one else 2*one;val dx=if(right)-8*one else 8*one
                drawPath(Path().apply{moveTo(x+dx,12*one);lineTo(x,12*one);lineTo(x,h-12*one);lineTo(x+dx,h-12*one)},accent,style=Stroke(one,cap=StrokeCap.Round,join=StrokeJoin.Round))}
        }
        QuoteDecoration.CORNERS->{for(right in listOf(false,true))for(bottom in listOf(false,true)){
            val x=if(right)w-2*one else 2*one;val y=if(bottom)h-2*one else 2*one;val dx=if(right)-12*one else 12*one;val dy=if(bottom)-12*one else 12*one
            drawPath(Path().apply{moveTo(x+dx,y);lineTo(x,y);lineTo(x,y+dy)},accent,style=Stroke(one,cap=StrokeCap.Round,join=StrokeJoin.Round))
        }}
        QuoteDecoration.DOUBLE_RULE->{border(color=accent.copy(alpha=.45f));border(5*one,color=outline.copy(alpha=.6f),radius=(r-4*one).coerceAtLeast(0f),width=.6f*one)}
        QuoteDecoration.CAPSULE_CARD->border(color=accent.copy(alpha=.3f),radius=h/2)
        QuoteDecoration.SQUARE_CARD->border(color=outline,radius=one)
        QuoteDecoration.OFFSET_CARD->line(Offset(18*one,h-2*one),Offset(w-18*one,h-2*one),outline.copy(alpha=.55f))
        QuoteDecoration.FROSTED_CARD->{drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha=.38f),Color.Transparent,secondary.copy(alpha=.09f))));border(color=Color.White.copy(alpha=.65f));border(3*one,color=accent.copy(alpha=.12f),radius=(r-2*one).coerceAtLeast(0f))}
        QuoteDecoration.INSET_CARD->{border(5*one,color=outline.copy(alpha=.5f),radius=(r-4*one).coerceAtLeast(0f));line(Offset(18*one,2*one),Offset(w-18*one,2*one),Color.White.copy(alpha=.5f))}
        QuoteDecoration.DASH_OUTLINE->border(color=accent.copy(alpha=.4f),effect=PathEffect.dashPathEffect(floatArrayOf(4*one,5*one)))
        QuoteDecoration.SECTION_TAB->{border();drawRoundRect(accent.copy(alpha=.65f),Offset(w*.4f,0f),Size(w*.2f,4*one),CornerRadius(2*one))}
        else->Unit
    }
}
