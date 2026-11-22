// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

/** Contact and legal links of the published app. */
object AppPublication {
    const val supportEmail = "oepenvapp@gmail.com"
    const val privacyUrl = "https://ambivalenton.github.io/openv-release/privacy/"
    const val sourceCodeUrl = "https://github.com/Ambivalenton/openv-release"

    /** Restore the timetable service of this build after loading settings or a backup. */
    fun settings(value: AppSettings): AppSettings = value.copy(
        engine = TimetableServices.active.engine,
        efaEndpoint = TimetableServices.active.endpoint,
    )
}
