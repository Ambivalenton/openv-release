// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import dev.ambivalenton.openv.data.model.reduceMotion

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner

/** General loading remains with the dotted indicators; this circle belongs only to a pull. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JourneyPullRefresh(
    busy: Boolean,
    onRefresh: () -> Unit,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val controller = remember(scope) { ManualPullRefreshController(scope) }
    val confirmation by controller.confirmationVisible.collectAsState()
    val refresh by rememberUpdatedState(onRefresh)
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val active = enabled && lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(active) { if (!active) controller.cancel() }
    // M15: the confirmation ends with the request it started, not after a fixed time.
    LaunchedEffect(busy) { controller.loadingChanged(busy) }
    DisposableEffect(controller) { onDispose { controller.cancel() } }

    PullToRefreshBox(
        isRefreshing = active && confirmation,
        onRefresh = { if (controller.confirmGesture(blocked = busy || !active)) refresh() },
        modifier = Modifier.fillMaxSize(),
        indicator = {
            // No AnimatedVisibility or exit transition: the slot disappears when the request has ended.
            if (active && confirmation) {
                Surface(
                    Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
                        .testTag("manual-pull-confirmation"),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 3.dp,
                ) {
                    Box(Modifier.padding(8.dp)) {
                        if(dev.ambivalenton.openv.ui.theme.LocalAppSettings.current.reduceMotion) CalmLoadingRail(Modifier.width(36.dp)) else CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            }
        },
        content = content,
    )
}
