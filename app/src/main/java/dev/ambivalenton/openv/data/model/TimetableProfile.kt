// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import kotlin.coroutines.CoroutineContext
import dev.ambivalenton.openv.data.repository.TransitRequestBudget

@Suppress("UNUSED_PARAMETER")
internal object TimetableProfile {
    val endpoint get() = TimetableServices.active.endpoint
    val name get() = TimetableServices.active.name
    val pagingRequests get() = TimetableServices.active.pagingRequests
    const val sourceNotice = true
    /** M11: whether this edition lets the person choose the timetable service (settings search shows it only then). */
    const val serviceChoice = false

    fun identity(settings: AppSettings) = "${TimetableServices.active.engine.name}\u0000\u0000${settings.efaEndpoint}"

    fun normalize(settings: AppSettings) = AppPublication.settings(settings)

    fun withTimetable(target: AppSettings, source: AppSettings) =
        target.copy(efaEndpoint = source.efaEndpoint)

    fun safeExtras(settings: AppSettings, isHttps: (String) -> Boolean) = settings

    fun requestTime(query: JourneyQuery, settings: AppSettings) = query.minuteDeparture()

    fun requestContext(limit: Int): CoroutineContext = TransitRequestBudget(limit)

    /** H06: the service name used in short coverage messages. */
    fun shortName(engine: Engine): String? = TimetableServices.of(engine).shortName

    /** H06: the app has one timetable service per build (see TimetableService); no switch is offered. */
    fun alternativeEngine(origin: Location?, destination: Location?, current: Engine): Engine? = null

    fun coverage(engine: Engine) = TimetableServices.of(engine).coverage
}
