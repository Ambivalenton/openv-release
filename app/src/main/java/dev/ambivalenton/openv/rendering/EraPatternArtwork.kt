// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.rendering

import android.graphics.*
import dev.ambivalenton.openv.data.model.ThemeTexture
import kotlin.math.*

/** Original static vector compositions, shared by app surfaces and board bitmaps. */
fun drawEraPattern(c:Canvas,t:ThemeTexture,w:Float,h:Float,color:Int,opacity:Float=.12f) {
    if(w<=0 || h<=0)return
    val p=Paint(Paint.ANTI_ALIAS_FLAG);val u=min(w,h).coerceAtLeast(1f)
    fun ink(a:Float=1f,stroke:Float=0f) {
        p.color=color;p.alpha=(255*opacity*a).toInt().coerceIn(0,255);p.style=if(stroke>0)Paint.Style.STROKE else Paint.Style.FILL
        p.strokeWidth=(u*stroke).coerceAtLeast(.6f);p.strokeCap=Paint.Cap.ROUND;p.shader=null
    }
    fun line(x:Float,y:Float,xx:Float,yy:Float,a:Float=1f,stroke:Float=.004f){ink(a,stroke);c.drawLine(x*w,y*h,xx*w,yy*h,p)}
    fun oval(x:Float,y:Float,rx:Float,ry:Float,a:Float=1f,stroke:Float=0f){ink(a,stroke);c.drawOval((x-rx)*w,(y-ry)*h,(x+rx)*w,(y+ry)*h,p)}
    fun rect(x:Float,y:Float,ww:Float,hh:Float,a:Float=1f,stroke:Float=0f,radius:Float=0f){ink(a,stroke);c.drawRoundRect(x*w,y*h,(x+ww)*w,(y+hh)*h,u*radius,u*radius,p)}
    fun path(a:Float=1f,stroke:Float=0f,body:Path.()->Unit){ink(a,stroke);c.drawPath(Path().apply(body),p)}
    when(t) {
        ThemeTexture.KITES->{repeat(4){i->val x=.18f+i*.24f;val y=.13f+(i%2)*.33f
            path(.8f){moveTo(x*w,(y-.05f)*h);lineTo((x+.07f)*w,y*h);lineTo(x*w,(y+.09f)*h);lineTo((x-.05f)*w,y*h);close()}
            path(.7f,.003f){moveTo(x*w,(y+.09f)*h);cubicTo((x+.12f)*w,(y+.16f)*h,(x-.10f)*w,(y+.25f)*h,(x+.04f)*w,(y+.4f)*h)}
        }}
        ThemeTexture.FIREFLIES->{repeat(11){i->val x=(i*37%101)/100f;val y=.16f+(i*23%77)/100f
            oval(x,y,.008f,.004f,.7f);oval(x,y,.02f,.01f,.22f);line(x,1f,x+.05f,.65f+i%3*.05f,.35f,.002f)
        }}
        ThemeTexture.CONFETTI->{repeat(30){i->val x=(i*37%103)/100f;val y=(i*29%101)/100f
            if(i%3==0)oval(x,y,.025f,.012f,.7f,.006f) else if(i%3==1)line(x,y,x+.035f,y+.018f,.7f,.012f)
            else path(.5f){moveTo(x*w,y*h);lineTo((x+.035f)*w,(y+.035f)*h);lineTo((x-.02f)*w,(y+.035f)*h);close()}
        }}
        ThemeTexture.RIBBONS->{repeat(4){i->val y=(i+.25f)/4
            path(.65f,.045f){moveTo(-.1f*w,y*h);cubicTo(.75f*w,(y-.26f)*h,.9f*w,(y+.25f)*h,.3f*w,(y+.17f)*h);cubicTo(-.05f*w,(y+.13f)*h,.5f*w,(y+.02f)*h,1.1f*w,(y+.27f)*h)}
        }}
        ThemeTexture.RENAISSANCE->{for(x in listOf(.06f,.5f,.94f)) {
            path(.8f,.005f){moveTo((x-.18f)*w,.95f*h);lineTo((x-.18f)*w,.29f*h);cubicTo((x-.18f)*w,.06f*h,(x+.18f)*w,.06f*h,(x+.18f)*w,.29f*h);lineTo((x+.18f)*w,.95f*h)}
            line(x-.2f,.3f,x+.2f,.3f,.8f);line(x-.2f,.91f,x+.2f,.91f,.8f)
        };line(.02f,.07f,.98f,.07f);line(.02f,.96f,.98f,.96f)}
        ThemeTexture.BAROQUE->{for(side in listOf(0f,1f))repeat(3){i->val y=.1f+i*.37f
            path(.8f,.004f){moveTo(side*w,y*h);cubicTo((side+if(side==0f).32f else -.32f)*w,(y-.13f)*h,(side+if(side==0f).35f else -.35f)*w,(y+.16f)*h,side*w,(y+.16f)*h)}
            oval(if(side==0f).12f else .88f,y+.04f,.045f,.024f,.55f,.003f)
        };rect(.035f,.025f,.93f,.95f,.8f,.003f,.025f)}
        ThemeTexture.ROMANTIC->{oval(.78f,.19f,.10f,.05f,.6f)
            repeat(3){i->path(.23f+i*.18f){moveTo(0f,h);lineTo(0f,(.65f+i*.08f)*h);lineTo(.3f*w,(.48f+i*.1f)*h);lineTo(.56f*w,(.69f+i*.06f)*h);lineTo(.84f*w,(.52f+i*.1f)*h);lineTo(w,.76f*h);lineTo(w,h);close()}}
        }
        ThemeTexture.IMPRESSION->{repeat(40){i->val x=(i*47%107)/100f;val y=(i*31%103)/100f
            oval(x,y,.025f+(i%3)*.016f,.006f+(i%4)*.004f,.25f+(i%4)*.16f)
        }}
        ThemeTexture.NOUVEAU->{for(side in listOf(.06f,.94f)) {
            path(.8f,.004f){moveTo(side*w,h);cubicTo((1-side)*w,.72f*h,(side-.17f)*w,.38f*h,side*w,0f)}
            repeat(5){i->val y=.1f+i*.19f;oval(side+(if(side<.5f).06f else -.06f),y,.065f,.022f,.45f,.002f)}
        }}
        ThemeTexture.EXPRESSION->{repeat(7){i->val x=-.2f+i*.22f
            path(if(i%2==0).65f else .3f){moveTo(x*w,h);lineTo((x+.25f)*w,.06f*h);lineTo((x+.34f)*w,.34f*h);lineTo((x+.03f)*w,h);close()}
        }}
        ThemeTexture.CONSTRUCT->{oval(.8f,.14f,.20f,.11f,.6f);path(.9f){moveTo(0f,.65f*h);lineTo(w,.28f*h);lineTo(w,.38f*h);lineTo(0f,.75f*h);close()}
            rect(.04f,.06f,.13f,.31f,.6f);line(.21f,.96f,.94f,.62f,.8f,.018f)
        }
        ThemeTexture.SURREAL->{oval(.74f,.3f,.11f,.06f,.55f);path(.35f){moveTo(.74f*w,.36f*h);lineTo(.98f*w,.76f*h);lineTo(.55f*w,.77f*h);close()}
            path(.85f,.008f){moveTo(.12f*w,.9f*h);lineTo(.12f*w,.49f*h);cubicTo(.12f*w,.28f*h,.48f*w,.28f*h,.48f*w,.49f*h);lineTo(.48f*w,.9f*h)}
            oval(.3f,.85f,.14f,.02f,.4f)
        }
        ThemeTexture.POP_ART->{repeat(9){y->repeat(7){x->oval(x/6f,y/8f,.014f,.009f,.6f)}}
            rect(.68f,.08f,.29f,.13f,.5f);path(.6f){moveTo(0f,.75f*h);lineTo(.32f*w,.59f*h);lineTo(.22f*w,.91f*h);close()}
        }
        ThemeTexture.RETRO_70->{repeat(5){i->ink(.22f+i*.12f,.018f);c.drawArc(RectF(-w*.65f,h*.05f,w*(1.1f-i*.12f),h*(1.45f-i*.11f)),205f,220f,false,p)}
            oval(.81f,.15f,.12f,.06f,.4f)
        }
        ThemeTexture.RETRO_80->{rect(.13f,.23f,.74f,.27f,.6f,.005f,.025f);rect(.2f,.27f,.6f,.13f,.45f,.003f,.012f)
            oval(.35f,.335f,.06f,.03f,.9f,.005f);oval(.65f,.335f,.06f,.03f,.9f,.005f);line(.36f,.38f,.64f,.38f)
            repeat(6){i->line(-.1f,.64f+i*.055f,1.1f,.5f+i*.055f,.4f,.003f)}
        }
        ThemeTexture.RETRO_90->{repeat(3){i->val x=.04f+i*.12f;val y=.12f+i*.22f
            rect(x,y,.62f,.25f,.35f);rect(x,y,.62f,.25f,.8f,.004f);rect(x+.014f,y+.013f,.59f,.035f,.55f)
            rect(x+.55f,y+.019f,.025f,.02f,.8f);line(x+.02f,y+.238f,x+.59f,y+.238f,.8f,.003f)
        }}
        ThemeTexture.RETRO_00->{repeat(3){i->val y=.18f+i*.3f
            ink(.7f,.03f);p.shader=LinearGradient(0f,y*h,w,(y+.13f)*h,intArrayOf(Color.TRANSPARENT,color and 0xffffff or(45 shl 24),Color.WHITE and 0xffffff or(38 shl 24)),null,Shader.TileMode.CLAMP)
            c.drawOval(-.2f*w,(y-.13f)*h,1.15f*w,(y+.13f)*h,p);p.shader=null
        }}
        ThemeTexture.RETRO_10->{path(.45f){moveTo(0f,0f);lineTo(w*.72f,0f);lineTo(0f,h*.55f);close()}
            path(.65f){moveTo(w,h*.42f);lineTo(w,h);lineTo(w*.21f,h);close()};oval(.72f,.22f,.17f,.09f,.3f)
        }
        else->Unit
    }
}
