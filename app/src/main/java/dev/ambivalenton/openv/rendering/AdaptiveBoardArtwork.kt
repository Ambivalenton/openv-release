// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.rendering
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.theme.*
import kotlin.math.min
internal fun drawAdaptiveBoardBackdrop(canvas:Canvas,theme:AppTheme,width:Float,height:Float,color:Int,background:Int){
    CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,androidx.compose.ui.graphics.Canvas(canvas),Size(width,height)){
        if(!drawSeasonalThemeArtwork(theme,Color(color)))drawDecorativeThemeTexture(theme.texture,Color(color),buildFullThemeBackdrop(theme.texture,size),Color(background))
    }
}
internal fun adaptiveBoardOutline(w:Float,h:Float,t:AppTheme,cornerScale:Float):Path {
    val r=(24f*t.radiusScale*cornerScale).coerceIn(0f,min(w,h)/2)
    return Path().apply {when(t.shapeStyle){
        ThemeShape.ROUNDED->addRoundRect(RectF(0f,0f,w,h),r,r,Path.Direction.CW)
        ThemeShape.LEAF->addRoundRect(RectF(0f,0f,w,h),floatArrayOf(r*1.5f,r*1.5f,r*.22f,r*.22f,r*1.5f,r*1.5f,r*.22f,r*.22f),Path.Direction.CW)
        ThemeShape.SHARP->addRect(RectF(0f,0f,w,h),Path.Direction.CW)
        ThemeShape.PIXEL->{val s=(r/2f).coerceAtLeast(2f)
            moveTo(0f,2*s);lineTo(s,2*s);lineTo(s,s);lineTo(2*s,s);lineTo(2*s,0f);lineTo(w-2*s,0f);lineTo(w-2*s,s);lineTo(w-s,s);lineTo(w-s,2*s);lineTo(w,2*s)
            lineTo(w,h-2*s);lineTo(w-s,h-2*s);lineTo(w-s,h-s);lineTo(w-2*s,h-s);lineTo(w-2*s,h);lineTo(2*s,h);lineTo(2*s,h-s);lineTo(s,h-s);lineTo(s,h-2*s);lineTo(0f,h-2*s);close()}
        ThemeShape.ASYMMETRIC->addRoundRect(RectF(0f,0f,w,h),floatArrayOf(r,r,r*.25f,r*.25f,r,r,r*.25f,r*.25f),Path.Direction.CW)
        ThemeShape.CUT,ThemeShape.BEVEL->{val cuts=if(t.shapeStyle==ThemeShape.CUT)listOf(r*.55f,r*.55f,r*.55f,r*.55f)else listOf(r*.2f,r*.7f,r*.2f,r*.7f)
            moveTo(cuts[0],0f);lineTo(w-cuts[1],0f);lineTo(w,cuts[1]);lineTo(w,h-cuts[2]);lineTo(w-cuts[2],h);lineTo(cuts[3],h);lineTo(0f,h-cuts[3]);lineTo(0f,cuts[0]);close()}
        ThemeShape.SQUIRCLE->addPath(squirclePath(Size(w,h),r,r,r,r).asAndroidPath())
        ThemeShape.PEBBLE->{val a=min(r*1.35f,min(w,h)/2);val b=min(r*.55f,min(w,h)/2);val c=min(r*1.2f,min(w,h)/2);val d=min(r*.7f,min(w,h)/2)
            addRoundRect(RectF(0f,0f,w,h),floatArrayOf(a,a,b,b,c,c,d,d),Path.Direction.CW)}
    }}
}
