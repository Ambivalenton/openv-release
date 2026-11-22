// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.network

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.JourneyPositionSource
import dev.ambivalenton.openv.data.model.PinnedNetworkJourney
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.components.AppIcon
import dev.ambivalenton.openv.rendering.iconResource
import kotlin.math.roundToInt

internal data class PinnedVehiclePlacement(val point:Offset,val heading:Offset)

/** Shared geometry for drawing and for keeping departure chips clear of the actual vehicle. */
internal fun pinnedVehiclePlacement(journey:PinnedNetworkJourney,track:PathMeasure,nodeRadius:Float,width:Float):PinnedVehiclePlacement? {
    val fraction=journey.fraction ?: return null
    if(track.length<=0f) return null
    val inset=minOf(track.length/2,nodeRadius+width*1.5f)
    val along=if(journey.forward) fraction else 1f-fraction
    val distance=inset+(track.length-2*inset)*along
    val point=track.getPosition(distance)
    val tangent=track.getTangent(distance)
    if(!point.x.isFinite() || !point.y.isFinite() || !tangent.x.isFinite() || !tangent.y.isFinite()) return null
    return PinnedVehiclePlacement(point,if(journey.forward)tangent else Offset(-tangent.x,-tangent.y))
}

/** One persistent vehicle, placed by the assistant rather than the decoration's animation clock. */
@Composable
internal fun PinnedNetworkVehicle(journey: PinnedNetworkJourney, track: PathMeasure, nodeRadius: Float,
                                  width: Float, accent: Color, modifier: Modifier = Modifier) {
    val source = if (journey.fraction == null && journey.source != JourneyPositionSource.GPS_UNMATCHED)
        tr("Position im Netzplan noch nicht zugeordnet") else when (journey.source) {
        JourneyPositionSource.GPS -> tr("Gemessener Standort")
        JourneyPositionSource.GPS_LAST_KNOWN -> tr("Letzter bekannter Standort")
        JourneyPositionSource.TIMETABLE -> if (journey.gpsPending) tr("Position nach Fahrplan, GPS wird zugeordnet") else tr("Position nach Fahrplan")
        JourneyPositionSource.GPS_UNMATCHED -> tr("GPS-Standort wird zugeordnet")
    }
    val label = tr("%1\$s · %2\$s", journey.lineName.ifBlank { journey.lineType.label }, source)
    val fraction = journey.fraction
    if (fraction == null) {
        Box(modifier.testTag("network-pinned-unknown").semantics { contentDescription = label })
        return
    }
    val placement=pinnedVehiclePlacement(journey,track,nodeRadius,width) ?: return
    val point=placement.point
    val heading=placement.heading
    val scheme = MaterialTheme.colorScheme
    val semantics = modifier.testTag("network-pinned-vehicle").semantics {
        contentDescription = label
        progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
    }
    val kind = vehicleKind(journey.lineType)
    if (kind != null) Canvas(semantics) {
        // A single compact body stays visible on short lines, including while waiting to board.
        val car = VehicleCar(if (kind == VehicleKind.TRAIN) 2.3f else 1.9f, true, true, false)
        drawVehicleCar(kind, car, 0, point, heading, width,
            if (journey.source == JourneyPositionSource.GPS) 1f else .8f, scheme.surface, scheme.onSurface, accent)
    } else Box(semantics) {
        val diameter = with(LocalDensity.current) { (width * 2.4f).toDp().coerceAtLeast(18.dp) }
        val radius = with(LocalDensity.current) { diameter.toPx() / 2 }
        // Walking, ferry and unknown modes keep their own symbol instead of pretending to be a train.
        AppIcon(journey.lineType.iconResource(), null,
            Modifier.offset { IntOffset((point.x - radius).roundToInt(), (point.y - radius).roundToInt()) }
                .size(diameter).background(scheme.surface, CircleShape).padding(2.dp), tint = accent)
    }
}
