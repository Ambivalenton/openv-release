// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import androidx.compose.ui.geometry.Offset
import androidx.core.graphics.ColorUtils
import dev.ambivalenton.openv.color.utilities.Hct
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.network.*
import kotlin.math.abs
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Octilinear line layout, opponent line colours and the motion geometry. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class Build66LayoutTest {
    private fun angleOk(a:GridPoint,b:GridPoint):Boolean {
        val dx=abs(b.x-a.x);val dy=abs(b.y-a.y)
        return dx==0 || dy==0 || dx==dy
    }

    @Test fun everyRandomNetworkUsesOnly0_45_90DegreesAvoidsForeignNodesAndIsDeterministic() {
        val random=Random(66)
        repeat(40) {
            val points=mutableListOf<GridPoint>()
            while(points.size<8) {
                val p=GridPoint(random.nextInt(12),random.nextInt(12))
                if(p.free(points,NETWORK_GRID_SIZE,NETWORK_GRID_SIZE))points+=p
            }
            val placed=points.mapIndexed {i,p->"n$i" to p}.toMap()
            val edges=(0 until 10).map {i->NetworkEdge("e$i","n${random.nextInt(8)}","n${random.nextInt(8)}")}
                .filter {it.aId!=it.bId}.distinctBy {setOf(it.aId,it.bId)}
            val routes=routeNetwork(placed,edges)
            assertEquals(edges.map {it.id},routes.keys.toList())
            routes.forEach {(id,path)->
                val edge=edges.first {it.id==id}
                assertEquals(placed[edge.aId],path.first());assertEquals(placed[edge.bId],path.last())
                path.zipWithNext().forEach {(a,b)->assertTrue("segment $a→$b",angleOk(a,b))}
                val foreign=placed.filterKeys {it!=edge.aId && it!=edge.bId}.values.toSet()
                val full=path.zipWithNext().flatMap {(a,b)->
                    val sx=Integer.signum(b.x-a.x);val sy=Integer.signum(b.y-a.y)
                    generateSequence(a) {p->if(p==b)null else GridPoint(p.x+sx,p.y+sy)}.toList()
                }
                assertTrue("line $id passes through another node",full.none {it in foreign})
                // No sharp (≥ 135°) turns.
                path.zipWithNext().zipWithNext().forEach {(s1,s2)->
                    val d1=Offset((s1.second.x-s1.first.x).toFloat(),(s1.second.y-s1.first.y).toFloat())
                    val d2=Offset((s2.second.x-s2.first.x).toFloat(),(s2.second.y-s2.first.y).toFloat())
                    val cos=(d1.x*d2.x+d1.y*d2.y)/(d1.getDistance()*d2.getDistance())
                    assertTrue("turn in $id too sharp",cos>-.01f)
                }
            }
            assertEquals(routes,routeNetwork(placed,edges))
        }
    }

    @Test fun aDiagonalOffsetGetsExactlyOneSoftBend() {
        val path=routeNetwork(mapOf("a" to GridPoint(1,1),"b" to GridPoint(8,4)),listOf(NetworkEdge("e","a","b")))["e"]!!
        assertEquals(3,path.size)
        assertTrue(path.zipWithNext().all {(a,b)->angleOk(a,b)})
    }

    private fun hue(argb:Int)=Hct.fromInt(argb).hue
    private fun near(h:Double,target:Double,tolerance:Double)=abs(((h-target+540)%360)-180)<=tolerance

    @Test fun theLineTakesTheOpponentColourOfBothFavourites() {
        val azur=0xFF245EA8.toInt();val night=0xFF404B92.toInt()
        val ruby=0xFFAE344D.toInt();val coral=0xFFB64A43.toInt()
        // Two blue favourites → yellow (HCT yellow ≈ 100–115°).
        assertTrue(near(opponentLineHue(azur,night)!!,108.0,15.0))
        // Two red favourites → green (HCT green ≈ 140–170°).
        assertTrue(near(opponentLineHue(ruby,coral)!!,158.0,20.0))
        // Red + blue → between yellow and green.
        assertTrue(near(opponentLineHue(ruby,azur)!!,128.0,20.0))
        // Achromatic colours carry no hue: the theme colour is used.
        assertNull(opponentLineHue(0xFF808080.toInt(),0xFF202020.toInt()))
    }

    @Test fun lineColoursReachThreeToOneOnLightAndDarkBackgrounds() {
        val light=0xFFFFFBFE.toInt();val dark=0xFF141218.toInt()
        val pairs=listOf(0xFF245EA8 to 0xFF404B92,0xFFAE344D to 0xFFB64A43,0xFF087E62 to 0xFF79629B,0xFF896100 to 0xFF006A6A)
        pairs.forEach {(a,b)->
            listOf(light to false,dark to true).forEach {(bg,isDark)->
                val c=edgeLineColor(a.toInt(),b.toInt(),bg,0xFF6750A4.toInt(),isDark)
                assertTrue(ColorUtils.calculateContrast(c or (0xFF shl 24),bg)>=3.0)
            }
        }
        val blueOnLight=edgeLineColor(0xFF245EA8.toInt(),0xFF404B92.toInt(),light,0,false)
        assertTrue("yellow turns olive on a light background",Hct.fromInt(blueOnLight).tone<60)
    }

    @Test fun theRopeHangsBelowTheChordAndTheRippleStaysFinite() {
        val rope=RopeSimulation()
        val a=Offset(0f,0f);val b=Offset(300f,0f)
        rope.reset(a)
        repeat(240) {rope.step(1f/60f,a,b,2400f,30f)}
        val middle=rope.points[rope.points.size/2]
        assertTrue("sags under gravity",middle.y>5f)
        assertEquals(a,rope.points.first());assertEquals(b,rope.points.last())
        val samples=resample(listOf(Offset(0f,0f),Offset(100f,0f),Offset(100f,100f)),64)
        assertEquals(64,samples.size)
        listOf(0f,.3f,.7f,1f).forEach {t->
            val wave=rippleOffsets(samples,t,6f,20f,40f)
            assertTrue(wave.all {(o,glow)->o.x.isFinite() && o.y.isFinite() && glow in 0f..1f})
        }
        // At the end the line is back in place.
        rippleOffsets(samples,1f,6f,20f,40f).zip(samples).forEach {(w,s)->assertEquals(s.x,w.first.x,.01f);assertEquals(s.y,w.first.y,.01f)}
    }
}
