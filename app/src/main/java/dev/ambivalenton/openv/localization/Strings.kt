// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.localization

import java.util.Locale

/** Pure text boundary, usable by domain code, widgets and Compose without an Android Context.
 * Catalog keys are literal German source templates, never user/provider text.
 * Android restarts the activity on a system/app-language configuration change.
 */
fun serviceLanguage(locale: Locale = Locale.getDefault()): String =
    if (locale.language == "de") "de" else "en"

fun tr(german: String, vararg arguments: Any?): String =
    translate(german, Locale.getDefault(), *arguments)

internal fun translate(german: String, locale: Locale, vararg arguments: Any?): String {
    val template = if (serviceLanguage(locale) == "de") german else englishStrings[german] ?: german
    return if (arguments.isEmpty()) template else String.format(locale, template, *arguments)
}
