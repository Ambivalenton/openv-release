// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.screens

import android.animation.ValueAnimator
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.DialogProperties
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.components.*
import dev.ambivalenton.openv.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*

internal fun themeFlightBounds(start:Rect,floating:Rect,destination:Rect,progress:Float,first:Boolean):Rect{
    val p=progress.coerceIn(0f,1f)
    fun blend(a:Rect,b:Rect,t:Float)=Rect(a.left+(b.left-a.left)*t,a.top+(b.top-a.top)*t,a.right+(b.right-a.right)*t,a.bottom+(b.bottom-a.bottom)*t)
    val base=if(first&&p<.35f)blend(start,floating,p/.35f)else blend(if(first)floating else start,destination,if(first)(p-.35f)/.65f else p)
    val curve=if(first&&p<.35f)0f else sin((if(first)(p-.35f)/.65f else p)*PI).toFloat()*minOf(start.width*.08f,32f)
    return base.translate(0f,-curve)
}

/** The first save gives the new menu time to grow; later saves arrive within one second. */
@Composable internal fun PersonalThemeSaveDialog(state:PersonalThemeUiState,store:StoredState,
    onSave:suspend(String)->Result<PersonalTheme>){
    if(!state.dialogOpen)return
    val open=remember{Animatable(0f)};val flight=remember{Animatable(0f)}
    var saved by remember{mutableStateOf<PersonalTheme?>(null)}
    var busy by remember{mutableStateOf(false)};var closing by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf<String?>(null)}
    var frozenStart by remember{mutableStateOf<Rect?>(null)}
    var pressedAt by remember{mutableLongStateOf(0L)}
    var first by remember{mutableStateOf(false)}
    val settings=store.settings;val calm=settings.reduceMotion||!ValueAnimator.areAnimatorsEnabled()
    val scope=rememberCoroutineScope();val keyboard=LocalSoftwareKeyboardController.current;val focus=LocalFocusManager.current
    val latestSave by rememberUpdatedState(onSave)
    fun cancel(){if(!busy&&saved==null)closing=true}
    DisposableEffect(Unit){onDispose{state.deferFirstInsertion=false}}
    LaunchedEffect(closing,calm){
        open.animateTo(if(closing)0f else 1f,tween(if(calm)0 else if(closing)360 else 550, easing=FastOutSlowInEasing))
        if(closing)state.dismiss()
    }
    LaunchedEffect(saved,calm){
        if(saved==null)return@LaunchedEffect
        if(calm){state.deferFirstInsertion=false;state.expanded=true;state.revealTick++;state.dismiss();return@LaunchedEffect}
        if(first){
            flight.animateTo(.35f,tween(430, easing=FastOutSlowInEasing));delay(170)
            state.deferFirstInsertion=false;state.revealTick++
            delay(660)
            flight.animateTo(1f,tween(620,easing=FastOutSlowInEasing))
        }else{
            state.revealTick++
            val duration=(950-(android.os.SystemClock.elapsedRealtime()-pressedAt)).toInt().coerceIn(80,850)
            flight.animateTo(1f,tween(duration,easing=FastOutSlowInEasing))
        }
        state.expanded=true;state.dismiss()
    }
    AppDialog({cancel()},DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false),onTabDismiss=state::dismiss,drawScrim=false){
        ReleaseDialogWindowOnDispose(ownsScrim=true)
        val density=LocalDensity.current;val view=LocalView.current;val colors=MaterialTheme.colorScheme
        var screenOffset by remember{mutableStateOf(Offset.Zero)}
        BoxWithConstraints(Modifier.fillMaxSize().imePadding().testTag("personal-theme-save-dialog").onGloballyPositioned{c->
            val screen=IntArray(2);val window=IntArray(2);view.getLocationOnScreen(screen);view.getLocationInWindow(window)
            screenOffset=c.boundsInWindow().topLeft+Offset((screen[0]-window[0]).toFloat(),(screen[1]-window[1]).toFloat())
        }.semantics{paneTitle=tr("Persönliches Theme speichern");stateDescription=if(saved==null)tr("Name eingeben")else tr("Theme gespeichert")}){
            val w=with(density){maxWidth.toPx()};val h=with(density){maxHeight.toPx()};val d=density.density
            val origin=(state.buttonBounds?:Rect(w-64*d,28*d,w-16*d,76*d)).translate(-screenOffset.x,-screenOffset.y)
            val width=min(w-32*d,420*d);val height=min(h-24*d,340*d).coerceAtLeast(1f)
            val target=Rect((w-width)/2,(h-height)/2,(w+width)/2,(h+height)/2)
            val floating=Rect(w/2-96*d,h*.5f-46*d,w/2+96*d,h*.5f+46*d)
            val menu=(state.menuBounds?:state.buttonBounds?:origin).translate(-screenOffset.x,-screenOffset.y)
            val destination=Rect(menu.center.x-24*d,menu.center.y-16*d,menu.center.x+24*d,menu.center.y+16*d)
            val p=open.value
            val bounds=if(saved!=null)themeFlightBounds(frozenStart?:target,floating,destination,flight.value,first)
                else Rect(origin.left+(target.left-origin.left)*p,origin.top+(target.top-origin.top)*p,
                    origin.right+(target.right-origin.right)*p,origin.bottom+(target.bottom-origin.bottom)*p)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=.32f*p*(1-flight.value))))
            Surface(Modifier.offset{IntOffset(bounds.left.roundToInt(),bounds.top.roundToInt())}
                .size(with(density){bounds.width.toDp()},with(density){bounds.height.toDp()})
                .graphicsLayer{alpha=if(saved!=null)(1-flight.value.pow(8)).coerceIn(0f,1f)else 1f}
                .testTag(if(saved==null)"theme-name-popup"else"theme-save-flying-card"),
                shape=RoundedCornerShape((24*(1-p)+28*p*LocalAppVisuals.current.radiusScale).dp),
                color=androidx.compose.ui.graphics.lerp(colors.primaryContainer,colors.surfaceContainerHigh,p).copy(alpha=1f),shadowElevation=12.dp){
                if(saved!=null){
                    Column(Modifier.fillMaxSize().padding(12.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){
                        AppIcon(R.drawable.ic_save,null)
                        if(bounds.width>120*d)FittedText(saved!!.name,style=MaterialTheme.typography.titleSmall)
                    }
                }else if(p<.55f){
                    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){AppIcon(R.drawable.ic_save,null)}
                }else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp)
                    .graphicsLayer{alpha=((p-.55f)/.45f).coerceIn(0f,1f)},verticalArrangement=Arrangement.spacedBy(14.dp)){
                    FittedText(tr("Dein persönliches Theme"),style=MaterialTheme.typography.titleLarge)
                    Text(tr("Speichert deine Designeinstellungen: Farben, Schrift, Übersicht, Balken, Anzeigetafel und App-Logo."),style=MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(state.draft,{state.draft=it.take(48);error=null},Modifier.fillMaxWidth().testTag("personal-theme-name"),
                        label={Text(tr("Name"))},singleLine=true,enabled=!busy,isError=error!=null)
                    error?.let{Text(it,color=colors.error,style=MaterialTheme.typography.bodySmall,modifier=Modifier.semantics{liveRegion=LiveRegionMode.Polite})}
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End,verticalAlignment=Alignment.CenterVertically){
                        TextButton({cancel()},enabled=!busy){Text(tr("Verwerfen"))}
                        ThemedButton({
                            val name=personalThemeName(state.draft)
                            first=!store.personalThemesIntroduced;state.deferFirstInsertion=first&&!calm
                            pressedAt=android.os.SystemClock.elapsedRealtime()
                            frozenStart=bounds;busy=true;focus.clearFocus();keyboard?.hide()
                            scope.launch{
                                val result=latestSave(name)
                                result.onSuccess{state.committed=true;saved=it}.onFailure{
                                    state.deferFirstInsertion=false;busy=false;error=it.message?:tr("Das Theme konnte nicht gespeichert werden.")
                                }
                            }
                        },enabled=!busy&&validPersonalThemeName(personalThemeName(state.draft)),modifier=Modifier.testTag("commit-personal-theme")){
                            Text(tr("Speichern"))
                        }
                    }
                }
            }
        }
    }
}
