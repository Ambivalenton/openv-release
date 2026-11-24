// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.network

import dev.ambivalenton.openv.color.utilities.Hct
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.theme.readableForeground
import kotlin.math.abs

/*
 * Drawing parameters of the network plan designs (build 70). One renderer draws every design; a
 * design only changes the sheet, the line colours, line weight, corners, casing and the station mark.
 * Gestures, labels, departure times and vehicles stay the same in every design.
 */

/** How a favourite is drawn on the plan. Its symbol is always kept. */
internal enum class StationMark {
    /** The favourite's own shape and colour (Öpen-V). */
    FAVORITE,
    /** Paper-coloured shape, dark ring, symbol in the favourite's colour (London, Berlin …). */
    RING,
    /** Paper-coloured shape with a ring and symbol in the favourite's colour (Tokio, Seoul). */
    ACCENT_RING,
    /** Dark filled dot with a light symbol (New York). */
    INK,
    /** A tile in the colour of the station's first line with a light symbol (Mexiko-Stadt). */
    LINE_TILE,
}

/** What separates a line from the sheet and from lines it crosses. */
internal enum class LineCasing { NONE, PAPER,
    /** A dark edge (elevated structures, graphic designs). */
    INK }

/** How the line band itself is drawn. */
internal enum class LineStyle {
    SOLID,
    /** A tube: coloured edges, sheet-coloured core. */
    HOLLOW,
    /** Railway signature: coloured band with light sleepers. */
    RAIL,
    /** Two parallel strands. */
    DOUBLE,
    /** Neon tube: halo, coloured core and a bright centre. */
    GLOW,
    /** Build 110 · a ribbon with a running stitch down its middle (cloth designs). */
    STITCH,
}

/** The station outline of a design; null keeps the favourite's own shape. */
internal enum class StationShape { CIRCLE, SQUARE, TILE, DIAMOND }

/** How a line is put on the sheet (App-Theme design). */
internal enum class PlanStroke {
    /** A plain metro band. */
    SOLID,
    /** Square pixels on a coarse grid, like a sprite. */
    PIXEL,
    /** Oil or gouache: a wavering body with dry-brush bristles. */
    BRUSH,
    /** Impressionist dabs of light and dark along the line. */
    DABS,
    /** Sumi ink: a dense core with a soft bleed into the paper. */
    INK,
    /** Neon tube with a wide glow. */
    GLOW,
    /** Hand-drawn pencil line. */
    SKETCH,
    /** Translucent glass with a highlight. */
    GLASS,
    /** Printed-circuit trace with vias at the bends. */
    TRACE,
}

internal data class NetworkPlanLook(
    /** Sheet colour in light mode; null = the app theme's own surface (Öpen-V). */
    val paper: Long?,
    /** Line colours; null = the opponent colour of both favourites (Öpen-V). */
    val palette: List<Long>?,
    /** Line weight relative to the Öpen-V line. */
    val weight: Float,
    /** Corner radius in grid fields (0 = sharp corner). */
    val corner: Float,
    /** Free-flowing curves through the corners instead of octilinear bends. */
    val smooth: Boolean = false,
    val casing: LineCasing = LineCasing.NONE,
    val sheen: Boolean = false,
    /** Square line ends instead of round ones. */
    val butt: Boolean = false,
    val station: StationMark = StationMark.RING,
    /** Faint grid dots in live mode (edit mode always shows the grid). */
    val grid: Boolean = false,
    /** Dark ink for rings and dots in light mode. */
    val ink: Long = 0xFF1B1B1F,
    val line: LineStyle = LineStyle.SOLID,
    val stationShape: StationShape? = null,
    /** The sheet stays dark in light mode too (Dubai, neon app themes). */
    val night: Boolean = false,
    val stroke: PlanStroke = PlanStroke.SOLID,
    /** Paint the app theme's own motif (or system pane) on the sheet, like on the app's panels. */
    val motif: Boolean = false,
    /** Build 110 · material of sheet and stations (App-Theme design only). */
    val finish: PlanFinish = PlanFinish.PLAIN,
)

