// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model
import kotlinx.serialization.Serializable
import dev.ambivalenton.openv.localization.tr

@Serializable enum class QuoteDecoration(private val titleKey:String) {
    PLAIN("Ohne Rahmen"),STICKY_NOTE("Klebezettel"),GILDED("Goldrahmen"),PICTURE_FRAME("Bilderrahmen"),
    E_READER("E-Book-Reader"),BOOK_PAGE("Buchseite"),LETTER("Brief"),POSTCARD("Postkarte"),POLAROID("Sofortbild"),
    CORKBOARD("Pinnwand"),BLUEPRINT("Blaupause"),TICKET("Fahrkarte"),NEWSPAPER("Zeitungsseite"),TYPEWRITER("Schreibmaschine"),
    CHALKBOARD("Kreidetafel"),SCHOOL_NOTE("Schulheft"),PARCHMENT("Pergament"),FILE_CARD("Karteikarte"),JOURNAL("Reisetagebuch"),
    CARDBOARD("Paketpapier"),GLASS_CASE("Glasvitrine"),SLIDE("Filmstreifen"),STAMP("Briefmarke"),WOOD_SIGN("Holzschild"),MARBLE("Marmortafel"),
    MATERIAL_CARD("Material-Karte"),APP_DESIGN("App-Theme"),PINNED_CARD("Angepinnte Karte"),HAIRLINE("Feine Kontur"),ACCENT_RULE("Akzentlinie"),
    TONAL_PLATE("Tonfläche"),
    SOFT_OUTLINE("Weicher Rand"),
    TOP_RULE("Kopflinie"),
    BOTTOM_RULE("Unterstrichen"),
    SIDE_BRACKETS("Klammern"),
    CORNERS("Rahmenecken"),
    DOUBLE_RULE("Doppellinie"),
    CAPSULE_CARD("Kapsel"),
    SQUARE_CARD("Klare Kante"),
    OFFSET_CARD("Schwebend"),
    FROSTED_CARD("Mattglas"),
    INSET_CARD("Eingelassen"),
    DASH_OUTLINE("Gestrichelt"),
    SECTION_TAB("Register");
    val title get()=tr(titleKey)
    val description get()=tr(quoteDescriptions.getValue(this))
}

val orderedQuoteDecorations=listOf(QuoteDecoration.PLAIN)+QuoteDesignGroup.MINIMAL.designs+QuoteDesignGroup.REALISTIC.designs

enum class QuoteDesignGroup(private val titleKey:String,private val detailKey:String){
    MINIMAL("Minimalistisch","Klare Flächen, feine Konturen und moderne App-Farben."),
    REALISTIC("Realistisch","Papier, Karten und greifbare Rahmen mit Charakter.");
    val title get()=tr(titleKey)
    val description get()=tr(detailKey)
    val designs:List<QuoteDecoration> get()=when(this){
        MINIMAL->listOf(QuoteDecoration.APP_DESIGN,QuoteDecoration.MATERIAL_CARD,QuoteDecoration.PINNED_CARD,QuoteDecoration.HAIRLINE,QuoteDecoration.ACCENT_RULE,QuoteDecoration.TONAL_PLATE,QuoteDecoration.SOFT_OUTLINE,QuoteDecoration.TOP_RULE,QuoteDecoration.BOTTOM_RULE,QuoteDecoration.SIDE_BRACKETS,QuoteDecoration.CORNERS,QuoteDecoration.DOUBLE_RULE,QuoteDecoration.CAPSULE_CARD,QuoteDecoration.SQUARE_CARD,QuoteDecoration.OFFSET_CARD,QuoteDecoration.FROSTED_CARD,QuoteDecoration.INSET_CARD,QuoteDecoration.DASH_OUTLINE,QuoteDecoration.SECTION_TAB)
        REALISTIC->QuoteDecoration.entries.filter {it!=QuoteDecoration.PLAIN && it !in MINIMAL.designs}
    }
}

