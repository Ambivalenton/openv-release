// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

/** A gallery contains the same endpoint/filter query only. Never mix different pair routes. */
fun boardJourneyGallery(rows:List<Departure>,query:JourneyQuery):List<Trip> = rows.filter {
    it.journeyQuery?.copy(time=query.time)==query && it.journey!=null
}.mapNotNull {it.journey}.distinctBy {it.id}.sortedBy {it.startTime}
