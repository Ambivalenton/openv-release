// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import dev.ambivalenton.openv.data.model.*
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** At most this many stations get a local timetable. */
const val PERSONAL_TIMETABLE_PLACES = 24
/** A searched place that was not searched again for this long leaves the local timetable. */
const val PERSONAL_PLACE_RETENTION_MILLIS = 30L * 24 * 3600_000
/** Upper bound of the local timetable on disk; the least important places give way first. */
internal const val PERSONAL_TIMETABLE_MAX_BYTES = 8L * 1024 * 1024

/**
 * Build 129 · the stations of daily use, most important first: saved places, the departure board's
 * stations, the chosen start, then places searched in the last 30 days (more often, then more
 * recently first). Only these get a local timetable – never a whole region.
 */
fun personalTimetablePlaces(state: StoredState, now: Long): List<String> {
    val fixed = state.favorites.map { it.location } + state.boardStops +
        listOfNotNull(state.settings.board.selectedStop, state.departureBoard.stop, state.settings.manualOrigin)
    val searched = state.history.asSequence()
        .filter { now - it.searchedAt in 0..PERSONAL_PLACE_RETENTION_MILLIS }
        .sortedWith(compareByDescending<SearchHistory> { it.count }.thenByDescending { it.searchedAt })
        .flatMap { sequenceOf(it.query.origin, it.query.destination) + it.query.waypoints.asSequence() }
    return (fixed.asSequence() + searched).mapNotNull(::efaGlobalStopId).distinct()
        .take(PERSONAL_TIMETABLE_PLACES).toList()
}

/**
 * Read-only, licensed GTFS data (VRR-Sollfahrplan, CC BY). OpenService responses never enter it.
 *
 * Build 129: the app ships the regional timetable compressed, but keeps on the device only an
 * extract for the person's own stations ([prepare]): their departures and the direct services
 * between them. The full table is unpacked to the cache only while the extract is built and is
 * deleted right after; a station that is no longer used leaves with the next build.
 */
class RegionalTimetable(context: Context) {
    private val app = context.applicationContext
    private val source = CatalogAsset(app, "timetable/schedule.json", "timetable/schedule.catalog",
        "timetable-source", root = app.cacheDir)
    private val folder = File(app.noBackupFilesDir, "personal-timetable")
    private val extract = File(folder, "timetable.sqlite")
    private val lock = Mutex()

    /** The stations the local timetable covers now (global stop IDs, most important first). */
    suspend fun places(): List<String> = read(emptyList()) { db ->
        db.rawQuery("SELECT dhid FROM personal ORDER BY rank", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }
    }

    /**
     * Builds the extract for [wanted] (see [personalTimetablePlaces]) unless it already covers exactly
     * these stations of the shipped timetable edition. Cheap when nothing changed; otherwise about a
     * second of disk work, off the main thread. Errors leave the previous extract in place.
     */
    suspend fun prepare(wanted: List<String>) = withContext(Dispatchers.IO) {
        lock.withLock {
            // 1.2.0.8 kept the whole regional table (about 29 MB) unpacked; it is no longer read.
            File(app.noBackupFilesDir, "regional-timetable").takeIf { it.exists() }?.deleteRecursively()
            val places = wanted.distinct().take(PERSONAL_TIMETABLE_PLACES)
            val digest = try { source.digest() } catch (e: CancellationException) { throw e } catch (_: Exception) { return@withLock }
            if (places.isEmpty()) { extract.delete(); return@withLock }
            // The order changes with every search; only another set of stations needs a new extract.
            if (current(digest) == places.toSet()) return@withLock
            try {
                val full = source.file()
                var take = places.size
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val built = build(full, places.take(take), places, digest)
                    // Too large: the least important stations give way (saved places come first).
                    if (built.length() <= PERSONAL_TIMETABLE_MAX_BYTES || take <= 4) {
                        folder.mkdirs()
                        check(built.renameTo(extract) || (extract.delete() && built.renameTo(extract)))
                        break
                    }
                    built.delete(); take = take * 2 / 3
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* The live service still answers; the next change tries again. */ }
            finally { source.release() }
        }
    }

    /** The stations requested for the extract of this edition, or null without a usable extract. */
    private fun current(digest: String): Set<String>? = try {
        if (!extract.isFile) null
        else SQLiteDatabase.openDatabase(extract.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val edition = db.rawQuery("SELECT digest FROM source", null).use { if (it.moveToFirst()) it.getString(0) else null }
            if (edition != digest) null
            else db.rawQuery("SELECT dhid FROM request", null).use { c ->
                buildSet { while (c.moveToNext()) add(c.getString(0)) }
            }
        }
    } catch (_: Exception) { null }