private val quoteDescriptions=mapOf(
    QuoteDecoration.PLAIN to "Der Text steht ganz für sich.",
    QuoteDecoration.STICKY_NOTE to "Ein Gedanke auf einem gelben Zettel.",
    QuoteDecoration.GILDED to "Goldene Leisten für kleine Kostbarkeiten.",
    QuoteDecoration.PICTURE_FRAME to "Warmes Holz und ein helles Passepartout.",
    QuoteDecoration.E_READER to "Ruhiges Papier in einem digitalen Buch.",
    QuoteDecoration.BOOK_PAGE to "Eine helle Seite mit weicher Buchkante.",
    QuoteDecoration.LETTER to "Ein persönlicher Gruß auf Briefpapier.",
    QuoteDecoration.POSTCARD to "Ein kleiner Gruß von unterwegs.",
    QuoteDecoration.POLAROID to "Ein Moment mit weißem Bildrand.",
    QuoteDecoration.CORKBOARD to "Papier und Pins auf warmem Kork.",
    QuoteDecoration.BLUEPRINT to "Feine Rasterlinien auf tiefem Blau.",
    QuoteDecoration.TICKET to "Ein Gedanke für die nächste Fahrt.",
    QuoteDecoration.NEWSPAPER to "Klare Spalten wie frisch gedruckt.",
    QuoteDecoration.TYPEWRITER to "Papier mit dem Charme alter Tasten.",
    QuoteDecoration.CHALKBOARD to "Helle Kreide auf dunkler Tafel.",
    QuoteDecoration.SCHOOL_NOTE to "Vertraute Linien wie im Schulheft.",
    QuoteDecoration.PARCHMENT to "Warmes Papier mit leisen Spuren.",
    QuoteDecoration.FILE_CARD to "Kleine Gedanken, ordentlich abgelegt.",
    QuoteDecoration.JOURNAL to "Eine Seite aus deinem Reisetagebuch.",
    QuoteDecoration.CARDBOARD to "Schlichtes Packpapier und etwas Klebeband.",
    QuoteDecoration.GLASS_CASE to "Ein heller Gedanke hinter Glas.",
    QuoteDecoration.SLIDE to "Ein kleiner Ausschnitt im Filmrahmen.",
    QuoteDecoration.STAMP to "Gezackter Rand für einen Reisegruß.",
    QuoteDecoration.WOOD_SIGN to "Ein warmer Wegweiser aus Holz.",
    QuoteDecoration.MARBLE to "Heller Stein mit feinen Adern.",
    QuoteDecoration.MATERIAL_CARD to "Klare Flächen, sanfte Tiefe.",
    QuoteDecoration.APP_DESIGN to "Farben und Formen deines App-Themes.",
    QuoteDecoration.PINNED_CARD to "Eine kleine Karte, leicht angepinnt.",
    QuoteDecoration.HAIRLINE to "Eine einzige, sehr feine Kontur.",
    QuoteDecoration.ACCENT_RULE to "Ein Farbstreifen als leiser Begleiter.",
    QuoteDecoration.TONAL_PLATE to "Eine leise Fläche in deiner Akzentfarbe.",
    QuoteDecoration.SOFT_OUTLINE to "Runde Ecken, eine feine Linie.",
    QuoteDecoration.TOP_RULE to "Ein klarer Akzent über deinem Text.",
    QuoteDecoration.BOTTOM_RULE to "Eine feine Linie trägt den Gedanken.",
    QuoteDecoration.SIDE_BRACKETS to "Zwei offene Seiten geben Halt.",
    QuoteDecoration.CORNERS to "Nur die Ecken setzen den Rahmen.",
    QuoteDecoration.DOUBLE_RULE to "Zwei feine Konturen, viel Ruhe.",
    QuoteDecoration.CAPSULE_CARD to "Ein weicher, durchgehender Bogen.",
    QuoteDecoration.SQUARE_CARD to "Gerade Linien ohne Verzierung.",
    QuoteDecoration.OFFSET_CARD to "Eine leichte Karte mit leisem Schatten.",
    QuoteDecoration.FROSTED_CARD to "Licht und Kontur statt schwerer Fläche.",
    QuoteDecoration.INSET_CARD to "Ein feiner Innenrand fasst den Text.",
    QuoteDecoration.DASH_OUTLINE to "Kleine Striche, ein offener Eindruck.",
    QuoteDecoration.SECTION_TAB to "Ein kleiner Reiter setzt den Akzent."
)