/* Line colours as published by the operators (rounded to sRGB); order = assignment order. */
private val londonColors = listOf(0xFFDC241F, 0xFF0019A8, 0xFF007D32, 0xFFFFD329, 0xFF000000, 0xFF0098D4, 0xFFB36305,
    0xFF9B0056, 0xFFA0A5A9, 0xFFF3A9BB, 0xFF95CDBA, 0xFF00A4A7)
private val newYorkColors = listOf(0xFFEE352E, 0xFF0039A6, 0xFF00933C, 0xFFFF6319, 0xFFFCCC0A, 0xFFB933AD, 0xFF6CBE45,
    0xFF996633, 0xFFA7A9AC, 0xFF00ADD0)
private val tokyoColors = listOf(0xFFF39700, 0xFFE60012, 0xFF9CAEB7, 0xFF00A7DB, 0xFF009944, 0xFFD7C447, 0xFF9B7CB6,
    0xFF00ADA9, 0xFFBB641D)
private val parisColors = listOf(0xFFFFCD00, 0xFF003CA6, 0xFF837902, 0xFFCF009E, 0xFFFF7E2E, 0xFF6ECA97, 0xFFFA9ABA,
    0xFFE19BDF, 0xFFB6BD00, 0xFFC9910D, 0xFF704B1C, 0xFF007852, 0xFF6EC4E8, 0xFF62259D)
private val berlinColors = listOf(0xFF7DAD4C, 0xFFDA421E, 0xFF16683D, 0xFFF0D722, 0xFF7E5330, 0xFF8C6DAB, 0xFF528DBA,
    0xFF224F86, 0xFFF3791D)
private val moscowColors = listOf(0xFFEF161E, 0xFF2DBE2C, 0xFF0078BE, 0xFF00BFFF, 0xFF8D5B2D, 0xFFED9121, 0xFF800080,
    0xFFFFD702, 0xFF999999, 0xFF99CC00, 0xFF82C0C0)
private val hongKongColors = listOf(0xFF007DC5, 0xFFE2231A, 0xFF00AB4E, 0xFFF7943E, 0xFF7D499D, 0xFF5EB6E4, 0xFF9A3B26,
    0xFFBAC429, 0xFF00888A, 0xFFF173AC)
private val washingtonColors = listOf(0xFFBF0D3E, 0xFFED8B00, 0xFF009CDE, 0xFF00B140, 0xFFFFD100, 0xFF919D9D)
private val mexicoColors = listOf(0xFFF04E98, 0xFF005EB8, 0xFFAF9800, 0xFF6BBBAE, 0xFFFFD100, 0xFFDA291C, 0xFFE87722,
    0xFF009A44, 0xFF512F2E, 0xFF981D97, 0xFFB0B3B2, 0xFFB0A32A)
private val seoulColors = listOf(0xFF0052A4, 0xFF00A84D, 0xFFEF7C1C, 0xFF00A5DE, 0xFF996CAC, 0xFFCD7C2F, 0xFF747F00,
    0xFFE6186C, 0xFFBDB092)
private val singaporeColors = listOf(0xFFD42E12, 0xFF009645, 0xFF9900AA, 0xFFFA9E0D, 0xFF005EC4, 0xFF9D5B25)
// Palettes, after the line colours published by the operators (rounded; extra suburban or
// tram colours complete the smaller networks to at least six).
private val madridColors = listOf(0xFF38A3DC, 0xFFE60003, 0xFFFFDF00, 0xFF8A5B2C, 0xFF96BF0D, 0xFFA3A3A3, 0xFFF8A11C,
    0xFFEF7EB5, 0xFF9D2E8F, 0xFF0E3193, 0xFF006A35, 0xFFA49A00)
private val chicagoColors = listOf(0xFFC60C30, 0xFF00A1DE, 0xFF62361B, 0xFF009B3A, 0xFFF9461C, 0xFF522398, 0xFFE27EA6, 0xFFF9E300)
private val lisbonColors = listOf(0xFF4E84C4, 0xFFFDB913, 0xFF00A9A6, 0xFFEE2E5D, 0xFF7D5BA6, 0xFFE87722, 0xFF6A8B3C)
private val shanghaiColors = listOf(0xFFE3002B, 0xFF8CC220, 0xFFFCD600, 0xFF461D84, 0xFF944D9A, 0xFFD40068, 0xFFED6F00,
    0xFF0094D8, 0xFF87CAED, 0xFFC6AFD4, 0xFF871C2B, 0xFF007A60, 0xFFE999C0, 0xFF616020, 0xFFBCA886, 0xFF98D1C0)
