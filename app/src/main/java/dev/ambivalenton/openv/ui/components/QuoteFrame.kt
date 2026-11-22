// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.QuoteDecoration
import dev.ambivalenton.openv.data.model.QuoteDesignGroup
import dev.ambivalenton.openv.ui.theme.appCorner
import dev.ambivalenton.openv.ui.theme.appPanelDecoration
import kotlin.math.*
import kotlin.random.Random

internal fun quoteInk(design:QuoteDecoration,plain:Color)=when(design) {
    QuoteDecoration.BLUEPRINT->Color(0xffe6f3ff)
    QuoteDecoration.CHALKBOARD->Color(0xfff3f2d8)
    else->if(design==QuoteDecoration.PLAIN || design in minimalQuoteFrames)plain else Color(0xff302a24)
}
internal val minimalQuoteFrames=QuoteDesignGroup.MINIMAL.designs.toSet()
@Composable fun QuoteFrame(design:QuoteDecoration,modifier:Modifier=Modifier,content:@Composable (Color)->Unit) {
    if(design in minimalQuoteFrames && design !in originalMinimalFrames){MinimalQuoteFrame(design,modifier,content);return}
    val ink=quoteInk(design,MaterialTheme.colorScheme.onSurface)
    if(design in minimalQuoteFrames) {
        val colors=MaterialTheme.colorScheme
        Surface(modifier,shape=if(design==QuoteDecoration.APP_DESIGN) appCorner(24.dp) else RoundedCornerShape(18.dp),
            color=if(design==QuoteDecoration.HAIRLINE || design==QuoteDecoration.ACCENT_RULE) colors.surface else colors.surfaceContainerLow,
            border=if(design==QuoteDecoration.HAIRLINE || design==QuoteDecoration.APP_DESIGN) BorderStroke(.8.dp,colors.outlineVariant) else null,
            shadowElevation=if(design==QuoteDecoration.PINNED_CARD) 3.dp else 0.dp,
            tonalElevation=if(design==QuoteDecoration.MATERIAL_CARD) 2.dp else 0.dp) {
            Box(Modifier.then(if(design==QuoteDecoration.APP_DESIGN) Modifier.appPanelDecoration(appCorner(24.dp)) else Modifier)
                .drawBehind {
                    if(design==QuoteDecoration.ACCENT_RULE) drawRoundRect(colors.primary,Offset(4.dp.toPx(),16.dp.toPx()),
                        Size(3.dp.toPx(),(size.height-32.dp.toPx()).coerceAtLeast(0f)),CornerRadius(2.dp.toPx()))
                    if(design==QuoteDecoration.PINNED_CARD) {
                        val x=size.width/2;val y=11.dp.toPx()
                        drawLine(colors.outline,Offset(x,y),Offset(x+3.dp.toPx(),y+7.dp.toPx()),1.dp.toPx())
                        drawCircle(colors.primary,4.dp.toPx(),Offset(x,y))
                        drawCircle(Color.White.copy(alpha=.6f),1.dp.toPx(),Offset(x-1.dp.toPx(),y-1.dp.toPx()))
                    }
                }.padding(horizontal=16.dp,vertical=if(design==QuoteDecoration.PINNED_CARD) 24.dp else 16.dp)) {content(ink)}
        }
        return
    }
    val vertical=when(design){QuoteDecoration.GILDED,QuoteDecoration.SLIDE->30.dp;QuoteDecoration.LETTER,QuoteDecoration.TYPEWRITER,QuoteDecoration.TICKET->36.dp;else->22.dp}
    val padding=if(design==QuoteDecoration.PLAIN)PaddingValues(0.dp) else PaddingValues(horizontal=if(design==QuoteDecoration.GILDED)32.dp else 24.dp,vertical=vertical)
    Box(modifier.heightIn(min=if(design==QuoteDecoration.PLAIN)0.dp else 124.dp)
        .then(if(design==QuoteDecoration.PLAIN)Modifier else Modifier.shadow(4.dp,RoundedCornerShape(if(design==QuoteDecoration.E_READER)18.dp else 4.dp)))
        .drawBehind {drawQuoteFrame(design)}.padding(padding)) {content(ink)}
}

