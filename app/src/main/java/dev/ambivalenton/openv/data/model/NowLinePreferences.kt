// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import kotlinx.serialization.Serializable

/** Build 114 · the clock at the end of the bar chart's now line. */
@Serializable enum class NowClockStyle(val titleKey: String) {
    AUTO("Passend"), DIGITAL("Digital"), ANALOG("Analog"), BOTH("Beides"), NONE("Ohne Uhr")
}

/** Build 114 · how the now line is drawn. [AUTO] follows the bar chart (or app) design. */
@Serializable enum class NowStroke(val titleKey: String) {
    AUTO("Passend zum Design"),
    SOLID("Durchgezogen"), DASHED("Gestrichelt"), DOTTED("Gepunktet"), DASH_DOT("Strich-Punkt"),
    DOUBLE("Doppellinie"), RAIL("Gleis"), WAVE("Welle"), GLOW("Leuchtend"), FADE("Verlauf"),
    ARROWS("Pfeile"), PIXEL("Pixel"), STITCH("Naht"),
}

@Serializable enum class NowWeight(val titleKey: String, val factor: Float) { FINE("Fein", .6f), MEDIUM("Mittel", 1f), BOLD("Kräftig", 1.6f) }

@Serializable enum class NowColor(val titleKey: String) { ACCENT("Akzent"), SECONDARY("Zweitfarbe"), TEXT("Textfarbe") }

/**
 * Build 114 · the now line of the bar chart: shown or not, its clock, its stroke, weight and colour.
 * Defaults keep the former look in the standard designs (digital clock, dashed accent line).
 */
@Serializable data class NowLinePreferences(
    val visible: Boolean = true,
    val clock: NowClockStyle = NowClockStyle.AUTO,
    val stroke: NowStroke = NowStroke.AUTO,
    val weight: NowWeight = NowWeight.MEDIUM,
    val color: NowColor = NowColor.ACCENT,
    val seconds: Boolean = false,
)

/** What [NowLinePreferences] mean for one design: never AUTO. */
data class ResolvedNowLine(val clock: NowClockStyle, val stroke: NowStroke)

/**
 * The design's own now line: station clocks and fine rules for printed, Swiss, chrome and textile
 * looks, glowing lines for glass and neon, pixel steps for 8-bit, a track with sleepers for metro
 * rails. [language] is the app design's construction, used when the bar chart follows the app.
 */
fun nowLineFor(style: TimelineBarStyle, language: DesignLanguage?): ResolvedNowLine {
    val digital = NowClockStyle.DIGITAL; val analog = NowClockStyle.ANALOG
    if (style == TimelineBarStyle.APP_THEME) return when (language ?: DesignLanguage.MATERIAL) {
        DesignLanguage.MATERIAL -> ResolvedNowLine(digital, NowStroke.DASHED)
        DesignLanguage.FLAT, DesignLanguage.BENTO -> ResolvedNowLine(digital, NowStroke.SOLID)
        DesignLanguage.SOFT, DesignLanguage.CLAY -> ResolvedNowLine(analog, NowStroke.DOTTED)
        DesignLanguage.GLASS, DesignLanguage.NEON -> ResolvedNowLine(digital, NowStroke.GLOW)
        DesignLanguage.AURORA -> ResolvedNowLine(digital, NowStroke.FADE)
        DesignLanguage.BRUTAL -> ResolvedNowLine(digital, NowStroke.SOLID)
        DesignLanguage.SWISS, DesignLanguage.OUTLINE -> ResolvedNowLine(analog, NowStroke.SOLID)
        DesignLanguage.EDITORIAL, DesignLanguage.CHROME -> ResolvedNowLine(analog, NowStroke.DOUBLE)
        DesignLanguage.INK -> ResolvedNowLine(analog, NowStroke.DASHED)
        DesignLanguage.TERMINAL -> ResolvedNowLine(digital, NowStroke.DASHED)
        DesignLanguage.PIXEL -> ResolvedNowLine(digital, NowStroke.PIXEL)
        DesignLanguage.PAPER -> ResolvedNowLine(analog, NowStroke.DASH_DOT)
        DesignLanguage.STITCH -> ResolvedNowLine(analog, NowStroke.STITCH)
    }
    return when (style) {
        TimelineBarStyle.CLASSIC, TimelineBarStyle.DASHED, TimelineBarStyle.SEQUENCE, TimelineBarStyle.TERMINAL -> ResolvedNowLine(digital, NowStroke.DASHED)
        TimelineBarStyle.CAPSULE, TimelineBarStyle.DOTTED -> ResolvedNowLine(digital, NowStroke.DOTTED)
        TimelineBarStyle.SQUARED, TimelineBarStyle.BRACKET, TimelineBarStyle.MINIMAL -> ResolvedNowLine(digital, NowStroke.SOLID)
        TimelineBarStyle.OUTLINE -> ResolvedNowLine(analog, NowStroke.SOLID)
        TimelineBarStyle.METRO_RAILS, TimelineBarStyle.TRACK_SLEEPERS, TimelineBarStyle.SPINE -> ResolvedNowLine(analog, NowStroke.RAIL)
        TimelineBarStyle.DOUBLE_RAIL -> ResolvedNowLine(analog, NowStroke.DOUBLE)
        TimelineBarStyle.BEADS, TimelineBarStyle.HALFTONE, TimelineBarStyle.LOLLIPOP -> ResolvedNowLine(analog, NowStroke.DOTTED)
        TimelineBarStyle.TICKET -> ResolvedNowLine(analog, NowStroke.DASH_DOT)
        TimelineBarStyle.CHEVRON -> ResolvedNowLine(digital, NowStroke.ARROWS)
        TimelineBarStyle.RIBBON, TimelineBarStyle.BRAID -> ResolvedNowLine(analog, NowStroke.WAVE)
        TimelineBarStyle.GLASS, TimelineBarStyle.NEON -> ResolvedNowLine(digital, NowStroke.GLOW)
        TimelineBarStyle.GRADIENT -> ResolvedNowLine(digital, NowStroke.FADE)
        TimelineBarStyle.PIXEL -> ResolvedNowLine(digital, NowStroke.PIXEL)
        TimelineBarStyle.APP_THEME -> ResolvedNowLine(digital, NowStroke.DASHED)
    }
}

/** The preferences applied to a design: AUTO parts come from [nowLineFor]. */
fun NowLinePreferences.resolve(style: TimelineBarStyle, language: DesignLanguage?): ResolvedNowLine {
    val auto = nowLineFor(style, language)
    return ResolvedNowLine(if (clock == NowClockStyle.AUTO) auto.clock else clock, if (stroke == NowStroke.AUTO) auto.stroke else stroke)
}
