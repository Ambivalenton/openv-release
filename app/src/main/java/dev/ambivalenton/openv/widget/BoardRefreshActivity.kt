// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.widget

import dev.ambivalenton.openv.localization.tr

import android.Manifest
import androidx.core.net.toUri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import dev.ambivalenton.openv.OpenVApp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.DepartureBoardRepository
import dev.ambivalenton.openv.data.repository.boardRefreshUsesLocation
import androidx.compose.foundation.verticalScroll
import dev.ambivalenton.openv.ui.components.ShimmerLoadingBar
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/** A user-initiated, visible refresh gives GPS access without continuous background tracking. */
class BoardRefreshActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as OpenVApp
        lifecycleScope.launch {
            if(!app.deviceLocation.hasPermission() || !app.deviceLocation.locationEnabled()) {
                app.locations.activateManualOrigin()
                val state=app.locations.state.first()
                // M10: without location the nearest stop cannot be determined; the widget explains
                // that and offers a fixed stop, which needs no location at all.
                if(boardRefreshUsesLocation(state)) {
                    showMissingLocation(app, permissionMissing = !app.deviceLocation.hasPermission())
                    return@launch
                }
            }
            val tracker=app.koin.get<dev.ambivalenton.openv.data.repository.PinnedJourneyController>()
            tracker.restore()
            if (tracker.focusIsOpen) {
                startActivity(android.content.Intent(this@BoardRefreshActivity,dev.ambivalenton.openv.MainActivity::class.java)
                    .setData("openv://overview".toUri()))
                finish()
            } else showRefreshUi(app)
        }
    }

    /** M10: opens the departures' stop sheet in the app; the fixed stop applies to app and widget. */
    private fun chooseFixedStop() {
        startActivity(android.content.Intent(this, dev.ambivalenton.openv.MainActivity::class.java)
            .setData("openv://departures/fixed".toUri())
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or
                android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }

    /** M10: the location is missing for the automatic choice: say so and offer the ways that work. */
    private fun showMissingLocation(app: OpenVApp, permissionMissing: Boolean) {
        setContent {
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
                if (result.values.any { it } && app.deviceLocation.locationEnabled()) showRefreshUi(app)
            }
            OpenVTheme {
                AlertDialog(
                    onDismissRequest = { finish() },
                    title = { Text(tr("Standort fehlt")) },
                    text = {
                        Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(tr("Für die nächstgelegene Haltestelle aus deiner Liste braucht die Tafel deinen Standort. Eine feste Haltestelle zeigt Abfahrten auch ohne Standort; sie gilt für die Tafel in der App und im Widget."))
                            Button({ chooseFixedStop() }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                Text(tr("Feste Haltestelle für dieses Widget wählen"))
                            }
                            if (permissionMissing)
                                OutlinedButton({ launcher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)) },
                                    Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(tr("Standort freigeben")) }
                            else
                                OutlinedButton({
                                    runCatching { startActivity(android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                                    finish()
                                }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(tr("Standort in Android aktivieren")) }
                        }
                    },
                    confirmButton = { TextButton({ finish() }) { Text(tr("Schließen")) } },
                )
            }
        }
    }

    private fun showRefreshUi(app: OpenVApp) {
        setContent {
            val initial = remember { app.locations.state }
            val saved by initial.collectAsState(initial = null)
            // M10: the same rule as the widget's label: a fixed stop or a manual start needs no GPS.
            val fixedStop = saved?.let { !boardRefreshUsesLocation(it) } == true
            var granted by remember { mutableStateOf(app.deviceLocation.hasPermission()) }
            var permissionAsked by remember { mutableStateOf(false) }
            var error by remember { mutableStateOf<String?>(null) }
            var finished by remember { mutableStateOf(false) }
            val launcher =
                rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { result ->
                    granted = result.values.any { it }
                    if (!granted)
                        error = tr("Für die nächstgelegene Haltestelle bitte den Standort freigeben.")
                }
            LaunchedEffect(granted, fixedStop, saved != null) {
                if (saved == null) return@LaunchedEffect
                if (!granted && !fixedStop && !permissionAsked) {
                    permissionAsked = true
                    launcher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                        )
                    )
                } else if ((granted || fixedStop) && !finished) {
                    lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
                    try {
                        if (!fixedStop) {
                            app.deviceLocation.current()
                            val presence =
                                requireNotNull(app.deviceLocation.lastPresence) {
                                    tr("Keine ausreichend bestimmte Position erhalten.")
                                }
                            app.locations.recordPresence(presence)
                        }
                        app.koin.get<DepartureBoardRepository>().refresh(force = true)
                        DepartureBoardWidget().updateAll(this@BoardRefreshActivity)
                        AppQuickWidget().updateAll(this@BoardRefreshActivity)
                        val board = app.locations.state.first().departureBoard
                        if (board.error == null) {
                            finished = true
                            finish()
                        } else error = board.error
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        error = e.message ?: tr("Bitte den Standort in Android aktivieren.")
                    }
                }
            }
            OpenVTheme {
                AlertDialog(
                    onDismissRequest = { finish() },
                    title = { Text(tr("Öpen-V · Anzeigetafel")) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (error == null)
                                ShimmerLoadingBar(
                                    Modifier.fillMaxWidth().height(4.dp),
                                    tr("Standort und Abfahrten laden"),
                                )
                            Text(error ?: if (fixedStop) tr("Abfahrten werden geladen …") else tr("Standort ermitteln und Abfahrten laden …"))
                            if (error != null && !fixedStop)
                                OutlinedButton({ chooseFixedStop() }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                    Text(tr("Feste Haltestelle für dieses Widget wählen"))
                                }
                        }
                    },
                    confirmButton = {
                        if (error != null)
                            TextButton({
                                startActivity(settingsIntent(this@BoardRefreshActivity))
                                finish()
                            }) {
                                Text(tr("Einstellungen"))
                            }
                    },
                    dismissButton = {
                        TextButton({ finish() }) {
                            Text(if (error == null) tr("Abbrechen") else tr("Schließen"))
                        }
                    },
                )
            }
        }
    }
}
