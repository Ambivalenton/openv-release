// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.theme

import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.ThemeTexture
import kotlin.math.*

/** Static, deterministic decoration. Tiles are bounded; no bitmap, timer or shader loop. */
fun DrawScope.drawDecorativeThemeTexture(texture:ThemeTexture,color:Color,
    cachedBackdrop:FullThemeBackdrop?=null,background:Color=Color.Transparent) {
    if (texture.name.startsWith("CATALOG_")) {
        drawIntoCanvas { dev.ambivalenton.openv.rendering.drawCatalogThemePattern(
            it.nativeCanvas, texture, size.width, size.height, color.toArgb()) }
        return
    }
    if(texture.name.startsWith("OS_")){drawIntoCanvas {dev.ambivalenton.openv.rendering.drawOsThemePattern(it.nativeCanvas,texture,size.width,size.height,color.toArgb())};return}
    if(texture in dev.ambivalenton.openv.data.model.eraTextures) {
        drawIntoCanvas {dev.ambivalenton.openv.rendering.drawEraPattern(it.nativeCanvas,texture,size.width,size.height,color.toArgb(),.09f)}
        return
    }
    val ink=color.copy(alpha=.09f)
    val soft=color.copy(alpha=.045f)
    val thin=1.dp.toPx()
    fun tiles(stepDp:Float=64f,draw:(Float,Float,Int)->Unit) {
        val step=stepDp.dp.toPx()
        val columns=ceil(size.width/step).toInt().coerceIn(1,32)
        val rows=ceil(size.height/step).toInt().coerceIn(1,48)
        repeat(min(columns*rows,384)) {i->draw(i%columns*step,i/columns*step,i)}
    }
    when(texture) {
        ThemeTexture.AURORA->{
            repeat(4) {i->
                val x=size.width*(-.3f+i*.3f)
                val ribbon=Path().apply {
                    moveTo(x,-size.height*.1f)
                    cubicTo(x+size.width*.85f,size.height*.25f,x-size.width*.25f,size.height*.62f,x+size.width*.6f,size.height*1.1f)
                    lineTo(x+size.width*.86f,size.height*1.1f)
                    cubicTo(x-size.width*.04f,size.height*.62f,x+size.width*1.1f,size.height*.25f,x+size.width*.22f,-size.height*.1f);close()
                }
                drawPath(ribbon,Brush.linearGradient(listOf(color.copy(alpha=.015f),color.copy(alpha=.11f),Color(0xFFAC91EE).copy(alpha=.08f))))
            }
        }
        ThemeTexture.PRISM->{
            val center=Offset(size.width*.47f,size.height*.46f)
            val corners=listOf(Offset(-size.width*.1f,0f),Offset(size.width*.7f,-size.height*.1f),Offset(size.width*1.1f,size.height*.3f),
                Offset(size.width*.9f,size.height*1.1f),Offset(-size.width*.1f,size.height*.9f))
            corners.forEachIndexed {i,p->val q=corners[(i+1)%corners.size]
                val facet=Path().apply {moveTo(center.x,center.y);lineTo(p.x,p.y);lineTo(q.x,q.y);close()}
                drawPath(facet,if(i%2==0)soft else ink);drawLine(Color.White.copy(alpha=.22f),center,p,thin)
            }
        }
        ThemeTexture.TOYBOX->{
            repeat(12) {i->
                val p=Offset(size.width*((i*37%97)/100f),size.height*((i*23%101)/100f))
                val r=(14+i%4*5).dp.toPx()
                rotate((i*31%70-35).toFloat(),p) {
                    when(i%4) {
                        0->drawRoundRect(ink,p,Size(r*1.7f,r),CornerRadius(r*.25f))
                        1->drawCircle(soft,r,p,style=Stroke(3.dp.toPx()))
                        2->drawPath(Path().apply {moveTo(p.x,p.y-r);lineTo(p.x+r,p.y+r);lineTo(p.x-r,p.y+r);close()},ink)
                        else->{drawLine(ink,p-Offset(r,0f),p+Offset(r,0f),r*.4f,StrokeCap.Round);drawLine(ink,p-Offset(0f,r),p+Offset(0f,r),r*.4f,StrokeCap.Round)}
                    }
                }
            }
        }
        ThemeTexture.AQUATIC->{
            repeat(5) {i->val y=size.height*(.15f+i*.19f)
                val wave=Path().apply {moveTo(-size.width*.1f,y);cubicTo(size.width*.2f,y-size.height*.07f,size.width*.7f,y+size.height*.08f,size.width*1.1f,y)}
                drawPath(wave,soft,style=Stroke(8.dp.toPx(),cap=StrokeCap.Round))
                val p=Offset(size.width*(.2f+(i%3)*.25f),y+28.dp.toPx());val r=10.dp.toPx()
                drawOval(ink,p,Size(r*2,r));drawPath(Path().apply {moveTo(p.x+r*2,p.y+r*.5f);lineTo(p.x+r*2.7f,p.y-r*.1f);lineTo(p.x+r*2.7f,p.y+r*1.1f);close()},ink)
                drawCircle(ink,3.dp.toPx(),p-Offset(r,r));drawCircle(soft,5.dp.toPx(),p-Offset(r*.3f,r*2.1f))
            }
        }
        ThemeTexture.ORBITS->{
            val center=Offset(size.width*.52f,size.height*.48f)
            repeat(5) {i->rotate(16f+i*13f,center) {
                val w=size.width*(.55f+i*.24f);val h=size.height*(.12f+i*.08f)
                drawOval(ink,center-Offset(w/2,h/2),Size(w,h),style=Stroke(thin))
                drawCircle(if(i%2==0)ink else soft,(5+i*2).dp.toPx(),center+Offset(w*.42f,h*.23f))
            }}
            drawCircle(soft,size.width*.13f,center)
        }
        ThemeTexture.HALFTONE->tiles(14f) {x,y,i->drawCircle(ink,if(i%3==0) 1.8.dp.toPx() else .8.dp.toPx(),Offset(x+7.dp.toPx(),y+7.dp.toPx()))}
        ThemeTexture.COLLAGE->{drawOval(soft,Offset(-size.width*.25f,-size.height*.12f),Size(size.width*.95f,size.height*.5f));
            drawOval(ink,Offset(size.width*.7f,size.height*.5f),Size(size.width*.65f,size.height*.7f))}
        ThemeTexture.BLUEPRINT->{
            val step=40.dp.toPx()
            repeat((size.width/step).toInt().coerceAtMost(32)+1) {i->drawLine(soft,Offset(i*step,0f),Offset(i*step,size.height),thin)}
            repeat((size.height/step).toInt().coerceAtMost(48)+1) {i->drawLine(soft,Offset(0f,i*step),Offset(size.width,i*step),thin)}
            tiles(40f) {x,y,_->val d=3.dp.toPx();drawLine(ink,Offset(x-d,y),Offset(x+d,y),thin);drawLine(ink,Offset(x,y-d),Offset(x,y+d),thin)}
        }
        ThemeTexture.LEAVES->tiles(84f) {x,y,i->rotate(if(i%2==0) -28f else 32f,Offset(x+20.dp.toPx(),y+22.dp.toPx())) {
            drawOval(soft,Offset(x+8.dp.toPx(),y+8.dp.toPx()),Size(16.dp.toPx(),30.dp.toPx()))
            drawLine(ink,Offset(x+16.dp.toPx(),y+9.dp.toPx()),Offset(x+16.dp.toPx(),y+43.dp.toPx()),thin)}}
        ThemeTexture.DUNES,ThemeTexture.WAVES-> {
            val step=(if(texture==ThemeTexture.DUNES) 72 else 44).dp.toPx()
            repeat((size.height/step).toInt().coerceAtMost(48)+2) {i->
                val y=i*step;val path=Path().apply {moveTo(-30f,y);cubicTo(size.width*.28f,y-step*.55f,size.width*.65f,y+step*.55f,size.width+30f,y)}
                drawPath(path,if(i%2==0) ink else soft,style=Stroke(if(texture==ThemeTexture.DUNES) step*.16f else thin))}
        }
        ThemeTexture.STARS->tiles(72f) {x,y,i->
            val p=Offset(x+(12+i%3*11).dp.toPx(),y+(12+i%4*9).dp.toPx())
            drawCircle(ink,1.2.dp.toPx(),p)
            if(i%4==0){val d=4.dp.toPx();drawLine(ink,p-Offset(d,0f),p+Offset(d,0f),thin);drawLine(ink,p-Offset(0f,d),p+Offset(0f,d),thin)}}
        ThemeTexture.POSTCARD->tiles(60f) {x,y,i->
            if(i%2==0) drawLine(ink,Offset(x,y),Offset(x+28.dp.toPx(),y+8.dp.toPx()),5.dp.toPx())}
        ThemeTexture.CITRUS->{for(i in 0..3) {
            val r=size.width*(.24f+i*.04f);val p=Offset(if(i%2==0) -r*.35f else size.width+r*.2f,size.height*(i+.3f)/4f)
            drawCircle(soft,r,p);drawCircle(ink,r*.8f,p,style=Stroke(thin))}}
        ThemeTexture.CHERRIES->tiles(88f) {x,y,_->
            val p=Offset(x+18.dp.toPx(),y+30.dp.toPx());val r=4.dp.toPx()
            drawCircle(ink,r,p);drawCircle(ink,r,p+Offset(10.dp.toPx(),2.dp.toPx()))
            drawLine(ink,p,p+Offset(7.dp.toPx(),-13.dp.toPx()),thin);drawLine(ink,p+Offset(10.dp.toPx(),2.dp.toPx()),p+Offset(7.dp.toPx(),-13.dp.toPx()),thin)}
        ThemeTexture.DECO->tiles(80f) {x,y,_->for(i in 1..3) {
            val r=i*14.dp.toPx();drawArc(ink,190f,160f,false,Offset(x-r,y-r),Size(r*2,r*2),style=Stroke(thin))}}
        ThemeTexture.BLOCKS->tiles(112f) {x,y,i->drawRect(if(i%2==0) soft else ink,Offset(x+10.dp.toPx(),y+12.dp.toPx()),Size(27.dp.toPx(),8.dp.toPx()))}
        ThemeTexture.MEMPHIS->tiles(78f) {x,y,i->
            val p=Offset(x+18.dp.toPx(),y+18.dp.toPx());val d=7.dp.toPx()
            when(i%3){0->drawCircle(ink,d,p,style=Stroke(thin*2));1->drawPath(Path().apply {moveTo(p.x-d,p.y+d);lineTo(p.x+d,p.y+d);lineTo(p.x,p.y-d);close()},soft)
                else->drawLine(ink,p-Offset(d,d),p+Offset(d,d),thin*2,StrokeCap.Round)}}
        ThemeTexture.CLOUDS->tiles(128f) {x,y,_->
            drawRoundRect(soft,Offset(x-20.dp.toPx(),y+26.dp.toPx()),Size(78.dp.toPx(),23.dp.toPx()),CornerRadius(18.dp.toPx()))
            drawCircle(soft,18.dp.toPx(),Offset(x+12.dp.toPx(),y+24.dp.toPx()));drawCircle(soft,13.dp.toPx(),Offset(x+35.dp.toPx(),y+28.dp.toPx()))}
        ThemeTexture.PIXELS->tiles(18f) {x,y,i->if(i%4==0) drawRect(soft,Offset(x,y),Size(5.dp.toPx(),5.dp.toPx()))}
        ThemeTexture.SCANLINES->{val step=5.dp.toPx();repeat((size.height/step).toInt().coerceAtMost(512)) {i->drawLine(soft,Offset(0f,i*step),Offset(size.width,i*step),1f)}}
        ThemeTexture.KRAFT->tiles(24f) {x,y,i->drawLine(soft,Offset(x,y),Offset(x+(3+i%4).dp.toPx(),y+2.dp.toPx()),1f)}
        ThemeTexture.TILES->tiles(60f) {x,y,_->
            val d=26.dp.toPx();drawRoundRect(soft,Offset(x+3.dp.toPx(),y+3.dp.toPx()),Size(d*2,d*2),CornerRadius(4.dp.toPx()),style=Stroke(thin))
            drawCircle(ink,2.dp.toPx(),Offset(x+30.dp.toPx(),y+30.dp.toPx()))}
        ThemeTexture.CIRCUITS->drawCircuitBackdrop((cachedBackdrop ?: buildFullThemeBackdrop(texture,size)) as CircuitBackdrop,color)
        ThemeTexture.CONTOURS-> {
            val p=Offset(size.width*.8f,size.height*.4f)
            for(i in 1..12) drawOval(if(i%3==0) ink else soft,p-Offset(i*22.dp.toPx(),i*29.dp.toPx()),Size(i*44.dp.toPx(),i*58.dp.toPx()),style=Stroke(thin))}
        ThemeTexture.ROUTES->drawNetworkBackdrop((cachedBackdrop ?: buildFullThemeBackdrop(texture,size)) as NetworkBackdrop,color,background)
        ThemeTexture.INK-> {
            for(i in 0..4) drawArc(soft,25f,150f,false,Offset(-size.width*.5f,size.height*(i-1)/4f),Size(size.width*1.4f,size.height*.45f),style=Stroke((5+i*3).dp.toPx(),cap=StrokeCap.Round))}
        ThemeTexture.WATERCOLOR->repeat(4) {i->
            val center=Offset(size.width*(if(i%2==0) .12f else .88f),size.height*(.08f+i*.26f))
            val radius=size.width*(.6f+i*.04f)
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha=.10f),color.copy(alpha=.025f),Color.Transparent),center,radius),radius,center)
        }
        ThemeTexture.NEWSPRINT-> {
            val gutter=12.dp.toPx();val column=(size.width-gutter*4)/3
            repeat(3) {c->
                val x=gutter+c*(column+gutter)
                drawLine(ink,Offset(x,0f),Offset(x,size.height),thin*.6f)
                val step=13.dp.toPx()
                repeat((size.height/step).toInt().coerceAtMost(120)) {r->
                    val end=x+column*(.5f+((r*7+c*3)%11)/22f)
                    drawLine(soft,Offset(x+4.dp.toPx(),r*step),Offset(end,r*step),if(r%15==0) 3.dp.toPx() else thin)
                }
            }
        }
        ThemeTexture.FEATHERS->repeat(5) {i->
            val center=Offset(size.width*(if(i%2==0) .18f else .83f),size.height*(.09f+i*.21f))
            rotate(if(i%2==0) -28f else 25f,center) {
                drawOval(soft,center-Offset(size.width*.22f,size.width*.34f),Size(size.width*.44f,size.width*.68f))
                drawOval(ink,center-Offset(size.width*.12f,size.width*.2f),Size(size.width*.24f,size.width*.4f),style=Stroke(thin))
                drawLine(ink,center,center+Offset(0f,size.width*.5f),thin)
            }
        }
        ThemeTexture.MOSAIC-> {
            val dx=size.width/5;val dy=size.height/8
            repeat(8) {r->repeat(5) {c->
                val skew=((c*3+r*5)%7-3)*dx*.035f
                val path=Path().apply {moveTo(c*dx+3,r*dy+skew);lineTo((c+1)*dx-4,r*dy-skew)
                    lineTo((c+1)*dx-3,(r+1)*dy+skew-4);lineTo(c*dx+5,(r+1)*dy-skew-3);close()}
                drawPath(path,if((r+c)%3==0) ink else soft,style=Stroke(thin))
            }}
        }
        ThemeTexture.CITYLINE-> {
            val dx=size.width/13
            repeat(13) {i->
                val height=size.height*(.08f+((i*7)%11)*.018f)
                val top=size.height-height
                drawRect(soft,Offset(i*dx,top),Size(dx-2.dp.toPx(),height))
                for(row in 1..4) for(column in 0..1) if((row+column+i)%3!=0)
                    drawRect(ink,Offset(i*dx+dx*(.2f+column*.4f),top+height*row/6),Size(2.dp.toPx(),3.dp.toPx()))
            }
        }
        ThemeTexture.ORIGAMI-> {
            val points=listOf(listOf(Offset(-.1f,.1f),Offset(.85f,.27f),Offset(.26f,.58f)),
                listOf(Offset(.45f,.39f),Offset(1.1f,.51f),Offset(.9f,.9f)),
                listOf(Offset(-.1f,.72f),Offset(.65f,.83f),Offset(.18f,1.1f)))
            points.forEach {triangle->val p=triangle.map {Offset(it.x*size.width,it.y*size.height)}
                drawPath(Path().apply {moveTo(p[0].x,p[0].y);lineTo(p[1].x,p[1].y);lineTo(p[2].x,p[2].y);close()},soft)
                drawLine(ink,p[0],(p[1]+p[2])/2f,thin)
            }
        }
        ThemeTexture.VELVET->repeat(9) {i->
            val x=size.width*i/8
            val path=Path().apply {moveTo(x,-20f);cubicTo(x-size.width*.28f,size.height*.32f,x+size.width*.28f,size.height*.68f,x,size.height+20f)}
            drawPath(path,if(i%2==0) soft else ink,style=Stroke(if(i%2==0) 14.dp.toPx() else thin))
        }
        ThemeTexture.BUBBLES->repeat(7) {i->
            val p=Offset(size.width*((i*47+8)%97)/97,size.height*((i*31+9)%101)/101)
            val r=size.width*(.11f+(i%3)*.035f)
            drawCircle(soft,r,p);drawCircle(ink,r*.8f,p,style=Stroke(thin))
        }
        else->Unit
    }
}
