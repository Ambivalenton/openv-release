// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.rendering

import android.graphics.Color
import androidx.core.graphics.ColorUtils
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.theme.readableForeground
import dev.ambivalenton.openv.ui.theme.toneColor

/**
 * "Display": every app theme gets a screen that could hang at a station, in a café or in a
 * games room. The screen contrasts with the app (light themes → dark screens and vice versa), while
 * its colours, era and motifs come from the theme. Art and retro themes may be paintings or games.
 */
enum class DisplayMaterial {
    OLED, LED_MATRIX, STATION_TFT, VFD, CRT, CHALKBOARD, E_PAPER, LIGHT_LCD, REFLECTIVE_LCD,
    LIGHTBOX, PAPER, ENAMEL, SPLIT_FLAP, PAINTING, GAME, OS_WINDOW,
    /** Build 110 · linen in a stitched frame, every letter in cross-stitch (textile collection). */
    EMBROIDERY,
    /** Build 110 · a polished stone or metal plate with engraved letters and a faceted rim (minerals). */
    ENGRAVED,
    /** Build 110 · a felt letter board with grooves, white push-in letters and a wooden frame (pets, café). */
    LETTERBOARD,
}

enum class DisplayMotif {
    NONE, ARTWORK, SPECTRUM, OSCILLOSCOPE, FLAT,
    // Paper and print
    GREENBAR, WHITEPRINT, STAR_CHART, CHART_RECORDER, CONTACT_SHEET, WASHI, AIRMAIL, STAMP_ALBUM, POSTER, STAVES,
    // Signs
    HAZARD, CONCRETE, TICKER,
    // Paintings
    OIL_PANEL, FRESCO_SKY, MISTY_DAWN, NOCTURNE, NOUVEAU, COLOR_FIELDS, RED_WEDGE, SURREAL_DUSK, BENDAY,
    DECO_FAN, INK_MIST, MEMPHIS,
    // Games
    NES, PIXEL_DUSK, POCKET, OUTRUN, PONG, HANDHELD, RPG_WINDOW, XMB,
}

/** Typeface family of the screen. Dots and pixels use the bundled 5×7 LED alphabet. */
enum class DisplayFace { SANS, CONDENSED, SERIF, MONO, ROUNDED, DOTS, PIXELS }

data class DisplayProfile(
    val material: DisplayMaterial,
    val motif: DisplayMotif,
    val background: Int,
    val ink: Int,
    val accent: Int,
    val muted: Int,
    val tint: Int,
    val face: DisplayFace,
) {
    val light: Boolean get() = ColorUtils.calculateLuminance(background or Color.BLACK) > .35
}

private fun opaque(color: Int) = color or Color.BLACK
private fun mix(a: Int, b: Int, t: Float) = opaque(ColorUtils.blendARGB(a, b, t))
private fun readable(color: Int, background: Int, minimum: Double) =
    readableForeground(opaque(color).toLong() and 0xffffffffL, opaque(background).toLong() and 0xffffffffL, minimum).toInt() or Color.BLACK

private fun profile(material: DisplayMaterial, motif: DisplayMotif, background: Int, ink: Int, accent: Int,
    face: DisplayFace, tint: Int = accent): DisplayProfile {
    val bg = opaque(background)
    val text = readable(ink, bg, 7.0)
    return DisplayProfile(material, motif, bg, text, readable(accent, bg, 4.5),
        readable(mix(text, bg, .30f), bg, 4.5), opaque(tint), face)
}

/** The theme plus the user's own accent colours, if they changed them. */
private class Seed(val theme: AppTheme, val accent: Long, val secondary: Long) {
    val font get() = theme.font
}
private fun Seed.tone(value: Double) = toneColor(accent, value).toInt() or Color.BLACK
private fun Seed.secondTone(value: Double) = toneColor(secondary, value).toInt() or Color.BLACK
private fun Seed.themeFace() = when (font) {
    ThemeFont.SERIF -> DisplayFace.SERIF
    ThemeFont.MONO -> DisplayFace.MONO
    ThemeFont.ROUNDED -> DisplayFace.ROUNDED
    ThemeFont.SANS -> DisplayFace.SANS
}

