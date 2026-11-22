// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.ui.graphics.Path
import dev.ambivalenton.openv.data.model.FootprintStyle
import kotlin.math.*

/** A planted print never translates. Only its initial heel/toe contact is revealed locally. */
fun footstepContactFraction(ageMillis:Float):Float {
    val x=(ageMillis/180f).coerceIn(0f,1f);return x*x*(3-2*x)
}
fun footstepContactMask(g:FootstepGeometry,ageMillis:Float,style:FootprintStyle):Path? {
    if(ageMillis>=180f || (style==FootprintStyle.CRUTCHES && g.fore==true) || style in setOf(FootprintStyle.HORSE,FootprintStyle.DEER,FootprintStyle.GOAT,FootprintStyle.BOAR,
        FootprintStyle.ELEPHANT,FootprintStyle.ROBOT,FootprintStyle.FROG,FootprintStyle.RABBIT)) return null
    val fraction=footstepContactFraction(ageMillis)
    val heelFirst=style in setOf(FootprintStyle.SHOE,FootprintStyle.BAREFOOT,FootprintStyle.HIKING_BOOT,FootprintStyle.HIGH_HEEL,
        FootprintStyle.BEAR,FootprintStyle.RACCOON,FootprintStyle.BADGER,FootprintStyle.SNEAKER,FootprintStyle.CRUTCHES,
        FootprintStyle.PIXEL_HERO,FootprintStyle.SPACE_BOOT,FootprintStyle.YETI)
    val halfWidth=FOOTPRINT_WIDTH_PX*g.scale*.65f
    val halfLength=FOOTPRINT_LENGTH_PX*g.scale*.65f
    val from=if(heelFirst) -halfLength else halfLength-2*halfLength*fraction
    val to=if(heelFirst) -halfLength+2*halfLength*fraction else halfLength
    return Path().apply {
        listOf(-halfWidth to from,halfWidth to from,halfWidth to to,-halfWidth to to).forEachIndexed {i,(x,y)->
            val px=g.x+x*cos(g.angle)+y*sin(g.angle);val py=g.y+y*cos(g.angle)-x*sin(g.angle)
            if(i==0) moveTo(px,py) else lineTo(px,py)
        };close()
    }
}
