// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.NoticeHit
import dev.ambivalenton.openv.localization.tr

/** 2.5: keys of notices no longer emphasised and the "Hinweis nicht mehr hervorheben" action, provided by the app. */
data class JourneyNoticeActions(val dismissed: Set<String> = emptySet(), val onDismiss: ((NoticeHit) -> Unit)? = null)
val LocalJourneyNotices = staticCompositionLocalOf { JourneyNoticeActions() }

/** 2.5: the exact original text with the words that caused the hit in bold. */
fun noticeAnnotated(hit: NoticeHit): AnnotatedString = buildAnnotatedString {
    var at = 0
    for (range in hit.triggers.sortedBy { it.first }) {
        val start = range.first.coerceIn(0, hit.text.length); val end = (range.last + 1).coerceIn(start, hit.text.length)
        if (start < at) continue
        append(hit.text.substring(at, start))
        pushStyle(SpanStyle(fontWeight = FontWeight.Bold)); append(hit.text.substring(start, end)); pop()
        at = end
    }
    append(hit.text.substring(at.coerceAtMost(hit.text.length)))
}

/**
 * 2.5: matched notices at the very top of the timeline, already open: which boarding or alighting is
 * affected, the original text with the trigger words in bold, and the way to hide the card.
 * Hiding ends only the emphasis; the notice stays in the timeline with its mark on the rail.
 */
@Composable fun JourneyNoticeCards(hits: List<NoticeHit>, modifier: Modifier = Modifier) {
    if (hits.isEmpty()) return
    val actions = LocalJourneyNotices.current
    Column(modifier.fillMaxWidth().testTag("journey-notice-cards"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        hits.forEach { hit ->
            Surface(Modifier.fillMaxWidth().testTag("journey-notice"), shape = dev.ambivalenton.openv.ui.theme.appCorner(18.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val affected = hit.targets.joinToString(" · ") { tr("%1\$s: %2\$s", hit.label(it), it.stopName) }
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NoticeExclamation(Modifier.size(24.dp))
                        Text(affected, Modifier.weight(1f).semantics { heading() }.testTag("journey-notice-target"), style = MaterialTheme.typography.titleSmall)
                    }
                    Text(noticeAnnotated(hit), Modifier.testTag("journey-notice-text"), style = MaterialTheme.typography.bodyMedium)
                    Text(tr("Erkannt an festen Begriffen. Ob die Fahrt möglich ist, entscheiden die Fahrplandaten."),
                        style = MaterialTheme.typography.bodySmall)
                    // Only this emphasis is hidden; the notice stays in the timeline (with its
                    // exclamation mark on the rail) and the mark in the bar stays.
                    actions.onDismiss?.let { dismiss ->
                        TextButton({ dismiss(hit) }, Modifier.heightIn(min = 48.dp).testTag("journey-notice-dismiss")) {
                            Text(tr("Hervorhebung ausblenden"))
                        }
                    }
                }
            }
        }
    }
}

/** The exclamation mark of a recognised notice, in the theme's accent on a light disc. */
@Composable fun NoticeExclamation(modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.tertiary; val disc = MaterialTheme.colorScheme.surface
    androidx.compose.foundation.Canvas(modifier) {
        val r = size.minDimension / 2
        drawCircle(disc, r)
        val w = r * .3f
        drawRoundRect(accent, androidx.compose.ui.geometry.Offset(center.x - w / 2, center.y - r * .62f),
            androidx.compose.ui.geometry.Size(w, r * .78f), androidx.compose.ui.geometry.CornerRadius(w / 2))
        drawCircle(accent, w * .62f, androidx.compose.ui.geometry.Offset(center.x, center.y + r * .48f))
    }
}
