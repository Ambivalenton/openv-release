// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.onboarding

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

enum class SetupLocationAccess { NONE, APPROXIMATE, PRECISE }
data class SetupPermissions(
    val location: SetupLocationAccess=SetupLocationAccess.NONE,
    val locationEnabled: Boolean=true,
    val notificationsEnabled: Boolean=false,
    val busy: Boolean=false,
    val requestLocation: ()->Unit={},
    val requestNotifications: ()->Unit={},
)

private tailrec fun Context.activity(): Activity? = when(this) {
    is Activity->this
    is ContextWrapper->baseContext.activity()
    else->null
}

/** System state is the authority. No permission dialog is launched during composition. */
@Composable
fun rememberOnboardingPermissions(onLocationGranted: ()->Unit): SetupPermissions {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val latestLocationCallback by rememberUpdatedState(onLocationGranted)
    var revision by remember {mutableIntStateOf(0)}
    var busy by rememberSaveable {mutableStateOf(false)}
    var askedLocation by rememberSaveable {mutableStateOf(false)}
    var askedNotifications by rememberSaveable {mutableStateOf(false)}
    fun refresh() {revision++;busy=false}
    val locationLauncher=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {result->
        refresh()
        if(result.values.any {it}) latestLocationCallback()
    }
    val notificationLauncher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {refresh()}
    val settingsLauncher=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refresh()
        if(ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)
            latestLocationCallback()
    }
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_RESUME) refresh()}
        lifecycle.addObserver(observer)
        onDispose {lifecycle.removeObserver(observer)}
    }
    val snapshot=remember(revision,context) {
        val access=when {
            ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED->SetupLocationAccess.PRECISE
            ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED->SetupLocationAccess.APPROXIMATE
            else->SetupLocationAccess.NONE
        }
        Triple(access,LocationManagerCompat.isLocationEnabled(context.getSystemService(LocationManager::class.java)),
            NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    fun openSettings(intent: Intent) {
        busy=true
        runCatching {settingsLauncher.launch(intent)}.onFailure {refresh()}
    }
    return SetupPermissions(snapshot.first,snapshot.second,snapshot.third,busy,
        requestLocation={
            if(!busy) {
                if(snapshot.first!=SetupLocationAccess.NONE && !snapshot.second)
                    openSettings(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                else if(snapshot.first==SetupLocationAccess.NONE) {
                    val canExplain=context.activity()?.let {ActivityCompat.shouldShowRequestPermissionRationale(it,Manifest.permission.ACCESS_COARSE_LOCATION)}==true
                    if(askedLocation && !canExplain)
                        openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}")))
                    else {
                        askedLocation=true;busy=true
                        locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION))
                    }
                }
            }
        },
        requestNotifications={
            if(!busy && !snapshot.third) {
                val denied=Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED
                val canExplain=Build.VERSION.SDK_INT>=33 && context.activity()?.let {
                    ActivityCompat.shouldShowRequestPermissionRationale(it,Manifest.permission.POST_NOTIFICATIONS)
                }==true
                if(denied && (!askedNotifications || canExplain)) {
                    askedNotifications=true;busy=true
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))
            }
        })
}
