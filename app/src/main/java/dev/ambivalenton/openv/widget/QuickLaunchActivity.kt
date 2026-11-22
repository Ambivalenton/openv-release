// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.widget

import dev.ambivalenton.openv.localization.tr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Visible native progress feedback; the destination always opens our own routing activity. */
class QuickLaunchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target =
            intent.getStringExtra("targetId")
                ?: run {
                    finish()
                    return
                }
        setContent {
            OpenVTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(tr("Verbindung wird geöffnet …"))
                    }
                }
            }
        }
        lifecycleScope.launch {
            delay(180)
            startActivity(favoriteIntent(this@QuickLaunchActivity, target))
            finish()
        }
    }
}
