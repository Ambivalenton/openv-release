// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.repository

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dev.ambivalenton.openv.data.model.*
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A dedicated database keeps an untruncated reading history out of the bounded preferences file. */
class EditorialHistoryRepository(context:Context,databaseName:String="editorial-history.db",private val clock:()->Long=System::currentTimeMillis) {
    private val lock=Mutex()
    private val database=object:SQLiteOpenHelper(context.applicationContext,databaseName,null,1) {
        override fun onCreate(db:SQLiteDatabase) {
            db.execSQL("CREATE TABLE texts (entry_key TEXT PRIMARY KEY,content_id TEXT NOT NULL,kind TEXT NOT NULL,body TEXT NOT NULL,language TEXT NOT NULL,source_url TEXT,first_shown INTEGER NOT NULL,last_shown INTEGER NOT NULL,shown_count INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX texts_last_shown ON texts(last_shown DESC)")
        }
        override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int) {}
    }
    private val rows=MutableStateFlow<List<SeenEditorialText>>(emptyList())
    val history=rows.asStateFlow()
    private fun readRows():List<SeenEditorialText> = database.readableDatabase.rawQuery(
        "SELECT entry_key,content_id,kind,body,language,source_url,first_shown,last_shown,shown_count FROM texts ORDER BY last_shown DESC,entry_key",null
    ).use {cursor->buildList {
        while(cursor.moveToNext()) add(SeenEditorialText(cursor.getString(0),cursor.getString(1),
            JourneyContentKind.valueOf(cursor.getString(2)),cursor.getString(3),cursor.getString(4),
            if(cursor.isNull(5)) null else cursor.getString(5),cursor.getLong(6),cursor.getLong(7),cursor.getLong(8)))
    }}
    suspend fun load()=withContext(Dispatchers.IO) {lock.withLock {rows.value=readRows()}}
    suspend fun recordDisplayed(content:JourneyContent,displayedText:String=content.text)=withContext(Dispatchers.IO) {
        require(displayedText==content.german || displayedText==content.english)
        val language=if(displayedText==content.german) "de" else "en"
        val digest=MessageDigest.getInstance("SHA-256").digest(displayedText.toByteArray()).take(12).joinToString("") {"%02x".format(it)}
        val key="${content.id}:$language:$digest"
        lock.withLock {
            val db=database.writableDatabase;val now=clock()
            val values=ContentValues().apply {
                put("entry_key",key);put("content_id",content.id);put("kind",content.kind.name);put("body",displayedText)
                put("language",language);put("source_url",content.sourceUrl);put("first_shown",now);put("last_shown",now);put("shown_count",1)
            }
            db.beginTransaction()
            try {
                if(db.insertWithOnConflict("texts",null,values,SQLiteDatabase.CONFLICT_IGNORE)==-1L)
                    db.execSQL("UPDATE texts SET last_shown=?,shown_count=shown_count+1 WHERE entry_key=?",arrayOf<Any>(now,key))
                db.setTransactionSuccessful()
            } finally {db.endTransaction()}
            rows.value=readRows()
        }
    }
    suspend fun snapshot():List<SeenEditorialText> = withContext(Dispatchers.IO){lock.withLock{readRows()}}
    private fun replaceRows(next:List<SeenEditorialText>){
        val db=database.writableDatabase;db.beginTransaction()
        try{
            db.delete("texts",null,null)
            next.forEach{r->db.insertOrThrow("texts",null,ContentValues().apply{
                put("entry_key",r.key);put("content_id",r.contentId);put("kind",r.kind.name);put("body",r.text)
                put("language",r.language);put("source_url",r.sourceUrl);put("first_shown",r.firstShownAt)
                put("last_shown",r.lastShownAt);put("shown_count",r.shownCount)
            })}
            db.setTransactionSuccessful()
        }finally{db.endTransaction()}
    }
    suspend fun replaceAll(next:List<SeenEditorialText>)=withContext(Dispatchers.IO){lock.withLock{
        replaceRows(next);rows.value=next
    }}
    /** No suspending call holds a SQLite transaction. The mutex hides the staged rows until commit. */
    suspend fun <T> replaceForImport(next:List<SeenEditorialText>,stage:(List<SeenEditorialText>)->Unit,commit:suspend()->T):T = withContext(Dispatchers.IO){lock.withLock{
        val old=readRows();stage(old)
        try{replaceRows(next);val result=commit();rows.value=next;result}
        catch(error:Exception){replaceRows(old);rows.value=old;throw error}
    }}
    fun close() {database.close()}
}
