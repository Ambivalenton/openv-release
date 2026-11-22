// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.runtime.Immutable

@Immutable data class JourneyHandOff<T>(val busy:Boolean,val icon:Int,val label:String,val actionLabel:String,val run:(T)->Unit)
