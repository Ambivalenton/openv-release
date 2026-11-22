// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import dev.ambivalenton.openv.localization.tr

data class CompactPinnedNotification(val title: String,val text: String)

fun compactPinnedNotification(session:PinnedJourneySession,now:Long,presence:DevicePresence?=session.lastTrackedPresence,
    display:JourneyFocus?=null):CompactPinnedNotification {
    val focus=display?:assistantDisplayFocus(PinnedJourneyState(session=session,presence=presence),now)!!
    val place=pinnedStopLabel(focus,session.trip).first
    return CompactPinnedNotification(focus.line.ifBlank {session.favorite.label}+" · "+place,
        listOf(focus.heading(),tr("%1\$s %2\$s",focus.timeLabel,clockTime(focus.nextTime)),
            nextBoardingInstruction(session,focus),assistantStepDetail(focus),focus.delay.orEmpty())
            .filter {it.isNotBlank()}.joinToString(" · "))
}
