// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import kotlinx.serialization.Serializable

val quietAppThemes=listOf(AppTheme.CLASSIC_LIGHT,AppTheme.CLASSIC_DARK,AppTheme.AMOLED_BLACK)
val quietBoardDesigns=listOf(BoardDesign.APP_INTEGRATED,BoardDesign.APP_THEME,BoardDesign.RETRO_LCD,BoardDesign.E_PAPER,BoardDesign.PLAIN,BoardDesign.MATERIAL)
val quietTimelineThemes=listOf(TimelineTheme.MONOCHROME,TimelineTheme.MINIMAL,TimelineTheme.ORIGINAL,TimelineTheme.APP_THEME)

/** Appearance only: switching modes never changes routes, location consent, favourites or alerts. */
@Serializable data class AppearanceProfile(
    val appTheme:AppTheme?=null,val themeMode:ThemeMode=ThemeMode.SYSTEM,
    val appThemeColors:Map<AppTheme,AppThemeColors> = emptyMap(),val accentSeed:Long?=null,
    val readability:AppReadability=AppReadability(),
    val timelineTheme:TimelineTheme=TimelineTheme.ORIGINAL,val timelineLabelControl:Float=1f,
    val timelineSeed:Long?=null,val timelinePalettes:Map<TimelineTheme,TimelinePaletteOverride> = emptyMap(),
    val timelineLineColors:Map<LineType,Long> = emptyMap(),
    val boardDesign:BoardDesign=BoardDesign.DOT_MATRIX,val boardColors:Map<BoardDesign,BoardColors> = emptyMap(),
    val boardMaterial:Boolean=false,val widget:WidgetStyle=WidgetStyle(),val footprints:FootprintPreferences=FootprintPreferences(),
)
fun AppSettings.appearanceProfile()=AppearanceProfile(appTheme,themeMode,appThemeColors,accentSeed,readability,
    timelineTheme,timelineLabelControl,timelineSeed,timelinePalettes,timelineLineColors,
    board.design,board.customColors,board.forceMaterialInApp,widget,footprints)
internal fun AppSettings.applyProfile(p:AppearanceProfile)=copy(appTheme=p.appTheme,themeMode=p.themeMode,
    appThemeColors=p.appThemeColors,accentSeed=p.accentSeed,readability=p.readability,
    timelineTheme=p.timelineTheme,timelineLabelControl=p.timelineLabelControl,timelineSeed=p.timelineSeed,
    timelinePalettes=p.timelinePalettes,timelineLineColors=p.timelineLineColors,
    board=board.copy(design=p.boardDesign,customColors=p.boardColors,forceMaterialInApp=p.boardMaterial),widget=p.widget,footprints=p.footprints)
fun initialQuietAppearance()=AppearanceProfile(appTheme=AppTheme.CLASSIC_DARK,
    appThemeColors=mapOf(
        AppTheme.CLASSIC_LIGHT to AppThemeColors(0xFFF8F8F6,0xFFFFFFFF,0xFF252525,0xFF555555,0xFF181818),
        AppTheme.CLASSIC_DARK to AppThemeColors(0xFF171717,0xFF252525,0xFFE8E8E8,0xFFBBBBBB,0xFFF4F4F4),
        AppTheme.AMOLED_BLACK to AppThemeColors(0xFF000000,0xFF101010,0xFFE8E8E8,0xFFBBBBBB,0xFFFFFFFF)),
    timelineTheme=TimelineTheme.MONOCHROME,boardDesign=BoardDesign.E_PAPER,
    footprints=FootprintPreferences(speed=0f))
fun AppSettings.withQuietMode(enabled:Boolean):AppSettings {
    val normalized=normalizedQuietAppearance()
    if(normalized!=this) return normalized.withQuietMode(enabled)
    if(enabled==quietMode) return this
    val current=appearanceProfile()
    return if(enabled) applyProfile(quietAppearance ?: initialQuietAppearance()).copy(quietMode=true,normalAppearance=current)
    else applyProfile(normalAppearance ?: AppearanceProfile()).copy(quietMode=false,quietAppearance=current)
}
/** Defensive load boundary for edited/imported preferences. Each mode retains its own draft. */
fun AppSettings.normalizedQuietAppearance():AppSettings {
    val oldDefault=initialQuietAppearance().copy(appTheme=AppTheme.CLASSIC_LIGHT)
    val migrated=if(quietAppearanceVersion>=1) this else copy(quietAppearanceVersion=1,
        quietAppearance=if(quietAppearance==oldDefault) initialQuietAppearance() else quietAppearance,
        appTheme=if(quietMode && appearanceProfile()==oldDefault) AppTheme.CLASSIC_DARK else appTheme)
    if(!migrated.quietMode) return migrated
    return migrated.copy(appTheme=if (migrated.appTheme == null && migrated.themeMode == ThemeMode.SYSTEM) null
        else migrated.appTheme?.takeIf {it in quietAppThemes} ?: AppTheme.CLASSIC_DARK,
        timelineTheme=migrated.timelineTheme.takeIf {it in quietTimelineThemes} ?: TimelineTheme.MONOCHROME,
        board=migrated.board.copy(design=migrated.board.design.takeIf {it in quietBoardDesigns} ?: BoardDesign.E_PAPER))
}
