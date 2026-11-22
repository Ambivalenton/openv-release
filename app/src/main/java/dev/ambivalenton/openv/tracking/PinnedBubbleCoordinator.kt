// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.tracking

import android.app.Activity
import android.app.Application
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Bundle
import android.provider.Settings
import androidx.core.content.ContextCompat
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.LocationRepository
import dev.ambivalenton.openv.data.repository.PinnedJourneyController
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Can this app draw a system overlay right now? Revocation is noticed at every check. */
fun overlayAllowed(context:Context)=Settings.canDrawOverlays(context)

/**
 * Process-wide owner of the floating bubble. It only observes the shared journey state and the
 * setting; it never starts tracking, location, sensors, polling or replanning. The bubble shows
 * inside and outside the app instead of the pinned card, hides on the lock screen, and disappears when the
 * pin ends. Closing it is stored per pinned session in the controller.
 */
class PinnedBubbleCoordinator(private val app:Application,private val controller:PinnedJourneyController,
    private val locations:LocationRepository) {
    // Observation runs off the main thread; only window operations switch to it (resolved when
    // used, never captured at construction).
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
    private val mutableActive=MutableStateFlow(false)
    /** True while mode, access, pin and "not closed" ask for a bubble (it may still be hidden). */
    val active:StateFlow<Boolean> = mutableActive.asStateFlow()
    private val mutableShown=MutableStateFlow(false)
    /** True exactly while a bubble window is attached. Only this hides the pinned card in the
     * overview, so a locked screen, a failed window or a closed bubble always leaves the card. */
    val shown:StateFlow<Boolean> = mutableShown.asStateFlow()
    private val deviceLocked=MutableStateFlow(false)
    private val recheck=MutableStateFlow(0)
    private var overlay:PinnedBubbleOverlay?=null
    private var started=false
    private var observer:Job?=null

    private val activities=object:Application.ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity:Activity) {}
        override fun onActivityStopped(activity:Activity) {}
        // Returning from the overlay-permission screen: re-check the access.
        override fun onActivityResumed(activity:Activity) {recheck.value++}
        override fun onActivityCreated(activity:Activity,savedInstanceState:Bundle?) {}
        override fun onActivityPaused(activity:Activity) {}
        override fun onActivitySaveInstanceState(activity:Activity,outState:Bundle) {}
        override fun onActivityDestroyed(activity:Activity) {}
    }
    private val screen=object:BroadcastReceiver() {
        override fun onReceive(context:Context,intent:Intent) {
            deviceLocked.value=when(intent.action) {
                Intent.ACTION_SCREEN_OFF->true
                Intent.ACTION_USER_PRESENT->false
                else->app.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked==true
            }
        }
    }
    private val configuration=object:ComponentCallbacks {
        override fun onConfigurationChanged(newConfig:Configuration) {overlay?.reclamp()}
        @Deprecated("Deprecated in Java") override fun onLowMemory() {}
    }

    fun start() {
        if(started)return
        started=true
        app.registerActivityLifecycleCallbacks(activities)
        app.registerComponentCallbacks(configuration)
        ContextCompat.registerReceiver(app,screen,IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF);addAction(Intent.ACTION_SCREEN_ON);addAction(Intent.ACTION_USER_PRESENT)
        },ContextCompat.RECEIVER_NOT_EXPORTED)
        deviceLocked.value=app.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked==true
        observer=scope.launch {
            // With the mode off (the default) nothing but the setting itself is observed.
            locations.state.map {it.settings}.distinctUntilChanged().catch {}.collectLatest {settings->
                if(!settings.pinnedBubble) {mutableActive.value=false;withContext(Dispatchers.Main) {overlay?.hide()};return@collectLatest}
                combine(controller.state,deviceLocked,recheck) {state,locked,_->
                    val inputs=PinnedBubbleInputs(true,overlayAllowed(app),state.session?.id,state.session?.bubbleDismissed==true,locked)
                    mutableActive.value=pinnedBubbleActive(inputs)
                    state to pinnedBubbleVisible(inputs)
                }.collect {(state,show)->
                    withContext(Dispatchers.Main) {
                        if(show) {
                            val host=overlay ?: PinnedBubbleOverlay(app,mutableShown) {id->scope.launch {controller.setBubbleDismissed(id,true)}}.also {overlay=it}
                            host.update(state,settings);host.show()
                        } else overlay?.hide()
                    }
                }
            }
        }
    }

    /** Ends the observation and removes a shown bubble. Call on the main thread (used by tests). */
    internal fun stop() {
        if(!started)return
        started=false
        observer?.cancel();observer=null;mutableActive.value=false
        app.unregisterActivityLifecycleCallbacks(activities)
        app.unregisterComponentCallbacks(configuration)
        try {app.unregisterReceiver(screen)}catch(_:IllegalArgumentException){}
        overlay?.hide()
    }

    /** Explicit bubble button: reopen for this session (the app then moves to the background). */
    suspend fun reopen(sessionId:String):Boolean=controller.setBubbleDismissed(sessionId,false)
}
