// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.theme

import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import kotlin.math.roundToInt

/** Exactly twenty stable ARGB values. A theme's established colour family remains authoritative. */
fun favoriteThemeColors(settings:AppSettings,themeAccent:Long):List<DesignSwatch> {
    val theme=settings.appTheme
    if(theme!=null && appColorFamily(theme)==DesignColorFamily.INK) return (0..19).map {i->
        val v=(255f*i/19).roundToInt().toLong()
        DesignSwatch(tr("Graustufe %1\$s",i+1),0xff000000L or (v shl 16) or (v shl 8) or v)
    }
    fun blend(a:Long,b:Long,t:Float):Long {
        fun c(s:Int)=(((a shr s and 255)*(1-t)+(b shr s and 255)*t).roundToInt()).toLong()
        return 0xff000000L or (c(16) shl 16) or (c(8) shl 8) or c(0)
    }
    val base=theme?.let {appColorFamily(it).colors} ?: favoriteColorPresets.map {DesignSwatch(it.name,it.seed)}
    val background=theme?.let {settings.appThemeColors[it]?.background ?: it.defaultColors().background} ?: 0xfff7f5faL
    val choices=linkedMapOf<Long,DesignSwatch>()
    fun add(s:DesignSwatch) {choices.putIfAbsent(s.color or 0xff000000L,s.copy(color=s.color or 0xff000000L))}
    base.forEachIndexed {i,s->add(if(theme==null) s.copy(color=blend(s.color,themeAccent,.16f)) else s)}
    for(amount in listOf(.32f,.19f,.48f,.64f)) {
        base.forEach {s->add(DesignSwatch(tr("%1\$s · Variante",tr(s.nameKey)),blend(s.color,background,amount)))}
        if(choices.size>=20) break
    }
    check(choices.size>=20)
    return choices.values.take(20)
}
