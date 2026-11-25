// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.repository
import dev.ambivalenton.openv.data.model.*
import kotlin.math.abs
import kotlin.math.min
private val streetParts=setOf("st","str","stra","stras","strass","strasse")
private val roadParts=setOf("weg","platz","allee","gasse","ring","damm","ufer","chaussee","steig","pfad")
private val searchFillers=setOf("der","die","das","den","am","an","auf","im","in","von","zum","zur")
internal fun placeWords(value:String):List<String> {
    val raw=searchText(value).replace(Regex("(?<=\\p{L})(?=\\d)")," ").replace(Regex("(?<=\\d)(?=\\p{L}{2})")," ").split(' ').filter {it.isNotBlank()}
    val words=mutableListOf<String>()
    raw.forEachIndexed {i,word->
        val suffix=word in streetParts && (word!="st" || i==raw.lastIndex || raw.getOrNull(i+1)?.firstOrNull()?.isDigit()==true)
        if(words.isNotEmpty() && words.last().length>=3 && (suffix || word in roadParts))words[words.lastIndex]=words.last()+(if(suffix)"strasse"else word)
        // "12 a" and "12a" are the same house number.
        else if(word.length==1 && word[0].isLetter() && words.lastOrNull()?.let {it.length<=4 && it.all(Char::isDigit)}==true)words[words.lastIndex]=words.last()+word
        else words+=when(word){"hbf","hauptbf","hauptbhf"->"hauptbahnhof";"bf","bhf"->"bahnhof";else->if(word.endsWith("str")&&word.length>5)word.dropLast(3)+"strasse"else word}
    };return words
}
/** Splits "Musterstraße 12a Musterstadt" into the query without the house number and the number, or null
 * when there is no house number. A five-digit postcode is never taken for a house number. */
internal fun splitHouseNumber(query:String):Pair<String,String>? {
    val words=placeWords(query)
    val index=words.indexOfFirst {w->w.first().isDigit() && w.length<=5 && !(w.length==5 && w.all(Char::isDigit))}
    if(index<=0)return null
    val rest=words.filterIndexed {i,_->i!=index}
    return if(rest.none {it.any(Char::isLetter)})null else rest.joinToString(" ") to words[index]
}
/** The text as typed, only spaces tidied. Photon matches separate words such as "Hölzer Weg" and
 * understands "Str."/"Str" itself; the joined matching form ("holzerweg") made it miss such streets. */
internal fun photonTypedQuery(query:String):String=query.trim().replace(Regex("\\s+")," ")
private val placeRoadSuffixes=listOf("straße","strasse","weg","platz","allee","gasse","ring","damm","ufer","chaussee")
/** Retry spellings, only used when the typed form found nothing: streets named after a place are
 * written apart ("Hölzer Weg", "Bremer Straße"), most others joined ("Kirchweg", "Rosenstraße"). */