/** Deterministic paper fibres, bevels and highlights; no timers or downloaded textures. */
internal fun DrawScope.drawQuoteFrame(d:QuoteDecoration) {
    if(d==QuoteDecoration.PLAIN)return
    // Canvas previews use a neutral palette; the live frame above uses the selected app theme.
    if(d in minimalQuoteFrames) {
        val shape=CornerRadius(if(d==QuoteDecoration.APP_DESIGN) 24.dp.toPx() else 18.dp.toPx())
        drawRoundRect(Color(0xfff4f3f8),cornerRadius=shape)
        if(d !in originalMinimalFrames){drawMinimalQuoteDecoration(d,Color(0xff6750a4),Color(0xff625b71),Color(0xffc9c4ce));return}
        when(d) {
            QuoteDecoration.MATERIAL_CARD->drawRoundRect(Color(0xff6750a4).copy(alpha=.04f),cornerRadius=shape)
            QuoteDecoration.APP_DESIGN->{drawRoundRect(Color(0xff6750a4),cornerRadius=shape,style=Stroke(1.dp.toPx()));drawCircle(Color(0xff6750a4).copy(alpha=.1f),size.width*.3f,Offset(size.width,size.height))}
            QuoteDecoration.PINNED_CARD->drawCircle(Color(0xff6750a4),4.dp.toPx(),Offset(size.width/2,11.dp.toPx()))
            QuoteDecoration.HAIRLINE->drawRoundRect(Color(0xff807b89),cornerRadius=shape,style=Stroke(.8.dp.toPx()))
            QuoteDecoration.ACCENT_RULE->drawRoundRect(Color(0xff6750a4),Offset(4.dp.toPx(),16.dp.toPx()),Size(3.dp.toPx(),size.height-32.dp.toPx()),CornerRadius(2.dp.toPx()))
            else->Unit
        }
        return
    }
    val w=size.width;val h=size.height;val u=density
    fun rect(c:Color,inset:Float=0f,radius:Float=0f,stroke:Float=0f) {
        drawRoundRect(c,Offset(inset*u,inset*u),Size((w-2*inset*u).coerceAtLeast(0f),(h-2*inset*u).coerceAtLeast(0f)),CornerRadius(radius*u),
            style=if(stroke>0)Stroke(stroke*u) else Fill)
    }
    fun rule(y:Float,color:Color=Color(0xff90aec9),start:Float=16f,end:Float=16f)=drawLine(color,Offset(start*u,y*u),Offset(w-end*u,y*u),.6f*u)
    val cream=Color(0xfff5eedb);val wood=Color(0xff906039);val gold=Color(0xffb58a32)
    val paper=when(d) {
        QuoteDecoration.STICKY_NOTE->Color(0xffffeb8c);QuoteDecoration.BLUEPRINT->Color(0xff153d69)
        QuoteDecoration.CHALKBOARD->Color(0xff233e32);QuoteDecoration.PARCHMENT->Color(0xffddc39a)
        QuoteDecoration.CARDBOARD->Color(0xffc69f6e);QuoteDecoration.GLASS_CASE->Color(0xffe4eef0)
        QuoteDecoration.WOOD_SIGN->Color(0xffd8b486);QuoteDecoration.NEWSPAPER->Color(0xffe8e5dc)
        else->cream
    }
    rect(paper,radius=if(d==QuoteDecoration.E_READER)18f else 4f)
    val random=Random(4500+d.ordinal)
    repeat(160) {drawCircle(Color.Black.copy(alpha=.025f),(.2f+random.nextFloat()*.5f)*u,Offset(random.nextFloat()*w,random.nextFloat()*h))}
    when(d) {
        QuoteDecoration.STICKY_NOTE->{
            drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha=.1f),Color.Transparent,Color(0xffb59b30).copy(alpha=.18f))))
            drawRect(Color(0xffd8bc39).copy(alpha=.3f),Offset(0f,0f),Size(w,12*u))
            val fold=Path().apply {moveTo(w-20*u,h);lineTo(w,h-20*u);lineTo(w,h);close()};drawPath(fold,Color(0xfffff5b4))
            drawLine(Color(0xffaa8e2a).copy(alpha=.4f),Offset(w-20*u,h),Offset(w,h-20*u),u)
        }
        QuoteDecoration.GILDED->{
            rect(Color(0xff5a3b17),radius=5f);rect(gold,3f,4f);rect(Color(0xfff1d789),7f,3f);rect(Color(0xff77511e),11f,2f);rect(gold,14f,1f);rect(cream,19f)
            for(x in listOf(10*u,w-10*u))for(y in listOf(10*u,h-10*u))repeat(8) {i->
                val a=i*PI.toFloat()/4;drawOval(Color(0xfff6df98),Offset(x+cos(a)*6*u-3*u,y+sin(a)*6*u-2*u),Size(6*u,4*u))
            }
        }
        QuoteDecoration.PICTURE_FRAME->{rect(Color(0xff57351f));rect(wood,3f);rect(Color(0xffbc936c),8f);rect(Color(0xff4b3222),13f);rect(Color(0xfffaf8f0),17f)
            repeat(7){i->rect(Color(0xff4c2c19).copy(alpha=.18f),2f+i*1.6f,stroke=.5f)} }
        QuoteDecoration.E_READER->{rect(Color(0xff33373a),radius=18f);rect(Color(0xff70777a),2f,16f,1f);rect(Color(0xffe6e8d9),12f,5f)
            drawCircle(Color(0xff3a3c3c),5*u,Offset(w/2,h-7*u));drawCircle(Color(0xffa1a8a5),2*u,Offset(w/2,h-7*u))}
        QuoteDecoration.BOOK_PAGE->{
            drawRect(Brush.horizontalGradient(listOf(Color(0xffa4906c),cream,Color(0xfffffcf2),Color(0xffdfd1b6))))
            repeat(6){drawLine(Color(0xff8a775c).copy(alpha=.12f),Offset((5+it)*u,8*u),Offset((5+it)*u,h-8*u),u)}
            drawPath(Path().apply {moveTo(w-17*u,h);quadraticBezierTo(w-3*u,h-5*u,w,h-22*u);lineTo(w,h);close()},Color(0xffc4b18d))
        }
        QuoteDecoration.LETTER->{rect(Color(0xffd6c7b0));rect(Color(0xfffcf7e9),8f,2f)
            drawLine(Color(0xffbdab8c),Offset(3*u,h-27*u),Offset(w/2,h-4*u),u);drawLine(Color(0xffbdab8c),Offset(w-3*u,h-27*u),Offset(w/2,h-4*u),u)
            drawCircle(Color(0xff93392f),9*u,Offset(w-25*u,h-20*u));drawCircle(Color(0xffcf7c66),6*u,Offset(w-25*u,h-20*u),style=Stroke(u))}
        QuoteDecoration.POSTCARD->{
            repeat((w/(12*u)).toInt()){i->drawRect(if(i%2==0)Color(0xffa95048) else Color(0xff426c92),Offset(i*12*u,0f),Size(7*u,4*u));drawRect(if(i%2==0)Color(0xff426c92) else Color(0xffa95048),Offset(i*12*u,h-4*u),Size(7*u,4*u))}
            drawRect(Color(0xffb8c9b2),Offset(w-30*u,9*u),Size(20*u,22*u));drawCircle(Color(0xff586a65),12*u,Offset(w-45*u,22*u),style=Stroke(.7f*u))}
        QuoteDecoration.POLAROID->{rect(Color(0xfffdfcf7));rect(Color(0xffb7c7c1),10f,1f);rect(Color(0xffedf1e8),12f,1f);rule(h/u-14,Color(0xffb7b7aa),w/u*.3f,w/u*.3f)}
        QuoteDecoration.CORKBOARD->{rect(Color(0xffaa7c49));repeat(240){drawCircle(Color(0xff614527).copy(alpha=.15f),random.nextFloat()*u+u,Offset(random.nextFloat()*w,random.nextFloat()*h))};rect(cream,12f,1f)
            for(x in listOf(20*u,w-20*u)){drawCircle(Color(0xff3e3022).copy(alpha=.3f),4*u,Offset(x+u,17*u));drawCircle(Color(0xffbb4f42),3*u,Offset(x,15*u));drawCircle(Color.White.copy(alpha=.5f),u,Offset(x-u,14*u))}}
        QuoteDecoration.BLUEPRINT->{var x=0f;while(x<w){drawLine(Color.White.copy(alpha=.08f),Offset(x,0f),Offset(x,h),.5f*u);x+=12*u};var y=0f;while(y<h){drawLine(Color.White.copy(alpha=.08f),Offset(0f,y),Offset(w,y),.5f*u);y+=12*u};rect(Color.White.copy(alpha=.45f),9f,stroke=.8f)}
        QuoteDecoration.TICKET->{
            for(i in 0..(h/(15*u)).toInt()){drawCircle(Color(0xffac956d),2*u,Offset(2*u,i*15*u));drawCircle(Color(0xffac956d),2*u,Offset(w-2*u,i*15*u))}
            repeat(27){i->drawRect(Color(0xff332d28),Offset(w-82*u+i*2*u,9*u),Size(if(i%3==0)2*u else .8f*u,14*u))}
            rule(h/u-14,Color(0xff988362));rule(28f,Color(0xff988362))}
        QuoteDecoration.NEWSPAPER->{rule(10f,Color(0xff34342f),12f,12f);rule(13f,Color(0xff77766c),12f,12f);rule(h/u-12,Color(0xff55554c),12f,12f)
            repeat(8){i->drawLine(Color(0xff868377).copy(alpha=.15f),Offset((14+i*2)*u,18*u),Offset((14+i*2)*u,h-18*u),.4f*u)}}
        QuoteDecoration.TYPEWRITER->{rect(Color(0xff4d5254),radius=8f);rect(cream,12f,2f)
            drawRect(Color(0xff424849),Offset(8*u,h-30*u),Size(w-16*u,24*u));repeat((w/(14*u)).toInt()-1){i->drawCircle(Color(0xffd6d2c6),4*u,Offset((15+i*14)*u,h-18*u));drawCircle(Color(0xff777e7e),2*u,Offset((15+i*14)*u,h-18*u))}}
        QuoteDecoration.CHALKBOARD->{rect(wood);rect(Color(0xff203d30),9f)
            repeat(20){val y=random.nextFloat()*h;drawLine(Color.White.copy(alpha=.028f),Offset(12*u,y),Offset(w-12*u,y+4*u),3*u)}
            drawRect(Color(0xffceb68c),Offset(8*u,h-10*u),Size(w-16*u,6*u));drawRect(Color(0xffeeede0),Offset(22*u,h-11*u),Size(22*u,3*u))}
        QuoteDecoration.SCHOOL_NOTE->{var y=18f;while(y<h/u){rule(y,Color(0xffa6bfce),6f,6f);y+=18};drawLine(Color(0xffd99898),Offset(15*u,0f),Offset(15*u,h),u)}
        QuoteDecoration.PARCHMENT->{
            drawRect(Brush.radialGradient(listOf(Color.Transparent,Color(0xff7e5431).copy(alpha=.18f)),radius=w*.8f))
            repeat(8){drawCircle(Color(0xff684724).copy(alpha=.035f),(8+random.nextInt(20))*u,Offset(random.nextFloat()*w,random.nextFloat()*h))};rect(Color(0xff8f6c40).copy(alpha=.3f),3f,stroke=1f)}
        QuoteDecoration.FILE_CARD->{var y=34f;while(y<h/u-8){rule(y,Color(0xffb7c5d3),8f,8f);y+=18};rule(19f,Color(0xffcc7775),8f,8f);drawCircle(Color(0xff9e9b8c),3*u,Offset(w-15*u,12*u),style=Stroke(u))}
        QuoteDecoration.JOURNAL->{rect(Color(0xff344859),radius=8f);rect(cream,11f,3f);drawRect(Color(0xff9f4d54),Offset(w-24*u,0f),Size(8*u,18*u))
            var y=12f;while(y<h/u-8){drawLine(Color(0xffd5c7a7),Offset(5*u,y*u),Offset(8*u,(y+4)*u),u);y+=9}}
        QuoteDecoration.CARDBOARD->{var y=0f;while(y<h){drawLine(Color(0xff73542e).copy(alpha=.07f),Offset(0f,y),Offset(w,y+10*u),u);y+=4*u}
            for(x in listOf(20*u,w-20*u))rotate(if(x<w/2)-12f else 12f,Offset(x,8*u)){drawRect(Color(0xffffedc3).copy(alpha=.55f),Offset(x-18*u,-3*u),Size(36*u,16*u))}}
        QuoteDecoration.GLASS_CASE->{drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha=.8f),Color.Transparent,Color(0xff81a9b5).copy(alpha=.2f))));rect(Color.White.copy(alpha=.9f),2f,8f,1f);rect(Color(0xff78909b).copy(alpha=.6f),5f,5f,.7f)
            for(x in listOf(10*u,w-10*u))for(y in listOf(10*u,h-10*u))drawCircle(Color(0xff6b7b81),2*u,Offset(x,y))}
        QuoteDecoration.SLIDE->{rect(Color(0xff262a2a),radius=3f);rect(Color(0xffe6e2d3),19f,1f)
            repeat((w/(18*u)).toInt()){i->for(y in listOf(5*u,h-12*u))drawRoundRect(Color(0xffd5d0ba),Offset((6+i*18)*u,y),Size(10*u,7*u),CornerRadius(u))}}
        QuoteDecoration.STAMP->{rect(Color(0xfffffdf2));rect(Color(0xffaa675e),10f,stroke=1f)
            repeat((w/(12*u)).toInt()){i->for(y in listOf(2*u,h-2*u))drawCircle(Color(0xff9d9c8c),2*u,Offset((6+i*12)*u,y),style=Stroke(.6f*u))}
            repeat((h/(12*u)).toInt()){i->for(x in listOf(2*u,w-2*u))drawCircle(Color(0xff9d9c8c),2*u,Offset(x,(6+i*12)*u),style=Stroke(.6f*u))}}
        QuoteDecoration.WOOD_SIGN->{var y=0f;while(y<h){drawLine(Color(0xff77532e).copy(alpha=.18f),Offset(0f,y),Offset(w,y+3*u),.6f*u);y+=7*u}
            for(x in listOf(10*u,w-10*u)){drawCircle(Color(0xff766e5d),3*u,Offset(x,10*u));drawLine(Color(0xffe6d5b9),Offset(x-2*u,10*u),Offset(x+2*u,10*u),u)}}
        QuoteDecoration.MARBLE->{rect(Color(0xfff2f0e8),radius=3f);repeat(5){i->val x=random.nextFloat()*w;val path=Path().apply {moveTo(x,0f);cubicTo(x-20*u,h*.3f,x+30*u,h*.5f,x-8*u,h)};drawPath(path,Color(0xff8f9698).copy(alpha=.14f),style=Stroke((.5f+i*.12f)*u))};rect(gold.copy(alpha=.65f),9f,stroke=.8f)}
        else->Unit
    }
}
