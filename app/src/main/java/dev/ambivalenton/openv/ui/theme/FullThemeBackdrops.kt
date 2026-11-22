// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.theme

import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.ThemeTexture

sealed interface FullThemeBackdrop
data class CircuitBackdrop(val chips:List<Rect>,val traces:List<Path>,val vias:List<Offset>,
    val components:List<Rect>):FullThemeBackdrop
data class NetworkBackdrop(val routes:List<List<Offset>>,val paths:List<Path>,val interchanges:Set<Offset>,
    val stations:List<Offset>):FullThemeBackdrop

private fun polyline(points:List<Offset>)=Path().apply {
    points.forEachIndexed {i,p->if(i==0) moveTo(p.x,p.y) else lineTo(p.x,p.y)}
}

fun buildFullThemeBackdrop(texture:ThemeTexture?,size:Size):FullThemeBackdrop?=when(texture) {
    ThemeTexture.CIRCUITS-> {
        val w=size.width;val h=size.height
        val chips=listOf(Rect(w*.14f,h*.15f,w*.43f,h*.28f),Rect(w*.59f,h*.42f,w*.84f,h*.58f),
            Rect(w*.16f,h*.73f,w*.39f,h*.85f))
        val vias=mutableListOf<Offset>();val paths=mutableListOf<Path>()
        chips.forEachIndexed {chipIndex,chip->
            repeat(6) {pin->
                val y=chip.top+chip.height*(pin+1)/7
                val side=if((pin+chipIndex)%2==0) chip.left else chip.right
                val endpoint=Offset(if(side==chip.left) w*(.025f+pin*.012f) else w*(.94f-pin*.009f),
                    (y+h*(if(pin%2==0) -.07f else .08f)).coerceIn(h*.04f,h*.96f))
                val elbow=Offset((side+endpoint.x)/2,y)
                paths+=polyline(listOf(Offset(side,y),elbow,Offset(elbow.x,endpoint.y),endpoint));vias+=endpoint
            }
        }
        // Trunks join the three functional blocks instead of restarting a small drawing in each tile.
        val bus=listOf(Offset(w*.45f,h*.06f),Offset(w*.51f,h*.12f),Offset(w*.51f,h*.37f),
            Offset(w*.46f,h*.42f),Offset(w*.46f,h*.82f),Offset(w*.55f,h*.91f),Offset(w*.94f,h*.91f))
        paths+=polyline(bus);vias+=bus.first();vias+=bus.last()
        paths+=polyline(listOf(Offset(chips[0].center.x,chips[0].bottom),Offset(chips[0].center.x,h*.35f),
            Offset(chips[1].center.x,h*.35f),Offset(chips[1].center.x,chips[1].top)))
        paths+=polyline(listOf(Offset(chips[1].center.x,chips[1].bottom),Offset(chips[1].center.x,h*.66f),
            Offset(chips[2].center.x,h*.66f),Offset(chips[2].center.x,chips[2].top)))
        CircuitBackdrop(chips,paths,vias,listOf(Rect(w*.68f,h*.12f,w*.76f,h*.23f),
            Rect(w*.08f,h*.45f,w*.16f,h*.54f),Rect(w*.69f,h*.75f,w*.8f,h*.79f)))
    }
    ThemeTexture.ROUTES-> {
        fun p(x:Int,y:Int)=Offset(size.width*x/12f,size.height*y/22f)
        val routes=listOf(
            listOf(p(0,3),p(2,3),p(3,4),p(3,9),p(6,12),p(10,12),p(12,14),p(12,22)),
            listOf(p(0,19),p(2,19),p(5,16),p(5,13),p(6,12),p(8,10),p(8,3),p(11,0)),
            listOf(p(0,9),p(3,9),p(4,8),p(8,8),p(10,6),p(12,6)),
            listOf(p(0,22),p(1,22),p(5,18),p(5,16),p(9,16),p(12,19)),
            listOf(p(1,0),p(1,2),p(3,4),p(7,4),p(8,3),p(12,3)))
        val interchanges=setOf(p(3,4),p(3,9),p(6,12),p(8,3),p(5,16))
        val stations=routes.flatMap {route->route.zipWithNext().flatMap {(a,b)->
            val count=((b-a).getDistance()/(size.minDimension/9).coerceAtLeast(1f)).toInt().coerceIn(1,6)
            (0 until count).map {i->a+(b-a)*(i.toFloat()/count)}
        }}.distinct().filter {candidate->interchanges.none {(it-candidate).getDistance()<size.minDimension*.035f}}
        NetworkBackdrop(routes,routes.map(::polyline),interchanges,stations)
    }
    else->null
}

fun DrawScope.drawCircuitBackdrop(scene:CircuitBackdrop,color:Color) {
    val copper=color.copy(alpha=.18f);val silk=color.copy(alpha=.11f);val thin=1.dp.toPx()
    scene.traces.forEach {drawPath(it,copper,style=Stroke(thin,cap=StrokeCap.Square,join=StrokeJoin.Miter))}
    scene.vias.forEach {drawCircle(copper,2.3.dp.toPx(),it,style=Stroke(thin))}
    scene.chips.forEach {chip->
        drawRoundRect(color.copy(alpha=.065f),chip.topLeft,chip.size,CornerRadius(2.dp.toPx()))
        drawRect(silk,chip.topLeft,chip.size,style=Stroke(thin))
        drawCircle(silk,2.dp.toPx(),chip.topLeft+Offset(7.dp.toPx(),7.dp.toPx()))
        repeat(6) {i->
            val y=chip.top+chip.height*(i+1)/7
            drawLine(copper,Offset(chip.left-4.dp.toPx(),y),Offset(chip.left,y),2.dp.toPx())
            drawLine(copper,Offset(chip.right,y),Offset(chip.right+4.dp.toPx(),y),2.dp.toPx())
        }
    }
    scene.components.forEach {r->
        drawRect(silk,r.topLeft,r.size,style=Stroke(thin))
        drawLine(copper,Offset(r.left,r.center.y),Offset(r.right,r.center.y),thin)
    }
}

fun DrawScope.drawNetworkBackdrop(scene:NetworkBackdrop,color:Color,background:Color) {
    val colors=listOf(color,Color(0xFF71C7C6),Color(0xFFD89DBD),Color(0xFF98ADEA),Color(0xFFA3D08A))
    scene.paths.forEachIndexed {i,path->drawPath(path,colors[i].copy(alpha=.17f),
        style=Stroke(2.1.dp.toPx(),cap=StrokeCap.Round,join=StrokeJoin.Round))}
    scene.stations.forEach {p->drawCircle(color.copy(alpha=.2f),2.2.dp.toPx(),p,style=Stroke(1.dp.toPx()))}
    scene.interchanges.forEach {p->
        drawCircle(background,5.dp.toPx(),p)
        drawCircle(color.copy(alpha=.28f),5.dp.toPx(),p,style=Stroke(1.5.dp.toPx()))
        drawCircle(color.copy(alpha=.2f),2.4.dp.toPx(),p,style=Stroke(1.dp.toPx()))
    }
}
