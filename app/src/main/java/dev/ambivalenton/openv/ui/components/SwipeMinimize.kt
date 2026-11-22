// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

/** Compatibility for existing callers/tests. Live focus uses DirectModalSheet. */
fun shouldMinimizeFocus(distance:Float,velocity:Float,threshold:Float):Boolean =
    shouldCloseJourneySheet(distance,velocity,threshold,1_200f,0f)