private val delhiColors = listOf(0xFFFFCB05, 0xFF0066B3, 0xFFEE1C25, 0xFF00A651, 0xFF822C8E, 0xFFE8519B, 0xFFC4007A,
    0xFFA6A6A6, 0xFFF58220)
private val saoPauloColors = listOf(0xFF0455A1, 0xFF007E5E, 0xFFEE372F, 0xFFFFD100, 0xFF9B3894, 0xFF9E9E9E, 0xFFCA016B,
    0xFF97A098, 0xFF01A9A7, 0xFF049FC3, 0xFFF68368, 0xFF133C8D)
private val buenosAiresColors = listOf(0xFF00ADD0, 0xFFE2231A, 0xFF0071BC, 0xFF008065, 0xFF6D2281, 0xFFFFD600, 0xFFF7931D)
private val stockholmColors = listOf(0xFF179E48, 0xFFE31F26, 0xFF0070BF, 0xFFEC619F, 0xFF8A2B8C, 0xFF778899, 0xFFF28C28)
private val taipeiColors = listOf(0xFFC48C31, 0xFFE3002C, 0xFF008659, 0xFFF8B61C, 0xFF0070BD, 0xFFFFDB00, 0xFF8246AF)
private val dubaiColors = listOf(0xFFE21836, 0xFF4CB848, 0xFF0072BC, 0xFFF7A800, 0xFF8E3A96, 0xFF00A9CE)
private val istanbulColors = listOf(0xFFE32D30, 0xFF009B48, 0xFF00A8E1, 0xFFF29FC5, 0xFF7B2C8D, 0xFFA97A50, 0xFFEB6EA6,
    0xFF3D63AE, 0xFFF7C300, 0xFF00ABA0)
private val budapestColors = listOf(0xFFFFD800, 0xFFE41F18, 0xFF005CA5, 0xFF4CA22F, 0xFF821066, 0xFF884200, 0xFFEE7203, 0xFFE50D6E)
private val osakaColors = listOf(0xFFE5171F, 0xFF522886, 0xFF0078BA, 0xFF019A66, 0xFFE44D93, 0xFF814721, 0xFFA9CC51,
    0xFFEE7B1A, 0xFF00B2E5)

/** The look as drawn: the App-Theme design is derived from the active theme, all others are fixed. */
internal fun NetworkPlanDesign.lookFor(theme: AppTheme?): NetworkPlanLook =
    if (this == NetworkPlanDesign.APP_THEME && theme != null) appThemePlanLook(theme).copy(finish = planFinishFor(theme)) else look

private val pixelThemes = setOf(AppTheme.POCKET_CONSOLE, AppTheme.PIXEL_DAWN, AppTheme.RETRO_ARCADE, AppTheme.DECADE_90)
private val neonThemes = setOf(AppTheme.SYNTHWAVE, AppTheme.CYBERPUNK_NEON, AppTheme.DECADE_80, AppTheme.NEON_ORIGAMI,
    AppTheme.SPACE_TERMINAL, AppTheme.COSMOS_PULSAR, AppTheme.PRINT_INTERFERENCE)

/**
 * The theme's colour family, corners, transparency and drawing technique. The technique
 * follows the theme's character: its former motif, its design group and its design language
 * (DesignLanguage of the redesigned groups, ThemeLanguage of the ten collections). Themes that keep a
 * plain line take line style, casing and station form from their design language.
 */
