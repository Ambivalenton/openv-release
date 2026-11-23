// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.onboarding

import dev.ambivalenton.openv.ui.components.ThemedButton
import dev.ambivalenton.openv.ui.components.ThemedOutlinedButton

import dev.ambivalenton.openv.ui.components.CalmVisibility
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import dev.ambivalenton.openv.ui.dialogs.FavoriteLookStep
import dev.ambivalenton.openv.ui.dialogs.FavoritePlaceStep
import dev.ambivalenton.openv.ui.dialogs.StudioPop
import dev.ambivalenton.openv.ui.dialogs.favoriteDraftSaver
import dev.ambivalenton.openv.ui.dialogs.staggeredRise
import dev.ambivalenton.openv.ui.dialogs.studioQuiet
import dev.ambivalenton.openv.ui.dialogs.studioTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.draw.clip
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
    /** H01: straight to the first search (finishes the setup with the defaults, no permission asked). */
    onSearchNow: (()->Unit)?=null,
    /** Build 101: false lets the finished setup fade out over the app; [onExited] follows once it is gone. */
    visible: Boolean=true,
    onExited: ()->Unit={},
) {
    val permissions=rememberOnboardingPermissions(onLocationGranted)
    // H01: the first view says what the app is for; the setup wizard is optional.
    var wizard by rememberSaveable {mutableStateOf(store.onboardingDraft!=null || onSearchNow==null)}
    val quiet=studioQuiet()
    val shown=remember {MutableTransitionState(false)}
    shown.targetState=visible
    val exited by rememberUpdatedState(onExited)
    LaunchedEffect(shown) {snapshotFlow {shown.isIdle && !shown.currentState && !shown.targetState}.collect {gone->if(gone) exited()}}
    // Build 101: the whole first launch is one layer of the app's overlay host, above the app and its
    // navigation. Since build 96 the first view was composed below the app's opaque main screen: it could be
    // neither seen nor tapped, so the setup never finished (no tutorial, no navigation pill, no GPS start).
    Dialog(onDismissRequest={},properties=DialogProperties(usePlatformDefaultWidth=false,dismissOnBackPress=false,
        dismissOnClickOutside=false,decorFitsSystemWindows=false),behindNavigation=true,drawScrim=false) {
        AnimatedVisibility(shown,Modifier.fillMaxSize(),
            enter=if(quiet) EnterTransition.None else fadeIn(tween(200)),
            exit=if(quiet) ExitTransition.None else fadeOut(tween(320,delayMillis=40))+scaleOut(tween(380),targetScale=1.05f)) {
            Box(Modifier.fillMaxSize().appThemeBackground().testTag("first-launch")) {
                AnimatedContent(wizard || onSearchNow==null,Modifier.fillMaxSize(),label="first-launch",transitionSpec={
                    if(quiet) EnterTransition.None togetherWith ExitTransition.None
                    else if(targetState) (slideInVertically(spring(dampingRatio=.8f,stiffness=340f)) {it/5}+fadeIn(tween(220,delayMillis=60))+
                        scaleIn(spring(dampingRatio=.8f,stiffness=340f),initialScale=.94f)) togetherWith (fadeOut(tween(160))+scaleOut(tween(220),targetScale=.92f))
                    else (fadeIn(tween(220,delayMillis=60))+scaleIn(spring(dampingRatio=.8f,stiffness=340f),initialScale=.92f)) togetherWith
                        (slideOutVertically(tween(220)) {it/5}+fadeOut(tween(160)))
                }) {setup->
                    if(setup) OnboardingWizard(store,permissions,onChange,onFinish,search,
                        onBackToIntro=if(onSearchNow!=null) ({wizard=false}) else null,hosted=true)
                    else FirstLaunchIntro(onSearch={onSearchNow?.invoke()},onSetup={wizard=true})
                }
            }
        }
    }
}

/**
 * H01: the first view. Purpose and main action are clear at once: "Verbindung suchen" opens the search
 * with start and destination (one tap, no permission, no design choice). Setting the app up and
 * importing a backup are offered beside it. Location is asked for at "Meinen Standort nutzen",
 * notifications when a journey companion starts. Build 101: the lines rise in one after another.
 */
