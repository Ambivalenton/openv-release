// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import dev.ambivalenton.openv.data.model.Location
import dev.ambivalenton.openv.data.model.LocationKind
import dev.ambivalenton.openv.localization.tr
import java.io.File
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/** An offline stop directory, deliberately independent of provider IDs and live timetables. */
class StaticStopCatalog internal constructor(private val databaseFile: suspend () -> File?) {
    constructor(context: Context) : this(CatalogAsset(context.applicationContext)::file)

    @Volatile private var knownCities:List<String>?=null
    suspend fun search(typed:String,bias:Location?=null):List<Location> = safely {db->
        // Build 122: ranked by what was meant ("central station" → "hauptbahnhof"), so such hits are not dropped.
        val query=stationWords(placeWords(typed)).joinToString(" ").ifBlank {typed}
        val input=searchText(query);val match=catalogMatch(typed)?:return@safely emptyList()
        suspend fun lookup(expression:String,limit:Int=64,cityFocus:String=input):List<Location> {
            val args=mutableListOf(expression,"$cityFocus%",input,"$input%",input)
            val distance=if(bias?.hasCoordinates==true){args+=listOf(bias.latitude.toString(),bias.longitude.toString());", abs(s.lat-CAST(? AS REAL))+abs(s.lon-CAST(? AS REAL))*0.7"}else ""
            return db.rawQuery("""SELECT s.dhid,s.name,s.city,s.lat,s.lon,stop_search.terms FROM stop_search JOIN stops s ON s.rowid=stop_search.docid
                WHERE stop_search MATCH ? AND s.retired=0
                ORDER BY CASE WHEN s.city_key LIKE ? THEN CASE
                    WHEN (' '||s.name_key||' ') LIKE '% hbf %' OR (' '||s.name_key||' ') LIKE '% hauptbahnhof %' THEN 0
                    WHEN (' '||s.name_key||' ') LIKE '% bf %' OR (' '||s.name_key||' ') LIKE '% bahnhof %' OR (' '||s.name_key||' ') LIKE '% bhf %' OR (' '||s.name_key||' ') LIKE '% stadtbahnhof %' THEN 1
                    WHEN (' '||s.name_key||' ') LIKE '% zob %' OR (' '||s.name_key||' ') LIKE '% busbahnhof %' THEN 2 ELSE 3 END ELSE 3 END,
                    CASE WHEN s.name_key=? THEN 0 WHEN s.name_key LIKE ? THEN 1 ELSE 2 END,
                    CASE WHEN s.city_key=? THEN 0 ELSE 1 END $distance,length(s.name),s.dhid LIMIT $limit""",args.toTypedArray()).use {c->buildList {
                while(c.moveToNext()){currentCoroutineContext().ensureActive();val id=c.getString(0);val name=c.getString(1);val city=c.getString(2)
                    add(Location(if(searchText(name).startsWith(searchText(city)))name else "$city, $name",id,c.getDouble(3),c.getDouble(4),PROVIDER,LocationKind.STOP,tr("Lokales Haltestellenverzeichnis · DELFI"),city=city,dhid=id,searchTerms=c.getString(5)))
                }
            }}
        }
        fun ranked(rows:List<Location>)=rankSearchedPlaces(query,mergePlaceRows(rows),directory=rows,bias=bias).take(12)
        val direct=ranked(lookup(match));if(direct.isNotEmpty())return@safely direct
        val tokens=placeWords(query);if(tokens.any {it.any(Char::isDigit)}||tokens.size>5)return@safely emptyList()
        val candidates=mutableListOf<Location>()
        for(index in tokens.indices.filter {tokens[it].length>=5}.sortedByDescending {tokens[it].length}.take(2)) {
            catalogMatch(tokens.mapIndexed {i,t->if(i==index)t.take(4)else t}.joinToString(" "))?.let {candidates+=lookup(it,96)}
        }
        val relaxed=ranked(candidates);if(relaxed.isNotEmpty())return@safely relaxed
        if(input.length in 4..40&&tokens.size<=3) {
            val cities=knownCities?:db.rawQuery("SELECT DISTINCT city_key FROM stops WHERE retired=0 AND city_key<>''",null).use {c->buildList {while(c.moveToNext()){currentCoroutineContext().ensureActive();add(c.getString(0))}}}.also {knownCities=it}
            val maximum=if(input.length>=8)2 else 1
            val corrected=cities.mapNotNull {city->if(kotlin.math.abs(city.length-input.length)>maximum)null else {
                val d=placeEditDistance(input,city,maximum);if(d<=maximum&&(d<=1||city.take(2)==input.take(2)))city to d else null
            }}.sortedWith(compareBy<Pair<String,Int>>{it.second}.thenBy{it.first.length}).take(3)
            for((city,_)in corrected)catalogMatch(city)?.let {candidates+=lookup(it,96,city)}
        }
        ranked(candidates)
    }

