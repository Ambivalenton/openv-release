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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import dev.ambivalenton.openv.ui.network.VehicleKind
import dev.ambivalenton.openv.ui.network.drawVehicleCar
import dev.ambivalenton.openv.ui.network.vehicleConsist
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
 * H01: the first view. Build 121: setting the app up is the main action – a minute of setup makes the app work
 * well from the start (start place, favourite, modes, design). "Directly search" stays as a way out, importing a
 * backup beside it; neither takes the room of the setup. A small line with a train gliding from stop to stop
 * opens the page; the lines rise in one after another.
 */
@Composable fun FirstLaunchIntro(onSearch: ()->Unit, onSetup: ()->Unit) {
    var importSheet by rememberSaveable {mutableStateOf(false)}
    var busy by remember {mutableStateOf(false)}
    val quiet=studioQuiet()
    Box(Modifier.fillMaxSize().appThemeBackground().testTag("first-launch-intro")) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState())
            .padding(horizontal=24.dp,vertical=28.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Spacer(Modifier.weight(1f))
            IntroJourney(Modifier.staggeredRise(0,quiet).fillMaxWidth().height(92.dp).testTag("intro-journey"))
            Text(tr("Öpen-V"),Modifier.staggeredRise(1,quiet),style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary)
            Text(tr("Finde deine nächste Bus- oder Bahnverbindung."),Modifier.staggeredRise(2,quiet).semantics {heading()}.testTag("intro-purpose"),
                style=MaterialTheme.typography.headlineMedium)
            Text(tr("Kostenlos, ohne Werbung und ohne Konto. In einer Minute eingerichtet – dann passt Öpen-V von Anfang an zu dir."),
                Modifier.staggeredRise(3,quiet),style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            ThemedButton({if(!busy) onSetup()},Modifier.staggeredRise(4,quiet).fillMaxWidth().heightIn(min=58.dp).testTag("intro-setup")) {
                AppIcon(R.drawable.ic_settings,null,Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                FittedText(tr("App einrichten"),style=MaterialTheme.typography.titleMedium,maxLines=1)
            }
            Column(Modifier.staggeredRise(5,quiet).fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally) {
                TextButton({if(!busy){busy=true;onSearch()}},Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("intro-search")) {
                    FittedText(tr("Direkt eine Verbindung suchen"),maxLines=1)
                }
                TextButton({importSheet=true},Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("intro-import")) {
                    FittedText(tr("Sicherung importieren"),maxLines=1)
                }
            }
            Spacer(Modifier.weight(.4f))
        }
        if(importSheet) OnboardingImportSheet {importSheet=false}
    }
}

/**
 * Build 121: the intro's picture – a soft line with three stops. A small train glides from stop to stop
 * (easing in and out, a short halt at each), the line fills behind it, every reached stop pops, and the
 * destination glows when it arrives. Still and complete in the reduced motion.
 */
