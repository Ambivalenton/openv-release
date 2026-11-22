// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.repository
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.io.ByteArrayInputStream
import java.net.URI

const val MAX_BACKUP_BYTES=16*1024*1024
/** Newest backup format this build writes; older and newer files are read as far as possible. */
const val BACKUP_FORMAT_VERSION=1
/** Some editors and cloud services put a UTF-8 byte-order mark in front of the JSON. */
private val BYTE_ORDER_MARK=Char(0xFEFF).toString()
@Serializable data class AppBackupDocument(val format:String="openv-backup",val version:Int=1,
    val exportedAt:Long,val state:StoredState,val launcherLogo:AppLogo,val textHistory:List<SeenEditorialText> = emptyList())

/** A readable backup plus everything that was repaired or skipped on the way (shown before import). */
data class BackupImportPreview(val document:AppBackupDocument,val notes:List<String>)

/** A file that cannot be imported, with a message that says why and what to do. Nothing was changed. */
class BackupFormatException(message:String,cause:Throwable?=null):IllegalArgumentException(message,cause)

/**
 * Versioned UTF-8 JSON. Validation completes before either persistence system is modified.
 *
 * Reading is tolerant. Until build 81 one unknown field, one damaged text-history entry or
 * one non-HTTPS server address rejected the whole file with the same generic message. Now unknown
 * fields are ignored, every list entry is read on its own (a broken entry is skipped and named in the
 * preview), unsafe server addresses fall back to the defaults, and the app's own store invariants are
 * met by repairing instead of rejecting. A raw app store file is accepted as well. Only a file that is
 * empty, cut off, not JSON or clearly not from Öpen-V is refused – with a message saying which.
 */
object AppBackupCodec{
    val json=Json{encodeDefaults=true;prettyPrint=true;ignoreUnknownKeys=false;coerceInputValues=false}
    private val lenient=Json{ignoreUnknownKeys=true;coerceInputValues=true;isLenient=true;encodeDefaults=true;explicitNulls=false}

    fun encode(state:StoredState,logo:AppLogo,history:List<SeenEditorialText>,now:Long):ByteArray{
        val portable=portableBackup(state.copy(presence=null,departureBoard=DepartureBoardState(),launchFeedback=null,lastImportToken=null,
            launcherLogo=logo.currentReplacement()))
        val bytes=json.encodeToString(AppBackupDocument(exportedAt=now,state=portable,launcherLogo=logo.currentReplacement(),textHistory=history)).encodeToByteArray()
        require(bytes.size<=MAX_BACKUP_BYTES){tr("Die Sicherung ist zu groß.")}
        return bytes
    }

    /** The importable document only (tests and callers that do not show notes). */
    suspend fun decode(bytes:ByteArray):AppBackupDocument=inspect(bytes).document