// Reusable screens, each tinted with the theme's own hue.
private fun Seed.oled(motif: DisplayMotif = DisplayMotif.ARTWORK, face: DisplayFace = themeFace()) =
    profile(DisplayMaterial.OLED, motif, mix(tone(10.0), Color.BLACK, .62f), mix(Color.WHITE, tone(90.0), .25f), tone(80.0), face, secondTone(62.0))
private fun Seed.lightLcd(motif: DisplayMotif = DisplayMotif.ARTWORK, face: DisplayFace = themeFace()) =
    profile(DisplayMaterial.LIGHT_LCD, motif, mix(Color.WHITE, tone(90.0), .42f), tone(12.0), tone(36.0), face, secondTone(55.0))
private fun Seed.ePaper(motif: DisplayMotif = DisplayMotif.NONE, face: DisplayFace = themeFace()) =
    profile(DisplayMaterial.E_PAPER, motif, mix(0xFFE6E4DC.toInt(), tone(88.0), .14f), 0xFF171717.toInt(), tone(34.0), face, tone(55.0))
private fun Seed.stationTft(face: DisplayFace = DisplayFace.CONDENSED) =
    profile(DisplayMaterial.STATION_TFT, DisplayMotif.NONE, mix(0xFF0A1A30.toInt(), tone(18.0), .40f), 0xFFF3F6FA.toInt(), tone(84.0), face, tone(70.0))
private fun Seed.chalk() =
    profile(DisplayMaterial.CHALKBOARD, DisplayMotif.NONE, mix(0xFF26302C.toInt(), tone(22.0), .18f), 0xFFEDEBE0.toInt(),
        mix(0xFFF2D88A.toInt(), tone(85.0), .35f), if (font == ThemeFont.MONO) DisplayFace.MONO else DisplayFace.ROUNDED, tone(80.0))
private fun Seed.lightbox() =
    profile(DisplayMaterial.LIGHTBOX, DisplayMotif.NONE, mix(0xFFFBFAF4.toInt(), tone(94.0), .18f), 0xFF141414.toInt(), tone(32.0), DisplayFace.CONDENSED, tone(60.0))

/** The single source of truth for the Display design. Unknown future themes fall back by brightness. */
fun displayProfile(t: AppTheme, colors: AppThemeColors = t.defaultColors()): DisplayProfile =
    Seed(t, colors.accent, colors.secondary).let { s -> collectionDisplay(t, s) ?: special(t, s) ?: grouped(t, s) }

/** A hand-made screen exists for this theme (painting, game, system window or a chosen material). */
internal fun hasCuratedDisplay(t: AppTheme): Boolean = Seed(t, t.accent, t.secondary).let { collectionDisplay(t, it) != null || special(t, it) != null }

// Build 110 · screens made of each collection's own material.
// Always in counter-brightness to the app (light design → dark screen, dark design → light screen).
private fun Seed.embroidery(): DisplayProfile = if (!theme.dark)
    profile(DisplayMaterial.EMBROIDERY, DisplayMotif.NONE, mix(0xFF22262E.toInt(), tone(16.0), .45f), mix(0xFFF4EBDD.toInt(), tone(92.0), .25f),
        secondTone(80.0), DisplayFace.DOTS, tone(70.0))
else profile(DisplayMaterial.EMBROIDERY, DisplayMotif.NONE, mix(0xFFF2EADB.toInt(), tone(94.0), .2f), mix(0xFF2A2522.toInt(), tone(22.0), .45f),
    tone(36.0), DisplayFace.DOTS, secondTone(50.0))
private fun Seed.engraved(): DisplayProfile = if (!theme.dark)
    profile(DisplayMaterial.ENGRAVED, DisplayMotif.NONE, mix(0xFF1A1A1F.toInt(), tone(14.0), .5f), mix(0xFFE9E4D8.toInt(), tone(90.0), .3f),
        secondTone(80.0), DisplayFace.SERIF, tone(60.0))
else profile(DisplayMaterial.ENGRAVED, DisplayMotif.NONE, mix(0xFFEAE6E0.toInt(), tone(92.0), .3f), mix(0xFF22201E.toInt(), tone(20.0), .4f),
    tone(32.0), DisplayFace.SERIF, secondTone(55.0))
