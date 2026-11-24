// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

/**
 * Build 109 · a curated catalogue: designs that were near copies of a sibling leave the picker. Their
 * enum entries stay (stored settings, boards and tests refer to them by name), and every stored choice
 * moves to the closest kept design of the same brightness – usually from the same collection, so the
 * app looks almost as before. The successor is final: no kept design is ever mapped again.
 */
val retiredAppThemes: Map<AppTheme, AppTheme> = mapOf(
    // Klar & reduziert: Graphit and Monochrom were the same black-on-white ink.
    AppTheme.COOL_GRAPHITE to AppTheme.MONOCHROME,
    // Weich & taktil repeated the languages "Sanftes Relief" and "Knete & Clay"; it moves into them.
    AppTheme.MINT_SILENCE to AppTheme.SOFT_SAGE, AppTheme.APRICOT_AIR to AppTheme.SOFT_SANDSTONE,
    AppTheme.CANDY_CLOUD to AppTheme.SOFT_ROSEQUARTZ, AppTheme.PORCELAIN to AppTheme.SOFT_PEARL,
    AppTheme.LAVENDER_FIELDS to AppTheme.KNETE_CLOUD, AppTheme.CHERRY_SODA to AppTheme.KNETE_BUBBLEGUM,
    AppTheme.FROSTED_BERRY to AppTheme.SOFT_ROSEQUARTZ, AppTheme.MIDNIGHT_ORBIT to AppTheme.SOFT_MIDNIGHT,
    // Kante & Kontrast keeps Swiss, industry and comic; its brutalist trio lives in "Neo-Brutalismus".
    AppTheme.BRUTALIST to AppTheme.BRUTAL_COPYSHOP, AppTheme.CITRUS_CLUB to AppTheme.BRUTAL_KIOSK,
    AppTheme.LIME_PUNCH to AppTheme.BRUTAL_KIOSK,
    AppTheme.BRUTAL_TANGERINE to AppTheme.BRUTAL_POSTER, AppTheme.BRUTAL_LILAC to AppTheme.BRUTAL_MINT,
    AppTheme.BRUTAL_GARDEN to AppTheme.BRUTAL_KIOSK,
    AppTheme.SOFT_MIST to AppTheme.SOFT_PEARL, AppTheme.SOFT_LAVENDER to AppTheme.SOFT_ROSEQUARTZ,
    AppTheme.SOFT_BUTTERMILK to AppTheme.SOFT_SANDSTONE,
    AppTheme.KNETE_PEACH to AppTheme.KNETE_BUBBLEGUM, AppTheme.KNETE_LILAC to AppTheme.KNETE_CLOUD,
    AppTheme.KNETE_BANANA to AppTheme.KNETE_PISTACHIO, AppTheme.KNETE_CORAL to AppTheme.KNETE_LAGOON,
    AppTheme.LINE_FINELINER to AppTheme.LINE_PENCIL, AppTheme.LINE_INDIGO to AppTheme.LINE_DRAFTING,
    AppTheme.LINE_CORAL to AppTheme.LINE_REDPEN,
    AppTheme.BENTO_LAVENDER to AppTheme.BENTO_PORCELAIN, AppTheme.BENTO_SAKURA to AppTheme.BENTO_SALMON,
    AppTheme.BENTO_SESAME to AppTheme.BENTO_YUZU,
    AppTheme.TONE_ROSE to AppTheme.TONE_TERRACOTTA, AppTheme.TONE_LAVENDER to AppTheme.TONE_OCEAN,
    AppTheme.BLOCK_SIGNAL to AppTheme.BLOCK_VIOLET, AppTheme.BLOCK_TEAL to AppTheme.BLOCK_FIR,
    AppTheme.BLOCK_SKY to AppTheme.BLOCK_MINT,
    AppTheme.JAPANDI_LINEN to AppTheme.JAPANDI_OAK, AppTheme.JAPANDI_MOSS to AppTheme.JAPANDI_TEAHOUSE,
    AppTheme.JAPANDI_CLAYBOWL to AppTheme.JAPANDI_OAK,
    AppTheme.ORGANIC_POLLEN to AppTheme.ORGANIC_LEAF, AppTheme.ORGANIC_FIG to AppTheme.ORGANIC_REEF,
    AppTheme.ORGANIC_DUNE to AppTheme.ORGANIC_PEBBLE,
    AppTheme.GLOW_ROSE to AppTheme.GLOW_VIOLET, AppTheme.GLOW_MINT to AppTheme.GLOW_CYAN,
    AppTheme.GLOW_ICE to AppTheme.GLOW_AZURE,
    AppTheme.FROST_LAGOON to AppTheme.SEA_GLASS, AppTheme.LILAC_GLASS to AppTheme.PRISM_STUDIO,
    AppTheme.ORIGAMI to AppTheme.PAPER_CUT,
    AppTheme.ORBIT_ATLAS to AppTheme.NIGHT_TRAIN, AppTheme.NEON_ORIGAMI to AppTheme.CYBERPUNK_NEON,
    AppTheme.RIBBON_PLAY to AppTheme.JELLY_SPACE, AppTheme.KITE_POST to AppTheme.CONFETTI_STUDIO,
    // Collections: three or four near copies each.
    AppTheme.COSMOS_CORONA to AppTheme.COSMOS_NAVIGATOR, AppTheme.COSMOS_PRISM to AppTheme.COSMOS_NAVIGATOR,
    AppTheme.COSMOS_SELENE to AppTheme.COSMOS_NAVIGATOR,
    AppTheme.MINERAL_ROSEGOLD to AppTheme.MINERAL_AGATE, AppTheme.MINERAL_TERRAZZO to AppTheme.MINERAL_AGATE,
    AppTheme.MINERAL_BASALT to AppTheme.MINERAL_OBSIDIAN,
    AppTheme.TEXTILE_TAILOR to AppTheme.TEXTILE_DENIM, AppTheme.TEXTILE_EMBROIDERY to AppTheme.TEXTILE_PICNIC,
    AppTheme.TEXTILE_LOOM to AppTheme.TEXTILE_TWEED,
    AppTheme.ARCH_COLONNADE to AppTheme.ARCH_ARCADE, AppTheme.ARCH_STAIRS to AppTheme.ARCH_CUBES,
    AppTheme.ARCH_NIGHTWALK to AppTheme.ARCH_CLOISTER,
    AppTheme.CAFE_RAMEN to AppTheme.CAFE_ESPRESSO, AppTheme.CAFE_CACAO to AppTheme.CAFE_ESPRESSO,
    AppTheme.CAFE_CROISSANT to AppTheme.CAFE_LATTE,
    AppTheme.PRINT_AIRMAIL to AppTheme.PRINT_WOODBLOCK, AppTheme.PRINT_SOYINK to AppTheme.PRINT_LINOCUT,
    AppTheme.PRINT_INTERFERENCE to AppTheme.PRINT_DARKROOM,
    AppTheme.RAINBOW_SKY to AppTheme.RAINBOW_CLASSIC, AppTheme.RAINBOW_SUNSET to AppTheme.RAINBOW_CITRUS,
    AppTheme.RAINBOW_AMETHYST to AppTheme.RAINBOW_BERRY, AppTheme.RAINBOW_FERN to AppTheme.RAINBOW_TIDAL,
    AppTheme.RAINBOW_MEADOW to AppTheme.RAINBOW_TIDAL, AppTheme.RAINBOW_LILAC to AppTheme.RAINBOW_CLASSIC,
    // Bildschirm-Epochen: ten distinct eras remain.
    AppTheme.OS_IOS_GLASS to AppTheme.OS_WIN_7, AppTheme.OS_IOS_4 to AppTheme.OS_MAC_CLASSIC,
    AppTheme.OS_MAC_LEOPARD to AppTheme.OS_WIN_8, AppTheme.OS_ATARI to AppTheme.OS_MAC_CLASSIC,
    AppTheme.OS_NEXTSTEP to AppTheme.OS_CDE, AppTheme.OS_OS2 to AppTheme.OS_WIN_95,
    AppTheme.OS_UBUNTU to AppTheme.OS_MAC_AQUA, AppTheme.OS_GNOME to AppTheme.OS_MAC_AQUA,
    AppTheme.GAME_ESPORTS to AppTheme.GAME_CONSOLE_NIGHT, AppTheme.GAME_SPEEDRUN to AppTheme.GAME_PIXEL_HERO,
    AppTheme.GAME_SPACE_SHOOTER to AppTheme.GAME_CONSOLE_NIGHT, AppTheme.GAME_PUZZLE to AppTheme.GAME_PLATFORMER,
    AppTheme.GAME_RACING to AppTheme.GAME_HANDHELD, AppTheme.GAME_TAVERN to AppTheme.GAME_DUNGEON,
    AppTheme.GAME_SANDBOX to AppTheme.GAME_COZY_FARM, AppTheme.GAME_ARCADE_CABINET to AppTheme.GAME_LOOT_GOLD,
    AppTheme.GAME_STEALTH to AppTheme.GAME_DUNGEON, AppTheme.GAME_VR to AppTheme.GAME_RHYTHM,
    AppTheme.GAME_BOARD_NIGHT to AppTheme.GAME_HEX_STRATEGY, AppTheme.GAME_WINTER_LEVEL to AppTheme.GAME_PLATFORMER,
    AppTheme.PET_CAT_CALICO to AppTheme.PET_CAT_TABBY, AppTheme.PET_DOG_PARK to AppTheme.PET_DOG_GOLDEN,
    AppTheme.PET_PUPPY to AppTheme.PET_DOG_GOLDEN, AppTheme.PET_RABBIT to AppTheme.PET_CAT_TABBY,
    AppTheme.PET_HAMSTER to AppTheme.PET_DOG_GOLDEN, AppTheme.PET_BUDGIE to AppTheme.PET_GOLDFISH,
    AppTheme.PET_TURTLE to AppTheme.PET_GOLDFISH, AppTheme.PET_HORSE to AppTheme.PET_DOG_DACHSHUND,
    AppTheme.PET_GUINEA_PIG to AppTheme.PET_CAT_TABBY, AppTheme.PET_VET to AppTheme.PET_GOLDFISH,
    AppTheme.PET_CAT_MAINE_COON to AppTheme.PET_DOG_DACHSHUND, AppTheme.PET_DOG_PUG to AppTheme.PET_CAT_SIAMESE,
)

