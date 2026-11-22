// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.staticCompositionLocalOf
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import java.util.Locale

val LocalWalkingNavigationMode=staticCompositionLocalOf {WalkingNavigationMode.OPENSTREETMAP}
val LocalWalkingNavigationOrigin=staticCompositionLocalOf<Location?> {null}

data class WalkingNavigationPlan(val uri: Uri,val preferredPackage: String?=null,val fallback: Uri?=null)

/** One provider policy for station cards and every walking leg; no map SDK or new location query. */
object WalkingNavigationManager {
    private fun coordinate(place: Location)=String.format(Locale.ROOT,"%.7f,%.7f",place.latitude,place.longitude)
    private fun endpoint(place: Location)=if(place.hasCoordinates) coordinate(place) else place.name.trim().take(512)
    fun plan(mode: WalkingNavigationMode,origin: Location?,destination: Location): WalkingNavigationPlan {
        require(destination.hasCoordinates || destination.name.isNotBlank()) {tr("Für dieses Ziel fehlen Ortsangaben.")}
        val target=endpoint(destination)
        return when(mode) {
            WalkingNavigationMode.OPENSTREETMAP->{
                val builder=Uri.parse("https://www.openstreetmap.org/directions").buildUpon()
                    .appendQueryParameter("engine","fossgis_valhalla_foot")
                if(origin?.hasCoordinates==true && destination.hasCoordinates)
                    builder.appendQueryParameter("route","${coordinate(origin)};${coordinate(destination)}")
                else {
                    origin?.let {builder.appendQueryParameter("from",endpoint(it))}
                    builder.appendQueryParameter("to",target)
                }
                WalkingNavigationPlan(builder.build())
            }
            WalkingNavigationMode.GOOGLE_NAVIGATION->{
                val primary=Uri.parse("google.navigation:q=${Uri.encode(target)}&mode=w")
                val fallback=Uri.parse("https://www.google.com/maps/dir/").buildUpon()
                    .appendQueryParameter("api","1").appendQueryParameter("destination",target)
                    .appendQueryParameter("travelmode","walking").appendQueryParameter("dir_action","navigate").build()
                WalkingNavigationPlan(primary,"com.google.android.apps.maps",fallback)
            }
            WalkingNavigationMode.GOOGLE_LOCATION->{
                val uri=Uri.parse("https://www.google.com/maps/dir/").buildUpon()
                    .appendQueryParameter("api","1").appendQueryParameter("destination",target)
                    .appendQueryParameter("travelmode","walking").apply{
                        if(origin?.hasCoordinates==true)appendQueryParameter("origin",coordinate(origin))
                    }.build()
                WalkingNavigationPlan(uri,"com.google.android.apps.maps",uri)
            }
        }
    }
    fun open(context: Context,mode: WalkingNavigationMode,origin: Location?,destination: Location): Result<Unit> = runCatching {
        val plan=plan(mode,origin,destination)
        fun launch(uri: Uri,pkg: String?) {
            val intent=Intent(Intent.ACTION_VIEW,uri).apply {
                pkg?.let {setPackage(it)}
                if(context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
        try {launch(plan.uri,plan.preferredPackage)}
        catch (_: ActivityNotFoundException) {
            val fallback=plan.fallback ?: throw IllegalStateException(tr("Bitte eine Karten-App oder einen Browser installieren."))
            try {launch(fallback,null)} catch (_:ActivityNotFoundException) {
                throw IllegalStateException(tr("Bitte eine Karten-App oder einen Browser installieren."))
            }
        }
    }
}

fun stationNavigationUri(stop: Location,mode: WalkingNavigationMode=WalkingNavigationMode.OPENSTREETMAP,origin: Location?=null): Uri =
    WalkingNavigationManager.plan(mode,origin,stop).uri
fun walkingNavigationUri(segment: TripSegment,mode: WalkingNavigationMode=WalkingNavigationMode.OPENSTREETMAP): Uri {
    require(segment.isWalk)
    return WalkingNavigationManager.plan(mode,segment.origin,segment.destination).uri
}
fun openStationNavigation(context: Context,stop: Location,mode: WalkingNavigationMode=WalkingNavigationMode.OPENSTREETMAP,origin: Location?=null): Result<Unit> =
    WalkingNavigationManager.open(context,mode,origin,stop)
fun openWalkingNavigation(context: Context,segment: TripSegment,mode: WalkingNavigationMode=WalkingNavigationMode.OPENSTREETMAP): Result<Unit> = runCatching {
    require(segment.isWalk)
    WalkingNavigationManager.open(context,mode,segment.origin,segment.destination).getOrThrow()
}
