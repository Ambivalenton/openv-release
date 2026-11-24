// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

/**
 * A design language decides how surfaces are built – fill, outline, shadow, header,
 * background and typography – independently of the palette. The classic designs, the six motif
 * collections and the operating systems keep [MATERIAL]; every other design names its own language.
 * All of it is drawn with vectors; no images, downloads, timers or blur dependencies.
 */
enum class DesignLanguage(val titleKey:String) {
    /** Material 3 as before (classic, collections, operating systems). */
    MATERIAL("Material"),
    /** Flat tonal planes, no outline, no shadow. */
    FLAT("Flach & tonal"),
    /** Soft UI: surfaces in the background colour, shaped by a light and a dark soft shadow. */
    SOFT("Soft UI"),
    /** Claymorphism: puffy, rounded, inner highlight and a coloured soft shadow. */
    CLAY("Clay"),
    /** Glassmorphism: translucent planes, specular edge, colour glows behind. */
    GLASS("Glas"),
    /** Gradient edges and coloured glows on calm surfaces. */
    AURORA("Lichtkante"),
    /** Neo-brutalism: thick ink outline and a hard offset shadow. */
    BRUTAL("Neo-Brutalismus"),
    /** International typographic style: flat, accent stripe, heavy grotesk, strict grid. */
    SWISS("Swiss Style"),
    /** Magazine layout: open surfaces between fine rules, serif headlines. */
    EDITORIAL("Editorial"),
    /** Line UI: no fills, hairline outlines. */
    OUTLINE("Linien-UI"),
    /** Bento grid: contrasting tiles on a tinted ground, large radii, no shadow. */
    BENTO("Bento"),
    /** E-ink: greyscale, crisp black rules, no shadow. */
    INK("E-Ink"),
    /** Command line: dashed outlines, monospace, square. */
    TERMINAL("Terminal"),
    /** Neon: dark planes, glowing coloured outlines. */
    NEON("Neon"),
    /** 8-bit: stepped pixel corners, solid outline and a stepped shadow. */
    PIXEL("8-Bit"),
    /** Y2K chrome: glossy gradients, a highlight band, silver edges. */
    CHROME("Chrom"),
    /** Layered paper: stacked cut-paper shadows. */
    PAPER("Papierschichten"),
    /** Build 110 · cloth: woven surfaces, a stitched seam just inside every edge, soft fabric shadow. */
    STITCH("Naht & Stoff"),
}

enum class LanguageShadow { NONE, SOFT, NEUMORPHIC, CLAY, HARD, STEPPED, GLOW, LAYERED }
enum class LanguageBackdrop { PLAIN, TINT, GLOW, AURORA, HORIZON, CRESCENT, DOTS }
enum class LanguageHeader { NONE, HAIRLINE, RULE, DOUBLE, BLOCK, GRADIENT, DASHED, GLASS }
enum class LanguageFill { CONTAINER, BACKGROUND, GLASS, TINTED, SURFACE }

/** Static description of one language; colours always come from the active scheme. */
data class LanguageSpec(
    val shadow:LanguageShadow,
    /** Outline width in dp; 0 = none. */
    val border:Float,
    /** Outline in text ink (true) or a quiet outline colour (false). */
    val inkBorder:Boolean,
    val fill:LanguageFill,
    val backdrop:LanguageBackdrop,
    val header:LanguageHeader,
    /** Heading weight 100–900. */
    val headingWeight:Int,
    /** Heading tracking in em. */
    val headingTracking:Float,
    /** Label tracking in em. */
    val labelTracking:Float,
    val dashed:Boolean=false,
)

