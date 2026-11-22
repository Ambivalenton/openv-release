// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.data.model

import dev.ambivalenton.openv.localization.tr

import java.time.ZonedDateTime
import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    AMOLED,
}

@Serializable
enum class OriginMode {
    AUTOMATIC,
    MANUAL,
}

@Serializable
enum class IconStyle(private val titleKey: String) {
    CIRCLE("Kreis"),
    SQUIRCLE("Squircle"),
    ROUNDED("Abgerundetes Quadrat"),
    TEARDROP("Tropfen"),
    HEXAGON("Sechseck"),
    DIAMOND("Raute"),
    COOKIE("Cookie"),
    CLOVER("Kleeblatt"),
    STAR("Stern"),
    PILL("Kapsel"),
    OCTAGON("Achteck"),
    // Material-3-Expressive-inspired shapes (geometry: rendering/ExpressiveShapeOutline.kt).
    PENTAGON("Fünfeck"),
    GEM("Edelstein"),
    ARCH("Bogen"),
    SLANTED("Schräg"),
    SUNNY("Sonne"),
    FLOWER("Blüte"),
    BURST("Zackenstern"),
    HEART("Herz"),
    GHOST("Geist"),
    PIXEL("Pixelkreis");

    val title: String get() = tr(titleKey)

}

enum class WidgetShadow(val depth: Int, private val titleKey: String) {
    NONE(0, "Keiner"),
    SOFT(2, "Sanft"),
    MEDIUM(6, "Mittel"),
    STRONG(10, "Stark");

    val title: String get() = tr(titleKey)

    companion object {
        fun fromDepth(depth: Int) = entries.minBy { kotlin.math.abs(it.depth - depth) }
    }
}

@Serializable
enum class FavoriteIcon(private val titleKey: String) {
    HOME("Zuhause"),
    WORK("Arbeit"),
    SCHOOL("Schule / Uni"),
    FITNESS_CENTER("Fitness"),
    SHOPPING_BAG("Einkaufen"),
    DIRECTIONS_BUS("Bus"),
    TRAIN("Bahn"),
    FLIGHT("Flughafen"),
    LOCAL_HOSPITAL("Krankenhaus"),
    PARK("Park"),
    LOCAL_BAR("Bar"),
    COFFEE("Café"),
    FAVORITE("Herz"),
    STAR("Stern"),
    BUILD("Werkstatt"),
    PLACE("Ort"),
    // People, everyday places and leisure (appended; stored by name).
    MOTHER("Mama"),
    FATHER("Papa"),
    GRANDPARENTS("Großeltern"),
    PARENTS("Eltern"),
    PARTNER("Partner/in"),
    SIBLINGS("Geschwister"),
    FRIENDS("Freunde"),
    BAKERY("Bäckerei"),
    RESTAURANT("Restaurant"),
    SUPERMARKET("Supermarkt"),
    KINDERGARTEN("Kita"),
    PHARMACY("Apotheke"),
    DOCTOR("Arztpraxis"),
    TOWN_HALL("Amt"),
    LIBRARY("Bibliothek"),
    CINEMA("Kino"),
    THEATER("Theater & Konzert"),
    STADIUM("Stadion"),
    SWIMMING("Schwimmbad"),
    CLUB("Verein"),
    WORSHIP("Gotteshaus"),
    HOTEL("Unterkunft"),
    LAKE("See & Strand"),
    DOG("Gassirunde"),
    // Retained for existing DataStore files; the editor presents FAVORITE instead.
    HEART("Herz"),
    STOP("Haltestelle");

    val title: String get() = tr(titleKey)
}