/** Taken out of the picker in build 109; see [retiredAppThemes]. */
val AppTheme.retired: Boolean get() = this in retiredAppThemes

/** The design shown for a stored choice: itself, or the kept design that replaced it. */
val AppTheme.successor: AppTheme get() = retiredAppThemes[this] ?: this

/** Own colours of the active design travel with it to its successor, unless that one has its own. */
private fun Map<AppTheme, AppThemeColors>.curated(active: AppTheme?): Map<AppTheme, AppThemeColors> {
    if (keys.none { it.retired }) return this
    val kept = filterKeys { !it.retired }.toMutableMap()
    if (active != null && active.retired) this[active]?.let { kept.putIfAbsent(active.successor, it) }
    return kept
}

fun AppearanceProfile.curatedThemes(): AppearanceProfile =
    if (appTheme?.retired != true && appThemeColors.keys.none { it.retired }) this
    else copy(appTheme = appTheme?.successor, appThemeColors = appThemeColors.curated(appTheme))

/** Every place a design is stored: the active one, its colours, the last random draw, day/night and quiet mode. */
fun AppSettings.curatedThemes(): AppSettings = copy(
    appTheme = appTheme?.successor,
    appThemeColors = appThemeColors.curated(appTheme),
    lastRandomTheme = lastRandomTheme?.successor,
    normalAppearance = normalAppearance?.curatedThemes(),
    quietAppearance = quietAppearance?.curatedThemes(),
    dayNight = dayNight.copy(other = dayNight.other?.curatedThemes()),
)