internal fun appThemePlanLook(t: AppTheme): NetworkPlanLook {
    val language = t.language
    val themeLanguage = t.themeLanguage
    val group = appThemeGroup(t)
    val stroke = when {
        t in pixelThemes || t.texture == ThemeTexture.PIXELS || language == DesignLanguage.PIXEL -> PlanStroke.PIXEL
        t.texture == ThemeTexture.IMPRESSION || t == AppTheme.IMPRESSION_GARDEN -> PlanStroke.DABS
        t.texture == ThemeTexture.INK || t == AppTheme.INK_WASH || t == AppTheme.PRINT_WOODBLOCK ||
            themeLanguage == ThemeLanguage.JAPANDI -> PlanStroke.INK
        t.texture in setOf(ThemeTexture.RENAISSANCE, ThemeTexture.BAROQUE, ThemeTexture.ROMANTIC, ThemeTexture.NOUVEAU,
            ThemeTexture.EXPRESSION, ThemeTexture.SURREAL, ThemeTexture.WATERCOLOR, ThemeTexture.POP_ART) ||
            group == AppThemeGroup.SEASONS || group == AppThemeGroup.ART -> PlanStroke.BRUSH
        t in neonThemes || t.texture == ThemeTexture.SCANLINES || language == DesignLanguage.NEON || language == DesignLanguage.AURORA ||
            themeLanguage == ThemeLanguage.GLOW_EDGE -> PlanStroke.GLOW
        t.texture in setOf(ThemeTexture.CIRCUITS, ThemeTexture.BLUEPRINT) || t in setOf(AppTheme.ARCH_DRAFT, AppTheme.BLUEPRINT,
            AppTheme.COPPER_CIRCUIT) || language == DesignLanguage.TERMINAL -> PlanStroke.TRACE
        t.glassOpacity < 1f || group == AppThemeGroup.GLASS || language == DesignLanguage.GLASS || language == DesignLanguage.CHROME -> PlanStroke.GLASS
        group in setOf(AppThemeGroup.PAPER, AppThemeGroup.CAFE) || t.texture in setOf(ThemeTexture.KRAFT,
            ThemeTexture.PAPER, ThemeTexture.COLLAGE, ThemeTexture.NEWSPRINT, ThemeTexture.POSTCARD) ||
            language == DesignLanguage.EDITORIAL || language == DesignLanguage.PAPER || themeLanguage == ThemeLanguage.OUTLINE -> PlanStroke.SKETCH
        else -> PlanStroke.SOLID
    }
    val corner = when {
        t.hasSquareCorners || stroke == PlanStroke.PIXEL || stroke == PlanStroke.TRACE ||
            t.shapeStyle == ThemeShape.SHARP || t.shapeStyle == ThemeShape.PIXEL -> 0f
        t.shapeStyle == ThemeShape.CUT || t.shapeStyle == ThemeShape.BEVEL -> .12f
        t.shapeStyle == ThemeShape.ASYMMETRIC || t.shapeStyle == ThemeShape.LEAF || t.shapeStyle == ThemeShape.PEBBLE -> .35f
        else -> (.35f + .45f * t.radiusScale.coerceIn(0f, 1.4f)).coerceAtMost(.95f)
    }
    val weight = when (stroke) {
        PlanStroke.PIXEL -> 1.15f; PlanStroke.GLOW -> .85f; PlanStroke.INK -> 1.25f; PlanStroke.TRACE -> .7f
        PlanStroke.SKETCH -> .8f; PlanStroke.DABS -> 1.3f; PlanStroke.BRUSH -> 1.2f
        else -> if (t.font == ThemeFont.ROUNDED) 1.15f else 1f
    }
    val base = NetworkPlanLook(
        paper = null,
        palette = appColorFamily(t).colors.map { it.color },
        weight = weight,
        corner = corner,
        smooth = stroke in setOf(PlanStroke.BRUSH, PlanStroke.DABS, PlanStroke.INK) ||
            (t.shapeStyle == ThemeShape.ROUNDED && t.font == ThemeFont.ROUNDED && stroke == PlanStroke.SOLID),
        casing = if (stroke in setOf(PlanStroke.SOLID, PlanStroke.GLASS, PlanStroke.TRACE)) LineCasing.PAPER else LineCasing.NONE,
        butt = stroke == PlanStroke.PIXEL || t.shapeStyle == ThemeShape.CUT,
        station = if (stroke == PlanStroke.TRACE) StationMark.RING else StationMark.FAVORITE,
        grid = stroke == PlanStroke.PIXEL || stroke == PlanStroke.TRACE,
        stroke = stroke,
        motif = true,
    )
    if (stroke != PlanStroke.SOLID) return base
    // Plain lines speak the design language: ink contours and square stations for poster designs,
    // hollow tubes for line designs, double strands for paper layers, soft fat lines for clay.
    if (themeLanguage == ThemeLanguage.NEO_BRUTAL || themeLanguage == ThemeLanguage.COLOR_BLOCK)
        return base.copy(casing = LineCasing.INK, butt = true, corner = 0f, stationShape = StationShape.SQUARE, weight = 1.3f)
    if (language == DesignLanguage.MATERIAL) return base
    val spoken = appThemeNetworkLook(t, t.accent, t.secondary, t.accent, t.dark)
    return base.copy(weight = spoken.weight, casing = spoken.casing, butt = base.butt || spoken.butt, line = spoken.line,
        station = spoken.station, stationShape = spoken.stationShape, corner = minOf(base.corner, maxOf(spoken.corner, .12f)))
}

