// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

/**
 * A design language changes the structure of the app, not only its colours: frames,
 * shadows, shapes, typography, controls, ride bars and the departure board follow one set of rules.
 * Existing themes use [STANDARD] and render exactly as before. The name prefix of an [AppTheme]
 * assigns its language, so stored theme IDs never have to change.
 */
enum class ThemeLanguage(val prefix:String?) {
    STANDARD(null),
    /** Ink outlines, hard offset shadows without blur, flat poster colours. */
    NEO_BRUTAL("BRUTAL_"),
    /** One material, embossed by a light highlight top left and a soft shade bottom right. */
    SOFT_RELIEF("SOFT_"),
    /** Plump pastel forms with an inner light rim and a coloured soft shadow. */
    CLAY("KNETE_"),
    /** No filled surfaces: precise outlines on the ground colour. */
    OUTLINE("LINE_"),
    /** White tiles on a quiet ground, fine joints, large continuous corners. */
    BENTO("BENTO_"),
    /** One hue in finely graded tones; containers step through that hue. */
    TONAL("TONE_"),
    /** Saturated flat colour fields, no patterns, no gradients, no shadows. */
    COLOR_BLOCK("BLOCK_"),
    /** Warm neutrals, hairlines, plenty of space and one quiet gesture. */
    JAPANDI("JAPANDI_"),
    /** Pebble shapes with unequal radii and a few soft organic forms. */
    ORGANIC("ORGANIC_"),
    /** Dark product interfaces: hairline light edges and one luminous accent. */
    GLOW_EDGE("GLOW_");

    /** Frames draw their own shadow; the platform elevation shadow would blur or double it. */
    val ownsShadow:Boolean get()=this in setOf(NEO_BRUTAL,SOFT_RELIEF,CLAY,ORGANIC,GLOW_EDGE)
    /** Flat languages draw no shadow at all. */
    val flat:Boolean get()=this in setOf(OUTLINE,BENTO,COLOR_BLOCK,JAPANDI,TONAL)
}

/** Resolved once per theme; drawing code asks for the language on every frame. */
private val languageByTheme:Array<ThemeLanguage> by lazy {
    val prefixed=ThemeLanguage.entries.filter {it.prefix!=null}
    AppTheme.entries.map {theme->prefixed.firstOrNull {theme.name.startsWith(it.prefix!!)} ?: ThemeLanguage.STANDARD}.toTypedArray()
}

val AppTheme.themeLanguage:ThemeLanguage get()=languageByTheme[ordinal]

val AppTheme?.themeLanguageOrStandard:ThemeLanguage get()=this?.themeLanguage ?: ThemeLanguage.STANDARD

/**
 * Real display technology of the "Display" board for a ThemeLanguage design, always in
 * counter-brightness: light designs get an emitting screen, dark designs a light reflective one.
 */
fun ThemeLanguage.defaultDisplay(dark:Boolean):BoardDisplayTechnology?=when(this) {
    ThemeLanguage.STANDARD->null
    ThemeLanguage.NEO_BRUTAL->if(dark) BoardDisplayTechnology.E_INK else BoardDisplayTechnology.FLIP_DOT
    ThemeLanguage.SOFT_RELIEF,ThemeLanguage.TONAL->if(dark) BoardDisplayTechnology.E_INK else BoardDisplayTechnology.OLED
    ThemeLanguage.CLAY->if(dark) BoardDisplayTechnology.REFLECTIVE_LCD else BoardDisplayTechnology.SEGMENT_LED
    ThemeLanguage.OUTLINE->if(dark) BoardDisplayTechnology.E_INK else BoardDisplayTechnology.VFD
    ThemeLanguage.BENTO->if(dark) BoardDisplayTechnology.REFLECTIVE_LCD else BoardDisplayTechnology.TFT_LCD
    ThemeLanguage.COLOR_BLOCK->if(dark) BoardDisplayTechnology.REFLECTIVE_LCD else BoardDisplayTechnology.LED_MATRIX
    ThemeLanguage.JAPANDI->if(dark) BoardDisplayTechnology.E_INK else BoardDisplayTechnology.SPLIT_FLAP
    ThemeLanguage.ORGANIC->if(dark) BoardDisplayTechnology.E_INK else BoardDisplayTechnology.MICRO_LED
    ThemeLanguage.GLOW_EDGE->BoardDisplayTechnology.E_INK
}