/** Felt letter board: black felt with white letters for light designs, white felt with black letters for dark ones. */
private fun Seed.letterboard(): DisplayProfile = if (!theme.dark)
    profile(DisplayMaterial.LETTERBOARD, DisplayMotif.NONE, mix(0xFF1E1D1F.toInt(), tone(16.0), .45f), 0xFFF6F3EC.toInt(), tone(80.0),
        DisplayFace.CONDENSED, 0xFFA8743F.toInt())
else profile(DisplayMaterial.LETTERBOARD, DisplayMotif.NONE, mix(0xFFF4F1EA.toInt(), tone(95.0), .15f), 0xFF1C1B1A.toInt(), tone(35.0),
    DisplayFace.CONDENSED, 0xFFB98A55.toInt())
private fun Seed.game(motif: DisplayMotif, background: Int, ink: Int, accent: Int, tint: Int, face: DisplayFace = DisplayFace.PIXELS) =
    profile(DisplayMaterial.GAME, motif, background, ink, accent, face, tint)

private fun collectionDisplay(t: AppTheme, s: Seed): DisplayProfile? = when {
    t.name.startsWith("TEXTILE_") -> s.embroidery()
    t.name.startsWith("MINERAL_") -> s.engraved()
    t.name.startsWith("PET_") -> s.letterboard()
    t.name.startsWith("CAFE_") && t.dark -> s.letterboard()
    else -> when (t) {
        // Dark game designs get a light handheld or arcade screen, light ones a dark console screen.
        AppTheme.GAME_CONSOLE_NIGHT -> s.game(DisplayMotif.POCKET, 0xFF9BBC0F.toInt(), 0xFF0F380F.toInt(), 0xFF0F380F.toInt(), 0xFF306230.toInt())
        AppTheme.GAME_PIXEL_HERO -> s.game(DisplayMotif.POCKET, 0xFFFFD9E1.toInt(), 0xFF3A1030.toInt(), 0xFF8E1E4A.toInt(), 0xFFD9668A.toInt())
        AppTheme.GAME_HEX_STRATEGY -> s.game(DisplayMotif.RPG_WINDOW, 0xFF22311F.toInt(), 0xFFF3EFD8.toInt(), 0xFFE8C46A.toInt(), 0xFF5C7A3F.toInt())
        AppTheme.GAME_LOOT_GOLD -> s.game(DisplayMotif.POCKET, 0xFFE9D28A.toInt(), 0xFF2B1D05.toInt(), 0xFF6B4300.toInt(), 0xFFB07A1C.toInt())
        AppTheme.GAME_HANDHELD -> s.game(DisplayMotif.NES, 0xFF100E1C.toInt(), 0xFFF4F1FF.toInt(), 0xFFC8A6FF.toInt(), 0xFF7A52C7.toInt())
        AppTheme.GAME_COZY_FARM -> s.game(DisplayMotif.PIXEL_DUSK, 0xFF2D2245.toInt(), 0xFFFFF4E0.toInt(), 0xFFB8E07A.toInt(), 0xFFE08A5B.toInt())
        AppTheme.GAME_DUNGEON -> s.game(DisplayMotif.HANDHELD, 0xFFCFC6B4.toInt(), 0xFF1E1712.toInt(), 0xFF6E2A1A.toInt(), 0xFFB8562F.toInt(), DisplayFace.MONO)
        AppTheme.GAME_RHYTHM -> s.game(DisplayMotif.OUTRUN, 0xFFFFE9F3.toInt(), 0xFF2A0F4F.toInt(), 0xFFD1006F.toInt(), 0xFF00B7C7.toInt())
        AppTheme.GAME_PLATFORMER -> s.game(DisplayMotif.NES, 0xFF16206B.toInt(), Color.WHITE, 0xFFFFE36E.toInt(), 0xFFB13425.toInt())
        AppTheme.GAME_CARD_DUEL -> s.game(DisplayMotif.POCKET, 0xFFDCCFEE.toInt(), 0xFF22113A.toInt(), 0xFF5A2290.toInt(), 0xFF9A6CC9.toInt())
        else -> null
    }
}