internal val NetworkPlanDesign.look: NetworkPlanLook get() = when (this) {
    // Without a theme (system design) the App-Theme plan keeps a calm, rounded look in curated colours.
    NetworkPlanDesign.APP_THEME -> NetworkPlanLook(null, DesignColorFamily.PRIMARY.colors.map { it.color }, 1f, .6f,
        casing = LineCasing.PAPER, station = StationMark.FAVORITE, motif = true)
    NetworkPlanDesign.OPENV -> NetworkPlanLook(null, null, 1f, .5f, casing = LineCasing.PAPER, sheen = true,
        station = StationMark.FAVORITE, grid = true)
    NetworkPlanDesign.LONDON -> NetworkPlanLook(0xFFFFFFFF, londonColors, 1f, .2f, ink = 0xFF10161F)
    NetworkPlanDesign.NEW_YORK -> NetworkPlanLook(0xFFFBFBF8, newYorkColors, 1.35f, .32f, butt = true, station = StationMark.INK,
        ink = 0xFF111111)
    NetworkPlanDesign.TOKYO -> NetworkPlanLook(0xFFFFFFFF, tokyoColors, .9f, .3f, station = StationMark.ACCENT_RING)
    NetworkPlanDesign.PARIS -> NetworkPlanLook(0xFFF6F1E4, parisColors, .72f, .95f, ink = 0xFF232323)
    NetworkPlanDesign.BERLIN -> NetworkPlanLook(0xFFFFFFFF, berlinColors, 1.1f, .3f, casing = LineCasing.PAPER)
    NetworkPlanDesign.MOSCOW -> NetworkPlanLook(0xFFFFFFFF, moscowColors, 1.15f, .5f, smooth = true)
    NetworkPlanDesign.HONG_KONG -> NetworkPlanLook(0xFFFFFFFF, hongKongColors, 1.25f, .55f, casing = LineCasing.PAPER)
    NetworkPlanDesign.WASHINGTON -> NetworkPlanLook(0xFFFFFFFF, washingtonColors, 1.7f, .45f)
    NetworkPlanDesign.MEXICO_CITY -> NetworkPlanLook(0xFFFFFFFF, mexicoColors, 1.2f, .3f, station = StationMark.LINE_TILE)
    NetworkPlanDesign.SEOUL -> NetworkPlanLook(0xFFFFFFFF, seoulColors, .62f, .3f, station = StationMark.ACCENT_RING)
    NetworkPlanDesign.SINGAPORE -> NetworkPlanLook(0xFFFFFFFF, singaporeColors, 1.2f, .25f)
    NetworkPlanDesign.MADRID -> NetworkPlanLook(0xFFFFFFFF, madridColors, .85f, .15f, stationShape = StationShape.CIRCLE, ink = 0xFF1D1D1B)
    NetworkPlanDesign.CHICAGO -> NetworkPlanLook(0xFFF7F7F5, chicagoColors, 1.45f, 0f, casing = LineCasing.INK, butt = true,
        stationShape = StationShape.SQUARE, ink = 0xFF231F20)
    NetworkPlanDesign.LISBON -> NetworkPlanLook(0xFFF9F6EE, lisbonColors, 1.05f, .6f, smooth = true, station = StationMark.ACCENT_RING,
        stationShape = StationShape.CIRCLE)
    NetworkPlanDesign.SHANGHAI -> NetworkPlanLook(0xFFFFFFFF, shanghaiColors, .68f, .35f, station = StationMark.RING,
        stationShape = StationShape.TILE, grid = true)
    NetworkPlanDesign.DELHI -> NetworkPlanLook(0xFFFFFFFF, delhiColors, 1.2f, .45f, casing = LineCasing.PAPER,
        station = StationMark.ACCENT_RING, stationShape = StationShape.CIRCLE)
    NetworkPlanDesign.SAO_PAULO -> NetworkPlanLook(0xFFFFFFFF, saoPauloColors, 1.6f, .5f, casing = LineCasing.PAPER,
        station = StationMark.LINE_TILE, stationShape = StationShape.CIRCLE)
    NetworkPlanDesign.BUENOS_AIRES -> NetworkPlanLook(0xFFFFFFFF, buenosAiresColors, 1.55f, .3f, line = LineStyle.DOUBLE,
        stationShape = StationShape.CIRCLE)
    NetworkPlanDesign.STOCKHOLM -> NetworkPlanLook(0xFFF3F6F8, stockholmColors, 1.25f, .5f, line = LineStyle.HOLLOW)
    NetworkPlanDesign.TAIPEI -> NetworkPlanLook(0xFFFFFFFF, taipeiColors, 1.3f, .8f, casing = LineCasing.PAPER,
        station = StationMark.ACCENT_RING)
    NetworkPlanDesign.DUBAI -> NetworkPlanLook(0xFF0E1A2B, dubaiColors, 1.1f, .4f, line = LineStyle.GLOW,
        station = StationMark.ACCENT_RING, stationShape = StationShape.CIRCLE, night = true, ink = 0xFFEEF3F8)
    NetworkPlanDesign.ISTANBUL -> NetworkPlanLook(0xFFFFFFFF, istanbulColors, 1.05f, .25f, casing = LineCasing.PAPER,
        stationShape = StationShape.DIAMOND)
    NetworkPlanDesign.BUDAPEST -> NetworkPlanLook(0xFFFFFFFF, budapestColors, 1.25f, .1f, butt = true, station = StationMark.INK,
        stationShape = StationShape.CIRCLE, ink = 0xFF111111)
    NetworkPlanDesign.OSAKA -> NetworkPlanLook(0xFFFFFFFF, osakaColors, 1.2f, .25f, line = LineStyle.RAIL)
}

