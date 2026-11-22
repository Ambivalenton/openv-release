// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import kotlin.math.roundToInt

data class DesignSwatch(val nameKey:String,val color:Long)
data class DesignColorSet(val title:String,val swatches:List<DesignSwatch>,val role:DesignColorRole=DesignColorRole.ACCENT) {
    init {
        require(swatches.size==if(role==DesignColorRole.BACKGROUND || role==DesignColorRole.SURFACE) 5 else 10)
        require(swatches.map {it.color}.distinct().size==swatches.size)
    }
}
enum class DesignColorRole { ACCENT, BACKGROUND, SURFACE, TEXT }

/** Ten curated accents per family. Defaults and custom colours are separate controls, never extra grid cells. */
enum class DesignColorFamily(val colors:List<DesignSwatch>) {
    NEON(listOf(DesignSwatch("Neon-Pink",0xFFFF2D95),DesignSwatch("Cyber-Cyan",0xFF00E5F2),DesignSwatch("Magenta",0xFFEC4FDB),DesignSwatch("Deep Violet",0xFF925DFF),DesignSwatch("Laserblau",0xFF5C95FF),DesignSwatch("Electric Mint",0xFF66F7CE),DesignSwatch("Neon-Orange",0xFFFF8A35),DesignSwatch("Laser-Gelb",0xFFF5F251),DesignSwatch("Acid-Grün",0xFFB6F94E),DesignSwatch("Neon-Rot",0xFFFF4B53))),
    RETRO(listOf(DesignSwatch("Arcade-Koralle",0xFFFF9B82),DesignSwatch("Pixel-Mint",0xFF71F6C7),DesignSwatch("Spielhallen-Lila",0xFFBA95F8),DesignSwatch("Laserblau",0xFF64A1FF),DesignSwatch("Arcade-Gelb",0xFFFFE38F),DesignSwatch("Kirschrot",0xFFEA7185),DesignSwatch("Pixel-Orange",0xFFDB8B49),DesignSwatch("Arcade-Limette",0xFFB5C947),DesignSwatch("Türkis",0xFF45B0B1),DesignSwatch("Arcade-Tinte",0xFF414858))),
    AMBER(listOf(DesignSwatch("LED-Amber",0xFFFFA83E),DesignSwatch("Bernstein",0xFFFFD35A),DesignSwatch("Honig",0xFFF2B85A),DesignSwatch("Gold",0xFFF3CF78),DesignSwatch("Signalgelb",0xFFFFE692),DesignSwatch("Warmweiß",0xFFFFF1CD),DesignSwatch("Rost",0xFFB85C26),DesignSwatch("Lichtweiß",0xFFFFF9E4),DesignSwatch("Dunkelamber",0xFF9B6300),DesignSwatch("LED-Kupfer",0xFFDC8138))),
    MINT(listOf(DesignSwatch("Phosphor-Grün",0xFF8EFFA2),DesignSwatch("Neon-Mint",0xFFA3E4BE),DesignSwatch("Smaragd",0xFF65C4A3),DesignSwatch("Jade",0xFF7BD7C4),DesignSwatch("Eisgrün",0xFFD7FFE0),DesignSwatch("Moos",0xFF8CA66F),DesignSwatch("Terminal-Dunkelgrün",0xFF306047),DesignSwatch("Leuchtgrün",0xFFC5EF66),DesignSwatch("Terminal-Türkis",0xFF36AA99),DesignSwatch("Phosphor-Weiß",0xFFF1FFE3))),
    EARTH(listOf(DesignSwatch("Terrakotta",0xFF94543D),DesignSwatch("Lehm",0xFFD8A086),DesignSwatch("Kupfer",0xFFA77B24),DesignSwatch("Sand",0xFFBB9F71),DesignSwatch("Salbei",0xFF6D8164),DesignSwatch("Walnuss",0xFF463B36),DesignSwatch("Petrol",0xFF366D78),DesignSwatch("Altrosa",0xFFAC7189),DesignSwatch("Pflaume",0xFF73547C),DesignSwatch("Ocker",0xFFBC972F))),
    CANDY(listOf(DesignSwatch("Rosé",0xFFC26196),DesignSwatch("Lavendel",0xFF997ABD),DesignSwatch("Marshmallow",0xFFFFCCD9),DesignSwatch("Pistazie",0xFFB6DAC0),DesignSwatch("Zuckerblau",0xFF9BBFE9),DesignSwatch("Pfirsich",0xFFF2B399),DesignSwatch("Zuckerkoralle",0xFFE784A1),DesignSwatch("Zitronenbonbon",0xFFF5D37A),DesignSwatch("Zuckeriris",0xFF788FDB),DesignSwatch("Bonbon-Türkis",0xFF65ADAB))),
    ICE(listOf(DesignSwatch("Fjordblau",0xFF3E7B9D),DesignSwatch("Türkis",0xFF328E9B),DesignSwatch("Gletscher",0xFFA7D4E5),DesignSwatch("Nebelblau",0xFF7C9FB9),DesignSwatch("Flieder",0xFF9189AC),DesignSwatch("Schiefer",0xFF52697F),DesignSwatch("Eisrosa",0xFFC995B4),DesignSwatch("Eissalbei",0xFF7BAF95),DesignSwatch("Frostgold",0xFFB89B59),DesignSwatch("Eisviolett",0xFF665586))),
    INK(listOf(DesignSwatch("Tinte",0xFF202831),DesignSwatch("Graphit",0xFF4A4F55),DesignSwatch("Schiefer",0xFF666C74),DesignSwatch("Elfenbein",0xFFE9E3D5),DesignSwatch("Papier",0xFFF6F3EA),DesignSwatch("Silber",0xFFB6BEC3),DesignSwatch("Kohle",0xFF101214),DesignSwatch("Nebelgrau",0xFF83878A),DesignSwatch("Silbergrau",0xFFD2D5D5),DesignSwatch("Reinweiß",0xFFFFFFFF))),
    PRIMARY(listOf(DesignSwatch("Signalrot",0xFFB32532),DesignSwatch("Kobaltblau",0xFF2454A5),DesignSwatch("Sonnengelb",0xFFD5A327),DesignSwatch("Tannengrün",0xFF32705F),DesignSwatch("Violett",0xFF714B8E),DesignSwatch("Druckschwarz",0xFF22272E),DesignSwatch("Signalorange",0xFFD26A24),DesignSwatch("Türkis",0xFF217D8C),DesignSwatch("Himbeere",0xFFAA3972),DesignSwatch("Blattgrün",0xFF799335))),
    MEADOW(listOf(DesignSwatch("Waldgrün",0xFF264D39),DesignSwatch("Salbei",0xFF94A58F),DesignSwatch("Moos",0xFF7A8D37),DesignSwatch("Sand",0xFFDBC8A5),DesignSwatch("Borke",0xFF7C4C32),DesignSwatch("Blattgrün",0xFF459479),DesignSwatch("Wiesenblüte",0xFFC26E8E),DesignSwatch("Kornblume",0xFF57A0C1),DesignSwatch("Blütenlila",0xFF9C7BC3),DesignSwatch("Sonnenblume",0xFFD6AB36))),
    ORBIT(listOf(DesignSwatch("Sternenlila",0xFFC8B4ED),DesignSwatch("Nebeltürkis",0xFF8DCFD2),DesignSwatch("Mondlicht",0xFFEEE4CC),DesignSwatch("Abendrosa",0xFFD2A9C0),DesignSwatch("Orbitblau",0xFF8CA9DA),DesignSwatch("Kometengold",0xFFDFC28A),DesignSwatch("Marsrot",0xFFE78D76),DesignSwatch("Sternenorange",0xFFDFA05F),DesignSwatch("Polargrün",0xFF9EC57A),DesignSwatch("Nebelmagenta",0xFFB97DC5))),
    COPPER(listOf(DesignSwatch("Kupfer",0xFFCD956F),DesignSwatch("Roségold",0xFFE8B4A0),DesignSwatch("Patina",0xFF80BBA9),DesignSwatch("Messing",0xFFCEB46C),DesignSwatch("Pflaume",0xFFB68CAD),DesignSwatch("Tintenblau",0xFF8FA9BE),DesignSwatch("Oxidblau",0xFF6287B7),DesignSwatch("Rostrot",0xFFC56C5A),DesignSwatch("Dunkelpetrol",0xFF3F827C),DesignSwatch("Lichtgold",0xFFF1DA9A))),
    CITRUS(listOf(DesignSwatch("Limette",0xFF9EBE38),DesignSwatch("Blutorange",0xFFBB5034),DesignSwatch("Zitronengelb",0xFFDDC949),DesignSwatch("Blattgrün",0xFF487644),DesignSwatch("Mandarine",0xFFC37633),DesignSwatch("Pistazie",0xFF93A66B),DesignSwatch("Grapefruit",0xFFC86892),DesignSwatch("Blütenlila",0xFF8F6CAE),DesignSwatch("Himmelblau",0xFF5487B1),DesignSwatch("Petrol",0xFF387D81))),
    LCD(listOf(DesignSwatch("LCD-Grün",0xFF344F2B),DesignSwatch("Olivgrün",0xFF5A6635),DesignSwatch("Moos",0xFF7E8C50),DesignSwatch("Displaygrün",0xFF9AAB68),DesignSwatch("Lichtgrün",0xFFB8C785),DesignSwatch("Pistazie",0xFFD6DFAD),DesignSwatch("LCD-Schwarzgrün",0xFF172717),DesignSwatch("LCD-Dunkeloliv",0xFF424626),DesignSwatch("LCD-Salbei",0xFF8D9D79),DesignSwatch("LCD-Licht",0xFFF0F2D4))),
    STATION(listOf(DesignSwatch("Bahnhofsblau",0xFF7FABC8),DesignSwatch("Wegweisergelb",0xFFFFD563),DesignSwatch("Tafelweiß",0xFFF3F7FF),DesignSwatch("Eisblau",0xFFA8D2EB),DesignSwatch("Silber",0xFFC9D5DF),DesignSwatch("Warmweiß",0xFFFFF2D4),DesignSwatch("Signalrot",0xFFE7777D),DesignSwatch("Wegweisergrün",0xFF7BCCA8),DesignSwatch("Violett",0xFFB19ADC),DesignSwatch("Orange",0xFFECA565)));
}

