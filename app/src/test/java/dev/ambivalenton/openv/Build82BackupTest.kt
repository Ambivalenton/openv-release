// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.app.Application
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.*
import dev.ambivalenton.openv.localization.tr
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A backup is read as far as possible; only empty, cut-off or foreign files are refused. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class Build82BackupTest {
    private val now=1_800_000_000_000L
    private val home=Location("Herne","h",51.54,7.22,"TEST");private val work=Location("Dortmund","w",51.51,7.46,"TEST")
    private val text=SeenEditorialText("fact:de:1","fact",JourneyContentKind.FACT,"Ein hilfreicher Fakt.","de","https://example.org/fact",10,20,2)
    private fun state():StoredState {
        var s=StoredState(onboardingVersion=1,favorites=listOf(Favorite("home","Zuhause",home,icon=FavoriteIcon.HOME),Favorite("work","Arbeit",work,icon=FavoriteIcon.WORK)),
            settings=AppSettings(appTheme=AppTheme.MINT_SILENCE))
        s=s.withNetworkNodeFor("home").withNetworkNodeFor("work").withEdge("home","work")
        return s.withPersonalTheme("Abend",AppLogo.ORIGINAL,now).first
    }
    private fun exported(s:StoredState=state(),texts:List<SeenEditorialText> = listOf(text))=
        Json.parseToJsonElement(AppBackupCodec.encode(s,AppLogo.ORIGINAL,texts,now).decodeToString()).jsonObject
    private fun JsonObject.edit(block:MutableMap<String,JsonElement>.()->Unit)=JsonObject(toMutableMap().apply(block))
    private fun JsonObject.editState(block:MutableMap<String,JsonElement>.()->Unit)=edit {put("state",(get("state") as JsonObject).edit(block))}
    private fun inspect(root:JsonElement)=runBlocking {AppBackupCodec.inspect(root.toString().encodeToByteArray())}

    @Test fun anExportReadsBackCompletelyWithoutNotes() {
        val preview=inspect(exported())
        assertEquals(emptyList<String>(),preview.notes)
        assertEquals(listOf("home","work"),preview.document.state.favorites.map {it.id})
        assertEquals(1,preview.document.state.network.edges.size)
        assertEquals(listOf(text),preview.document.textHistory)
    }

    @Test fun unknownFieldsAndValuesFromOtherVersionsAreIgnored() {
        val root=exported().edit {put("comment",JsonPrimitive("neuer"))}.editState {
            put("futureFeature",JsonObject(mapOf("a" to JsonPrimitive(1))))
            put("settings",(get("settings") as JsonObject).edit {put("unknownSwitch",JsonPrimitive(true));put("appTheme",JsonPrimitive("FUTURE_THEME"))})
        }
        val preview=inspect(root)
        assertEquals(2,preview.document.state.favorites.size)
        assertNull("an unknown theme falls back instead of failing",preview.document.state.settings.appTheme)
        val newer=inspect(exported().edit {put("version",JsonPrimitive(7))})
        assertTrue(newer.notes.any {it.contains(tr("neueren App-Version").take(10))})
    }

    @Test fun aDamagedEntryIsSkippedAndNamedInsteadOfRejectingTheFile() {
        val root=exported(texts=listOf(text,text.copy(key="bad",language="xx"),text.copy(key="nourl",sourceUrl="http://unsafe"))).editState {
            put("favorites",JsonArray((get("favorites") as JsonArray)+JsonPrimitive("kaputt")+JsonObject(mapOf("label" to JsonPrimitive("")))))
        }
        val preview=inspect(root)
        assertEquals(listOf("home","work"),preview.document.state.favorites.map {it.id})
        assertEquals(listOf("fact:de:1","nourl"),preview.document.textHistory.map {it.key})
        assertNull("an unsafe source link is dropped, the text kept",preview.document.textHistory[1].sourceUrl)
        assertTrue(preview.notes.isNotEmpty())
    }

    @Test fun unsafeServersAndDuplicateThemeNamesAreRepaired() {
        val theme=state().personalThemes.single()
        val root=exported().editState {
            put("settings",(get("settings") as JsonObject).edit {put("efaEndpoint",JsonPrimitive("http://unsafe.example/"))})
            put("personalThemes",JsonArray(listOf(Json.encodeToJsonElement(theme),Json.encodeToJsonElement(theme.copy(id="other",name="abend ")))))
        }
        val preview=inspect(root)
        assertEquals(AppSettings().efaEndpoint,preview.document.state.settings.efaEndpoint)
        assertEquals(2,preview.document.state.personalThemes.size)
        assertEquals(2,preview.document.state.personalThemes.map {personalThemeNameKey(it.name)}.distinct().size)
    }

    @Test fun aRawStoreFileAndAnUnfinishedSetupAreAccepted() {
        val raw=StateSerializer.json.encodeToJsonElement(StoredState.serializer(),state().copy(onboardingVersion=0))
        val preview=inspect(raw)
        assertEquals(2,preview.document.state.favorites.size)
        assertEquals("an imported backup completes the first-launch setup",1,preview.document.state.onboardingVersion)
        assertTrue(preview.notes.isNotEmpty())
    }

    @Test fun emptyCutOffAndForeignFilesExplainTheProblem() {
        fun message(bytes:ByteArray)=runBlocking {runCatching {AppBackupCodec.inspect(bytes)}.exceptionOrNull()}
        val empty=message(ByteArray(0));assertTrue(empty is BackupFormatException);assertTrue(empty!!.message!!.contains(tr("leer").take(4)))
        val valid=AppBackupCodec.encode(state(),AppLogo.ORIGINAL,emptyList(),now)
        val cut=message(valid.copyOf(valid.size/2));assertTrue(cut is BackupFormatException)
        assertTrue(cut!!.message!!.contains(tr("unvollständig").take(6)))
        assertTrue(message("{\"format\":\"andere-app\"}".encodeToByteArray()) is BackupFormatException)
        assertTrue(message("Hallo".encodeToByteArray()) is BackupFormatException)
        // A byte-order mark in front of the JSON does not matter.
        assertEquals(2,runBlocking {AppBackupCodec.inspect(byteArrayOf(0xEF.toByte(),0xBB.toByte(),0xBF.toByte())+valid)}.document.state.favorites.size)
    }
}
