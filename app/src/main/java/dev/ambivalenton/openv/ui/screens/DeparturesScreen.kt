// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.ui.screens

import dev.ambivalenton.openv.ui.theme.cardElevation
import dev.ambivalenton.openv.ui.theme.appFrameShadow
import dev.ambivalenton.openv.ui.theme.appFrame
import dev.ambivalenton.openv.ui.components.ThemedTonalButton
import dev.ambivalenton.openv.ui.components.ThemedOutlinedButton

import dev.ambivalenton.openv.ui.components.CalmVisibility
import dev.ambivalenton.openv.ui.components.calmContentSize
import dev.ambivalenton.openv.localization.tr

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.isActive
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.boardCandidates
import dev.ambivalenton.openv.data.repository.boardConfigurationKey
import dev.ambivalenton.openv.data.repository.boardLocationKey
import dev.ambivalenton.openv.data.repository.distanceMeters
import dev.ambivalenton.openv.ui.*
import dev.ambivalenton.openv.ui.components.*
import dev.ambivalenton.openv.ui.theme.appCorner
import dev.ambivalenton.openv.ui.theme.appPanelDecoration
import kotlin.math.roundToInt

@Composable
fun DeparturesScreen(ui: AppUiState, vm: AppViewModel, onPermission: () -> Unit,onJourney:(JourneyQuery,Trip)->Unit={query,trip->vm.openKnownJourney(query,trip,true)},onComparison:(JourneyQuery,List<Trip>)->Unit=vm::openKnownJourneys,active:Boolean=true) {
    var picked by rememberSaveable(stateSaver = locationSaver) { mutableStateOf<Location?>(null) }
    val settings = ui.store.settings
    var radiusFilter by rememberSaveable { mutableStateOf(false) }
    var designSheet by rememberSaveable { mutableStateOf(false) }
    var stopsSheet by rememberSaveable { mutableStateOf(false) }
    // M08: the stop sheet either adds to the list or picks one fixed stop for the board.
    var fixedStopSheet by rememberSaveable { mutableStateOf(false) }
    val fixedStopRequest by vm.fixedStopRequest.collectAsState()
    LaunchedEffect(fixedStopRequest) {
        if (fixedStopRequest != 0L) { fixedStopSheet = true; stopsSheet = true; vm.consumeFixedStopRequest() }
    }
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(active,ui.ready,settings.board.rotationSeconds,settings.board.hiddenInApp,settings.board.selectedStop,stopsSheet,designSheet,lifecycle) {
        if(active && ui.ready && !settings.board.hiddenInApp && settings.board.rotationSeconds>0 && !stopsSheet && !designSheet)
            lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
                while(kotlinx.coroutines.currentCoroutineContext().isActive) {
                    kotlinx.coroutines.delay(settings.board.rotationSeconds*1000L)
                    vm.advanceBoardStop()
                }
            }
    }
    DisposableEffect(active,settings.board.hiddenInApp,lifecycle) {
        if(!active || settings.board.hiddenInApp)vm.resetBoardRotation()
        val observer=androidx.lifecycle.LifecycleEventObserver {_,event->
            if(event==androidx.lifecycle.Lifecycle.Event.ON_STOP)vm.resetBoardRotation()
        }
        lifecycle.addObserver(observer)
        onDispose {lifecycle.removeObserver(observer);vm.resetBoardRotation()}
    }
    LaunchedEffect(ui.ready, boardConfigurationKey(ui.store), ui.store.presence) {
        if (ui.ready) vm.refreshBoard()
    }
    LaunchedEffect(
        ui.ready,
        TimetableProfile.identity(settings),
        settings.originMode,
        settings.manualOrigin,
        settings.departureRadiusMeters,
        settings.minimalistMode,
        ui.position.value,
    ) {
        if (ui.ready && !settings.minimalistMode && (ui.position.value != null || settings.originMode == OriginMode.MANUAL))
            vm.nearby()
    }
    if(settings.minimalistMode && !settings.board.hiddenInApp) {
        SharedDepartureBoard(ui.store,{stopsSheet=true},{designSheet=true},
            modifier=Modifier.fillMaxSize().padding(start=20.dp,end=20.dp,top=8.dp,bottom=8.dp+LocalNavigationClearance.current),fillHeight=true,onJourney=onJourney,onComparison=onComparison,onRowsRequired=vm::requireBoardRows)
    } else LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start=20.dp,end=20.dp,top=20.dp,bottom=20.dp+dev.ambivalenton.openv.ui.components.LocalNavigationClearance.current),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (!settings.board.hiddenInApp)
            item(key = "shared-board") {
                FittedText(
                    tr("Anzeigetafel"),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                SharedDepartureBoard(
                    ui.store,
                    { stopsSheet = true },
                    { designSheet = true },
                    onJourney=onJourney,onComparison=onComparison,onRowsRequired=vm::requireBoardRows,
                )
                // M08: an empty board offers its two direct ways; a fixed stop needs no location.
                if (boardCandidates(ui.store).isEmpty())
                    androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp).testTag("board-empty-actions"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemedButton({ fixedStopSheet = false; stopsSheet = true }, Modifier.heightIn(min = 48.dp).testTag("board-add-stop")) {
                            Text(tr("Haltestelle hinzufügen")) }
                        ThemedOutlinedButton({ fixedStopSheet = true; stopsSheet = true }, Modifier.heightIn(min = 48.dp).testTag("board-fixed-stop")) {
                            Text(tr("Feste Haltestelle wählen")) }
                    }
            }
        if (settings.board.hiddenInApp)
            item(key = "board-list-action") {
                ThemedOutlinedButton({ stopsSheet = true }) {
                    AppIcon(R.drawable.ic_list, null)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Haltestellen-Liste"))
                }
            }
        if(!settings.minimalistMode) {
        item(key = "nearby-heading") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // M08: a search of its own around the location, independent of the board's list.
                FittedText(
                    tr("Haltestellen in deiner Nähe"),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                )
                AssistChip(
                    { radiusFilter = !radiusFilter },
                    label = { Text(radiusLabel(settings.departureRadiusMeters)) },
                    leadingIcon = { AppIcon(R.drawable.ic_filter, null, Modifier.size(16.dp)) },
                )
            }
            CalmVisibility(radiusFilter) {
                RadiusSlider(settings.departureRadiusMeters) { meters ->
                    vm.settings { it.copy(departureRadiusMeters = meters) }
                }
            }
        }
        if (ui.nearby.loading)
            item {
                ShimmerLoadingBar(
                    Modifier.fillMaxWidth().height(4.dp),
                    tr("Haltestellen werden geladen"),
                )
            }
        ui.nearby.error?.let { message ->
            item {
                ErrorPanel(message, { vm.nearby(force = true) })
                if (settings.originMode == OriginMode.AUTOMATIC)
                    TextButton(onPermission) { Text(tr("Standort prüfen")) }
            }
        }
        if (
            ui.position.value == null &&
                settings.originMode == OriginMode.AUTOMATIC &&
                !ui.position.loading
        )
            item {
                ThemedTonalButton(onPermission) { Text(tr("Standort freigeben und Umkreis laden")) }
            }
        val stops = ui.nearby.value.orEmpty()
        if (stops.isEmpty() && ui.nearby.value != null && !ui.nearby.loading)
            item { Text(tr("Keine Haltestellen im gewählten Umkreis gefunden.")) }
        items(stops, key = { "nearby:${it.provider}:${it.id}" }) { stop ->
            val key = "nearby:${stop.provider}:${stop.id}"
            DepartureStationCard(key, stop.name, stop, ui.boards[key], ui, vm)
        }
        item(key = "search-stop") {
            Section(tr("Andere Haltestelle suchen")) {
                PlaceSearchField(
                    tr("Haltestelle oder Adresse"),
                    picked,
                    "${settings.engine}:${settings.geocodingEndpoint}",
                    vm::findLocations,
                    { picked = it },
                )
            }
        }
        picked?.let { place ->
            item(key = "picked") {
                val key =
                    "picked:${place.provider}:${place.id}:${place.latitude}:${place.longitude}"
                DepartureStationCard(key, place.name, place, ui.boards[key], ui, vm)
            }
        }
    }
    }
    if (designSheet) BoardDesignSheet(ui.store, vm) { designSheet = false }
    if (stopsSheet) BoardStopsSheet(ui.store, vm, onPermission, { stopsSheet = false; fixedStopSheet = false }, chooseFixed = fixedStopSheet)
}

