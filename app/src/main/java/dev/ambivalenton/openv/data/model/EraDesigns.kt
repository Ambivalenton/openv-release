// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model
import dev.ambivalenton.openv.localization.tr

/** App and board share artwork, palette and typography without coupling user selections. */
val eraBoardThemes:Map<BoardDesign,AppTheme> = mapOf(
    BoardDesign.MINT_SILENCE to AppTheme.MINT_SILENCE,
    BoardDesign.APRICOT_AIR to AppTheme.APRICOT_AIR,
    BoardDesign.IRIS_PAPER to AppTheme.IRIS_PAPER,
    BoardDesign.OCEAN_LINE to AppTheme.OCEAN_LINE,
    BoardDesign.KITE_POST to AppTheme.KITE_POST,
    BoardDesign.FIREFLY_NIGHT to AppTheme.FIREFLY_NIGHT,
    BoardDesign.CONFETTI_STUDIO to AppTheme.CONFETTI_STUDIO,
    BoardDesign.RIBBON_PLAY to AppTheme.RIBBON_PLAY,
    BoardDesign.REN_LUMEN to AppTheme.REN_LUMEN,
    BoardDesign.BAROQUE_INK to AppTheme.BAROQUE_INK,
    BoardDesign.ROMANTIC_HAZE to AppTheme.ROMANTIC_HAZE,
    BoardDesign.IMPRESSION_GARDEN to AppTheme.IMPRESSION_GARDEN,
    BoardDesign.NOUVEAU_BOTANIC to AppTheme.NOUVEAU_BOTANIC,
    BoardDesign.EXPRESSION_VIVID to AppTheme.EXPRESSION_VIVID,
    BoardDesign.CONSTRUCT_RED to AppTheme.CONSTRUCT_RED,
    BoardDesign.SURREAL_DREAM to AppTheme.SURREAL_DREAM,
    BoardDesign.POP_PRINT to AppTheme.POP_PRINT,
    BoardDesign.DECADE_70 to AppTheme.DECADE_70,
    BoardDesign.DECADE_80 to AppTheme.DECADE_80,
    BoardDesign.DECADE_90 to AppTheme.DECADE_90,
    BoardDesign.DECADE_00 to AppTheme.DECADE_00,
    BoardDesign.DECADE_10 to AppTheme.DECADE_10,
)
val eraTextures:Set<ThemeTexture> = setOf(
    ThemeTexture.KITES,
    ThemeTexture.FIREFLIES,
    ThemeTexture.CONFETTI,
    ThemeTexture.RIBBONS,
    ThemeTexture.RENAISSANCE,
    ThemeTexture.BAROQUE,
    ThemeTexture.ROMANTIC,
    ThemeTexture.IMPRESSION,
    ThemeTexture.NOUVEAU,
    ThemeTexture.EXPRESSION,
    ThemeTexture.CONSTRUCT,
    ThemeTexture.SURREAL,
    ThemeTexture.POP_ART,
    ThemeTexture.RETRO_70,
    ThemeTexture.RETRO_80,
    ThemeTexture.RETRO_90,
    ThemeTexture.RETRO_00,
    ThemeTexture.RETRO_10,
)
fun eraBoardPresets(design:BoardDesign):List<BoardColorPreset> = eraBoardThemes[design]?.let {theme->
    listOf(BoardColorPreset(tr("Original"),BoardColors(if(theme.dark)0xFFF8F3E7 else 0xFF202D32,theme.background,theme.accent)))
}.orEmpty()

/** Keep old paired IDs readable; expose one adaptive choice. */
fun normalizedBoardDesign(design:BoardDesign)=design.takeIf {it in supportedBoardDesigns}?:BoardDesign.APP_THEME
val visibleBoardDesigns:List<BoardDesign> get()=supportedBoardDesigns
