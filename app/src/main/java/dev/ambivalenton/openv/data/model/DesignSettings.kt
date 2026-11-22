// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.data.model

import dev.ambivalenton.openv.localization.tr

import kotlinx.serialization.Serializable

@Serializable
enum class BoardDesign(private val titleKey: String, private val descriptionKey: String = "") {
    MATERIAL("Material Expressive", "Ruhige Material-Flächen und klare moderne Schrift"),
    DOT_MATRIX("Orange DOT Matrix", "Orange Punktmatrix auf dunklem LED-Raster"),
    DB("DB Anzeigetafel", "Weiße Schrift und gelbe Hinweise auf Bahnhofsblau"),
    SEVEN_SEGMENT("7-Segment Display", "Digitale Segmentziffern mit technischer Klarheit"),
    RETRO_LCD("Retro LCD", "Schwarze 7-Segment-Ziffern auf grauem Flüssigkristall-Display"),
    TELETEXT("Teletext / Videotext", "Blockpixel und kräftige Kontraste wie im Videotext"),
    DOS("DOS Text", "Monospace-Text im Stil eines alten Terminals"),
    PLAIN("Plain Text", "Reduzierte Textanzeige ohne zusätzliche Dekoration"),
    FLIP_DOT("Flip-Dot Vintage", "Gelbe Klapppunkte wie am alten Busbahnhof"),
    SPLIT_FLAP("Fallblatt Klassisch", "Mechanische Bahnhofstafel mit geteilten Zeilen"),
    E_PAPER("E-Ink Minimalist", "Ruhiges Schwarzweiß mit klaren Trennlinien"),
    NIGHT_SKY("Nachtfahrt", "Leuchtende Abfahrten unter einem kleinen Sternenhimmel"),
    METRO_MAP("Linienplan", "Haltepunkte und Linienband statt starrer Tabellen"),
    DEPARTURE_TICKET("Fahrkarte", "Papier, Lochrand und Abfahrten zum Mitnehmen"),
    MODERN_MATRIX("Modernes Matrix-LED", "Cyanfarbene LED-Pixel auf tiefschwarzem Raster"),
    RETRO_CRT("Retro CRT-Monitor (VDV)", "Grüner Monospace-Text mit feinen Scanlines"),
    CYBERPUNK("Cyberpunk Neon-Grid", "Cyanes Gitternetz, pinke Akzente und dunkle Flächen"),
    NIXIE("Nixie-Röhren","Warme Ziffernröhren mit dunklem Glas und Kupferlicht"),
    WAYFINDER("Wegweiser","Markante Richtungspfeile, klare Zeilen und gelbe Signale"),
    TRAM_ROLLSIGN("Straßenbahn-Rollband","Helle Schrift auf grünem Rollband mit großen Liniennummern"),
    PRINTED_SCHEDULE("Papierfahrplan","Serifenschrift, Spalten und feine Linien wie am Haltestellenaushang"),
    DEPARTURE_CARDS("Abfahrtskarten","Gestapelte blaue Karten mit gefalteten Zeitmarken"),
    RADAR("Sonar-Anzeige","Leuchtende Fahrten vor konzentrischen Radarlinien"),
    COMIC("Comic-Express","Sprechblasen, versetzte Schatten und ein feines Comic-Punktraster"),
    AIRPORT_GATE("Flughafen-Gate","Große Gate-Marken und präzise Abflugtafeln für die nächste Fahrt"),
    PUNCH_CARD("Lochkarten-Archiv","Gestanzte Karten mit codierten Löchern und Schreibmaschinenschrift"),
    VINYL("Vinyl-Fahrten","Schallplattenrillen und Plattenhüllen als kleine Abfahrts-Singles"),
    PIXEL_QUEST("Pixel-Quest","Eine kleine Pixelbahn fährt durch deine nächste Fahrtenliste"),
    CHALKBOARD("Kreidetafel","Kreidestriche, Tafellinien und handgezeichnete Zeitmarken"),
    CLOCKWORK("Uhrwerk","Zahnräder, Messingringe und verschraubte Fahrtenplaketten"),
    PAPER_DIORAMA("Papier-Diorama","Geschichtete Papierlandschaften mit gefalteten Abfahrtsbändern"),
    MINT_SILENCE("Minzruhe","Klares Minzweiß, dunkles Grün und ruhige weiche Karten"),
    APRICOT_AIR("Aprikosenlicht","Helle Aprikose, warme Akzente und ein klares Schriftbild"),
    IRIS_PAPER("Irispapier","Kühles Flieder, Tintenlila und reduzierte elegante Flächen"),
    OCEAN_LINE("Küstenklar","Tiefes Küstenblau, helles Aqua und schmale ruhige Konturen"),
    KITE_POST("Drachenpost","Bunte Drachen, feine Schnüre und sonnige Papierflächen"),
    FIREFLY_NIGHT("Glühwürmchennacht","Leuchtpunkte zwischen dunklen Halmen und sanftem Waldgrün"),
    CONFETTI_STUDIO("Konfettistudio","Koralle, Kobalt und grafisches Konfetti auf warmem Weiß"),
    RIBBON_PLAY("Schleifenfahrt","Große fließende Schleifen auf einer matten Pflaumennacht"),
    REN_LUMEN("Renaissance · Lichtbogen","Elfenbein, Sienarot und symmetrische Arkaden"),
    BAROQUE_INK("Barock · Samtgold","Bordeaux, Messing und geschwungene Ornamentlinien"),
    ROMANTIC_HAZE("Romantik · Fernweh","Nebelblau, sanfte Berge und ein ferner warmer Mond"),
    IMPRESSION_GARDEN("Impressionismus · Lichtgarten","Lichttupfer in Salbei, Flieder und warmem Gelb"),
    NOUVEAU_BOTANIC("Jugendstil · Iris","Fließende Pflanzenlinien, Pfauengrün und Elfenbein"),
    EXPRESSION_VIVID("Expressionismus · Farbklang","Markante Schrägen, Nachtblau und leuchtende Farbkanten"),
    CONSTRUCT_RED("Konstruktivismus · Rotplan","Rot, Schwarz und Creme mit entschiedener Geometrie"),
    SURREAL_DREAM("Surrealismus · Traumgleis","Schwebende Kugeln, lange Schatten und ein unmöglicher Torbogen"),
    POP_PRINT("Pop Art · Siebdruck","Große Rasterpunkte, kräftiges Pink und grafische Farbflächen"),
    DECADE_70("70er · Sonnenfunk","Avocado, Orange und breite geschwungene Retro-Bänder"),
    DECADE_80("80er · Mixtape","Kassetten, elektrische Akzente und tiefe Violetttöne"),
    DECADE_90("90er · Netzkultur","Türkis, graue Fensterrahmen und kantige Pixel-Ecken"),
    DECADE_00("00er · Chromwelle","Glänzende Aquabögen, Silber und durchscheinende Flächen"),
    DECADE_10("10er · Flat Studio","Klare Farbflächen, Koralle und großzügige flache Formen"),
    // Serialized name kept from the original adaptive board; shown as "Display".
    APP_THEME("Display","Ein echtes Display passend zu deinem App-Theme: hell zu dunklen Designs, dunkel zu hellen, bei Kunst und Retro auch als Gemälde oder Spiel"),
    APP_INTEGRATED("App-Theme","Fügt sich mit Farben, Formen, Schrift und Motiven nahtlos in dein App-Theme ein");

