// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import dev.ambivalenton.openv.data.model.AppTheme
import kotlin.math.*

/** Small static vectors, also used inside adaptive departure boards. No assets or animation loop. */
internal fun DrawScope.drawSeasonalThemeArtwork(theme:AppTheme,ink:Color):Boolean {
    // Redesigned seasons, festivals and rainbows speak a design language instead of a wallpaper.
    if(theme.language!=dev.ambivalenton.openv.data.model.DesignLanguage.MATERIAL)return false
    val stripes=when(theme) {
        AppTheme.RAINBOW_CLASSIC->listOf(0xFFE40303,0xFFFF8C00,0xFFFFED00,0xFF008026,0xFF2446AD,0xFF732982)
        AppTheme.RAINBOW_SKY->listOf(0xFF5BCEFA,0xFFF5A9B8,0xFFFFFFFF,0xFFF5A9B8,0xFF5BCEFA)
        AppTheme.RAINBOW_BERRY->listOf(0xFFD60270,0xFFD60270,0xFF9B4F96,0xFF0038A8,0xFF0038A8)
        AppTheme.RAINBOW_CITRUS->listOf(0xFFFF218C,0xFFFFD800,0xFF21B1FF)
        AppTheme.RAINBOW_MOON->listOf(0xFFFFF430,0xFFFFFFFF,0xFF9C59D1,0xFF2C2C2C)
        AppTheme.RAINBOW_SUNSET->listOf(0xFFD52D00,0xFFEF7627,0xFFFF9A56,0xFFFFFFFF,0xFFD162A4,0xFFB55690,0xFFA30262)
        AppTheme.RAINBOW_TIDAL->listOf(0xFF078D70,0xFF26CEAA,0xFF98E8C1,0xFFFFFFFF,0xFF7BADE2,0xFF5049CC,0xFF3D1A78)
        AppTheme.RAINBOW_AMETHYST->listOf(0xFF141414,0xFFA3A3A3,0xFFFFFFFF,0xFF800080)
        AppTheme.RAINBOW_FERN->listOf(0xFF3DA542,0xFFA7D379,0xFFFFFFFF,0xFFA9A9A9,0xFF242424)
        AppTheme.RAINBOW_FLUID->listOf(0xFFFF75A2,0xFFFFFFFF,0xFFBE18D6,0xFF202020,0xFF333EBD)
        AppTheme.RAINBOW_MEADOW->listOf(0xFF202020,0xFFB9B9B9,0xFFFFFFFF,0xFFB8F483,0xFFFFFFFF,0xFFB9B9B9,0xFF202020)
        AppTheme.RAINBOW_LILAC->listOf(0xFFB57EDC,0xFFFFFFFF,0xFF4A8123)
        else->emptyList()
    }
    if(stripes.isNotEmpty()) {
        val h=size.height/stripes.size
        stripes.forEachIndexed {i,c->
            val y=i*h
            val band=Path().apply {
                moveTo(0f,y);cubicTo(size.width*.3f,y+h*.35f,size.width*.7f,y-h*.35f,size.width,y)
                lineTo(size.width,y+h);cubicTo(size.width*.7f,y+h*.65f,size.width*.3f,y+h*1.35f,0f,y+h);close()
            }
            drawPath(band,Color(c).copy(alpha=.16f))
        }
        return true
    }
    if(theme==AppTheme.RAINBOW_SOLAR) {
        drawRect(Color(0xFFFFD800).copy(alpha=.12f))
        drawCircle(Color(0xFF7902AA).copy(alpha=.18f),size.minDimension*.28f,Offset(size.width*.5f,size.height*.44f),style=Stroke(size.minDimension*.04f))
        return true
    }
    if(!theme.name.startsWith("FESTIVAL_") && !theme.name.startsWith("VIVALDI_INVERNO"))return false
    val w=size.width;val h=size.height
    repeat(if(theme==AppTheme.FESTIVAL_HANUKKAH)8 else 12) {i->
        val p=Offset(w*((i*37%89+5)/100f),h*((i*29%91+4)/100f))
        val r=size.minDimension*(.018f+(i%3)*.006f)
        val c=if(theme==AppTheme.FESTIVAL_HOLI)listOf(Color(0xFFE92F86),Color(0xFFF2BC21),Color(0xFF20AAB0),Color(0xFF7849C8))[i%4].copy(alpha=.15f) else ink.copy(alpha=.10f)
        when(theme) {
            AppTheme.FESTIVAL_MUERTOS,AppTheme.FESTIVAL_VESAK,AppTheme.FESTIVAL_NOWRUZ->{
                repeat(7) {petal->val a=petal*2*PI/7
                    drawCircle(c,r*.65f,p+Offset(cos(a).toFloat()*r,sin(a).toFloat()*r))}
                drawCircle(c,r*.5f,p)
            }
            AppTheme.FESTIVAL_DIWALI,AppTheme.FESTIVAL_CHRISTMAS,AppTheme.FESTIVAL_HANUKKAH->{
                drawLine(c,p+Offset(0f,r*.2f),p+Offset(0f,r*2),r*.65f,StrokeCap.Round)
                drawOval(ink.copy(alpha=.15f),p-Offset(r*.3f,r),Size(r*.6f,r*1.1f))
            }
            AppTheme.FESTIVAL_LUNAR->{
                drawLine(c,Offset(p.x,0f),p,r*.08f)
                drawOval(c,p-Offset(r,r*.4f),Size(r*2,r*2.5f));drawLine(c,p+Offset(0f,r*2),p+Offset(0f,r*3),r*.15f)
            }
            AppTheme.FESTIVAL_EID->{drawArc(c,35f,280f,false,p-Offset(r,r),Size(r*2,r*2),style=Stroke(r*.4f))}
            AppTheme.FESTIVAL_HALLOWEEN->{
                drawOval(c,p-Offset(r,r*.6f),Size(r*2,r*1.7f));drawLine(c,p-Offset(0f,r*.5f),p-Offset(-r*.3f,r),r*.2f)
            }
            AppTheme.FESTIVAL_YULE->{
                drawLine(c,p-Offset(0f,r),p+Offset(0f,r*2),r*.12f)
                repeat(3){n->val y=p.y+n*r*.7f;drawLine(c,Offset(p.x,y),Offset(p.x-r,y+r*.8f),r*.12f);drawLine(c,Offset(p.x,y),Offset(p.x+r,y+r*.8f),r*.12f)}
            }
            AppTheme.VIVALDI_INVERNO,AppTheme.VIVALDI_INVERNO_II,AppTheme.VIVALDI_INVERNO_III->{repeat(3){ray->rotate(ray*60f,p){drawLine(c,p-Offset(r,0f),p+Offset(r,0f),r*.13f)}}}
            else->{drawCircle(c,r,p);drawCircle(c,r*1.7f,p,style=Stroke(r*.2f))}
        }
    }
    return true
}