    suspend fun inspect(bytes:ByteArray):BackupImportPreview{
        if(bytes.isEmpty())throw BackupFormatException(tr("Die Datei ist leer. Vermutlich wurde die Sicherung nicht vollständig gespeichert. Bitte die Sicherung in der alten Installation erneut speichern."))
        if(bytes.size>MAX_BACKUP_BYTES)throw BackupFormatException(tr("Die Datei ist größer als 16 MB und damit keine Öpen-V-Sicherung."))
        val text=bytes.decodeToString().removePrefix(BYTE_ORDER_MARK).trim()
        if(text.isEmpty())throw BackupFormatException(tr("Die Datei enthält keinen Text. Bitte die Sicherung erneut exportieren."))
        val root=try{lenient.parseToJsonElement(text)}catch(e:SerializationException){
            throw BackupFormatException(if(text.startsWith("{"))tr("Die Datei ist unvollständig oder beschädigt (sie endet mitten in den Daten). Bitte die Sicherung erneut exportieren.")
                else tr("Die Datei ist keine Öpen-V-Sicherung (kein JSON-Text)."),e)
        } as? JsonObject ?: throw BackupFormatException(tr("Die Datei ist keine Öpen-V-Sicherung."))
        val notes=mutableListOf<String>()
        val wrapped=root["format"]?.jsonPrimitive?.contentOrNull=="openv-backup"
        val stateElement=when {
            wrapped->root["state"] as? JsonObject ?: throw BackupFormatException(tr("In der Sicherung fehlen die App-Daten."))
            // A copy of the app's own store file (openv-v1.json) carries the same state without wrapper.
            root.containsKey("favorites")||root.containsKey("settings")||root.containsKey("network")->root.also {
                notes+=tr("Die Datei ist eine Kopie des App-Speichers, keine gespeicherte Sicherung. Sie wird trotzdem übernommen.")
            }
            root.containsKey("format")->throw BackupFormatException(tr("Diese Datei stammt nicht aus Öpen-V."))
            else->throw BackupFormatException(tr("Die Datei ist keine Öpen-V-Sicherung."))
        }
        val version=root["version"]?.jsonPrimitive?.intOrNull ?: 1
        if(wrapped && version>BACKUP_FORMAT_VERSION)notes+=tr("Die Sicherung stammt aus einer neueren App-Version. Unbekannte Angaben werden übersprungen.")
        val state=readState(stateElement,notes)
        val logo=(root["launcherLogo"] ?: stateElement["launcherLogo"])?.let {runCatching {lenient.decodeFromJsonElement<AppLogo>(it)}.getOrNull()}
            ?: state.launcherLogo ?: AppLogo.ORIGINAL
        val texts=readTexts(root["textHistory"],notes)
        val exportedAt=root["exportedAt"]?.jsonPrimitive?.longOrNull ?: 0L
        val document=AppBackupDocument(exportedAt=exportedAt,state=state.copy(launcherLogo=logo.currentReplacement(),
            personalThemesIntroduced=state.personalThemesIntroduced||state.personalThemes.isNotEmpty()),
            launcherLogo=logo.currentReplacement(),textHistory=texts)
        return BackupImportPreview(document,notes)
    }

    /** Each list entry on its own: a damaged entry is skipped instead of failing the whole file. */
    private fun <T> entries(element:JsonElement?,serializer:KSerializer<T>,skipped:(Int)->Unit):List<T> {
        val array=element as? JsonArray ?: return emptyList()
        var broken=0
        val result=array.mapNotNull {item->runCatching {lenient.decodeFromJsonElement(serializer,item)}.getOrElse {broken++;null}}
        if(broken>0)skipped(broken)
        return result
    }

