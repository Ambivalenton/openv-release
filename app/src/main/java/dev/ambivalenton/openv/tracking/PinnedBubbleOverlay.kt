// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.tracking

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.ambivalenton.openv.MainActivity
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.components.AppIcon
import dev.ambivalenton.openv.ui.components.shapeShadow
import dev.ambivalenton.openv.ui.network.shapeContour
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.roundToInt

/** Turns off the window manager's own slide to a new window position. Public from Android 14;
 * before that the same switch is a private flag (0x40), set defensively and skipped if unavailable. */
@SuppressLint("DiscouragedPrivateApi","SoonBlockedPrivateApi")
private fun disableSystemMoveAnimation(params:WindowManager.LayoutParams) {
    if(Build.VERSION.SDK_INT>=34) {params.setCanPlayMoveAnimation(false);return}
    try {
        val field=WindowManager.LayoutParams::class.java.getField("privateFlags")
        field.setInt(params,field.getInt(params) or 0x00000040)
    } catch(_:Throwable) {}
}

/** Lifecycle and saved-state owner for a ComposeView in a WindowManager window (no Activity). */
private class OverlayOwner:LifecycleOwner,SavedStateRegistryOwner {
    private val registry=LifecycleRegistry(this)
    private val saved=SavedStateRegistryController.create(this)
    override val lifecycle:Lifecycle get()=registry
    override val savedStateRegistry:SavedStateRegistry get()=saved.savedStateRegistry
    fun resume() {saved.performAttach();saved.performRestore(null);registry.currentState=Lifecycle.State.RESUMED}
    fun destroy() {if(registry.currentState!=Lifecycle.State.INITIALIZED)registry.currentState=Lifecycle.State.DESTROYED}
}

/** Android 12+: a window context receives configuration changes (dark mode, font scale, density).
 * Older versions keep the application context (the display variant of the API needs 31). */
private fun overlayContext(context:Context):Context = if(Build.VERSION.SDK_INT>=31) try {
    val display=context.getSystemService(android.hardware.display.DisplayManager::class.java).getDisplay(android.view.Display.DEFAULT_DISPLAY)
    context.createWindowContext(display,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,null)
} catch(_:RuntimeException) {context} else context

/**
 * The floating bubble window. Only the bubble (and, while dragging, a non-touchable close zone)
 * occupy windows; there is never an invisible full-screen window blocking other apps. All calls
 * happen on the main thread; show/hide are idempotent. Positions are clamped to the usable screen
 * (system bars and display cutouts excluded) and restored on the docked side.
 *
 * The window is larger than the visible bubble by [PinnedBubbleMetrics.SHADOW_INSET_DP] on every
 * side, so the shadow fades out inside the window instead of ending at a hard edge. All position
 * rules work on the visible bubble; only the window offset subtracts the inset. [attached] is true
 * exactly while a bubble window is really added; the overview hides the pinned card only then.
 */
