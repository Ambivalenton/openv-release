// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

/** Rows of the gesture-free list in the network editor: every favourite and every line. */
sealed interface FavoriteManagementEntry {
    val id: String
    data class Single(val favorite: Favorite) : FavoriteManagementEntry {
        override val id get() = favorite.id
    }
    data class Line(val edge: NetworkEdge, val a: Favorite, val b: Favorite) : FavoriteManagementEntry {
        override val id get() = edge.id
    }
}

/** Favourites nearest first (unknown distances last, ties keep the saved order), then all lines
 * in their layout order. No lookup is needed. */
fun StoredState.favoriteManagementEntries(position: Location? = routingPosition()): List<FavoriteManagementEntry> {
    val byId = favorites.associateBy { it.id }
    val singles = favorites.sortedBy { if (position == null) Double.MAX_VALUE else distanceMeters(position, it.location) ?: Double.MAX_VALUE }
        .map { FavoriteManagementEntry.Single(it) }
    val lines = network.edges.mapNotNull { e ->
        val a = byId[e.aId] ?: return@mapNotNull null
        val b = byId[e.bId] ?: return@mapNotNull null
        FavoriteManagementEntry.Line(e, a, b)
    }
    return singles + lines
}

/** "Zuhause ↔ Arbeit", "Zuhause → Arbeit" or "Arbeit → Zuhause", as in the direction menu. */
fun FavoriteManagementEntry.Line.title(): String = when (edge.direction) {
    DirectionMode.BOTH -> "${a.label} ↔ ${b.label}"
    DirectionMode.A_TO_B -> "${a.label} → ${b.label}"
    DirectionMode.B_TO_A -> "${b.label} → ${a.label}"
}

/** Imported/legacy repeated IDs must not discard a separately saved favorite. Keep
 * the first identity (including pair/widget references), and re-identify only copies. */
internal fun StoredState.withUniqueFavoriteIds(): StoredState {
    val used = (favoritePairs.map { it.id } + network.edges.map { it.id }).toMutableSet()
    val reserved = favorites.map { it.id }.toSet() + used
    var changed = false
    val unique = favorites.mapIndexed { index, favorite ->
        if (favorite.id.isNotBlank() && used.add(favorite.id)) favorite
        else {
            var id: String
            var salt = 0
            do {
                id = java.util.UUID.nameUUIDFromBytes(
                    "openv-favorite-instance:${favorite.id.length}:${favorite.id}:$index:${salt++}".toByteArray(Charsets.UTF_8),
                ).toString()
            } while (id in reserved || !used.add(id))
            changed = true
            favorite.copy(id = id)
        }
    }
    return if (changed) copy(favorites = unique) else this
}
