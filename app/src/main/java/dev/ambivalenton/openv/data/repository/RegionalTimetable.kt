// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import dev.ambivalenton.openv.data.model.*
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Read-only, licensed GTFS data. OpenService responses never enter this database. */
class RegionalTimetable(context: Context) {
    private val asset = CatalogAsset(context.applicationContext, "timetable/schedule.json",
        "timetable/schedule.catalog", "regional-timetable")

    suspend fun direct(query: JourneyQuery): List<Trip> {
        val origin = efaGlobalStopId(query.origin) ?: return emptyList()
        val target = efaGlobalStopId(query.destination) ?: return emptyList()
        if (origin == target || query.waypoints.isNotEmpty() || query.products.isEmpty()) return emptyList()
        return withContext(Dispatchers.IO) {
            try {
                val file = asset.file()
                SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                    direct(db, query, origin, target)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { emptyList() } // Live routing also works when disk space is exhausted.
        }
    }

    private data class Ride(val pattern: Int, val run: Int, val first: Int, val last: Int,
        val zero: Long, val dep: Long, val arr: Long, val line: String, val product: LineType, val direction: String,
        val departurePlatform: String?, val arrivalPlatform: String?)

    private suspend fun direct(db: SQLiteDatabase, query: JourneyQuery, origin: String, target: String): List<Trip> {
        val date = Instant.ofEpochMilli(query.time).atZone(transitZone).toLocalDate()
        val feed = db.rawQuery("SELECT first,last,version,max_seconds FROM feed", null)
        val first: LocalDate
        val last: LocalDate
        val edition: Long
        val overnightDays: Long
        feed.use {
            if (!it.moveToFirst()) return emptyList()
            first = LocalDate.parse(it.getString(0))
            last = LocalDate.parse(it.getString(1))
            edition = LocalDate.parse(it.getString(2), DateTimeFormatter.BASIC_ISO_DATE)
                .atStartOfDay(transitZone).toInstant().toEpochMilli()
            overnightDays = it.getLong(3) / 86400 + 1
        }
        val departure = query.timeMode == TimeMode.DEPARTURE
        val event = if (departure) "a.dep" else "b.arr"
        val order = if (departure) "ASC" else "DESC"
        val products = query.products.map { it.name }
        val placeholders = products.joinToString(",") { "?" }
        val sql = """
            SELECT p.id,r.id,a.seq,b.seq,r.start,a.dep,b.arr,p.line,p.product,p.direction,sa.platform,sb.platform
            FROM stop sa JOIN call a ON a.stop=sa.id JOIN call b ON a.pattern=b.pattern AND a.seq<b.seq
            JOIN stop sb ON b.stop=sb.id JOIN pattern p ON p.id=a.pattern
            JOIN run r ON r.pattern=p.id JOIN service s ON r.service=s.id
            WHERE sa.dhid=? AND sb.dhid=? AND a.pickup=1 AND b.dropoff=1 AND p.product IN ($placeholders)
            AND substr(s.days,?,1)='1' AND r.start BETWEEN CAST(? AS INTEGER)-$event AND CAST(? AS INTEGER)-$event
            ORDER BY r.start+$event $order, r.id LIMIT 32
        """.trimIndent()
        val window = 3 * 3600_000L
        val from = if (departure) query.time else query.time-window
        val until = if (departure) query.time+window else query.time
        val rides = mutableListOf<Ride>()
        for (offset in -overnightDays..1L) {
            currentCoroutineContext().ensureActive()
            val day = date.plusDays(offset)
            if (day.isBefore(first) || day.isAfter(last)) continue
            // GTFS times measure elapsed seconds from local noon minus 12 hours, including on DST days.
            val zero = day.atTime(12, 0).atZone(transitZone).toInstant().toEpochMilli() - 12*3600_000L
            val args = listOf(origin,target) + products + listOf((day.toEpochDay()-first.toEpochDay()+1).toString(),
                Math.floorDiv(from-zero+999,1000L).toString(),Math.floorDiv(until-zero,1000L).toString())
            db.rawQuery(sql,args.toTypedArray()).use { c ->
                while (c.moveToNext()) {
                    val start = zero + c.getLong(4)*1000
                    rides += Ride(c.getInt(0),c.getInt(1),c.getInt(2),c.getInt(3),start,
                        start+c.getLong(5)*1000,start+c.getLong(6)*1000,c.getString(7),
                        LineType.valueOf(c.getString(8)),c.getString(9),c.getString(10).ifBlank { null },c.getString(11).ifBlank { null })
                }
            }
        }
        val ordered = if (departure) rides.sortedBy { it.dep } else rides.sortedByDescending { it.arr }
        return ordered.distinctBy { Triple(it.line,it.dep,it.arr) }.asSequence().map { ride ->
            val id = "gtfs-${ride.run}-${ride.zero}-${ride.first}-${ride.last}"
            val stops = db.rawQuery("""SELECT s.name,s.dhid,s.platform,c.arr,c.dep FROM call c
                JOIN stop s ON s.id=c.stop WHERE c.pattern=? AND c.seq BETWEEN ? AND ? ORDER BY c.seq""",
                arrayOf(ride.pattern.toString(),ride.first.toString(),ride.last.toString())).use { c ->
                    buildList {
                        while (c.moveToNext()) {
                            val place = Location(c.getString(0),c.getString(1),provider=StaticStopCatalog.PROVIDER,
                                kind=LocationKind.STOP,dhid=c.getString(1))
                            val arr = ride.zero+c.getLong(3)*1000
                            val dep = ride.zero+c.getLong(4)*1000
                            add(Stopover(place,arr,dep,c.getString(2).ifBlank { null },plannedArrivalTime=arr,plannedDepartureTime=dep))
                        }
                    }
                }
            val leg = TripSegment(id,query.origin,query.destination,ride.dep,ride.arr,ride.product,ride.line,
                direction=ride.direction,stops=stops,departurePlatform=ride.departurePlatform,arrivalPlatform=ride.arrivalPlatform,
                departureSource=TimeSource.PLANNED,arrivalSource=TimeSource.PLANNED)
            Trip(id,listOf(leg),"VRR GTFS",edition)
        }.filter { it.matchesTransportFilter(query.transportFilter) }.take(6).sortedBy { it.startTime }.toList()
    }
}