fun appColorFamily(theme:AppTheme):DesignColorFamily=when(theme) {
    // Design languages: tonal themes follow their single hue, the others their collection.
    AppTheme.BRUTAL_POSTER,AppTheme.BRUTAL_KIOSK,AppTheme.BRUTAL_COPYSHOP,AppTheme.BRUTAL_MINT,AppTheme.BRUTAL_TANGERINE,AppTheme.BRUTAL_MONDAY,AppTheme.BRUTAL_LILAC,AppTheme.BRUTAL_GARDEN,AppTheme.BRUTAL_NIGHTSHIFT,AppTheme.BRUTAL_INDIGO,AppTheme.BENTO_PORCELAIN,AppTheme.BENTO_MATCHA,AppTheme.BENTO_SALMON,AppTheme.BENTO_LAVENDER,AppTheme.BENTO_SESAME,AppTheme.BENTO_SAKURA,AppTheme.BENTO_YUZU,AppTheme.BENTO_NORI,AppTheme.BENTO_INK,AppTheme.BENTO_UMAMI,AppTheme.BLOCK_COBALT,AppTheme.BLOCK_TANGERINE,AppTheme.BLOCK_FLAMINGO,AppTheme.BLOCK_MINT,AppTheme.BLOCK_VIOLET,AppTheme.BLOCK_SIGNAL,AppTheme.BLOCK_SUN,AppTheme.BLOCK_TEAL,AppTheme.BLOCK_SKY,AppTheme.BLOCK_FIR->DesignColorFamily.PRIMARY
    AppTheme.SOFT_PEARL,AppTheme.SOFT_SANDSTONE,AppTheme.SOFT_SAGE,AppTheme.SOFT_ROSEQUARTZ,AppTheme.SOFT_MIST,AppTheme.SOFT_LAVENDER,AppTheme.SOFT_BUTTERMILK,AppTheme.SOFT_GRAPHITE,AppTheme.SOFT_MIDNIGHT,AppTheme.SOFT_MOSS,AppTheme.TONE_OCEAN,AppTheme.TONE_PETROL->DesignColorFamily.ICE
    AppTheme.KNETE_BUBBLEGUM,AppTheme.KNETE_PISTACHIO,AppTheme.KNETE_PEACH,AppTheme.KNETE_LAGOON,AppTheme.KNETE_LILAC,AppTheme.KNETE_BANANA,AppTheme.KNETE_CLOUD,AppTheme.KNETE_CORAL,AppTheme.KNETE_NIGHT,AppTheme.KNETE_COCOA,AppTheme.TONE_ROSE,AppTheme.TONE_LAVENDER->DesignColorFamily.CANDY
    AppTheme.LINE_PENCIL,AppTheme.LINE_DRAFTING,AppTheme.LINE_REDPEN,AppTheme.LINE_FINELINER,AppTheme.LINE_INDIGO,AppTheme.LINE_CORAL,AppTheme.LINE_FERN,AppTheme.LINE_CHALK,AppTheme.LINE_NEONWIRE,AppTheme.LINE_AMBER,AppTheme.TONE_SLATE->DesignColorFamily.INK
    AppTheme.TONE_FOREST,AppTheme.ORGANIC_PEBBLE,AppTheme.ORGANIC_LEAF,AppTheme.ORGANIC_DROP,AppTheme.ORGANIC_POLLEN,AppTheme.ORGANIC_REEF,AppTheme.ORGANIC_FIG,AppTheme.ORGANIC_DUNE,AppTheme.ORGANIC_LICHEN,AppTheme.ORGANIC_TIDE,AppTheme.ORGANIC_NIGHTBLOOM->DesignColorFamily.MEADOW
    AppTheme.TONE_TERRACOTTA,AppTheme.JAPANDI_OAK,AppTheme.JAPANDI_STONE,AppTheme.JAPANDI_TEAHOUSE,AppTheme.JAPANDI_LINEN,AppTheme.JAPANDI_RICEPAPER,AppTheme.JAPANDI_MOSS,AppTheme.JAPANDI_CLAYBOWL,AppTheme.JAPANDI_AIZOME,AppTheme.JAPANDI_SUMI,AppTheme.JAPANDI_BAMBOO->DesignColorFamily.EARTH
    AppTheme.TONE_SAFFRON->DesignColorFamily.AMBER
    AppTheme.TONE_PLUM->DesignColorFamily.ORBIT
    AppTheme.TONE_EMBER->DesignColorFamily.COPPER
    AppTheme.GLOW_VIOLET,AppTheme.GLOW_CYAN,AppTheme.GLOW_LIME,AppTheme.GLOW_EMBER,AppTheme.GLOW_ROSE,AppTheme.GLOW_AZURE,AppTheme.GLOW_MINT,AppTheme.GLOW_GOLD,AppTheme.GLOW_ICE,AppTheme.GLOW_SIGNAL->DesignColorFamily.NEON
    AppTheme.COSMOS_CHART,AppTheme.COSMOS_NAVIGATOR,AppTheme.COSMOS_PULSAR,AppTheme.COSMOS_RADIO,AppTheme.COSMOS_ECLIPSE,AppTheme.COSMOS_CORONA,AppTheme.COSMOS_SPECTRUM,AppTheme.COSMOS_PRISM,AppTheme.COSMOS_SELENE,AppTheme.COSMOS_MARS->DesignColorFamily.ORBIT
    AppTheme.MINERAL_AMETHYST,AppTheme.MINERAL_FLUORITE,AppTheme.MINERAL_AGATE,AppTheme.MINERAL_MALACHITE,AppTheme.MINERAL_CARRARA,AppTheme.MINERAL_OBSIDIAN,AppTheme.MINERAL_TITANIUM,AppTheme.MINERAL_ROSEGOLD,AppTheme.MINERAL_TERRAZZO,AppTheme.MINERAL_BASALT->DesignColorFamily.COPPER
    AppTheme.TEXTILE_TARTAN,AppTheme.TEXTILE_PICNIC,AppTheme.TEXTILE_TWEED,AppTheme.TEXTILE_TAILOR,AppTheme.TEXTILE_SASHIKO,AppTheme.TEXTILE_EMBROIDERY,AppTheme.TEXTILE_LOOM,AppTheme.TEXTILE_DENIM,AppTheme.TEXTILE_IKAT,AppTheme.TEXTILE_SILK->DesignColorFamily.EARTH
    AppTheme.ARCH_ARCADE,AppTheme.ARCH_CLOISTER,AppTheme.ARCH_CUBES,AppTheme.ARCH_DRAFT,AppTheme.ARCH_COLONNADE,AppTheme.ARCH_PAVILION,AppTheme.ARCH_STAIRS,AppTheme.ARCH_NIGHTWALK,AppTheme.ARCH_GLAZING,AppTheme.ARCH_ATRIUM->DesignColorFamily.PRIMARY
    AppTheme.CAFE_ESPRESSO,AppTheme.CAFE_LATTE,AppTheme.CAFE_MATCHA,AppTheme.CAFE_HIBISCUS,AppTheme.CAFE_CROISSANT,AppTheme.CAFE_PATISSERIE,AppTheme.CAFE_RAMEN,AppTheme.CAFE_UDON,AppTheme.CAFE_CACAO,AppTheme.CAFE_PISTACHIO->DesignColorFamily.EARTH
    AppTheme.PRINT_REGISTER,AppTheme.PRINT_DARKROOM,AppTheme.PRINT_LINOCUT,AppTheme.PRINT_WOODBLOCK,AppTheme.PRINT_MOIRE,AppTheme.PRINT_INTERFERENCE,AppTheme.PRINT_RISO,AppTheme.PRINT_SOYINK,AppTheme.PRINT_POSTAGE,AppTheme.PRINT_AIRMAIL->DesignColorFamily.PRIMARY
    AppTheme.VIVALDI_PRIMAVERA_II,AppTheme.VIVALDI_PRIMAVERA_III,AppTheme.VIVALDI_AUTUNNO_III,AppTheme.VIVALDI_PRIMAVERA,AppTheme.FESTIVAL_NOWRUZ,AppTheme.FESTIVAL_YULE,AppTheme.RAINBOW_FERN,AppTheme.RAINBOW_MEADOW->DesignColorFamily.MEADOW
    AppTheme.VIVALDI_ESTATE,AppTheme.FESTIVAL_SONGKRAN,AppTheme.RAINBOW_CITRUS,AppTheme.RAINBOW_SOLAR->DesignColorFamily.CITRUS
    AppTheme.VIVALDI_AUTUNNO_II,AppTheme.VIVALDI_AUTUNNO->DesignColorFamily.EARTH
    AppTheme.VIVALDI_ESTATE_II,AppTheme.VIVALDI_ESTATE_III,AppTheme.VIVALDI_INVERNO_II,AppTheme.VIVALDI_INVERNO_III,AppTheme.VIVALDI_INVERNO,AppTheme.RAINBOW_SKY,AppTheme.RAINBOW_TIDAL->DesignColorFamily.ICE
    AppTheme.FESTIVAL_MUERTOS,AppTheme.FESTIVAL_HALLOWEEN,AppTheme.FESTIVAL_DIWALI,AppTheme.FESTIVAL_EID,AppTheme.FESTIVAL_HANUKKAH,AppTheme.RAINBOW_BERRY,AppTheme.RAINBOW_FLUID->DesignColorFamily.ORBIT
    AppTheme.FESTIVAL_HOLI,AppTheme.RAINBOW_CLASSIC->DesignColorFamily.PRIMARY
    AppTheme.FESTIVAL_VESAK,AppTheme.RAINBOW_SUNSET,AppTheme.RAINBOW_LILAC->DesignColorFamily.CANDY
    AppTheme.FESTIVAL_LUNAR,AppTheme.FESTIVAL_CHRISTMAS->DesignColorFamily.AMBER
    AppTheme.RAINBOW_MOON,AppTheme.RAINBOW_AMETHYST->DesignColorFamily.INK
    AppTheme.OS_WIN_XP,AppTheme.OS_WIN_7,AppTheme.OS_WIN_8,AppTheme.OS_IOS_GLASS,AppTheme.OS_IOS_4,AppTheme.OS_WIN_95,AppTheme.OS_MAC_CLASSIC,AppTheme.OS_MAC_AQUA,AppTheme.OS_MAC_LEOPARD,AppTheme.OS_AMIGA,AppTheme.OS_ATARI,AppTheme.OS_BEOS,AppTheme.OS_NEXTSTEP,AppTheme.OS_OS2,AppTheme.OS_ANDROID_HOLO,AppTheme.OS_UBUNTU,AppTheme.OS_GNOME,AppTheme.OS_CDE->if(theme.dark)DesignColorFamily.ORBIT else DesignColorFamily.ICE
    AppTheme.FROST_LAGOON,AppTheme.SMOKED_GLASS,AppTheme.PRISM_STUDIO,AppTheme.AQUARIUM->DesignColorFamily.ICE
    AppTheme.LILAC_GLASS,AppTheme.STICKER_CLUB->DesignColorFamily.CANDY
    AppTheme.AURORA_VEIL,AppTheme.JELLY_SPACE,AppTheme.ORBIT_ATLAS->DesignColorFamily.ORBIT
    AppTheme.LINEA_LIGHT->DesignColorFamily.PRIMARY
    AppTheme.QUIET_SLATE->DesignColorFamily.MEADOW
    AppTheme.OAT_MILK->DesignColorFamily.EARTH
    AppTheme.COOL_GRAPHITE->DesignColorFamily.INK
    AppTheme.TOY_CITY->DesignColorFamily.PRIMARY
    AppTheme.PIXEL_DAWN->DesignColorFamily.RETRO
    AppTheme.NEON_ORIGAMI->DesignColorFamily.NEON
    AppTheme.AQUA_ATLAS->DesignColorFamily.ICE
    AppTheme.NEWSPRINT->DesignColorFamily.INK
    AppTheme.PEACOCK->DesignColorFamily.ORBIT
    AppTheme.CLAY_MOSAIC->DesignColorFamily.EARTH
    AppTheme.BLUE_HOUR->DesignColorFamily.STATION
    AppTheme.ORIGAMI->DesignColorFamily.PRIMARY
    AppTheme.VELVET->DesignColorFamily.COPPER
    AppTheme.LIME_PUNCH->DesignColorFamily.CITRUS
    AppTheme.SYNTHWAVE,AppTheme.CYBERPUNK_NEON->DesignColorFamily.NEON
    AppTheme.RETRO_ARCADE->DesignColorFamily.RETRO
    AppTheme.VINTAGE_TRANSIT,AppTheme.SUNSET_POSTCARD,AppTheme.SOLAR_MAP->DesignColorFamily.AMBER
    AppTheme.SPACE_TERMINAL->DesignColorFamily.MINT
    AppTheme.POCKET_CONSOLE->DesignColorFamily.LCD
    AppTheme.FOREST,AppTheme.PARCHMENT,AppTheme.TERRACOTTA,AppTheme.KRAFT_STUDIO,AppTheme.PAPER_CUT->DesignColorFamily.EARTH
    AppTheme.BUBBLEGUM,AppTheme.LAVENDER_FIELDS,AppTheme.CANDY_CLOUD,AppTheme.CHERRY_SODA,AppTheme.FROSTED_BERRY->DesignColorFamily.CANDY
    AppTheme.NORDIC_ICE,AppTheme.BLUEPRINT,AppTheme.SEA_GLASS,AppTheme.PORCELAIN->DesignColorFamily.ICE
    AppTheme.AMOLED_BLACK,AppTheme.MONOCHROME,AppTheme.MONOCHROME_INK,AppTheme.BRUTALIST->DesignColorFamily.INK
    AppTheme.CLASSIC_LIGHT,AppTheme.INDUSTRIAL,AppTheme.SWISS_METRO,AppTheme.COMIC_POP,AppTheme.MEMPHIS->DesignColorFamily.PRIMARY
    AppTheme.BOTANICAL->DesignColorFamily.MEADOW
    AppTheme.CLASSIC_DARK,AppTheme.MIDNIGHT_ORBIT,AppTheme.NIGHT_TRAIN->DesignColorFamily.ORBIT
    AppTheme.ART_DECO,AppTheme.COPPER_CIRCUIT,AppTheme.INK_WASH->DesignColorFamily.COPPER
    AppTheme.OLED_HIGH_CONTRAST,AppTheme.CITRUS_CLUB->DesignColorFamily.CITRUS
    AppTheme.MINT_SILENCE,AppTheme.IMPRESSION_GARDEN,AppTheme.NOUVEAU_BOTANIC->DesignColorFamily.MEADOW
    AppTheme.APRICOT_AIR,AppTheme.REN_LUMEN,AppTheme.DECADE_70->DesignColorFamily.EARTH
    AppTheme.IRIS_PAPER,AppTheme.CONFETTI_STUDIO,AppTheme.KITE_POST->DesignColorFamily.CANDY
    AppTheme.OCEAN_LINE,AppTheme.DECADE_00,AppTheme.DECADE_10->DesignColorFamily.ICE
    AppTheme.FIREFLY_NIGHT,AppTheme.ROMANTIC_HAZE,AppTheme.SURREAL_DREAM->DesignColorFamily.ORBIT
    AppTheme.RIBBON_PLAY,AppTheme.DECADE_80->DesignColorFamily.NEON
    AppTheme.BAROQUE_INK->DesignColorFamily.COPPER
    AppTheme.EXPRESSION_VIVID,AppTheme.CONSTRUCT_RED,AppTheme.POP_PRINT->DesignColorFamily.PRIMARY
    AppTheme.DECADE_90->DesignColorFamily.RETRO
    // Gaming and pets.
    AppTheme.GAME_CONSOLE_NIGHT,AppTheme.GAME_ESPORTS,AppTheme.GAME_SPEEDRUN,AppTheme.GAME_RHYTHM->DesignColorFamily.NEON
    AppTheme.GAME_PIXEL_HERO,AppTheme.GAME_HANDHELD,AppTheme.GAME_ARCADE_CABINET->DesignColorFamily.RETRO
    AppTheme.GAME_HEX_STRATEGY,AppTheme.GAME_TAVERN,AppTheme.GAME_CARD_DUEL,AppTheme.PET_CAT_TABBY,AppTheme.PET_CAT_SIAMESE,AppTheme.PET_HORSE,AppTheme.PET_CAT_MAINE_COON,AppTheme.PET_DOG_PUG->DesignColorFamily.EARTH
    AppTheme.GAME_LOOT_GOLD,AppTheme.PET_DOG_GOLDEN,AppTheme.PET_HAMSTER,AppTheme.PET_GUINEA_PIG->DesignColorFamily.AMBER
    AppTheme.GAME_COZY_FARM,AppTheme.GAME_SANDBOX,AppTheme.PET_DOG_PARK,AppTheme.PET_TURTLE,AppTheme.PET_VET->DesignColorFamily.MEADOW
    AppTheme.GAME_DUNGEON,AppTheme.PET_DOG_DACHSHUND->DesignColorFamily.COPPER
    AppTheme.GAME_SPACE_SHOOTER,AppTheme.GAME_VR,AppTheme.PET_CAT_BLACK->DesignColorFamily.ORBIT
    AppTheme.GAME_PUZZLE,AppTheme.PET_CAT_CALICO,AppTheme.PET_CAT_NAP,AppTheme.PET_PUPPY->DesignColorFamily.CANDY
    AppTheme.GAME_RACING,AppTheme.GAME_PLATFORMER,AppTheme.GAME_BOARD_NIGHT->DesignColorFamily.PRIMARY
    AppTheme.GAME_STEALTH->DesignColorFamily.MINT
    AppTheme.GAME_WINTER_LEVEL,AppTheme.PET_DOG_HUSKY,AppTheme.PET_BUDGIE,AppTheme.PET_GOLDFISH->DesignColorFamily.ICE
    AppTheme.PET_DOG_DALMATIAN->DesignColorFamily.INK
    AppTheme.PET_RABBIT,AppTheme.PET_PARROT->DesignColorFamily.CITRUS
}

