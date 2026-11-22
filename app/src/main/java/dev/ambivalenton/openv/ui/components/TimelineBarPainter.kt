// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.DelayInfo
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.data.model.TimelineBarStyle
import dev.ambivalenton.openv.data.model.TimelineLabelEffect
import dev.ambivalenton.openv.ui.theme.TimelineInk
import dev.ambivalenton.openv.ui.theme.designPreviewShape
import dev.ambivalenton.openv.ui.theme.contrastContentColor
import dev.ambivalenton.openv.ui.theme.LocalAppVisuals
import dev.ambivalenton.openv.ui.theme.LocalAppSettings
import dev.ambivalenton.openv.ui.theme.hardShadow
import dev.ambivalenton.openv.data.model.spec
import dev.ambivalenton.openv.ui.theme.FullThemeBackdrop
import dev.ambivalenton.openv.ui.theme.buildFullThemeBackdrop
import dev.ambivalenton.openv.data.model.reduceMotion
import dev.ambivalenton.openv.data.model.themeLanguageOrStandard
import dev.ambivalenton.openv.ui.theme.frameShadow
import kotlin.math.*

val TimelineBarStyle.open:Boolean get()=when(this) {
    TimelineBarStyle.CLASSIC,TimelineBarStyle.CAPSULE,TimelineBarStyle.SQUARED,
    TimelineBarStyle.TICKET,TimelineBarStyle.RIBBON,TimelineBarStyle.GRADIENT,TimelineBarStyle.APP_THEME->false
    else->true
}

/** The SAME vector painter is used by the live comparison and every design miniature.
 * It decorates the existing time bounds; no timestamp, column width or snap transform changes. */
