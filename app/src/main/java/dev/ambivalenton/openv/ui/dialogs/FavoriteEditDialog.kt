// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.ui.dialogs

import dev.ambivalenton.openv.ui.components.ThemedButton
import dev.ambivalenton.openv.ui.components.ThemedOutlinedButton

import dev.ambivalenton.openv.ui.components.CalmVisibility
import dev.ambivalenton.openv.localization.tr

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import dev.ambivalenton.openv.ui.tutorial.FirstUseTutorialHost
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.components.*
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

fun minuteOfDay(input: String): Int? {
    val parts = input.split(':')
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    return if (hour in 0..23 && minute in 0..59) hour * 60 + minute else null
}

internal fun hhmm(value: Int) = "%02d:%02d".format(Locale.ROOT, value / 60, value % 60)

@Serializable
internal data class ScheduleDraft(
    val id: String = newId(),
    val days: Set<Int> = (1..7).toSet(),
    val from: String = "18:00",
    val until: String = "00:00",
) {
    val valid
        get() = days.isNotEmpty() && minuteOfDay(from) != null && minuteOfDay(until) != null

    fun rule() = HighlightRule(days, minuteOfDay(from)!!, minuteOfDay(until)!!)
}

internal fun HighlightRule.draft() =
    ScheduleDraft(days = days, from = hhmm(fromMinute), until = hhmm(untilMinute))

internal val scheduleSaver =
    Saver<ScheduleDraft, String>(
        save = { Json.encodeToString(it) },
        restore = { Json.decodeFromString(it) },
    )