/**
 * Network look of the active app theme. Line colours are a harmonic set around the theme's
 * primary, secondary and tertiary hues (eight tones at map contrast); weight, corners, line style and
 * station marks follow the design language. Without a theme the classic look stands in.
 */
internal fun appThemeNetworkLook(theme: dev.ambivalenton.openv.data.model.AppTheme?, primary: Long, secondary: Long, tertiary: Long,
    dark: Boolean): NetworkPlanLook {
    val seeds = listOf(primary, secondary, tertiary).map { Hct.fromInt(it.toInt()) }
    val tone = if (dark) 72.0 else 48.0
    // The theme's own hues first, then their complements and the quarters between, each at least
    // 28° from the others: themes whose secondary equals their tertiary still get eight distinct lines.
    val p = seeds[0].hue
    val candidates = seeds.map { it.hue } + seeds.map { (it.hue + 180.0) % 360.0 } +
        listOf(90.0, 270.0, 45.0, 135.0, 225.0, 315.0, 22.5, 202.5, 112.5, 292.5).map { (p + it) % 360.0 }
    fun distance(a: Double, b: Double) = abs(((a - b + 540.0) % 360.0) - 180.0)
    val hues = mutableListOf<Double>()
    for (h in candidates) if (hues.size < 8 && hues.none { distance(it, h) < 28.0 }) hues += h
    val chroma = maxOf(seeds.maxOf { it.chroma }, 42.0)
    val palette = hues.map { h -> Hct.from(h, chroma, tone).toInt().toLong() and 0xFFFFFFFFL }
    val language = theme?.language ?: dev.ambivalenton.openv.data.model.DesignLanguage.MATERIAL
    val radius = (theme?.radiusScale ?: 1f).coerceIn(0f, 2f)
    val corner = when (theme?.shapeStyle) {
        dev.ambivalenton.openv.data.model.ThemeShape.SHARP, dev.ambivalenton.openv.data.model.ThemeShape.PIXEL -> 0f
        dev.ambivalenton.openv.data.model.ThemeShape.CUT, dev.ambivalenton.openv.data.model.ThemeShape.BEVEL -> .12f
        else -> (.18f + radius * .32f).coerceAtMost(.85f)
    }
    return when (language) {
        DesignLanguage.BRUTAL, DesignLanguage.PIXEL -> NetworkPlanLook(null, palette, 1.35f, 0f, casing = LineCasing.INK, butt = true, station = StationMark.RING,
            stationShape = StationShape.SQUARE)
        DesignLanguage.SWISS -> NetworkPlanLook(null, palette, 1.25f, 0f, butt = true, station = StationMark.INK, stationShape = StationShape.CIRCLE)
        DesignLanguage.EDITORIAL, DesignLanguage.INK -> NetworkPlanLook(null, palette, .8f, corner, line = LineStyle.HOLLOW, station = StationMark.RING)
        DesignLanguage.OUTLINE -> NetworkPlanLook(null, palette, .75f, corner, line = LineStyle.HOLLOW, station = StationMark.ACCENT_RING)
        DesignLanguage.TERMINAL -> NetworkPlanLook(null, palette, .8f, 0f, line = LineStyle.RAIL, station = StationMark.RING, stationShape = StationShape.SQUARE, grid = true)
        DesignLanguage.NEON, DesignLanguage.AURORA -> NetworkPlanLook(null, palette, 1f, corner, line = LineStyle.GLOW, station = StationMark.ACCENT_RING)
        DesignLanguage.GLASS, DesignLanguage.CHROME -> NetworkPlanLook(null, palette, 1.2f, corner, casing = LineCasing.PAPER, sheen = true, station = StationMark.FAVORITE)
        DesignLanguage.SOFT, DesignLanguage.CLAY -> NetworkPlanLook(null, palette, 1.45f, .85f, casing = LineCasing.PAPER, station = StationMark.FAVORITE)
        DesignLanguage.PAPER -> NetworkPlanLook(null, palette, 1.15f, corner, line = LineStyle.DOUBLE, station = StationMark.FAVORITE)
        DesignLanguage.STITCH -> NetworkPlanLook(null, palette, 1.35f, corner, line = LineStyle.STITCH, station = StationMark.FAVORITE)
        DesignLanguage.BENTO, DesignLanguage.FLAT -> NetworkPlanLook(null, palette, 1.2f, corner, station = StationMark.FAVORITE)
        DesignLanguage.MATERIAL -> NetworkPlanLook(null, palette, 1.05f, corner, casing = LineCasing.PAPER, station = StationMark.ACCENT_RING)
    }
}