fun DrawScope.drawTimelineBar(style:TimelineBarStyle,ink:TimelineInk,appArtwork:AppChartArtwork?=null,backdrop:FullThemeBackdrop?=null) {
    val w=size.width;val h=size.height
    if(w<=0f || h<=0f) return
    val c=ink.container;val line=ink.rail
    val thin=min(2.dp.toPx(),min(w,h)/4f).coerceAtLeast(min(1f,min(w,h)))
    val radius=min(6.dp.toPx(),min(w,h)/2f)
    fun box(color:Color=c,r:Float=radius,stroke:Float?=null) {
        val inset=(stroke ?: 0f)/2f
        drawRoundRect(color,Offset(inset,inset),Size((w-2*inset).coerceAtLeast(0f),(h-2*inset).coerceAtLeast(0f)),
            CornerRadius(r),style=if(stroke==null) Fill else Stroke(stroke))
    }
    val railInset=min(radius,h*.18f)
    fun lineAt(x:Float,color:Color=line,stroke:Float=thin)=drawLine(color,Offset(x,railInset),Offset(x,h-railInset),stroke,StrokeCap.Round)
    fun repeatY(step:Float,minimum:Int=1,block:(Float,Int)->Unit) {
        val count=ceil(h/step.coerceAtLeast(1f)).toInt().coerceIn(minimum,256)
        repeat(count) {i->block((i+.5f)*h/count,i)}
    }
    when(style) {
        TimelineBarStyle.APP_THEME->if(appArtwork!=null)drawAppChartBar(ink,appArtwork,backdrop)else box()
        TimelineBarStyle.CLASSIC->box()
        TimelineBarStyle.CAPSULE->box(r=min(w,h)/2f)
        TimelineBarStyle.SQUARED->drawRect(c)
        TimelineBarStyle.OUTLINE->{box(c.copy(alpha=.08f));box(line,stroke=thin)}
        TimelineBarStyle.METRO_RAILS->{lineAt(w/2f,stroke=min(5.dp.toPx(),min(w*.3f,h*.28f)));
            val r=min(7.dp.toPx(),min(w*.3f,h*.22f));drawCircle(line,r,Offset(w/2f,r));drawCircle(line,r,Offset(w/2f,h-r))}
        TimelineBarStyle.DOUBLE_RAIL->{lineAt(w*.3f);lineAt(w*.7f)
            drawLine(line,Offset(w*.3f,railInset),Offset(w*.7f,railInset),thin)
            drawLine(line,Offset(w*.3f,h-railInset),Offset(w*.7f,h-railInset),thin)}
        TimelineBarStyle.DASHED->repeatY(13.dp.toPx(),2) {y,_->
            val hh=min(8.dp.toPx(),h/ceil(h/(13.dp.toPx())).coerceAtLeast(2f)*.68f);drawRoundRect(c,Offset(2.dp.toPx(),(y-hh/2).coerceAtLeast(0f)),Size((w-4.dp.toPx()).coerceAtLeast(1f),hh),CornerRadius(2.dp.toPx()))}
        TimelineBarStyle.DOTTED->repeatY(12.dp.toPx(),2) {y,_->drawCircle(line,min(3.dp.toPx(),min(w/4f,h/ceil(h/(12.dp.toPx())).coerceAtLeast(2f)/2.5f)),Offset(w/2f,y))}
        TimelineBarStyle.BEADS->{lineAt(w/2f,stroke=1.dp.toPx());repeatY(18.dp.toPx(),2) {y,_->
            val hh=min(11.dp.toPx(),h/ceil(h/(18.dp.toPx())).coerceAtLeast(2f)*.66f);drawOval(c,Offset(w*.15f,y-hh/2f),Size(w*.7f,hh))}}
        TimelineBarStyle.TICKET->{
            val notch=min(6.dp.toPx(),min(w,h)/5f)
            val p=Path().apply {moveTo(0f,0f);lineTo(w,0f);lineTo(w,h*.5f-notch)
                quadraticTo(w-notch*2f,h*.5f,w,h*.5f+notch);lineTo(w,h);lineTo(0f,h)
                lineTo(0f,h*.5f+notch);quadraticTo(notch*2f,h*.5f,0f,h*.5f-notch);close()}
            drawPath(p,c)
            drawLine(ink.content.copy(alpha=.4f),Offset(notch,h*.5f),Offset(w-notch,h*.5f),1.dp.toPx(),
                pathEffect=PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(),3.dp.toPx())))
        }
        TimelineBarStyle.CHEVRON->repeatY(22.dp.toPx(),2) {y,_->
            val hh=min(18.dp.toPx(),h/ceil(h/(22.dp.toPx())).coerceAtLeast(2f)*.82f);val p=Path().apply {moveTo(w*.12f,y-hh/2);lineTo(w*.5f,y)
                lineTo(w*.88f,y-hh/2);lineTo(w*.88f,y);lineTo(w*.5f,y+hh/2);lineTo(w*.12f,y);close()};drawPath(p,c)}
        TimelineBarStyle.RIBBON->{val fold=min(w*.24f,h*.16f);drawPath(Path().apply {
            moveTo(0f,0f);lineTo(w,0f);lineTo(w,h);lineTo(w/2f,h-fold);lineTo(0f,h);close()},c)
            drawRect(ink.content.copy(alpha=.16f),size=Size(w,min(6.dp.toPx(),h/4f)))}
        TimelineBarStyle.GLASS->{drawRoundRect(Brush.linearGradient(listOf(c.copy(alpha=.6f),c.copy(alpha=.2f),c.copy(alpha=.5f))),cornerRadius=CornerRadius(radius))
            box(line.copy(alpha=.75f),stroke=1.dp.toPx());drawLine(Color.White.copy(alpha=.7f),Offset(3.dp.toPx(),radius),Offset(w-3.dp.toPx(),radius),1.dp.toPx())}
        TimelineBarStyle.NEON->{box(line.copy(alpha=.10f));box(line.copy(alpha=.2f),stroke=7.dp.toPx());box(line,stroke=thin)
            lineAt(w/2f,line.copy(alpha=.8f),1.dp.toPx())}
        TimelineBarStyle.GRADIENT->drawRoundRect(Brush.verticalGradient(listOf(lerp(c,Color.White,.28f),c,lerp(c,Color.Black,.25f))),cornerRadius=CornerRadius(radius))
        TimelineBarStyle.TERMINAL->{box(line.copy(alpha=.1f),r=0f);box(line,r=0f,stroke=1.dp.toPx());
            repeatY(16.dp.toPx(),2) {y,_->drawLine(line,Offset(0f,y),Offset(w*.16f,y),thin);drawLine(line,Offset(w*.84f,y),Offset(w,y),thin)}}
        TimelineBarStyle.PIXEL->{val unit=min(w/3f,min(9.dp.toPx(),h/3f));repeatY(unit+3.dp.toPx(),2) {y,i->
            val left=if(i%2==0) w*.18f else w*.55f;drawRect(c,Offset(left,y-unit/2),Size(unit,unit));
            drawRect(c.copy(alpha=.6f),Offset(w-left-unit,y-unit/2),Size(unit,unit))}}
        TimelineBarStyle.BRAID->{for(side in listOf(-1f,1f)) {
            val p=Path();repeatY(12.dp.toPx(),3) {y,i->val x=w/2+side*(if(i%2==0) -1 else 1)*w*.22f
                if(i==0) p.moveTo(x,y) else p.lineTo(x,y)};drawPath(p,if(side<0) c else line.copy(alpha=.55f),style=Stroke(min(3.dp.toPx(),h/3f),cap=StrokeCap.Round,join=StrokeJoin.Round))}}
        TimelineBarStyle.TRACK_SLEEPERS->{lineAt(w*.3f);lineAt(w*.7f);repeatY(12.dp.toPx(),2) {y,_->
            drawLine(line.copy(alpha=.85f),Offset(w*.12f,y),Offset(w*.88f,y),min(3.dp.toPx(),h/5f).coerceAtLeast(1f),StrokeCap.Round)}}
        TimelineBarStyle.SPINE->{lineAt(w*.2f,stroke=3.dp.toPx());repeatY(14.dp.toPx(),2) {y,_->
            val hh=min(6.dp.toPx(),h/ceil(h/(14.dp.toPx())).coerceAtLeast(2f)*.65f)
            drawRoundRect(c,Offset(w*.2f,y-hh/2),Size(w*.7f,hh),CornerRadius(2.dp.toPx()))}}
        TimelineBarStyle.BRACKET->{for(side in listOf(false,true)) {
            val x=if(side) w*.86f else w*.14f;val end=if(side) w*.62f else w*.38f
            drawPath(Path().apply {moveTo(end,thin);lineTo(x,thin);lineTo(x,h-thin);lineTo(end,h-thin)},line,
                style=Stroke(thin,join=StrokeJoin.Round,cap=StrokeCap.Round))}}
        TimelineBarStyle.HALFTONE->{box(c.copy(alpha=.16f));
            val clip=Path().apply {addRoundRect(RoundRect(Rect(Offset.Zero,size),CornerRadius(radius)))}
            clipPath(clip) {val step=8.dp.toPx();val columns=(w/step).toInt().coerceIn(1,12)
                repeatY(step,2) {y,row->repeat(columns) {col->drawCircle(c,min(1.6.dp.toPx(),h/ceil(h/step).coerceAtLeast(2f)/2.5f),Offset((col+.5f+(row%2)*.2f)*w/columns,y))}}}}
        TimelineBarStyle.LOLLIPOP->{val r=min(11.dp.toPx(),min(w/2f,h*.25f));lineAt(w/2,line,3.dp.toPx())
            drawCircle(c,r,Offset(w/2,r));drawCircle(line,r,Offset(w/2,h-r));
            drawCircle(ink.content,r*.35f,Offset(w/2,r));drawCircle(ink.content,r*.35f,Offset(w/2,h-r))}
        TimelineBarStyle.SEQUENCE->repeatY(26.dp.toPx(),2) {y,i->val hh=min(22.dp.toPx(),h/ceil(h/(26.dp.toPx())).coerceAtLeast(2f)*.82f)
            drawRoundRect(if(i%2==0)c else lerp(c,line,.3f),Offset(0f,y-hh/2),Size(w,hh),CornerRadius(3.dp.toPx()))
            drawLine(ink.content.copy(alpha=.2f),Offset(4.dp.toPx(),y-hh*.3f),Offset(w-4.dp.toPx(),y-hh*.3f),1.dp.toPx())}
        TimelineBarStyle.MINIMAL->{lineAt(w/2f,stroke=1.5.dp.toPx());
            drawLine(line,Offset(w*.3f,thin),Offset(w*.7f,thin),thin)
            drawLine(line,Offset(w*.3f,h-thin),Offset(w*.7f,h-thin),thin)}
    }
}

