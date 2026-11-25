// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.FailureKind
import dev.ambivalenton.openv.data.repository.TransitFailure
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.AppUiState
import kotlinx.coroutines.delay

/** H05: the next step a failure message offers. Every screen maps the same cause to the same step. */
enum class FailureStep { RETRY, RELOAD, CHOOSE_START, CHANGE_TIME, CHECK_PRODUCTS, COVERAGE, SWITCH_PROVIDER }

/** Where a failure is shown; it only changes the wording of the retry action. */
enum class FailureContext { PLACE_SEARCH, RESULTS, DEPARTURES }

/** Text and steps of one failure (pure, unit-tested). */
data class FailureCopy(val title: String, val body: String?, val primary: FailureStep?, val secondary: FailureStep? = null)

/**
 * H05/H06: one mapping from cause to message and step. [provider] is the short name of the active
 * service; [alternative] names another service whose region fits (only when a build offers several).
 */
fun failureCopy(failure: TransitFailure, context: FailureContext, now: Long, provider: String? = null, alternative: String? = null): FailureCopy {
    val retry = if (context == FailureContext.DEPARTURES) FailureStep.RELOAD else FailureStep.RETRY
    return when (failure.kind) {
        FailureKind.OFFLINE -> FailureCopy(tr("Keine Internetverbindung"),
            if (context == FailureContext.PLACE_SEARCH) tr("Gespeicherte Orte und Haltestellen aus dem Verzeichnis bleiben auswählbar.")
            else tr("Prüfe WLAN oder mobile Daten. Deine Eingaben bleiben erhalten."), retry)
        FailureKind.TIMEOUT -> FailureCopy(tr("Der Fahrplandienst antwortet nicht rechtzeitig"),
            tr("Die Verbindung ist gerade langsam. Deine Eingaben bleiben erhalten."), retry)
        FailureKind.RATE_LIMITED -> FailureCopy(tr("Der Fahrplandienst begrenzt gerade die Anfragen"),
            failure.waitSeconds(now)?.let { tr("Das ist vorübergehend. Bitte warte noch %1\$s Sekunden.", it) }
                ?: tr("Das ist vorübergehend. Du kannst es jetzt erneut versuchen."), retry)
        // Build 122: said plainly – the timetable is out of reach, not "no journey at this time".
        FailureKind.UNAVAILABLE -> FailureCopy(tr("Fahrplan gerade nicht erreichbar"),
            tr("Das liegt nicht an deinem Gerät. Versuche es gleich noch einmal."), FailureStep.RELOAD)
        FailureKind.INVALID_RESPONSE -> FailureCopy(tr("Die Antwort des Fahrplandienstes war unvollständig"),
            tr("Versuche es gleich noch einmal."), FailureStep.RELOAD)
        FailureKind.MISSING_START -> FailureCopy(tr("Wähle einen Start für diese Fahrt."), null, FailureStep.CHOOSE_START)
        FailureKind.NO_COVERAGE -> FailureCopy(
            provider?.let { tr("Haltestelle gefunden. Für diese Strecke fehlen Antworten von %1\$s.", it) }
                ?: tr("Haltestelle gefunden. Für diese Strecke fehlen Antworten des Fahrplandienstes."),
            tr("Fahrten hängen vom Fahrplandienst ab."), FailureStep.COVERAGE,
            FailureStep.SWITCH_PROVIDER.takeIf { alternative != null })
        FailureKind.NO_CONNECTION -> FailureCopy(tr("Für diese Zeit sind keine passenden Fahrten verfügbar"),
            tr("Fahrten hängen vom Fahrplandienst ab."), FailureStep.CHANGE_TIME, FailureStep.CHECK_PRODUCTS)
        FailureKind.OTHER -> FailureCopy(failure.detail?.takeIf { it.isNotBlank() } ?: tr("Etwas ist schiefgegangen."),
            null, retry)
    }
}

/** Label of a step; [alternative] names the service for [FailureStep.SWITCH_PROVIDER]. */
fun failureStepLabel(step: FailureStep, alternative: String? = null): String = when (step) {
    FailureStep.RETRY -> tr("Erneut suchen")
    FailureStep.RELOAD -> tr("Erneut laden")
    FailureStep.CHOOSE_START -> tr("Start wählen")
    FailureStep.CHANGE_TIME -> tr("Zeit ändern")
    FailureStep.CHECK_PRODUCTS -> tr("Verkehrsmittel prüfen")
    FailureStep.COVERAGE -> tr("Abdeckung ansehen")
    FailureStep.SWITCH_PROVIDER -> alternative?.let { tr("Zu %1\$s wechseln", it) } ?: tr("Fahrplandienst wechseln")
}

/** Retry steps wait for a pause the service asked for (HTTP 429/503 with Retry-After). */
private fun waits(step: FailureStep) = step == FailureStep.RETRY || step == FailureStep.RELOAD