/**
 * Colours for the lines in their stable list order. Each line takes the unused palette colour whose
 * hue is closest to its Öpen-V colour (the opponent colour of its two favourites), so the meaning
 * survives the change of design and neighbouring lines still differ. Only when every colour is taken
 * the palette starts again. [wanted] = ARGB per line or null (achromatic favourites).
 */
internal fun assignPaletteColors(wanted: List<Int?>, palette: List<Long>): List<Long> {
    if (palette.isEmpty()) return wanted.map { 0xFF000000 }
    val hues = palette.map { Hct.fromInt(it.toInt()) }
    val used = mutableSetOf<Int>()
    return wanted.map { argb ->
        if (used.size == palette.size) used.clear()
        val free = palette.indices.filter { it !in used }
        val target = argb?.let { Hct.fromInt(it) }?.takeIf { it.chroma >= 12.0 }
        val index = if (target == null) free.first() else free.minBy { i ->
            val h = hues[i]
            // Grey palette colours fit poorly to any wish; hue distance otherwise (0…180°).
            if (h.chroma < 12.0) 200.0 else abs(((h.hue - target.hue + 540.0) % 360.0) - 180.0)
        }
        used += index
        palette[index]
    }
}

/** A palette colour readable on the sheet: at least 3:1 (WCAG 1.4.11), lightened on dark sheets. */
internal fun readableLine(color: Long, paper: Int): Int =
    readableForeground(color and 0xFFFFFFFFL, paper.toLong() and 0xFFFFFFFFL, 3.0).toInt()