/** Drop shadow of a ride bar. The bars lie above their time chips and cast it onto them. */
internal val TIMELINE_BAR_ELEVATION=2.dp
/** Core opacity of the bar shadow (build 74, follow-up: subtler than the favourite shadows). */
internal const val TIMELINE_BAR_SHADOW_ALPHA=.16f

/**
 * Bar shadow with the same margin on every side. [shapeShadow] grows a shape uniformly by its smaller
 * side; a short, wide bar (a few minutes) then got a shadow far wider than the bar, cut off hard at
 * the column edge. Here width and height grow separately, so the shadow stays a soft rim of
 * [elevation] around any bar, within the column's gutter.
 */
internal fun Modifier.timelineBarShadow(shape:Shape,elevation:androidx.compose.ui.unit.Dp,ink:Color):Modifier=drawWithCache {
    val path=Path().apply {addOutline(shape.createOutline(size,layoutDirection,this@drawWithCache))}
    val lift=elevation.toPx()
    val w=size.width.coerceAtLeast(1f);val h=size.height.coerceAtLeast(1f)
    onDrawBehind {
        for(step in SHAPE_SHADOW_STEPS downTo 1) {
            val spread=lift*step/SHAPE_SHADOW_STEPS
            withTransform({translate(0f,lift*.35f);scale(1f+2f*spread/w,1f+2f*spread/h,center)}) {
                drawPath(path,ink.copy(alpha=TIMELINE_BAR_SHADOW_ALPHA/SHAPE_SHADOW_STEPS))
            }
        }
    }
}

