// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import java.security.MessageDigest

const val BOARD_JOURNEY_EXTRA="openv_board_journey"
fun Departure.journeyToken():String=MessageDigest.getInstance("SHA-256")
    .digest(id.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
