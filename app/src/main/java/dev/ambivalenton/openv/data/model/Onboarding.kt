// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import kotlinx.serialization.Serializable

/**
 * Build 121: the stages of the setup. The start place ([LOCATION]) only appears while the app may not use
 * the location: then a place chosen here is where every search starts, so the app works from the first
 * minute without any permission. [MODES] comes before [DESIGN] – the quiet mode brings its own design.
 */
@Serializable
enum class OnboardingStage(val titleKey: String) {
    PERMISSIONS("Freigaben"), LOCATION("Dein Startpunkt"), FAVORITE("Dein erster Favorit"), MODES("Deine Modi"), DESIGN("Dein Design")
}

/** The stages shown: without location access, the start place follows the permissions. */
fun onboardingStages(locationGranted: Boolean): List<OnboardingStage> =
    OnboardingStage.entries.filter { it != OnboardingStage.LOCATION || !locationGranted }

/** Saved separately from live preferences; completion commits all choices in one transaction. */
@Serializable
data class OnboardingDraft(
    /** Before build 121 the only position (0 permissions, 1 favourite, 2 design); now the index among the shown stages. */
    val step: Int = 0,
    val settings: AppSettings = AppSettings(),
    val favorites: List<Favorite> = emptyList(),
    val offerMoreFavorites: Boolean = false,
    /** Build 121: the stage itself – stable when the start place appears or disappears. */
    val stage: OnboardingStage? = null,
) {
    /** The stage this draft stands on (a draft from before build 121 knows only its index). */
    val currentStage: OnboardingStage get() = stage ?: when (step) {
        0 -> OnboardingStage.PERMISSIONS
        1 -> OnboardingStage.FAVORITE
        else -> OnboardingStage.DESIGN
    }

    /** The stage shown among [stages]: a stage that is gone (start place after granting) gives way to the next one. */
    fun shownStage(stages: List<OnboardingStage>): OnboardingStage =
        currentStage.takeIf { it in stages } ?: stages.firstOrNull { it.ordinal > currentStage.ordinal } ?: stages.last()

    /** [by] stages further (or back) among [stages]. */
    fun moved(stages: List<OnboardingStage>, by: Int): OnboardingDraft {
        val next = stages[(stages.indexOf(shownStage(stages)) + by).coerceIn(0, stages.lastIndex)]
        return copy(stage = next, step = stages.indexOf(next))
    }
}

/** Build 121: the start place chosen in the setup: searches start there while the location is not used. */
fun AppSettings.withSetupStart(place: Location?, now: Long = System.currentTimeMillis()): AppSettings =
    if (place == null) copy(originMode = OriginMode.AUTOMATIC, manualOrigin = null)
    else copy(originMode = OriginMode.MANUAL, manualOrigin = place, manualOriginChangedAt = now)

/** The start place chosen in the setup, if any. */
val AppSettings.setupStart: Location? get() = manualOrigin?.takeIf { originMode == OriginMode.MANUAL }

fun needsFirstLaunchSetup(state: StoredState) = state.onboardingVersion < 1

enum class SentencePreset { COMPACT, EVERYDAY, DETAILED }

/** Presets change wording fields only. Routing, weather consent and quotes are retained. */
fun OverviewSettings.withSentencePreset(preset: SentencePreset): OverviewSettings = copy(
    showDepartureTime = true,
    showArrivalTime = preset != SentencePreset.COMPACT,
    showOriginStop = true,
    showDestination = true,
    showDuration = preset == SentencePreset.DETAILED,
    showWaitTimes = preset == SentencePreset.DETAILED,
    showWalks = preset == SentencePreset.DETAILED,
    showTransfers = preset == SentencePreset.DETAILED,
    showTransport = preset == SentencePreset.DETAILED,
    showLineNames = preset == SentencePreset.DETAILED,
    showPlatforms = preset == SentencePreset.DETAILED,
)

fun OverviewSettings.sentencePreset(): SentencePreset? = SentencePreset.entries.firstOrNull {
    withSentencePreset(it) == this
}

/** Explicitly selected changes are applied; a denied OS permission is never stored as a grant. */
internal fun StoredState.finishFirstLaunchSetup(): StoredState {
    if (!needsFirstLaunchSetup(this)) return this
    val draft = onboardingDraft ?: OnboardingDraft(settings = settings)
    require(draft.favorites.size + favorites.size <= 40)
    require(draft.favorites.all { it.label.isNotBlank() && it.label.length <= 80 && it.location.routable })
    require((favorites + draft.favorites).map { it.id }.distinct().size == favorites.size + draft.favorites.size)
    return copy(settings = draft.settings, favorites = favorites + draft.favorites,
        onboardingVersion = 1, onboardingDraft = null)
}