/**
 * "Display": the curated screens above come first. Otherwise a theme with a design language
 * shows the real display technology of its language (LED matrix, flip-dot, split-flap, 7-segment,
 * VFD, TFT …) – but only where that screen contrasts with the app (light designs → dark screen,
 * dark designs → light e-ink or reflective LCD). Everything else gets the brightness fallback above.
 * Null = draw the [displayProfile] screen.
 */
fun displayTechnologyFor(t: AppTheme): BoardDisplayTechnology? {
    if (t.isOperatingSystem || hasCuratedDisplay(t)) return null
    val technology = t.display ?: t.language.defaultDisplay(t.dark) ?: t.themeLanguage.defaultDisplay(t.dark) ?: return null
    val lightScreen = technology == BoardDisplayTechnology.E_INK || technology == BoardDisplayTechnology.REFLECTIVE_LCD
    return technology.takeIf { lightScreen == t.dark }
}

private fun grouped(t: AppTheme, s: Seed): DisplayProfile = when (appThemeGroup(t)) {
    AppThemeGroup.CAFE -> if (t.dark) s.lightbox() else s.chalk()
    AppThemeGroup.ARCHITECTURE -> if (t.dark) s.lightbox() else s.stationTft()
    AppThemeGroup.CITY -> if (t.dark) s.lightLcd(DisplayMotif.NONE, DisplayFace.CONDENSED) else s.stationTft()
    AppThemeGroup.MINERALS, AppThemeGroup.TEXTILES, AppThemeGroup.NATURE, AppThemeGroup.CLEAN ->
        if (t.dark) s.ePaper(DisplayMotif.ARTWORK) else s.oled()
    AppThemeGroup.SEASONS -> if (t.dark) profile(DisplayMaterial.PAPER, DisplayMotif.STAVES, 0xFFF4EEDF.toInt(), 0xFF22201C.toInt(),
        s.tone(32.0), DisplayFace.SERIF, s.tone(45.0)) else s.oled()
    else -> if (t.dark) s.lightLcd() else s.oled()
}

