// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val quietMode:Boolean=false,
    val powerSaving:Boolean=false,
    val monochrome:Boolean=false,
    val fontId:String?=null,
    // Legacy read compatibility only; ticket handoff has been withdrawn.
    val showTicketPurchase:Boolean=false,
    val alternativeSuggestions:Boolean=true,
    val minimalistMode:Boolean=false,
    val chartLongPress:ChartLongPressAction=ChartLongPressAction.SHARE,
    /** Show the pinned card as a floating system bubble outside the app (needs overlay access). */
    val pinnedBubble:Boolean=false,
    val normalAppearance:AppearanceProfile?=null,
    val quietAppearance:AppearanceProfile?=null,
    val quietAppearanceVersion:Int=0,
    val footprints:FootprintPreferences=FootprintPreferences(),
    val engine: Engine = Engine.VRR,
    val efaEndpoint: String = TimetableProfile.endpoint,
    val geocodingEndpoint: String = "https://photon.komoot.io/",
    val mapTileTemplate: String = "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
    val originMode: OriginMode = OriginMode.AUTOMATIC,
    val manualOrigin: Location? = null,
    val manualOriginChangedAt: Long = 0L,
    val products: Set<LineType> = localProducts,
    val includeLongDistance: Boolean = false,
    val departureRadiusMeters: Int = 500,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val appTheme: AppTheme? = null,
    val appThemeColors:Map<AppTheme,AppThemeColors> = emptyMap(),
    val readability: AppReadability = AppReadability(),
    val walkingNavigation: WalkingNavigationMode = WalkingNavigationMode.OPENSTREETMAP,
    val timelineShowWalking: Boolean = true,
    val detailedWalkingNavigation: Boolean = true,
    val walkingRoutingEndpoint: String = "https://valhalla1.openstreetmap.de",
    val walkingSpeed: WalkingSpeed = WalkingSpeed.AVERAGE,
    val ignoreWalking: Boolean = false,
    val searchResultOrder:List<SearchResultGroup> = defaultSearchResultOrder,
    val searchResultPriority: SearchResultPriority = SearchResultPriority.STOPS_FIRST,
    val accentSeed: Long? = null,
    val widget: WidgetStyle = WidgetStyle(),
    val autoRefresh: Boolean = true,
    val timelineTheme: TimelineTheme = TimelineTheme.ORIGINAL,
    val timelineLabelControl: Float = 1f,
    val timelineSeed: Long? = null,
    val timelinePalettes:Map<TimelineTheme,TimelinePaletteOverride> = emptyMap(),
    val timelineLineColors: Map<LineType, Long> = emptyMap(),
    val overview: OverviewSettings = OverviewSettings(),
    /** Retained so stored settings and backups still read; favourites at the
     * current location are always hidden in the overview and this value is ignored. */
    val hideFavoritesAtCurrentLocation: Boolean = true,
    /** Radius around favourites inside which the app starts there: 0 = never (always the real
     * position), 1…2000 m, [FAVORITE_RADIUS_ALWAYS] = always the nearest favourite. */
    val favoriteRadiusMeters: Int = FAVORITE_RADIUS_DEFAULT,
    val board: BoardSettings = BoardSettings(),
    /** Look of the favourite network plan (build 70). */
    val networkPlanDesign: NetworkPlanDesign = NetworkPlanDesign.OPENV,
    /** Small trains and buses moving along the plan's lines; decoration only (build 70: switchable). */
    val networkVehicles: Boolean = true,
    /** Holding a favourite in the network plan (live mode). */
    val networkHoldAction: NetworkHoldAction = NetworkHoldAction.MOVE,
    /** H07 (build 93): the preferred view of the results, chart or list. */
    val resultsView: ResultsView = ResultsView.CHART,
    /** 2.10 (build 93): the day and the night design and when they change. */
    val dayNight: DayNightSettings = DayNightSettings(),
    /** 2.8 (build 93, hidden window): a random app theme at each cold start. */
    val randomThemeOnStart: Boolean = false,
    /** 2.8: the theme the last cold start drew, so it is not drawn again right away. */
    val lastRandomTheme: AppTheme? = null,
    /** 2.8: temporary accent colours that change with every completed tab change. */
    val rainbowMode: Boolean = false,
    /**
     * "Planermodus": the app as a pure timetable planner: only the search with start and
     * destination fields, network plan and history; no navigation pill, no overview or board, no
     * location requests; a pinned journey follows the timetable data alone.
     */
    val plannerMode: Boolean = false,
    /** The favourites' lines are shown as a list instead of the network plan. */
    val favoriteListView: Boolean = false,
)
