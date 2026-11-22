// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import dev.ambivalenton.openv.localization.tr

/** Groups for the font picker. Order is the order of the expandable sections. */
enum class AppFontCategory(private val titleKey:String,private val descriptionKey:String) {
    SANS("Serifenlos & modern","Klare Alltagsschriften für Bedienoberflächen."),
    SIGNAGE("Schmal & Beschilderung","Platzsparend wie auf Anzeigern und Wegweisern."),
    SERIF("Serifen & Buchdruck","Klassische Buch- und Zeitungsschriften."),
    MONO("Monospace & Technik","Gleich breite Zeichen wie am Terminal."),
    PIXEL("Pixel & Retro","Digitale Raster früher Bildschirme und Spiele."),
    HANDWRITING("Handschrift & Schwung","Geschrieben, gezeichnet und verbunden."),
    DISPLAY("Verspielt & Plakativ","Runde, kräftige Schriften mit viel Charakter."),
    LEGIBILITY("Lesehilfe","Für Erkennbarkeit und das Lesenlernen gestaltet.");
    val title:String get()=tr(titleKey)
    val description:String get()=tr(descriptionKey)
}

private val fontCategoryIds:Map<AppFontCategory,Set<String>> = mapOf(
    AppFontCategory.SANS to setOf("inter","roboto","opensans","sourcesans3","montserrat","lato","raleway","manrope",
        "dmsans","outfit","spacegrotesk","nunito","quicksand","worksans","rubik"),
    AppFontCategory.SIGNAGE to setOf("barlow","barlowcondensed","robotocondensed","oswald","bebasneue","overpass",
        "teko","rajdhani","sairacondensed"),
    AppFontCategory.SERIF to setOf("lora","merriweather","bitter","playfairdisplay","ebgaramond","librebaskerville",
        "cormorantgaramond","cinzel","oldstandardtt","bodonimoda","arvo"),
    AppFontCategory.MONO to setOf("sharetechmono","jetbrainsmono","ibmplexmono","spacemono","firacode","inconsolata"),
    AppFontCategory.PIXEL to setOf("pixelifysans","pressstart2p","vt323","silkscreen","dotgothic16"),
    AppFontCategory.HANDWRITING to setOf("caveat","dancingscript","greatvibes","pacifico","lobster","allura",
        "courgette","patrickhand","amaticsc"),
    AppFontCategory.DISPLAY to setOf("fredoka","baloo2","chelseamarket","bungee"),
    AppFontCategory.LEGIBILITY to setOf("atkinsonhyperlegiblenext","lexend","andika"),
)

/** Unknown future fonts land in the general sans-serif group instead of disappearing from the picker. */
fun appFontCategory(id:String):AppFontCategory=
    fontCategoryIds.entries.firstOrNull {id in it.value}?.key ?: AppFontCategory.SANS

fun groupedAppFonts(fonts:List<AppFontOption> = appFonts):List<Pair<AppFontCategory,List<AppFontOption>>> =
    AppFontCategory.entries.map {category->category to fonts.filter {appFontCategory(it.id)==category}}
        .filter {it.second.isNotEmpty()}
