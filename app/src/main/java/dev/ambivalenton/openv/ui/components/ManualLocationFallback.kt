// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import android.content.*
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.DialogProperties
import dev.ambivalenton.openv.ui.theme.appCorner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.location.DeviceLocationProvider
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.*

internal data class DeviceLocationAvailability(val available:Boolean,val openedAt:Long)

/** Listen to the Android location switch and recheck permissions whenever the app resumes. */
@Composable internal fun rememberDeviceLocationAvailable(device:DeviceLocationProvider):DeviceLocationAvailability {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    fun available()=device.hasPermission() && device.locationEnabled()
    var value by remember(device){mutableStateOf(available())}
    var openedAt by remember(device){mutableLongStateOf(System.currentTimeMillis())}
    DisposableEffect(device,lifecycle,context) {
        val receiver=object:BroadcastReceiver() {override fun onReceive(c:Context?,intent:Intent?){value=available()}}
        val filter=IntentFilter(LocationManager.MODE_CHANGED_ACTION).apply {addAction(LocationManager.PROVIDERS_CHANGED_ACTION)}
        ContextCompat.registerReceiver(context,receiver,filter,ContextCompat.RECEIVER_NOT_EXPORTED)
        val observer=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_RESUME){value=available();openedAt=System.currentTimeMillis()}}
        lifecycle.addObserver(observer)
        onDispose {lifecycle.removeObserver(observer);context.unregisterReceiver(receiver)}
    }
    return DeviceLocationAvailability(value,openedAt)
}

/**
 * Without device location the start switches to manual mode. As long as no start is set, the picker
 * comes back when the app is opened again (Restored at the publisher's request, so every
 * function works without location access). Not in the mode without location.
 */
@Composable internal fun ManualLocationFallback(ui:AppUiState,vm:AppViewModel,device:DeviceLocationProvider,introduction:Boolean) {
    val status=rememberDeviceLocationAvailable(device)
    val available=status.available
    LaunchedEffect(ui.ready,introduction,available,ui.store.settings.originMode,ui.store.settings.manualOrigin) {
        if(ui.ready && ui.storageError==null && !introduction && !available && ui.store.settings!=ui.store.settings.manualLocationFallback())
            vm.useManualLocationFallback()
    }
    var dismissed by rememberSaveable(available,status.openedAt){mutableStateOf(false)}
    // While a time-dependent location applies and no start was chosen, there is nothing to ask.
    val scheduled=ui.store.settings.manualOrigin?.routable!=true && ui.store.scheduledFavorite(status.openedAt)!=null
    val needsPlace=ui.store.settings.manualLocationPromptDue(status.openedAt) && !scheduled
    if(ui.ready && ui.storageError==null && !introduction && !available && needsPlace && !dismissed && !ui.store.settings.plannerMode)
        ManualLocationPicker(ui.store,vm::findLocations,vm::setManualLocation,{dismissed=true})
}

/**
 * "Startort wählen" (H02/M18). Title, content and actions stay inside the safe area below the status
 * bar and cut-out and above the keyboard; the title may wrap, the content scrolls, the actions stay
 * reachable. Saved places are the first suggestions of the field.
 */
@Composable internal fun ManualLocationPicker(store:StoredState,search:suspend(String)->List<Location>,
    onSelect:(Location)->Unit,onDismiss:()->Unit) {
    var picked by rememberSaveable(stateSaver=locationSaver){mutableStateOf<Location?>(store.settings.manualOrigin?.takeIf {it.routable && !it.isDevicePosition})}
    AppDialog(onDismiss,DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(vertical=12.dp),
            contentAlignment=Alignment.Center) {
            Surface(Modifier.widthIn(max=560.dp).fillMaxWidth(.94f).heightIn(max=maxHeight).testTag("manual-location-dialog"),
                shape=appCorner(28.dp),color=MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text(tr("Startort wählen"),Modifier.testTag("manual-location-title"),style=MaterialTheme.typography.titleLarge)
                    Column(Modifier.weight(1f,fill=false).fillMaxWidth().verticalScroll(rememberScrollState())
                        .testTag("manual-location-fallback"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text(tr("Du kannst Öpen-V ohne Gerätestandort nutzen. Der Start gilt für Suche, Übersicht und Anzeigetafel, bis du ihn änderst. Der Reisebegleiter verwendet solange Fahrplan- und Serverdaten."))
                        PlaceSearchField(tr("Haltestelle oder Adresse"),picked,"${store.settings.engine}:${store.settings.geocodingEndpoint}",search,{picked=it})
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                        TextButton(onDismiss,Modifier.heightIn(min=48.dp)){Text(tr("Später"))}
                        ThemedButton({picked?.takeIf {it.routable && !it.isDevicePosition}?.let(onSelect)},
                            enabled=picked?.let {it.routable && !it.isDevicePosition}==true,
                            modifier=Modifier.heightIn(min=48.dp).testTag("manual-location-apply")){Text(tr("Übernehmen"))}
                    }
                }
            }
        }
    }
}
