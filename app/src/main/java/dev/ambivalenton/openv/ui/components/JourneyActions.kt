// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.runtime.Composable
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.PinnedJourneyState
import dev.ambivalenton.openv.data.model.Trip
import dev.ambivalenton.openv.localization.tr

@Composable
fun rememberJourneyHandOff(): JourneyHandOff<Trip> = JourneyHandOff(
    false, R.drawable.ic_compact_view, tr("Kompaktansicht"), tr("Kompaktansicht öffnen"), rememberJourneyCompactView(),
)

@Composable
fun rememberLiveJourneyHandOff(state: PinnedJourneyState): JourneyHandOff<Unit> {
    val show = rememberLiveJourneyCompactView(state)
    return JourneyHandOff(false, R.drawable.ic_compact_view, tr("Kompaktansicht"), tr("Kompaktansicht öffnen")) { show() }
}
