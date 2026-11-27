// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dev.ambivalenton.openv.data.model.*
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Personal lookup index. Only route keys, the public timetable fingerprint and a boarding
 * stop reference are stored here. Live journey payloads stay in the short-lived view cache. */
class RegularRouteMemory(context: Context) {
    private val helper = object : SQLiteOpenHelper(context.applicationContext,
        File(context.cacheDir,"regular-routes-v1.db").path,null,1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("""CREATE TABLE route (key TEXT PRIMARY KEY, pattern TEXT NOT NULL,
                board TEXT NOT NULL, day INTEGER NOT NULL, visits INTEGER NOT NULL, checked INTEGER NOT NULL)""")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    private fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        .joinToString("") { "%02x".format(Locale.ROOT,it) }

    private fun key(query: JourneyQuery, settings: AppSettings): String? {
        val a=efaGlobalStopId(query.origin) ?: return null
        val b=efaGlobalStopId(query.destination) ?: return null
        if (query.waypoints.isNotEmpty() || query.timeMode != TimeMode.DEPARTURE || settings.overview.routingPreferences.enabled) return null
        val local=Instant.ofEpochMilli(query.time).atZone(transitZone)
        return digest(listOf(journeyCacheProvider(settings),a,b,local.hour,local.minute/15,
            query.products.sortedBy { it.name },query.transportFilter?.sortedBy { it.name },
            query.walkingSpeed,query.ignoreWalking).joinToString("|"))
    }

    private fun pattern(offer: List<Trip>): String? {
        if (offer.size < 3) return null
        return digest(offer.take(3).joinToString("|") { trip ->
            val leg=trip.segments.single()
            fun clock(ms:Long)=Instant.ofEpochMilli(ms).atZone(transitZone).toLocalTime().toSecondOfDay()
            "${trip.fetchedAt}/${leg.lineType}/${leg.lineName}/${clock(leg.plannedStartTime)}/${clock(leg.plannedEndTime)}"
        })
    }

    // A broken or evicted cache always falls back to the regular provider.
    private suspend fun <T> access(fallback:T, action:(SQLiteDatabase)->T):T = withContext(Dispatchers.IO) {
        try { action(helper.writableDatabase) } catch (e:CancellationException) { throw e }
        catch (_:android.database.SQLException) { fallback }
    }

    suspend fun candidate(query:JourneyQuery,settings:AppSettings,now:Long):Boolean {
        val key=key(query,settings) ?: return false
        return access(false) { db ->
            db.rawQuery("SELECT 1 FROM route WHERE key=? AND visits>=2 AND checked BETWEEN CAST(? AS INTEGER) AND CAST(? AS INTEGER)",
                arrayOf(key,(now-48*3600_000L).toString(),now.toString())).use { it.moveToFirst() }
        }
    }

    suspend fun boardingStop(query:JourneyQuery, settings:AppSettings, plan:List<Trip>, now:Long):Location? {
        val key=key(query,settings) ?: return null
        val pattern=pattern(plan) ?: return null
        if (plan.first().startTime-now !in 0..45*60_000L) return null
        return access<Location?>(null) { db ->
            db.rawQuery("""SELECT board FROM route WHERE key=? AND pattern=? AND visits>=2
                AND checked BETWEEN CAST(? AS INTEGER) AND CAST(? AS INTEGER)""",
                arrayOf(key,pattern,(now-48*3600_000L).toString(),now.toString())).use { c ->
                if (c.moveToFirst()) query.origin.copy(id=c.getString(0),provider="VRR") else null
            }
        }
    }

    suspend fun observe(query:JourneyQuery, settings:AppSettings, plan:List<Trip>, live:List<Trip>, now:Long) {
        val key=key(query,settings) ?: return
        val signature=pattern(plan) ?: return
        if (plan.first().startTime-now !in 0..45*60_000L) return
        val first=live.filter { it.queryStart(query.ignoreWalking)>=query.time }.sortedBy { it.startTime }.take(3)
        val agrees=first.size==3 && first.zip(plan).all { (trip,scheduled) ->
            val a=trip.segments.singleOrNull()
            val b=scheduled.segments.single()
            a!=null && !trip.cancelled && a.origin.provider=="VRR" && a.origin.id!=null &&
                efaGlobalStopId(a.origin)==efaGlobalStopId(query.origin) && efaGlobalStopId(a.destination)==efaGlobalStopId(query.destination) &&
                a.lineType==b.lineType && boardLine(a.lineName)==boardLine(b.lineName) &&
                a.plannedStartTime==b.plannedStartTime && a.plannedEndTime==b.plannedEndTime
        }
        access(Unit) { db ->
            db.beginTransaction()
            try {
                db.delete("route","checked < ?",arrayOf((now-14*86400_000L).toString()))
                if (!agrees) db.delete("route","key=?",arrayOf(key))
                else {
                    val day=Instant.ofEpochMilli(query.time).atZone(transitZone).toLocalDate().toEpochDay()
                    val visits=db.rawQuery("SELECT day,visits FROM route WHERE key=? AND pattern=?",
                        arrayOf(key,signature)).use { c ->
                        if (!c.moveToFirst()) 1 else (c.getInt(1)+if (c.getLong(0)!=day) 1 else 0).coerceAtMost(2)
                    }
                    db.execSQL("INSERT OR REPLACE INTO route VALUES(?,?,?,?,?,?)",arrayOf<Any>(key,signature,first.first().segments.first().origin.id!!,day,visits,now))
                    db.execSQL("DELETE FROM route WHERE key IN (SELECT key FROM route ORDER BY checked DESC LIMIT -1 OFFSET 64)")
                }
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        }
    }

    suspend fun clear() = access(Unit) { it.delete("route",null,null); Unit }
}
