// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model
import dev.ambivalenton.openv.localization.tr
enum class AppThemeGroup(private val titleKey:String,private val descriptionKey:String) {
    COSMOS("Kosmos & Forschung","Sternkarten, Spektren und Messkurven zwischen Observatorium und Labor."),
    MINERALS("Minerale & Metalle","Kristallschliff, Achatadern und gebürstetes Metall mit eigenem Materialgefühl."),
    TEXTILES("Textil & Muster","Gewebte Raster, Sashiko-Stiche und kräftige Stoffmuster zwischen Leinen und Denim."),
    ARCHITECTURE("Architektur & Räume","Arkaden, Treppen und räumliche Raster als ruhige grafische Räume."),
    CAFE("Kulinarik & Café","Espressoringe, Teezweige und Gebäcklinien in warmen und frischen Farbwelten."),
    PRINT("Druck & Grafik","Passermarken, Linolschnitt und Risografie mit klarer grafischer Handschrift."),
    SEASONS("Le quattro stagioni · Vivaldi","Vier Konzerte mit jeweils drei Sätzen: zwölf originale Titel und eigene Farbwelten."),
    FESTIVALS("Feste der Welt","Von Festen und Traditionen weltweit inspirierte Farben und Lichtmotive."),
    RAINBOWS("Regenbögen","Farbige Bänder, sanfte Übergänge und kleine Geschichten aus Licht."),
    SYSTEMS("Betriebssysteme","Legendäre Oberflächen mit eigenen Glas-, Kachel- und Fenster-Motiven neu interpretiert."),
    CLEAN("Klar & reduziert","Linien-UI, Bento-Kacheln, E-Ink und tonale Flächen: Struktur statt Dekor."),
    // Ten design languages, listed in this order directly after "Klar & reduziert".
    NEO_BRUTAL("Neo-Brutalismus","Kräftige Konturen, harte Schlagschatten und plakative Farbflächen ohne Verläufe."),
    SOFT_RELIEF("Sanftes Relief","Weich herausgeprägte Flächen aus einem einzigen Material, beleuchtet von links oben."),
    CLAY("Knete & Clay","Pralle, weich gerundete Formen in Pastell, mit sanftem Innenlicht wie aus Knete."),
    OUTLINE("Linie & Kontur","Nur Linien, keine Füllungen: präzise Konturen auf ruhigem Grund."),
    BENTO("Bento & Kacheln","Klar gegliederte Kacheln mit großen Radien, feinen Fugen und einer Akzentkachel."),
    TONAL("Ton in Ton","Eine einzige Farbe in fein abgestuften Tönen – ruhig, elegant und modern."),
    COLOR_BLOCK("Farbblock & Pop","Mutige, flache Farbflächen in fröhlichen Kombinationen – ohne Muster, ohne Verläufe."),
    JAPANDI("Japandi & Stille","Warme Naturtöne, viel Weißraum und eine einzelne ruhige Geste."),
    ORGANIC("Organische Formen","Weiche, unregelmäßige Rundungen und fließende Formen – wie Kiesel, Blätter und Tropfen."),
    GLOW_EDGE("Nacht & Leuchtkante","Tiefe dunkle Flächen, haarfeine Lichtkanten und ein einzelner leuchtender Akzent."),
    SOFT("Weich & taktil","Soft UI und Clay: erhabene Flächen, weiche Schatten und griffige runde Formen."),
    GLASS("Glas & Licht","Milchglas, Lichtkanten und Verlaufsglut über ruhigen Farbräumen."),
    BOLD("Kante & Kontrast","Neo-Brutalismus und Swiss Style: dicke Konturen, harte Schatten, strenge Raster."),
    NATURE("Natur & Weite","Organische Blattformen, Leuchtkanten und ein weiter Horizont in Naturfarben."),
    PAPER("Papier & Satz","Editorial-Satz, feine Regeln, Tinte und geschichtete Papierlagen."),
    CITY("Stadt & Technik","Terminal, Blaupause, Dashboard-Kacheln und Neon für die Nachtstadt."),
    PLAY("Spiel & Fantasie","Clay-Spielzeug, Sticker, Glasblasen und 8-Bit-Pixelecken."),
    ART("Kunst & Epochen","Kunstrichtungen als Designsprachen: vom Goldenen Schnitt bis zur Pop Art."),
    RETRO("Jahrzehnte & Retro","Von 70er-Clay über 80er-Neon und 90er-Pixel bis zum Flat Design der 2010er."),
    GAMING("Gaming & Spielwelten","Steuerkreuz, Pixelherzen, Hexfelder und Münzen – von der Konsole bis zum Strategiespiel."),
    PETS("Haustiere","Pfotenabdrücke, Wollknäuel, Knochen und Federn – Katzen, Hunde und ihre Freunde.");
    val title get()=tr(titleKey)
    val description get()=tr(descriptionKey)
}
/** One collection per design language, between "Klar & reduziert" and "Glas & Licht". */
val designLanguageThemeGroups = listOf(
    AppThemeGroup.NEO_BRUTAL, AppThemeGroup.SOFT_RELIEF, AppThemeGroup.CLAY, AppThemeGroup.OUTLINE,
    AppThemeGroup.BENTO, AppThemeGroup.TONAL, AppThemeGroup.COLOR_BLOCK, AppThemeGroup.JAPANDI,
    AppThemeGroup.ORGANIC, AppThemeGroup.GLOW_EDGE,
)
/** Group of a design language; [ThemeLanguage.STANDARD] has none. */
val ThemeLanguage.themeGroup:AppThemeGroup? get()=when(this) {
    ThemeLanguage.STANDARD->null
    ThemeLanguage.NEO_BRUTAL->AppThemeGroup.NEO_BRUTAL;ThemeLanguage.SOFT_RELIEF->AppThemeGroup.SOFT_RELIEF
    ThemeLanguage.CLAY->AppThemeGroup.CLAY;ThemeLanguage.OUTLINE->AppThemeGroup.OUTLINE
    ThemeLanguage.BENTO->AppThemeGroup.BENTO;ThemeLanguage.TONAL->AppThemeGroup.TONAL
    ThemeLanguage.COLOR_BLOCK->AppThemeGroup.COLOR_BLOCK;ThemeLanguage.JAPANDI->AppThemeGroup.JAPANDI
    ThemeLanguage.ORGANIC->AppThemeGroup.ORGANIC;ThemeLanguage.GLOW_EDGE->AppThemeGroup.GLOW_EDGE
}

