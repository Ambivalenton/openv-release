// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.theme.appCorner

/**
 * Without the navigation pill the pinned journey floats in its place: the
 * next step, its stop, its time and the progress in one compact card. A tap opens the companion,
 * the cross ends it (after the usual question). Times come from the same state as the companion.
 */
@Composable fun PinnedHoverCard(state: PinnedJourneyState, onOpen: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val session = state.session ?: return
    val now = rememberJourneyNow(); val t = MaterialTheme.typography; val scheme = MaterialTheme.colorScheme
    val focus = assistantDisplayFocus(state, now) ?: return
    val (nextPlace, railName) = pinnedStopLabel(focus, session.trip)
    Box(modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 10.dp, vertical = PillNavigationPadding)) {
        AppCard(onClick = onOpen, modifier = Modifier.fillMaxWidth().heightIn(min = PillNavigationBox).testTag("pinned-hover-card"),
            shape = appCorner(42.dp), colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerHigh),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = PillNavigationBox).padding(start = 22.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    PinnedTextSlot(focus.heading(), t.titleSmall.copy(fontWeight = FontWeight.SemiBold), tag = "hover-action", marquee = true)
                    PinnedTextSlot(nextPlace, t.bodyMedium, tag = "hover-next-place", marquee = true, fitRail = railName)
                    JourneyWiggleProgress(focus.progress, assistantWaveActive(session, now, state.status), session.id,
                        Modifier.fillMaxWidth().padding(top = 4.dp).semantics { contentDescription = tr("Reisefortschritt") },
                        urgent = journeyHasDelay(session) || session.missedBoarding != null)
                }
                Column(Modifier.width(76.dp).padding(start = 8.dp), horizontalAlignment = Alignment.End) {
                    PinnedTextSlot(clockTime(focus.nextTime), t.titleLarge.copy(fontWeight = FontWeight.Bold), color = scheme.primary, tag = "hover-time")
                    PinnedTextSlot(focus.delay.orEmpty(), t.labelSmall, color = scheme.error, tag = "hover-delay")
                }
                IconButton(onRemove, Modifier.size(44.dp).testTag("hover-remove")) { AppIcon(R.drawable.ic_close, tr("Reisebegleiter beenden")) }
            }
        }
    }
}
