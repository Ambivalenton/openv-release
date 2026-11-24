// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.theme

import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import kotlin.math.roundToInt

/**
 * Exactly twenty stable ARGB values. Build 113: with an app design, they are that design's own colour
 * world (ThemeColorWorld) – its hues, their partners, lighter and deeper forms – instead of a family
 * shared with many other designs. Names come from the colours themselves (empty nameKey).
 */
fun favoriteThemeColors(settings:AppSettings,themeAccent:Long):List<DesignSwatch> {
    settings.appTheme?.let {theme->return ThemeColorWorld.favorites(theme).map {DesignSwatch("",it)}}
    // Material You / no app design: the presets, tinted a little towards the system accent, and lighter variants.
    fun blend(a:Long,b:Long,t:Float):Long {
        fun c(s:Int)=(((a shr s and 255)*(1-t)+(b shr s and 255)*t).roundToInt()).toLong()
        return 0xff000000L or (c(16) shl 16) or (c(8) shl 8) or c(0)
    }
    val base=favoriteColorPresets.map {DesignSwatch(it.name,it.seed)}
    val background=0xfff7f5faL
    val choices=linkedMapOf<Long,DesignSwatch>()
    fun add(s:DesignSwatch) {choices.putIfAbsent(s.color or 0xff000000L,s.copy(color=s.color or 0xff000000L))}
    base.forEach {s->add(s.copy(color=blend(s.color,themeAccent,.16f)))}
    for(amount in listOf(.32f,.19f,.48f,.64f)) {
        base.forEach {s->add(DesignSwatch(tr("%1\$s · Variante",tr(s.nameKey)),blend(s.color,background,amount)))}
        if(choices.size>=20) break
    }
    check(choices.size>=20)
    return choices.values.take(20)
}
