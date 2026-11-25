// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.ui.components

import dev.ambivalenton.openv.localization.tr

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import dev.ambivalenton.openv.ui.theme.appFrame
import dev.ambivalenton.openv.ui.theme.appFrameShadow
import dev.ambivalenton.openv.ui.theme.cardElevation
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import dev.ambivalenton.openv.data.repository.placeMatch
import dev.ambivalenton.openv.data.repository.placeIdentity
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.ui.tutorial.tutorialTarget
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.errorText
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.collect

@Composable
fun AppIcon(
    resource: Int,
    description: String?,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color = LocalContentColor.current,
) {
    Icon(painterResource(resource), description, modifier.size(24.dp), tint = tint)
}

fun FavoriteIcon.resource() =
    when (this) {
        FavoriteIcon.HOME -> R.drawable.ic_home
        FavoriteIcon.WORK -> R.drawable.ic_work
        FavoriteIcon.SCHOOL -> R.drawable.ic_school
        FavoriteIcon.HEART,
        FavoriteIcon.FAVORITE -> R.drawable.ic_heart
        FavoriteIcon.PLACE -> R.drawable.ic_place
        FavoriteIcon.STOP -> R.drawable.ic_stop
        FavoriteIcon.FITNESS_CENTER -> R.drawable.ic_fitness_center
        FavoriteIcon.SHOPPING_BAG -> R.drawable.ic_shopping_bag
        FavoriteIcon.DIRECTIONS_BUS -> R.drawable.ic_bus
        FavoriteIcon.TRAIN -> R.drawable.ic_train
        FavoriteIcon.FLIGHT -> R.drawable.ic_flight
        FavoriteIcon.LOCAL_HOSPITAL -> R.drawable.ic_hospital
        FavoriteIcon.PARK -> R.drawable.ic_park
        FavoriteIcon.LOCAL_BAR -> R.drawable.ic_bar
        FavoriteIcon.COFFEE -> R.drawable.ic_coffee
        FavoriteIcon.STAR -> R.drawable.ic_star
        FavoriteIcon.BUILD -> R.drawable.ic_build
        FavoriteIcon.RESTAURANT -> R.drawable.ic_restaurant
        FavoriteIcon.MOTHER -> R.drawable.ic_fav_mother
        FavoriteIcon.FATHER -> R.drawable.ic_fav_father
        FavoriteIcon.GRANDPARENTS -> R.drawable.ic_fav_grandparents
        FavoriteIcon.PARENTS -> R.drawable.ic_fav_parents
        FavoriteIcon.PARTNER -> R.drawable.ic_fav_partner
        FavoriteIcon.SIBLINGS -> R.drawable.ic_fav_siblings
        FavoriteIcon.FRIENDS -> R.drawable.ic_fav_friends
        FavoriteIcon.BAKERY -> R.drawable.ic_fav_bakery
        FavoriteIcon.SUPERMARKET -> R.drawable.ic_fav_supermarket
        FavoriteIcon.KINDERGARTEN -> R.drawable.ic_fav_kindergarten
        FavoriteIcon.PHARMACY -> R.drawable.ic_fav_pharmacy
        FavoriteIcon.DOCTOR -> R.drawable.ic_fav_doctor
        FavoriteIcon.TOWN_HALL -> R.drawable.ic_fav_town_hall
        FavoriteIcon.LIBRARY -> R.drawable.ic_fav_library
        FavoriteIcon.CINEMA -> R.drawable.ic_fav_cinema
        FavoriteIcon.THEATER -> R.drawable.ic_fav_theater
        FavoriteIcon.STADIUM -> R.drawable.ic_fav_stadium
        FavoriteIcon.SWIMMING -> R.drawable.ic_fav_swimming
        FavoriteIcon.CLUB -> R.drawable.ic_fav_club
        FavoriteIcon.WORSHIP -> R.drawable.ic_fav_worship
        FavoriteIcon.HOTEL -> R.drawable.ic_fav_hotel
        FavoriteIcon.LAKE -> R.drawable.ic_fav_lake
        FavoriteIcon.DOG -> R.drawable.ic_fav_dog
    }

