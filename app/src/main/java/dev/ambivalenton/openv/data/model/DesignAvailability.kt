// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

/** Real display technologies and the explicitly retained alternatives. IDs remain decodable. */
val supportedBoardDesigns=listOf(BoardDesign.APP_INTEGRATED,BoardDesign.APP_THEME,BoardDesign.MATERIAL,BoardDesign.DOT_MATRIX,BoardDesign.DB,
    BoardDesign.SEVEN_SEGMENT,BoardDesign.RETRO_LCD,BoardDesign.TELETEXT,BoardDesign.DOS,BoardDesign.FLIP_DOT,BoardDesign.SPLIT_FLAP,
    BoardDesign.E_PAPER,BoardDesign.MODERN_MATRIX,BoardDesign.RETRO_CRT,BoardDesign.NIXIE,BoardDesign.TRAM_ROLLSIGN,
    BoardDesign.PRINTED_SCHEDULE,BoardDesign.PLAIN)
val supportedTimelineThemes=listOf(TimelineTheme.APP_THEME,TimelineTheme.ORIGINAL,TimelineTheme.VIVID,
    TimelineTheme.MONOCHROME,TimelineTheme.PASTEL,TimelineTheme.MONET,TimelineTheme.CAPSULE,TimelineTheme.SQUARED,TimelineTheme.OUTLINE)
fun normalizedTimelineTheme(theme:TimelineTheme)=theme.takeIf {it in supportedTimelineThemes}?:TimelineTheme.APP_THEME
fun WidgetStyle.fixedFavoritePresentation()=copy(opacity=0,iconsOnly=true,showSettings=false,elevation=2,glassDepth=0,
    backgroundColor=null,tileColor=null,borderColor=null,iconColor=null,compactBackground=false,
    design=DesignOptions(containerElevation=0f,buttonElevation=2f,containerShadow=false,tileShadow=true))