val DesignLanguage.spec:LanguageSpec get()=when(this) {
    DesignLanguage.MATERIAL->LanguageSpec(LanguageShadow.SOFT,0f,false,LanguageFill.CONTAINER,LanguageBackdrop.PLAIN,LanguageHeader.NONE,500,0f,0f)
    DesignLanguage.FLAT->LanguageSpec(LanguageShadow.NONE,0f,false,LanguageFill.TINTED,LanguageBackdrop.PLAIN,LanguageHeader.NONE,650,-.01f,.01f)
    DesignLanguage.SOFT->LanguageSpec(LanguageShadow.NEUMORPHIC,0f,false,LanguageFill.BACKGROUND,LanguageBackdrop.PLAIN,LanguageHeader.NONE,600,0f,.02f)
    DesignLanguage.CLAY->LanguageSpec(LanguageShadow.CLAY,0f,false,LanguageFill.TINTED,LanguageBackdrop.TINT,LanguageHeader.NONE,700,0f,.01f)
    DesignLanguage.GLASS->LanguageSpec(LanguageShadow.SOFT,1f,false,LanguageFill.GLASS,LanguageBackdrop.GLOW,LanguageHeader.GLASS,550,0f,.02f)
    DesignLanguage.AURORA->LanguageSpec(LanguageShadow.GLOW,1.5f,false,LanguageFill.CONTAINER,LanguageBackdrop.AURORA,LanguageHeader.GRADIENT,600,.005f,.03f)
    DesignLanguage.BRUTAL->LanguageSpec(LanguageShadow.HARD,2.5f,true,LanguageFill.TINTED,LanguageBackdrop.PLAIN,LanguageHeader.RULE,850,-.015f,.02f)
    DesignLanguage.SWISS->LanguageSpec(LanguageShadow.NONE,0f,false,LanguageFill.CONTAINER,LanguageBackdrop.PLAIN,LanguageHeader.BLOCK,800,-.025f,.06f)
    DesignLanguage.EDITORIAL->LanguageSpec(LanguageShadow.NONE,1f,true,LanguageFill.BACKGROUND,LanguageBackdrop.PLAIN,LanguageHeader.DOUBLE,700,-.01f,.08f)
    DesignLanguage.OUTLINE->LanguageSpec(LanguageShadow.NONE,1f,false,LanguageFill.BACKGROUND,LanguageBackdrop.PLAIN,LanguageHeader.HAIRLINE,450,.01f,.06f)
    DesignLanguage.BENTO->LanguageSpec(LanguageShadow.NONE,1f,false,LanguageFill.SURFACE,LanguageBackdrop.TINT,LanguageHeader.NONE,700,-.02f,.01f)
    DesignLanguage.INK->LanguageSpec(LanguageShadow.NONE,1.5f,true,LanguageFill.BACKGROUND,LanguageBackdrop.PLAIN,LanguageHeader.RULE,700,0f,.04f)
    DesignLanguage.TERMINAL->LanguageSpec(LanguageShadow.NONE,1f,false,LanguageFill.TINTED,LanguageBackdrop.PLAIN,LanguageHeader.DASHED,600,0f,.04f,dashed=true)
    DesignLanguage.NEON->LanguageSpec(LanguageShadow.GLOW,1.2f,false,LanguageFill.CONTAINER,LanguageBackdrop.GLOW,LanguageHeader.GRADIENT,600,.03f,.08f)
    DesignLanguage.PIXEL->LanguageSpec(LanguageShadow.STEPPED,2f,true,LanguageFill.TINTED,LanguageBackdrop.PLAIN,LanguageHeader.RULE,500,0f,.02f)
    DesignLanguage.CHROME->LanguageSpec(LanguageShadow.SOFT,1f,false,LanguageFill.CONTAINER,LanguageBackdrop.TINT,LanguageHeader.GLASS,700,0f,.02f)
    DesignLanguage.PAPER->LanguageSpec(LanguageShadow.LAYERED,0f,false,LanguageFill.CONTAINER,LanguageBackdrop.PLAIN,LanguageHeader.HAIRLINE,650,-.005f,.03f)
    DesignLanguage.STITCH->LanguageSpec(LanguageShadow.SOFT,0f,false,LanguageFill.TINTED,LanguageBackdrop.PLAIN,LanguageHeader.DASHED,650,0f,.03f,dashed=true)
}

/** Languages whose ride bars and time chips carry a solid ink contour (build 74 "graphic" borders). */
val DesignLanguage.graphicOutline:Boolean get()=this in setOf(DesignLanguage.BRUTAL,DesignLanguage.INK,DesignLanguage.PIXEL)

/** Default display technology of the adaptive departure board for each language. */
fun DesignLanguage.defaultDisplay(dark:Boolean):BoardDisplayTechnology?=when(this) {
    DesignLanguage.MATERIAL->null
    DesignLanguage.FLAT->BoardDisplayTechnology.OLED
    DesignLanguage.SOFT->if(dark)BoardDisplayTechnology.OLED else BoardDisplayTechnology.E_INK
    DesignLanguage.CLAY->BoardDisplayTechnology.REFLECTIVE_LCD
    DesignLanguage.GLASS->BoardDisplayTechnology.TFT_LCD
    DesignLanguage.AURORA->BoardDisplayTechnology.MICRO_LED
    DesignLanguage.BRUTAL->BoardDisplayTechnology.FLIP_DOT
    DesignLanguage.SWISS->BoardDisplayTechnology.SPLIT_FLAP
    DesignLanguage.EDITORIAL->BoardDisplayTechnology.E_INK
    DesignLanguage.OUTLINE->BoardDisplayTechnology.VFD
    DesignLanguage.BENTO->BoardDisplayTechnology.TFT_LCD
    DesignLanguage.INK->BoardDisplayTechnology.E_INK
    DesignLanguage.TERMINAL->BoardDisplayTechnology.MONO_CRT
    DesignLanguage.NEON->BoardDisplayTechnology.LED_MATRIX
    DesignLanguage.PIXEL->if(dark)BoardDisplayTechnology.LED_MATRIX else BoardDisplayTechnology.REFLECTIVE_LCD
    DesignLanguage.CHROME->BoardDisplayTechnology.TFT_LCD
    DesignLanguage.PAPER->BoardDisplayTechnology.SEGMENT_LED
    DesignLanguage.STITCH->if(dark)BoardDisplayTechnology.E_INK else BoardDisplayTechnology.VFD
}
