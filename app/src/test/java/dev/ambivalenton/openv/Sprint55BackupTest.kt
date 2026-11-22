// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.test.core.app.ApplicationProvider
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.*
import dev.ambivalenton.openv.data.preferences.normalizePreferences
import dev.ambivalenton.openv.launcher.LauncherIconManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

internal class Memory55Store(initial:StoredState):DataStore<StoredState>{
    val state=MutableStateFlow(initial);var failWrites=false
    private val mutex=Mutex()
    override val data:Flow<StoredState> = state
    override suspend fun updateData(transform:suspend(StoredState)->StoredState):StoredState=mutex.withLock{
        val next=transform(state.value)
        if(failWrites)throw IOException("Simulated full disk")
        StateSerializer.writeTo(next,ByteArrayOutputStream())
        state.value=next;next
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class Sprint55BackupTest{
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    private val text=SeenEditorialText("fact:de:1","fact",JourneyContentKind.FACT,"Ein hilfreicher Fakt.","de","https://example.org/fact",10,20,2)
    private fun rich():StoredState{
        val settings=normalizePreferences(AppSettings(appTheme=AppTheme.OS_WIN_XP,fontId="inter",timelineTheme=TimelineTheme.APP_THEME,
            timelineShowWalking=false,appThemeColors=mapOf(AppTheme.OS_WIN_XP to AppThemeColors(0xffaabbcc,0xffeeeeee,0xff112233,0xff223344,0xff000011)),
            overview=OverviewSettings(showQuotes=false,quoteDecoration=QuoteDecoration.TONAL_PLATE,cards=true),
            board=BoardSettings(design=BoardDesign.APP_THEME),includeLongDistance=true,walkingSpeed=WalkingSpeed.FAST))
        val f=Favorite("home","Zuhause",Fixture45.a,icon=FavoriteIcon.HOME,accentSeed=0xffcc8844,shape=IconStyle.CLOVER)
        return StoredState(onboardingVersion=1,settings=settings,favorites=listOf(f),history=listOf(SearchHistory(JourneyQuery(Fixture45.a,Fixture45.b,Fixture45.now),Fixture45.now,4)),
            presence=DevicePresence(Fixture45.a,Fixture45.now,5f),completedTutorials=setOf("design"),stopColors=mapOf("a" to 0xffabcdef),launcherLogo=AppLogo.BRAND_PIXEL)
            .withPersonalTheme("Mein Alltag",AppLogo.BRAND_PIXEL,100).first
    }
    @Test fun portableJsonRoundTripRetainsUserDataAndEveryDesignButNeverALiveLocation()=runBlocking{
        val original=rich();val bytes=AppBackupCodec.encode(original,AppLogo.BRAND_PIXEL,listOf(text),1000)
        val doc=AppBackupCodec.decode(bytes)
        assertEquals(original.favorites,doc.state.favorites);assertEquals(original.history,doc.state.history)
        assertEquals(original.settings.personalDesign(AppLogo.BRAND_PIXEL),doc.state.settings.personalDesign(doc.launcherLogo))
        assertEquals(original.personalThemes,doc.state.personalThemes);assertEquals(listOf(text),doc.textHistory)
        assertEquals(original.stopColors,doc.state.stopColors);assertNull(doc.state.presence);assertNull(doc.state.lastImportToken)
        assertEquals(DepartureBoardState(),doc.state.departureBoard)
    }
    @Test fun malformedUnsupportedAndUnsafeFilesAreRejectedBeforeAnyStateChange()=runBlocking{
        val valid=AppBackupCodec.encode(rich(),AppLogo.ORIGINAL,listOf(text),1000).decodeToString()
        for(bad in listOf("{}",valid.take(80))){
            assertTrue(bad.take(100),runCatching{AppBackupCodec.decode(bad.encodeToByteArray())}.isFailure)
        }
        // Tolerated and repaired instead of rejecting the whole file.
        assertTrue(AppBackupCodec.inspect(valid.replace("\"version\": 1","\"version\": 99").encodeToByteArray()).notes.isNotEmpty())
        assertEquals(AppSettings().efaEndpoint,AppBackupCodec.decode(valid.replace(TimetableProfile.endpoint,"http://unsafe.example/").encodeToByteArray()).state.settings.efaEndpoint)
        assertTrue(AppBackupCodec.decode(valid.replace("\"label\": \"Zuhause\"","\"label\": \"\"").encodeToByteArray()).state.favorites.isEmpty())
        val store=Memory55Store(rich());val before=store.state.value
        assertTrue(runCatching{AppBackupCodec.decode(ByteArray(0))}.isFailure);assertSame(before,store.state.value)
    }
    @Test fun applyingADesignPreservesJourneyPoliciesAndDeletingTheLastThemeKeepsTheMenu()=runBlocking{
        val initial=rich();val store=Memory55Store(initial);val locations=LocationRepository(store)
        val changed=initial.settings.copy(appTheme=AppTheme.NORDIC_ICE,fontId=null,includeLongDistance=false,walkingSpeed=WalkingSpeed.RELAXED,alternativeSuggestions=false,
            overview=initial.settings.overview.copy(bufferMinutes=7f),board=initial.settings.board.copy(selectedStop=Fixture45.b.copy(kind=LocationKind.STOP,id="stop-b")))
        store.state.value=initial.copy(settings=changed,boardStops=listOf(requireNotNull(changed.board.selectedStop)))
        locations.applyPersonalTheme(initial.personalThemes.single().id)
        val after=store.state.value
        assertEquals(AppTheme.OS_WIN_XP,after.settings.appTheme);assertEquals("inter",after.settings.fontId)
        assertFalse(after.settings.includeLongDistance);assertFalse(after.settings.alternativeSuggestions)
        assertEquals(WalkingSpeed.RELAXED,after.settings.walkingSpeed);assertEquals(7f,after.settings.overview.bufferMinutes)
        assertEquals(changed.board.selectedStop,after.settings.board.selectedStop);assertEquals(initial.favorites,after.favorites)
        assertEquals(AppLogo.BRAND_PIXEL,after.launcherLogo)
        locations.removePersonalTheme(initial.personalThemes.single().id)
        assertTrue(store.state.value.personalThemesIntroduced);assertTrue(store.state.value.personalThemes.isEmpty())
    }
    @Test fun namesAreNormalizedAndDuplicatesCannotOverwriteAPreset(){
        val s=rich()
        for(name in listOf("", "   ", "Mein Alltag", "MEIN ALLTAG", "Bad\u0000Name", "x".repeat(49))){
            assertTrue(runCatching{s.withPersonalTheme(name,AppLogo.ORIGINAL,100)}.isFailure)
        }
        val next=s.withPersonalTheme("  Café  ",AppLogo.ORIGINAL,200).first
        assertEquals("Café",next.personalThemes.last().name);assertEquals(2,next.personalThemes.size)
    }
    @Test fun realSqliteImportAndRepeatedRestoreKeepFullHistoryWithoutDuplicates()=runBlocking{
        val memory=Memory55Store(StoredState(onboardingVersion=1));val locations=LocationRepository(memory)
        val db=EditorialHistoryRepository(context,"backup55-${newId()}.db");val service=AppBackupService(context,locations,db,LauncherIconManager(context))
        try{
            val doc=AppBackupCodec.decode(AppBackupCodec.encode(rich(),AppLogo.BRAND_PIXEL,listOf(text),200))
            repeat(2){assertTrue(service.import(doc).isSuccess)}
            assertEquals(1,db.snapshot().size);assertEquals(listOf(text),db.snapshot());assertEquals(rich().favorites,memory.state.value.favorites)
            val exported=AppBackupCodec.decode(service.export());assertEquals(listOf(text),exported.textHistory)
            assertEquals(AppLogo.BRAND_PIXEL,exported.launcherLogo);assertEquals(1,exported.state.personalThemes.size)
        }finally{db.close();LauncherIconManager(context).select(AppLogo.ORIGINAL)}
    }
    @Test fun failedDatastoreWriteRollsBackTheRealSqliteChronicleAndRecoveryIsIdempotent()=runBlocking{
        val old=text.copy(key="old",text="Vorher")
        val memory=Memory55Store(rich());val before=memory.state.value;val locations=LocationRepository(memory)
        val db=EditorialHistoryRepository(context,"rollback55-${newId()}.db");val service=AppBackupService(context,locations,db,LauncherIconManager(context))
        try{
            db.replaceAll(listOf(old));memory.failWrites=true
            val doc=AppBackupCodec.decode(AppBackupCodec.encode(StoredState(),AppLogo.ORIGINAL,listOf(text),200))
            assertTrue(runCatching{service.import(doc)}.isFailure)
            assertEquals(listOf(old),db.snapshot());assertSame(before,memory.state.value)
            memory.failWrites=false;repeat(2){service.recoverIfNeeded()};assertEquals(listOf(old),db.snapshot())
        }finally{db.close()}
    }
    @Test fun recoveryAfterProcessDeathUsesTheDatastoreCommitMarker()=runBlocking{
        val memory=Memory55Store(StoredState());val db=EditorialHistoryRepository(context,"recovery55-${newId()}.db")
        val service=AppBackupService(context,LocationRepository(memory),db,LauncherIconManager(context))
        val journal=File(context.filesDir,"backup-import-recovery.json")
        try{
            val old=text.copy(key="old");val token="import-token"
            fun stage(){journal.writeText(buildJsonObject{put("token",token);put("previousTexts",AppBackupCodec.json.encodeToJsonElement(listOf(old)))}.toString())}
            db.replaceAll(listOf(text));stage();service.recoverIfNeeded();assertEquals(listOf(old),db.snapshot())
            db.replaceAll(listOf(text));stage();memory.state.value=memory.state.value.copy(lastImportToken=token)
            service.recoverIfNeeded();assertEquals(listOf(text),db.snapshot());assertFalse(journal.exists())
        }finally{journal.delete();db.close()}
    }
}
