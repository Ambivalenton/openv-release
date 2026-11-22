// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.ui.screens

import dev.ambivalenton.openv.ui.components.FittedText

import dev.ambivalenton.openv.localization.tr

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.*
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.OverviewSettings
import dev.ambivalenton.openv.ui.components.ContinuousSettingSlider
import dev.ambivalenton.openv.ui.components.StepBubbleSetting

@Composable
fun OverviewSettingsContent(
    settings: OverviewSettings,
    onChange: ((OverviewSettings) -> OverviewSettings) -> Unit,
) {
    OverviewSentenceSettings(settings,onChange)
    HorizontalDivider(Modifier.padding(vertical=12.dp))
    OverviewRoutingSettings(settings,onChange)
}

@Composable
internal fun OverviewSentenceSettings(
    settings: OverviewSettings,
    onChange: ((OverviewSettings) -> OverviewSettings) -> Unit,
) {
    dev.ambivalenton.openv.ui.components.OverviewPresentationSettings(settings,onChange)
    OverviewSentenceFields(settings,onChange)
}

@Composable internal fun OverviewSentenceFields(settings:OverviewSettings,onChange:((OverviewSettings)->OverviewSettings)->Unit){
    Text(tr("Zeigt das Wetter am Ziel. Dafür erhält Open-Meteo den Zielort. Ohne Wetterdaten bleibt die Verbindung sichtbar."),
        style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    @Composable
    fun Field(
        label: String,
        checked: Boolean,
        change: (OverviewSettings, Boolean) -> OverviewSettings,
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                checked,
                role = Role.Checkbox,
            ) { value ->
                onChange { change(it, value) }
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked, null)
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    }
    Field(tr("Abfahrtszeit"), settings.showDepartureTime) { s, v -> s.copy(showDepartureTime = v) }
    Field(tr("Ankunftszeit"), settings.showArrivalTime) { s, v -> s.copy(showArrivalTime = v) }
    Field(tr("Starthaltestelle"), settings.showOriginStop) { s, v -> s.copy(showOriginStop = v) }
    Field(tr("Name des Favoriten oder Ziels"), settings.showDestination) { s, v ->
        s.copy(showDestination = v)
    }
    Field(tr("Gesamtdauer"), settings.showDuration) { s, v -> s.copy(showDuration = v) }
    Field(tr("Wartezeit beim Umsteigen"), settings.showWaitTimes) { s, v -> s.copy(showWaitTimes = v) }
    Field(tr("Fußwege"), settings.showWalks) { s, v -> s.copy(showWalks = v) }
    Field(tr("Anzahl Umstiege"), settings.showTransfers) { s, v -> s.copy(showTransfers = v) }
    Field(tr("Verkehrsmittel"), settings.showTransport) { s, v ->
        s.copy(showTransport = v)
    }
    Field(tr("Liniennamen / Liniennummern"), settings.showLineNames) { s, v -> s.copy(showLineNames = v) }
    Field(tr("Gleis- / Bussteig-Angaben"), settings.showPlatforms) { s, v -> s.copy(showPlatforms = v) }
    Field(tr("Wetter bei der Ankunft"), settings.showWeather) { s, v -> s.copy(showWeather = v) }
}

