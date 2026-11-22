// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import kotlinx.serialization.Serializable

@Serializable enum class AppContrast { STANDARD, HIGH }

@Serializable data class AppReadability(
    val textScale: Float = 1f,
    val cornerScale: Float = 1f,
    val contrast: AppContrast = AppContrast.STANDARD,
)

fun AppReadability.normalized() = copy(
    textScale = if (textScale.isFinite()) textScale.coerceIn(.85f, 1.3f) else 1f,
    cornerScale = if (cornerScale.isFinite()) cornerScale.coerceIn(.25f, 1.75f) else 1f,
)
