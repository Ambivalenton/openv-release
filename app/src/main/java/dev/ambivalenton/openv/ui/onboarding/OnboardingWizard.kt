// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.onboarding

import dev.ambivalenton.openv.ui.components.ThemedButton
import dev.ambivalenton.openv.ui.components.ThemedOutlinedButton

import dev.ambivalenton.openv.ui.components.CalmVisibility
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.ui.components.AppDialog as Dialog
import androidx.compose.ui.window.DialogProperties
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.components.*
import dev.ambivalenton.openv.ui.screens.OverviewSentenceSettings
import dev.ambivalenton.openv.ui.screens.SettingsRadioRow
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import dev.ambivalenton.openv.ui.theme.appThemeBackground
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable fun FirstLaunchOnboarding(
    store: StoredState,
    onChange: suspend ((OnboardingDraft)->OnboardingDraft)->Boolean,
    onFinish: suspend ()->Boolean,
    search: suspend (String)->List<Location>,
    onLocationGranted: ()->Unit,
    example: suspend (Favorite,AppSettings)->Trip?={_,_->null},
    /** H01: straight to the first search (finishes the setup with the defaults, no permission asked). */
    onSearchNow: (()->Unit)?=null,
) {
    val permissions=rememberOnboardingPermissions(onLocationGranted)
    // H01: the first view says what the app is for; the setup wizard is optional.
    var wizard by rememberSaveable {mutableStateOf(store.onboardingDraft!=null || onSearchNow==null)}
    if(!wizard && onSearchNow!=null) FirstLaunchIntro(onSearch=onSearchNow,onSetup={wizard=true})
    else OnboardingWizard(store,permissions,onChange,onFinish,search,example,
        onBackToIntro=if(onSearchNow!=null) ({wizard=false}) else null,onSearchNow=onSearchNow)
}

/**
 * H01: the first view. Purpose and main action are clear at once: "Verbindung suchen" opens the search
 * with start and destination (one tap, no permission, no design choice). Setting the app up and
 * importing a backup are offered beside it. Location is asked for at "Meinen Standort nutzen",
 * notifications when a journey companion starts.
 */
@Composable fun FirstLaunchIntro(onSearch: ()->Unit, onSetup: ()->Unit) {
    var importSheet by rememberSaveable {mutableStateOf(false)}
    var busy by remember {mutableStateOf(false)}
    Box(Modifier.fillMaxSize().appThemeBackground().testTag("first-launch-intro")) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState())
            .padding(horizontal=24.dp,vertical=28.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Spacer(Modifier.weight(1f))
            Text(tr("Öpen-V"),style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary)
            Text(tr("Finde deine nächste Bus- oder Bahnverbindung."),Modifier.semantics {heading()}.testTag("intro-purpose"),
                style=MaterialTheme.typography.headlineMedium)
            Text(tr("Kostenlos, ohne Werbung und ohne Konto. Standort und Benachrichtigungen fragt Öpen-V erst, wenn du sie brauchst."),
                style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            ThemedButton({if(!busy){busy=true;onSearch()}},Modifier.fillMaxWidth().heightIn(min=56.dp).testTag("intro-search")) {
                Text(tr("Verbindung suchen"),style=MaterialTheme.typography.titleMedium)
            }
            ThemedOutlinedButton(onSetup,Modifier.fillMaxWidth().heightIn(min=52.dp).testTag("intro-setup")) {Text(tr("App einrichten"))}
            TextButton({importSheet=true},Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("intro-import")) {Text(tr("Sicherung importieren"))}
            Spacer(Modifier.weight(.4f))
        }
        if(importSheet) OnboardingImportSheet {importSheet=false}
    }
}