internal fun photonRetryQueries(query:String):List<String> {
    val typed=photonTypedQuery(query)
    val split=typed.split(' ').joinToString(" ") {word->
        val lower=word.lowercase(java.util.Locale.ROOT)
        val suffix=placeRoadSuffixes.firstOrNull {lower.endsWith(it) && lower.length-it.length>=5 && lower.dropLast(it.length).endsWith("er")}
        if(suffix==null) word else word.dropLast(suffix.length)+" "+word.takeLast(suffix.length).replaceFirstChar {it.titlecase(java.util.Locale.ROOT)}
    }
    return (listOf(split)+placeQueryVariants(typed)).filter {searchText(it)!=searchText(typed)}.distinctBy(::searchText)
}
/** Typed form first, then at most one retry: the same request budget as before. */
internal fun photonSpellings(query:String):List<String> = listOf(photonTypedQuery(query))+photonRetryQueries(query).take(1)
/** The typed text without its house number ("Hölzer Weg 14, Beispielstadt" → "Hölzer Weg Beispielstadt"). */
internal fun typedWithoutHouseNumber(query:String):String? {
    val tokens=photonTypedQuery(query).split(' ')
    fun clean(token:String)=searchText(token)
    val index=tokens.indices.firstOrNull {i->
        val w=clean(tokens[i]).replace(" ","")
        i>0 && w.isNotEmpty() && w.first().isDigit() && w.length<=5 && !(w.length==5 && w.all(Char::isDigit)) &&
            tokens.take(i).any {t->clean(t).any(Char::isLetter)}
    } ?: return null
    val skip=if(clean(tokens.getOrNull(index+1).orEmpty()).let {it.length==1 && it[0].isLetter()}) setOf(index,index+1) else setOf(index)
    return tokens.filterIndexed {i,_->i !in skip}.joinToString(" ").trim().trimEnd(',').ifBlank {null}
}
internal fun placeQueryVariants(query:String):List<String> {
    val words=placeWords(query);val number=words.any {it.any(Char::isDigit)}
    fun expand(w:String,ambiguous:Boolean):String {
        if(w.endsWith("strasse"))return w
        val end=listOf("strass","stras","stra","str","st").firstOrNull {w.endsWith(it)&&w.length-it.length>=3}?:return w
        if(end=="st"&&!ambiguous&&(!number||w.length<8))return w
        return w.dropLast(end.length)+"strasse"
    }
    return listOf(false,true).map {a->words.joinToString(" "){expand(it,a)}}.filter {it.isNotBlank()}.distinct().take(2)
}
internal fun placeEditDistance(a:String,b:String,limit:Int=2):Int {
    if(abs(a.length-b.length)>limit)return limit+1
    if(a==b)return 0
    var before=IntArray(b.length+1){it};var previous=before.copyOf()
    for(i in 1..a.length){val current=IntArray(b.length+1);current[0]=i;var smallest=i
        for(j in 1..b.length){current[j]=minOf(previous[j]+1,current[j-1]+1,previous[j-1]+if(a[i-1]==b[j-1])0 else 1)
            if(i>1&&j>1&&a[i-1]==b[j-2]&&a[i-2]==b[j-1])current[j]=min(current[j],before[j-2]+1)
            smallest=min(smallest,current[j])}
        if(smallest>limit)return limit+1;before=previous;previous=current
    };return previous.last()
}
private fun streetStem(w:String):String {val s=listOf("strasse","strass","stras","stra","str","st").firstOrNull {w.endsWith(it)&&w.length-it.length>=3};return if(s==null)w else w.dropLast(s.length)}
private fun termScore(q:String,c:String):Int {
    if(q==c)return 100
    // House numbers must match exactly; only an added letter ("12" finds "12a") is accepted.
    if(q.any(Char::isDigit))return if(q.all(Char::isDigit)&&c.length==q.length+1&&c.startsWith(q)&&c.last().isLetter())88 else 0
    if(c.startsWith(q))return 92
    val a=streetStem(q);val b=streetStem(c)
    if((a==q&&b!=c)||(a!=q&&b==c)) {
        // One typo in the suffix is OK; a city name cannot satisfy an explicit street name.
        if(min(q.length,c.length)>=5&&placeEditDistance(q,c,1)<=1)return 70
        if(a!=q&&b==c)return 0
    }
    if(a.length<4||b.length<4)return 0
    if(a==b)return 100
    val max=if(min(a.length,b.length)>=8&&a.take(2)==b.take(2))2 else 1
    val full=placeEditDistance(a,b,max)
    val d=if(b.length>a.length)minOf(full,placeEditDistance(a,b.take(a.length),max),placeEditDistance(a,b.take(a.length+1),max))else full
    return if(d<=max&&d.toFloat()/maxOf(a.length,b.length)<=.26f)78-d*8 else 0
}
internal data class PlaceMatch(val exact:Boolean,val score:Int,val fuzzy:Boolean=false)
internal fun placeMatch(query:String,place:Location,aliases:List<String> = emptyList()):PlaceMatch? {
    // Build 122: "central station" means the main station, "Duesseldorf" Düsseldorf (see stationWords).
    val all=stationWords(placeWords(query)).distinct().take(12);val terms=all.filter {it !in searchFillers}.ifEmpty {all};if(terms.isEmpty())return null
    return (listOf(place.name+" "+place.city.orEmpty())+listOfNotNull(place.searchTerms)+aliases).mapNotNull {value->
        val words=placeWords(value).distinct();val used=mutableSetOf<Int>();val scores=mutableListOf<Int>()
        for(term in terms.sortedByDescending {it.length}) {
            val folded=umlautFolded(term)
            val match=words.indices.filter {it !in used}.map {it to maxOf(termScore(term,words[it]),if(folded!=term) termScore(folded,words[it]) else 0)}.maxByOrNull {it.second}
            if(match==null||match.second==0)return@mapNotNull null
            used+=match.first;scores+=match.second
        }
        val exact=scores.all {it==100};val fuzzy=scores.any {it<90}
        val joined=words.filter {it !in searchFillers}.joinToString(" ")
        PlaceMatch(exact,(if(joined==terms.joinToString(" "))4500 else if(exact)3500 else if(!fuzzy)2500 else 1300)+scores.sum()-(words.size-terms.size).coerceIn(0,30)*8,fuzzy)
    }.maxByOrNull {it.score}
}
/** "ue", "oe", "ae" written for an umlaut, folded like the stored names (Düsseldorf → dusseldorf). */
internal fun umlautFolded(term:String):String = term.replace(Regex("(?<!q)ue"),"u").replace("oe","o").replace("ae","a")
internal fun stationNameRank(name:String):Int {val w=placeWords(name).toSet();return when{"hauptbahnhof" in w->0;"bahnhof" in w||"stadtbahnhof" in w||"central" in w||"railway" in w->1;"zob" in w||"busbahnhof" in w->2;else->3}}
internal fun isCityQuery(query:String,city:String?):Boolean {
    if(city.isNullOrBlank())return false;val q=searchText(query);val c=searchText(city)
    return !q.any(Char::isDigit)&&(q==c||(q.length>=3&&c.startsWith(q))||(q.length>=4&&placeEditDistance(q,c,1)<=1))
}
internal fun sameSearchPlace(a:Location,b:Location):Boolean {
    if(placeIdentity(a)==placeIdentity(b))return true
    if(a.kind!=LocationKind.STOP||b.kind!=LocationKind.STOP)return false
    if(a.dhid!=null&&b.dhid!=null&&a.dhid!=b.dhid)return false
    val cities=listOfNotNull(a.city,b.city).map(::searchText).filter {it.isNotBlank()}.distinct();if(cities.size>1)return false
    fun name(p:Location):String {val n=searchText(p.name);return cities.firstOrNull {n.startsWith("$it ") }?.let {n.removePrefix("$it ")}?:n}
    return name(a)==name(b)&&distanceMeters(a,b)?.let {it<=50.0}==true
}
internal fun inGermanSearchScope(p:Location)=!p.hasCoordinates||(p.latitude!! in 47.2..55.2&&p.longitude!! in 5.8..15.1)
internal fun rankSearchedPlaces(query:String,rows:List<Location>,directory:List<Location> = emptyList(),stored:List<Location> = emptyList(),bias:Location?=null,aliases:Map<String,List<String>> = emptyMap(),order:List<SearchResultGroup> = defaultSearchResultOrder):List<Location> {
    val priorities=normalizedSearchOrder(order).withIndex().associate {it.value to it.index}
    val ids=(directory+stored.filter {it.kind==LocationKind.STOP}).map(::placeIdentity).toSet()
    data class Ranked(val place:Location,val group:Int,val station:Int,val score:Int,val distance:Double,val index:Int)
    return rows.mapIndexedNotNull {i,p->
        if(!inGermanSearchScope(p))return@mapIndexedNotNull null
        val localMatch=if(p.kind==LocationKind.STOP)(directory+stored).firstOrNull {sameSearchPlace(it,p)}else null
        val evidence=aliases[placeIdentity(p)].orEmpty()+localMatch?.let {aliases[placeIdentity(it)]}.orEmpty()+listOfNotNull(localMatch?.searchTerms,localMatch?.name)
        val m=placeMatch(query,p,evidence)?:return@mapIndexedNotNull null
        val local=p.kind==LocationKind.STOP&&(p.provider==StaticStopCatalog.PROVIDER||placeIdentity(p)in ids||localMatch!=null)
        val group=when{p.favoriteLabel!=null->SearchResultGroup.FAVORITES;local->SearchResultGroup.LOCAL_STOPS;p.kind==LocationKind.STOP->SearchResultGroup.REMOTE_STOPS;m.exact->SearchResultGroup.EXACT_PLACES;else->SearchResultGroup.MORE_PLACES}
        val station=if(p.kind==LocationKind.STOP&&isCityQuery(query,p.city?:localMatch?.city))stationNameRank(p.name+" "+localMatch?.name.orEmpty())else 3
        Ranked(p,priorities.getValue(group),station,m.score,if(bias!=null)distanceMeters(bias,p)?:Double.MAX_VALUE else 0.0,i)
    }.sortedWith(compareBy<Ranked>{it.group}.thenBy{it.station}.thenByDescending{it.score}.thenBy{it.distance}.thenBy{it.index}).map{it.place}
}
