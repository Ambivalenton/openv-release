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
import org.junit.Assert.*
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

/** Real MainActivity screenshots with clearly named example places, never live passenger data. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], application=OpenVApp::class, qualifiers="de-rDE-w432dp-h768dp-400dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoreAssetsTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val folder = File("../audit/store-assets")
    private val home = Favorite("home", "Zuhause · Beispiel",
        Location("Herne Bahnhof", "home", 51.5401, 7.2201, "VRR", kind=LocationKind.STOP), icon=FavoriteIcon.HOME)
    private val work = Favorite("work", "Arbeit · Beispiel",
        Location("Dortmund Hbf", "work", 51.5179, 7.4593, "VRR", kind=LocationKind.STOP), icon=FavoriteIcon.WORK)
    private val sport = Favorite("sport", "Sport · Beispiel",
        Location("Bochum Hbf", "sport", 51.478, 7.223, "VRR", kind=LocationKind.STOP), icon=FavoriteIcon.FITNESS_CENTER)
    private val fixture = object : ExternalResource() {
        override fun before() {
            val app = RuntimeEnvironment.getApplication() as OpenVApp
            RuntimeEnvironment.setFontScale(1f)
            // Demonstrate the fully supported manual-origin flow. Robolectric cannot host real
            // launcher widgets, so simulated GPS updates must not start widget refreshes.
            shadowOf(app).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            val manager = app.getSystemService(LocationManager::class.java)
            shadowOf(manager).setLocationEnabled(true); shadowOf(manager).setProviderEnabled("gps", true)
            val repo = LocationRepository(Memory55Store(StoredState()))
            runBlocking {
                repo.updateOnboarding { it.copy(favorites=listOf(home, work, sport)) }
                repo.completeOnboarding()
                repo.updateSettings { it.copy(quietMode=false, powerSaving=true, themeMode=ThemeMode.LIGHT, originMode=OriginMode.MANUAL, manualOrigin=home.location, overview=it.overview.copy(showLineNames=true, showTransport=true, showDuration=true)) }
                repo.connectNodes("home", "work", "line1")
                repo.connectNodes("home", "sport", "line2")
            }
            // A typed test store avoids any network, account or real user preferences.
            val configured = runBlocking { repo.state.first() }
            val store = Memory55Store(configured.copy(
                completedTutorials=TutorialPaths.all.map { it.id }.toSet(),
                network=configured.network.copy(columns=4,rows=3,nodes=listOf(
                    NetworkNode("home",GridPoint(0,1)),NetworkNode("work",GridPoint(3,0)),NetworkNode("sport",GridPoint(3,2)))),
                presence=null))
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

    private fun settle() {
        repeat(6) { compose.mainClock.advanceTimeBy(250); compose.waitForIdle() }
    }

    private fun save(bitmap: Bitmap, name: String) {
        folder.mkdirs()
        if(name!="icon.png") bitmap.setHasAlpha(false)
        File(folder, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
    }

    private fun capture(name: String) {
        compose.onNodeWithText("Die Favoriten konnten nicht aktualisiert werden.").assertDoesNotExist()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            assertTrue(view.width>=320 && view.height>=320)
            val bitmap = Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            save(bitmap,name)
        }
    }

    @Test fun storeViewsAndGraphicsComeFromThePublishedApplication() {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("main-screen-title").fetchSemanticsNodes().isNotEmpty() }
        settle()
        assertTrue(compose.onAllNodesWithTag("transit-source-vrr",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty())
        capture("01-uebersicht.png")
        compose.onNode(hasText("Suchen") and SemanticsMatcher.expectValue(SemanticsProperties.Role,Role.Tab)).performClick()
        settle()
        compose.onNodeWithTag("network-plan").performScrollTo()
        settle(); capture("02-netzplan.png")
        compose.onNodeWithContentDescription("Einstellungen öffnen").performClick()
        settle(); capture("03-einstellungen.png")
        compose.onNodeWithTag("settings-INFO").performScrollTo().performClick()
        settle(); capture("04-info-hilfe.png")
        val context = compose.activity
        val logo = requireNotNull(context.getDrawable(AppLogo.ORIGINAL.preview))
        val icon = Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888)
        Canvas(icon).apply { drawColor(Color.WHITE); logo.setBounds(0,0,512,512); logo.draw(this) }
        save(icon,"icon.png")
        val graphic = Bitmap.createBitmap(1024,500,Bitmap.Config.ARGB_8888)
        Canvas(graphic).apply {
            drawColor(Color.rgb(244,248,244))
            val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(21,65,56) }
            ink.typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL); ink.textSize=76f
            drawText("Öpen-V",64f,163f,ink)
            ink.typeface=Typeface.create("sans-serif",Typeface.NORMAL); ink.textSize=35f
            drawText("Deine Orte. Deine Wege.",64f,235f,ink)
            ink.textSize=24f; drawText("Fahrpläne für Rhein-Ruhr",64f,298f,ink)
            ink.color=Color.rgb(210,231,218)
            drawRoundRect(664f,104f,952f,392f,64f,64f,ink)
            logo.setBounds(692,132,924,364); logo.draw(this)
        }
        save(graphic,"featureGraphic.png")
    }
}