@Composable
internal fun OverviewRoutingSettings(settings: OverviewSettings,onChange: ((OverviewSettings)->OverviewSettings)->Unit) {
    FittedText(tr("Welche Verbindung passt zu dir?"), style = MaterialTheme.typography.titleMedium)
    Text(routingPreferencesSummary(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OverviewBufferSetting(settings, onChange)
    RoutingPreferenceToggles(settings, onChange)
}

/** What the preferences do, in one place for both pages that show them. */
internal fun routingPreferencesSummary(): String =
    tr("Danach wägt die App Verbindungen ab: Übersicht und Widgets schlagen die passendste vor, und die Suche blendet umständliche Verbindungen aus, wenn es eine vergleichbare, deutlich bequemere gibt.")

/**
 * 2.4: the lead time before a departure (its own frame under "Verbindungssuche"). From
 * −15 to 30 minutes; the track is parted at zero with a dot there, and the thumb rests on zero
 * when it comes close.
 */
@Composable
internal fun OverviewBufferSetting(settings: OverviewSettings,onChange: ((OverviewSettings)->OverviewSettings)->Unit) {
    dev.ambivalenton.openv.ui.components.ZeroSplitSlider(tr("Zeit bis zur Abfahrt"), settings.bufferMinutes, BUFFER_MINUTES_RANGE, tr("Min.")) {
        value ->
        onChange { it.copy(bufferMinutes = value) }
    }
}

/**
 * The preferences as sliders with speech bubbles (as for the bar chart and the network
 * plan): time, transfers, walking, time to change and the leeway. Each slider names its stop in
 * the bubble and explains below what it does.
 */
@Composable
internal fun RoutingPreferenceToggles(settings: OverviewSettings,onChange: ((OverviewSettings)->OverviewSettings)->Unit) {
    val prefs = settings.routingPreferences
    fun set(next: OverviewRoutingPreferences) = onChange { it.copy(routingPreferences = next) }
    Column(Modifier.fillMaxWidth().testTag("routing-preferences"), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        val times = RoutePriority.slider
        StepBubbleSetting(tr("Abfahrt oder Ankunft"), times.map { it.title }, times.indexOf(prefs.effectivePriority).coerceAtLeast(0),
            { set(prefs.with(priority = times[it])) }, "pref-priority", { when (times[it]) {
                RoutePriority.BALANCED -> tr("Abfahrt und Ankunft zählen etwa gleich.")
                RoutePriority.ARRIVAL -> tr("Es zählt, wann du ankommst – nicht, wann du losfährst.")
                else -> tr("Lieber bald los. Eine spätere Fahrt schlägt die App nur vor, wenn sie spürbar besser passt.")
            } }, framed = false)
        val transfers = TransferPreference.entries
        StepBubbleSetting(tr("Umsteigen"), transfers.map { it.title }, prefs.effectiveTransfers.ordinal,
            { set(prefs.with(transfers = transfers[it])) }, "pref-transfers", { when (transfers[it]) {
                TransferPreference.FEWEST -> tr("Ein Umstieg weniger darf rund 20 Minuten kosten. Die Suche fragt zusätzlich gezielt nach Verbindungen mit wenig Umstiegen.")
                TransferPreference.FEWER -> tr("Ein Umstieg weniger darf rund 10 Minuten kosten. Die Suche fragt zusätzlich nach Verbindungen mit wenig Umstiegen.")
                TransferPreference.NORMAL -> tr("Ein Umstieg zählt wie etwa 5 Minuten Fahrt.")
                TransferPreference.ANY -> tr("Umstiege stören dich kaum. Verbindungen mit unnötig vielen Umstiegen blendet die App trotzdem aus.")
            } }, framed = false)
        val walking = WalkingPreference.entries
        StepBubbleSetting(tr("Fußwege"), walking.map { it.title }, prefs.effectiveWalking.ordinal,
            { set(prefs.with(walking = walking[it])) }, "pref-walking", { when (walking[it]) {
                WalkingPreference.MINIMAL -> tr("Immer der kürzeste Fußweg – auch wenn eine andere Verbindung früher ankäme. Von einer Adresse geht es nur zu den nächsten Haltestellen.")
                WalkingPreference.LESS -> tr("Eine Minute zu Fuß zählt wie zweieinhalb Minuten Fahrt. Die Suche fragt zusätzlich nach Verbindungen mit wenig Fußweg.")
                WalkingPreference.NORMAL -> tr("Eine Minute zu Fuß zählt wie anderthalb Minuten Fahrt.")
                WalkingPreference.RELAXED -> tr("Längere Fußwege sind dir recht. Einen unnötig langen Fußweg schlägt die App trotzdem nicht vor.")
            } }, framed = false)
        val buffers = TransferBuffer.entries
        StepBubbleSetting(tr("Zeit zum Umsteigen"), buffers.map { it.title }, prefs.buffer.ordinal,
            { set(prefs.with(buffer = buffers[it])) }, "pref-buffer", { when (buffers[it]) {
                TransferBuffer.TIGHT -> tr("Knappe Umstiege sind in Ordnung.")
                TransferBuffer.NORMAL -> tr("Ein knapper Umstieg zählt so viel, wie du beim Verpassen voraussichtlich verlieren würdest.")
                TransferBuffer.SAFE -> tr("Lieber etwas mehr Zeit zum Umsteigen – ein wackliger Anschluss zählt deutlich mehr.")
            } }, framed = false)
        val steps = (0..30 step 5).toList()
        Box(Modifier.fillMaxWidth().alpha(if (prefs.enabled) 1f else .55f)) {
            StepBubbleSetting(tr("Spielraum"), steps.map { if (it == 0) tr("Keiner") else tr("%1\$s Min.", it) },
                steps.indexOf((prefs.tolerance / 5) * 5).coerceAtLeast(0), { set(prefs.with(tolerance = steps[it])) }, "pref-tolerance",
                { index -> if (steps[index] == 0) tr("Die Übersicht schlägt immer die nächste passende Verbindung vor.")
                    else tr("Eine besser passende Verbindung darf bis zu %1\$s Min. später abfahren und ankommen als die nächste.", steps[index]) +
                        if (prefs.enabled) "" else " " + tr("Wirkt, sobald oben etwas anderes als „Bald losfahren“ und „Normal“ gewählt ist.") },
                framed = false)
        }
    }
}