    /** Build 116 · stops within about [radiusMeters] of a point (bounding box), for stop signs on maps. */
    suspend fun nearby(latitude: Double, longitude: Double, radiusMeters: Int = 300, limit: Int = 30): List<Location> = safely { db ->
        val dLat = radiusMeters / 111_195.0
        val dLon = dLat / kotlin.math.cos(Math.toRadians(latitude)).coerceAtLeast(.2)
        db.rawQuery("SELECT dhid,name,city,lat,lon FROM stops WHERE retired=0 AND lat BETWEEN ? AND ? AND lon BETWEEN ? AND ? LIMIT ?",
            arrayOf((latitude - dLat).toString(), (latitude + dLat).toString(), (longitude - dLon).toString(), (longitude + dLon).toString(), limit.toString())).use { c ->
            buildList { while (c.moveToNext()) add(Location(c.getString(1), c.getString(0), c.getDouble(3), c.getDouble(4), PROVIDER, LocationKind.STOP, city = c.getString(2))) }
        }
    }

    /** Only proven root/alias IDs may add a municipality; never infer one from a numeric API ID. */
    suspend fun enrich(rows: List<Location>): List<Location> {
        if (rows.isEmpty()) return rows
        val enriched=safely { db ->
            rows.map { place ->
                currentCoroutineContext().ensureActive()
                if(place.kind!=LocationKind.STOP || place.id==null || place.provider!="VRR") place
                else db.rawQuery("""
                    SELECT dhid,city FROM stops WHERE dhid=?
                    UNION ALL SELECT s.dhid,s.city FROM aliases a JOIN stops s ON s.dhid=a.dhid
                    WHERE a.alias=? LIMIT 1
                """.trimIndent(), arrayOf(place.id,place.id)).use { c ->
                    if(c.moveToFirst()) place.copy(city=place.city?.takeIf { it.isNotBlank() } ?: c.getString(1), dhid=c.getString(0))
                    else place
                }
            }
        }
        return enriched.ifEmpty { rows }
    }