private fun special(t: AppTheme, s: Seed): DisplayProfile? = when (t) {
    // Clean & reduced
    AppTheme.CLASSIC_DARK, AppTheme.OCEAN_LINE -> s.lightLcd(DisplayMotif.NONE)
    AppTheme.AMOLED_BLACK, AppTheme.QUIET_SLATE -> s.ePaper()
    AppTheme.OLED_HIGH_CONTRAST -> profile(DisplayMaterial.E_PAPER, DisplayMotif.NONE, 0xFFF1F0EA.toInt(), Color.BLACK, 0xFF3D5200.toInt(), DisplayFace.SANS)
    AppTheme.MONOCHROME -> profile(DisplayMaterial.OLED, DisplayMotif.NONE, 0xFF050505.toInt(), 0xFFF2F2EE.toInt(), 0xFFFFFFFF.toInt(), DisplayFace.MONO, 0xFF777777.toInt())
    AppTheme.MONOCHROME_INK -> profile(DisplayMaterial.E_PAPER, DisplayMotif.NONE, 0xFFEDEBE3.toInt(), 0xFF121212.toInt(), 0xFF121212.toInt(), DisplayFace.MONO)
    AppTheme.COOL_GRAPHITE, AppTheme.CLAY_MOSAIC, AppTheme.TEXTILE_LOOM -> s.stationTft()
    AppTheme.SWISS_METRO -> profile(DisplayMaterial.STATION_TFT, DisplayMotif.NONE, 0xFF0B0B0C.toInt(), Color.WHITE, 0xFFFF4B4B.toInt(), DisplayFace.CONDENSED, 0xFFEB0000.toInt())
    AppTheme.BRUTALIST -> profile(DisplayMaterial.LED_MATRIX, DisplayMotif.CONCRETE, 0xFF151515.toInt(), 0xFFFFB648.toInt(), 0xFFFFDE9C.toInt(), DisplayFace.DOTS, 0xFF5A5A55.toInt())
    AppTheme.INDUSTRIAL -> profile(DisplayMaterial.ENAMEL, DisplayMotif.HAZARD, 0xFFF2C200.toInt(), 0xFF141414.toInt(), 0xFF141414.toInt(), DisplayFace.CONDENSED, 0xFF141414.toInt())
    AppTheme.NEWSPRINT -> profile(DisplayMaterial.LED_MATRIX, DisplayMotif.TICKER, 0xFF0C0B09.toInt(), 0xFFFF9F2E.toInt(), 0xFFFFD27A.toInt(), DisplayFace.DOTS, 0xFF3A2A12.toInt())
    AppTheme.PARCHMENT, AppTheme.KRAFT_STUDIO, AppTheme.OAT_MILK -> s.chalk()
    AppTheme.SOLAR_MAP, AppTheme.COSMOS_NAVIGATOR -> profile(DisplayMaterial.VFD, DisplayMotif.NONE, 0xFF050A09.toInt(), 0xFFFFC56B.toInt(), 0xFFFFE2A6.toInt(), DisplayFace.MONO)
    // City & technology
    AppTheme.SPACE_TERMINAL -> profile(DisplayMaterial.PAPER, DisplayMotif.GREENBAR, 0xFFF4F6EE.toInt(), 0xFF1D2A20.toInt(), 0xFF2F6B3E.toInt(), DisplayFace.MONO, 0xFFCFE6CF.toInt())
    AppTheme.BLUEPRINT, AppTheme.ARCH_DRAFT -> profile(DisplayMaterial.PAPER, DisplayMotif.WHITEPRINT, 0xFFF3F6F8.toInt(), 0xFF173A66.toInt(), 0xFF1E5AA8.toInt(), DisplayFace.MONO, 0xFF7FA6D1.toInt())
    AppTheme.ORBIT_ATLAS, AppTheme.COSMOS_CHART -> profile(DisplayMaterial.PAPER, DisplayMotif.STAR_CHART, 0xFFF1EBDD.toInt(), 0xFF1C2433.toInt(), s.tone(35.0), DisplayFace.SERIF, 0xFF8C99AE.toInt())
    AppTheme.COPPER_CIRCUIT -> profile(DisplayMaterial.REFLECTIVE_LCD, DisplayMotif.NONE, 0xFFB4BAA6.toInt(), 0xFF1D241B.toInt(), 0xFF1D241B.toInt(), DisplayFace.MONO)
    AppTheme.CYBERPUNK_NEON -> profile(DisplayMaterial.LIGHT_LCD, DisplayMotif.NONE, 0xFFF4E500.toInt(), 0xFF0E0E10.toInt(), 0xFF005E73.toInt(), DisplayFace.MONO, 0xFF00C8E8.toInt())
    AppTheme.NIGHT_TRAIN, AppTheme.BLUE_HOUR -> s.lightLcd(DisplayMotif.NONE, DisplayFace.CONDENSED)
    AppTheme.SYNTHWAVE -> profile(DisplayMaterial.GAME, DisplayMotif.OUTRUN, 0xFFFFE6EE.toInt(), 0xFF2B0F55.toInt(), 0xFFC4005F.toInt(), DisplayFace.PIXELS, 0xFFFF7A59.toInt())
    // Art & eras: paintings
    AppTheme.REN_LUMEN -> profile(DisplayMaterial.PAINTING, DisplayMotif.OIL_PANEL, 0xFF2E2216.toInt(), 0xFFF1E3C2.toInt(), 0xFFE0B865.toInt(), DisplayFace.SERIF, 0xFF6B4A26.toInt())
    AppTheme.BAROQUE_INK -> profile(DisplayMaterial.PAINTING, DisplayMotif.FRESCO_SKY, 0xFFE9E3D6.toInt(), 0xFF3E1320.toInt(), 0xFF7A4A0E.toInt(), DisplayFace.SERIF, 0xFFC9A04F.toInt())
    AppTheme.ROMANTIC_HAZE -> profile(DisplayMaterial.PAINTING, DisplayMotif.MISTY_DAWN, 0xFFEDE6DC.toInt(), 0xFF1F2C39.toInt(), 0xFF7C3F22.toInt(), DisplayFace.SERIF, 0xFF8E9EB2.toInt())
    AppTheme.IMPRESSION_GARDEN -> profile(DisplayMaterial.PAINTING, DisplayMotif.NOCTURNE, 0xFF15303A.toInt(), 0xFFF3EDD6.toInt(), 0xFFF0CF6E.toInt(), DisplayFace.SERIF, 0xFF6FA38B.toInt())
    AppTheme.NOUVEAU_BOTANIC -> profile(DisplayMaterial.PAINTING, DisplayMotif.NOUVEAU, 0xFF143632.toInt(), 0xFFF2EAD3.toInt(), 0xFFDDBF74.toInt(), DisplayFace.SERIF, 0xFFC7A75A.toInt())
    AppTheme.EXPRESSION_VIVID -> profile(DisplayMaterial.PAINTING, DisplayMotif.COLOR_FIELDS, 0xFFF1E9D8.toInt(), 0xFF151A2E.toInt(), 0xFF1F3FA8.toInt(), DisplayFace.SANS, 0xFFE8742A.toInt())
    AppTheme.CONSTRUCT_RED -> profile(DisplayMaterial.PAINTING, DisplayMotif.RED_WEDGE, 0xFF1A1917.toInt(), 0xFFF0E7D3.toInt(), 0xFFFF5A47.toInt(), DisplayFace.CONDENSED, 0xFFD7322A.toInt())
    AppTheme.SURREAL_DREAM -> profile(DisplayMaterial.PAINTING, DisplayMotif.SURREAL_DUSK, 0xFF1D2442.toInt(), 0xFFF3ECFF.toInt(), 0xFFF5C77E.toInt(), DisplayFace.SERIF, 0xFF6E4A78.toInt())
    AppTheme.POP_PRINT, AppTheme.COMIC_POP -> profile(DisplayMaterial.PAINTING, DisplayMotif.BENDAY, 0xFF14152B.toInt(), Color.WHITE, 0xFFFFE14D.toInt(), DisplayFace.CONDENSED, 0xFFE8338E.toInt())
    AppTheme.ART_DECO -> profile(DisplayMaterial.PAINTING, DisplayMotif.DECO_FAN, 0xFFF3EBD8.toInt(), 0xFF141A18.toInt(), 0xFF135A4F.toInt(), DisplayFace.CONDENSED, 0xFFC09A45.toInt())
    AppTheme.INK_WASH -> profile(DisplayMaterial.PAINTING, DisplayMotif.INK_MIST, 0xFF1C1E1D.toInt(), 0xFFEEE9DC.toInt(), 0xFFE0675C.toInt(), DisplayFace.SERIF, 0xFF8A918D.toInt())
    AppTheme.MEMPHIS -> profile(DisplayMaterial.PAINTING, DisplayMotif.MEMPHIS, 0xFF141414.toInt(), Color.WHITE, 0xFFFFD84A.toInt(), DisplayFace.ROUNDED, 0xFFFF5C8A.toInt())
    // Decades & retro: games and period screens
    AppTheme.POCKET_CONSOLE -> profile(DisplayMaterial.GAME, DisplayMotif.NES, 0xFF0A0B1E.toInt(), 0xFFF4F4F4.toInt(), 0xFFF8B800.toInt(), DisplayFace.PIXELS, 0xFFB5651D.toInt())
    AppTheme.PIXEL_DAWN -> profile(DisplayMaterial.GAME, DisplayMotif.PIXEL_DUSK, 0xFF231642.toInt(), 0xFFFFF4E0.toInt(), 0xFFFFC66B.toInt(), DisplayFace.PIXELS, 0xFFD95B8A.toInt())
    AppTheme.RETRO_ARCADE -> profile(DisplayMaterial.GAME, DisplayMotif.POCKET, 0xFF9BBC0F.toInt(), 0xFF0F380F.toInt(), 0xFF0F380F.toInt(), DisplayFace.PIXELS, 0xFF306230.toInt())
    AppTheme.DECADE_70 -> profile(DisplayMaterial.GAME, DisplayMotif.PONG, 0xFF0B0B0B.toInt(), 0xFFF2F2F2.toInt(), 0xFFFFFFFF.toInt(), DisplayFace.PIXELS, 0xFF9A9A9A.toInt())
    AppTheme.DECADE_80 -> profile(DisplayMaterial.GAME, DisplayMotif.HANDHELD, 0xFFC9C7B3.toInt(), 0xFF1A1A17.toInt(), 0xFF1A1A17.toInt(), DisplayFace.MONO, 0xFFE58A5A.toInt())
    AppTheme.DECADE_90 -> profile(DisplayMaterial.GAME, DisplayMotif.RPG_WINDOW, 0xFF14227A.toInt(), Color.WHITE, 0xFFFFE27A.toInt(), DisplayFace.PIXELS, 0xFF3047B8.toInt())
    AppTheme.DECADE_00 -> profile(DisplayMaterial.GAME, DisplayMotif.XMB, 0xFF07142B.toInt(), 0xFFF2F7FF.toInt(), 0xFF9AD7FF.toInt(), DisplayFace.SANS, 0xFF3D7FD0.toInt())
    AppTheme.DECADE_10 -> s.oled(DisplayMotif.FLAT, DisplayFace.SANS)
    AppTheme.VINTAGE_TRANSIT -> profile(DisplayMaterial.SPLIT_FLAP, DisplayMotif.NONE, 0xFFECE4CF.toInt(), 0xFF1B1A17.toInt(), 0xFF8E2A1E.toInt(), DisplayFace.CONDENSED, 0xFF24221E.toInt())
    // Cosmos & research
    AppTheme.COSMOS_PULSAR -> profile(DisplayMaterial.PAPER, DisplayMotif.CHART_RECORDER, 0xFFF6F1EA.toInt(), 0xFF1E2421.toInt(), 0xFF9A2B3E.toInt(), DisplayFace.MONO, 0xFFE7B3BE.toInt())
    AppTheme.COSMOS_RADIO -> profile(DisplayMaterial.CRT, DisplayMotif.OSCILLOSCOPE, 0xFF040A06.toInt(), 0xFF8CFF9E.toInt(), 0xFFD5FFD9.toInt(), DisplayFace.MONO)
    AppTheme.COSMOS_PRISM -> s.oled(DisplayMotif.SPECTRUM)
    AppTheme.COSMOS_SPECTRUM -> s.lightLcd(DisplayMotif.SPECTRUM)
    AppTheme.COSMOS_ECLIPSE -> s.ePaper(DisplayMotif.ARTWORK)
    // Print & graphics
    AppTheme.PRINT_REGISTER -> profile(DisplayMaterial.PAPER, DisplayMotif.POSTER, 0xFF161616.toInt(), 0xFFF5F1E6.toInt(), 0xFFFF6A5C.toInt(), DisplayFace.MONO, 0xFF3AA6D9.toInt())
    AppTheme.PRINT_LINOCUT -> profile(DisplayMaterial.PAPER, DisplayMotif.POSTER, 0xFF14231F.toInt(), 0xFFF0EBDA.toInt(), 0xFF9BD3C6.toInt(), DisplayFace.SERIF, 0xFF2E5A50.toInt())
    AppTheme.PRINT_RISO -> profile(DisplayMaterial.PAPER, DisplayMotif.POSTER, 0xFF103B44.toInt(), 0xFFFFF1E2.toInt(), 0xFFFF8A9A.toInt(), DisplayFace.ROUNDED, 0xFFFF6C8A.toInt())
    AppTheme.PRINT_SOYINK -> profile(DisplayMaterial.PAPER, DisplayMotif.POSTER, 0xFF26261A.toInt(), 0xFFF2EFDF.toInt(), 0xFFE2D36A.toInt(), DisplayFace.SANS, 0xFF6E631F.toInt())
    AppTheme.PRINT_POSTAGE -> profile(DisplayMaterial.PAPER, DisplayMotif.STAMP_ALBUM, 0xFF17191C.toInt(), 0xFFF3ECDC.toInt(), 0xFFE7C77A.toInt(), DisplayFace.SERIF, 0xFF315D80.toInt())
    AppTheme.PRINT_DARKROOM -> profile(DisplayMaterial.PAPER, DisplayMotif.CONTACT_SHEET, 0xFFF7F6F2.toInt(), 0xFF141414.toInt(), 0xFFB0322A.toInt(), DisplayFace.MONO, 0xFF2A2A2A.toInt())
    AppTheme.PRINT_WOODBLOCK -> profile(DisplayMaterial.PAPER, DisplayMotif.WASHI, 0xFFF3EDDD.toInt(), 0xFF1B2940.toInt(), 0xFFB3402E.toInt(), DisplayFace.SERIF, 0xFF4E6D96.toInt())
    AppTheme.PRINT_AIRMAIL -> profile(DisplayMaterial.PAPER, DisplayMotif.AIRMAIL, 0xFFF7F3EA.toInt(), 0xFF1B2734.toInt(), 0xFF1D4E9E.toInt(), DisplayFace.MONO, 0xFFD23A33.toInt())
    AppTheme.MINERAL_TITANIUM, AppTheme.COSMOS_MARS -> s.lightLcd(DisplayMotif.NONE, DisplayFace.CONDENSED)
    else -> if (t.isOperatingSystem) osWindow(t) else null
}