@Serializable
enum class TimelineTheme(private val titleKey: String,val style:TimelineBarStyle=TimelineBarStyle.CLASSIC,private val descriptionKey:String="") {
    ORIGINAL("ÖPNV"),
    APP_THEME("App-Theme",TimelineBarStyle.APP_THEME,"Übernimmt Farben, Ecken und typische Details des aktiven App-Designs"),
    MONET("Material Design"),
    PASTEL("Pastell"),
    VIVID("Vivid Pop"),
    MONOCHROME("Monochrom"),
    OCEAN("Ocean Wave"),
    CAPSULE("Kapseln",TimelineBarStyle.CAPSULE,"Weiche, durchgehend gerundete Fahrten"),
    SQUARED("Kantig",TimelineBarStyle.SQUARED,"Gerade Kanten und klare Zeitblöcke"),
    OUTLINE("Konturen",TimelineBarStyle.OUTLINE,"Offene Balken mit kräftiger Umrandung"),
    METRO_RAILS("Metrolinien",TimelineBarStyle.METRO_RAILS,"Schmale Linien mit runden Endstationen"),
    DOUBLE_RAIL("Doppelgleis",TimelineBarStyle.DOUBLE_RAIL,"Zwei parallele Gleise pro Fahrt"),
    DASHED("Taktstriche",TimelineBarStyle.DASHED,"Kurze Balken im gleichmäßigen Takt"),
    DOTTED("Punktspur",TimelineBarStyle.DOTTED,"Eine feine Spur aus runden Punkten"),
    BEADS("Perlenkette",TimelineBarStyle.BEADS,"Breite Perlen auf einer verbindenden Schnur"),
    TICKET("Fahrkarten",TimelineBarStyle.TICKET,"Fahrtblöcke mit ausgestanzten Ticket-Kanten"),
    CHEVRON("Richtungspfeile",TimelineBarStyle.CHEVRON,"Gestapelte Pfeile entlang der Fahrt"),
    RIBBON("Bänder",TimelineBarStyle.RIBBON,"Breite Bänder mit gefalteten Enden"),
    GLASS("Glas",TimelineBarStyle.GLASS,"Durchscheinende Flächen mit heller Lichtkante"),
    NEON("Neonröhren",TimelineBarStyle.NEON,"Leuchtende Konturen und ein schmaler Kern"),
    GRADIENT("Farbfluss",TimelineBarStyle.GRADIENT,"Weiche Verläufe innerhalb der Zeitbalken"),
    TERMINAL("Terminal",TimelineBarStyle.TERMINAL,"Technische Kanten, Markierungen und Monospace"),
    PIXEL("Pixelblöcke",TimelineBarStyle.PIXEL,"Kleine quadratische Bausteine als Zeitspur"),
    BRAID("Geflochten",TimelineBarStyle.BRAID,"Zwei ineinanderlaufende Spuren"),
    TRACK_SLEEPERS("Gleisbett",TimelineBarStyle.TRACK_SLEEPERS,"Schienen mit querliegenden Schwellen"),
    SPINE("Rückgrat",TimelineBarStyle.SPINE,"Seitliche Achse mit abzweigenden Zeitrippen"),
    BRACKET("Klammern",TimelineBarStyle.BRACKET,"Offene Klammern statt gefüllter Balken"),
    HALFTONE("Rasterpunkte",TimelineBarStyle.HALFTONE,"Flächige Balken mit regelmäßigem Punktraster"),
    LOLLIPOP("Start & Ziel",TimelineBarStyle.LOLLIPOP,"Große Endpunkte mit einer feinen Verbindung"),
    SEQUENCE("Zeitkacheln",TimelineBarStyle.SEQUENCE,"Getrennte Kacheln mit kleinen Zwischenräumen"),
    MINIMAL("Feine Linie",TimelineBarStyle.MINIMAL,"Eine reduzierte Linie mit präzisen Endmarken");

    val title: String get() = tr(titleKey)
    val description:String get()=if(descriptionKey.isBlank()) tr("Klassische Balken mit aufeinander abgestimmten Linienfarben") else tr(descriptionKey)

}

@Serializable
enum class Accent {
    DYNAMIC,
    TEAL,
    BLUE,
    VIOLET,
}

@Serializable
data class HighlightRule(
    val days: Set<Int> = (1..5).toSet(),
    val fromMinute: Int = 6 * 60,
    val untilMinute: Int = 10 * 60,
) {
    init {
        require(days.isNotEmpty() && days.all { it in 1..7 })
        require(fromMinute in 0..1439 && untilMinute in 0..1439)
    }

    /** Half-open minute ranges within one week (Monday 00:00 = 0); an overnight window is split. */
    internal fun weekRanges(): List<IntRange> = days.flatMap { day ->
        val start = (day - 1) * 1440
        when {
            fromMinute == untilMinute -> listOf(start until start + 1440)
            fromMinute < untilMinute -> listOf(start + fromMinute until start + untilMinute)
            else -> listOf(start + fromMinute until start + 1440) +
                ((start + 1440) % 10080).let { next -> if (untilMinute > 0) listOf(next until next + untilMinute) else emptyList() }
        }
    }

    /** True when both windows share at least one minute of the week. */
    fun overlaps(other: HighlightRule): Boolean {
        val mine = weekRanges(); val theirs = other.weekRanges()
        return mine.any { a -> theirs.any { b -> a.first <= b.last && b.first <= a.last } }
    }

    fun matches(now: ZonedDateTime): Boolean {
        val minute = now.hour * 60 + now.minute
        return if (fromMinute == untilMinute) now.dayOfWeek.value in days
        else if (fromMinute < untilMinute)
            now.dayOfWeek.value in days && minute in fromMinute until untilMinute
        else
            (now.dayOfWeek.value in days && minute >= fromMinute) ||
                (now.minusDays(1).dayOfWeek.value in days && minute < untilMinute)
    }
}

@Serializable
data class Favorite(
    val id: String = newId(),
    val label: String,
    val location: Location,
    val icon: FavoriteIcon = FavoriteIcon.PLACE,
    val rule: HighlightRule? = null,
    val accentSeed: Long? = null,
    val hiddenRules: List<HighlightRule> = emptyList(),
    val shape: IconStyle = IconStyle.SQUIRCLE,
    val counterpole: Location? = null,
    val transportFilter: Set<FavoriteTransport>? = null,
    val originAccentSeed:Long?=null,
    val originLabel:String?=null,
)

