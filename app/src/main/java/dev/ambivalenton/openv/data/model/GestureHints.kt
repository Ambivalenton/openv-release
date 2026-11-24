// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import kotlinx.serialization.Serializable

/**
 * Build 115 · the wordless first-use hints: when they started, how long the app has been in use since
 * (foreground time) and which gestures have been used already (each hint ends for good after that).
 */
@Serializable data class GestureHintProgress(val startedAt: Long? = null, val usedMillis: Long = 0, val learned: Set<String> = emptySet())

/** Hints show during the first three hours of use, and never longer than two weeks after they started. */
const val GESTURE_HINT_USE_MS = 3 * 60 * 60_000L
const val GESTURE_HINT_DAYS_MS = 14 * 24 * 60 * 60_000L

/** True while hints may still show at [now], counting [session] milliseconds not yet stored. */
fun GestureHintProgress.active(now: Long, session: Long = 0L): Boolean =
    usedMillis + session < GESTURE_HINT_USE_MS && (startedAt == null || now - startedAt < GESTURE_HINT_DAYS_MS)

/** Adds [millis] of foreground use (bounded, so a wrong clock never ends the hints at once). */
fun GestureHintProgress.used(millis: Long, now: Long) =
    copy(startedAt = startedAt ?: now, usedMillis = usedMillis + millis.coerceIn(0L, 6 * 60 * 60_000L))

/** Keys of the hints; [GestureHintProgress.learned] stores them. */
object GestureHintKeys {
    const val SETTINGS = "open-settings"
    const val PLAN_EDIT = "plan-edit"
    const val PLAN_HOLD = "plan-hold"
    const val PLAN_MOVE = "plan-move"
    const val PLAN_CONNECT = "plan-connect"
    const val PLAN_TAP = "plan-tap"
    const val CHART_HOLD = "chart-hold"
    const val PAGES = "pages-swipe"
    const val BOARD_DESIGN = "board-design"
    const val SEARCH_TIME = "search-time"
}
