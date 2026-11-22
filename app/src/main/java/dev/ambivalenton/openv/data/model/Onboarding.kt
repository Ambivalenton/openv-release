// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import kotlinx.serialization.Serializable

const val ONBOARDING_STEPS = 6

/** Saved separately from live preferences; completion commits all choices in one transaction. */
@Serializable
data class OnboardingDraft(
    val step: Int = 0,
    val settings: AppSettings = AppSettings(),
    val favorites: List<Favorite> = emptyList(),
    val offerMoreFavorites: Boolean = false,
)

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
