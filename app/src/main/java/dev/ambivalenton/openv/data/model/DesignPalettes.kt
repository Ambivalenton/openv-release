// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import kotlinx.serialization.Serializable

@Serializable data class AppThemeColors(val background:Long,val surface:Long,val accent:Long,val secondary:Long,val text:Long)
fun AppTheme.defaultColors()=AppThemeColors(background,surface,accent,secondary,if(dark) 0xFFF3F2EE else 0xFF181A1C)

@Serializable data class TimelinePaletteOverride(val seed:Long?=null,val lineColors:Map<LineType,Long> = emptyMap())

/** Keep legacy fields readable, but save each selected design's palette independently. */
fun AppSettings.timelinePalette(theme:TimelineTheme=timelineTheme):TimelinePaletteOverride =
    timelinePalettes[theme] ?: if(theme==timelineTheme) TimelinePaletteOverride(timelineSeed,timelineLineColors) else TimelinePaletteOverride()

fun AppSettings.selectTimelineTheme(theme:TimelineTheme):AppSettings {
    val saved=timelinePalettes+(timelineTheme to timelinePalette())
    val target=saved[theme] ?: TimelinePaletteOverride()
    return copy(timelineTheme=theme,timelineSeed=target.seed,timelineLineColors=target.lineColors,timelinePalettes=saved)
}

fun AppSettings.withTimelinePalette(theme:TimelineTheme,palette:TimelinePaletteOverride?):AppSettings {
    val saved=if(palette==null) timelinePalettes-theme else timelinePalettes+(theme to palette)
    return if(theme==timelineTheme) copy(timelinePalettes=saved,timelineSeed=palette?.seed,timelineLineColors=palette?.lineColors.orEmpty())
        else copy(timelinePalettes=saved)
}

enum class TimelineBarStyle {
    CLASSIC,CAPSULE,SQUARED,OUTLINE,METRO_RAILS,DOUBLE_RAIL,DASHED,DOTTED,BEADS,TICKET,
    CHEVRON,RIBBON,GLASS,NEON,GRADIENT,TERMINAL,PIXEL,BRAID,TRACK_SLEEPERS,SPINE,BRACKET,
    HALFTONE,LOLLIPOP,SEQUENCE,MINIMAL,APP_THEME
}

fun TimelineTheme.titleFor(settings:AppSettings)=if(this==TimelineTheme.APP_THEME)
    dev.ambivalenton.openv.localization.tr("App-Theme · %1\$s",settings.appTheme?.title?:dev.ambivalenton.openv.localization.tr("Systemdesign")) else title