/** The six collections stay together immediately above Vivaldi in the picker. */
val catalogThemeGroups = listOf(
    AppThemeGroup.COSMOS, AppThemeGroup.MINERALS, AppThemeGroup.TEXTILES,
    AppThemeGroup.ARCHITECTURE, AppThemeGroup.CAFE, AppThemeGroup.PRINT,
    AppThemeGroup.GAMING, AppThemeGroup.PETS,
)

/** The redesigned groups list their members explicitly, in the order of the picker. */
internal val languageGroupMembers:Map<AppThemeGroup,List<AppTheme>> = mapOf(
    AppThemeGroup.CLEAN to listOf(AppTheme.CLASSIC_LIGHT,AppTheme.CLASSIC_DARK,AppTheme.AMOLED_BLACK,AppTheme.LINEA_LIGHT,AppTheme.QUIET_SLATE,
        AppTheme.OAT_MILK,AppTheme.COOL_GRAPHITE,AppTheme.MONOCHROME,AppTheme.OLED_HIGH_CONTRAST,AppTheme.IRIS_PAPER,AppTheme.OCEAN_LINE),
    AppThemeGroup.SOFT to listOf(AppTheme.MINT_SILENCE,AppTheme.APRICOT_AIR,AppTheme.CANDY_CLOUD,AppTheme.PORCELAIN,AppTheme.LAVENDER_FIELDS,
        AppTheme.CHERRY_SODA,AppTheme.FROSTED_BERRY,AppTheme.MIDNIGHT_ORBIT),
    AppThemeGroup.GLASS to listOf(AppTheme.NORDIC_ICE,AppTheme.SEA_GLASS,AppTheme.FROST_LAGOON,AppTheme.LILAC_GLASS,AppTheme.SMOKED_GLASS,
        AppTheme.AURORA_VEIL,AppTheme.PRISM_STUDIO,AppTheme.BLUE_HOUR,AppTheme.PEACOCK),
    AppThemeGroup.BOLD to listOf(AppTheme.BRUTALIST,AppTheme.SWISS_METRO,AppTheme.COMIC_POP,AppTheme.INDUSTRIAL,AppTheme.CITRUS_CLUB,
        AppTheme.LIME_PUNCH,AppTheme.TERRACOTTA),
    AppThemeGroup.NATURE to listOf(AppTheme.FOREST,AppTheme.BOTANICAL,AppTheme.CLAY_MOSAIC,AppTheme.AQUA_ATLAS,AppTheme.FIREFLY_NIGHT,
        AppTheme.SUNSET_POSTCARD),
    AppThemeGroup.PAPER to listOf(AppTheme.PARCHMENT,AppTheme.MONOCHROME_INK,AppTheme.PAPER_CUT,AppTheme.KRAFT_STUDIO,AppTheme.INK_WASH,
        AppTheme.NEWSPRINT,AppTheme.ORIGAMI,AppTheme.VELVET),
    AppThemeGroup.CITY to listOf(AppTheme.BLUEPRINT,AppTheme.SOLAR_MAP,AppTheme.ORBIT_ATLAS,AppTheme.NIGHT_TRAIN,AppTheme.SPACE_TERMINAL,
        AppTheme.COPPER_CIRCUIT,AppTheme.CYBERPUNK_NEON,AppTheme.NEON_ORIGAMI),
    AppThemeGroup.PLAY to listOf(AppTheme.TOY_CITY,AppTheme.BUBBLEGUM,AppTheme.AQUARIUM,AppTheme.STICKER_CLUB,AppTheme.CONFETTI_STUDIO,
        AppTheme.KITE_POST,AppTheme.JELLY_SPACE,AppTheme.RIBBON_PLAY,AppTheme.PIXEL_DAWN),
    AppThemeGroup.ART to listOf(AppTheme.REN_LUMEN,AppTheme.BAROQUE_INK,AppTheme.ROMANTIC_HAZE,AppTheme.IMPRESSION_GARDEN,AppTheme.NOUVEAU_BOTANIC,
        AppTheme.EXPRESSION_VIVID,AppTheme.CONSTRUCT_RED,AppTheme.SURREAL_DREAM,AppTheme.POP_PRINT,AppTheme.ART_DECO,AppTheme.MEMPHIS),
    AppThemeGroup.RETRO to listOf(AppTheme.DECADE_70,AppTheme.DECADE_80,AppTheme.SYNTHWAVE,AppTheme.DECADE_90,AppTheme.DECADE_00,
        AppTheme.DECADE_10,AppTheme.RETRO_ARCADE,AppTheme.VINTAGE_TRANSIT,AppTheme.POCKET_CONSOLE),
)
private val languageGroupOf:Map<AppTheme,AppThemeGroup> = languageGroupMembers.flatMap {(group,themes)->themes.map {it to group}}.toMap()

