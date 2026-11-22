// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.data.repository

import kotlinx.serialization.json.*

/** Legacy enum spellings are read only here; the active design catalog contains Soft/Glas. */
internal fun migrateDesignOptions(root: JsonElement): JsonElement {
    val state = root as? JsonObject ?: return root
    val settings = state["settings"] as? JsonObject ?: return root
    val widget = settings["widget"] as? JsonObject ?: return root
    val design = widget["design"] as? JsonObject ?: return root
    val migrated = design.toMutableMap()
    if ((design["surface"] as? JsonPrimitive)?.content in setOf("MATERIAL", "NONE"))
        migrated["surface"] = JsonPrimitive("SOFT")
    for (key in listOf("containerElevation", "buttonElevation")) {
        val old = (design[key] as? JsonPrimitive)?.content
        val number =
            when (old) {
                "NONE" -> 0f
                "LOW" -> 2f
                "MEDIUM" -> 6f
                "HIGH" -> 10f
                else -> null
            }
        if (number != null) migrated[key] = JsonPrimitive(number)
    }
    val updatedWidget =
        widget.toMutableMap().apply {
            put("design", JsonObject(migrated))
            if ((design["surface"] as? JsonPrimitive)?.content == "NONE")
                put("opacity", JsonPrimitive(0))
        }
    return JsonObject(
        state + ("settings" to JsonObject(settings + ("widget" to JsonObject(updatedWidget))))
    )
}

/** Preserve the last global icon shape on each old favorite, once, before individual editing. */
internal fun migrateFavoriteShapes(root: JsonElement): JsonElement {
    val state = root as? JsonObject ?: return root
    val settings = state["settings"] as? JsonObject ?: JsonObject(emptyMap())
    val widget = settings["widget"] as? JsonObject ?: JsonObject(emptyMap())
    val oldShape = (widget["iconStyle"] as? JsonPrimitive)?.content
        ?.takeIf { name -> dev.ambivalenton.openv.data.model.IconStyle.entries.any { it.name == name } }
        ?: "SQUIRCLE"
    val favorites = (state["favorites"] as? JsonArray)?.map { item ->
        val f = item as? JsonObject
        if (f != null && "shape" !in f) JsonObject(f + ("shape" to JsonPrimitive(oldShape))) else item
    }
    val radius = (widget["cornerRadius"] as? JsonPrimitive)?.intOrNull ?: 28
    val updated = widget.toMutableMap().apply {
        put("showSettings", JsonPrimitive(false))
        if ("containerShape" !in widget) put("containerShape", JsonPrimitive(when {
            radius == 0 -> "RECTANGLE"
            radius >= 48 -> "ROUND"
            else -> "ROUNDED"
        }))
    }
    return JsonObject(state + ("settings" to JsonObject(settings + ("widget" to JsonObject(updated)))) +
        (favorites?.let { mapOf("favorites" to JsonArray(it)) } ?: emptyMap()))
}
