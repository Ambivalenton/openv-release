// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.rendering
import android.graphics.*
import dev.ambivalenton.openv.data.model.ThemeTexture
import kotlin.math.min
/** Original vector interpretations. No manufacturer logos, wallpapers or proprietary typefaces. */
fun drawOsThemePattern(c:Canvas,t:ThemeTexture,w:Float,h:Float,color:Int,opacity:Float=.12f) {
    val p=Paint(Paint.ANTI_ALIAS_FLAG);val u=min(w,h)
    fun ink(a:Float=1f,line:Float=0f){p.shader=null;p.color=color;p.alpha=(255*opacity*a).toInt().coerceIn(0,255);p.strokeWidth=(u*line).coerceAtLeast(.6f);p.style=if(line>0)Paint.Style.STROKE else Paint.Style.FILL}
    fun rect(x:Float,y:Float,ww:Float,hh:Float,a:Float=1f,line:Float=0f,r:Float=0f){ink(a,line);c.drawRoundRect(x*w,y*h,(x+ww)*w,(y+hh)*h,r*u,r*u,p)}
    fun line(x:Float,y:Float,xx:Float,yy:Float,a:Float=1f){ink(a,.003f);c.drawLine(x*w,y*h,xx*w,yy*h,p)}
    fun oval(x:Float,y:Float,ww:Float,hh:Float,a:Float=1f){ink(a);c.drawOval(x*w,y*h,(x+ww)*w,(y+hh)*h,p)}
    fun path(a:Float=1f,body:Path.()->Unit){ink(a);c.drawPath(Path().apply(body),p)}
    fun shine(x:Float,y:Float,ww:Float,hh:Float,r:Float=.02f){ink();p.shader=LinearGradient(0f,y*h,0f,(y+hh)*h,intArrayOf(Color.WHITE,Color.TRANSPARENT,color),null,Shader.TileMode.CLAMP);c.drawRoundRect(x*w,y*h,(x+ww)*w,(y+hh)*h,r*u,r*u,p);p.shader=null}
    when(t){
        ThemeTexture.OS_XP->{path(.8f){moveTo(0f,h*.79f);cubicTo(w*.23f,h*.54f,w*.57f,h*.91f,w,h*.63f);lineTo(w,h);lineTo(0f,h);close()};path(.45f){moveTo(0f,h*.87f);cubicTo(w*.46f,h*.66f,w*.65f,h*.72f,w,h*.81f);lineTo(w,h);lineTo(0f,h);close()};oval(.12f,.13f,.31f,.045f,.3f);oval(.57f,.21f,.25f,.04f,.25f);shine(.04f,.035f,.92f,.038f)}
        ThemeTexture.OS_AERO->{repeat(3){i->path(.4f+i*.15f){val x=(-.3f+i*.34f)*w;moveTo(x,0f);lineTo(x+w*.14f,0f);lineTo(x+w*.94f,h);lineTo(x+w*.75f,h);close()}};rect(.035f,.035f,.93f,.93f,.7f,.003f,.025f);shine(.045f,.045f,.91f,.12f,.025f)}
        ThemeTexture.OS_TILES->{listOf(floatArrayOf(.06f,.1f,.55f,.18f),floatArrayOf(.65f,.1f,.28f,.18f),floatArrayOf(.06f,.32f,.28f,.24f),floatArrayOf(.38f,.32f,.55f,.24f),floatArrayOf(.06f,.6f,.55f,.29f),floatArrayOf(.65f,.6f,.28f,.29f)).forEachIndexed {i,r->rect(r[0],r[1],r[2],r[3],.18f+i*.07f)}}
        ThemeTexture.OS_IOS_GLASS->{shine(-.12f,.1f,.78f,.33f,.12f);shine(.42f,.48f,.7f,.25f,.12f);rect(.03f,.08f,.72f,.35f,.5f,.004f,.14f);oval(.73f,.12f,.22f,.11f,.25f)}
        ThemeTexture.OS_IOS_CLASSIC->{repeat(28){line(it/27f,0f,it/27f,1f,.12f)};repeat(42){line(0f,it/41f,1f,it/41f,.08f)};shine(0f,.02f,1f,.1f,0f);line(0f,.12f,1f,.12f,.8f);shine(0f,.9f,1f,.1f,0f)}
        ThemeTexture.OS_WIN_CLASSIC->{repeat(3){i->val x=.04f+i*.13f;val y=.12f+i*.22f;rect(x,y,.61f,.3f,.22f);rect(x,y,.61f,.3f,.9f,.004f);rect(x+.014f,y+.012f,.58f,.04f,.7f);line(x+.015f,y+.285f,x+.59f,y+.285f,.8f);rect(x+.51f,y+.019f,.025f,.022f,.7f)}}
        ThemeTexture.OS_MAC_CLASSIC->{repeat(3){i->val y=.12f+i*.27f;rect(.08f,y,.84f,.22f,.65f,.002f,.012f);repeat(4){j->line(.15f,y+.012f+j*.008f,.85f,y+.012f+j*.008f,.5f)};rect(.1f,y+.012f,.025f,.02f,.7f,.002f)}}
        ThemeTexture.OS_AQUA->{repeat(32){line(0f,it/31f,1f,it/31f,.17f)};shine(.1f,.12f,.8f,.085f,.05f);shine(.08f,.88f,.84f,.08f,.025f)}
        ThemeTexture.OS_MAC_SPACE->{path(.35f){moveTo(0f,h*.5f);cubicTo(w*.4f,h*.45f,w*.7f,h*.1f,w,h*.06f);lineTo(w,h*.16f);cubicTo(w*.7f,h*.23f,w*.4f,h*.55f,0f,h*.65f);close()};shine(.08f,.88f,.84f,.08f,.025f)}
        ThemeTexture.OS_WORKBENCH->{rect(.06f,.08f,.88f,.34f,.6f,.003f);rect(.06f,.08f,.88f,.045f,.8f);repeat(3){rect(.14f+it*.22f,.24f,.14f,.1f,.6f,.002f)}}
        ThemeTexture.OS_GEM->{repeat(22){i->line(i/21f,0f,i/21f,1f,.08f);line(0f,i/21f,1f,i/21f,.08f)};repeat(3){i->rect(.08f,.12f+i*.27f,.84f,.22f,.55f,.003f);rect(.1f,.135f+i*.27f,.8f,.04f,.6f)}}
        ThemeTexture.OS_BEOS->{repeat(3){i->val y=.12f+i*.27f;rect(.06f+i*.06f,y,.82f,.23f,.5f,.003f);rect(.06f+i*.06f,y-.035f,.35f,.04f,.9f,0f,.008f)}}
        ThemeTexture.OS_NEXT->{repeat(6){i->rect(.76f,.05f+i*.14f,.18f,.12f,.7f,.004f);shine(.77f,.06f+i*.14f,.16f,.1f,.004f)};rect(.07f,.12f,.62f,.64f,.5f,.003f);rect(.07f,.12f,.62f,.04f,.8f)}
        ThemeTexture.OS_WARP->{repeat(4){i->path(.3f){val x=(i*.3f-.4f)*w;moveTo(x,0f);lineTo(x+w*.15f,0f);lineTo(x+w*.85f,h);lineTo(x+w*.65f,h);close()}};shine(.08f,.08f,.84f,.08f,.008f)}
        ThemeTexture.OS_HOLO->{repeat(18){i->line(0f,i/17f,1f,i/17f,.08f)};line(.06f,.12f,.94f,.12f,.9f);line(.06f,.86f,.94f,.86f,.9f);repeat(3){rect(.15f+it*.25f,.93f,.15f,.005f,.9f)}}
        ThemeTexture.OS_UBUNTU->{oval(.06f,.28f,.88f,.65f,.4f);oval(.35f,.16f,.54f,.3f,.3f);rect(.06f,.05f,.88f,.04f,.55f,0f,.01f);repeat(3){oval(.09f+it*.035f,.063f,.02f,.01f,.9f)}}
        ThemeTexture.OS_ADWAITA->{rect(.06f,.1f,.88f,.73f,.45f,.002f,.03f);rect(.07f,.11f,.86f,.085f,.5f,0f,.025f);repeat(3){rect(.13f,.25f+it*.16f,.2f,.11f,.3f,0f,.018f)}}
        ThemeTexture.OS_CDE->{repeat(3){i->rect(.05f+i*.015f,.07f+i*.015f,.9f-i*.03f,.82f-i*.03f,.5f,.003f)};rect(.1f,.11f,.8f,.045f,.7f);repeat(5){rect(.13f+it*.15f,.92f,.12f,.035f,.6f,.002f)}}
        else->Unit
    }
}