internal val schedulesSaver =
    Saver<List<ScheduleDraft>, String>(
        save = { Json.encodeToString(it) },
        restore = { Json.decodeFromString(it) },
    )

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FavoriteEditDialog(
    existing: Favorite?,
    providerKey: String,
    search: suspend (String) -> List<Location>,
    onSave: (Favorite) -> Unit,
    onDismiss: () -> Unit,
    initialLocation: Location? = null,
    showSchedules:Boolean=true,
    pairedLocation:Location?=null,
    onDelete:(()->Unit)?=null,
    /** Role of the favourite's node in the network plan; null hides the choice. */
    networkRole:NodeRole?=null,
    onNetworkRole:((NodeRole)->Unit)?=null,
    /** Other favourite that already owns part of the given time windows (time-dependent location). */
    scheduleConflict:((List<HighlightRule>)->Favorite?)?=null,
    /** Places worth saving (recent destinations, board stops), shown on an empty search. */
    suggestions:List<FavoritePlaceSuggestion> = emptyList(),
    /** The other favourites (their colours keep the new one apart). */
    otherFavorites:List<Favorite> = emptyList(),
) {
    var role by rememberSaveable(existing?.id) {mutableStateOf(networkRole ?: NodeRole.AUTO)}
    // M02: a place handed over (e.g. from a search hit) is enough to save: name and neutral symbol are suggested.
    var label by rememberSaveable(existing?.id) { mutableStateOf(existing?.label ?: initialLocation?.takeIf { it.routable }?.let(::smartFavoriteName).orEmpty()) }
    var location by
        rememberSaveable(existing?.id, initialLocation, stateSaver = locationSaver) {
            mutableStateOf(existing?.location ?: initialLocation)
        }
    var icon by
        rememberSaveable(existing?.id) {
            mutableStateOf<FavoriteIcon?>(
                existing?.icon?.let { if (it == FavoriteIcon.HEART) FavoriteIcon.FAVORITE else it }
                    ?: initialLocation?.takeIf { existing == null && it.routable }?.let(::guessFavoriteIcon)
            )
        }
    var extras by rememberSaveable {mutableStateOf(false)}
    var filterEnabled by rememberSaveable(existing?.id) {mutableStateOf(existing?.transportFilter!=null)}
    val transportSaver=Saver<Set<FavoriteTransport>,String>(
        save={it.joinToString(","){v->v.name}},
        restore={raw->raw.split(",").mapNotNull {name->FavoriteTransport.entries.firstOrNull {it.name==name}}.toSet()})
    var transportFilter by rememberSaveable(existing?.id,stateSaver=transportSaver) {
        mutableStateOf(existing?.transportFilter ?: FavoriteTransport.entries.toSet())
    }
    var nameIsSuggested by rememberSaveable(existing?.id) {mutableStateOf(existing==null)}
    var shape by rememberSaveable(existing?.id) { mutableStateOf(existing?.shape ?: IconStyle.SQUIRCLE) }
    var accentSeed by rememberSaveable(existing?.id) { mutableStateOf(existing?.accentSeed) }
    // What the person chose themselves is never replaced by a suggestion.
    var iconChosen by rememberSaveable(existing?.id) { mutableStateOf(false) }
    var colorChosen by rememberSaveable(existing?.id) { mutableStateOf(false) }
    var moreOpen by rememberSaveable(existing?.id) { mutableStateOf(false) }
    // "Mehr" at the colour dots opens the options with the full colour choice already unfolded.
    val colorSection = rememberSaveable(existing?.id) { mutableStateOf(false) }
    val themeAccent = MaterialTheme.colorScheme.primary.toArgb().toLong() and 0xffffffffL
    val appSettings = dev.ambivalenton.openv.ui.theme.LocalAppSettings.current
    val palette = remember(appSettings.appTheme, appSettings.appThemeColors, themeAccent) {
        dev.ambivalenton.openv.ui.theme.favoriteThemeColors(appSettings, themeAccent).map { it.color } }
    // A new favourite starts in the colour that stands apart most from the others.
    LaunchedEffect(Unit) { if (existing == null && accentSeed == null) accentSeed = suggestFavoriteAccent(otherFavorites, palette) }
    var colorPicker by rememberSaveable { mutableStateOf(false) }
    var highlighted by rememberSaveable(existing?.id) { mutableStateOf(existing?.rule != null) }
    var highlight by
        rememberSaveable(existing?.id, stateSaver = scheduleSaver) {
            mutableStateOf((existing?.rule ?: HighlightRule()).draft())
        }
    var hideByTime by
        rememberSaveable(existing?.id) {
            mutableStateOf(existing?.hiddenRules?.isNotEmpty() == true)
        }
    var hidden by
        rememberSaveable(existing?.id, stateSaver = schedulesSaver) {
            mutableStateOf(
                existing?.hiddenRules?.map { it.draft() }?.ifEmpty { null }
                    ?: listOf(ScheduleDraft())
            )
        }
    val colorPreview =
        dev.ambivalenton.openv.ui.theme.favoriteColors(
            accentSeed,
            MaterialTheme.colorScheme.background.luminance() < .3f,
            MaterialTheme.colorScheme,
        )
    // Only one favourite may supply the location at any time of the week.
    val conflict=if(hideByTime && hidden.isNotEmpty() && hidden.all {it.valid}) scheduleConflict?.invoke(hidden.map {it.rule()}) else null
    val valid = conflict==null &&
        (pairedLocation==null || location?.let {sameLocation(it,pairedLocation)}!=true) && (!filterEnabled || transportFilter.isNotEmpty()) && (!highlighted || highlight.valid) &&
            (!hideByTime || (hidden.isNotEmpty() && hidden.all { it.valid }))
    val scroll=rememberScrollState()
    val target=rememberJourneyScrollTarget(scroll)
    DirectModalSheet(onDismiss,target,"favorite-editor-sheet",gesturesEnabled=!colorPicker && !extras) {motion,dismiss->
        FirstUseTutorialHost(null,Modifier.fillMaxSize(),modal=true) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)).imePadding()) {
            Row(Modifier.fillMaxWidth().heightIn(min=64.dp).padding(start=24.dp,end=8.dp),verticalAlignment=Alignment.CenterVertically) {
                FittedText(if(existing==null) tr("Favorit hinzufügen") else tr("Favorit bearbeiten"),
                    Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                IconButton(dismiss,Modifier.testTag("favorite-editor-close")) {AppIcon(R.drawable.ic_close,tr("Schließen"))}
            }
        Column(
            Modifier.weight(1f).fillMaxWidth()
                .verticalScroll(scroll,enabled=false,overscrollEffect=null)
                .directSheetScrollSemantics(scroll,motion::cancelContentFling)
                // 2.1: without the save bar below, the last symbol row keeps the navigation pill's
                // height free, so it can always be scrolled into view and tapped.
                .testTag("favorite-editor-scroll").padding(start=24.dp,end=24.dp,top=14.dp,
                    bottom=14.dp+if(favoriteCreationStage(location,icon)==FavoriteCreationStage.NAME) 0.dp else LocalNavigationClearance.current
                        +WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            verticalArrangement=Arrangement.spacedBy(14.dp),
        ) {
            if(existing==null) FavoriteCreatorBody(location,icon,label,nameIsSuggested,accentSeed,shape,providerKey,search,suggestions,otherFavorites,
                onLocation={picked->
                    location=picked
                    if(picked?.routable==true) {
                        if(!iconChosen) icon=guessFavoriteIcon(picked)
                        if(nameIsSuggested || label.isBlank()) {
                            label=icon?.takeIf {iconChosen}?.let {favoriteNameFor(it,picked)} ?: smartFavoriteName(picked);nameIsSuggested=true
                        }
                    }
                },
                onIcon={choice->
                    icon=choice;iconChosen=true
                    if(nameIsSuggested || label.isBlank()) {label=favoriteNameFor(choice,location);nameIsSuggested=true}
                },
                onName={label=it;nameIsSuggested=false},
                onColor={accentSeed=it;colorChosen=true},
                onMoreOptions={moreOpen=true;colorSection.value=true},
                autoFocus=initialLocation==null)
            else ProgressiveFavoriteFields(location,icon,label,nameIsSuggested,providerKey,search,
                onLocation={picked->
                    location=picked
                    // M02: the chosen place is enough to save: a neutral symbol and the place's name
                    // are suggested at once; "Name und Symbol ändern" stays optional.
                    if(existing==null && picked?.routable==true) {
                        if(icon==null) icon=FavoriteIcon.PLACE
                        if(nameIsSuggested || label.isBlank()) {label=suggestedFavoriteName(picked);nameIsSuggested=true}
                    }
                },detailsCollapsible=existing==null,onIcon={choice->
                    val suggested=nameIsSuggested || label.isBlank()
                    label=favoriteNameAfterIconChoice(label,nameIsSuggested,choice.title)
                    nameIsSuggested=suggested
                    icon=choice
                },onNameEdited={label=it;nameIsSuggested=false})
            CalmVisibility(if(existing==null) location?.routable==true else favoriteCreationStage(location,icon)==FavoriteCreationStage.NAME) {
            Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
            @Composable fun moreOptions() {
            Section(tr("Form")) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconStyle.entries.forEach { choice ->
                        FilterChip(shape == choice, { shape = choice },
                            leadingIcon = {
                                // A plain filled path: no surface layer, no outline for the system renderer.
                                Box(Modifier.size(24.dp).background(colorPreview.accent,
                                    dev.ambivalenton.openv.widget.GlanceThemeHelper.composeShape(choice)))
                            }, label = { Text(choice.title) })
                    }
                }
                FavoriteBadge(Favorite(label = label, location = location ?: Location(tr("Vorschau")),
                    icon = icon ?: FavoriteIcon.PLACE, accentSeed = accentSeed, shape = shape), size = 64.dp)
            }
            Section(tr("Farbe"), expansionState = colorSection) {
                FavoriteColorControls(accentSeed,colorPicker,{colorPicker=true},{colorPicker=false},{accentSeed=it})
            }
            if(pairedLocation!=null && location?.let {sameLocation(it,pairedLocation)}==true)Text(tr("Bitte zwei unterschiedliche Orte wählen."),color=MaterialTheme.colorScheme.error)
            SectionLink(tr("Extras"),{extras=true},Modifier.testTag("favorite-extras"))
            if(extras) FavoriteOptionsDialog(tr("Extras"),{extras=false}) {
            Section(tr("Verbindungen filtern")) {
                ToggleRow(tr("Eigener Verkehrsfilter"),tr("Gilt für die ganze Verbindung zu diesem Favoriten."),filterEnabled,{filterEnabled=it})
                if(filterEnabled) {
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        TextButton({transportFilter=FavoriteTransport.entries.toSet()}) {Text(tr("Alle"))}
                        TextButton({transportFilter=emptySet()}) {Text(tr("Keine"))}
                    }
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        FavoriteTransport.entries.forEach {choice->
                            FilterChip(choice in transportFilter,{
                                transportFilter=if(choice in transportFilter)transportFilter-choice else transportFilter+choice
                            },label={Text(choice.title)},modifier=Modifier.testTag("favorite-transport-${choice.name}"))
                        }
                    }
                    if(transportFilter.isEmpty())Text(tr("Bitte mindestens ein Verkehrsmittel auswählen."),color=MaterialTheme.colorScheme.error)
                }
            }
            if(showSchedules) {
            Section(tr("Zeitlich hervorheben")) {
                if (highlighted) ScheduleWindow(highlight, { highlight = it })
                ToggleRow(
                    tr("Hervorheben einschalten"),
                    tr("Zur gewählten Zeit zeigt die Übersicht nur die hervorgehobenen Verbindungen."),
                    highlighted,
                    { highlighted = it },
                )
            }
            Section(tr("Zeitabhängiger Standort")) {
                if (hideByTime) {
                    hidden.forEachIndexed { index, draft ->
                        key(draft.id) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    FittedText(
                                        tr("Zeitfenster %1\$s", index + 1),
                                        Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    IconButton({
                                        hidden = hidden.filterNot { it.id == draft.id }
                                    }) {
                                        AppIcon(
                                            R.drawable.ic_delete,
                                            tr("Zeitfenster %1\$s entfernen", index + 1),
                                        )
                                    }
                                }
                                ScheduleWindow(
                                    draft,
                                    { updated ->
                                        hidden =
                                            hidden.map { if (it.id == draft.id) updated else it }
                                    },
                                )
                            }
                            HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        }
                    }
                    ThemedOutlinedButton(
                        { hidden = hidden + ScheduleDraft() },
                        enabled = hidden.size < 8,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text(tr("Zeitfenster hinzufügen"))
                    }
                    conflict?.let {other->
                        Text(tr("Zu dieser Zeit gibt bereits „%1\$s“ den Standort vor. Bitte andere Tage oder Uhrzeiten wählen.",other.label),
                            Modifier.testTag("favorite-schedule-conflict"),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        tr("Ein passendes Zeitfenster genügt. Zu jeder Zeit kann nur ein Favorit den Standort vorgeben."),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                ToggleRow(tr("Standort nach Zeit einschalten"), tr("Zur gewählten Zeit gilt dieser Ort als dein Standort, solange kein GPS-Standort vorliegt und du keinen Start gewählt hast."), hideByTime, { hideByTime = it })
            }
            }
            if(onNetworkRole!=null && existing!=null) Section(tr("Rolle im Netzplan")) {
                Text(tr("Ziele beenden einen Weg in der Übersicht. Umstiegspunkte werden durchfahren: Bei Zuhause – Bahnhof – Arbeit sucht die App direkt von Zuhause zur Arbeit."),
                    style=MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf(NodeRole.AUTO to tr("Automatisch"),NodeRole.DESTINATION to tr("Ziel"),NodeRole.TRANSFER to tr("Umstiegspunkt")).forEach {(value,title)->
                        FilterChip(role==value,{role=value},label={Text(title)},modifier=Modifier.testTag("favorite-role-${value.name.lowercase()}"))
                    }
                }
                if(role==NodeRole.AUTO)Text(tr("Automatisch: Bahn-, Bus- und Haltestellensymbole mit mindestens zwei Verbindungen und neutrale Orte mitten in einer Reihe sind Umstiegspunkte."),
                    style=MaterialTheme.typography.bodySmall)
            }
            }
            }
            // For a new favourite everything beyond place, kind and colour waits behind one disclosure.
            if(existing==null) SettingsDisclosure(tr("Weitere Optionen"),"favorite-more-options",expanded=moreOpen,onExpandedChange={moreOpen=it}) {
                Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {moreOptions()}
            } else moreOptions()
            if(existing!=null && onDelete!=null) TextButton(onDelete,Modifier.testTag("favorite-delete")) {
                AppIcon(R.drawable.ic_delete,null,tint=MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text(tr("Favorit löschen"),color=MaterialTheme.colorScheme.error)
            }

            }
            }
        }
        if(existing==null || favoriteCreationStage(location,icon)==FavoriteCreationStage.NAME) {
            Box(Modifier.fillMaxWidth().padding(start=24.dp,end=24.dp,top=8.dp,bottom=14.dp+LocalNavigationClearance.current)) {
            ThemedButton(
                onClick = {
                    location?.let {
                        val updated=Favorite(
                                id = existing?.id ?: newId(),
                                label = label.trim(),
                                location = it,
                                icon = icon ?: FavoriteIcon.PLACE,
                                shape = shape,
                                counterpole = null,
                                transportFilter = if(filterEnabled)transportFilter else null,
                                rule = if(!showSchedules)existing?.rule else if (highlighted) highlight.rule() else null,
                                accentSeed = accentSeed,
                                hiddenRules =
                                    if(!showSchedules)existing?.hiddenRules.orEmpty() else if (hideByTime) hidden.map { draft -> draft.rule() }
                                    else emptyList(),
                            )
                        if(onNetworkRole!=null && existing!=null && role!=networkRole)onNetworkRole(role)
                        onSave(updated)
                    }
                },
                enabled = label.isNotBlank() && icon!=null && location?.routable == true && valid,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("favorite-save"),
            ) {
                Text(if (existing == null) tr("Favorit speichern") else tr("Speichern"))
            }
            }
        }
        }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ScheduleWindow(draft: ScheduleDraft, onChange: (ScheduleDraft) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        listOf(tr("Mo"), tr("Di"), tr("Mi"), tr("Do"), tr("Fr"), tr("Sa"), tr("So")).forEachIndexed { index, day ->
            FilterChip(
                index + 1 in draft.days,
                {
                    onChange(
                        draft.copy(
                            days =
                                if (index + 1 in draft.days) draft.days - (index + 1)
                                else draft.days + (index + 1)
                        )
                    )
                },
                label = { Text(day) },
            )
        }
    }
    if (draft.days.isEmpty())
        Text(
            tr("Bitte mindestens einen Tag wählen."),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    ScheduleTimeField(tr("Ab"), minuteOfDay(draft.from) ?: 18 * 60) {
        onChange(draft.copy(from = hhmm(it)))
    }
    ScheduleTimeField(tr("Bis"), minuteOfDay(draft.until) ?: 0) {
        onChange(draft.copy(until = hhmm(it)))
    }
    Text(
        tr("Gleiche Uhrzeiten: ganztägig. Über Mitternacht zählt der Starttag."),
        style = MaterialTheme.typography.bodySmall,
    )
}