/**
 * H05: a failure with its cause and the matching steps. Read as one polite live region; the retry
 * stays disabled while a requested pause runs and counts down without moving the layout.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FailurePanel(failure: TransitFailure, context: FailureContext, onStep: (FailureStep) -> Unit, modifier: Modifier = Modifier,
    provider: String? = null, alternative: String? = null,
    /** Build 122: the search that is kept for "Erneut suchen" (start → destination · time). */
    kept: String? = null) {
    var now by remember(failure) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(failure) {
        while (failure.waitSeconds(System.currentTimeMillis()) != null) { delay(1_000); now = System.currentTimeMillis() }
        now = System.currentTimeMillis()
    }
    val copy = failureCopy(failure, context, now, provider, alternative)
    val paused = failure.waitSeconds(now) != null
    Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = dev.ambivalenton.openv.ui.theme.appCorner(20.dp),
        modifier = modifier.fillMaxWidth().testTag("failure-${failure.kind.name.lowercase()}").semantics { liveRegion = LiveRegionMode.Polite }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(copy.title, style = MaterialTheme.typography.titleSmall)
            copy.body?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            kept?.let { Text(tr("Deine Suche bleibt erhalten: %1\$s", it), Modifier.testTag("failure-kept-query"),
                style = MaterialTheme.typography.bodySmall) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOfNotNull(copy.primary, copy.secondary).forEach { step ->
                    TextButton({ onStep(step) }, Modifier.heightIn(min = 48.dp).testTag("failure-step-${step.name.lowercase()}"),
                        enabled = !(paused && waits(step))) { Text(failureStepLabel(step, alternative)) }
                }
            }
        }
    }
}

/** K01/M15: one sentence about what the shown bars are based on (pure, unit-tested; also the spoken text). */
data class ResultsFreshness(val text: String, val saved: Boolean, val failed: Boolean, val refreshing: Boolean)

fun resultsFreshness(ui: AppUiState, minimal: Boolean = false): ResultsFreshness? {
    val trips = ui.allTrips
    if (trips.isEmpty() || ui.sameLocationMessage != null) return null
    val stale = trips.filter { it.id in ui.staleTripIds }
    val failed = ui.refreshFailure != null
    val refreshing = ui.trips.loading
    if (stale.isNotEmpty()) {
        // The oldest confirmed stand among the bars no answer has confirmed again.
        val head = tr("Gespeicherte Fahrten · Stand %1\$s Uhr.", clockTime(stale.minOf { it.fetchedAt }))
        val status = when {
            refreshing -> tr("Fahrten werden aktualisiert …")
            failed -> tr("Aktualisierung fehlgeschlagen.")
            stale.size < trips.size -> tr("%1\$s von %2\$s Fahrten bestätigt.", trips.size - stale.size, trips.size)
            else -> tr("Noch nicht bestätigt.")
        }
        return ResultsFreshness("$head $status", saved = true, failed = failed, refreshing = refreshing)
    }
    if (failed) return ResultsFreshness(tr("Stand %1\$s Uhr. Aktualisierung fehlgeschlagen.", clockTime(ui.trips.updatedAt.takeIf { it > 0 } ?: trips.maxOf { it.fetchedAt })),
        saved = false, failed = true, refreshing = refreshing)
    if (minimal) return null
    if (refreshing && ui.refreshAttemptAt != null) return ResultsFreshness(tr("Fahrten werden aktualisiert …"), false, false, true)
    val stand = ui.trips.updatedAt.takeIf { it > 0 } ?: trips.maxOf { it.fetchedAt }
    return ResultsFreshness(if (ui.refreshAttemptAt != null) tr("Aktualisiert um %1\$s Uhr", clockTime(stand)) else tr("Stand %1\$s Uhr", clockTime(stand)),
        saved = false, failed = false, refreshing = refreshing)
}

/**
 * K01: the stand of the shown bars, at the top of the results and first in the reading order. Saved
 * or failed states are emphasised; a live stand stays a quiet line. [onRetry] repeats the refresh.
 */
@Composable
fun ResultsFreshnessLine(ui: AppUiState, minimal: Boolean, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val freshness = resultsFreshness(ui, minimal) ?: return
    val failure = ui.refreshFailure
    val emphasis = freshness.saved || freshness.failed
    val scheme = MaterialTheme.colorScheme
    Surface(modifier.fillMaxWidth().testTag("results-freshness").semantics(mergeDescendants = true) {
            liveRegion = LiveRegionMode.Polite
        },
        color = if (emphasis) scheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent,
        contentColor = if (emphasis) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
        shape = dev.ambivalenton.openv.ui.theme.appCorner(16.dp)) {
        Column(Modifier.padding(horizontal = if (emphasis) 14.dp else 0.dp, vertical = if (emphasis) 10.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(freshness.text, style = if (emphasis) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.labelMedium,
                modifier = Modifier.testTag("results-freshness-text"))
            if (failure != null && !freshness.refreshing) {
                val copy = failureCopy(failure, FailureContext.RESULTS, System.currentTimeMillis())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(copy.title, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onRetry, Modifier.heightIn(min = 48.dp).testTag("results-freshness-retry"),
                        enabled = failure.waitSeconds(System.currentTimeMillis()) == null) { Text(tr("Erneut laden")) }
                }
            }
        }
    }
}
