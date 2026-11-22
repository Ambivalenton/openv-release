// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components
import androidx.compose.ui.graphics.Path
import dev.ambivalenton.openv.data.model.FootprintStyle
import kotlin.math.*

/** Normalised, code-native silhouettes. Toes face the direction of travel; right prints mirror left. */
private class Sole(private val g:FootstepGeometry,private val aspect:Float=1f) {
    val path=Path()
    private fun point(u:Float,v:Float):Pair<Float,Float> {
        val x=((if(g.left) u else 1-u)-.5f)*FOOTPRINT_WIDTH_PX*g.scale
        val y=(.5f-v)*FOOTPRINT_LENGTH_PX*g.scale*aspect
        return g.x+x*cos(g.angle)+y*sin(g.angle) to g.y+y*cos(g.angle)-x*sin(g.angle)
    }
    fun move(x:Float,y:Float) {val p=point(x,y);path.moveTo(p.first,p.second)}
    fun line(x:Float,y:Float) {val p=point(x,y);path.lineTo(p.first,p.second)}
    fun curve(a:Float,b:Float,c:Float,d:Float,e:Float,f:Float) {
        val p=point(a,b);val q=point(c,d);val r=point(e,f);path.cubicTo(p.first,p.second,q.first,q.second,r.first,r.second)
    }
    fun oval(x:Float,y:Float,rx:Float,ry:Float) {
        val k=.5522848f;move(x+rx,y)
        curve(x+rx,y+k*ry,x+k*rx,y+ry,x,y+ry);curve(x-k*rx,y+ry,x-rx,y+k*ry,x-rx,y)
        curve(x-rx,y-k*ry,x-k*rx,y-ry,x,y-ry);curve(x+k*rx,y-ry,x+rx,y-k*ry,x+rx,y);path.close()
    }
    fun polygon(vararg p:Pair<Float,Float>) {p.forEachIndexed {i,v->if(i==0) move(v.first,v.second) else line(v.first,v.second)};path.close()}
    fun pad(wide:Boolean=false) {
        move(.18f,.62f);curve(.22f,.46f,.37f,.44f,.5f,.5f);curve(.64f,.43f,.81f,.48f,.85f,.65f)
        curve(.96f,.87f,.69f,.94f,.5f,if(wide) .83f else .78f);curve(.24f,.98f,.04f,.84f,.18f,.62f);path.close()
    }
    fun claw(x:Float,y:Float)=polygon(x-.035f to y+.06f,x to y-.04f,x+.035f to y+.06f)
}
fun animalFootprintsPath(steps:List<FootstepGeometry>,style:FootprintStyle):Path=Path().apply {
    for(g in steps) {
        if(style==FootprintStyle.CRUTCHES && g.fore!=true) {
            addPath(footprintShapesPath(listOf(g),FootprintStyle.SHOE))
            if(g.fore==null) addPath(animalFootprintsPath(listOf(
                g.copy(x=g.x-9*g.scale,y=g.y-5*g.scale,scale=g.scale*.28f,fore=true),
                g.copy(x=g.x+9*g.scale,y=g.y-5*g.scale,scale=g.scale*.28f,fore=true,left=false)),style))
            continue
        }
        val aspect=when(style) {FootprintStyle.DUCK->.62f;FootprintStyle.CAT,FootprintStyle.DOG,FootprintStyle.BEAR->.65f;FootprintStyle.ELEPHANT->.55f;FootprintStyle.FOX->.75f;FootprintStyle.HORSE->.8f;FootprintStyle.TURTLE->.7f;else->1f}
        val d=Sole(g,aspect)
        with(d) {when(style) {
            FootprintStyle.CAT->{pad();oval(.16f,.36f,.12f,.12f);oval(.38f,.22f,.12f,.13f);oval(.65f,.22f,.12f,.13f);oval(.87f,.38f,.11f,.12f)}
            FootprintStyle.DOG->{pad(true);listOf(.16f,.38f,.64f,.86f).forEachIndexed {i,x->val y=if(i==0 || i==3) .31f else .19f;oval(x,y,.12f,.14f);claw(x,y-.12f)}}
            FootprintStyle.DUCK->{
                move(.48f,.98f);curve(.17f,1f,.16f,.79f,.16f,.65f)
                line(.015f,.30f);curve(.005f,.23f,.04f,.21f,.12f,.24f);line(.25f,.33f)
                line(.28f,.06f);curve(.29f,0f,.34f,.01f,.39f,.07f);line(.54f,.22f)
                curve(.58f,.26f,.61f,.23f,.65f,.17f);line(.81f,.045f)
                curve(.90f,-.025f,.95f,.015f,.95f,.12f);line(.94f,.63f)
                curve(.94f,.83f,.74f,.98f,.48f,.98f);path.close()
            }
            FootprintStyle.BAREFOOT->{move(.27f,.97f);curve(.04f,.91f,.1f,.72f,.25f,.56f);curve(.39f,.42f,.1f,.4f,.13f,.3f);curve(.35f,.17f,.91f,.18f,.92f,.39f);curve(.94f,.55f,.6f,.52f,.62f,.73f);curve(.68f,.99f,.5f,1f,.27f,.97f);path.close();oval(.17f,.13f,.13f,.11f);oval(.41f,.11f,.09f,.09f);oval(.6f,.13f,.08f,.08f);oval(.77f,.17f,.07f,.07f);oval(.91f,.22f,.06f,.06f)}
            FootprintStyle.HORSE->{move(.08f,.91f);line(.08f,.37f);curve(.08f,-.02f,.91f,-.02f,.92f,.37f);line(.92f,.91f);line(.69f,.91f);line(.7f,.39f);curve(.7f,.17f,.3f,.17f,.3f,.39f);line(.31f,.91f);path.close()}
            FootprintStyle.BIRD->{polygon(.46f to .95f,.44f to .62f,.04f to .18f,.1f to .09f,.47f to .46f,.48f to .02f,.57f to .02f,.56f to .47f,.92f to .14f,.98f to .24f,.57f to .63f,.56f to .95f)}
            FootprintStyle.RABBIT->{oval(.5f,.67f,.28f,.3f);oval(.3f,.22f,.13f,.2f);oval(.68f,.22f,.13f,.2f)}
            FootprintStyle.FOX->{polygon(.2f to .66f,.5f to .44f,.79f to .67f,.5f to .98f);listOf(.15f,.39f,.65f,.88f).forEachIndexed {i,x->val y=if(i==0 || i==3) .36f else .2f;oval(x,y,.1f,.14f);claw(x,y-.12f)}}
            FootprintStyle.BEAR->{oval(.5f,.69f,.43f,.27f);listOf(.1f,.3f,.5f,.7f,.9f).forEachIndexed {i,x->val y=.23f+abs(i-2)*.035f;oval(x,y,.09f,.14f);claw(x,y-.14f)}}
            FootprintStyle.LIZARD->{polygon(.4f to .96f,.44f to .58f,.03f to .55f,.02f to .44f,.38f to .44f,.07f to .12f,.15f to .07f,.46f to .36f,.47f to .02f,.56f to .02f,.57f to .36f,.86f to .06f,.96f to .13f,.64f to .44f,.98f to .43f,.98f to .55f,.62f to .59f,.6f to .96f)}
            FootprintStyle.TURTLE->{oval(.5f,.65f,.4f,.3f);oval(.18f,.27f,.12f,.22f);oval(.49f,.2f,.13f,.18f);oval(.8f,.27f,.12f,.22f)}
            FootprintStyle.ELEPHANT->{oval(.5f,.6f,.48f,.36f);oval(.13f,.25f,.1f,.14f);oval(.4f,.16f,.14f,.14f);oval(.7f,.18f,.13f,.14f);oval(.91f,.29f,.08f,.13f)}
            FootprintStyle.DINOSAUR->polygon(.32f to .98f,.26f to .62f,.02f to .24f,.12f to .03f,.34f to .39f,.5f to .01f,.63f to .04f,.62f to .4f,.89f to .08f,.99f to .26f,.72f to .65f,.65f to .98f)
            FootprintStyle.ROBOT->{polygon(.06f to .02f,.94f to .02f,.98f to .48f,.71f to .58f,.29f to .58f,.02f to .48f);polygon(.28f to .69f,.72f to .69f,.88f to .97f,.12f to .97f);polygon(.04f to .57f,.19f to .6f,.15f to .8f,.02f to .78f)}
            FootprintStyle.DEER->{
                move(.44f,.03f);curve(.13f,.19f,.04f,.61f,.13f,.89f);curve(.23f,1f,.42f,.91f,.45f,.67f);line(.44f,.03f);path.close()
                move(.56f,.03f);curve(.87f,.19f,.96f,.61f,.87f,.89f);curve(.77f,1f,.58f,.91f,.55f,.67f);line(.56f,.03f);path.close()
            }
            FootprintStyle.GOAT->{
                move(.4f,.1f);curve(.15f,.03f,.03f,.31f,.08f,.6f);curve(.13f,.86f,.37f,.84f,.44f,.66f);line(.4f,.1f);path.close()
                move(.6f,.1f);curve(.85f,.03f,.97f,.31f,.92f,.6f);curve(.87f,.86f,.63f,.84f,.56f,.66f);line(.6f,.1f);path.close()
                oval(.24f,.94f,.09f,.055f);oval(.76f,.94f,.09f,.055f)
            }
            FootprintStyle.WOLF->{
                move(.18f,.64f);curve(.24f,.51f,.4f,.46f,.5f,.55f);curve(.65f,.43f,.83f,.53f,.85f,.7f)
                curve(.86f,.91f,.62f,.99f,.5f,.9f);curve(.29f,.99f,.08f,.83f,.18f,.64f);path.close()
                listOf(.14f,.38f,.64f,.87f).forEachIndexed {i,x->val y=if(i==0 || i==3) .4f else .25f;oval(x,y,.105f,.17f);claw(x,y-.17f)}
            }
            FootprintStyle.OTTER->{
                polygon(.08f to .36f,.23f to .23f,.39f to .17f,.58f to .16f,.78f to .23f,.94f to .38f,.83f to .68f,.65f to .81f,.35f to .81f,.15f to .65f)
                listOf(.1f,.3f,.5f,.7f,.9f).forEachIndexed {i,x->oval(x,.22f+abs(i-2)*.065f,.085f,.15f)}
                oval(.5f,.87f,.23f,.11f)
            }
            FootprintStyle.RACCOON->{
                oval(.5f,.76f,.28f,.22f)
                listOf(.08f,.28f,.48f,.68f,.88f).forEachIndexed {i,x->val y=.18f+abs(i-2)*.065f;oval(x,y,.065f,.16f);polygon(.4f to .69f,x-.05f to y+.08f,x+.05f to y+.08f,.6f to .69f)}
            }
            FootprintStyle.HEDGEHOG->{
                oval(.5f,.73f,.32f,.24f)
                listOf(.11f,.31f,.51f,.71f,.91f).forEachIndexed {i,x->val y=.3f+abs(i-2)*.055f;oval(x,y,.075f,.13f);claw(x,y-.13f)}
            }
            FootprintStyle.FROG->{
                oval(.5f,.79f,.17f,.19f)
                val count=if(g.fore==true) 4 else 5
                repeat(count) {i->val x=.075f+.85f*i/(count-1);val y=.12f+abs(x-.5f)*.42f
                    polygon(.42f to .75f,x-.035f to y,x+.035f to y,.58f to .75f);oval(x,y,.07f,.065f)
                    if(g.fore!=true && i<count-1) polygon(.5f to .69f,x to y+.1f,(x+.85f/(count-1)) to (.12f+abs(x+.85f/(count-1)-.5f)*.42f+.1f))
                }
            }
            FootprintStyle.BADGER->{
                oval(.5f,.78f,.42f,.2f)
                listOf(.1f,.3f,.5f,.7f,.9f).forEachIndexed {i,x->val y=.39f+abs(i-2)*.035f;oval(x,y,.085f,.14f)
                    polygon(x-.035f to y-.1f,x-.025f to (if(g.fore==false) y-.2f else .035f),x+.025f to (if(g.fore==false) y-.2f else .035f),x+.035f to y-.1f)}
            }
            FootprintStyle.HIKING_BOOT->{
                move(.13f,.43f);curve(.03f,-.1f,.97f,-.1f,.91f,.43f);line(.82f,.65f);line(.64f,.75f);line(.28f,.75f);line(.1f,.57f);path.close()
                polygon(.2f to .83f,.72f to .83f,.8f to .98f,.16f to .98f)
                listOf(.15f,.36f,.59f,.82f).forEach {x->polygon(x-.06f to .25f,x+.06f to .25f,x+.06f to .42f,x-.06f to .42f)}
                polygon(.01f to .48f,.15f to .46f,.16f to .6f,.02f to .62f)
            }
            FootprintStyle.HIGH_HEEL->{
                move(.12f,.21f);curve(.13f,-.05f,.91f,-.03f,.92f,.25f);curve(.94f,.5f,.67f,.68f,.5f,.7f)
                curve(.29f,.65f,.07f,.47f,.12f,.21f);path.close();oval(.46f,.91f,.13f,.075f)
            }
            FootprintStyle.SNEAKER->{
                path.addPath(footprintShapesPath(listOf(g),FootprintStyle.SHOE));path.fillType=androidx.compose.ui.graphics.PathFillType.EvenOdd
                for(y in listOf(.16f,.3f,.43f,.83f))polygon(.26f to y,.76f to y,.72f to y+.045f,.22f to y+.045f)
            }
            FootprintStyle.CRUTCHES->{oval(.5f,.5f,.47f,.3f);oval(.5f,.22f,.3f,.12f)}
            FootprintStyle.PENGUIN->{
                polygon(.46f to .98f,.18f to .66f,.02f to .19f,.16f to .12f,.34f to .35f,.45f to .03f,.58f to .04f,.67f to .34f,.9f to .12f,.98f to .22f,.82f to .72f,.6f to .98f)
            }
            FootprintStyle.OSTRICH->{
                oval(.43f,.62f,.2f,.34f);oval(.43f,.23f,.16f,.22f);oval(.8f,.46f,.13f,.24f);claw(.43f,.035f)
            }
            FootprintStyle.CHICKEN->{
                polygon(.44f to .98f,.45f to .63f,.03f to .22f,.1f to .1f,.46f to .46f,.48f to .02f,.58f to .02f,.57f to .46f,.88f to .14f,.98f to .25f,.58f to .65f,.57f to .98f)
                claw(.07f,.14f);claw(.53f,.025f);claw(.94f,.18f)
            }
            FootprintStyle.SQUIRREL,FootprintStyle.MOUSE->{
                oval(.5f,.75f,.27f,.21f)
                val n=if(g.fore==true)4 else 5
                repeat(n) {i->val x=.1f+.8f*i/(n-1);val y=.2f+abs(x-.5f)*.3f
                    oval(x,y,.065f,if(style==FootprintStyle.SQUIRREL).18f else .1f)
                    polygon(.44f to .72f,x-.035f to y+.08f,x+.035f to y+.08f,.56f to .72f)
                    if(style==FootprintStyle.SQUIRREL)claw(x,y-.17f)
                }
            }
            FootprintStyle.LION->{pad(true);oval(.14f,.36f,.12f,.15f);oval(.38f,.2f,.14f,.17f);oval(.66f,.23f,.14f,.16f);oval(.88f,.41f,.11f,.14f)}
            FootprintStyle.BOAR->{
                polygon(.44f to .03f,.15f to .16f,.07f to .66f,.24f to .83f,.44f to .7f)
                polygon(.56f to .03f,.85f to .16f,.93f to .66f,.76f to .83f,.56f to .7f)
                oval(.15f,.94f,.09f,.06f);oval(.85f,.94f,.09f,.06f)
            }
            FootprintStyle.CROCODILE->{
                oval(.5f,.73f,.27f,.25f)
                val n=if(g.fore==false)4 else 5
                repeat(n) {i->val x=.06f+.88f*i/(n-1);val y=.15f+abs(x-.5f)*.45f
                    polygon(.42f to .76f,x-.05f to y,x+.05f to y,.58f to .76f)
                    oval(x,y,.07f,.11f);if(i<3)claw(x,y-.1f)
                }
            }
            FootprintStyle.PIXEL_HERO->{
                polygon(.1f to .06f,.8f to .06f,.8f to .18f,.96f to .18f,.96f to .48f,.82f to .48f,.82f to .65f,.63f to .65f,.63f to .78f,.18f to .78f,.18f to .61f,.06f to .61f,.06f to .2f,.1f to .2f)
                polygon(.22f to .86f,.7f to .86f,.7f to .98f,.22f to .98f)
            }
            FootprintStyle.SPACE_BOOT->{
                polygon(.12f to .03f,.88f to .03f,.99f to .16f,.94f to .49f,.76f to .63f,.24f to .63f,.06f to .49f,.01f to .16f)
                polygon(.22f to .73f,.78f to .73f,.92f to .98f,.08f to .98f)
                path.fillType=androidx.compose.ui.graphics.PathFillType.EvenOdd
                polygon(.25f to .22f,.5f to .32f,.75f to .22f,.75f to .28f,.5f to .39f,.25f to .28f)
            }
            FootprintStyle.YETI->{
                oval(.5f,.7f,.43f,.28f);listOf(.1f,.31f,.53f,.74f,.92f).forEachIndexed {i,x->oval(x,.2f+abs(i-2)*.035f,.09f,.13f)}
            }
            FootprintStyle.BIKE_SCOOTER->polygon(.4f to 0f,.6f to 0f,.6f to 1f,.4f to 1f)
            FootprintStyle.WHEELCHAIR->{polygon(.02f to 0f,.2f to 0f,.2f to 1f,.02f to 1f);polygon(.8f to 0f,.98f to 0f,.98f to 1f,.8f to 1f)}
            FootprintStyle.SHOE->Unit
        }}
        addPath(d.path)
    }
}
