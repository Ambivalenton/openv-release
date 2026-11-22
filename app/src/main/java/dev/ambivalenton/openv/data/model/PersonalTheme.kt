// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model
import kotlinx.serialization.Serializable
import dev.ambivalenton.openv.localization.tr
import java.text.Normalizer
import java.util.Locale

const val MAX_PERSONAL_THEMES=30
@Serializable data class PersonalDesign(
    val appearance:AppearanceProfile,
    val fontId:String?=null,
    val overview:OverviewSettings=OverviewSettings(),
    val board:BoardSettings=BoardSettings(),
    val timelineShowWalking:Boolean=true,
    val logo:AppLogo=AppLogo.ORIGINAL,
    /** Null in older themes = keep the current network plan design. */
    val networkPlan:NetworkPlanDesign?=null,
    /** 2.10 (build 93): the day and the night design are saved together; null in older themes = keep. */
    val dayNight:DayNightSettings?=null,
)
@Serializable data class PersonalTheme(val id:String=newId(),val name:String,val createdAt:Long,
    val design:PersonalDesign)

fun personalThemeName(value:String)=Normalizer.normalize(value.trim(),Normalizer.Form.NFC)
fun personalThemeNameKey(value:String)=personalThemeName(value).lowercase(Locale.ROOT)
fun validPersonalThemeName(value:String)=value.length in 1..48&&value.none{it.isISOControl()}&&value==personalThemeName(value)
fun AppSettings.personalDesign(logo:AppLogo)=PersonalDesign(appearanceProfile(),fontId,
    overview.copy(bufferMinutes=0f,routingPreferences=OverviewRoutingPreferences()),
    board.copy(selectedStop=null),timelineShowWalking,logo.currentReplacement(),networkPlanDesign,
    dayNight.copy(lastAutoNight=null))

/** 2.10: whether [design] is the design shown now (older themes without day/night compare without it). */
fun PersonalDesign.matches(current:PersonalDesign)=
    if(dayNight==null) current.copy(dayNight=null)==this else current==this

/** Design only: a personal theme never replaces a route, location, favourite or navigation policy. */
fun AppSettings.withPersonalDesign(d:PersonalDesign):AppSettings {
    val p=d.appearance
    val next=copy(appTheme=p.appTheme,themeMode=p.themeMode,appThemeColors=p.appThemeColors,accentSeed=p.accentSeed,
        readability=p.readability,widget=p.widget,footprints=p.footprints,fontId=d.fontId,
        timelineTheme=p.timelineTheme,timelineLabelControl=p.timelineLabelControl,timelineSeed=p.timelineSeed,
        timelinePalettes=p.timelinePalettes,timelineLineColors=p.timelineLineColors,timelineShowWalking=d.timelineShowWalking,
        overview=d.overview.copy(bufferMinutes=overview.bufferMinutes,routingPreferences=overview.routingPreferences),
        board=d.board.copy(selectedStop=board.selectedStop),networkPlanDesign=d.networkPlan ?: networkPlanDesign,
        dayNight=d.dayNight?.copy(lastAutoNight=dayNight.lastAutoNight) ?: dayNight)
    // Quiet mode owns a separate restrained appearance. Preserve the chosen normal profile.
    return if(!quietMode)next else next.copy(quietMode=false).withQuietMode(true)
}

fun StoredState.withPersonalTheme(name:String,logo:AppLogo,now:Long):Pair<StoredState,PersonalTheme>{
    val label=personalThemeName(name)
    require(validPersonalThemeName(label)){tr("Bitte einen Namen mit höchstens 48 Zeichen eingeben.")}
    require(personalThemes.size<MAX_PERSONAL_THEMES){tr("Bis zu 30 persönliche Themes sind möglich.")}
    require(personalThemes.none{personalThemeNameKey(it.name)==personalThemeNameKey(label)}){tr("Dieser Theme-Name ist bereits vergeben.")}
    val theme=PersonalTheme(name=label,createdAt=now,design=settings.personalDesign(logo))
    return copy(personalThemes=personalThemes+theme,personalThemesIntroduced=true) to theme
}
