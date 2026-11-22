// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.ambivalenton.openv.data.model.AppSettings
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.tracking.overlayAllowed

/** Opens the system screen for "display over other apps"; false if no handler exists. */
fun openOverlaySettings(context:Context):Boolean {
    val specific=Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,"package:${context.packageName}".toUri())
    val details=Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,"package:${context.packageName}".toUri())
    for(intent in listOf(specific,details)) {
        try {context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return true}
        catch(_:ActivityNotFoundException) {} catch(_:SecurityException) {}
    }
    return false
}

/**
 * The switch is only shown as active when the mode is on AND overlay access is actually granted.
 * Turning it on without access leads to the system screen; the result is checked on return.
 * Rejection, later revocation or a missing settings screen never leave a misleading active switch.
 */
@Composable fun PinnedBubbleSetting(settings:AppSettings,update:((AppSettings)->AppSettings)->Unit) {
    val context=LocalContext.current
    var allowed by remember {mutableStateOf(overlayAllowed(context))}
    var problem by remember {mutableStateOf<String?>(null)}
    var awaiting by remember {mutableStateOf(false)}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            allowed=overlayAllowed(context)
            if(awaiting) {
                awaiting=false
                if(allowed){update {it.copy(pinnedBubble=true)};problem=null}
                else problem=tr("Ohne die Erlaubnis „Über anderen Apps einblenden“ kann keine schwebende Reisekarte angezeigt werden.")
            }
        }
    }
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        allowed=overlayAllowed(context)
        awaiting=false
        if(allowed){update {it.copy(pinnedBubble=true)};problem=null}
        else problem=tr("Ohne die Erlaubnis „Über anderen Apps einblenden“ kann keine schwebende Reisekarte angezeigt werden.")
    }
    val active=settings.pinnedBubble && allowed
    ToggleRow(tr("Schwebende Reisekarte"),
        tr("Zeigt deine nächste Abfahrt, auch wenn du eine andere App geöffnet hast. Nutzt denselben Reisestand, ohne zusätzliche Abfragen."),
        active) {on->
        problem=null
        if(!on) update {it.copy(pinnedBubble=false)}
        else if(allowed) update {it.copy(pinnedBubble=true)}
        else {
            awaiting=true
            val intent=Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,"package:${context.packageName}".toUri())
            try {launcher.launch(intent)}
            catch(_:ActivityNotFoundException) {
                awaiting=openOverlaySettings(context)
                if(!awaiting)problem=tr("Die Einstellung für Overlays konnte nicht geöffnet werden.")
            }
        }
    }
    val hint=problem ?: if(settings.pinnedBubble && !allowed)tr("Die Erlaubnis für die schwebende Reisekarte wurde entzogen. Schalte sie erneut ein, um sie zu erteilen.") else null
    hint?.let {Text(it,Modifier.testTag("pinned-bubble-hint"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)}
}
