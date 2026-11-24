// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.screens

import androidx.compose.foundation.relocation.bringIntoViewRequester

import dev.ambivalenton.openv.ui.components.ThemedButton

import dev.ambivalenton.openv.ui.components.AppModalBottomSheet as ModalBottomSheet

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.BuildConfig
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.*
import dev.ambivalenton.openv.ui.components.*
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** One saved destination, one parent per page, no duplicate design controls on the root. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(ui: AppUiState, vm: AppViewModel,navigation: SettingsNavigationState=rememberSettingsNavigationState(),renderHeader:Boolean=true,overview:OverviewUiState=OverviewUiState(),onOverviewRefresh:()->Unit={},personalThemeUi:PersonalThemeUiState=rememberPersonalThemeUiState()) {
    val savedPages=rememberSaveableStateHolder()
    val page=navigation.page
    val settings=ui.store.settings
    val context=androidx.compose.ui.platform.LocalContext.current
    val icons=remember(context){dev.ambivalenton.openv.launcher.LauncherIconManager(context)}
    val currentLogo=ui.store.launcherLogo?:remember(icons){icons.current()}
    var pendingPersonalTheme by remember{mutableStateOf<PersonalTheme?>(null)}
    var deletingPersonalTheme by remember{mutableStateOf<PersonalTheme?>(null)}
    var themeFeedback by remember{mutableStateOf<String?>(null)}
    val themeScope=rememberCoroutineScope()
    val tutorials=dev.ambivalenton.openv.ui.tutorial.LocalTutorialState.current
    var provider by rememberSaveable {mutableStateOf(false)}
    var boardPicker by rememberSaveable {mutableStateOf(false)}
    var services by rememberSaveable {mutableStateOf(false)}
    var favoriteEditor by rememberSaveable {mutableStateOf(false)}
    var editFavorite by remember {mutableStateOf<Favorite?>(null)}
    var glossary by rememberSaveable {mutableStateOf(false)}
    var secretDesign by rememberSaveable {mutableStateOf(false)}
    var vibecoded by rememberSaveable {mutableStateOf(false)}
    var legalDocument by rememberSaveable {mutableStateOf<String?>(null)}
    BackHandler(page!=SettingsPage.ROOT) {navigation.back()}
    Column(Modifier.fillMaxSize()) {
        if(renderHeader) SettingsAppBar(navigation,{},{enabled->vm.settings {it.withQuietMode(enabled)}},personalThemeUi)
        AnimatedContent(page,modifier=Modifier.weight(1f),label="settings-page",
            transitionSpec={ if(settings.reduceMotion) (EnterTransition.None togetherWith ExitTransition.None) else (fadeIn(tween(180))+slideInHorizontally(tween(220)){it/12}) togetherWith
                (fadeOut(tween(120))+slideOutHorizontally(tween(180)){-it/12}) }) {destination ->
            savedPages.SaveableStateProvider(destination.name) {
                Column(Modifier.fillMaxSize().testTag("settings-page-${destination.name}")
                    .verticalScroll(rememberScrollState()).padding(start=20.dp,end=20.dp,top=20.dp,bottom=20.dp+LocalNavigationClearance.current),
                    verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    when(destination) {
                        SettingsPage.ROOT -> {
                            // M11: one search over all pages, with the path of each hit and a direct jump.
                            SettingsSearch {entry->navigation.openAt(entry.page,entry.anchor)}
                            SettingsMenu(destination,navigation::open,onSecretHold={secretDesign=true})
                        }
                        SettingsPage.MODES -> AppModesSettings(settings,vm::settings,
                            place=ui.store.presence?.location?.takeIf {it.hasCoordinates} ?: settings.manualOrigin?.takeIf {it.hasCoordinates},
                            dayNightAnchor=navigation.anchor,onAnchorShown={navigation.anchor=null}) {
                            // Export and import moved here from "Dienste & Daten", as the last frame.
                            val backupFrame=remember {androidx.compose.foundation.relocation.BringIntoViewRequester()}
                            LaunchedEffect(navigation.anchor) {
                                if(navigation.anchor=="backup") {kotlinx.coroutines.delay(260);runCatching {backupFrame.bringIntoView()};navigation.anchor=null}
                            }
                            Box(Modifier.bringIntoViewRequester(backupFrame)) {
                            ModeFrame("mode-backup",{m->BackupPicture(m)}) {
                                FittedText(tr("Sichern & Übertragen"),style=MaterialTheme.typography.titleMedium)
                                BackupSettings(vm::updateWidgets)
                            }
                            }
                        }
                        SettingsPage.TRAVEL -> SettingsMenu(destination,navigation::open,inlineContent={child->
                            when(child) {
                                SettingsPage.WALKING_TIMELINE->TimelineWalkingSettings(settings,vm::settings)
                                SettingsPage.WALKING->TimelineWalkingSettings(settings,vm::settings)
                                SettingsPage.SEARCH_PRIORITY->SearchPrioritySettings(settings,vm::settings)
                                else->Unit
                            }
                        })
                        SettingsPage.DESIGN -> {
                            PersonalThemesMenu(ui.store,personalThemeUi,currentLogo,onApply={theme->
                                if(theme.design.logo.currentReplacement()!=currentLogo)pendingPersonalTheme=theme
                                else themeScope.launch{vm.applyPersonalTheme(theme.id).onFailure{themeFeedback=tr("Das Theme konnte nicht angewendet werden.")}}
                            },onDelete={id->deletingPersonalTheme=ui.store.personalThemes.firstOrNull{it.id==id}})
                            themeFeedback?.let{Text(it,color=MaterialTheme.colorScheme.error)}
                            AppAppearanceSettings(settings,vm::settings)
                            BoardDesignSelector(ui.store) {boardPicker=true}
                            TimelineDesignSettings(ui.store,vm)
                            dev.ambivalenton.openv.ui.network.NetworkPlanDesignSettings(settings,vm::settings)
                            // The overview design gets its framed preview like the others.
                            OverviewDesignSelector(ui.store,overview) {navigation.open(SettingsPage.SENTENCES)}
                            SettingsMenu(destination,navigation::open,setOf(SettingsPage.APP_THEME,SettingsPage.BOARD,SettingsPage.TIMELINE,SettingsPage.NETWORK_PLAN,SettingsPage.SENTENCES))
                        }
                        SettingsPage.APP_LOGO -> AppLogoSettings(onChange=vm::changeAppLogo)
                        SettingsPage.APP_THEME -> AppAppearanceSettings(settings,vm::settings)
                        SettingsPage.BOARD -> BoardDesignSettings(ui.store,vm,
                            beforeProducts={TextButton(vm::updateWidgets) {Text(tr("Anzeigetafel aktualisieren"))}},
                            extraSwitches={BoardLocationControls(settings, vm::settings)})
                        SettingsPage.SENTENCES -> OverviewDesignSettings(ui.store,overview,onOverviewRefresh,vm::settings)
                        SettingsPage.TYPOGRAPHY -> ReadabilitySettings(settings,vm::settings)
                        SettingsPage.TIMELINE -> TimelineDesignSettings(ui.store,vm)
                        SettingsPage.NETWORK_PLAN -> dev.ambivalenton.openv.ui.network.NetworkPlanDesignSettings(settings,vm::settings)
                        SettingsPage.WIDGET -> Unit // Legacy destinations redirect to Design.
                        SettingsPage.FAVORITES -> FavoriteSettingsContent(ui.store,
                            onAdd={editFavorite=null;favoriteEditor=true},onEdit={editFavorite=it;favoriteEditor=true},
                            onSettings=vm::settings,onGridSize=vm::setGridSize)
                        // 2.4: framed groups; the transport-mode filter moved here from the search screen.
                        SettingsPage.SEARCH -> ConnectionSearchSettings(settings,vm::settings,ui.store.dismissedNotices.size,vm::restoreAllNotices)
                        SettingsPage.WALKING_TIMELINE -> TimelineWalkingSettings(settings,vm::settings)
                        SettingsPage.WALKING -> TimelineWalkingSettings(settings,vm::settings)
                        SettingsPage.SEARCH_PRIORITY -> SearchPrioritySettings(settings,vm::settings)
                        SettingsPage.SERVICES -> {
                            ProviderNotice(settings.engine)
                            TextButton({provider=true}) {Text(tr("Fahrplandienst wählen"))}
                            TextButton({services=true}) {Text(tr("Server, Ortssuche & Karten"))}
                            TextButton(vm::clearHistory) {Text(tr("Suchverlauf löschen"))}
                            // Build 117: own punctuality reports (reliability estimate), local only.
                            if(!ui.store.reliability.isEmpty)TextButton(vm::clearPunctuality,Modifier.testTag("clear-punctuality")) {
                                Text(tr("Eigene Pünktlichkeitsangaben löschen (%1\$s)",ui.store.reliability.reports))}
                        }
                        SettingsPage.INFO,SettingsPage.LEGAL,SettingsPage.HELP -> {
                            InfoHelpContent(onDocument={legalDocument=it},onGlossary={glossary=true},
                                onTutorials={tutorials?.restart();vm.resetTutorials()},
                                onSetup=vm::restartOnboarding,onEasterEgg={vibecoded=true})
                            SettingsMenu(SettingsPage.INFO,navigation::open,setOf(SettingsPage.LEGAL,SettingsPage.HELP))
                        }
                    }
                }
            }
        }
    }
    PersonalThemeSaveDialog(personalThemeUi,ui.store){name->vm.savePersonalTheme(name,currentLogo)}
    pendingPersonalTheme?.let{theme->AlertDialog(onDismissRequest={pendingPersonalTheme=null},title={Text(tr("Persönliches Theme anwenden?"))},
        text={Text(tr("Übernimmt das gesamte gespeicherte Design einschließlich App-Logo. Android kann die App beim Aktualisieren des Symbols kurz neu öffnen."))},
        confirmButton={TextButton({pendingPersonalTheme=null;themeScope.launch{
            vm.applyPersonalTheme(theme.id).onFailure{themeFeedback=tr("Das Theme konnte nicht angewendet werden.")}
        }}){Text(tr("Anwenden"))}},dismissButton={TextButton({pendingPersonalTheme=null}){Text(tr("Verwerfen"))}})}
    deletingPersonalTheme?.let{theme->AlertDialog(onDismissRequest={deletingPersonalTheme=null},title={Text(tr("Persönliches Theme löschen?"))},text={Text(theme.name)},
        confirmButton={TextButton({vm.removePersonalTheme(theme.id);deletingPersonalTheme=null}){Text(tr("Löschen"))}},
        dismissButton={TextButton({deletingPersonalTheme=null}){Text(tr("Behalten"))}})}
    if(glossary) GlossarySheet {glossary=false}
    if(secretDesign) SecretDesignDialog(settings,vm::settings,onRandomOff={dev.ambivalenton.openv.ui.theme.SessionAppearance.clearRandom()}) {secretDesign=false}
    if(boardPicker) BoardDesignPicker(ui.store,{design->vm.settings {it.copy(board=it.board.copy(design=design))}},{boardPicker=false},
        update=vm::settings,footer={BoardOptionsDisclosure(ui.store,vm)})
    if(vibecoded) VibecodedEasterEggDialog {vibecoded=false}
    legalDocument?.let {document->LegalDocumentSheet(document) {legalDocument=null}}
    if(favoriteEditor) dev.ambivalenton.openv.ui.dialogs.FavoriteEditDialog(editFavorite,
        "${settings.engine}:${settings.geocodingEndpoint}",vm::findLocations,
        {vm.favorite(it);if(editFavorite!=null) favoriteEditor=false},{favoriteEditor=false},
        scheduleConflict={windows->ui.store.scheduleConflict(editFavorite?.id,windows)},
        networkRole=editFavorite?.id?.let {id->ui.store.network.node(id)?.role},
        onNetworkRole=editFavorite?.id?.let {id->{role:NodeRole->vm.setNodeRole(id,role)}},
        suggestions=remember(ui.store.history,ui.store.favorites) {ui.store.favoritePlaceSuggestions()},
        otherFavorites=ui.store.favorites.filter {it.id!=editFavorite?.id},
        partnerFor={id->ui.store.connectionPartnerFor(id)},onConnect={a,b->vm.connectNodes(a,b)!=null})
    if(services) ServiceSettingsSheet(settings,{change->vm.settings(change);services=false},{services=false})
    if(provider) ProviderPicker(settings,{change->vm.settings(change);provider=false},{provider=false})
}

@Composable
private fun SettingSlider(
    label: String,
    value: Int,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    onCommit: (Int) -> Unit,
) {
    var draft by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column {
        Text(tr("%1\$s · %2\$s %3\$s", label, draft.roundToInt(), unit), style = MaterialTheme.typography.labelLarge)
        Slider(
            draft,
            { draft = it },
            valueRange = range,
            onValueChangeFinished = { onCommit(draft.roundToInt()) },
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

@Composable
private fun WidgetPreview(store:StoredState)=FavoriteWidgetPreview(store)
