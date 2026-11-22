// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

internal fun TripSegment.isLongDistance(): Boolean = isLongDistance(lineType, lineName)

private val longDistanceLine =
    Regex("^(ICE|IC|EC|FLX|FLIXTRAIN|TGV|RJX?|NJ|EN)(?:\\s|\\d|$)", RegexOption.IGNORE_CASE)

internal fun isLongDistance(lineType: LineType, lineName: String): Boolean =
    lineType == LineType.ICE || longDistanceLine.containsMatchIn(lineName)