fun boardColorFamily(design:BoardDesign,app:AppTheme?):DesignColorFamily=when(design) {
    BoardDesign.COMIC->DesignColorFamily.PRIMARY
    BoardDesign.AIRPORT_GATE->DesignColorFamily.STATION
    BoardDesign.PUNCH_CARD->DesignColorFamily.EARTH
    BoardDesign.VINYL->DesignColorFamily.CANDY
    BoardDesign.PIXEL_QUEST->DesignColorFamily.RETRO
    BoardDesign.CHALKBOARD->DesignColorFamily.MEADOW
    BoardDesign.CLOCKWORK->DesignColorFamily.COPPER
    BoardDesign.PAPER_DIORAMA->DesignColorFamily.ICE
    BoardDesign.NIXIE->DesignColorFamily.AMBER
    BoardDesign.WAYFINDER->DesignColorFamily.PRIMARY
    BoardDesign.TRAM_ROLLSIGN,BoardDesign.RADAR->DesignColorFamily.MEADOW
    BoardDesign.PRINTED_SCHEDULE->DesignColorFamily.INK
    BoardDesign.DEPARTURE_CARDS->DesignColorFamily.ICE
    BoardDesign.DOT_MATRIX,BoardDesign.SEVEN_SEGMENT,BoardDesign.FLIP_DOT,BoardDesign.SPLIT_FLAP->DesignColorFamily.AMBER
    BoardDesign.CYBERPUNK->DesignColorFamily.NEON
    BoardDesign.DOS,BoardDesign.RETRO_CRT->DesignColorFamily.MINT
    BoardDesign.MODERN_MATRIX->DesignColorFamily.ICE
    BoardDesign.DB->DesignColorFamily.STATION
    BoardDesign.TELETEXT->DesignColorFamily.PRIMARY
    BoardDesign.PLAIN,BoardDesign.E_PAPER->DesignColorFamily.INK
    BoardDesign.NIGHT_SKY->DesignColorFamily.ORBIT
    BoardDesign.METRO_MAP->DesignColorFamily.MEADOW
    BoardDesign.DEPARTURE_TICKET->DesignColorFamily.EARTH
    BoardDesign.MATERIAL,BoardDesign.APP_THEME,BoardDesign.APP_INTEGRATED->app?.let(::appColorFamily) ?: DesignColorFamily.ICE
    else->eraBoardThemes[design]?.let(::appColorFamily) ?: DesignColorFamily.ICE
}