    /**
     * Services that call at two of the stations keep all their stops (intermediate stops of a direct
     * journey); all others keep only the calls at the person's stations (enough for a departure board).
     */
    private fun build(full: File, places: List<String>, requested: List<String>, digest: String): File {
        folder.mkdirs()
        val target = File(folder, "timetable.tmp").apply { delete() }
        SQLiteDatabase.openOrCreateDatabase(target, null).use { db ->
            db.execSQL("ATTACH DATABASE ? AS src", arrayOf(full.path))
            db.beginTransaction()
            try {
                listOf(
                    "CREATE TABLE stop(id INTEGER PRIMARY KEY, dhid TEXT, name TEXT, platform TEXT)",
                    "CREATE TABLE pattern(id INTEGER PRIMARY KEY, line TEXT, product TEXT, direction TEXT, last INTEGER)",
                    "CREATE TABLE call(pattern INTEGER, seq INTEGER, stop INTEGER, arr INTEGER, dep INTEGER, pickup INTEGER, dropoff INTEGER, PRIMARY KEY(pattern,seq)) WITHOUT ROWID",
                    "CREATE TABLE run(id INTEGER PRIMARY KEY, pattern INTEGER, start INTEGER, service INTEGER)",
                    "CREATE TABLE service(id INTEGER PRIMARY KEY, days TEXT)",
                    "CREATE TABLE feed(first TEXT, last TEXT, version TEXT, max_seconds INTEGER)",
                    "CREATE TABLE personal(dhid TEXT PRIMARY KEY, rank INTEGER)",
                    "CREATE TABLE source(digest TEXT)",
                    "CREATE TABLE request(dhid TEXT PRIMARY KEY)",
                ).forEach(db::execSQL)
                requested.forEach { db.execSQL("INSERT INTO request VALUES(?)", arrayOf(it)) }
                places.forEachIndexed { rank, dhid -> db.execSQL("INSERT INTO personal VALUES(?,?)", arrayOf<Any>(dhid, rank)) }
                db.execSQL("INSERT INTO source VALUES(?)", arrayOf(digest))
                listOf(
                    "CREATE TEMP TABLE own AS SELECT c.* FROM src.call c WHERE c.stop IN (SELECT id FROM src.stop WHERE dhid IN (SELECT dhid FROM personal))",
                    "CREATE TEMP TABLE pair AS SELECT o.pattern FROM own o JOIN src.stop s ON s.id=o.stop GROUP BY o.pattern HAVING COUNT(DISTINCT s.dhid)>=2",
                    "INSERT INTO call SELECT * FROM own WHERE pattern NOT IN (SELECT pattern FROM pair)",
                    "INSERT INTO call SELECT * FROM src.call WHERE pattern IN (SELECT pattern FROM pair)",
                    "INSERT INTO pattern SELECT p.id,p.line,p.product,p.direction,(SELECT MAX(seq) FROM src.call WHERE pattern=p.id) FROM src.pattern p WHERE p.id IN (SELECT DISTINCT pattern FROM own)",
                    "INSERT INTO run SELECT * FROM src.run WHERE pattern IN (SELECT id FROM pattern)",
                    "INSERT INTO service SELECT * FROM src.service WHERE id IN (SELECT DISTINCT service FROM run)",
                    "INSERT INTO stop SELECT * FROM src.stop WHERE id IN (SELECT DISTINCT stop FROM call)",
                    "INSERT INTO feed SELECT * FROM src.feed",
                    "CREATE INDEX station ON stop(dhid)",
                    "CREATE INDEX boarding ON call(stop,pattern)",
                    "CREATE INDEX operating ON run(pattern,start)",
                    "DROP TABLE own", "DROP TABLE pair",
                ).forEach(db::execSQL)
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
            db.execSQL("DETACH DATABASE src")
        }
        return target
    }

    private suspend fun <T> read(fallback: T, action: suspend (SQLiteDatabase) -> T): T = withContext(Dispatchers.IO) {
        try {
            if (!extract.isFile) fallback
            else SQLiteDatabase.openDatabase(extract.path, null, SQLiteDatabase.OPEN_READONLY).use { action(it) }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { fallback } // Live routing also works when the extract is missing or broken.
    }

    /** Planned direct services between two of the person's stations. */
    suspend fun direct(query: JourneyQuery): List<Trip> {
        val origin = efaGlobalStopId(query.origin) ?: return emptyList()
        val target = efaGlobalStopId(query.destination) ?: return emptyList()
        if (origin == target || query.waypoints.isNotEmpty() || query.products.isEmpty()) return emptyList()
        return read(emptyList()) { db -> if (own(db, origin) && own(db, target)) direct(db, query, origin, target) else emptyList() }
    }

    /**
     * Planned departures at one of the person's stations from [from] on, without any real-time
     * information ("Nur Fahrplan"). Empty for every other station.
     */
    suspend fun departures(stop: Location, from: Long, limit: Int, products: Set<LineType>? = null): List<Departure> {
        val station = efaGlobalStopId(stop) ?: return emptyList()
        return read(emptyList()) { db -> if (own(db, station)) departures(db, stop, station, from, limit.coerceIn(1, 60), products) else emptyList() }
    }

    /**
     * Only the person's own stations are complete: a service through two of them keeps all its stops
     * (for a direct journey's stops), but a third station on that line misses its other services.
     */
    private fun own(db: SQLiteDatabase, station: String) =
        db.rawQuery("SELECT 1 FROM personal WHERE dhid=?", arrayOf(station)).use { it.moveToFirst() }

    private class Edition(val first: LocalDate, val last: LocalDate, val published: Long, val overnightDays: Long)

    private fun edition(db: SQLiteDatabase): Edition? =
        db.rawQuery("SELECT first,last,version,max_seconds FROM feed", null).use {
            if (!it.moveToFirst()) null
            else Edition(LocalDate.parse(it.getString(0)), LocalDate.parse(it.getString(1)),
                LocalDate.parse(it.getString(2), DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay(transitZone).toInstant().toEpochMilli(),
                it.getLong(3) / 86400 + 1)
        }

    /** GTFS times measure elapsed seconds from local noon minus 12 hours, including on DST days. */
    private fun zero(day: LocalDate) = day.atTime(12, 0).atZone(transitZone).toInstant().toEpochMilli() - 12 * 3600_000L

    private suspend fun departures(db: SQLiteDatabase, stop: Location, station: String, from: Long, limit: Int,
        products: Set<LineType>?): List<Departure> {
        val feed = edition(db) ?: return emptyList()
        val date = Instant.ofEpochMilli(from).atZone(transitZone).toLocalDate()
        // The terminus is no departure; nor is a stop where nobody may board.
        val sql = """
            SELECT r.id,r.start,c.dep,c.seq,p.line,p.product,p.direction,s.platform
            FROM stop s JOIN call c ON c.stop=s.id JOIN pattern p ON p.id=c.pattern
            JOIN run r ON r.pattern=p.id JOIN service v ON r.service=v.id
            WHERE s.dhid=? AND c.pickup=1 AND c.seq<p.last AND substr(v.days,?,1)='1'
            AND r.start BETWEEN CAST(? AS INTEGER)-c.dep AND CAST(? AS INTEGER)-c.dep
            ORDER BY r.start+c.dep, r.id LIMIT $limit
        """.trimIndent()
        val until = from + 6 * 3600_000L
        val rows = mutableListOf<Departure>()
        for (offset in -feed.overnightDays..1L) {
            currentCoroutineContext().ensureActive()
            val day = date.plusDays(offset)
            if (day.isBefore(feed.first) || day.isAfter(feed.last)) continue
            val zero = zero(day)
            val args = arrayOf(station, (day.toEpochDay() - feed.first.toEpochDay() + 1).toString(),
                Math.floorDiv(from - zero + 999, 1000L).toString(), Math.floorDiv(until - zero, 1000L).toString())
            db.rawQuery(sql, args).use { c ->
                while (c.moveToNext()) {
                    val type = runCatching { LineType.valueOf(c.getString(5)) }.getOrNull() ?: continue
                    if (products != null && type !in products) continue
                    val time = zero + (c.getLong(1) + c.getLong(2)) * 1000
                    rows += Departure("gtfs-${c.getInt(0)}-$zero-${c.getInt(3)}", stop, c.getString(4), type,
                        c.getString(6), time, time, DelayInfo(), c.getString(7).ifBlank { null })
                }
            }
        }
        return rows.sortedBy { it.plannedTime }.distinctBy { Triple(boardLine(it.lineName), it.plannedTime, it.destination) }.take(limit)
    }

    private data class Ride(val pattern: Int, val run: Int, val first: Int, val last: Int,
        val zero: Long, val dep: Long, val arr: Long, val line: String, val product: LineType, val direction: String,
        val departurePlatform: String?, val arrivalPlatform: String?)

    private suspend fun direct(db: SQLiteDatabase, query: JourneyQuery, origin: String, target: String): List<Trip> {
        val feed = edition(db) ?: return emptyList()
        val date = Instant.ofEpochMilli(query.time).atZone(transitZone).toLocalDate()
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
        for (offset in -feed.overnightDays..1L) {
            currentCoroutineContext().ensureActive()
            val day = date.plusDays(offset)
            if (day.isBefore(feed.first) || day.isAfter(feed.last)) continue
            val zero = zero(day)
            val args = listOf(origin,target) + products + listOf((day.toEpochDay()-feed.first.toEpochDay()+1).toString(),
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
            Trip(id,listOf(leg),"VRR GTFS",feed.published)
        }.filter { it.matchesTransportFilter(query.transportFilter) }.take(6).sortedBy { it.startTime }.toList()
    }
}