/** System themes: the era's window chrome above a content area in the opposite brightness. */
private fun osWindow(t: AppTheme): DisplayProfile {
    fun os(bg: Long, ink: Long, accent: Long, face: DisplayFace = DisplayFace.SANS) =
        profile(DisplayMaterial.OS_WINDOW, DisplayMotif.NONE, bg.toInt(), ink.toInt(), accent.toInt(), face)
    return when (t) {
        AppTheme.OS_WIN_XP -> os(0xFF0B1834, 0xFFF2F6FF, 0xFF8EC0FF)
        AppTheme.OS_WIN_7 -> os(0xFF0B2547, 0xFFF1F7FF, 0xFF8FD3FF)
        AppTheme.OS_WIN_8 -> os(0xFFFFFFFF, 0xFF1D1D1D, 0xFF7A2E8E)
        AppTheme.OS_IOS_GLASS -> os(0xFF000000, 0xFFFFFFFF, 0xFF0A84FF)
        AppTheme.OS_IOS_4 -> os(0xFF121417, 0xFFF4F6F8, 0xFF8DB7E6)
        AppTheme.OS_WIN_95 -> os(0xFF000000, 0xFFC0C0C0, 0xFFFFFFFF, DisplayFace.MONO)
        AppTheme.OS_MAC_CLASSIC -> os(0xFF000000, 0xFFFFFFFF, 0xFFFFFFFF, DisplayFace.CONDENSED)
        AppTheme.OS_MAC_AQUA -> os(0xFF23272C, 0xFFF1F4F7, 0xFF6DB4FF)
        AppTheme.OS_MAC_LEOPARD -> os(0xFFFFFFFF, 0xFF1A1A1A, 0xFF1F63C9)
        AppTheme.OS_AMIGA -> os(0xFF0055AA, 0xFFFFFFFF, 0xFFFF8A00, DisplayFace.MONO)
        AppTheme.OS_ATARI -> os(0xFF000000, 0xFFF0F0F0, 0xFFFFFFFF, DisplayFace.MONO)
        AppTheme.OS_BEOS -> os(0xFF101010, 0xFFE8E8E8, 0xFFFFCB00, DisplayFace.MONO)
        AppTheme.OS_NEXTSTEP -> os(0xFF0C0C0C, 0xFFDADADA, 0xFFFFFFFF, DisplayFace.MONO)
        AppTheme.OS_OS2 -> os(0xFF000000, 0xFFC0C0C0, 0xFF55FFFF, DisplayFace.MONO)
        AppTheme.OS_ANDROID_HOLO -> os(0xFFF3F3F3, 0xFF222222, 0xFF0077A8)
        AppTheme.OS_UBUNTU -> os(0xFF300A24, 0xFFFFFFFF, 0xFFF26B3A, DisplayFace.MONO)
        AppTheme.OS_GNOME -> os(0xFF242424, 0xFFFFFFFF, 0xFF78AEED)
        AppTheme.OS_CDE -> os(0xFF1E3B44, 0xFFF4F4F4, 0xFFF0D78C, DisplayFace.MONO)
        else -> os(if (t.dark) 0xFFF4F4F4 else 0xFF141414, if (t.dark) 0xFF1A1A1A else 0xFFF4F4F4, t.accent)
    }
}