    private suspend fun readState(source:JsonObject,notes:MutableList<String>):StoredState {
        fun skipped(what:String)={count:Int->notes+=tr("%1\$s: %2\$s beschädigte Einträge übersprungen.",what,count)}
        val legacyBoard=!source.containsKey("boardStopsDecoupled")
        var favorites=entries(source["favorites"],Favorite.serializer(),skipped(tr("Favoriten")))
        val invalid=favorites.filterNot {it.hasValidRoutingOptions()&&it.label.isNotBlank()&&it.location.routable}
        if(invalid.isNotEmpty())notes+=tr("%1\$s Favoriten ohne gültigen Ort wurden übersprungen.",invalid.size)
        favorites=(favorites-invalid.toSet()).map {it.copy(label=it.label.take(80),hiddenRules=it.hiddenRules.take(8))}
        val limit=if(favorites.any {it.counterpole!=null})40 else 80
        if(favorites.size>limit){notes+=tr("Nur die ersten %1\$s Favoriten wurden übernommen.",limit);favorites=favorites.take(limit)}
        val history=entries(source["history"],SearchHistory.serializer(),skipped(tr("Suchverlauf")))
        val saved=entries(source["savedJourneys"],SavedJourney.serializer(),skipped(tr("Vorgemerkte Verbindungen")))
        val boardStops=entries(source["boardStops"],Location.serializer(),skipped(tr("Haltestellen-Liste"))).filter {it.routable}.take(MAX_BOARD_PLACES)
        val pairs=entries(source["favoritePairs"],FavoritePair.serializer(),skipped(tr("Favoritenpaare"))).take(40)
        val themes=repairPersonalThemes(entries(source["personalThemes"],PersonalTheme.serializer(),skipped(tr("Persönliche Themes"))),notes)
        val network=(source["network"] as? JsonObject)?.let {net->
            runCatching {lenient.decodeFromJsonElement(TransitNetwork.serializer(),net)}.getOrElse {
                notes+=tr("Der Netzplan war beschädigt; die Favoriten werden neu angeordnet.");null
            }
        }
        // Everything else is decoded as a whole, tolerant of unknown fields and values.
        val rest=JsonObject(source.filterKeys {it !in setOf("favorites","history","savedJourneys","boardStops","favoritePairs","personalThemes","network","onboardingDraft")})
        val dropped=mutableListOf<String>()
        // M14: a backup of the other edition may name a timetable service this one does not offer.
        val engine=((rest["settings"] as? JsonObject)?.get("engine") as? JsonPrimitive)?.contentOrNull
        if(engine!=null && Engine.entries.none {it.name==engine})
            notes+=tr("Die Sicherung stammt aus einer anderen Fassung von Öpen-V. Der Fahrplandienst wird für diese Fassung eingestellt.")
        val settings=(rest["settings"] as? JsonObject)?.let {greedy(AppSettings.serializer(),it,dropped)}
        val base=lenient.decodeFromJsonElement(StoredState.serializer(),
            greedy(StoredState.serializer(),JsonObject(rest+listOfNotNull(settings?.let {"settings" to it})),dropped))
        if(dropped.isNotEmpty())notes+=tr("Einige Einstellungen konnten nicht gelesen werden und bleiben auf dem Standard.")
        var state=portableBackup(base.copy(favorites=favorites,history=history,savedJourneys=saved,boardStops=boardStops,favoritePairs=pairs,
            personalThemes=themes,network=network ?: TransitNetwork(),onboardingDraft=null,
            schemaVersion=base.schemaVersion.coerceIn(1,10),boardStopsDecoupled=if(legacyBoard)false else base.boardStopsDecoupled,
            overviewFavoriteIds=base.overviewFavoriteIds.take(MAX_OVERVIEW_FAVORITES),settings=safeEndpoints(base.settings,notes),
            onboardingVersion=maxOf(base.onboardingVersion,1)))
        // Reuse the application's full invariants, migrations and two-megabyte store bound. If the store
        // is still too large, vormerkungen and then the oldest searches give way, never the favourites.
        repeat(4) {attempt->
            val bytes=StateSerializer.json.encodeToString(state).encodeToByteArray()
            val outcome=try {Result.success(StateSerializer.readFrom(ByteArrayInputStream(bytes)))}
                catch(e:kotlinx.coroutines.CancellationException){throw e} catch(e:Exception){Result.failure(e)}
            outcome.getOrNull()?.let {return it}
            val error=outcome.exceptionOrNull()
            state=when(attempt) {
                0->if(bytes.size>1_900_000&&state.savedJourneys.isNotEmpty()){notes+=tr("Vorgemerkte Verbindungen wurden wegen der Dateigröße nicht übernommen.");state.copy(savedJourneys=emptyList())}
                    else state.copy(network=TransitNetwork()).also {if(network!=null)notes+=tr("Der Netzplan wird neu angeordnet.")}
                1->state.copy(history=state.history.take(50),savedJourneys=emptyList()).also {notes+=tr("Ältere Suchen und Vormerkungen wurden nicht übernommen.")}
                2->state.copy(personalThemes=emptyList(),settings=state.settings.copy(quietAppearance=null,normalAppearance=null)).also {notes+=tr("Persönliche Themes konnten nicht übernommen werden.")}
                else->throw BackupFormatException(tr("Die Favoriten und Einstellungen in dieser Datei sind ungültig und können nicht übernommen werden."),error)
            }
        }
        throw BackupFormatException(tr("Die Daten in dieser Datei sind ungültig."))
    }

