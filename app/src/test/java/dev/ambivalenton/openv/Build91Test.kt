// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.components.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.*

/** The surge that forms the wide search button, its melt, the map's pin focus and calm chart clocks. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class Build91Test {
    // A 300 × 42 dp button at density 2: target centre, the map's edge 10 dp below its lower edge.
    private val w=600f;private val h=84f
    private val target=Offset(540f,1400f)
    private val floor=target.y+h/2+20f
    private val g=SurgeGeometry(Offset(520f,200f),floor,target,w,h)
    private fun at(t:Float)=liquidSurgeFrame(t,g)

    @Test fun theSurgeFallsHitsTheMapEdgeSpringsUpAndSetsExactlyIntoTheButton() {
        // Oozes out of the field edge, attached by its neck.
        val start=at(0f);assertEquals(1f,start.neck,0f);assertTrue(start.center.y-start.halfHeight>=g.source.y-1f)
        // Falls ever faster and lets go of the field long before it lands.
        val fall=(0..20).map {at(SURGE_BUD+(SURGE_IMPACT-SURGE_BUD)*it/20f-1e-4f).center.y}
        assertTrue(fall.zipWithNext().all {(a,b)->b>=a})
        assertTrue(fall.zipWithNext().map {(a,b)->b-a}.zipWithNext().all {(a,b)->b>=a-.5f})
        assertEquals(0f,at(SURGE_BUD+(SURGE_IMPACT-SURGE_BUD)*.5f).neck,0f)
        // Hits the map's lower edge and spreads flat on it, wider than the button.
        val hit=at(SURGE_IMPACT);assertEquals(floor,hit.center.y+hit.halfHeight,1.5f)
        val flat=at(SURGE_RISE-.001f);assertEquals(floor,flat.center.y+flat.halfHeight,.5f)
        assertTrue(flat.halfWidth>w/2);assertTrue(flat.halfHeight<h/2)
        // Springs up past its place once (a jump, not a creep) and comes back.
        val rise=(0..400).map {at(SURGE_RISE+(LIQUID_SURGE_SECONDS-SURGE_RISE)*it/400f)}
        assertTrue("overshoots upwards",rise.minOf {it.center.y}<target.y-2f)
        // Sets exactly into the button: the hand-over to the real button is invisible.
        val end=at(LIQUID_SURGE_SECONDS)
        assertEquals(target.x,end.center.x,.01f);assertEquals(target.y,end.center.y,.01f)
        assertEquals(w/2,end.halfWidth,.01f);assertEquals(h/2,end.halfHeight,.01f)
        assertEquals(1f,end.morph,0f);assertEquals(1f,end.content,0f);assertEquals(1f,end.shadow,0f);assertEquals(0f,end.wobble,1e-4f)
        // Symbol and label only appear once it has set.
        assertEquals(0f,at(SURGE_RISE+.1f).content,0f)
    }

    @Test fun theSurgeMovesContinuouslyWithoutJumps() {
        var last=at(0f)
        var t=.001f
        while(t<=LIQUID_SURGE_SECONDS) {
            val f=at(t)
            // At most 12 px per millisecond (the fastest moment is just before the impact).
            assertTrue("t=$t centre jumps",(f.center-last.center).getDistance()<12f)
            assertTrue("t=$t width jumps",abs(f.halfWidth-last.halfWidth)<6f)
            assertTrue("t=$t height jumps",abs(f.halfHeight-last.halfHeight)<3f)
            assertTrue(abs(f.content-last.content)<.02f && abs(f.shadow-last.shadow)<.02f && abs(f.morph-last.morph)<.02f)
            last=f;t+=.001f
        }
        // Droplets fly only for a moment after the impact.
        assertNull(surgeDroplet(0,SURGE_IMPACT-.05f,g));assertNotNull(surgeDroplet(0,SURGE_IMPACT+.05f,g));assertNull(surgeDroplet(0,SURGE_IMPACT+.5f,g))
    }

    /** Signed distance to a rounded rectangle with fully round ends, centred at 0. */
    private fun sdf(p:Offset,hw:Float,hh:Float):Float {
        val r=min(hw,hh);val qx=abs(p.x)-(hw-r);val qy=abs(p.y)-(hh-r)
        return hypot(max(qx,0f),max(qy,0f))+min(max(qx,qy),0f)-r
    }

    @Test fun theLiquidOutlineRunsAroundAFullyRoundedMassClockwise() {
        for(i in 0 until 200) {
            val (p,n)=roundRectPoint(i/200f,120f,18f)
            assertEquals("on the outline at $i",0f,sdf(p,120f,18f),.01f)
            assertEquals(1f,n.getDistance(),1e-3f)
            assertTrue(sdf(p+n*2f,120f,18f)>1.9f)
        }
        // From the middle of the right side downwards first, then along the bottom to the left.
        assertEquals(Offset(120f,0f),roundRectPoint(0f,120f,18f).first)
        assertEquals(18f,roundRectPoint(.25f,120f,18f).first.y,.01f)
        assertTrue(roundRectPoint(.2f,120f,18f).first.x>0f&&roundRectPoint(.3f,120f,18f).first.x<roundRectPoint(.2f,120f,18f).first.x)
    }

    @Test fun theButtonOutlineIsSampledInTheSameOrderSoTheMassSetsIntoIt() {
        val path=Path().apply {addRoundRect(RoundRect(Rect(0f,0f,w,h),CornerRadius(h/2)))}
        val contour=evenOutline(path.asAndroidPath(),Offset(w/2,h/2))
        assertEquals(SURGE_POINTS,contour.size)
        // Starts next to the middle of the right side, runs clockwise (down first).
        assertTrue(contour[0].x>w/2-2f && abs(contour[0].y)<10f)
        assertTrue(contour[2].y>contour[0].y)
        contour.forEach {assertEquals(0f,sdf(it,w/2,h/2),.5f)}
        // Fully set, the mass is exactly the button's outline.
        val end=liquidSurgeFrame(LIQUID_SURGE_SECONDS,g)
        surgePoints(end,contour,w/2,h/2).zip(contour).forEach {(a,b)->assertEquals(0f,(a-b).getDistance(),.01f)}
        // A sharp-cornered design keeps its corners in the end.
        val square=evenOutline(Path().apply {addRect(Rect(0f,0f,w,h))}.asAndroidPath(),Offset(w/2,h/2))
        val set=surgePoints(end,square,w/2,h/2)
        assertTrue(set.any {abs(it.x)>w/2-1f && abs(it.y)>h/2-1f})
    }

    @Test fun theWideButtonMeltsIntoOneDropOverThePill() {
        val d=2f
        // The round button keeps its drop; the wide one makes a somewhat larger one, within reason.
        assertEquals(27f*d,wideDropRadius(32f*d,32f*d,d),.01f)
        val wide=wideDropRadius(110f*d,21f*d,d);assertTrue(wide>27f*d && wide<=36f*d)
        assertEquals(36f*d,wideDropRadius(400f*d,40f*d,d),.01f)
        // It starts as exactly the button and gathers sideways into the drop while it sags.
        val start=liquidDropBody(0f,Offset(500f,1300f),h/2,wide,null,2000f,halfWidth=w/2)!!
        assertEquals(w/2,start.rx,.01f);assertEquals(h/2,start.ry,.01f);assertEquals(0f,start.morph,0f)
        val melted=liquidDropBody(LIQUID_MELT_END,Offset(500f,1300f),h/2,wide,null,2000f,halfWidth=w/2)!!
        assertEquals(wide*1.04f,melted.rx,.5f);assertEquals(1f,melted.morph,0f)
        val path=Path().apply {addRoundRect(RoundRect(Rect(0f,0f,w,h),CornerRadius(h/2)))}
        val contour=evenOutline(path.asAndroidPath(),Offset(w/2,h/2))
        val outline=dropOutline(contour,h/2,start,w/2).getBounds()
        assertEquals(500f-w/2,outline.left,1f);assertEquals(500f+w/2,outline.right,1f)
        assertEquals(1300f-h/2,outline.top,1f);assertEquals(1300f+h/2,outline.bottom,1f)
    }

    @Test fun theMapPutsThePlaceWhereItIsAskedFor() {
        val place=Location("Rottbruchstraße 41",null,51.4964,7.4655,"OSM",LocationKind.ADDRESS)
        val middle=destinationMapCamera(place,1000f,800f,2f)!!
        assertEquals(Offset(500f,400f),middle.toPx(middle.center,1000f,800f))
        val focused=destinationMapCamera(place,1000f,800f,2f,focusY=300f)!!
        assertEquals(500f,focused.toPx(focused.center,1000f,800f).x,.01f);assertEquals(300f,focused.toPx(focused.center,1000f,800f).y,.01f)
        // Same scale; a tap maps back to the same place.
        assertEquals(middle.scale,focused.scale,0.0)
        val tap=Offset(640f,210f);val back=focused.toPx(focused.toMap(tap,1000f,800f),1000f,800f)
        assertEquals(tap.x,back.x,.01f);assertEquals(tap.y,back.y,.01f)
        // The tiles still cover the whole map although the place stands higher.
        val tiles=focused.tiles(1000f,800f);val t=focused.tilePixels
        val bottom=tiles.maxOf {(it.y+1)*t-focused.center.y*focused.scale+300.0}
        assertTrue(bottom>=800.0)
    }

    private fun clock(key:String,placement:TimelineClockPlacement,top:Float)=
        PlacedClock(TimelineClockLabel(key,0,0L,top,placement=placement),top)

    @Test fun clockChangesAtRestFadeTogetherWithoutABlankFrame() {
        val before=listOf(clock("ride-start-1",TimelineClockPlacement.ABOVE_BAR,-20f),clock("walk-start",TimelineClockPlacement.WALK_START,-44f))
        val after=listOf(clock("ride-start-1",TimelineClockPlacement.INSIDE,2f),clock("walk-start",TimelineClockPlacement.WALK_START,-40f),
            clock("ride-end-1",TimelineClockPlacement.INSIDE,30f))
        val morph=ClockMorph(before)
        // At rest everything is fully there.
        before.forEach {assertEquals(1f,morph.alpha(it,false),0f)}
        morph.begin(after)
        // The very first frame of a change: what stays keeps full opacity, nothing blinks away.
        assertTrue(morph.morphing);assertEquals(0f,morph.progress,0f)
        assertEquals(1f,morph.alpha(after[1],false),0f)
        assertEquals(1f,morph.alpha(before[0],true),0f)
        assertEquals(0f,morph.alpha(after[0],false),0f);assertEquals(0f,morph.alpha(after[2],false),0f)
        // Halfway the former chip and the new inside time cross-fade.
        morph.progress=.5f
        assertEquals(.5f,morph.alpha(before[0],true),0f);assertEquals(.5f,morph.alpha(after[0],false),0f);assertEquals(1f,morph.alpha(after[1],false),0f)
        morph.settle(after)
        assertFalse(morph.morphing);assertTrue(morph.previous.isEmpty())
        after.forEach {assertEquals(1f,morph.alpha(it,false),0f)}
    }

    @Test fun theChartFollowsWithoutOvershootSoTimesDoNotWobble() {
        val p=ChartProjection();val from=ChartFrame(0f,20f,4f);val to=ChartFrame(30f,24f,6f)
        p.snapTo(from)
        var lastScale=p.scale;var lastOrigin=p.originMinutes;var t=0f
        while(t<1.5f) {
            p.step(to,1/120f);t+=1/120f
            // Critically damped: always towards the target, never past it.
            assertTrue(p.scale>=lastScale-1e-4f && p.scale<=to.scale+1e-4f)
            assertTrue(p.originMinutes>=lastOrigin-1e-3f && p.originMinutes<=to.originMinutes+1e-3f)
            lastScale=p.scale;lastOrigin=p.originMinutes
        }
        assertTrue(p.converged(to))
    }
}