val DepartureListRowHeight = 60.dp

@Composable
private fun DepartureStationCard(
    key: String,
    title: String,
    place: Location,
    board: StationBoard?,
    ui: AppUiState,
    vm: AppViewModel,
) {
    var expanded by rememberSaveable(key) { mutableStateOf(false) }
    val stop = board?.stop ?: place
    val listMember = dev.ambivalenton.openv.data.repository.boardPlaces(ui.store).any { boardLocationKey(it) == boardLocationKey(stop) }
    DisposableEffect(key) {
        vm.setBoardVisible(key, true)
        onDispose { vm.setBoardVisible(key, false) }
    }
    LaunchedEffect(
        key,
        place,
        TimetableProfile.identity(ui.store.settings),
        ui.store.settings.includeLongDistance,
    ) {
        vm.loadBoard(key, title, place)
    }
    // The arrow turns with a little overshoot instead of switching its picture (build 67).
    val arrow by androidx.compose.animation.core.animateFloatAsState(if (expanded) 180f else 0f,
        dev.ambivalenton.openv.ui.components.calmSpec(spring(dampingRatio = .5f, stiffness = 320f)), label = "departure-arrow")
    val cardShape = appCorner(28.dp)
    AppCard(
        shape = cardShape,
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            Modifier.fillMaxWidth().appPanelDecoration(cardShape)
                .calmContentSize(
                    spring(dampingRatio = .72f, stiffness = 300f),
                    alignment = Alignment.TopStart,
                )
                .clipToBounds()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(
                        role = Role.Button,
                        onClickLabel =
                            if (expanded) tr("Haltestelle einklappen") else tr("Haltestelle ausklappen"),
                    ) {
                        expanded = !expanded
                    }
                    .semantics {
                        stateDescription = if (expanded) tr("Ausgeklappt") else tr("Eingeklappt")
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(
                    R.drawable.ic_train,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    FittedText(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (
                        board?.stop != null &&
                            (stop.name != title || place.kind != LocationKind.STOP)
                    )
                        Text(
                            tr("Ab %1\$s", stop.name),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    if (
                        ui.position.value?.hasCoordinates == true && place.hasCoordinates
                    )
                        Text(
                            tr("%1\$s m Luftlinie", distanceMeters(ui.position.value, place)?.toInt()),
                            style = MaterialTheme.typography.labelSmall,
                        )
                }
                AppIcon(R.drawable.ic_expand, null, Modifier.graphicsLayer { rotationZ = arrow })
            }
            if (board?.rows?.loading == true || board == null)
                ShimmerLoadingBar(
                    Modifier.fillMaxWidth().height(4.dp),
                    tr("Live-Abfahrten werden geladen"),
                )
            board?.rows?.error?.let { message ->
                ErrorPanel(message, { vm.loadBoard(key, title, place, force = true) })
            }
            val departures = board?.rows?.value.orEmpty()
            if (board?.rows?.value != null && departures.isEmpty() && !board.rows.loading)
                Text(
                    tr("Aktuell keine Abfahrten gemeldet."),
                    style = MaterialTheme.typography.bodySmall,
                )
            if (!expanded) {
                Column(Modifier.clickable(role = Role.Button) { expanded = true }) {
                    departures.take(1).forEach { DepartureRow(it) }
                }
            }
            if (expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // A fixed two-row map remains above every service in the expanded state.
                    if (stop.hasCoordinates)
                        InteractiveMapContainer(
                            stop,
                            ui.store.settings.mapTileTemplate,
                            Modifier.height(DepartureListRowHeight * 2),
                        )
                    departures.forEach { departure ->
                        Box(Modifier.heightIn(min = DepartureListRowHeight)) {
                            DepartureRow(departure, showNotices = true)
                        }
                    }
                    if (board != null && board.canLoadMore && board.requestedLimit < 30)
                        ThemedOutlinedButton(
                            { vm.loadMoreBoard(key, title, place) },
                            Modifier.fillMaxWidth(),
                            enabled = !board.rows.loading,
                        ) {
                            Text(tr("Weitere laden"))
                        }
                    DepartureStopActions(listMember, stop.kind == LocationKind.STOP && stop.routable) { vm.addBoardStop(stop) }
                    if (board != null && board.rows.updatedAt > 0)
                        Text(
                            tr("Stand %1\$s · %2\$s Abfahrten", clockTime(board.rows.updatedAt), departures.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                }
            }
        }
    }
}

@Composable
fun DepartureRow(departure: Departure, showNotices: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                color = Color(departure.lineType.argb),
                contentColor = Color(departure.lineType.contentArgb),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(
                    departure.lineName,
                    Modifier.widthIn(min = 42.dp, max = 85.dp).padding(8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    departure.destination,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    // N02: the shared platform formatter names the platform after the vehicle.
                    listOfNotNull(clockTime(departure.time), departurePlatformText(departure)).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            DelayBadge(departure.delay)
        }
        if (showNotices)
            departure.notices.forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
    }
}

fun radiusLabel(meters: Int) = dev.ambivalenton.openv.data.preferences.formatRadius(meters)

@Composable
fun RadiusSlider(value: Int, onCommit: (Int) -> Unit) {
    var step by
        remember(value) {
            mutableIntStateOf(
                (dev.ambivalenton.openv.data.preferences.normalizeRadius(value) - 500) / 100
            )
        }
    Column(Modifier.padding(horizontal = 6.dp)) {
        Text(radiusLabel(500 + step * 100), style = MaterialTheme.typography.labelLarge)
        Slider(
            value = step.toFloat(),
            onValueChange = { step = it.roundToInt().coerceIn(0, 25) },
            valueRange = 0f..25f,
            steps = 24,
            onValueChangeFinished = { onCommit(500 + step * 100) },
            modifier =
                Modifier.semantics {
                    contentDescription = tr("Umkreis für Haltestellen")
                    stateDescription = radiusLabel(500 + step * 100)
                },
        )
    }
}