@Composable
fun Section(
    title: String,
    initiallyExpanded: Boolean = false,
    expansionState: MutableState<Boolean>? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val expansion = expansionState ?: rememberSaveable { mutableStateOf(initiallyExpanded) }
    var expanded by expansion
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, animationSpec=calmSpec(androidx.compose.animation.core.spring()), label = tr("Bereich öffnen"))
    AppCard(shape = dev.ambivalenton.openv.ui.theme.appCorner(24.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = title) { expanded = !expanded }
                .padding(20.dp)
                .semantics { stateDescription = if (expanded) tr("Ausgeklappt") else tr("Eingeklappt") },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FittedText(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            AppIcon(R.drawable.ic_expand, null, Modifier.rotate(rotation))
        }
        CalmVisibility(expanded) {
            Column(
                Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

@Composable
fun SectionLink(title:String,onClick:()->Unit,modifier:Modifier=Modifier) {
    AppCard(shape=dev.ambivalenton.openv.ui.theme.appCorner(24.dp),modifier=modifier.fillMaxWidth().pressScale(.98f)) {
        Row(Modifier.fillMaxWidth().clickable(role=Role.Button,onClickLabel=title,onClick=onClick).padding(20.dp),
            verticalAlignment=Alignment.CenterVertically) {
            FittedText(title,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
            AppIcon(R.drawable.ic_expand,null,Modifier.rotate(-90f))
        }
    }
}

@Composable
fun ToggleRow(
    title: String,
    subtitle: String? = null,
    value: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .toggleable(value = value, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            FittedText(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(value, null)
    }
}

@Composable
fun ProviderNotice(engine: Engine) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = dev.ambivalenton.openv.ui.theme.appCorner(20.dp),
    ) {
        Text(
            tr(TimetableProfile.coverage(engine)),
            Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
fun ErrorPanel(message: String, onRetry: (() -> Unit)? = null) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = dev.ambivalenton.openv.ui.theme.appCorner(20.dp),
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
            onRetry?.let { TextButton(it) { Text(tr("Erneut versuchen")) } }
        }
    }
}

/** The saved places every place field offers first (provided by the app with the store's favourites). */
val LocalFavoritePlaces = androidx.compose.runtime.staticCompositionLocalOf<List<Favorite>> { emptyList() }

@Composable
fun PlaceSearchField(
    label: String,
    value: Location?,
    providerKey: String,
    search: suspend (String) -> List<Location>,
    onSelected: (Location?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    expressive: Boolean = false,
    /** Build 120: a fine outline also at rest, where the field would otherwise melt into its surface (dialogs). */
    outlinedAtRest: Boolean = false,
    fastSearch: PlaceSearchGateway? = LocalPlaceSearchGateway.current,
    externalLabel: Boolean = false,
    onSuggestionLongClick: ((Location) -> Unit)? = null,
    /** No frame or fill of its own; the host (inline location panel) draws them. */
    bare: Boolean = false,
    onFocusChange: (Boolean) -> Unit = {},
    /** 2.3: the typed text (also while no place is chosen). */
    onTextChange: (String) -> Unit = {},
    /** H01/H02/N01: lets a screen move the focus into this field. */
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    /** H03: the keyboard's search action once a place is chosen. */
    onImeSearch: (() -> Unit)? = null,
    /** Saved places offered first: all of them on an empty field, then those that match. */
    favorites: List<Favorite> = LocalFavoritePlaces.current,
    /** A new value empties the typed text (the chosen place stays until another is picked). */
    clearText: Int = 0,
    /** Build 102: false leaves out "Wähle einen Ort aus den Vorschlägen." where the results speak for themselves. */
    chooseHint: Boolean = true,
) {
    val firstResult=remember {BringIntoViewRequester()}
    val suggestionsScroll=rememberScrollState()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var text by rememberSaveable { mutableStateOf(value?.name.orEmpty()) }
    var suggestions by remember { mutableStateOf<List<Location>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    // H05: a failed search (not an empty answer) offers "Erneut suchen" with the same text.
    var failed by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var focusedQuery by remember { mutableStateOf(false) }
    var fieldFocused by remember { mutableStateOf(false) }
    // One effect for both, so a newly shown field cannot write the chosen place's name back
    // over the requested empty text; every request value empties the field exactly once.
    var clearedFor by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(value, clearText) {
        // A request of 0 (field closed or opened by tap) arms the next one again.
        if (clearText == 0) clearedFor = 0
        if (clearText > 0 && clearText != clearedFor) {
            clearedFor = clearText
            text = ""
            focusedQuery = true
            suggestions = emptyList()
            onTextChange("")
        } else if (value != null) {
            text = value.name
            focusedQuery = false
            suggestions = emptyList()
        }
    }
    var previousProvider by remember {mutableStateOf(providerKey)}
    var suggestionsHeight by remember {mutableIntStateOf(0)}
    var revealed by remember {mutableStateOf(false)}
    var showBusy by remember {mutableStateOf(false)}
    val latestProvider by rememberUpdatedState(providerKey)
    LaunchedEffect(loading,text) {showBusy=false;if(loading){delay(300);showBusy=true}}
    // The saved places come first: on an empty field all of them, while typing only the
    // ones whose name or place contains the typed letters (the regular results follow).
    val savedPlaces=remember(favorites) {favorites.filter {it.location.routable}.distinctBy {it.location}
        .map {it.location.copy(favoriteLabel=it.label.takeIf {label->label!=it.location.name})}}
    fun savedMatches(typed:String)=if(typed.isEmpty()) savedPlaces else savedPlaces.filter {
        it.name.contains(typed,ignoreCase=true) || it.favoriteLabel?.contains(typed,ignoreCase=true)==true}
    fun withSaved(typed:String,found:List<Location>):List<Location> {
        val saved=savedMatches(typed)
        return saved+found.filterNot {f->saved.any {s->sameLocation(s,f)}}
    }
    LaunchedEffect(text,providerKey,enabled,focusedQuery,retry,fieldFocused,savedPlaces) {
        val requested=text.trim();val sameProvider=previousProvider==providerKey;previousProvider=providerKey;error=null;failed=false
        if(enabled&&fieldFocused&&requested.isEmpty()&&value==null&&savedPlaces.isNotEmpty()) {
            loading=false;focusedQuery=true;suggestions=savedPlaces
        } else if(enabled&&focusedQuery&&requested.isNotEmpty()) {
            val quick=fastSearch?.local?.invoke(requested).orEmpty()
            suggestions=withSaved(requested,if(quick.isNotEmpty())quick else if(sameProvider)suggestions.filter {placeMatch(requested,it)!=null}else emptyList())
            loading=true;var received=emptyList<Location>()
            try {
                if(fastSearch?.startsImmediately!=true)delay(PLACE_SEARCH_DEBOUNCE_MILLIS)
                val updates=fastSearch?.updates?.invoke(requested)?:flow {emit(search(requested))}
                updates.collect {result->
                    currentCoroutineContext().ensureActive();received=result
                    if(focusedQuery&&text.trim()==requested&&latestProvider==providerKey&&result.isNotEmpty()){suggestions=withSaved(requested,result);error=null}
                }
                if(focusedQuery&&text.trim()==requested&&latestProvider==providerKey){suggestions=withSaved(requested,received);if(suggestions.isEmpty())error=tr("Keine passenden Orte gefunden.")}
            }catch(e:CancellationException){throw e}
            catch(e:Exception){if(text.trim()==requested&&latestProvider==providerKey){
                val copy=failureCopy(dev.ambivalenton.openv.data.repository.transitFailure(e),FailureContext.PLACE_SEARCH,System.currentTimeMillis())
                error=listOfNotNull(copy.title,copy.body).joinToString(". ");failed=true}}
            finally {if(text.trim()==requested&&latestProvider==providerKey)loading=false}
        }else {loading=false;suggestions=emptyList();suggestionsHeight=0;if(!focusedQuery)revealed=false}
    }
    LaunchedEffect(suggestions.isNotEmpty(),focusedQuery) {
        if(focusedQuery&&suggestions.isNotEmpty()&&!revealed){revealed=true;withFrameNanos {};firstResult.bringIntoView()}
    }
    LaunchedEffect(text){if(focusedQuery&&suggestionsScroll.value>0)suggestionsScroll.scrollTo(0)}
    val latestText by rememberUpdatedState(onTextChange)
    LaunchedEffect(text) { latestText(text) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if(externalLabel) Text(label,Modifier.fillMaxWidth().testTag("place-search-heading"),style=MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            text,
            { input ->
                text = input.take(160)
                focusedQuery = true
                if (value != null) onSelected(null)
            },
            Modifier.fillMaxWidth().then(if(label==tr("Ziel, Adresse oder Haltestelle")) Modifier.tutorialTarget("search-destination") else Modifier)
                .then(if(focusRequester!=null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged {fieldFocused=it.isFocused;onFocusChange(it.isFocused)},
            enabled = enabled,
            label = if(externalLabel || bare) null else { { Text(label) } },
            // The bare field's label moves into the host's frame while typing; otherwise it reads like a label.
            // Same size, weight and colour as the resting label of the destination field (bodyLarge).
            placeholder = if(bare && !fieldFocused) { { Text(label, style=MaterialTheme.typography.bodyLarge, color=MaterialTheme.colorScheme.onSurfaceVariant) } } else null,
            shape = dev.ambivalenton.openv.ui.theme.appCorner(if(expressive) 28.dp else 16.dp),
            colors = if(bare) OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    disabledBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                ) else
                OutlinedTextFieldDefaults.colors(
                    focusedContainerColor =
                        if (expressive) MaterialTheme.colorScheme.surfaceContainerHigh
                        else MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor =
                        if (expressive) MaterialTheme.colorScheme.surfaceContainerHigh
                        else MaterialTheme.colorScheme.surface,
                    focusedBorderColor =
                        if (expressive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                    unfocusedBorderColor =
                        if (expressive && outlinedAtRest) MaterialTheme.colorScheme.outline.copy(alpha = .55f)
                        else if (expressive) androidx.compose.ui.graphics.Color.Transparent
                        else MaterialTheme.colorScheme.outline,
                ),
            textStyle =
                if (expressive || bare) MaterialTheme.typography.titleLarge
                else MaterialTheme.typography.bodyLarge,
            singleLine = true,
            leadingIcon = { AppIcon(R.drawable.ic_place, null) },
            trailingIcon = {
                if (text.isNotEmpty())
                    IconButton(
                        {
                            text = ""
                            focusedQuery = false
                            onSelected(null)
                        },
                        enabled = enabled,
                    ) {
                        AppIcon(R.drawable.ic_close, tr("Eingabe löschen"))
                    }
            },
            // H03: free text alone is not a destination; the hint stays visible (also in minimalist mode).
            supportingText =
                if (chooseHint && value == null && text.isNotBlank()) {
                    { Text(tr("Wähle einen Ort aus den Vorschlägen."), Modifier.testTag("place-choose-hint")) }
                } else null,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = {
                // The only suggestion is taken over; otherwise the choice stays with the person.
                val only = suggestions.singleOrNull()
                if (value == null && only != null) {
                    focusedQuery = false; suggestions = emptyList(); text = only.name; error = null; loading = false
                    focusManager.clearFocus(force = true); keyboard?.hide(); onSelected(only)
                } else if (value != null) { focusManager.clearFocus(force = true); keyboard?.hide(); onImeSearch?.invoke() }
            }),
        )
        Box(Modifier.fillMaxWidth().height(3.dp)) {if(showBusy&&loading&&suggestions.isEmpty())CalmLoadingRail(Modifier.fillMaxWidth())}
        if (enabled && (suggestions.isNotEmpty() || (loading&&suggestionsHeight>0)))
            Surface(Modifier.then(if(bare) Modifier.padding(horizontal=8.dp) else Modifier),shape = dev.ambivalenton.openv.ui.theme.appCorner(16.dp),
                color=if(bare) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.background,tonalElevation=0.dp) {
                val minimum=if(loading)with(LocalDensity.current){suggestionsHeight.toDp()}.coerceAtMost(240.dp)else 0.dp
                Column(Modifier.heightIn(min=minimum,max=240.dp).onSizeChanged {if(it.height>0&&suggestions.isNotEmpty())suggestionsHeight=it.height}.verticalScroll(suggestionsScroll).testTag("place-suggestions")) {
                    if(suggestions.isEmpty())Text(tr("Orte werden gesucht …"),Modifier.padding(12.dp),style=MaterialTheme.typography.bodySmall)
                    suggestions.forEachIndexed { index,location ->
                        key(placeIdentity(location)) {
                        // A symbol for the kind of place (station, stop, address, street, café …).
                        val category=placeCategory(location)
                        Row(
                            Modifier.fillMaxWidth().then(if(index==0)Modifier.bringIntoViewRequester(firstResult) else Modifier)
                                .combinedClickable(
                                    onLongClickLabel=if(onSuggestionLongClick!=null)tr("Als Favorit hinzufügen") else null,
                                    onLongClick=onSuggestionLongClick?.let { callback -> {
                                        focusedQuery=false;suggestions=emptyList();error=null;loading=false
                                        focusManager.clearFocus(force=true);keyboard?.hide();callback(location)
                                    } },
                                    onClick={
                                    focusedQuery = false
                                    suggestions = emptyList()
                                    text = location.name
                                    error = null
                                    loading = false
                                    focusManager.clearFocus(force = true)
                                    keyboard?.hide()
                                    onSelected(location)
                                })
                                .heightIn(min = 48.dp)
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                                .testTag("place-suggestion-${category.name.lowercase()}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            PlaceCategoryIcon(category)
                            Text(
                                (location.favoriteLabel?.let {"$it\n${location.name}"} ?: location.name)+location.note?.let {"\n$it"}.orEmpty(),
                                Modifier.weight(1f).semantics {contentDescription=tr("%1\$s: %2\$s",tr(category.labelKey),location.favoriteLabel?.let {"$it, ${location.name}"} ?: location.name)},
                                maxLines = if(location.note!=null) 3 else 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    }
                }
            }
        error?.let {
            Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically) {
                Text(
                    it,
                    Modifier.weight(1f).testTag("place-search-error"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                if (failed && enabled) TextButton({ focusedQuery = true; retry++ }, Modifier.heightIn(min = 48.dp).testTag("place-search-retry")) {
                    Text(tr("Erneut suchen"))
                }
            }
        }
    }
}

/** Drawable for each kind of place; shared by the suggestion list and its tests. */
internal fun placeCategoryIcon(category: PlaceCategory): Int = when (category) {
    PlaceCategory.FAVORITE -> R.drawable.ic_star
    PlaceCategory.STATION -> R.drawable.ic_train
    PlaceCategory.SUBWAY -> R.drawable.ic_subway
    PlaceCategory.TRAM -> R.drawable.ic_tram
    PlaceCategory.BUS -> R.drawable.ic_bus
    PlaceCategory.FERRY -> R.drawable.ic_ferry
    PlaceCategory.STOP -> R.drawable.ic_stop
    PlaceCategory.AIRPORT -> R.drawable.ic_flight
    PlaceCategory.ADDRESS -> R.drawable.ic_address
    PlaceCategory.STREET -> R.drawable.ic_street
    PlaceCategory.LOCALITY -> R.drawable.ic_city
    PlaceCategory.FOOD -> R.drawable.ic_restaurant
    PlaceCategory.CAFE -> R.drawable.ic_coffee
    PlaceCategory.SHOP -> R.drawable.ic_shopping_bag
    PlaceCategory.HEALTH -> R.drawable.ic_hospital
    PlaceCategory.EDUCATION -> R.drawable.ic_school
    PlaceCategory.PARK -> R.drawable.ic_park
    PlaceCategory.SPORT -> R.drawable.ic_fitness_center
    PlaceCategory.CULTURE -> R.drawable.ic_culture
    PlaceCategory.WORK -> R.drawable.ic_work
    PlaceCategory.POI -> R.drawable.ic_place
    PlaceCategory.COORDINATE -> R.drawable.ic_gps
}

/** Transit places in the accent tone, everything else neutral: stops stand out in a mixed list. */
@Composable fun PlaceCategoryIcon(category: PlaceCategory, modifier: Modifier = Modifier) {
    val transit = category in setOf(PlaceCategory.STATION, PlaceCategory.SUBWAY, PlaceCategory.TRAM, PlaceCategory.BUS,
        PlaceCategory.FERRY, PlaceCategory.STOP, PlaceCategory.AIRPORT)
    val scheme = MaterialTheme.colorScheme
    Surface(modifier.size(36.dp), shape = dev.ambivalenton.openv.ui.theme.appCorner(12.dp),
        color = when { category == PlaceCategory.FAVORITE -> scheme.tertiaryContainer; transit -> scheme.primaryContainer; else -> scheme.surfaceContainerHighest },
        contentColor = when { category == PlaceCategory.FAVORITE -> scheme.onTertiaryContainer; transit -> scheme.onPrimaryContainer; else -> scheme.onSurfaceVariant }) {
        Box(contentAlignment = Alignment.Center) {
            AppIcon(placeCategoryIcon(category), null, Modifier.size(20.dp).testTag("place-category-${category.name.lowercase()}"))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProductChips(selected: Set<LineType>, onChange: (Set<LineType>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        selectableProducts
            .filterNot { it == LineType.ICE }
            .forEach { type ->
                FilterChip(
                    type in selected,
                    { onChange(if (type in selected) selected - type else selected + type) },
                    label = { Text(type.label) },
                )
            }
    }
    if (selected.isEmpty())
        Text(
            tr("Mindestens ein Verkehrsmittel wählen."),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
}

@Composable
fun DelayBadge(delay: DelayInfo) {
    val minute = delay.minutes
    val text =
        when {
            delay.cancelled -> tr("Fällt aus")
            minute == null -> tr("Nur Fahrplan")
            minute > 0 -> tr("+%1\$s Min.", minute)
            minute < 0 -> tr("%1\$s Min.", minute)
            else -> tr("+0 Min.")
        }
    val dark = MaterialTheme.colorScheme.background.luminance() < .3f
    val good =
        if (dark) androidx.compose.ui.graphics.Color(0xFF154C29)
        else androidx.compose.ui.graphics.Color(0xFFC0F0C6)
    val warning =
        if (dark) androidx.compose.ui.graphics.Color(0xFF664000)
        else androidx.compose.ui.graphics.Color(0xFFFFDDB0)
    val color =
        when {
            delay.cancelled || (minute ?: 0) >= 5 -> MaterialTheme.colorScheme.errorContainer
            minute == null -> MaterialTheme.colorScheme.surfaceVariant
            minute > 0 -> warning
            else -> good
        }
    val content =
        when {
            color == good ->
                if (dark) androidx.compose.ui.graphics.Color(0xFFC0F0C6)
                else androidx.compose.ui.graphics.Color(0xFF073915)
            color == warning ->
                if (dark) androidx.compose.ui.graphics.Color(0xFFFFDDB0)
                else androidx.compose.ui.graphics.Color(0xFF2A1800)
            else -> contentColorFor(color)
        }
    Surface(color = color, contentColor = content, shape = dev.ambivalenton.openv.ui.theme.appCorner(8.dp)) {
        Text(
            text,
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/** Coarse or precise access; either lets the automatic start determine a position. */
internal fun locationPermissionGranted(context: android.content.Context): Boolean =
    listOf(android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.ACCESS_FINE_LOCATION).any {
        androidx.core.content.ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

/** The app's own system settings page, where Android lists the location permission. */
internal fun openAppDetailsSettings(context: android.content.Context): Boolean =
    try {
        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            android.net.Uri.fromParts("package", context.packageName, null)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: android.content.ActivityNotFoundException) { false } catch (_: SecurityException) { false }