    /** Both follow the active app theme; custom board colours do not apply. */
    val followsAppTheme: Boolean get() = this == APP_THEME || this == APP_INTEGRATED

    val title: String get() = tr(titleKey)
    val description: String get() = tr(descriptionKey)

}

@Serializable data class BoardColors(val foreground: Long, val background: Long, val accent: Long)

data class BoardColorPreset(val title: String, val colors: BoardColors)

fun boardColorPresets(design: BoardDesign): List<BoardColorPreset> =
    when (design) {
        BoardDesign.DOT_MATRIX,
        BoardDesign.SEVEN_SEGMENT ->
            listOf(
                BoardColorPreset(tr("Orange"), BoardColors(0xFFFFA83E, 0xFF10120F, 0xFFFFE0AB)),
                BoardColorPreset(tr("Neon-Grün"), BoardColors(0xFF72FF87, 0xFF080E09, 0xFFFFFF83)),
                BoardColorPreset(tr("Silber / Weiß"), BoardColors(0xFFEAF0F4, 0xFF0C1015, 0xFF8FDFFF)),
                BoardColorPreset(tr("Bernstein"), BoardColors(0xFFFFD35A, 0xFF100F08, 0xFFFFF1AD)),
            )
        BoardDesign.DOS,
        BoardDesign.TELETEXT ->
            listOf(
                BoardColorPreset(tr("Videotext"), BoardColors(0xFFFFFFFF, 0xFF080A10, 0xFFFFFF55)),
                BoardColorPreset(tr("CRT-Grün"), BoardColors(0xFF70F590, 0xFF07120B, 0xFFC9FFD0)),
                BoardColorPreset(tr("Cyan auf Blau"), BoardColors(0xFF75FFFF, 0xFF001C68, 0xFFFFFF70)),
                BoardColorPreset(tr("Amber-Terminal"), BoardColors(0xFFFFCE69, 0xFF151007, 0xFFFFE9B8)),
            )
        BoardDesign.DB ->
            listOf(BoardColorPreset(tr("Bahnhof"), BoardColors(0xFFFFFFFF, 0xFF091C40, 0xFFFFDA65)))
        BoardDesign.RETRO_LCD -> listOf(
            BoardColorPreset(tr("Graues LCD"), BoardColors(0xFF202720, 0xFFB8BDB2, 0xFF202720)),
        )
        BoardDesign.PLAIN ->
            listOf(BoardColorPreset(tr("Papier"), BoardColors(0xFF1A1C1D, 0xFFFFFFFF, 0xFF006A6A)))
        BoardDesign.FLIP_DOT -> listOf(
            BoardColorPreset(tr("Klapppunkte"), BoardColors(0xFFFFE45C, 0xFF15190F, 0xFFF5F4C8)),
            BoardColorPreset(tr("Mint"), BoardColors(0xFFAAEDB6, 0xFF0B1711, 0xFFE2F5DD)),
        )
        BoardDesign.SPLIT_FLAP -> listOf(
            BoardColorPreset(tr("Bahnhofshalle"), BoardColors(0xFFF5EEE2, 0xFF1A1C21, 0xFFFFD076)),
            BoardColorPreset(tr("Nachtblau"), BoardColors(0xFFF3F7FF, 0xFF14253A, 0xFFA9D8FF)),
        )
        BoardDesign.E_PAPER -> listOf(
            BoardColorPreset(tr("Schwarzweiß"), BoardColors(0xFF191919, 0xFFF7F7F2, 0xFF191919)),
            BoardColorPreset(tr("Invertiert"), BoardColors(0xFFF4F4ED, 0xFF171918, 0xFFF4F4ED)),
        )
        BoardDesign.NIGHT_SKY -> listOf(
            BoardColorPreset(tr("Sternennacht"), BoardColors(0xFFE9E4FF, 0xFF14102E, 0xFF9FE8ED)),
            BoardColorPreset(tr("Polarlicht"), BoardColors(0xFFDBFFEC, 0xFF061C21, 0xFF9EF4CC)),
        )
        BoardDesign.METRO_MAP -> listOf(
            BoardColorPreset(tr("Stadtplan"), BoardColors(0xFF17303A, 0xFFF0F7F8, 0xFF006A78)),
            BoardColorPreset(tr("Nachtnetz"), BoardColors(0xFFEDF8F7, 0xFF102B31, 0xFF93DAD5)),
        )
        BoardDesign.DEPARTURE_TICKET -> listOf(
            BoardColorPreset(tr("Fahrschein"), BoardColors(0xFF312A20, 0xFFFFF4D8, 0xFF874515)),
            BoardColorPreset(tr("Rosa Ticket"), BoardColors(0xFF3B2632, 0xFFFFE8F0, 0xFF8B315E)),
        )
        BoardDesign.MODERN_MATRIX -> listOf(BoardColorPreset(tr("LED-Cyan"),BoardColors(0xFF6CF3FF,0xFF050F16,0xFFFFFFFF)))
        BoardDesign.RETRO_CRT -> listOf(BoardColorPreset(tr("Phosphor-Grün"),BoardColors(0xFF8EFFA2,0xFF07120B,0xFFD7FFE0)))
        BoardDesign.CYBERPUNK -> listOf(BoardColorPreset(tr("Neon-Grid"),BoardColors(0xFF00F0FF,0xFF100B29,0xFFFF62B0)))
        BoardDesign.NIXIE -> listOf(BoardColorPreset(tr("Kupferglas"),BoardColors(0xFFFFBA7A,0xFF17120F,0xFFB2DBCC)))
        BoardDesign.WAYFINDER -> listOf(BoardColorPreset(tr("Signalpapier"),BoardColors(0xFF222725,0xFFFFF6D6,0xFF685100)))
        BoardDesign.TRAM_ROLLSIGN -> listOf(BoardColorPreset(tr("Rollbandgrün"),BoardColors(0xFFF5F0D6,0xFF173932,0xFFD5DB9C)))
        BoardDesign.PRINTED_SCHEDULE -> listOf(BoardColorPreset(tr("Aushang"),BoardColors(0xFF282C33,0xFFFBF8EC,0xFF78433D)))
        BoardDesign.DEPARTURE_CARDS -> listOf(BoardColorPreset(tr("Kartenblau"),BoardColors(0xFF203F65,0xFFEBF3FC,0xFF80603B)))
        BoardDesign.RADAR -> listOf(BoardColorPreset(tr("Sonargrün"),BoardColors(0xFFB1E9CB,0xFF102D2B,0xFFE8D699)))
        BoardDesign.COMIC -> listOf(BoardColorPreset(tr("Druckatelier"),BoardColors(0xFF262131,0xFFFFF5D7,0xFF8B2464)))
        BoardDesign.AIRPORT_GATE -> listOf(BoardColorPreset(tr("Terminalblau"),BoardColors(0xFFF3F7FF,0xFF0B2035,0xFFFFD66D)))
        BoardDesign.PUNCH_CARD -> listOf(BoardColorPreset(tr("Archivkarton"),BoardColors(0xFF3B2E24,0xFFECE0C7,0xFF8C4531)))
        BoardDesign.VINYL -> listOf(BoardColorPreset(tr("B-Seite"),BoardColors(0xFFF6E7D2,0xFF261C30,0xFFFFB09D)))
        BoardDesign.PIXEL_QUEST -> listOf(BoardColorPreset(tr("Nachtlevel"),BoardColors(0xFFE2F7DE,0xFF10252C,0xFFF0CE7B)))
        BoardDesign.CHALKBOARD -> listOf(BoardColorPreset(tr("Tafelkreide"),BoardColors(0xFFF1F0D9,0xFF173A35,0xFFFFD784)))
        BoardDesign.CLOCKWORK -> listOf(BoardColorPreset(tr("Messingwerk"),BoardColors(0xFFF0DFC0,0xFF292522,0xFFE5B974)))
        BoardDesign.PAPER_DIORAMA -> listOf(BoardColorPreset(tr("Papierkulisse"),BoardColors(0xFF203F49,0xFFEDF5EC,0xFF6C4568)))
        BoardDesign.MATERIAL -> emptyList()
        else -> eraBoardPresets(design)
    }

