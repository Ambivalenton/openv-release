// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import dev.ambivalenton.openv.data.model.reduceMotion
import android.animation.ValueAnimator
import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.theme.LocalAppSettings
import dev.ambivalenton.openv.ui.theme.appCorner
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlin.math.*

/**
 * The search button. One wide button with the magnifier on the left and "Verbindung suchen"
 * next to it (the former label box and the round button merged). It forms out of a surge of liquid
 * that falls out of the destination field, hits the lower edge of the map and springs up into place
 * ([LiquidSurge]); pressed, it tilts in where the finger is; it floats over the map on a strong soft
 * shadow. On removal it hands itself to the [LiquidStage] and melts over the navigation pill; the
 * in-place dissolve remains where no stage exists.
 */
@Composable fun LiquidSearchAction(sourceInRoot:Offset?,selectionKey:Any,enabled:Boolean,animate:Boolean,
 onRevealed:()->Unit,onClick:()->Unit,modifier:Modifier=Modifier,visible:Boolean=true,onHidden:()->Unit={},
 /** Upper edge of the button in this item. */
 buttonTop:Dp=0.dp,height:Dp=LiquidSearchActionHeight,
 /** What the search will do with the chosen time (see [searchButtonLabel]). */
 label:String=tr("Verbindung suchen")) {
 val quiet=LocalAppSettings.current.reduceMotion
 val progress=remember {Animatable(if(animate&&!quiet)0f else 1f)}
 val finished by rememberUpdatedState(onRevealed)
 val hidden by rememberUpdatedState(onHidden)
 val retreat=remember {Animatable(0f)}
 val stage=LocalLiquidStage.current
 val density=LocalDensity.current
 val direction=LocalLayoutDirection.current
 // The visible button: upper left corner in root coordinates and size; symbol and label centres in root.
 var origin by remember {mutableStateOf(Offset.Zero)}
 var buttonSize by remember {mutableStateOf(IntSize.Zero)}
 var iconCenter by remember {mutableStateOf<Offset?>(null)}
 var labelCenter by remember {mutableStateOf<Offset?>(null)}
 // Once handed to the liquid layer nothing is drawn here any more (the item is removed right after).
 var handedOff by remember {mutableStateOf(false)}
 val buttonColor=MaterialTheme.colorScheme.primary
 val ink=MaterialTheme.colorScheme.onPrimary
 val labelStyle=MaterialTheme.typography.labelLarge
 val labelText=label
 val buttonHeight=searchButtonHeight()
 val shape=appCorner(buttonHeight/2)
 val margin=SearchButtonTouchMargin
 val solid by remember {derivedStateOf {progress.value>=1f}}
 val scope=rememberCoroutineScope()
 // The press: where the finger is (-1…1 across and down the button) and how far it is pressed in.
 val press=remember {Animatable(0f)}
 var pressAt by remember {mutableStateOf(Offset.Zero)}
 var held by remember {mutableStateOf(false)}
 var clicking by remember {mutableStateOf(false)}
 LaunchedEffect(held) {
  if(held) press.animateTo(1f,tween(90,easing=FastOutSlowInEasing))
  else if(!clicking) press.animateTo(0f,spring(dampingRatio=.42f,stiffness=520f))
 }
 val latestClick by rememberUpdatedState(onClick)
 val outline=remember(shape,buttonSize,density,direction) {
  if(buttonSize==IntSize.Zero) null else shape.createOutline(buttonSize.toSize(),direction,density)
 }
 val contour=remember(outline,buttonSize) {
  outline?.let {evenOutline(Path().apply {addOutline(it)}.asAndroidPath(),Offset(buttonSize.width/2f,buttonSize.height/2f))}.orEmpty()
 }
 val shadow=remember(outline,buttonSize,density,quiet) {
  if(quiet) null else outline?.let {ButtonShadow.of(Path().apply {addOutline(it)}.asAndroidPath(),buttonSize.toSize(),density.density)}
 }
 fun geometry():SurgeGeometry {
  val w=buttonSize.width.toFloat();val h=buttonSize.height.toFloat();val target=Offset(w/2,h/2)
  val source=sourceInRoot?.minus(origin)?:target-Offset(0f,h*5f)
  return SurgeGeometry(source,h+SearchActionMapGap.value*density.density,target,w,h)
 }
 LaunchedEffect(selectionKey,quiet,visible) {
  val duration=currentCoroutineContext()[MotionDurationScale]?.scaleFactor?:1f
  val reduced=quiet||!ValueAnimator.areAnimatorsEnabled()||duration<=0
  if(!visible) {
   if(stage!=null&&!reduced&&buttonSize!=IntSize.Zero) {
    // Hand over what is there right now: the button, or the surge still on its way.
    val w=buttonSize.width.toFloat();val h=buttonSize.height.toFloat();val middle=origin+Offset(w/2,h/2)
    val frame=if(progress.value>=1f) null else liquidSurgeFrame(progress.value*LIQUID_SURGE_SECONDS,geometry())
    val center=frame?.let {origin+it.center}?:middle
    val sx=frame?.let {it.halfWidth/(w/2)}?:1f
    val points=frame?.let {surgePoints(it,contour,w/2,h/2)}?:contour
    stage.release(center,frame?.halfHeight?:(h/2),points,buttonColor,ink,labelText,
     center+((labelCenter?:middle)-middle)*sx,labelStyle,ink,halfWidth=frame?.halfWidth?:(w/2),
     iconOffset=((iconCenter?:middle)-middle)*sx,iconSize=24*density.density,
     radius=wideDropRadius(w/2,h/2,density.density),shadow=if(shadow==null) 0f else frame?.shadow?:1f,content=frame?.content?:1f)
    handedOff=true;hidden();return@LaunchedEffect
   }
   if(reduced)retreat.snapTo(1f) else retreat.animateTo(1f,tween(LIQUID_SEARCH_EXIT_DURATION_MS,easing=LinearEasing))
   hidden()
  }else {
   handedOff=false
   retreat.snapTo(0f)
   if(reduced||!animate)progress.snapTo(1f)
   else {progress.snapTo(0f);progress.animateTo(1f,tween((LIQUID_SURGE_SECONDS*1000).roundToInt(),easing=LinearEasing))}
   finished()
  }
 }
 val color=buttonColor.copy(alpha=if(enabled||!visible)1f else .38f)
 if(handedOff) {Box(modifier.fillMaxWidth().height(height));return}
 // H03: the action is usable (touch, keyboard, TalkBack) as soon as a place is chosen; the surge only
 // decorates the button that is already there. Only the press tilt waits for the formed button.
 val interactive=visible
 Box(modifier.fillMaxWidth().height(height)) {
  // As wide as its label needs, up to the whole width above the map.
  Box(Modifier.offset(y=buttonTop-margin).fillMaxWidth().padding(horizontal=12.dp).height(buttonHeight+margin*2)) {
   Box(Modifier.align(Alignment.Center).fillMaxHeight().widthIn(min=SearchButtonMinWidth)
    .then(if(interactive&&enabled&&solid) Modifier.pointerInput(Unit) {
     awaitEachGesture {
      val down=awaitFirstDown(requireUnconsumed=false)
      val inner=(this.size.height-2*margin.toPx()).coerceAtLeast(1f)
      pressAt=Offset((down.position.x/this.size.width*2-1).coerceIn(-1f,1f),((down.position.y-margin.toPx())/inner*2-1).coerceIn(-1f,1f))
      held=true
      try {waitForUpOrCancellation()} finally {held=false}
     }
    } else Modifier)
    .then(if(interactive) Modifier.testTag("search-submit")
     .clickable(interactionSource=null,indication=null,enabled=enabled,role=Role.Button,onClickLabel=labelText) {
      // H03: the tap counts at once; the press animation follows it instead of delaying it.
      latestClick()
      if(!quiet && solid) scope.launch {
       clicking=true
       try {press.animateTo(1f,tween(80,easing=FastOutSlowInEasing))}
       finally {clicking=false}
       press.animateTo(0f,spring(dampingRatio=.42f,stiffness=520f))
      }
     } else Modifier.testTag("search-submit-dissolving"))
    .padding(vertical=margin)
    .onGloballyPositioned {origin=it.positionInRoot();buttonSize=it.size}
    // Shadow and liquid: drawn without the tilt, under everything else.
    .drawBehind {
     val w=this.size.width;val h=this.size.height
     val o=outline?:return@drawBehind
     if(!visible) {
      // No stage: the button sinks into a pool in place.
      val exit=liquidSearchExit(retreat.value)
      scale(1f+.35f*(1-exit.height),exit.height.coerceAtLeast(.001f),Offset(w/2,h)) {drawOutline(o,color.copy(alpha=color.alpha*exit.alpha))}
      return@drawBehind
     }
     if(progress.value>=1f) {
      val tilt=if(quiet) 0f else pressAt.x
      shadow?.let {with(it) {draw(Offset(w/2,h/2),1f,1f,1f,press.value,tilt)}}
      return@drawBehind
     }
     val t=progress.value*LIQUID_SURGE_SECONDS;val g=geometry();val frame=liquidSurgeFrame(t,g)
     shadow?.let {with(it) {draw(frame.center,frame.halfWidth/(w/2),frame.halfHeight/(h/2),frame.shadow,0f,0f)}}
     drawPath(surgeOutline(frame,g.source,contour,w/2,h/2),color)
     for(i in 0 until 6) surgeDroplet(i,t,g)?.let {(c,r)->drawCircle(color,r,c)}
     // A soft highlight while it is liquid.
     if(frame.morph<1f) drawOval(Color.White.copy(alpha=.20f*(1-frame.morph)),
      Offset(frame.center.x-frame.halfWidth*.62f,frame.center.y-frame.halfHeight*.62f),Size(frame.halfWidth*.42f,frame.halfHeight*.34f))
    }
    .graphicsLayer {
     // Pressed where the finger is: that side goes down, the other comes up a little.
     val k=if(progress.value>=1f) press.value else 0f
     val tilt=if(quiet) 0f else 1f
     rotationY=SEARCH_TILT_Y*pressAt.x*k*tilt;rotationX=SEARCH_TILT_X*pressAt.y*k*tilt
     scaleX=1f-.035f*k;scaleY=1f-.035f*k;translationY=1.5f*density.density*k
     cameraDistance=3.2f*density.density
    }
    .drawBehind {
     val o=outline?:return@drawBehind
     if(!visible||progress.value<1f) return@drawBehind
     drawOutline(o,color)
     // Light falls on the raised side, the pressed side darkens.
     val k=(if(quiet) 0f else press.value)*abs(pressAt.x)
     if(k>.01f) {
      val dark=Color.Black.copy(alpha=.18f*k);val light=Color.White.copy(alpha=.16f*k)
      drawOutline(o,Brush.horizontalGradient(if(pressAt.x<0) listOf(dark,Color.Transparent,light) else listOf(light,Color.Transparent,dark)))
     }
    }
    .padding(horizontal=20.dp),contentAlignment=Alignment.Center) {
    // H03: the label belongs to the button from the first frame (TalkBack reads it during the surge too).
    Row(Modifier.then(if(interactive) Modifier else Modifier.clearAndSetSemantics {})
     .graphicsLayer {
      if(!visible) {alpha=liquidSearchExit(retreat.value).iconAlpha;return@graphicsLayer}
      if(progress.value>=1f) return@graphicsLayer
      val w=buttonSize.width.toFloat();val h=buttonSize.height.toFloat()
      if(w<=0f) {alpha=0f;return@graphicsLayer}
      val frame=liquidSurgeFrame(progress.value*LIQUID_SURGE_SECONDS,geometry())
      alpha=frame.content
      translationX=frame.center.x-w/2;translationY=frame.center.y-h/2
      scaleX=frame.halfWidth/(w/2);scaleY=frame.halfHeight/(h/2)
     },verticalAlignment=Alignment.CenterVertically) {
     AppIcon(R.drawable.ic_search,null,Modifier.size(24.dp)
      .onGloballyPositioned {iconCenter=it.positionInRoot()+Offset(it.size.width/2f,it.size.height/2f)},tint=ink)
     Spacer(Modifier.width(10.dp))
     // One line that grows or shrinks with the space; the button widens before the text gets smaller.
     FittedText(labelText,Modifier.weight(1f,fill=false).testTag("search-submit-label")
      .onGloballyPositioned {labelCenter=it.positionInRoot()+Offset(it.size.width/2f,it.size.height/2f)},color=ink,style=labelStyle,maxLines=1)
    }
   }
  }
 }
}