fun appThemeGroup(t:AppTheme):AppThemeGroup=t.themeLanguage.themeGroup ?: when {
    t.name.startsWith("COSMOS_")->AppThemeGroup.COSMOS
    t.name.startsWith("MINERAL_")->AppThemeGroup.MINERALS
    t.name.startsWith("TEXTILE_")->AppThemeGroup.TEXTILES
    t.name.startsWith("ARCH_")->AppThemeGroup.ARCHITECTURE
    t.name.startsWith("CAFE_")->AppThemeGroup.CAFE
    t.name.startsWith("PRINT_")->AppThemeGroup.PRINT
    t.name.startsWith("GAME_")->AppThemeGroup.GAMING
    t.name.startsWith("PET_")->AppThemeGroup.PETS
    t.name.startsWith("VIVALDI_")->AppThemeGroup.SEASONS
    t.name.startsWith("FESTIVAL_")->AppThemeGroup.FESTIVALS
    t.name.startsWith("RAINBOW_")->AppThemeGroup.RAINBOWS
    t.name.startsWith("OS_")->AppThemeGroup.SYSTEMS
    else->languageGroupOf[t] ?: AppThemeGroup.CLEAN
}
fun groupedAppThemes(quiet:Boolean=false):List<Pair<AppThemeGroup,List<AppTheme>>> {
    val themes=if(quiet)quietAppThemes else AppTheme.entries
    val tail = listOf(AppThemeGroup.SYSTEMS) + catalogThemeGroups +
        listOf(AppThemeGroup.SEASONS, AppThemeGroup.RAINBOWS, AppThemeGroup.FESTIVALS)
    return (AppThemeGroup.entries.filter { it !in tail } + tail).map { group ->
        group to themes.filter { appThemeGroup(it) == group }
            .sortedBy { if (group == AppThemeGroup.SEASONS) vivaldiOrder(it)
                else languageGroupMembers[group]?.indexOf(it)?.takeIf { i -> i >= 0 } ?: it.ordinal }
    }.filter { it.second.isNotEmpty() }
}

private fun vivaldiOrder(t:AppTheme):Int {
    val season=when {t.name.contains("PRIMAVERA")->0;t.name.contains("ESTATE")->1;t.name.contains("AUTUNNO")->2;else->3}
    val movement=when {t.name.endsWith("_III")->2;t.name.endsWith("_II")->1;else->0}
    return season*3+movement
}