    /**
     * The largest readable part of [source]: keys are taken one after another and kept while the
     * object still decodes (every field has a default). Unreadable keys are reported in [dropped].
     */
    private fun <T> greedy(serializer:KSerializer<T>,source:JsonObject,dropped:MutableList<String>):JsonObject {
        if(runCatching {lenient.decodeFromJsonElement(serializer,source)}.isSuccess)return source
        val kept=LinkedHashMap<String,JsonElement>()
        for((key,value) in source) {
            kept[key]=value
            if(runCatching {lenient.decodeFromJsonElement(serializer,JsonObject(kept))}.isFailure){kept.remove(key);dropped+=key}
        }
        return JsonObject(kept)
    }

    private fun repairPersonalThemes(themes:List<PersonalTheme>,notes:MutableList<String>):List<PersonalTheme> {
        val names=mutableSetOf<String>();val ids=mutableSetOf<String>();var renamed=0
        val result=themes.filter {it.id.length in 1..80 && ids.add(it.id)}.take(MAX_PERSONAL_THEMES).map {theme->
            var name=personalThemeName(theme.name).take(48).ifBlank {tr("Mein Design")}
            var n=2
            while(personalThemeNameKey(name) in names){name=personalThemeName("${theme.name.take(44)} $n");n++}
            if(name!=theme.name)renamed++
            names+=personalThemeNameKey(name)
            theme.copy(name=name,createdAt=theme.createdAt.coerceAtLeast(0))
        }.filter {validPersonalThemeName(it.name)}
        if(renamed>0)notes+=tr("%1\$s persönliche Themes wurden umbenannt, weil ihr Name doppelt oder ungültig war.",renamed)
        if(result.size<themes.size)notes+=tr("%1\$s persönliche Themes wurden übersprungen.",themes.size-result.size)
        return result
    }

    private fun readTexts(element:JsonElement?,notes:MutableList<String>):List<SeenEditorialText> {
        var broken=0
        val rows=entries(element,SeenEditorialText.serializer()) {broken+=it}.mapNotNull {t->
            val first=t.firstShownAt.coerceAtLeast(0)
            t.copy(firstShownAt=first,lastShownAt=t.lastShownAt.coerceAtLeast(first),shownCount=t.shownCount.coerceAtLeast(1),
                sourceUrl=t.sourceUrl?.takeIf(::https),text=t.text.take(20_000))
                .takeIf {it.key.length in 1..256&&it.contentId.length in 1..160&&it.text.isNotEmpty()&&it.language in setOf("de","en")}
                ?: run {broken++;null}
        }.distinctBy {it.key}.take(20_000)
        if(broken>0)notes+=tr("Textchronik: %1\$s ungültige Einträge übersprungen.",broken)
        return rows
    }

    private fun https(value:String)=runCatching{val u=URI(value);u.scheme=="https"&&!u.host.isNullOrBlank()&&u.userInfo==null}.getOrDefault(false)

    /** A backup never brings a non-HTTPS server along: such addresses return to the defaults. */
    private fun safeEndpoints(s:AppSettings,notes:MutableList<String>):AppSettings {
        val d=AppSettings()
        fun tiles(t:String)=https(t.replace("{z}","1").replace("{x}","1").replace("{y}","1").replace("{s}","a").replace("{r}",""))
        val safe=s.copy(efaEndpoint=s.efaEndpoint.takeIf(::https) ?: d.efaEndpoint,
            geocodingEndpoint=s.geocodingEndpoint.takeIf(::https) ?: d.geocodingEndpoint,
            walkingRoutingEndpoint=s.walkingRoutingEndpoint.takeIf(::https) ?: d.walkingRoutingEndpoint,
            mapTileTemplate=s.mapTileTemplate.takeIf(::tiles) ?: d.mapTileTemplate)
            .let { TimetableProfile.safeExtras(it, ::https) }
        if(safe!=s)notes+=tr("Serveradressen ohne HTTPS wurden auf die Standardwerte zurückgesetzt.")
        return safe
    }
}