internal class PinnedBubbleOverlay(context:Context,private val attached:MutableStateFlow<Boolean> = MutableStateFlow(false),
    private val onDismiss:(String)->Unit) {
    private val context=overlayContext(context)
    private val windows=this.context.getSystemService(WindowManager::class.java)
    private val density=this.context.resources.displayMetrics.density
    private val bubbleSize=(PinnedBubbleMetrics.BUBBLE_DP*density).roundToInt()
    private val margin=(PinnedBubbleMetrics.EDGE_MARGIN_DP*density).roundToInt()
    private val inset=(PinnedBubbleMetrics.SHADOW_INSET_DP*density).roundToInt()
    private val cardWidth=(PinnedBubbleMetrics.CARD_DP*density).roundToInt()
    private val zoneHeight=(PinnedBubbleMetrics.CLOSE_ZONE_DP*density).roundToInt()
    private val touchSlop=ViewConfiguration.get(this.context).scaledTouchSlop.toFloat()
    private val preferences=this.context.getSharedPreferences("pinned_bubble",Context.MODE_PRIVATE)
    private var owner:OverlayOwner?=null
    private var bubble:ComposeView?=null
    private var zone:ComposeView?=null
    private var animator:ValueAnimator?=null
    private var dockedRight=preferences.getBoolean("right",true)
    /** Visible top-left corner of the bubble head (or of the expanded card), not of the window. */
    private var visibleX=0
    private var visibleY=0
    private val journey=mutableStateOf<PinnedJourneyState?>(null)
    private val appSettings=mutableStateOf(AppSettings())
    private val expanded=mutableStateOf(false)
    private val zoneHot=mutableStateOf(false)
    private val params=WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        // NO_LIMITS: the transparent shadow margin may extend past the screen edge while the visible
        // bubble keeps its edge margin.
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT).apply {
        gravity=Gravity.TOP or Gravity.START
        if(Build.VERSION.SDK_INT>=28)layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        title="Öpen-V bubble"
        // The system slides an overlay window to every new x/y on its own. On the right edge the
        // window's left edge moves when the card opens or closes, so the card glided in from off
        // screen and back (build 67). The bubble animates its own glides; the system must not.
        disableSystemMoveAnimation(this)
    }
    val showing get()=bubble!=null

    fun update(state:PinnedJourneyState,settings:AppSettings) {journey.value=state;appSettings.value=settings}

    fun show() {
        if(bubble!=null)return
        val lifecycle=OverlayOwner().also {it.resume()};owner=lifecycle
        val view=ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycle);setViewTreeSavedStateRegistryOwner(lifecycle)
            setContent {OpenVTheme(appSettings.value) {BubbleContent()}}
        }
        val area=usableArea()
        val rest=bubbleRestoredPosition(dockedRight,preferences.getFloat("y",.35f),bubbleSize,area,margin)
        place(rest.x,rest.y)
        try {windows.addView(view,params);bubble=view;attached.value=true}
        catch(_:RuntimeException) {lifecycle.destroy();owner=null;attached.value=false} // Access revoked meanwhile: stay hidden.
    }

    fun hide() {
        animator?.cancel();animator=null
        hideZone()
        bubble?.let {view->view.disposeComposition();removeSafely(view)}
        bubble=null;expanded.value=false;windowOpen=false;attached.value=false
        owner?.destroy();owner=null
    }

    /** Rotation or size change: keep the side and relative height, inside the new usable area. */
    fun reclamp() {
        val view=bubble ?: return
        animator?.cancel()
        val area=usableArea()
        val rest=bubbleRestoredPosition(dockedRight,preferences.getFloat("y",.35f),bubbleSize,area,margin)
        place(if(expanded.value)bubbleCardX(dockedRight,rest.x,bubbleSize,cardWidth,area,margin) else rest.x,rest.y)
        try {windows.updateViewLayout(view,params)}catch(_:RuntimeException){}
    }

    private fun removeSafely(view:View) {try {if(view.isAttachedToWindow)windows.removeViewImmediate(view) else windows.removeView(view)}catch(_:RuntimeException){}}

    // Android 8–10 offer no WindowMetrics for an overlay window; the system bar dimensions are the
    // only available approximation there. From Android 11 on the real window insets are used.
    @Suppress("DEPRECATION") @SuppressLint("InternalInsetResource","DiscouragedApi")
    private fun usableArea():BubbleArea {
        if(Build.VERSION.SDK_INT>=30) {
            val metrics=windows.currentWindowMetrics
            val insets=metrics.windowInsets.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
            val b=metrics.bounds
            return BubbleArea(b.left+insets.left,b.top+insets.top,b.right-insets.right,b.bottom-insets.bottom)
        }
        val size=android.graphics.Point();windows.defaultDisplay.getRealSize(size)
        val id=context.resources.getIdentifier("status_bar_height","dimen","android")
        val status=if(id>0)context.resources.getDimensionPixelSize(id) else (24*density).roundToInt()
        val navId=context.resources.getIdentifier("navigation_bar_height","dimen","android")
        val nav=if(navId>0)context.resources.getDimensionPixelSize(navId) else 0
        return BubbleArea(0,status,size.x,size.y-nav)
    }

    /** Visible position → window position: the window starts one shadow inset earlier. */
    private fun place(x:Int,y:Int) {visibleX=x;visibleY=y;params.x=x-inset;params.y=y-inset}

    private fun move(x:Int,y:Int) {
        val view=bubble ?: return
        place(x,y)
        try {windows.updateViewLayout(view,params)}catch(_:RuntimeException){}
    }

    private fun showZone() {
        if(zone!=null || owner==null)return
        val area=usableArea()
        val zoneParams=WindowManager.LayoutParams(area.width,zoneHeight,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // The close zone never takes touches; they keep flowing to the bubble and other apps.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,PixelFormat.TRANSLUCENT).apply {
            gravity=Gravity.TOP or Gravity.START;x=area.left;y=area.bottom-zoneHeight;title="Öpen-V bubble close zone"
        }
        val view=ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner);setViewTreeSavedStateRegistryOwner(owner)
            setContent {OpenVTheme(appSettings.value) {CloseZone(zoneHot.value)}}
        }
        try {windows.addView(view,zoneParams);zone=view}catch(_:RuntimeException){}
    }

    private fun hideZone() {zone?.let {view->view.disposeComposition();removeSafely(view)};zone=null;zoneHot.value=false}

    private fun settle(velocityX:Float,velocityY:Float) {
        val area=usableArea()
        val rest=bubbleRestingPosition(visibleX,visibleY,bubbleSize,velocityX,velocityY,area,margin,PinnedBubbleMetrics.MAX_FLING_DP_PER_SECOND*density)
        dockedRight=rest.dockedRight
        preferences.edit {putBoolean("right",rest.dockedRight);putFloat("y",bubbleVerticalFraction(rest.y,bubbleSize,area,margin))}
        animateTo(rest.x,rest.y)
    }

    private fun animateTo(x:Int,y:Int) {
        animator?.cancel()
        val startX=visibleX;val startY=visibleY
        if(appSettings.value.reduceMotion) {move(x,y);return}
        // A short native decelerating glide; it ends at rest and leaves no running loop behind.
        animator=ValueAnimator.ofFloat(0f,1f).apply {
            duration=280;interpolator=DecelerateInterpolator(1.6f)
            addUpdateListener {val f=it.animatedValue as Float
                move((startX+(x-startX)*f).roundToInt(),(startY+(y-startY)*f).roundToInt())}
            start()
        }
    }

    private fun dockX(area:BubbleArea)=if(dockedRight)area.right-bubbleSize-margin else area.left+margin

    private fun setExpanded(open:Boolean) {
        if(expanded.value==open)return
        animator?.cancel()
        // Only the state changes here; the window follows in syncExpansion() from the composition
        // that shows (or hides) the card, so content and window position change in the same frame.
        expanded.value=open
    }

    /** Expansion the window position currently belongs to. */
    private var windowOpen=false

    /** Called from the composition (SideEffect) once the card is composed or removed: on the right
     * edge the window's left edge moves to the card's left edge (or back to the bubble). */
    private fun syncExpansion(open:Boolean) {
        if(open==windowOpen)return
        windowOpen=open
        if(bubble==null)return
        val area=usableArea()
        move(when {
            open->bubbleCardX(dockedRight,dockX(area),bubbleSize,cardWidth,area,margin)
            gesture.dragging->visibleX
            else->dockX(area)
        },visibleY)
    }

    private fun toggle()=setExpanded(!expanded.value)

    private fun openTimeline(sessionId:String) {
        // Same entry as the notification: opens the in-app timeline of this pinned journey and
        // keeps the pin. The mini card folds away so it never covers the opened timeline.
        setExpanded(false)
        val intent=Intent(context,MainActivity::class.java).apply {
            action=Intent.ACTION_VIEW;data="openv://overview".toUri()
            putExtra(PinnedJourneyService.EXTRA_PINNED_ID,sessionId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        try {context.startActivity(intent)}catch(_:RuntimeException){}
    }

    private fun dismiss(sessionId:String) {
        // Only the overlay goes away. The pin, the journey companion and the card in the overview stay.
        hideZone();onDismiss(sessionId)
        try {Toast.makeText(context,tr("Schwebende Reisekarte ausgeblendet · Die Karte ist wieder in der Übersicht."),Toast.LENGTH_SHORT).show()}
        catch(_:RuntimeException){}
        // Removal is posted: a window is not removed inside its own dispatch.
        bubble?.post {hide()}
    }

    private inner class Gesture {
        var downX=0f;var downY=0f;var startX=0;var startY=0;var downAt=0L;var dragging=false
        var tracker:VelocityTracker?=null
        fun handle(event:MotionEvent,sessionId:String?):Boolean {
            val raw=MotionEvent.obtain(event).apply {setLocation(event.rawX,event.rawY)}
            try {
                when(event.actionMasked) {
                    MotionEvent.ACTION_DOWN-> {
                        // A new touch stops any remaining glide immediately.
                        animator?.cancel();animator=null
                        downX=event.rawX;downY=event.rawY;startX=visibleX;startY=visibleY;downAt=event.eventTime;dragging=false
                        tracker?.recycle();tracker=VelocityTracker.obtain().also {it.addMovement(raw)}
                    }
                    MotionEvent.ACTION_MOVE-> {
                        tracker?.addMovement(raw)
                        val dx=event.rawX-downX;val dy=event.rawY-downY
                        if(!dragging && bubbleDragged(dx,dy,touchSlop)) {
                            dragging=true
                            if(expanded.value) {expanded.value=false;startX=dockX(usableArea())}
                            showZone()
                        }
                        if(dragging) {
                            move((startX+dx).roundToInt(),(startY+dy).roundToInt())
                            zoneHot.value=bubbleInCloseZone(visibleY+bubbleSize/2f,usableArea(),zoneHeight)
                        }
                    }
                    MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL-> {
                        tracker?.addMovement(raw);tracker?.computeCurrentVelocity(1000)
                        val vx=tracker?.xVelocity ?: 0f;val vy=tracker?.yVelocity ?: 0f
                        tracker?.recycle();tracker=null
                        val cancelled=event.actionMasked==MotionEvent.ACTION_CANCEL
                        when {
                            !dragging && !cancelled && bubbleIsTap(event.rawX-downX,event.rawY-downY,touchSlop,event.eventTime-downAt)->toggle()
                            dragging && !cancelled && zoneHot.value && sessionId!=null-> {dismiss(sessionId);dragging=false;return true}
                            dragging->settle(if(cancelled)0f else vx,if(cancelled)0f else vy)
                        }
                        hideZone();dragging=false
                    }
                }
            } finally {raw.recycle()}
            return true
        }
    }
    private val gesture=Gesture()

    @OptIn(ExperimentalComposeUiApi::class)
    @SuppressLint("ClickableViewAccessibility")
    @Composable private fun BubbleContent() {
        val state=journey.value ?: return
        val session=state.session ?: return
        // The same minute clock as the pinned card: card and bubble switch at the same moment.
        val now=dev.ambivalenton.openv.ui.components.rememberJourneyNow()
        // The bubble shows the next change or the destination, not the next intermediate stop.
        val focus=assistantDisplayFocus(state,now)?.let {bubbleJourneyFocus(it,session,now)} ?: return
        val open=expanded.value
        SideEffect {syncExpansion(open)}
        val scheme=MaterialTheme.colorScheme
        val dark=scheme.background.luminance()<.3f
        // The head takes the shape and colours of the destination favourite, like its badge in the app.
        val colors=dev.ambivalenton.openv.ui.theme.favoriteColors(session.favorite.accentSeed,dark,scheme)
        val shape=dev.ambivalenton.openv.widget.GlanceThemeHelper.composeShape(session.favorite.shape)
        val shadowInk=androidx.compose.ui.graphics.lerp(androidx.compose.ui.graphics.Color.Black,scheme.primary,.10f)
        val head:@Composable ()->Unit={
            Box(Modifier.size(PinnedBubbleMetrics.BUBBLE_DP.dp)
                // Own shadow in the theme's shadow ink (the platform shadow crashed with concave shapes);
                // the window inset leaves room for it.
                .shapeShadow(shape,6.dp,shadowInk)
                .background(colors.accent,shape)
                .shapeContour(shape,1.5.dp,scheme.surface.copy(alpha=.85f)).testTag("pinned-bubble")
                .semantics {role=Role.Button;contentDescription=tr("Schwebende Reisekarte: %1\$s um %2\$s",focus.heading(),clockTime(focus.nextTime))
                    onClick(tr(if(expanded.value)"Mini-Karte schließen" else "Mini-Karte öffnen")) {toggle();true}}
                .pointerInteropFilter {event->gesture.handle(event,session.id)},contentAlignment=Alignment.Center) {
                Text(clockTime(focus.nextTime),style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.Bold,
                    color=colors.onAccent,maxLines=1)
            }
        }
        Column(Modifier.padding(PinnedBubbleMetrics.SHADOW_INSET_DP.dp),
            horizontalAlignment=if(dockedRight)Alignment.End else Alignment.Start,verticalArrangement=Arrangement.spacedBy(6.dp)) {
            head()
            // The expanded card is the pinned card of the app in its compact form: one design, two sizes.
            if(expanded.value)dev.ambivalenton.openv.ui.components.PinnedJourneyCard(state,onOpen={openTimeline(session.id)},onRemove={},
                focusTransform={f,at->bubbleJourneyFocus(f,session,at)},
                modifier=Modifier.width(PinnedBubbleMetrics.CARD_DP.dp),testTag="pinned-bubble-card",headerActions={
                    IconButton({openTimeline(session.id)},Modifier.size(42.dp).testTag("pinned-bubble-expand")) {
                        AppIcon(R.drawable.ic_journey_timeline,tr("Vergrößern: Zeitstrahl öffnen"))
                    }
                })
        }
    }

    @Composable private fun CloseZone(hot:Boolean) {
        val scheme=MaterialTheme.colorScheme
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(
            listOf(androidx.compose.ui.graphics.Color.Transparent,scheme.scrim.copy(alpha=if(hot).55f else .32f)))),
            contentAlignment=Alignment.Center) {
            Row(Modifier.background(if(hot)scheme.errorContainer else scheme.surfaceContainerHigh,CircleShape).padding(horizontal=14.dp,vertical=8.dp),
                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                AppIcon(R.drawable.ic_close,null,Modifier.size(20.dp),tint=if(hot)scheme.onErrorContainer else scheme.onSurface)
                Text(tr("Hier ablegen zum Ausblenden"),style=MaterialTheme.typography.labelLarge,textAlign=TextAlign.Center,
                    color=if(hot)scheme.onErrorContainer else scheme.onSurface)
            }
        }
    }
}