/** Fixed chrome and a scrolling body keep small displays and large fonts usable. */
@Composable fun OnboardingWizard(
    store: StoredState,
    permissions: SetupPermissions,
    onChange: suspend ((OnboardingDraft)->OnboardingDraft)->Boolean,
    onFinish: suspend ()->Boolean,
    search: suspend (String)->List<Location>,
    example: suspend (Favorite,AppSettings)->Trip?={_,_->null},
    /** H01: Android back on the first step returns to the first view. */
    onBackToIntro: (()->Unit)?=null,
    /** H01: "Zur Suche" ends the setup and opens the search. */
    onSearchNow: (()->Unit)?=null,
) {
    val draft=store.onboardingDraft ?: OnboardingDraft(settings=store.settings)
    val step=draft.step.coerceIn(0,ONBOARDING_STEPS-1)
    val scope=rememberCoroutineScope()
    var writing by remember {mutableStateOf(false)}
    var error by rememberSaveable {mutableStateOf(false)}
    var themePicker by rememberSaveable {mutableStateOf(false)}
    var boardPicker by rememberSaveable {mutableStateOf(false)}
    // Import a backup from the first step instead of setting up again.
    var importSheet by rememberSaveable {mutableStateOf(false)}
    var favoriteLabel by rememberSaveable {mutableStateOf("")}
    var favoriteLocation by rememberSaveable(stateSaver=locationSaver) {mutableStateOf<Location?>(null)}
    var favoriteIcon by rememberSaveable {mutableStateOf<FavoriteIcon?>(null)}
    var suggestedName by rememberSaveable {mutableStateOf(true)}
    val busy=writing || permissions.busy
    val canSaveFavorite=favoriteLocation?.routable==true && draft.favorites.size+store.favorites.size<40
    fun commit(action: suspend ()->Boolean,afterSuccess: ()->Unit={}) {
        if(busy) return
        writing=true;error=false
        scope.launch {
            try {if(action()) afterSuccess() else error=true}
            catch(e: CancellationException) {throw e}
            catch(_: Exception) {error=true}
            finally {writing=false}
        }
    }
    fun change(action: (OnboardingDraft)->OnboardingDraft) = commit({onChange(action)})
    OpenVTheme(draft.settings) {
        Dialog(onDismissRequest={if(step>0 && !busy) change {it.copy(step=step-1)} else if(!busy) onBackToIntro?.invoke()},
            properties=DialogProperties(usePlatformDefaultWidth=false,dismissOnClickOutside=false)) {
            // The window stays clear of status bar, navigation bar, cut-outs and keyboard (build 67):
            // before, the system navigation bar could lie over the lower buttons.
            Surface(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).widthIn(max=600.dp).fillMaxWidth(.96f).fillMaxHeight(.97f)
                .testTag("first-launch-onboarding"),shape=MaterialTheme.shapes.extraLarge,
                tonalElevation=6.dp) {
                Column(Modifier.appThemeBackground().padding(horizontal=20.dp,vertical=16.dp)) {
                    FittedText(tr("Willkommen bei Öpen-V"),style=MaterialTheme.typography.titleLarge)
                    Text(tr("Schritt %1\$s von %2\$s",step+1,ONBOARDING_STEPS),Modifier.padding(top=6.dp),
                        style=MaterialTheme.typography.labelMedium)
                    Row(Modifier.fillMaxWidth().padding(vertical=12.dp).semantics(mergeDescendants=true) {
                        progressBarRangeInfo=ProgressBarRangeInfo((step+1).toFloat(),1f..ONBOARDING_STEPS.toFloat())
                    },horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        repeat(ONBOARDING_STEPS) {index->
                            Box(Modifier.weight(if(index==step) 2f else 1f).height(6.dp)
                                .background(if(index<=step) MaterialTheme.colorScheme.primary else
                                    MaterialTheme.colorScheme.outlineVariant,CircleShape))
                        }
                    }
                    AnimatedContent(step,modifier=Modifier.weight(1f),label="onboarding-step",
                        transitionSpec={
                            val direction=if(targetState>initialState) 1 else -1
                            if(draft.settings.quietMode) (EnterTransition.None togetherWith ExitTransition.None) else (slideInHorizontally(spring(dampingRatio=.82f,stiffness=420f)){it/5*direction}+fadeIn(tween(180))) togetherWith
                                (slideOutHorizontally(tween(180)){-it/8*direction}+fadeOut(tween(120)))
                        }) {page->
                        key(page) {
                            BoxWithConstraints(Modifier.fillMaxSize()) {
                            val fittedOverview=page==3 && maxHeight>=460.dp && LocalDensity.current.fontScale<=1.3f
                            Column(Modifier.fillMaxSize().then(if(fittedOverview)Modifier else Modifier.verticalScroll(rememberScrollState())).testTag("onboarding-step-$page"),
                                verticalArrangement=Arrangement.spacedBy(if(page==3)8.dp else 14.dp)) {
                                FittedText(when(page) {
                                    0->tr("Standort & Hinweise");1->tr("Dein Look");2->tr("Favoriten")
                                    3->tr("Deine Übersicht");4->tr("Dein Netzplan");else->tr("Bereit für deine Wege")
                                },Modifier.semantics {heading()},style=MaterialTheme.typography.headlineSmall)
                                when(page) {
                                    0->PermissionSetupContent(permissions)
                                    1->{
                                        Text(tr("Wähle deinen Stil. Die Vorschau reagiert direkt auf deine Auswahl."))
                                        AppThemeSelector(draft.settings) {if(!busy) themePicker=true}
                                        BoardDesignSelector(store.copy(settings=draft.settings)) {if(!busy) boardPicker=true}
                                    }
                                    2->OnboardingFavorites(draft,search,busy,favoriteLocation,favoriteIcon,favoriteLabel,suggestedName,
                                        onLocation={favoriteLocation=it},onIcon={choice->
                                            val suggested=suggestedName || favoriteLabel.isBlank()
                                            favoriteLabel=favoriteNameAfterIconChoice(favoriteLabel,suggestedName,choice.title)
                                            suggestedName=suggested;favoriteIcon=choice
                                        },onNameEdited={favoriteLabel=it;suggestedName=false})
                                    3->{
                                        // One short line and one row of choices: the example below keeps the room
                                        // (build 67; the long explanations squeezed it before).
                                        Text(tr("Wie ausführlich sollen die Texte sein?"),style=MaterialTheme.typography.bodyMedium)
                                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().testTag("onboarding-sentence-presets")) {
                                            SentencePreset.entries.forEachIndexed {index,preset->
                                                SegmentedButton(draft.settings.overview.sentencePreset()==preset,{
                                                    change {it.copy(settings=it.settings.copy(overview=it.settings.overview.withSentencePreset(preset)))}
                                                },SegmentedButtonDefaults.itemShape(index,SentencePreset.entries.size),icon={}) {
                                                    Text(when(preset) {
                                                        SentencePreset.COMPACT->tr("Kompakt")
                                                        SentencePreset.EVERYDAY->tr("Alltag")
                                                        SentencePreset.DETAILED->tr("Alle Details")
                                                    },maxLines=1)
                                                }
                                            }
                                        }
                                        OnboardingOverviewPreview(draft.favorites.firstOrNull() ?: store.favorites.firstOrNull(),
                                            draft.settings,permissions,example,modifier=if(fittedOverview)Modifier.weight(1f) else Modifier,fillHeight=fittedOverview)
                                        Text(tr("Tippen öffnet die Fahrt, Halten sucht weitere Verbindungen."),
                                            Modifier.testTag("onboarding-overview-actions"),style=MaterialTheme.typography.bodySmall,
                                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    4->NetworkPlanGuide(draft.settings.networkHoldAction)
                                    else->{
                                        AppOutlinedCard(Modifier.fillMaxWidth().testTag("onboarding-long-press-gestures")) {
                                            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                                Text(tr("Einmal länger drücken"),style=MaterialTheme.typography.titleMedium)
                                                Text(tr("Halte einen Suchtreffer gedrückt, um ihn direkt als Favorit anzulegen."))
                                                // M01: generated from the current hold action instead of a fixed promise.
                                                Text(dev.ambivalenton.openv.ui.network.networkHoldHint(draft.settings.networkHoldAction)+" "+
                                                    tr("Unter Suchen fügst du mit „Favorit hinzufügen“ weitere Favoriten hinzu; das Zahnrad beim Bearbeiten öffnet die Favoriten-Einstellungen."),
                                                    Modifier.testTag("onboarding-hold-hint"))
                                                Text(tr("Halte einen Verbindungstext in der Übersicht gedrückt, um mehrere passende Fahrten im Balkendiagramm zu suchen."))
                                                Text(tr("Halte den Pin-Button einer Verbindung gedrückt, um sie für später vorzumerken. Der Reisebegleiter startet dabei nicht. Ein kurzer Tipp auf den Pin startet die Begleitung."))
                                            }
                                        }
                                        AppThemeThumbnail(draft.settings)
                                        Text(tr("App-Design: %1\$s",draft.settings.appTheme?.title ?: tr("Individuell / System")))
                                        Text(tr("Anzeigetafel: %1\$s",draft.settings.board.design.title))
                                        Text(tr("Neue Favoriten: %1\$s",draft.favorites.size))
                                        draft.favorites.forEach {Text(it.label,style=MaterialTheme.typography.bodyMedium)}
                                        Text(if(permissions.location==SetupLocationAccess.NONE) tr("Standort: später einrichten") else tr("Standortzugriff ist freigegeben"))
                                        Text(if(permissions.notificationsEnabled) tr("Benachrichtigungen sind aktiviert") else tr("Benachrichtigungen: später einrichten"))
                                        Text(tr("Du kannst all diese Einstellungen jederzeit später im Menü unter 'Einstellungen' anpassen."),
                                            style=MaterialTheme.typography.bodyLarge)
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                            }
                        }
                    }
                    if(error) Text(tr("Die Auswahl konnte nicht gespeichert werden. Bitte erneut versuchen."),
                        Modifier.semantics {liveRegion=LiveRegionMode.Polite},color=MaterialTheme.colorScheme.error)
                    HorizontalDivider(Modifier.padding(top=12.dp,bottom=8.dp))
                    // H13: with long labels or large fonts the actions move into a second line.
                    FlowRow(Modifier.fillMaxWidth(),itemVerticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        if(step>0) TextButton({change {it.copy(step=step-1)}},enabled=!busy) {Text(tr("Zurück"))}
                        else ThemedOutlinedButton({importSheet=true},enabled=!busy,modifier=Modifier.testTag("onboarding-import")) {Text(tr("Sicherung importieren"))}
                        Spacer(Modifier.weight(1f))
                        ThemedButton({when {
                            step==2->{
                                val selected=favoriteLocation ?: return@ThemedButton
                                val favorite=Favorite(label=favoriteLabel.trim().ifBlank {selected.name}.take(80),
                                    location=selected,icon=favoriteIcon ?: FavoriteIcon.PLACE)
                                commit({onChange {it.copy(step=3,favorites=it.favorites+favorite,offerMoreFavorites=false)}}) {
                                    favoriteLabel="";favoriteLocation=null;favoriteIcon=null;suggestedName=true
                                }
                            }
                            step<ONBOARDING_STEPS-1->change {it.copy(step=step+1)}
                            else->commit(onFinish)
                        }},enabled=!busy && (step!=2 || canSaveFavorite),modifier=Modifier.testTag("onboarding-next")) {
                            Text(if(step<ONBOARDING_STEPS-1) tr("Weiter") else tr("Einrichtung abschließen"))
                        }
                    }
                    // H01: two clearly named ways out: skip only this step, or end the setup and search.
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                        if(step<ONBOARDING_STEPS-1) TextButton({change {it.copy(step=step+1,offerMoreFavorites=false)}},enabled=!busy,
                            modifier=Modifier.heightIn(min=48.dp).testTag("onboarding-skip")) {Text(tr("Schritt überspringen"))}
                        else Spacer(Modifier.width(1.dp))
                        TextButton({commit(onFinish) {onSearchNow?.invoke()}},enabled=!busy,
                            modifier=Modifier.heightIn(min=48.dp).testTag("onboarding-to-search")) {Text(tr("Zur Suche"))}
                    }
                }
            }
            if(importSheet) OnboardingImportSheet {importSheet=false}
            if(themePicker) AppThemePicker(draft.settings.appTheme,{theme->
                change {it.copy(settings=it.settings.copy(appTheme=theme))};themePicker=false
            },{themePicker=false},settings=draft.settings,update={transform->change {it.copy(settings=transform(it.settings))}})
            if(boardPicker) BoardDesignPicker(store.copy(settings=draft.settings),{design->
                change {it.copy(settings=it.settings.copy(board=it.settings.board.copy(design=design)))};boardPicker=false
            },{boardPicker=false},update={transform->change {it.copy(settings=transform(it.settings))}})
        }
    }
}

@Composable private fun PermissionSetupContent(p: SetupPermissions) {
    Text(tr("Du entscheidest, was du freigibst. Du kannst jeden Schritt ohne Freigabe fortsetzen. Ohne Standort wählst du deinen Start selbst."))
    AppElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            FittedText(tr("Standort"),style=MaterialTheme.typography.titleMedium)
            Text(tr("Mit deinem Standort hilft dir Öpen-V, dich unterwegs leichter zu orientieren. Du kannst deinen Start auch selbst eingeben."))
            Text(when(p.location) {
                SetupLocationAccess.NONE->tr("Noch nicht freigegeben")
                SetupLocationAccess.APPROXIMATE->tr("Ungefährer Standort freigegeben")
                SetupLocationAccess.PRECISE->tr("Präziser Standort freigegeben")
            },style=MaterialTheme.typography.labelLarge)
            Text(tr("Du entscheidest, wie genau du deinen Standort teilen möchtest. Das lässt sich später jederzeit ändern."),
                style=MaterialTheme.typography.bodySmall)
            if(!p.locationEnabled && p.location!=SetupLocationAccess.NONE) Text(tr("Der Gerätestandort ist ausgeschaltet."))
            // H01: the permission is asked for only on this explicit action.
            if(p.location==SetupLocationAccess.NONE || !p.locationEnabled) ThemedOutlinedButton(p.requestLocation,enabled=!p.busy) {
                Text(if(p.location==SetupLocationAccess.NONE) tr("Meinen Standort nutzen") else tr("Standortdienste öffnen"))
            }
        }
    }
    AppElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            FittedText(tr("Benachrichtigungen"),style=MaterialTheme.typography.titleMedium)
            Text(tr("Erhalte unterwegs hilfreiche Hinweise, auch wenn du gerade nicht auf die App schaust."))
            // H01: notifications are asked for when a journey companion starts, not here.
            Text(if(p.notificationsEnabled) tr("Aktiviert") else tr("Öpen-V fragt danach, wenn du eine Reisebegleitung startest."),
                style=MaterialTheme.typography.labelLarge)
        }
    }
    Text(tr("Alles ist freiwillig. Du kannst auch ohne Freigaben weitermachen und dich später in Ruhe entscheiden."),
        style=MaterialTheme.typography.bodySmall)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun OnboardingFavorites(
    draft: OnboardingDraft,search: suspend (String)->List<Location>,busy: Boolean,
    location:Location?,icon:FavoriteIcon?,label:String,nameIsSuggested:Boolean,
    onLocation:(Location?)->Unit,onIcon:(FavoriteIcon)->Unit,onNameEdited:(String)->Unit,
) {
    draft.favorites.forEach {favorite->
        Row(Modifier.fillMaxWidth().padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
            AppIcon(favorite.icon.resource(),null)
            FittedText(favorite.label,Modifier.padding(start=12.dp),style=MaterialTheme.typography.titleSmall)
        }
    }
    key(draft.favorites.size) {
        ProgressiveFavoriteFields(location,icon,label,nameIsSuggested,
            "${draft.settings.engine}:${draft.settings.geocodingEndpoint}",search,
            onLocation=onLocation,onIcon=onIcon,onNameEdited=onNameEdited,tagPrefix="onboarding",enabled=!busy)
    }
    Text(tr("Mit Weiter speicherst du den ausgewählten Ort. Symbol und Name kannst du vorher anpassen oder später ändern. Ohne Favorit kannst du diesen Schritt überspringen."),style=MaterialTheme.typography.bodySmall)
    Text(tr("Du kannst denselben Ort mehrfach speichern, zum Beispiel mit verschiedenen Namen, Farben oder Zeitregeln. Jeder Favorit bleibt einzeln bearbeitbar."),
        Modifier.testTag("onboarding-duplicate-favorites"),style=MaterialTheme.typography.bodySmall)
}