/**
 * Silhouette of a filled ride bar (the same geometry as [drawTimelineBar]) for its drop
 * shadow. Open styles – lines, dots, rails, outlines – cast none: a box-shaped shadow would
 * contradict their drawing.
 */
internal class TimelineBarSilhouette(private val style:TimelineBarStyle,private val art:AppChartArtwork?):Shape {
    override fun createOutline(size:Size,layoutDirection:androidx.compose.ui.unit.LayoutDirection,density:androidx.compose.ui.unit.Density):Outline {
        val w=size.width;val h=size.height
        if(style==TimelineBarStyle.APP_THEME && art!=null) return appChartOutline(art,size,density,layoutDirection)
        val box=Rect(Offset.Zero,size)
        return when(style) {
            TimelineBarStyle.CAPSULE->Outline.Rounded(RoundRect(box,CornerRadius(min(w,h)/2f)))
            TimelineBarStyle.SQUARED->Outline.Rectangle(box)
            TimelineBarStyle.TICKET->{
                val notch=min(with(density){6.dp.toPx()},min(w,h)/5f)
                Outline.Generic(Path().apply {moveTo(0f,0f);lineTo(w,0f);lineTo(w,h*.5f-notch)
                    quadraticTo(w-notch*2f,h*.5f,w,h*.5f+notch);lineTo(w,h);lineTo(0f,h)
                    lineTo(0f,h*.5f+notch);quadraticTo(notch*2f,h*.5f,0f,h*.5f-notch);close()})
            }
            TimelineBarStyle.RIBBON->{
                val fold=min(w*.24f,h*.16f)
                Outline.Generic(Path().apply {moveTo(0f,0f);lineTo(w,0f);lineTo(w,h);lineTo(w/2f,h-fold);lineTo(0f,h);close()})
            }
            else->Outline.Rounded(RoundRect(box,CornerRadius(min(with(density){6.dp.toPx()},min(w,h)/2f))))
        }
    }
}

