// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import kotlinx.serialization.Serializable

/*
 * Legacy only (build 58–65). Favourite pairs no longer exist in the app: build 66 converts every
 * stored pair once into a network line with the same ID (migrateToNetwork). These declarations
 * remain solely to read old data stores and backups.
 */
@Serializable data class FavoritePair(
    val id:String=newId(),val firstId:String,val secondId:String,
    val rule:HighlightRule?=null,val hiddenRules:List<HighlightRule> = emptyList(),
)

private fun StoredState.pairForFavorite(id:String)=favoritePairs.firstOrNull {it.firstId==id || it.secondId==id}

fun FavoritePair.validIn(favorites:List<Favorite>):Boolean = id.isNotBlank() && firstId!=secondId &&
    favorites.any {it.id==firstId} && favorites.any {it.id==secondId} &&
    !sameLocation(favorites.first {it.id==firstId}.location,favorites.first {it.id==secondId}.location) && hiddenRules.size<=8 &&
    (listOfNotNull(rule)+hiddenRules).all {it.days.isNotEmpty() && it.days.all {d->d in 1..7} && it.fromMinute in 0..1439 && it.untilMinute in 0..1439}

/** Legacy step of the build-57 migration below; the resulting pair becomes a network line. */
private fun StoredState.withLegacyPair(firstId:String,secondId:String):StoredState {
    if(firstId==secondId || pairForFavorite(firstId)!=null || pairForFavorite(secondId)!=null)return this
    val first=favorites.firstOrNull {it.id==firstId} ?: return this
    val second=favorites.firstOrNull {it.id==secondId} ?: return this
    if(!first.location.routable || !second.location.routable || sameLocation(first.location,second.location))return this
    return copy(favoritePairs=favoritePairs+FavoritePair(firstId=firstId,secondId=secondId,
        rule=second.rule ?: first.rule,hiddenRules=second.hiddenRules.ifEmpty {first.hiddenRules}))
}

/** Older versions kept the second endpoint in a favorite. Convert once without losing an endpoint,
 * IDs, individual schedules or filters. Deterministic IDs make migration idempotent. */
fun StoredState.migrateFavoritePairs():StoredState {
    if(favorites.none {it.counterpole!=null})return this
    var result=this
    favorites.filter {it.counterpole!=null}.forEach {old->
        val other=old.counterpole!!
        if(result.pairForFavorite(old.id)!=null) {
            result=result.copy(favorites=result.favorites.map {if(it.id==old.id)it.copy(counterpole=null)else it})
            return@forEach
        }
        val match=result.favorites.firstOrNull {it.id!=old.id && result.pairForFavorite(it.id)==null && !sameLocation(it.location,old.location) &&
            ((!other.id.isNullOrBlank() && it.location.id==other.id && it.location.provider==other.provider) ||
                it.location.name.equals(other.name,true) && sameLocation(it.location,other))}
        val baseId="migrated-pair-${old.id}"
        var counterpartId=baseId;var suffix=2
        while(result.favorites.any {it.id==counterpartId} || result.favoritePairs.any {it.id==counterpartId}) {counterpartId="$baseId-${suffix++}"}
        val counterpart=match ?: Favorite(id=counterpartId,label=other.name,location=other,
            icon=old.icon,shape=old.shape,accentSeed=old.accentSeed,transportFilter=old.transportFilter)
        if(match==null)result=result.copy(favorites=result.favorites+counterpart)
        result=result.copy(favorites=result.favorites.map {if(it.id==old.id)it.copy(counterpole=null)else it})
            .withLegacyPair(old.id,counterpart.id)
    }
    return result
}
