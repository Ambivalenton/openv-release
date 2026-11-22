// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.ui.components

import dev.ambivalenton.openv.ui.components.AppDialog as Dialog

import dev.ambivalenton.openv.localization.tr

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.Location

@Composable
fun InteractiveMapContainer(stop: Location, tileTemplate: String, modifier: Modifier = Modifier) {
    InteractiveMapFrame(stop, modifier) { interactive, markerClick ->
        StationMap(stop, tileTemplate, Modifier.fillMaxSize(), interactive, markerClick)
    }
}

/** A renderer slot also lets UI tests exercise real gesture routing without a GPU map process. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InteractiveMapFrame(
    stop: Location,
    modifier: Modifier = Modifier,
    renderer: @Composable (interactive: Boolean, onMarkerClick: () -> Unit) -> Unit,
) {
    var fullscreen by rememberSaveable(stop.id, stop.name) { mutableStateOf(false) }
    var showMarker by rememberSaveable { mutableStateOf(false) }
    var navigationError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val navigationMode=LocalWalkingNavigationMode.current
    val navigationOrigin=LocalWalkingNavigationOrigin.current
    Box(modifier.fillMaxWidth().height(120.dp)) {
        Box(Modifier.matchParentSize().clip(RoundedCornerShape(24.dp))) {
            if (!fullscreen) renderer(false, {})
            // Taps open the map; drags remain available to the surrounding list.
            Box(
                Modifier.matchParentSize()
                    .clickable(role = Role.Button, onClickLabel = tr("Karte im Vollbild öffnen")) {
                        fullscreen = true
                    }
                    .semantics { contentDescription = tr("Karte bei %1\$s im Vollbild öffnen", stop.name) }
            )
        }
        // Measure the attribution first: both visible surfaces have exactly its width,
        // including when font scale or screen density changes. No measurement/recompose loop.
        Layout(
            modifier = Modifier.align(Alignment.TopEnd).fillMaxHeight().padding(5.dp),
            content = {
                MapAttribution(Modifier.testTag("map-attribution"))
                FloatingActionButton(
                    onClick = {
                        navigationError =
                            openStationNavigation(context,stop,navigationMode,navigationOrigin).exceptionOrNull()?.message
                    },
                    shape = RoundedCornerShape(50),
                    modifier =
                        Modifier.testTag("map-navigation").semantics {
                            contentDescription = "${navigationMode.actionLabel}: ${stop.name}"
                        },
                ) {
                    Text(
                        if(navigationMode==dev.ambivalenton.openv.data.model.WalkingNavigationMode.GOOGLE_LOCATION) tr("Standort öffnen") else tr("Navigation starten"),
                        Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 2,
                    )
                }
            },
        ) { measurables, constraints ->
            val attribution = measurables[0].measure(constraints.copy(minWidth = 0, minHeight = 0))
            val button =
                measurables[1].measure(
                    constraints.copy(
                        minWidth = attribution.width,
                        maxWidth = attribution.width,
                        minHeight = 0,
                        maxHeight = (constraints.maxHeight - attribution.height).coerceAtLeast(0),
                    )
                )
            layout(attribution.width, constraints.maxHeight) {
                button.placeRelative(0, 0)
                attribution.placeRelative(0, constraints.maxHeight - attribution.height)
            }
        }
    }
    navigationError?.let {
        Text(
            it,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (fullscreen)
        Dialog(
            onDismissRequest = { fullscreen = false },
            properties =
                DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                    TopAppBar(
                        title = { FittedText(stop.name) },
                        navigationIcon = {
                            IconButton({ fullscreen = false }) {
                                AppIcon(R.drawable.ic_back, tr("Karte schließen"))
                            }
                        },
                    )
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        renderer(true, { showMarker = !showMarker })
                        if (showMarker)
                            Surface(
                                Modifier.align(Alignment.BottomCenter).padding(20.dp),
                                shape = RoundedCornerShape(24.dp),
                                tonalElevation = 6.dp,
                            ) {
                                Row(
                                    Modifier.padding(18.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    AppIcon(R.drawable.ic_train, null)
                                    FittedText(
                                        stop.name,
                                        Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                }
                            }
                        MapAttribution(Modifier.align(Alignment.BottomEnd).padding(5.dp))
                    }
                }
            }
        }
}

/** OSM tile terms require visible attribution on the map, including its passive preview. */
@Composable
private fun MapAttribution(modifier: Modifier) {
    val uri = LocalUriHandler.current
    OutlinedMapAttribution(
        modifier
            .clip(RoundedCornerShape(50))
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = .94f),
                RoundedCornerShape(50),
            )
            .clickable(role = Role.Button) {
                uri.openUri("https://www.openstreetmap.org/copyright")
            }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelSmall,
        background = MaterialTheme.colorScheme.surface.copy(alpha = .94f),
    )
}