fun timelineColorFamily(theme:TimelineTheme,app:AppTheme?):DesignColorFamily=when(theme) {
    TimelineTheme.ORIGINAL,TimelineTheme.VIVID,TimelineTheme.SQUARED,TimelineTheme.METRO_RAILS,TimelineTheme.DOUBLE_RAIL,TimelineTheme.TRACK_SLEEPERS,TimelineTheme.SPINE,TimelineTheme.BRACKET,TimelineTheme.LOLLIPOP->DesignColorFamily.PRIMARY
    TimelineTheme.PASTEL,TimelineTheme.CAPSULE,TimelineTheme.BEADS,TimelineTheme.BRAID->DesignColorFamily.CANDY
    TimelineTheme.MONOCHROME,TimelineTheme.HALFTONE,TimelineTheme.MINIMAL->DesignColorFamily.INK
    TimelineTheme.OCEAN,TimelineTheme.OUTLINE,TimelineTheme.DOTTED,TimelineTheme.GLASS,TimelineTheme.GRADIENT->DesignColorFamily.ICE
    TimelineTheme.TICKET,TimelineTheme.RIBBON->DesignColorFamily.EARTH
    TimelineTheme.NEON->DesignColorFamily.NEON
    TimelineTheme.PIXEL,TimelineTheme.CHEVRON,TimelineTheme.SEQUENCE->DesignColorFamily.RETRO
    TimelineTheme.TERMINAL->DesignColorFamily.MINT
    TimelineTheme.DASHED->DesignColorFamily.AMBER
    TimelineTheme.MONET,TimelineTheme.APP_THEME->app?.let(::appColorFamily) ?: DesignColorFamily.ICE
}

