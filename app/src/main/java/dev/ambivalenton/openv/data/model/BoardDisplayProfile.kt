// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

/** Display materials are a design decision, independent of timetable data and widget size. */
enum class BoardDisplayTechnology { VFD, MONO_CRT, COLOR_CRT, REFLECTIVE_LCD, TFT_LCD, OLED, MICRO_LED,
    /** LED dot matrix with lit and unlit diodes. */
    LED_MATRIX,
    /** Electromagnetic flip-dot discs (bright face / dark back). */
    FLIP_DOT,
    /** Mechanical split-flap modules, one flap per character. */
    SPLIT_FLAP,
    /** Seven-segment LED digits with dim unused segments. */
    SEGMENT_LED,
    /** Reflective electronic paper: no glow, crisp ink, paper grain. */
    E_INK,
    /** Cold-cathode digit tubes behind a dark glass. */
    NIXIE,
    /** Monochrome orange gas-plasma panel with fine pixels. */
    PLASMA,
}

fun boardDisplayTechnology(theme:AppTheme):BoardDisplayTechnology=theme.display ?: theme.language.defaultDisplay(theme.dark) ?:
    theme.themeLanguage.defaultDisplay(theme.dark) ?: when {
    theme in setOf(AppTheme.POCKET_CONSOLE,AppTheme.PIXEL_DAWN)->BoardDisplayTechnology.REFLECTIVE_LCD
    theme==AppTheme.DECADE_70 || theme==AppTheme.VINTAGE_TRANSIT || theme.texture in setOf(
        ThemeTexture.RENAISSANCE,ThemeTexture.BAROQUE,ThemeTexture.NOUVEAU,ThemeTexture.DECO,
        ThemeTexture.WOOD,ThemeTexture.KRAFT,ThemeTexture.NEWSPRINT)->BoardDisplayTechnology.VFD
    theme in setOf(AppTheme.SPACE_TERMINAL,AppTheme.OS_MAC_CLASSIC,AppTheme.OS_ATARI) ||
        theme.texture in setOf(ThemeTexture.PAPER,ThemeTexture.INK,ThemeTexture.ROMANTIC,
            ThemeTexture.IMPRESSION,ThemeTexture.SURREAL,ThemeTexture.CONSTRUCT)->BoardDisplayTechnology.MONO_CRT
    theme in setOf(AppTheme.SYNTHWAVE,AppTheme.RETRO_ARCADE,AppTheme.DECADE_80,AppTheme.DECADE_90,
        AppTheme.OS_WIN_95,AppTheme.OS_AMIGA,AppTheme.OS_BEOS,AppTheme.OS_NEXTSTEP,AppTheme.OS_OS2,AppTheme.OS_CDE)->BoardDisplayTechnology.COLOR_CRT
    theme in setOf(AppTheme.DECADE_00,AppTheme.OS_WIN_XP,AppTheme.OS_WIN_7,AppTheme.OS_MAC_AQUA,
        AppTheme.OS_MAC_LEOPARD,AppTheme.OS_IOS_4)->BoardDisplayTechnology.TFT_LCD
    theme.texture in setOf(ThemeTexture.CIRCUITS,ThemeTexture.BLUEPRINT,ThemeTexture.ROUTES,
        ThemeTexture.CONCRETE,ThemeTexture.CITYLINE)->BoardDisplayTechnology.MICRO_LED
    else->BoardDisplayTechnology.OLED
}
