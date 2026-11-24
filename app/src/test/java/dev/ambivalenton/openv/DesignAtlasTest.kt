// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.rendering.BoardRenderer
import dev.ambivalenton.openv.rendering.boardPresentation
import dev.ambivalenton.openv.ui.components.AppThemeThumbnail
import dev.ambivalenton.openv.ui.components.TimelineDesignPreview
import dev.ambivalenton.openv.ui.network.NetworkPlanDesignPreview
import dev.ambivalenton.openv.ui.theme.AppTypefaces
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Design atlas: every app design as one strip – app preview, "App-Theme" board, "Display" board, bar
 * chart and network plan – one sheet per collection, for reviewing the catalogue side by side. Runs only
 * on request (`OPENV_ATLAS=all` or a comma-separated list of group names); writes to `../audit/atlas`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "de-rDE-w1300dp-h2600dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DesignAtlasTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val clock = System.currentTimeMillis()
    private val stop = Location("Herne Bf", "h")
    private val rows = listOf(
        Departure("a", stop, "42", LineType.BUS, "Zentrum", clock + 180_000, clock + 180_000, DelayInfo(departureSeconds = 0), "2"),
        Departure("b", stop, "U35", LineType.SUBWAY, "Bochum Hustadt", clock + 360_000, clock + 480_000, DelayInfo(departureSeconds = 120), "1"),
        Departure("c", stop, "RE3", LineType.REGIONAL, "Düsseldorf Hbf", clock + 540_000, clock + 540_000, DelayInfo(), "4"),
        Departure("d", stop, "S2", LineType.SUBURBAN, "Dortmund Hbf", clock + 720_000, clock + 720_000, DelayInfo(departureSeconds = 0), "5"),
    )

    @Composable private fun Board(settings: AppSettings, design: BoardDesign, width: Float) = OpenVTheme(settings) {
        val context = LocalContext.current
        val state = StoredState(settings = settings.copy(board = settings.board.copy(design = design)))
        val live = boardPresentation(state, MaterialTheme.colorScheme, inApp = true, now = clock,
            themeTypeface = AppTypefaces.get(context, settings.appTheme?.bodyFace))
        val model = live.copy(title = "Herne Bf", caption = "Abfahrten", rows = rows)
        val bitmap = remember(model) { BoardRenderer.render(model, width, 164f, 1f, density = 1f) }
        Image(bitmap.asImageBitmap(), null, Modifier.width(width.dp).height(164.dp), contentScale = ContentScale.FillBounds)
    }

    @Composable private fun Strip(theme: AppTheme) {
        val settings = AppSettings(appTheme = theme, timelineTheme = TimelineTheme.APP_THEME, networkPlanDesign = NetworkPlanDesign.APP_THEME)
        Row(Modifier.height(176.dp).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(150.dp)) {
                Text(theme.name, fontSize = 9.sp, color = Color.Black)
                Text(theme.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black, maxLines = 2)
                Text(theme.description, fontSize = 9.sp, color = Color.DarkGray, maxLines = 4)
                Text(listOfNotNull(theme.language.name, theme.headFont, theme.bodyFont, theme.display?.name).joinToString(" · "), fontSize = 8.sp, color = Color.Gray, maxLines = 3)
            }
            Box(Modifier.width(200.dp)) { AppThemeThumbnail(settings) }
            Board(settings, BoardDesign.APP_INTEGRATED, 250f)
            Board(settings, BoardDesign.APP_THEME, 250f)
            Box(Modifier.width(230.dp)) { TimelineDesignPreview(settings) }
            OpenVTheme(settings) {
                Box(Modifier.width(220.dp).background(MaterialTheme.colorScheme.background)) {
                    NetworkPlanDesignPreview(NetworkPlanDesign.APP_THEME, vehicles = false, animate = false)
                }
            }
        }
    }

    @Test fun atlas() {
        val wanted = System.getenv("OPENV_ATLAS")
        assumeTrue("set OPENV_ATLAS=all or group names", !wanted.isNullOrBlank())
        val groups = groupedAppThemes().filter { (group, _) -> wanted == "all" || group.name in wanted!!.split(',') }
        var page by mutableStateOf<Pair<String, List<AppTheme>>?>(null)
        compose.setContent {
            page?.let { (title, themes) ->
                Column(Modifier.fillMaxSize().background(Color(0xFFE9E9EC)).padding(6.dp)) {
                    Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    themes.forEach { key(it) { Strip(it) } }
                }
            }
        }
        groups.forEach { (group, themes) ->
            themes.chunked(12).forEachIndexed { index, chunk ->
                page = "${group.name} · ${group.title} (${themes.size})" to chunk
                compose.waitForIdle()
                compose.runOnIdle {
                    val v = compose.activity.window.decorView
                    val height = (40 + chunk.size * 176 + 20).coerceAtMost(v.height)
                    val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(android.graphics.Canvas(it)) }
                    val out = Bitmap.createBitmap(b, 0, 0, b.width, height)
                    val f = File("../audit/atlas/${group.ordinal.toString().padStart(2, '0')}-${group.name}-$index.png"); f.parentFile!!.mkdirs()
                    f.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    b.recycle()
                }
            }
        }
    }
}