/** How far a press tilts the button about its vertical and horizontal axis (degrees per unit). */
private const val SEARCH_TILT_Y=6f
private const val SEARCH_TILT_X=-5f

/** The soft shadow the button floats on: a wide ambient one and a tight contact one (pre-blurred, cached per size). */
internal class ButtonShadow private constructor(private val ambient:ImageBitmap,private val contact:ImageBitmap,
 private val ambientOrigin:Offset,private val contactOrigin:Offset,private val d:Float) {
 /** At [center], stretched by [sx]/[sy], [level] of full strength, [press] 0…1 and the press position [tilt] −1…1. */
 fun DrawScope.draw(center:Offset,sx:Float,sy:Float,level:Float,press:Float,tilt:Float) {
  if(level<=.003f) return
  val spread=1f-.07f*press
  withTransform({
   translate(center.x-tilt*press*5f*d,center.y+(10f-6f*press)*d)
   rotate(-tilt*press*2.2f,Offset.Zero)
   scale(sx*spread/AMBIENT_RES,sy*spread/AMBIENT_RES,Offset.Zero)
  }) {drawImage(ambient,-ambientOrigin,alpha=(level*(.48f-.24f*press)).coerceIn(0f,1f))}
  withTransform({
   translate(center.x,center.y+(2.6f-1.4f*press)*d)
   scale(sx*(1f-.02f*press),sy*(1f-.02f*press),Offset.Zero)
  }) {drawImage(contact,-contactOrigin,alpha=(level*(.30f+.08f*press)).coerceIn(0f,1f))}
 }
 companion object {
  private const val AMBIENT_RES=.5f
  fun of(path:android.graphics.Path,size:Size,d:Float):ButtonShadow {
   val (a,ao)=render(path,size,15f*d,AMBIENT_RES);val (c,co)=render(path,size,3.5f*d,1f)
   return ButtonShadow(a,c,ao,co,d)
  }
  /** The outline filled black and blurred by [blur] px, at [res] bitmap pixels per px; plus its centre in the bitmap. */
  private fun render(path:android.graphics.Path,size:Size,blur:Float,res:Float):Pair<ImageBitmap,Offset> {
   val pad=blur*1.9f+2f
   val w=ceil((size.width+2*pad)*res).toInt().coerceAtLeast(1);val h=ceil((size.height+2*pad)*res).toInt().coerceAtLeast(1)
   val bitmap=android.graphics.Bitmap.createBitmap(w,h,android.graphics.Bitmap.Config.ARGB_8888)
   val placed=android.graphics.Path(path).apply {transform(android.graphics.Matrix().apply {setTranslate(pad,pad);postScale(res,res)})}
   val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
    color=android.graphics.Color.BLACK
    if(blur*res>=.5f) maskFilter=BlurMaskFilter(blur*res,BlurMaskFilter.Blur.NORMAL)
   }
   android.graphics.Canvas(bitmap).drawPath(placed,paint)
   return bitmap.asImageBitmap() to Offset((size.width/2+pad)*res,(size.height/2+pad)*res)
  }
 }
}

/** Radius of the drop a wide button melts into: more liquid than the round button, within reason. */
internal fun wideDropRadius(halfWidth:Float,halfHeight:Float,density:Float)=(sqrt(halfWidth*halfHeight)*.7f).coerceIn(27f*density,36f*density)

/** Button and label of the search action; the item around it may be much taller. */
val LiquidSearchActionHeight=94.dp

/** Height of the former label box (one line of the label style plus room). */
@Composable internal fun searchActionLabelHeight():Dp =
 with(LocalDensity.current) {MaterialTheme.typography.labelLarge.lineHeight.let {if(it.isSp) it.toDp() else 20.dp}+12.dp}

/** The merged search button is 10 dp taller than the former label box. */
@Composable internal fun searchButtonHeight():Dp=searchActionLabelHeight()+10.dp

/** The button is at least this wide. */
internal val SearchButtonMinWidth=208.dp

/** Invisible room above and below the button that still takes the tap (48 dp targets). */
internal val SearchButtonTouchMargin=3.dp

/** Gap between the map's lower edge and the button (the surge lands on that edge). */
internal val SearchActionMapGap=10.dp
