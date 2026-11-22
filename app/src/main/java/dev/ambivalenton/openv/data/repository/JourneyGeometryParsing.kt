// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.repository

import dev.ambivalenton.openv.data.model.*
import kotlinx.serialization.json.*

/** EFA's existing WGS84 response uses longitude,latitude pairs (format x,y). */
internal fun efaJourneyShape(leg:JsonElement):List<RoutePoint> {
    val objectValue=leg as? JsonObject ?: return emptyList()
    if((objectValue["format"] as? JsonPrimitive)?.content!="x,y")return emptyList()
    val raw=(objectValue["path"] as? JsonPrimitive)?.content?.takeIf {it.length<=300_000} ?: return emptyList()
    val points=raw.trim().split(Regex("\\s+")).takeIf {it.size in 2..15_000} ?: return emptyList()
    return boundedRouteShape(points.map {token->
        val pair=token.split(',');if(pair.size!=2)return emptyList()
        RoutePoint(pair[1].toDoubleOrNull() ?: return emptyList(),pair[0].toDoubleOrNull() ?: return emptyList())
    })
}

/** transport.rest/hafas-client returns a GeoJSON feature collection of ordered points. */
internal fun restJourneyShape(leg:JsonElement):List<RoutePoint> {
    val polyline=(leg as? JsonObject)?.get("polyline") as? JsonObject ?: return emptyList()
    val features=polyline["features"] as? JsonArray ?: return emptyList()
    if(features.size !in 2..15_000)return emptyList()
    return boundedRouteShape(features.map {feature->
        val geometry=(feature as? JsonObject)?.get("geometry") as? JsonObject ?: return emptyList()
        if((geometry["type"] as? JsonPrimitive)?.content!="Point")return emptyList()
        val coords=geometry["coordinates"] as? JsonArray ?: return emptyList()
        if(coords.size<2)return emptyList()
        RoutePoint((coords[1] as? JsonPrimitive)?.doubleOrNull ?: return emptyList(),(coords[0] as? JsonPrimitive)?.doubleOrNull ?: return emptyList())
    })
}