    private suspend fun safely(operation: suspend (SQLiteDatabase) -> List<Location>): List<Location> = withContext(Dispatchers.IO) {
        try {
            val file=databaseFile() ?: return@withContext emptyList()
            currentCoroutineContext().ensureActive()
            SQLiteDatabase.openDatabase(file.path,null,SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { db ->
                if(db.version!=1 || !db.rawQuery("PRAGMA application_id",null).use { it.moveToFirst() && it.getInt(0)==0x4F565354 })
                    return@use emptyList()
                operation(db)
            }
        } catch(e:CancellationException) { throw e }
        catch(e:Exception) { emptyList() } // Optional local data must never break a working online search.
    }

    companion object { const val PROVIDER="DELFI_STATIC" }
}

internal fun catalogMatch(query: String): String? {
    if(query.trim().length !in 2..160) return null
    val terms=stationWords(placeWords(query))
    if(terms.isEmpty() || terms.size>8) return null
    return terms.joinToString(" ") {when(it){"hauptbahnhof"->"(hauptbahnhof* OR hbf* OR hauptbf*)";"bahnhof"->"(bahnhof* OR bf* OR bhf*)";
        "flughafen"->"(flughafen* OR airport*)";else->umlautAlternatives(it)}}
}

/**
 * Build 122 · how travellers write a station: "central station" and "main station" mean the main station,
 * "station" a station, "airport" the airport; a typo in "Hauptbahnhof" ("Hauptbanhof", "Hauptbahnhf") or
 * "Bahnhof" still counts. Only whole words are replaced; everything else stays as typed.
 */
internal fun stationWords(words:List<String>):List<String> {
    val out=mutableListOf<String>();var i=0
    while(i<words.size) {
        val w=words[i];val next=words.getOrNull(i+1)
        if((w=="central"||w=="main") && (next=="station"||next=="stn")) {out+="hauptbahnhof";i+=2;continue}
        out+=when {
            w=="station"||w=="stn"->"bahnhof"
            w=="airport"->"flughafen"
            w.length in 9..14 && w!="hauptbahnhof" && placeEditDistance(w,"hauptbahnhof",2)<=2->"hauptbahnhof"
            w.length in 6..8 && w!="bahnhof" && placeEditDistance(w,"bahnhof",1)<=1->"bahnhof"
            else->w
        }
        i++
    }
    return out
}

/** "ue", "oe" and "ae" written for an umlaut (Duesseldorf, Koeln): the folded spelling is searched as well. */
internal fun umlautAlternatives(term:String):String {
    val folded=umlautFolded(term)
    return if(folded==term || folded.length<2) "$term*" else "($term* OR $folded*)"
}

/** Lazy, cancellable extraction on IO; verified by the generated manifest, then opened read-only. */
internal class CatalogAsset(private val context:Context,
    private val manifest:String="stops/catalog.json", private val asset:String="stops/stops.catalog",
    private val folder:String="stop-catalog", private val root:File=context.noBackupFilesDir) {
    private val lock=Mutex()
    private var installed:File?=null
    suspend fun file():File = lock.withLock {
        installed?.takeIf { it.isFile }?.let { return@withLock it }
        val metadata=context.assets.open(manifest).bufferedReader().use { JSONObject(it.readText()) }
        val digest=metadata.getString("database_sha256")
        require(digest.matches(Regex("[0-9a-f]{64}")))
        val directory=File(root,folder).apply { mkdirs() }
        val target=File(directory,"stops-${digest.take(20)}.sqlite")
        // The file is only named into place after a successful checksum; interrupted copies stay temporary.
        if(!target.isFile || target.length()!=metadata.getLong("database_bytes")) {
            val temporary=File(directory,"copy.tmp")
            try {
                val hash=MessageDigest.getInstance("SHA-256")
                GZIPInputStream(context.assets.open(asset)).use { source ->
                    temporary.outputStream().use { sink ->
                        val buffer=ByteArray(64*1024)
                        while(true) {
                            currentCoroutineContext().ensureActive()
                            val count=source.read(buffer);if(count<0) break
                            hash.update(buffer,0,count);sink.write(buffer,0,count)
                        }
                    }
                }
                val actual=hash.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
                require(actual==digest && temporary.length()==metadata.getLong("database_bytes"))
                check(temporary.renameTo(target))
            } finally { temporary.delete() }
        }
        directory.listFiles()?.filter { it!=target && it.extension=="sqlite" }?.forEach { it.delete() }
        installed=target;target
    }
    /** The checksum of the shipped table, read from the manifest without unpacking anything. */
    fun digest():String=context.assets.open(manifest).bufferedReader().use { JSONObject(it.readText()) }.getString("database_sha256")
    /** Build 129: removes the unpacked copy; a later [file] unpacks it again. */
    suspend fun release()=lock.withLock { installed=null; File(root,folder).deleteRecursively(); Unit }
}