/** A measured device fix, never the user's manually selected routing origin. */
@Serializable
data class DevicePresence(val location: Location, val capturedAt: Long, val accuracyMeters: Float,
    val speedMetersPerSecond: Float? = null)

@Serializable
enum class WidgetContainerShape(private val titleKey: String) {
    ROUND("Ganz rund"),
    ROUNDED("Abgerundete Ecken"),
    RECTANGLE("Ganze Ecke");

    val title: String get() = tr(titleKey)


    fun radius(width: Float, height: Float): Float = when (this) {
        ROUND -> minOf(width, height) / 2f
        ROUNDED -> 24f
        RECTANGLE -> 0f
    }
}

@Serializable
data class WidgetStyle(
    val opacity: Int = 0,
    // Read-only compatibility values for old DataStore documents. New UI uses toggles,
    // containerShape and Favorite.shape; renderer ignores the old radius/global shape.
    val elevation: Int = 4,
    val cornerRadius: Int = 28,
    val iconsOnly: Boolean = true,
    val iconStyle: IconStyle = IconStyle.SQUIRCLE,
    val accent: Accent = Accent.DYNAMIC,
    // Legacy field is read for compatibility; the quick widget never renders a settings button.
    val showSettings: Boolean = false,
    val compactBackground: Boolean = true,
    val glassDepth: Int = 0,
    val design: DesignOptions? = null,
    val clickFeedback: ClickFeedback = ClickFeedback.RIPPLE,
    val backgroundColor: Long? = null,
    val tileColor: Long? = null,
    val containerShape: WidgetContainerShape = WidgetContainerShape.ROUNDED,
    val borderColor: Long? = null,
    val iconColor: Long? = null,
)

val WidgetStyle.designOptions: DesignOptions
    get() =
        design
            ?: DesignOptions(
                surface = if (glassDepth > 0) WidgetSurface.GLASS else WidgetSurface.SOFT,
                // Only old WidgetStyle files lack DesignOptions. Preserve their formerly rounded
                // level.
                buttonElevation = listOf(0f, 2f, 6f, 10f).minBy { kotlin.math.abs(it - elevation) },
            )

@Serializable
data class SearchHistory(val query: JourneyQuery, val searchedAt: Long, val count: Long = 1)

@Serializable
data class StoredState(
    val schemaVersion: Int = 10,
    val onboardingVersion: Int = 0,
    val onboardingDraft: OnboardingDraft? = null,
    val completedTutorials: Set<String> = emptySet(),
    val favorites: List<Favorite> = emptyList(),
    /** Legacy read field (schema ≤ 9 and old backups); migrated into [network] and then empty. */
    val favoritePairs:List<FavoritePair> = emptyList(),
    val network:TransitNetwork=TransitNetwork(),
    val locationOverride:UserLocationOverride?=null,
    /** An earlier version copied the former favourite places once into the board list (migrateBoardStops). */
    val boardStopsDecoupled:Boolean=true,
    val settings: AppSettings = AppSettings(),
    val history: List<SearchHistory> = emptyList(),
    val presence: DevicePresence? = null,
    val boardStops: List<Location> = emptyList(),
    // Favorite membership is projected; removals and temporary visibility are independent.
    val boardExcludedKeys: Set<String> = emptySet(),
    val boardDisabledKeys: Set<String> = emptySet(),
    val departureBoard: DepartureBoardState = DepartureBoardState(),
    // Visible-page rotation must never overwrite the user's fixed/nearest selection or an export.
    @kotlinx.serialization.Transient val rotatingBoardStop:Location?=null,
    val colorHistory: ColorHistory = ColorHistory(),
    val stopColors: Map<String, Long> = emptyMap(),
    val launchFeedback: LaunchFeedback? = null,
    val savedJourneys:List<SavedJourney> = emptyList(),
    val usageTips:UsageTipProgress=UsageTipProgress(),
    val personalThemes:List<PersonalTheme> = emptyList(),
    val personalThemesIntroduced:Boolean=false,
    val launcherLogo:AppLogo?=null,
    val lastImportToken:String?=null,
    /** Favourites whose overlapping time windows were shortened once (shown once, then cleared). */
    val scheduleNotice:List<String> = emptyList(),
    /** Favourite pinned to the overview by holding it in the plan ("In Übersicht anzeigen").
     * Only effective while that hold action is chosen; kept when another action is selected.
     * Since build 81 a legacy read value: it is carried into [overviewFavoriteIds] on the next change. */
    val overviewFavoriteId:String?=null,
    /** Up to [MAX_OVERVIEW_FAVORITES] favourites pinned to the overview, in pinning order. */
    val overviewFavoriteIds:List<String> = emptyList(),
    /** 2.5: notice texts hidden for one provider and stop (keys of noticeDismissKey); local only. */
    val dismissedNotices:Set<String> = emptySet(),
)