@Composable fun FirstLaunchIntro(onSearch: ()->Unit, onSetup: ()->Unit) {
    var importSheet by rememberSaveable {mutableStateOf(false)}
    var busy by remember {mutableStateOf(false)}
    val quiet=studioQuiet()
    Box(Modifier.fillMaxSize().appThemeBackground().testTag("first-launch-intro")) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState())
            .padding(horizontal=24.dp,vertical=28.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Spacer(Modifier.weight(1f))
            Text(tr("Öpen-V"),Modifier.staggeredRise(0,quiet),style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary)
            Text(tr("Finde deine nächste Bus- oder Bahnverbindung."),Modifier.staggeredRise(1,quiet).semantics {heading()}.testTag("intro-purpose"),
                style=MaterialTheme.typography.headlineMedium)
            Text(tr("Kostenlos, ohne Werbung und ohne Konto. Standort und Benachrichtigungen fragt Öpen-V erst, wenn du sie brauchst."),
                Modifier.staggeredRise(2,quiet),style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            ThemedButton({if(!busy){busy=true;onSearch()}},Modifier.staggeredRise(3,quiet).fillMaxWidth().heightIn(min=56.dp).testTag("intro-search")) {
                Text(tr("Verbindung suchen"),style=MaterialTheme.typography.titleMedium)
            }
            ThemedOutlinedButton(onSetup,Modifier.staggeredRise(4,quiet).fillMaxWidth().heightIn(min=52.dp).testTag("intro-setup")) {Text(tr("App einrichten"))}
            TextButton({importSheet=true},Modifier.staggeredRise(5,quiet).fillMaxWidth().heightIn(min=48.dp).testTag("intro-import")) {Text(tr("Sicherung importieren"))}
            Spacer(Modifier.weight(.4f))
        }
        if(importSheet) OnboardingImportSheet {importSheet=false}
    }
}

/**
 * Build 102: three short steps – Freigaben, erster Favorit, Design – with one line of text each.
 * Fixed chrome and a scrolling body keep small displays and large fonts usable.
 */
