// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv

import dev.ambivalenton.openv.data.model.*

// Test fixtures are never packaged in the application. Fresh installs have zero favorites.
fun initialFavorites() =
    listOf(
        Favorite(
            "dortmund",
            "Dortmund Hbf",
            Location("Dortmund Hbf", "8000080", 51.5179, 7.4593, "DB"),
            FavoriteIcon.WORK,
            HighlightRule(),
        ),
        Favorite(
            "bochum",
            "Bochum Hbf",
            Location("Bochum Hbf", "8000041", 51.4785, 7.2232, "DB"),
            FavoriteIcon.SCHOOL,
        ),
        Favorite(
            "essen",
            "Essen Hbf",
            Location("Essen Hbf", "8000098", 51.4514, 7.0138, "DB"),
            FavoriteIcon.HEART,
            HighlightRule(fromMinute = 900, untilMinute = 1320),
        ),
    )
