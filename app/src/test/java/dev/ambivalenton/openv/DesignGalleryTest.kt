// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.Manifest
import android.graphics.*
import android.location.LocationManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.*
import dev.ambivalenton.openv.ui.tutorial.TutorialPaths
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Design gallery: the real app in every design – overview, network plan and departure board – three
 * designs per sheet. Only on request (`OPENV_GALLERY=all`, group names or theme names); writes to
 * `../audit/gallery`. Board "Display", bar chart and plan follow the app design.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = OpenVApp::class, qualifiers = "de-rDE-w412dp-h860dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DesignGalleryTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var store: Memory55Store
    private val home = Favorite("home", "Zuhause", Location("Herne Bf", "home", 51.5401, 7.2201, "VRR", kind = LocationKind.STOP),
        icon = FavoriteIcon.HOME)
    private val work = Favorite("work", "Arbeit", Location("Dortmund Hbf", "work", 51.5179, 7.4593, "VRR", kind = LocationKind.STOP),
        icon = FavoriteIcon.WORK)
    private val sport = Favorite("sport", "Sport", Location("Bochum Hbf", "sport", 51.478, 7.223, "VRR", kind = LocationKind.STOP),
        icon = FavoriteIcon.FITNESS_CENTER)
    private val uni = Favorite("uni", "Uni", Location("Bochum Ruhr-Universität", "uni", 51.445, 7.262, "VRR", kind = LocationKind.STOP),
        icon = FavoriteIcon.SCHOOL)
    private val fixture = object : ExternalResource() {
        override fun before() {
            val app = RuntimeEnvironment.getApplication() as OpenVApp
            // OPENV_FONT_SCALE checks large system type (e.g. 1.6 or 2.0).
            RuntimeEnvironment.setFontScale(System.getenv("OPENV_FONT_SCALE")?.toFloatOrNull() ?: 1f)
            runCatching { androidx.work.WorkManager.initialize(app, androidx.work.Configuration.Builder().build()) }
            shadowOf(app).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            val manager = app.getSystemService(LocationManager::class.java)
            shadowOf(manager).setLocationEnabled(true); shadowOf(manager).setProviderEnabled("gps", true)
            val repo = LocationRepository(Memory55Store(StoredState()))
            runBlocking {
                repo.updateOnboarding { it.copy(favorites = listOf(home, work, sport, uni)) }
                repo.completeOnboarding()
                repo.updateSettings { it.copy(quietMode = false, powerSaving = false, originMode = OriginMode.MANUAL, manualOrigin = home.location,
                    timelineTheme = TimelineTheme.APP_THEME, networkPlanDesign = NetworkPlanDesign.APP_THEME,
                    board = it.board.copy(design = BoardDesign.APP_THEME),
                    overview = it.overview.copy(showLineNames = true, showTransport = true, showDuration = true)) }
                repo.connectNodes("home", "work", "line1")
                repo.connectNodes("home", "sport", "line2")
                repo.connectNodes("sport", "uni", "line3")
            }
            val configured = runBlocking { repo.state.first() }
            val now = System.currentTimeMillis()
            fun dep(i: Int, line: String, type: LineType, to: String, min: Int, delay: Int?, platform: String) = Departure("d$i", home.location,
                line, type, to, now + min * 60_000L, now + (min + (delay ?: 0)) * 60_000L, DelayInfo(departureSeconds = delay?.times(60)), platform)
            val base = configured.copy(completedTutorials = TutorialPaths.all.map { it.id }.toSet(),
                network = configured.network.copy(columns = 5, rows = 4, nodes = listOf(NetworkNode("home", GridPoint(0, 1)),
                    NetworkNode("work", GridPoint(4, 0)), NetworkNode("sport", GridPoint(2, 3)), NetworkNode("uni", GridPoint(4, 3)))),
                presence = null)
            val board = DepartureBoardState(home.location, listOf(dep(1, "U35", LineType.SUBWAY, "Bochum Hustadt", 2, 0, "1"),
                dep(2, "RE3", LineType.REGIONAL, "Düsseldorf Hbf", 5, 3, "4"), dep(3, "311", LineType.BUS, "Recklinghausen Hbf", 7, null, "C"),
                dep(4, "S2", LineType.SUBURBAN, "Dortmund Hbf", 9, 0, "2"), dep(5, "SB20", LineType.BUS, "Herten Mitte", 12, 0, "A"),
                dep(6, "RB43", LineType.REGIONAL, "Dorsten", 15, null, "5"), dep(7, "323", LineType.BUS, "Wanne-Eickel Hbf", 18, 0, "B")),
                fetchedAt = now, configurationKey = boardConfigurationKey(base))
            store = Memory55Store(base.copy(departureBoard = board))
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200)
                    .message("Example fixture").body("{}".toResponseBody()).build()
            }.build()
            app.koin.loadModules(listOf(org.koin.dsl.module {
                single<LocationRepository> { LocationRepository(store) }
                single<NetworkClient> { NetworkClient(client) }
                single { OverviewRepository({ OverviewTestProvider() }) }
            }))
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(fixture).around(compose)

    private fun settle() = repeat(6) { compose.mainClock.advanceTimeBy(250); compose.waitForIdle() }
    private fun shot(): Bitmap = compose.runOnIdle {
        val v = compose.activity.window.decorView
        val full = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }
        Bitmap.createScaledBitmap(full, v.width / 2, v.height / 2, true).also { full.recycle() }
    }
    private fun tab(name: String) {
        compose.onNode(hasText(name) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick(); settle()
    }

    /**
     * Font audit (`OPENV_FONT_AUDIT=1`, with `OPENV_FONT_SCALE`, optionally `OPENV_GALLERY` designs): every text
     * on the three pages and on all settings pages two levels deep, reported when a word breaks in the middle
     * or the text is cut off / runs out of its box. Writes `../audit/fonts.txt`.
     */
    @Test fun fontAudit() {
        assumeTrue("set OPENV_FONT_AUDIT=1", System.getenv("OPENV_FONT_AUDIT") == "1")
        val wanted = System.getenv("OPENV_GALLERY")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
        val themes = wanted?.map { AppTheme.valueOf(it) } ?: listOf(AppTheme.CLASSIC_LIGHT)
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("main-screen-title").fetchSemanticsNodes().isNotEmpty() }
        val found = linkedSetOf<String>()
        val visited = mutableListOf<String>()
        var before = 0
        fun audit(where: String) {
            settle()
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
                .fetchSemanticsNodes().forEach { node ->
                    val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                    runCatching { node.config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action?.invoke(results) }
                    val l = results.firstOrNull() ?: return@forEach
                    val text = l.layoutInput.text.text
                    if (text.isBlank()) return@forEach
                    if (node.boundsInRoot.width < 1f || node.boundsInRoot.height < 1f) return@forEach
                    val issues = listOfNotNull(
                        "word broken".takeIf { !dev.ambivalenton.openv.ui.components.breaksOnlyBetweenWords(l) },
                        // Whole pixels: a text measured at its intrinsic width may be a fraction wider than its box.
                        (0 until l.lineCount).maxOf { l.getLineRight(it) - l.getLineLeft(it) }.minus(l.size.width)
                            .takeIf { it > 2f }?.let { "cut off in width (${it.toInt()} px)" },
                        (l.multiParagraph.height - l.size.height).takeIf { it > 2f }?.let { "cut off in height (${it.toInt()} px)" },
                        "cut off (lines)".takeIf { l.multiParagraph.didExceedMaxLines })
                    if (issues.isNotEmpty()) found += "$where · ${issues.joinToString("+")} · ${l.layoutInput.style.fontSize} · " +
                        "${node.boundsInRoot.width.toInt()}×${node.boundsInRoot.height.toInt()} · \"${text.replace('\n', '⏎').take(80)}\""
                }
            visited += where
            if (found.size > before) {
                File("../audit/fonts").mkdirs()
                val b = shot(); File("../audit/fonts/${where.replace('/', '_')}.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }; b.recycle()
                before = found.size
            }
        }
        fun back() { compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }; settle() }
        for (theme in themes) {
            runBlocking { store.updateData { it.copy(settings = it.settings.copy(appTheme = theme)) } }
            for (page in listOf("Übersicht", "Suchen", "Abfahrten")) { tab(page); audit("${theme.name}/$page") }
            fun has(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
            fun open(page: dev.ambivalenton.openv.ui.screens.SettingsPage) =
                runCatching { compose.onNodeWithTag("settings-${page.name}").performScrollTo().performClick(); settle() }.isSuccess
            fun root() {
                repeat(4) { if (!has("settings-page-ROOT") && has("settings-app-bar")) back() }
                if (!has("settings-app-bar")) { compose.onAllNodesWithContentDescription("Einstellungen öffnen").onFirst().performClick(); settle() }
            }
            root(); audit("${theme.name}/settings")
            val top = dev.ambivalenton.openv.ui.screens.SettingsPage.ROOT.children
            for (first in top) {
                root(); if (!open(first)) { visited += "${theme.name}/settings/${first.name} (not reached)"; continue }
                audit("${theme.name}/settings/${first.name}")
                for (second in first.children) {
                    root(); open(first)
                    if (!open(second)) continue
                    audit("${theme.name}/settings/${first.name}/${second.name}")
                }
            }
            root(); back()
        }
        File("../audit").mkdirs()
        File("../audit/fonts.txt").writeText(found.joinToString("\n") + "\n\nvisited:\n" + visited.joinToString("\n"))
        println("FONT AUDIT ${found.size} findings")
    }

    @Test fun gallery() {
        val wanted = System.getenv("OPENV_GALLERY")
        assumeTrue("set OPENV_GALLERY=all, group or theme names", !wanted.isNullOrBlank())
        val names = wanted!!.split(',').map { it.trim() }
        // "sample": the first two designs of every collection (one light, one dark where possible).
        val themes = groupedAppThemes().flatMap { (group, list) ->
            if (wanted == "sample") listOfNotNull(list.firstOrNull { !it.dark }, list.firstOrNull { it.dark }).map { group to it }
            else list.filter { wanted == "all" || group.name in names || it.name in names }.map { group to it } }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("main-screen-title").fetchSemanticsNodes().isNotEmpty() }
        val folder = File("../audit/gallery").also { it.mkdirs() }
        themes.chunked(3).forEachIndexed { page, chunk ->
            val strips = chunk.map { (_, theme) ->
                runBlocking { store.updateData { it.copy(settings = it.settings.copy(appTheme = theme)) } }
                tab("Übersicht"); settle(); val a = shot()
                tab("Suchen"); val b = shot()
                tab("Abfahrten"); val c = shot()
                Triple(theme, listOf(a, b, c), a.height)
            }
            val w = strips.first().second.first().width
            val h = strips.first().third
            val label = 26
            val sheet = Bitmap.createBitmap(w * 3, (h + label) * strips.size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(sheet); canvas.drawColor(Color.rgb(233, 233, 236))
            val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 18f; typeface = Typeface.DEFAULT_BOLD }
            strips.forEachIndexed { row, (theme, shots, _) ->
                val top = row * (h + label)
                canvas.drawText("${theme.name} · ${theme.title} · ${theme.language} · ${theme.themeLanguage}", 8f, top + 19f, ink)
                shots.forEachIndexed { i, b -> canvas.drawBitmap(b, (i * w).toFloat(), (top + label).toFloat(), null); b.recycle() }
            }
            val group = chunk.first().first
            File(folder, "${group.ordinal.toString().padStart(2, '0')}-${group.name}-${page.toString().padStart(3, '0')}.png").outputStream()
                .use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
            sheet.recycle()
        }
    }
}