@Serializable
data class BoardSettings(
    val design: BoardDesign = BoardDesign.DOT_MATRIX,
    val customColors: Map<BoardDesign, BoardColors> = emptyMap(),
    val hiddenInApp: Boolean = false,
    val hideSettingsButtons: Boolean = false,
    val forceMaterialInApp: Boolean = false,
    val products: Set<LineType> = selectableProducts,
    val selectedStop: Location? = null,
    val listSort: BoardStopSort = BoardStopSort.ALPHABETICAL,
    // Retained to read older settings without losing an enabled 20-second interval.
    val autoRotate:Boolean=false,
    val rotationIntervalSeconds:Int?=null,
) {
    val rotationSeconds:Int get()=(rotationIntervalSeconds ?: if(autoRotate)20 else 0).coerceIn(0,120)
}

@Serializable
enum class BoardStopSort(private val titleKey: String) {
    ALPHABETICAL("A–Z"),
    DISTANCE("Entfernung");

    val title: String get() = tr(titleKey)

}

@Serializable
enum class WidgetSurface(private val labelKey: String) {
    SOFT("Soft"),
    GLASS("Glas");

    val label: String get() = tr(labelKey)

}

@Serializable
enum class ClickFeedback(private val labelKey: String) {
    RIPPLE("Sofort mit Ripple"),
    PROGRESS("Kurzes Ladefeedback");

    val label: String get() = tr(labelKey)

}

@Serializable
data class DesignOptions(
    val surface: WidgetSurface = WidgetSurface.SOFT,
    val containerElevation: Float = 0f,
    val buttonElevation: Float = 2f,
    // Nullable only to derive the initial toggles from legacy continuous elevations.
    val containerShadow: Boolean? = null,
    val tileShadow: Boolean? = null,
) {
    val hasContainerShadow: Boolean get() = containerShadow ?: (containerElevation > 0f)
    val hasTileShadow: Boolean get() = tileShadow ?: (buttonElevation > 0f)
    val containerShadowDp: Float get() = if (hasContainerShadow) 6f else 0f
    val tileShadowDp: Float get() = if (hasTileShadow) 2f else 0f
}


/** Existing serialized WidgetStyle fields are retained for lossless updates from 2.1.0. */
typealias WidgetSettings = WidgetStyle

typealias TileShape = IconStyle

@Serializable
data class ColorHistory(
    val recent: List<Long> = emptyList(),
    val favorites: List<Long> = emptyList(),
)

@Serializable
data class LaunchFeedback(val targetId: String, val startedAt: Long) {
    fun activeFor(id: String, now: Long) = id == targetId && now - startedAt in 0..4_999
}
