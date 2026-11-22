// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.ui.components

import dev.ambivalenton.openv.localization.tr

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.graphics.RectF
import android.os.Bundle
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.ambivalenton.openv.OpenVApp
import dev.ambivalenton.openv.data.model.Location
import dev.ambivalenton.openv.data.repository.NetworkClient
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.module.http.HttpRequestUtil
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point

/** Native MapLibre, embedded in Compose. Created only for the expanded, visible station card. */
@Composable
fun StationMap(
    stop: Location,
    tileTemplate: String,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    onMarkerClick: () -> Unit = {},
) {
    if (!stop.hasCoordinates) {
        Text(tr("Für diese Haltestelle sind keine Kartenkoordinaten vorhanden."))
        return
    }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var error by remember(stop, tileTemplate) { mutableStateOf<String?>(null) }
    val resizeListener =
        remember(stop, tileTemplate, interactive) {
            arrayOfNulls<android.view.View.OnLayoutChangeListener>(1)
        }
    val mapView =
        remember(stop, tileTemplate, interactive) {
            MapLibre.getInstance(context)
            val client = (context.applicationContext as OpenVApp).koin.get<OkHttpClient>()
            HttpRequestUtil.setOkHttpClient(
                client
                    .newBuilder()
                    .addInterceptor { chain ->
                        chain.proceed(
                            chain
                                .request()
                                .newBuilder()
                                .header("User-Agent", NetworkClient.userAgent)
                                .build()
                        )
                    }
                    .build()
            )
            HttpRequestUtil.setPrintRequestUrlOnFailure(false)
            MapView(context, MapLibreMapOptions.createFromAttributes(context).textureMode(true))
                .apply {
                    contentDescription =
                        if (interactive) tr("Interaktive Karte bei %1\$s", stop.name) else null
                    if (!interactive)
                        importantForAccessibility =
                            android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                    onCreate(Bundle())
                    addOnDidFailLoadingMapListener {
                        error = tr("Karte konnte nicht geladen werden. Internetverbindung prüfen.")
                    }
                    getMapAsync { map ->
                        map.prefetchZoomDelta = 0
                        map.uiSettings.apply {
                            isScrollGesturesEnabled = interactive
                            isZoomGesturesEnabled = interactive
                            isRotateGesturesEnabled = interactive
                            isTiltGesturesEnabled = interactive
                            isDoubleTapGesturesEnabled = interactive
                            isQuickZoomGesturesEnabled = interactive
                            isCompassEnabled = interactive
                            isAttributionEnabled = false
                            isLogoEnabled = false
                        }
                        if (interactive)
                            map.addOnMapClickListener { location ->
                                val point = map.projection.toScreenLocation(location)
                                val r = 24 * context.resources.displayMetrics.density
                                val selected =
                                    map.queryRenderedFeatures(
                                            RectF(
                                                point.x - r,
                                                point.y - r,
                                                point.x + r,
                                                point.y + r,
                                            ),
                                            "station-dot",
                                        )
                                        .isNotEmpty()
                                if (selected) onMarkerClick()
                                selected
                            }
                        fun focus(width: Int) {
                            map.cameraPosition =
                                CameraPosition.Builder()
                                    .target(LatLng(stop.latitude!!, stop.longitude!!))
                                    .zoom(15.0)
                                    .padding(miniMapCameraPadding(width.toDouble(), interactive))
                                    .build()
                        }
                        focus(width)
                        val listener =
                            android.view.View.OnLayoutChangeListener {
                                view,
                                left,
                                top,
                                right,
                                bottom,
                                oldLeft,
                                oldTop,
                                oldRight,
                                oldBottom ->
                                if (
                                    right - left != oldRight - oldLeft ||
                                        bottom - top != oldBottom - oldTop
                                )
                                    focus(view.width)
                            }
                        resizeListener[0] = listener
                        addOnLayoutChangeListener(listener)
                        map.setStyle(Style.Builder().fromJson(rasterMapStyle(tileTemplate))) { style
                            ->
                            style.addSource(
                                GeoJsonSource(
                                    "station",
                                    Feature.fromGeometry(
                                        Point.fromLngLat(stop.longitude!!, stop.latitude!!)
                                    ),
                                )
                            )
                            style.addLayer(
                                CircleLayer("station-dot", "station")
                                    .withProperties(
                                        circleColor("#006A65"),
                                        circleRadius(9f),
                                        circleStrokeColor("#FFFFFF"),
                                        circleStrokeWidth(3f),
                                    )
                            )
                        }
                    }
                }
        }
    DisposableEffect(mapView, lifecycle) {
        var started = false
        var resumed = false
        fun synchronize() {
            val wantStart = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            val wantResume = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (resumed && !wantResume) {
                mapView.onPause()
                resumed = false
            }
            if (started && !wantStart) {
                mapView.onStop()
                started = false
            }
            if (wantStart && !started) {
                mapView.onStart()
                started = true
            }
            if (wantResume && !resumed) {
                mapView.onResume()
                resumed = true
            }
        }
        val observer = LifecycleEventObserver { _, _ -> synchronize() }
        val callbacks =
            object : ComponentCallbacks {
                override fun onConfigurationChanged(newConfig: Configuration) = Unit

                @Suppress("OVERRIDE_DEPRECATION") override fun onLowMemory() = mapView.onLowMemory()
            }
        lifecycle.addObserver(observer)
        context.applicationContext.registerComponentCallbacks(callbacks)
        synchronize()
        onDispose {
            lifecycle.removeObserver(observer)
            context.applicationContext.unregisterComponentCallbacks(callbacks)
            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            resizeListener[0]?.let(mapView::removeOnLayoutChangeListener)
            resizeListener[0] = null
            mapView.onDestroy()
        }
    }
    Box(modifier) {
        AndroidView(
            factory = {
                MapTouchGate(context, interactive).apply {
                    addView(
                        mapView,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT,
                        ),
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        error?.let {
            Text(
                it,
                Modifier.padding(12.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

internal fun rasterMapStyle(template: String): String {
    require(template.startsWith("https://") && listOf("{z}", "{x}", "{y}").all { it in template })
    return buildJsonObject {
            put("version", 8)
            putJsonObject("sources") {
                putJsonObject("osm") {
                    put("type", "raster")
                    putJsonArray("tiles") { add(template) }
                    put("tileSize", 256)
                    put("maxzoom", 19)
                    put("attribution", tr("© OpenStreetMap contributors"))
                }
            }
            putJsonArray("layers") {
                addJsonObject {
                    put("id", "osm-raster")
                    put("type", "raster")
                    put("source", "osm")
                }
            }
        }
        .toString()
}

/**
 * Reject passive touches before Android's MapView ever sees them. The Compose parent can scroll.
 */
internal class MapTouchGate(context: Context, private val interactive: Boolean) :
    FrameLayout(context) {
    override fun dispatchTouchEvent(event: MotionEvent): Boolean =
        interactive && super.dispatchTouchEvent(event)
}

/** Insets put the mini-map target at x=27.5%, y=50%; fullscreen maps keep a centered camera. */
internal fun miniMapCameraPadding(width: Double, interactive: Boolean): DoubleArray =
    doubleArrayOf(0.0, 0.0, if (interactive) 0.0 else width.coerceAtLeast(0.0) * .45, 0.0)
