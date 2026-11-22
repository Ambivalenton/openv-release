// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.widget
import kotlin.math.*
internal enum class FavoriteWidgetKind { TOKEN,TILE,HORIZONTAL,FEATURED }
internal data class FavoriteWidgetSlot(val index:Int,val width:Float,val height:Float,val kind:FavoriteWidgetKind)
internal data class FavoriteWidgetRow(val slots:List<FavoriteWidgetSlot>,val height:Float)
internal data class FavoriteWidgetLayout(val width:Float,val height:Float,val padding:Float,val gap:Float,val headerHeight:Float,
 val bodyHeight:Float,val rows:List<FavoriteWidgetRow>,val columns:Int,val scroll:Boolean,val visibleRows:Int) {
 val innerWidth get()=width-2*padding
}
/** Actual launcher bounds, complete touch rows and every favourite retained. */
internal fun favoriteWidgetLayout(width:Float,height:Float,count:Int,fontScale:Float=1f,iconsOnly:Boolean=false,
 highlighted:Int=0,compactBackground:Boolean=false):FavoriteWidgetLayout {
 val w=if(width.isFinite())width.coerceAtLeast(48f) else 64f
 val h=if(height.isFinite())height.coerceAtLeast(48f) else 64f
 val n=count.coerceIn(0,41);val font=if(fontScale.isFinite())fontScale.coerceIn(.7f,2f) else 1f
 val side=min(w,h);val pad=when {side<80->0f;side<150->5f;else->8f};val gap=if(side<80)4f else 7f
 val aw=w-2*pad;val ah=h-2*pad
 val header=if(!iconsOnly&&aw>=200&&ah>=240&&n>=8)44f*min(font,1.4f) else 0f
 val available=(ah-header-(if(header>0)gap else 0f)).coerceAtLeast(48f)
 val featured=!iconsOnly&&aw>=144&&available>=(if(n==3)144 else 208)&&n in 3..7&&(highlighted>0||n%2==1)
 val hero=if(featured)min(available*.38f,112*font).coerceAtLeast(64f) else 0f
 val regular=n-(if(featured)1 else 0)
 val rest=(available-hero-(if(featured)gap else 0f)).coerceAtLeast(48f)
 val maxCols=min(5,floor((aw+gap)/(48+gap)).toInt().coerceAtLeast(1)).coerceAtMost(max(regular,1))
 data class Candidate(val cols:Int,val visible:Int,val th:Float,val score:Float)
 val best=(1..maxCols).map {cols->
  val rowCount=ceil(max(regular,1).toFloat()/cols).toInt()
  val visible=min(rowCount,floor((rest+gap)/(48+gap)).toInt().coerceAtLeast(1))
  val tw=(aw-gap*(cols-1))/cols;val natural=(rest-gap*(visible-1))/visible
  val cap=if(iconsOnly)116f else if(cols==1&&aw>=160)132*font else 148f
  val th=if(compactBackground)min(natural,cap) else natural
  val ideal=if(iconsOnly)76f else 92*min(font,1.4f)
  val aspect=if(tw>=150&&th<112)abs(tw/th-2.8f)*5 else abs(tw/th-1)*18
  Candidate(cols,visible,th,(rowCount-visible)*240+abs(tw-ideal)*.55f+aspect+(rowCount*cols-regular)*8)
 }.minBy {it.score}
 val rows=mutableListOf<FavoriteWidgetRow>()
 if(featured)rows+=FavoriteWidgetRow(listOf(FavoriteWidgetSlot(0,aw,hero,FavoriteWidgetKind.FEATURED)),hero)
 val tw=(aw-gap*(best.cols-1))/best.cols
 ((if(featured)1 else 0) until n).toList().chunked(best.cols).forEach {batch->
  val kind=when {iconsOnly||best.th<56||tw<56->FavoriteWidgetKind.TOKEN
   (best.cols==1&&tw>=144)||(tw>=144&&best.th<112)->FavoriteWidgetKind.HORIZONTAL;else->FavoriteWidgetKind.TILE}
  rows+=FavoriteWidgetRow(batch.map {FavoriteWidgetSlot(it,tw,best.th,kind)},best.th)
 }
 val visibleRows=best.visible+(if(featured)1 else 0)
 val viewport=hero+(if(featured)gap else 0f)+best.visible*best.th+gap*(best.visible-1)
 val actualH=if(compactBackground&&n>0)min(h,2*pad+header+(if(header>0)gap else 0f)+viewport) else h
 return FavoriteWidgetLayout(w,actualH,pad,gap,header,(actualH-2*pad-header-(if(header>0)gap else 0f)).coerceAtLeast(48f),
  rows,best.cols,regular>best.cols*best.visible,visibleRows)
}