@Composable private fun IntroJourney(modifier: Modifier) {
    val quiet=studioQuiet()
    val colors=MaterialTheme.colorScheme
    val t=if(quiet) .999f else rememberInfiniteTransition(label="intro-journey").animateFloat(0f,1f,
        infiniteRepeatable(tween(6400,easing=LinearEasing)),label="intro-journey-phase").value
    Canvas(modifier.clearAndSetSemantics {}) {
        val w=size.width;val h=size.height;val stroke=h*.075f
        val track=Path().apply {
            moveTo(w*.07f,h*.68f)
            cubicTo(w*.30f,h*.02f,w*.52f,h*1.02f,w*.93f,h*.32f)
        }
        val measure=PathMeasure().apply {setPath(track,false)}
        val length=measure.length
        fun ease(x: Float)=FastOutSlowInEasing.transform(x.coerceIn(0f,1f))
        // Ride, halt, ride, halt at the destination, then the whole picture breathes out and starts again.
        val progress=when {
            t<.34f->.5f*ease(t/.34f)
            t<.46f->.5f
            t<.80f->.5f+.5f*ease((t-.46f)/.34f)
            else->1f
        }
        val fade=if(quiet) 1f else (1f-((t-.94f)/.06f).coerceIn(0f,1f))
        drawPath(track,colors.outlineVariant,style=Stroke(stroke,cap=StrokeCap.Round))
        val travelled=Path().also {measure.getSegment(0f,length*progress,it,true)}
        drawPath(travelled,colors.primary.copy(alpha=fade),style=Stroke(stroke,cap=StrokeCap.Round))
        // The stops: an open ring until the train reaches it, then filled with a little pop.
        listOf(0f,.5f,1f).forEach {at->
            val p=measure.getPosition(length*at)
            val reached=progress>=at-.001f
            val since=when(at) {0f->t;.5f->(t-.34f)/.1f;else->(t-.80f)/.1f}.coerceIn(0f,1f)
            val pop=if(quiet || !reached) 1f else 1f+.35f*kotlin.math.sin(since*Math.PI.toFloat())
            val r=h*.11f*pop
            drawCircle(colors.surface,r,p)
            drawCircle(if(reached) colors.primary.copy(alpha=fade.coerceAtLeast(.25f)) else colors.outline,r,p,style=Stroke(stroke*.6f))
            if(reached) drawCircle(colors.primary.copy(alpha=fade.coerceAtLeast(.25f)),r*.45f,p)
        }
        // The destination glows once the train has arrived.
        if(progress>=1f) {
            val glow=((t-.80f)/.14f).coerceIn(0f,1f)
            val p=measure.getPosition(length)
            drawCircle(colors.tertiary.copy(alpha=.35f*(1f-glow)*fade),h*(.14f+.22f*glow),p,style=Stroke(stroke*.5f))
        }
        // The train: a capsule along the line with a window strip, leaning into the curve.
        val at=measure.getPosition(length*progress)
        val tangent=measure.getTangent(length*progress)
        val angle=Math.toDegrees(kotlin.math.atan2(tangent.y.toDouble(),tangent.x.toDouble())).toFloat()
        rotate(angle,at) {
            val tw=h*.46f;val th=h*.2f
            drawRoundRect(colors.primary.copy(alpha=fade),Offset(at.x-tw/2,at.y-th/2),Size(tw,th),CornerRadius(th/2))
            drawRoundRect(colors.onPrimary.copy(alpha=.85f*fade),Offset(at.x-tw*.3f,at.y-th*.18f),Size(tw*.6f,th*.3f),CornerRadius(th*.15f))
        }
    }
}

/**
 * Build 102 → 121: the guided setup. Freigaben, then (without location access) a start place, the first favourite,
 * the modes and the design – one short line each. "Weiter" is always in reach and never pushed off the row by
 * a large font; "Überspringen" appears only where "Weiter" waits for a choice. Importing a backup lives on the
 * first view, not in the steps. Fixed chrome and a scrolling body keep small displays and large fonts usable.
 */
