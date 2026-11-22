// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.ambivalenton.openv.ui.screens.BoardLocationControls
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import dev.ambivalenton.openv.data.model.AppSettings
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=OpenVApp::class,qualifiers="de-rDE-w411dp-h891dp-mdpi")
class AppPublicationUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test
    fun widgetsNeverRequestBackgroundLocationInTheStoreEdition() {
        val app = RuntimeEnvironment.getApplication() as OpenVApp
        @Suppress("DEPRECATION")
        val requested = app.packageManager.getPackageInfo(app.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty()
        assertFalse(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION in requested)
        var changed = false
        compose.setContent { OpenVTheme { BoardLocationControls(AppSettings()) { changed = true } } }
        compose.onNodeWithText("Standortwechsel automatisch erkennen").assertDoesNotExist()
        compose.runOnIdle { assertFalse(changed) }
    }

}