private fun mixColor(color:Long,towards:Long,amount:Float):Long {
    fun channel(shift:Int)=(((color shr shift and 255)*(1f-amount)+(towards shr shift and 255)*amount).roundToInt().coerceIn(0,255)).toLong()
    return 0xff000000L or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}

/** Five deliberately spaced surfaces; the exact saved/default colour remains a separate action. */
fun designColors(title:String,family:DesignColorFamily,role:DesignColorRole,default:Long,dark:Boolean):DesignColorSet {
    val backgrounds=role==DesignColorRole.BACKGROUND || role==DesignColorRole.SURFACE
    val colors=if(backgrounds) {
        val neutrals=if(dark) listOf(0xff10141bL,0xff252d38L,0xff273c36L,0xff403044L,0xff443c2bL)
            else listOf(0xfffffdf8L,0xffe1e9f0L,0xffdce9dfL,0xffeaddebL,0xffe8dfcdL)
        val names=listOf("Designstandard","Kühler Grund","Grüner Grund","Violetter Grund","Warmer Grund")
        val inkNeutrals=if(dark) listOf(0xff070707L,0xff1b1b1bL,0xff303030L,0xff474747L,0xff606060L)
            else listOf(0xffffffffL,0xfff4f4f4L,0xffe3e3e3L,0xffccccccL,0xffb5b5b5L)
        val candidates=if(family==DesignColorFamily.INK) inkNeutrals.mapIndexed {i,c->DesignSwatch(names[i],c)}
            else listOf(DesignSwatch(names[0],default))+(1..4).map {i->
                DesignSwatch(names[i],mixColor(family.colors[i*2].color,neutrals[i],if(role==DesignColorRole.SURFACE) .78f else .86f))
            }
        // Include neutral fallback candidates so even an arbitrary user default cannot cause a duplicate.
        (candidates+neutrals.map {DesignSwatch("Neutraler Grund",it)}).distinctBy {it.color}.take(5)
    } else if(role==DesignColorRole.TEXT && family!=DesignColorFamily.INK) {
        family.colors.map {it.copy(color=mixColor(it.color,if(dark) 0xfff7f5efL else 0xff10151bL,.45f))}
    } else family.colors
    return DesignColorSet(title,colors,role)
}
fun paletteRole(key:String):DesignColorRole=when(key) {
    "background"->DesignColorRole.BACKGROUND;"surface"->DesignColorRole.SURFACE;"text"->DesignColorRole.TEXT;else->DesignColorRole.ACCENT
}