@Composable fun OnboardingWizard(
    store: StoredState,
    permissions: SetupPermissions,
    onChange: suspend ((OnboardingDraft)->OnboardingDraft)->Boolean,
    onFinish: suspend ()->Boolean,
    search: suspend (String)->List<Location>,
    /** H01: "Zurück" (and Android back) on the first step returns to the first view. */
    onBackToIntro: (()->Unit)?=null,
    /** Build 101: inside the first-launch layer (full screen, own back handling) instead of a window of its own. */
    hosted: Boolean=false,
) {
    val draft=store.onboardingDraft ?: OnboardingDraft(settings=store.settings)
    val stages=onboardingStages(permissions.location!=SetupLocationAccess.NONE)
    val stage=draft.shownStage(stages)
    val index=stages.indexOf(stage)
    val scope=rememberCoroutineScope()
    var writing by remember {mutableStateOf(false)}
    var error by rememberSaveable {mutableStateOf(false)}
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
    val back: ()->Unit={if(index>0 && !busy) change {it.moved(stages,-1)} else if(!busy) onBackToIntro?.invoke()}
    val last=index==stages.lastIndex
    val start=draft.settings.setupStart
    val waiting=when(stage) {
        OnboardingStage.LOCATION->start==null
        OnboardingStage.FAVORITE->!canAddFavorite && draft.favorites.isEmpty()
        else->false
    }
    OpenVTheme(draft.settings) {
        val page: @Composable ()->Unit={
            Box(Modifier.fillMaxSize().appThemeBackground().testTag("first-launch-onboarding"),contentAlignment=Alignment.TopCenter) {
                Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).widthIn(max=640.dp).fillMaxSize()
                    .padding(horizontal=20.dp,vertical=16.dp)) {
                    // Build 121: the step counter is a little journey – stops on a line, a train of the network plan.
                    OnboardingProgress(stages.size,index,Modifier.fillMaxWidth().padding(top=4.dp,bottom=18.dp).semantics(mergeDescendants=true) {
                        progressBarRangeInfo=ProgressBarRangeInfo((index+1).toFloat(),1f..stages.size.toFloat())
                        stateDescription=tr("Schritt %1\$s von %2\$s",index+1,stages.size)
                    }.testTag("onboarding-progress"))
                    AnimatedContent(stage,modifier=Modifier.weight(1f),label="onboarding-step",
                        transitionSpec={studioTransition(quiet,targetState.ordinal>initialState.ordinal)}) {shown->
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("onboarding-step-${shown.name.lowercase()}"),
                            verticalArrangement=Arrangement.spacedBy(16.dp)) {
                            FittedText(tr(shown.titleKey),Modifier.semantics {heading()},style=MaterialTheme.typography.headlineMedium,maxLines=1)
                            when(shown) {
                                OnboardingStage.PERMISSIONS->PermissionSetupContent(permissions)
                                OnboardingStage.LOCATION->StartPlaceContent(draft.settings,"${draft.settings.engine}:${draft.settings.geocodingEndpoint}",
                                    search,!busy) {place->change {it.copy(settings=it.settings.withSetupStart(place))}}
                                OnboardingStage.FAVORITE->OnboardingFavorites(draft,store,studio,studioLook,search,busy,canAddFavorite,
                                    onStudio={studio=it},onLook={studioLook=it},onAdd={addFavorite()})
                                OnboardingStage.MODES->OnboardingModes(draft.settings,!busy) {edit->change {it.copy(settings=edit(it.settings))}}
                                OnboardingStage.DESIGN->DesignChoices(draft.settings,enabled=!busy) {theme->
                                    change {it.copy(settings=it.settings.withSetupDesign(theme))}
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    if(error) Text(tr("Die Auswahl konnte nicht gespeichert werden. Bitte erneut versuchen."),
                        Modifier.padding(top=8.dp).semantics {liveRegion=LiveRegionMode.Polite},color=MaterialTheme.colorScheme.error)
                    OnboardingBar(
                        back=if(index>0 || onBackToIntro!=null) ({
                            TextButton(back,enabled=!busy,modifier=Modifier.heightIn(min=48.dp).testTag("onboarding-back")) {
                                FittedText(tr("Zurück"),maxLines=1)
                            }
                        }) else null,
                        skip=if(waiting) ({
                            TextButton({change {it.moved(stages,1).copy(offerMoreFavorites=false)}},enabled=!busy,
                                modifier=Modifier.heightIn(min=48.dp).testTag("onboarding-skip")) {FittedText(tr("Überspringen"),maxLines=1)}
                        }) else null,
                    ) {
                        ThemedButton({when {
                            // A chosen place is not lost: "Weiter" adds it and moves on in one write.
                            stage==OnboardingStage.FAVORITE && canAddFavorite->addFavorite {it.moved(stages,1)}
                            !last->change {it.moved(stages,1)}
                            else->commit(onFinish)
                        }},enabled=!busy && !waiting,modifier=Modifier.heightIn(min=48.dp).testTag("onboarding-next")) {
                            FittedText(if(last) tr("Los geht’s") else tr("Weiter"),maxLines=1)
                        }
                    }
                }
            }
        }
        if(hosted) {BackHandler {back()};page()}
        else Dialog(onDismissRequest=back,properties=DialogProperties(usePlatformDefaultWidth=false,dismissOnClickOutside=false,
            decorFitsSystemWindows=false)) {page()}
    }
}

/**
 * Build 121: the setup's progress as the timeline of a journey. Every step is a stop on a line; a small train of
 * the network plan rides from stop to stop (a soft spring: it pulls away, glides and brakes into the stop), the
 * line behind it fills in the colours of the stops it passed, a reached stop fills and pops. The train's own
 * colour flows from one stop's colour to the next while it travels. Still at its stop in the reduced motion.
 */
@Composable internal fun OnboardingProgress(count: Int,index: Int,modifier: Modifier=Modifier) {
    val quiet=studioQuiet()
    val colors=MaterialTheme.colorScheme
    val position=remember {Animatable(index.toFloat())}
    LaunchedEffect(index,quiet) {
        if(quiet) position.snapTo(index.toFloat())
        else position.animateTo(index.toFloat(),spring(dampingRatio=.78f,stiffness=90f))
    }
    val stops=remember(colors.primary,count) {onboardingStopColors(colors.primary,count)}
    val surface=colors.surface;val idle=colors.outlineVariant;val ink=colors.onSurface
    val consist=remember {vehicleConsist(VehicleKind.TRAIN,3f,7)}
    Canvas(modifier.height(30.dp)) {
        val line=4.dp.toPx();val car=8.dp.toPx();val r=6.5.dp.toPx()
        val trainLength=consist.sumOf {(it.length*car).toDouble()}.toFloat()
        val left=trainLength+r*2.4f;val right=size.width-r-2.dp.toPx();val y=size.height/2
        val step=if(count>1) (right-left)/(count-1) else 0f
        fun x(at: Float)=left+step*at
        val p=position.value.coerceIn(0f,(count-1).toFloat())
        fun colorAt(at: Float): Color {
            val i=at.coerceIn(0f,(count-1).toFloat());val k=kotlin.math.floor(i).toInt().coerceAtMost(count-1)
            return if(k>=count-1) stops[count-1] else androidx.compose.ui.graphics.lerp(stops[k],stops[k+1],i-k)
        }
        // The whole line, then the travelled part in the colours of its stops (a depot stub before the first stop).
        drawLine(idle,Offset(0f,y),Offset(right,y),line,StrokeCap.Round)
        drawLine(stops[0],Offset(0f,y),Offset(x(0f),y),line,StrokeCap.Round)
        for(k in 0 until count-1) {
            val end=minOf(p,k+1f);if(end<=k) break
            drawLine(Brush.horizontalGradient(listOf(stops[k],stops[k+1]),x(k.toFloat()),x(k+1f)),Offset(x(k.toFloat()),y),Offset(x(end),y),line,StrokeCap.Round)
        }
        // The stops: open rings ahead, filled behind; the one the train just reached pops a little.
        for(k in 0 until count) {
            val c=Offset(x(k.toFloat()),y)
            val near=kotlin.math.abs(p-k)
            val reached=p>=k-.04f
            val pop=if(quiet) 1f else 1f+.32f*kotlin.math.exp(-(near*near)*18f)
            drawCircle(surface,r*pop,c)
            if(reached) {drawCircle(stops[k],r*pop,c);drawCircle(surface,r*.42f*pop,c)}
            else drawCircle(idle,r,c,style=Stroke(line*.7f))
        }
        // The train: its front at the position, body colour flowing between the stops' colours.
        val body=colorAt(p)
        val accent=androidx.compose.ui.graphics.lerp(body,Color.White,.45f)
        // It halts with its nose just before the stop's circle instead of covering it.
        var front=x(p)-r*1.25f
        consist.forEachIndexed {i,piece->
            val length=piece.length*car
            drawVehicleCar(VehicleKind.TRAIN,piece,i,Offset(front-length/2,y),Offset(1f,0f),car,1f,body,ink,accent)
            front-=length+car*.08f
        }
    }
}

/** The stops' colours: the design's accent turning round the colour wheel, in its own brightness and strength. */
internal fun onboardingStopColors(accent: Color,count: Int): List<Color> {
    val hsv=FloatArray(3)
    android.graphics.Color.colorToHSV(accent.toArgb(),hsv)
    val saturation=hsv[1].coerceAtLeast(.38f);val value=hsv[2].coerceIn(.45f,.92f)
    return List(count.coerceAtLeast(1)) {i->Color(android.graphics.Color.HSVToColor(floatArrayOf((hsv[0]+i*52f)%360f,saturation,value)))}
}

/**
 * Build 121: the setup's button row. "Weiter" keeps its full width (up to 62 % of the row) and is never pushed out;
 * "Überspringen" and "Zurück" share what is left, and their labels shrink instead of breaking inside a word.
 */
@Composable private fun OnboardingBar(back: (@Composable ()->Unit)?,skip: (@Composable ()->Unit)?,next: @Composable ()->Unit) {
    Layout(content={Box {back?.invoke()};Box {skip?.invoke()};Box {next()}},modifier=Modifier.fillMaxWidth().padding(top=12.dp)) {items,constraints->
        val gap=8.dp.roundToPx();val width=constraints.maxWidth
        fun natural(i: Int)=items[i].maxIntrinsicWidth(Constraints.Infinity)
        val nextItem=items[2].measure(Constraints(maxWidth=minOf(natural(2),(width*.62f).toInt())))
        var room=(width-nextItem.width-gap).coerceAtLeast(0)
        val backNatural=natural(0)
        val skipItem=items[1].measure(Constraints(maxWidth=minOf(natural(1),if(backNatural>0) maxOf(room-backNatural-gap,room/2) else room).coerceAtLeast(0)))
        if(skipItem.width>0) room=(room-skipItem.width-gap).coerceAtLeast(0)
        val backItem=items[0].measure(Constraints(maxWidth=minOf(backNatural,room)))
        val height=maxOf(backItem.height,skipItem.height,nextItem.height)
        layout(width,height) {
            backItem.place(0,(height-backItem.height)/2)
            nextItem.place(width-nextItem.width,(height-nextItem.height)/2)
            skipItem.place(width-nextItem.width-gap-skipItem.width,(height-skipItem.height)/2)
        }
    }
}

/**
 * Build 102 → 121: two cards, each with its own button; nothing is asked before a tap. The text has the whole
 * width (no word breaks in two), the button sits below it and turns into a quiet "Erlaubt" once granted.
 */
@Composable private fun PermissionSetupContent(p: SetupPermissions) {
    Text(tr("Beides ist freiwillig und lässt sich jederzeit ändern."),style=MaterialTheme.typography.bodyLarge,
        color=MaterialTheme.colorScheme.onSurfaceVariant)
    val located=p.location!=SetupLocationAccess.NONE
    PermissionRow(true,tr("Standort"),tr("Verbindungen ab deinem Standort und Abfahrten in deiner Nähe"),
        granted=located && p.locationEnabled,
        action=if(located && !p.locationEnabled) tr("Standort einschalten") else tr("Standort erlauben"),
        enabled=!p.busy,tag="onboarding-permission-location",onClick=p.requestLocation)
    PermissionRow(false,tr("Mitteilungen"),tr("Hinweise während der Fahrt, zum Beispiel vor dem Umsteigen"),
        granted=p.notificationsEnabled,action=tr("Mitteilungen erlauben"),enabled=!p.busy,tag="onboarding-permission-notifications",
        onClick=p.requestNotifications)
    CalmVisibility(!located,enter=fadeIn(tween(200))+expandVertically(),exit=fadeOut(tween(140))+shrinkVertically()) {
        Text(tr("Ohne Standort wählst du im nächsten Schritt einen Startpunkt für deine Suchen."),style=MaterialTheme.typography.bodyMedium,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun PermissionRow(location: Boolean,title: String,detail: String,granted: Boolean,action: String,enabled: Boolean,
    tag: String,onClick: ()->Unit) {
    val quiet=studioQuiet()
    AppElevatedCard(Modifier.fillMaxWidth().testTag(tag)) {
        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                PermissionGlyph(location,granted,Modifier.size(52.dp))
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    FittedText(title,style=MaterialTheme.typography.titleMedium,maxLines=1)
                    Text(detail,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            AnimatedContent(granted,label="permission-$tag",transitionSpec={
                if(quiet) EnterTransition.None togetherWith ExitTransition.None
                else (scaleIn(StudioPop,initialScale=.6f)+fadeIn(tween(160))) togetherWith (scaleOut(tween(120),targetScale=.8f)+fadeOut(tween(100)))
            }) {done->
                if(done) Row(Modifier.fillMaxWidth().heightIn(min=48.dp).semantics(mergeDescendants=true) {},verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(6.dp,Alignment.CenterHorizontally)) {
                    AppIcon(R.drawable.ic_check,null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.primary)
                    FittedText(tr("Erlaubt"),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary,maxLines=1)
                }
                else ThemedTonalButton(onClick,Modifier.fillMaxWidth().heightIn(min=48.dp),enabled=enabled) {FittedText(action,maxLines=1)}
            }
        }
    }
}

/**
 * Build 121: the permission's picture. Location: a position mark sending out radar rings while it waits, one calm
 * ring once granted. Notifications: a bell that swings now and then while it waits, still once granted.
 */
@Composable private fun PermissionGlyph(location: Boolean,granted: Boolean,modifier: Modifier) {
    val quiet=studioQuiet()
    val colors=MaterialTheme.colorScheme
    val waiting=!granted && !quiet
    val t=if(!waiting) 0f else rememberInfiniteTransition(label="permission-glyph").animateFloat(0f,1f,
        infiniteRepeatable(tween(if(location) 1800 else 2400,easing=LinearEasing)),label="permission-glyph-phase").value
    val settle by animateFloatAsState(if(granted) 1f else 0f,if(quiet) snap() else StudioPop,label="permission-glyph-settle")
    Box(modifier,contentAlignment=Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val c=center;val r=size.minDimension/2
            drawCircle(colors.primaryContainer,r*(.78f+.06f*settle),c)
            if(location && waiting) for(k in 0..1) {
                val p=((t+k*.5f)%1f)
                drawCircle(colors.primary.copy(alpha=.45f*(1f-p)),r*(.78f+.22f*p),c,style=Stroke(1.5.dp.toPx()))
            }
            if(granted) drawCircle(colors.primary.copy(alpha=.6f*settle),r*.98f,c,style=Stroke(2.dp.toPx()))
        }
        // A short swing in the first third of every cycle.
        val swing=if(!location && waiting && t<.3f) 14f*kotlin.math.sin(t/.3f*4f*Math.PI.toFloat())*(1f-t/.3f) else 0f
        AppIcon(if(location) R.drawable.ic_gps else R.drawable.ic_notifications,null,
            Modifier.size(24.dp).graphicsLayer {rotationZ=swing;transformOrigin=androidx.compose.ui.graphics.TransformOrigin(.5f,.1f)},
            tint=MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

/**
 * Build 121: without location access, a start place makes the app useful at once – every search starts there
 * (it can be changed in the search field at any time). Chosen, the place sits in a card that springs in.
 */
@Composable private fun StartPlaceContent(settings: AppSettings,providerKey: String,search: suspend (String)->List<Location>,
    enabled: Boolean,onPlace: (Location?)->Unit) {
    val quiet=studioQuiet()
    Text(tr("Ohne Standortfreigabe beginnen deine Suchen hier. Im Suchfeld kannst du den Startpunkt jederzeit ändern."),
        style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
    AnimatedContent(settings.setupStart,label="onboarding-start",transitionSpec={studioTransition(quiet,targetState!=null,landing=targetState!=null)}) {place->
        if(place==null) PlaceSearchField(tr("Ort, Adresse oder Haltestelle"),null,providerKey,search,{picked->if(picked?.routable==true) onPlace(picked)},
            Modifier.fillMaxWidth().testTag("onboarding-start-search"),enabled=enabled,expressive=true,outlinedAtRest=true,
            favorites=emptyList(),chooseHint=false)
        else AppElevatedCard(Modifier.fillMaxWidth().testTag("onboarding-start-chosen")) {
            Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.primaryContainer,CircleShape),contentAlignment=Alignment.Center) {
                    AppIcon(if(place.kind==LocationKind.STOP) R.drawable.ic_stop else R.drawable.ic_place,null,Modifier.size(24.dp),
                        tint=MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Column(Modifier.weight(1f)) {
                    Text(tr("Startpunkt"),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(place.name,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                TextButton({onPlace(null)},enabled=enabled,modifier=Modifier.heightIn(min=48.dp).testTag("onboarding-start-change")) {
                    FittedText(tr("Ändern"),maxLines=1)
                }
            }
        }
    }
}

/**
 * Build 121: the most important modes with their animated pictures, as on "Modi & Verhalten". Each switch shows
 * its effect at once – the whole setup turns grey, calm or minimal – and can be changed again later.
 */
@Composable private fun OnboardingModes(settings: AppSettings,enabled: Boolean,onChange: ((AppSettings)->AppSettings)->Unit) {
    Text(tr("Wähle, wie sich Öpen-V anfühlt. Alles lässt sich später unter „Modi & Verhalten“ ändern."),
        style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
    val quiet=studioQuiet()
    Column(Modifier.fillMaxWidth().testTag("onboarding-modes"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Box(Modifier.staggeredRise(0,quiet)) {
            MonochromeCard(settings.monochrome,tr("Die ganze App in Graustufen – für weniger Ablenkung.")) {v->if(enabled) onChange {it.copy(monochrome=v)}}
        }
        ModeSwitchCard(tr("Minimalismus"),tr("Nur das Wesentliche: ohne Hinweise, Infotexte und Suchchroniken."),settings.minimalistMode,
            {v->if(enabled) onChange {it.copy(minimalistMode=v)}},"onboarding-mode-minimal",modifier=Modifier.staggeredRise(1,quiet)) {on,m->MinimalPicture(on,m)}
        ModeSwitchCard(tr("Ruhemodus"),tr("Weniger Bewegung und ein eigenes ruhiges Design."),settings.quietMode,
            {v->if(enabled) onChange {it.withQuietMode(v)}},"onboarding-mode-quiet",modifier=Modifier.staggeredRise(2,quiet)) {on,m->QuietPicture(on,m)}
        ModeSwitchCard(tr("Planermodus"),tr("Nur Suchen und Planen – ohne Standort und Navigationsleiste."),settings.plannerMode,
            {v->if(enabled) onChange {it.copy(plannerMode=v)}},"onboarding-mode-planner",modifier=Modifier.staggeredRise(3,quiet)) {on,m->PlannerPicture(on,m)}
    }
}

/** Build 102: a few designs of different moods instead of all; the whole setup changes with a tap. */
internal val onboardingDesigns: List<AppTheme?> = listOf(null,AppTheme.SEA_GLASS,AppTheme.FOREST,AppTheme.SUNSET_POSTCARD,
    AppTheme.BLUE_HOUR,AppTheme.SYNTHWAVE)

@Composable private fun DesignChoices(settings: AppSettings,enabled: Boolean,onSelect: (AppTheme?)->Unit) {
    Text(tr("Weitere Designs findest du später in den Einstellungen."),style=MaterialTheme.typography.bodyLarge,
        color=MaterialTheme.colorScheme.onSurfaceVariant)
    val selected = settings.appTheme
    val choices = if (settings.quietMode) listOf(null) + quietAppThemes else onboardingDesigns
    val shown = if (selected in choices) choices else listOf(selected) + choices.dropLast(1)
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