@Composable fun OnboardingWizard(
    store: StoredState,
    permissions: SetupPermissions,
    onChange: suspend ((OnboardingDraft)->OnboardingDraft)->Boolean,
    onFinish: suspend ()->Boolean,
    search: suspend (String)->List<Location>,
    /** H01: Android back on the first step returns to the first view. */
    onBackToIntro: (()->Unit)?=null,
    /** Build 101: inside the first-launch layer (full screen, own back handling) instead of a window of its own. */
    hosted: Boolean=false,
) {
    val draft=store.onboardingDraft ?: OnboardingDraft(settings=store.settings)
    val step=draft.step.coerceIn(0,ONBOARDING_STEPS-1)
    val scope=rememberCoroutineScope()
    var writing by remember {mutableStateOf(false)}
    var error by rememberSaveable {mutableStateOf(false)}
    // Import a backup from the first step instead of setting up again.
    var importSheet by rememberSaveable {mutableStateOf(false)}
    // The favourite being created (same beats as "Favorit hinzufügen": place, then its look).
    var studio by rememberSaveable(stateSaver=favoriteDraftSaver) {mutableStateOf(FavoriteDraft())}
    var studioLook by rememberSaveable {mutableStateOf(false)}
    val busy=writing || permissions.busy
    val canAddFavorite=studioLook && studio.ready && draft.favorites.size+store.favorites.size<40
    val quiet=studioQuiet()
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
    /** Adds the favourite being created; [then] may move on in the same write (Weiter). */
    fun addFavorite(then: (OnboardingDraft)->OnboardingDraft={it}) {
        val favorite=studio.favorite()
        commit({onChange {then(it.copy(favorites=it.favorites+favorite,offerMoreFavorites=false))}}) {
            studio=FavoriteDraft();studioLook=false
        }
    }
    val back: ()->Unit={if(step>0 && !busy) change {it.copy(step=step-1)} else if(!busy) onBackToIntro?.invoke()}
    val last=step==ONBOARDING_STEPS-1
    OpenVTheme(draft.settings) {
        val page: @Composable ()->Unit={
            Box(Modifier.fillMaxSize().appThemeBackground().testTag("first-launch-onboarding"),contentAlignment=Alignment.TopCenter) {
                Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).widthIn(max=640.dp).fillMaxSize()
                    .padding(horizontal=20.dp,vertical=16.dp)) {
                    // The current step stretches with a little spring; the bar is the only step counter.
                    Row(Modifier.fillMaxWidth().padding(top=4.dp,bottom=20.dp).semantics(mergeDescendants=true) {
                        progressBarRangeInfo=ProgressBarRangeInfo((step+1).toFloat(),1f..ONBOARDING_STEPS.toFloat())
                        stateDescription=tr("Schritt %1\$s von %2\$s",step+1,ONBOARDING_STEPS)
                    }.testTag("onboarding-progress"),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        repeat(ONBOARDING_STEPS) {index->
                            val weight by animateFloatAsState(if(index==step) 2.4f else 1f,
                                if(quiet) snap() else spring(dampingRatio=.6f,stiffness=420f),label="onboarding-progress-$index")
                            val color by animateColorAsState(if(index<=step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                if(quiet) snap() else spring(stiffness=380f),label="onboarding-progress-color-$index")
                            Box(Modifier.weight(weight).height(6.dp).background(color,CircleShape))
                        }
                    }
                    AnimatedContent(step,modifier=Modifier.weight(1f),label="onboarding-step",
                        transitionSpec={studioTransition(quiet,targetState>initialState)}) {page->
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("onboarding-step-$page"),
                            verticalArrangement=Arrangement.spacedBy(16.dp)) {
                            Text(when(page) {0->tr("Freigaben");1->tr("Dein erster Favorit");else->tr("Dein Design")},
                                Modifier.semantics {heading()},style=MaterialTheme.typography.headlineMedium)
                            when(page) {
                                0->PermissionSetupContent(permissions)
                                1->OnboardingFavorites(draft,store,studio,studioLook,search,busy,canAddFavorite,
                                    onStudio={studio=it},onLook={studioLook=it},onAdd={addFavorite()})
                                else->DesignChoices(draft.settings.appTheme,enabled=!busy) {theme->
                                    change {it.copy(settings=it.settings.copy(appTheme=theme))}
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    if(error) Text(tr("Die Auswahl konnte nicht gespeichert werden. Bitte erneut versuchen."),
                        Modifier.padding(top=8.dp).semantics {liveRegion=LiveRegionMode.Polite},color=MaterialTheme.colorScheme.error)
                    // One row: back (or import on the first step), skip, next.
                    Row(Modifier.fillMaxWidth().padding(top=12.dp),verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                        if(step>0) TextButton({change {it.copy(step=step-1)}},enabled=!busy,modifier=Modifier.heightIn(min=48.dp)) {Text(tr("Zurück"))}
                        else TextButton({importSheet=true},enabled=!busy,modifier=Modifier.heightIn(min=48.dp).testTag("onboarding-import")) {
                            Text(tr("Sicherung importieren"),maxLines=1)
                        }
                        Spacer(Modifier.weight(1f))
                        if(!last) TextButton({change {it.copy(step=step+1,offerMoreFavorites=false)}},enabled=!busy,
                            modifier=Modifier.heightIn(min=48.dp).testTag("onboarding-skip")) {Text(tr("Überspringen"))}
                        ThemedButton({when {
                            // A chosen place is not lost: "Weiter" adds it and moves on in one write.
                            step==1 && canAddFavorite->addFavorite {it.copy(step=2)}
                            !last->change {it.copy(step=step+1)}
                            else->commit(onFinish)
                        }},enabled=!busy && (step!=1 || canAddFavorite || draft.favorites.isNotEmpty()),
                            modifier=Modifier.heightIn(min=48.dp).testTag("onboarding-next")) {
                            Text(if(last) tr("Los geht’s") else tr("Weiter"))
                        }
                    }
                }
            }
        }
        if(hosted) {BackHandler {back()};page()}
        else Dialog(onDismissRequest=back,properties=DialogProperties(usePlatformDefaultWidth=false,dismissOnClickOutside=false,
            decorFitsSystemWindows=false)) {page()}
        if(importSheet) OnboardingImportSheet {importSheet=false}
    }
}

/** Build 102: two lines, each with its own "Erlauben"; nothing is asked before a tap. */
@Composable private fun PermissionSetupContent(p: SetupPermissions) {
    Text(tr("Beides ist freiwillig und lässt sich jederzeit ändern."),style=MaterialTheme.typography.bodyLarge,
        color=MaterialTheme.colorScheme.onSurfaceVariant)
    PermissionRow(R.drawable.ic_gps,tr("Standort"),tr("Verbindungen ab deinem Standort"),
        granted=p.location!=SetupLocationAccess.NONE && p.locationEnabled,
        action=if(p.location!=SetupLocationAccess.NONE && !p.locationEnabled) tr("Einschalten") else tr("Erlauben"),
        enabled=!p.busy,tag="onboarding-permission-location",onClick=p.requestLocation)
    PermissionRow(R.drawable.ic_notifications,tr("Mitteilungen"),tr("Hinweise während der Fahrt"),
        granted=p.notificationsEnabled,action=tr("Erlauben"),enabled=!p.busy,tag="onboarding-permission-notifications",
        onClick=p.requestNotifications)
}

@Composable private fun PermissionRow(icon: Int,title: String,detail: String,granted: Boolean,action: String,enabled: Boolean,
    tag: String,onClick: ()->Unit) {
    val quiet=studioQuiet()
    AppElevatedCard(Modifier.fillMaxWidth().testTag(tag)) {
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer,CircleShape),contentAlignment=Alignment.Center) {
                AppIcon(icon,null,Modifier.size(22.dp),tint=MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(Modifier.weight(1f)) {
                Text(title,style=MaterialTheme.typography.titleMedium)
                Text(detail,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnimatedContent(granted,label="permission-$tag",transitionSpec={
                if(quiet) EnterTransition.None togetherWith ExitTransition.None
                else (scaleIn(StudioPop,initialScale=.6f)+fadeIn(tween(160))) togetherWith (scaleOut(tween(120),targetScale=.8f)+fadeOut(tween(100)))
            }) {done->
                if(done) Row(Modifier.heightIn(min=48.dp).semantics(mergeDescendants=true) {},verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    AppIcon(R.drawable.ic_check,null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.primary)
                    Text(tr("Erlaubt"),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
                }
                else ThemedTonalButton(onClick,Modifier.heightIn(min=48.dp),enabled=enabled) {Text(action,maxLines=1)}
            }
        }
    }
}

/** Build 102: a few designs of different moods instead of all; the whole setup changes with a tap. */
internal val onboardingDesigns: List<AppTheme?> = listOf(null,AppTheme.CLASSIC_DARK,AppTheme.FOREST,AppTheme.SUNSET_POSTCARD,
    AppTheme.BLUE_HOUR,AppTheme.SYNTHWAVE)

@Composable private fun DesignChoices(selected: AppTheme?,enabled: Boolean,onSelect: (AppTheme?)->Unit) {
    Text(tr("Weitere Designs findest du später in den Einstellungen."),style=MaterialTheme.typography.bodyLarge,
        color=MaterialTheme.colorScheme.onSurfaceVariant)
    val shown=if(selected in onboardingDesigns) onboardingDesigns else listOf(selected)+onboardingDesigns.dropLast(1)
    Column(Modifier.fillMaxWidth().selectableGroup().testTag("onboarding-designs"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        shown.chunked(2).forEach {row->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                row.forEach {theme->DesignTile(theme,theme==selected,enabled,Modifier.weight(1f)) {onSelect(theme)}}
                if(row.size==1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** A small live sample of a design: its background, a card, the accent and three capsule colours. */
@Composable private fun DesignTile(theme: AppTheme?,chosen: Boolean,enabled: Boolean,modifier: Modifier,onClick: ()->Unit) {
    val quiet=studioQuiet()
    val scale by animateFloatAsState(if(chosen) 1.04f else 1f,if(quiet) snap() else StudioPop,label="design-${theme?.name}")
    val ring by animateColorAsState(if(chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        if(quiet) snap() else spring(stiffness=400f),label="design-ring-${theme?.name}")
    val title=theme?.title ?: tr("Standard")
    Column(modifier.graphicsLayer {scaleX=scale;scaleY=scale}.clip(MaterialTheme.shapes.large)
        .selectable(chosen,enabled=enabled,role=Role.RadioButton,onClick=onClick).semantics {contentDescription=title}
        .testTag("onboarding-design-${theme?.name ?: "STANDARD"}"),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().height(104.dp).border(if(chosen) 3.dp else 1.dp,ring,MaterialTheme.shapes.large)
            .padding(if(chosen) 3.dp else 1.dp).clip(MaterialTheme.shapes.large)) {
            OpenVTheme(AppSettings(appTheme=theme,quietMode=true)) {
                val scheme=MaterialTheme.colorScheme
                Column(Modifier.fillMaxSize().appThemeBackground().padding(10.dp),verticalArrangement=Arrangement.SpaceBetween) {
                    Row(Modifier.fillMaxWidth().background(scheme.secondaryContainer,MaterialTheme.shapes.small).padding(8.dp),
                        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(18.dp).background(scheme.primary,CircleShape))
                        Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.width(54.dp).height(5.dp).background(scheme.onSecondaryContainer.copy(alpha=.7f),CircleShape))
                            Box(Modifier.width(34.dp).height(5.dp).background(scheme.onSecondaryContainer.copy(alpha=.4f),CircleShape))
                        }
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        listOf(scheme.primary,scheme.tertiary,scheme.secondary).forEach {c->Box(Modifier.size(14.dp).background(c,CircleShape))}
                    }
                }
            }
            if(chosen) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).background(MaterialTheme.colorScheme.primary,CircleShape),
                contentAlignment=Alignment.Center) {
                AppIcon(R.drawable.ic_check,null,Modifier.size(16.dp),tint=MaterialTheme.colorScheme.onPrimary)
            }
        }
        Text(title,Modifier.padding(start=10.dp,end=10.dp,bottom=6.dp),style=MaterialTheme.typography.labelLarge,maxLines=1,
            overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

/**
 * Build 101: the favourites step uses the beats of "Favorit hinzufügen": find the place (or say first what it is),
 * then its look, then "Hinzufügen". Added favourites pop into the row above; "Weiter" adds a chosen one, too.
 * Build 102: one line of text, no further notes.
 */
@Composable private fun OnboardingFavorites(
    draft: OnboardingDraft,store: StoredState,studio: FavoriteDraft,look: Boolean,
    search: suspend (String)->List<Location>,busy: Boolean,canAdd: Boolean,
    onStudio: (FavoriteDraft)->Unit,onLook: (Boolean)->Unit,onAdd: ()->Unit,
) {
    val quiet=studioQuiet()
    val known=store.favorites+draft.favorites
    val settings=dev.ambivalenton.openv.ui.theme.LocalAppSettings.current
    val themeAccent=MaterialTheme.colorScheme.primary.toArgb().toLong() and 0xffffffffL
    val palette=remember(settings.appTheme,settings.appThemeColors,themeAccent) {
        dev.ambivalenton.openv.ui.theme.favoriteThemeColors(settings,themeAccent).map {it.color}}
    val latestStudio by rememberUpdatedState(studio)
    LaunchedEffect(studio.accentSeed==null,known.size) {
        if(latestStudio.accentSeed==null) onStudio(latestStudio.copy(accentSeed=suggestFavoriteAccent(known,palette)))
    }
    val providerKey="${draft.settings.engine}:${draft.settings.geocodingEndpoint}"
    Text(tr("Ein Ort, zu dem du oft fährst – zum Beispiel Zuhause oder Arbeit."),style=MaterialTheme.typography.bodyLarge,
        color=MaterialTheme.colorScheme.onSurfaceVariant)
    if(draft.favorites.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("onboarding-added"),
        horizontalArrangement=Arrangement.spacedBy(14.dp)) {
        draft.favorites.forEachIndexed {index,favorite-> key(favorite.id) {AddedFavorite(favorite,quiet,Modifier.testTag("onboarding-added-$index"))}}
    }
    AnimatedContent(look,label="onboarding-favorite-beat",transitionSpec={studioTransition(quiet,targetState)}) {lookStep->
        if(!lookStep) FavoritePlaceStep(studio,providerKey,search,remember(store.history,store.favorites) {store.favoritePlaceSuggestions()},
            favoriteRoleChoices(known),onRole={onStudio(studio.withIcon(it))},onPlace={onStudio(studio.withPlace(it));onLook(true)},
            autoFocus=false,tagPrefix="onboarding",enabled=!busy)
        else Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
            FavoriteLookStep(studio,known,onIcon={onStudio(studio.withIcon(it))},onName={onStudio(studio.withName(it))},
                onColor={onStudio(studio.copy(accentSeed=it))},onShape={onStudio(studio.copy(shape=it))},
                onChangePlace={onLook(false)},tagPrefix="onboarding",enabled=!busy)
            ThemedTonalButton(onAdd,Modifier.fillMaxWidth().heightIn(min=52.dp).testTag("onboarding-add-favorite"),enabled=canAdd && !busy) {
                AppIcon(R.drawable.ic_add,null,Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("Hinzufügen"))
            }
        }
    }
}

/** A favourite added during the setup springs into the row. */
@Composable private fun AddedFavorite(favorite: Favorite,quiet: Boolean,modifier: Modifier) {
    val appear=remember {Animatable(if(quiet) 1f else 0f)}
    LaunchedEffect(Unit) {if(!quiet) appear.animateTo(1f,StudioPop)}
    Column(modifier.width(72.dp).graphicsLayer {scaleX=appear.value;scaleY=appear.value;alpha=appear.value.coerceIn(0f,1f)},
        horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
        FavoriteBadge(favorite,size=48.dp,shadow=true)
        Text(favorite.label,style=MaterialTheme.typography.labelMedium,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}