/** Shared production/preview label; effects never change the segment's time bounds. */
val TimelineLabelScaleKey=androidx.compose.ui.semantics.SemanticsPropertyKey<Float>("TimelineLabelScale")
val TimelineLabelOpacityKey=androidx.compose.ui.semantics.SemanticsPropertyKey<Float>("TimelineLabelOpacity")
val TimelineLabelEffectKey=androidx.compose.ui.semantics.SemanticsPropertyKey<String>("TimelineLabelEffect")
@Composable fun TimelineBar(style:TimelineBarStyle,ink:TimelineInk,label:String,modifier:Modifier=Modifier,showLabel:Boolean=true,delayInfo:DelayInfo?=null,showDelayLabel:Boolean=false,
    /** 2.5: a notice names this section's boarding or alighting stop: dashed band at the upper edge. */
    noticeMark:Boolean=false,
    /** Room kept free for departure/arrival times inside the bar; the line name stays between. */
    labelInsetTop:androidx.compose.ui.unit.Dp=0.dp,labelInsetBottom:androidx.compose.ui.unit.Dp=0.dp,
    content:@Composable BoxScope.()->Unit={}) {
    val appearance=dev.ambivalenton.openv.data.model.timelineLabelAppearance(dev.ambivalenton.openv.ui.theme.LocalTimelineLabelControl.current)
    val badge=appearance.effect==TimelineLabelEffect.BADGE
    val badgeBackground=MaterialTheme.colorScheme.surfaceContainerHigh
    val foreground=if(badge) contrastContentColor(badgeBackground)
        else if(style.open) MaterialTheme.colorScheme.onSurface else ink.content
    val warning=MaterialTheme.colorScheme.error
    // An amber of its own, so notice and delay (warning colour) never look alike.
    val noticeInk=NOTICE_MARK_COLOR
    val appArtwork=if(style==TimelineBarStyle.APP_THEME)AppChartArtwork(LocalAppVisuals.current,MaterialTheme.colorScheme,LocalAppSettings.current.reduceMotion) else null
    val shape=designPreviewShape(6.dp)
    val density=androidx.compose.ui.platform.LocalDensity.current
    val textStyle=MaterialTheme.typography.labelMedium.copy(shadow=
        if(appearance.effect==TimelineLabelEffect.PLAIN) null else Shadow(
            (if(foreground.luminance()>.5f) Color.Black else Color.White).copy(alpha=.55f),
            Offset(0f,with(density) {.7.dp.toPx()}),with(density) {.6.dp.toPx()}))
    // Filled bars cast their own shadow (outside the clip), the time chips no longer do.
    val silhouette=remember(style,appArtwork) {if(style.open) null else TimelineBarSilhouette(style,appArtwork)}
    val shadowInk=androidx.compose.ui.graphics.lerp(Color.Black,MaterialTheme.colorScheme.primary,.1f)
    // In "App-Theme" the bars cast the shadow of the design language: ThemeLanguage collections
    // their own (hard, relief, halo or none), DesignLanguage themes none, a hard offset or the soft default.
    val languageBar=appArtwork?.visuals?.theme.themeLanguageOrStandard!=dev.ambivalenton.openv.data.model.ThemeLanguage.STANDARD
    val language=if(style==TimelineBarStyle.APP_THEME) LocalAppVisuals.current.language else dev.ambivalenton.openv.data.model.DesignLanguage.MATERIAL
    val hardInk=if(MaterialTheme.colorScheme.background.luminance()<.3f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val barShadow=silhouette?.let {outline->when {
        languageBar->languageBarShadow(appArtwork,ink)?.let {Modifier.frameShadow(it,outline)} ?: Modifier
        else->when(language.spec.shadow) {
            dev.ambivalenton.openv.data.model.LanguageShadow.NONE->Modifier
            dev.ambivalenton.openv.data.model.LanguageShadow.HARD,dev.ambivalenton.openv.data.model.LanguageShadow.STEPPED->
                if(language==dev.ambivalenton.openv.data.model.DesignLanguage.MATERIAL) Modifier.timelineBarShadow(outline,TIMELINE_BAR_ELEVATION,shadowInk)
                else Modifier.hardShadow(outline,hardInk,2.5f)
            else->Modifier.timelineBarShadow(outline,TIMELINE_BAR_ELEVATION,shadowInk)
        }
    }} ?: Modifier
    Box(modifier.then(barShadow).clipToBounds()) {
        Layout(modifier=Modifier.fillMaxSize(),content={
            Box(Modifier.testTag("timeline-bar-artwork").drawWithCache {
                val backdrop=if(appArtwork!=null&&!appArtwork.quiet)buildFullThemeBackdrop(appArtwork.visuals.theme?.texture,size)else null
                onDrawBehind {drawTimelineBarWithDelay(style,ink,delayInfo,warning,appArtwork,backdrop,if(noticeMark) noticeInk else null)}
            })
            if(showLabel && appearance.scale>0f) Box(Modifier.testTag("timeline-bar-label").semantics {
                this[TimelineLabelScaleKey]=appearance.scale;this[TimelineLabelOpacityKey]=appearance.opacity
                this[TimelineLabelEffectKey]=appearance.effect.name
            }.then(if(badge) Modifier.shadow(2.dp,shape,clip=false)
                .background(badgeBackground,shape)
                .padding(horizontal=4.dp,vertical=2.dp) else Modifier)) {
                Column(horizontalAlignment=androidx.compose.ui.Alignment.CenterHorizontally) {
                    Text(label,color=foreground,style=textStyle,
                        fontFamily=dev.ambivalenton.openv.ui.theme.LocalAppVisuals.current.customFont?:if(style==TimelineBarStyle.TERMINAL || style==TimelineBarStyle.PIXEL) FontFamily.Monospace else null,
                        fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                    if(showDelayLabel && delayInfo!=null && (delayInfo.cancelled || (delayInfo.minutes ?: 0)>0))
                        Text(if(delayInfo.cancelled) tr("Fällt aus") else tr("+%1\$s Min.",delayInfo.minutes ?: 0),
                            color=foreground,style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis,
                            modifier=Modifier.testTag("timeline-delay-label"))
                }
            }
        }) {items,constraints->
            val w=constraints.maxWidth;val h=constraints.maxHeight
            val artwork=items[0].measure(Constraints.fixed(w,h))
            val text=items.getOrNull(1)?.measure(Constraints(maxWidth=(w-4.dp.roundToPx()).coerceAtLeast(0)))
            val top=labelInsetTop.roundToPx().coerceIn(0,h);val bottom=labelInsetBottom.roundToPx().coerceIn(0,h-top)
            val zone=h-top-bottom
            val fit=if(text!=null && text.height>0) min(1f,(zone-2.dp.roundToPx()).coerceAtLeast(0).toFloat()/text.height) else 0f
            layout(w,h) {
                artwork.placeRelative(0,0)
                if(text!=null && fit>0f) text.placeRelativeWithLayer((w-text.width)/2,top+(zone-text.height)/2) {
                    scaleX=appearance.scale*fit;scaleY=appearance.scale*fit;alpha=appearance.opacity
                }
            }
        }
        content()
    }
}

/** The colour of the notice band in the bars (amber, apart from the warning red of delays). */
val NOTICE_MARK_COLOR=androidx.compose.ui.graphics.Color(0xFFE39A12)
