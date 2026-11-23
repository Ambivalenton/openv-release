// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.components.LocalFavoritePlaces
import dev.ambivalenton.openv.ui.dialogs.FavoriteEditDialog
import dev.ambivalenton.openv.ui.onboarding.OnboardingWizard
import dev.ambivalenton.openv.ui.onboarding.SetupPermissions
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Build 101 · "Favorit hinzufügen" in three beats (Ort → Aussehen → Gespeichert), the same in the sheet and
 * in the first-launch setup. Walked through as people would use it; screenshots go to audit/build101.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "de-rDE-w400dp-h880dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build101FavoriteStudioTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val home = Favorite("h", "Zuhause", Location("Herne Bf", "h", 51.54, 7.22, "VRR", LocationKind.STOP), icon = FavoriteIcon.HOME, accentSeed = 0xFFD32F2FL)
    private val uni = Location("Bochum Ruhr-Universität", "r", 51.44, 7.26, "VRR", LocationKind.STOP)
    private val zoo = Location("Gelsenkirchen, Zoom Erlebniswelt", "z", 51.54, 7.06, "VRR", LocationKind.STOP)
    private val flat = Location("Wohnung am Park", latitude = 51.5, longitude = 7.2)

    private fun capture(name: String) = compose.runOnIdle {
        val view = WindowInspector.getGlobalWindowViews().lastOrNull { it.isShown } ?: compose.activity.window.decorView
        val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(image))
        val file = File("../audit/build101/$name.png"); file.parentFile!!.mkdirs()
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
    }

    // ---- the draft keeps what a person chose ---------------------------------------------------------

    @Test fun aRoleTappedFirstNamesThePlaceAndATypedNameIsNeverReplaced() {
        val role = FavoriteDraft().withIcon(FavoriteIcon.WORK).withPlace(zoo)
        assertEquals("Arbeit", role.label); assertEquals(FavoriteIcon.WORK, role.icon); assertTrue(role.ready)
        // Place first: name and symbol are suggested from the place.
        val place = FavoriteDraft().withPlace(uni)
        assertEquals("Bochum Ruhr-Universität", place.label); assertEquals(FavoriteIcon.SCHOOL, place.icon)
        // A typed name survives a new place and a new symbol.
        val typed = place.withName("Mein Campus").withPlace(zoo).withIcon(FavoriteIcon.FRIENDS)
        assertEquals("Mein Campus", typed.label); assertEquals(zoo, typed.location)
        // Letting go of a tapped role brings the place's own suggestion back.
        val released = FavoriteDraft().withIcon(FavoriteIcon.HOME).withPlace(uni).withIcon(null)
        assertEquals("Bochum Ruhr-Universität", released.label); assertEquals(FavoriteIcon.SCHOOL, released.icon); assertFalse(released.iconChosen)
        assertFalse(FavoriteDraft().withIcon(FavoriteIcon.HOME).ready)
    }

    @Test fun rolesThatAreTakenMoveToTheEnd() {
        val roles = favoriteRoleChoices(listOf(home))
        assertEquals(FavoriteIcon.WORK, roles.first()); assertEquals(FavoriteIcon.HOME, roles.last())
    }

    // ---- the sheet -----------------------------------------------------------------------------------

    private fun openSheet(saved: MutableList<Favorite>, partner: () -> Favorite? = { null }, connect: (String, String) -> Boolean = { _, _ -> true },
        closed: () -> Unit = {}, initial: Location? = null) = compose.setContent {
        OpenVTheme(AppSettings(appTheme = AppTheme.CONFETTI_STUDIO)) {
            CompositionLocalProvider(LocalFavoritePlaces provides listOf(home)) {
                Surface(Modifier.fillMaxSize()) {
                    FavoriteEditDialog(null, "VRR", { listOf(zoo) }, { saved += it }, closed, initialLocation = initial,
                        suggestions = listOf(FavoritePlaceSuggestion(uni, true)), otherFavorites = listOf(home),
                        partnerFor = { partner() }, onConnect = connect)
                }
            }
        }
    }

    @Test fun placeThenLookThenTheSavedMomentAndStraightOnToTheNext() {
        val saved = mutableListOf<Favorite>()
        var connected = 0
        openSheet(saved, partner = { home.takeIf { saved.isNotEmpty() } }, connect = { _, _ -> connected++; true })
        compose.onNodeWithTag("favorite-step-place").assertExists()
        compose.onNodeWithTag("favorite-save").assertDoesNotExist()
        capture("studio-1-place")
        compose.onNodeWithTag("favorite-suggestion-0").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("favorite-step-look").assertExists()
        compose.onNodeWithTag("favorite-hero-place").assertTextContains("Bochum Ruhr-Universität", substring = true)
        compose.onNodeWithTag("favorite-intent-SCHOOL").assertIsSelected()
        capture("studio-2-look")
        compose.onNodeWithTag("favorite-intent-FRIENDS").performClick()
        compose.onNodeWithTag("favorite-name").assertTextContains("Freunde")
        compose.onNodeWithTag("favorite-save").assertIsEnabled().performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            val favorite = saved.single()
            assertEquals("Freunde", favorite.label); assertEquals(FavoriteIcon.FRIENDS, favorite.icon); assertEquals(uni, favorite.location)
            assertNotNull(favorite.accentSeed); assertNotEquals(home.accentSeed, favorite.accentSeed)
        }
        compose.onNodeWithTag("new-favorite-saved").assertExists()
        compose.onNodeWithText("„Freunde“ ist gespeichert").assertExists()
        capture("studio-3-saved")
        compose.onNodeWithTag("new-favorite-connect").performClick()
        compose.runOnIdle { assertEquals(1, connected) }
        // "Noch einen Favoriten" starts again with the search, nothing of the last one left over.
        compose.onNodeWithTag("new-favorite-add-another").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("favorite-step-place").assertExists()
        compose.onNodeWithTag("new-favorite-saved").assertDoesNotExist()
        assertEquals(1, saved.size)
    }

    @Test fun backReturnsToThePlaceAndKeepsTheChosenRole() {
        openSheet(mutableListOf())
        compose.onNodeWithTag("favorite-intent-WORK").performClick()
        compose.onNodeWithText("Ort für „Arbeit“").assertExists()
        compose.onNodeWithTag("favorite-suggestion-0").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("favorite-name").assertTextContains("Arbeit")
        compose.onNodeWithTag("favorite-studio-back").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("favorite-step-place").assertExists()
        compose.onNodeWithTag("favorite-intent-WORK").assertIsSelected()
    }

    @Test fun aHandedOverPlaceStartsWithItsLookAndAllSymbolsUnfoldInPlace() {
        openSheet(mutableListOf(), initial = flat)
        compose.onNodeWithTag("favorite-step-look").assertExists()
        compose.onNodeWithTag("favorite-studio-back").assertDoesNotExist()
        compose.onNodeWithTag("favorite-icon-BAKERY").assertDoesNotExist()
        compose.onNodeWithTag("favorite-intent-more").performClick()
        compose.waitForIdle()
        // No window opens: the symbols unfold inside the sheet.
        compose.onNodeWithTag("favorite-all-icons").assertExists()
        compose.onNodeWithTag("favorite-icon-BAKERY").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("favorite-intent-BAKERY").assertIsSelected()
        compose.onNodeWithTag("favorite-name").assertTextContains("Wohnung am Park")
    }

    @Test fun theBeatsReadWellInDarkThemes() {
        var theme by mutableStateOf(AppTheme.AMOLED_BLACK)
        var phase by mutableStateOf(0)
        val saved = Favorite("n", "Uni", uni, icon = FavoriteIcon.SCHOOL, accentSeed = 0xFF7E57C2)
        compose.setContent {
            OpenVTheme(AppSettings(appTheme = theme, quietMode = true)) {
                Surface(Modifier.fillMaxSize()) {
                    androidx.compose.foundation.layout.Column(Modifier.padding(24.dp)) {
                        if (phase == 0) dev.ambivalenton.openv.ui.dialogs.FavoriteLookStep(FavoriteDraft(accentSeed = 0xFF7E57C2).withPlace(uni),
                            listOf(home), {}, {}, {}, {}, {}, "favorite")
                        else dev.ambivalenton.openv.ui.dialogs.FavoriteSavedStep(saved, home, false, {}, {}, {})
                    }
                }
            }
        }
        for (dark in listOf(AppTheme.AMOLED_BLACK, AppTheme.SYNTHWAVE)) {
            theme = dark
            phase = 0; compose.waitForIdle()
            compose.onNodeWithTag("favorite-name").assertTextContains("Bochum Ruhr-Universität")
            capture("studio-look-${dark.name.lowercase()}")
            phase = 1; compose.waitForIdle()
            compose.onNodeWithTag("new-favorite-connect").assertExists()
            capture("studio-saved-${dark.name.lowercase()}")
        }
    }

    // ---- the first-launch setup ---------------------------------------------------------------------

    @Test fun theSetupAddsSeveralFavoritesAndNextKeepsAChosenOne() {
        var store by mutableStateOf(StoredState(onboardingDraft = OnboardingDraft(step = 2, settings = AppSettings(appTheme = AppTheme.CONFETTI_STUDIO))))
        compose.setContent { OnboardingWizard(store, SetupPermissions(), { change -> store = store.copy(onboardingDraft = change(store.onboardingDraft!!)); true }, { true }, { listOf(flat, zoo) }) }
        fun choose(text: String, name: String) {
            compose.onNode(hasSetTextAction() and hasText("Adresse oder Haltestelle")).performScrollTo().performTextInput(text)
            compose.mainClock.advanceTimeBy(400); compose.waitForIdle()
            compose.onNodeWithText(name).performScrollTo().performClick(); compose.waitForIdle()
        }
        compose.onNodeWithTag("onboarding-next").assertIsNotEnabled()
        choose("Wohn", "Wohnung am Park")
        compose.onNodeWithTag("onboarding-intent-HOME").performScrollTo().performClick()
        compose.onNodeWithTag("onboarding-add-favorite").performScrollTo().assertIsEnabled().performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf("Zuhause"), store.onboardingDraft!!.favorites.map { it.label }) }
        compose.onNodeWithTag("onboarding-added-0").assertExists()
        // Back at the search; "Weiter" works without a new place now that one is added.
        compose.onNodeWithTag("onboarding-step-place").assertExists()
        compose.onNodeWithTag("onboarding-next").assertIsEnabled()
        choose("Zoom", "Gelsenkirchen, Zoom Erlebniswelt")
        capture("setup-favorites")
        // A chosen place is added by "Weiter" in the same step.
        compose.onNodeWithTag("onboarding-next").performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(3, store.onboardingDraft!!.step)
            assertEquals(listOf("Zuhause", "Zoom Erlebniswelt"), store.onboardingDraft!!.favorites.map { it.label })
        }
    }
}
