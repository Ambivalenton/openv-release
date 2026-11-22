// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import dev.ambivalenton.openv.data.model.TimetableProfile

import android.content.Intent
import android.content.Context
import android.widget.Toast
import androidx.core.net.toUri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.localization.tr

/**
 * A readable source link, also usable from an overlay service without an Activity context.
 */
@Composable
fun TransitSourceAttribution(modifier: Modifier = Modifier, compact: Boolean = false) {
    if (!dev.ambivalenton.openv.data.model.TimetableProfile.sourceNotice) return
    val context = LocalContext.current
    Text(
        tr(if (compact) "Fahrplandaten: VRR" else "Fahrplandaten: VRR · Alle Angaben ohne Gewähr."),
        modifier.testTag("transit-source-vrr").clickable(role = Role.Button) {
            openPublicationLink(context, dev.ambivalenton.openv.data.model.TimetableServices.active.sourceUrl)
        }.padding(vertical = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** External pages and mail remain user actions and fail gracefully on devices without a handler. */
internal fun openPublicationLink(context: Context, uri: String) {
    runCatching {
        context.startActivity(Intent(if(uri.startsWith("mailto:")) Intent.ACTION_SENDTO else Intent.ACTION_VIEW,
            uri.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        Toast.makeText(context, tr("Keine passende App zum Öffnen gefunden."), Toast.LENGTH_LONG).show()
    }
}
