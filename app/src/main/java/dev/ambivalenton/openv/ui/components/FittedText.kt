// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.unit.Constraints
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
/**
 * Intrinsic-size-safe fitting. Text keeps its size and wraps between words into up to [maxLines]
 * lines; it only gets smaller when a single word would not fit otherwise. A word is never broken
 * in the middle ("Sicherun-g"). [maxLines] = 1 keeps the former single-line fitting.
 */
@Composable fun FittedText(text:String,modifier:Modifier=Modifier,color:Color=Color.Unspecified,
 fontSize:TextUnit=TextUnit.Unspecified,fontWeight:FontWeight?=null,textAlign:TextAlign?=null,
 style:TextStyle=LocalTextStyle.current,onTextLayout:(TextLayoutResult)->Unit={},maxLines:Int=2) =
 FittedText(AnnotatedString(text.replace('\n',' ')),modifier,color,fontSize,fontWeight,textAlign,style,onTextLayout,maxLines)
@Composable fun FittedText(text:AnnotatedString,modifier:Modifier=Modifier,color:Color=Color.Unspecified,
 fontSize:TextUnit=TextUnit.Unspecified,fontWeight:FontWeight?=null,textAlign:TextAlign?=null,
 style:TextStyle=LocalTextStyle.current,onTextLayout:(TextLayoutResult)->Unit={},maxLines:Int=2) {
 var resolved=LocalTextStyle.current.merge(style)
 val max=if(fontSize!=TextUnit.Unspecified)fontSize else if(resolved.fontSize!=TextUnit.Unspecified)resolved.fontSize else 16.sp
 resolved=resolved.copy(fontSize=max,lineHeight=TextUnit.Unspecified,color=if(color!=Color.Unspecified)color else if(resolved.color!=Color.Unspecified)resolved.color else LocalContentColor.current,
  fontWeight=fontWeight?:resolved.fontWeight,textAlign=textAlign?:resolved.textAlign)
 // Build 113: "one line" means one line while it stays readable. A single word only shrinks (never
 // breaks); several words shrink down to a reading floor and then take a second line between words.
 if(maxLines<=1) BasicText(text,modifier,style=resolved,maxLines=2,softWrap=true,overflow=TextOverflow.Clip,
  autoSize=ReadableAutoSize(max.value,readingFloor(max.value)),onTextLayout=onTextLayout)
 else BasicText(text,modifier,style=resolved,maxLines=maxLines,softWrap=true,overflow=TextOverflow.Clip,
  autoSize=WordSafeAutoSize(max.value,maxLines),onTextLayout=onTextLayout)
}

/** True when no line of [layout] ends inside a word (a letter or digit on both sides of the break). */
fun breaksOnlyBetweenWords(layout:TextLayoutResult):Boolean {
 val text=layout.layoutInput.text.text
 for(line in 0 until layout.lineCount-1) {
  val end=layout.getLineEnd(line)
  if(end in 1 until text.length && text[end-1].isLetterOrDigit() && text[end].isLetterOrDigit()) return false
 }
 return true
}

/**
 * The largest size (at most [maxSp]) at which the text fits its space in [maxLines] lines with
 * breaks only between words; found by a binary search in steps of 0.25 sp down to 0.5 sp.
 */
private data class WordSafeAutoSize(val maxSp:Float,val maxLines:Int):TextAutoSize {
 override fun TextAutoSizeLayoutScope.getFontSize(constraints:Constraints,text:AnnotatedString):TextUnit {
  fun fits(size:Float):Boolean {
   val layout=performLayout(constraints,text,size.sp)
   return !layout.didOverflowWidth && !layout.didOverflowHeight && layout.lineCount<=maxLines && breaksOnlyBetweenWords(layout)
  }
  if(fits(maxSp)) return maxSp.sp
  var low=0;var high=((maxSp-.5f)/.25f).toInt()   // step index: size = 0.5 + i * 0.25
  if(!fits(.5f)) return .5.sp
  while(low<high) {
   val mid=(low+high+1)/2
   if(fits(.5f+mid*.25f)) low=mid else high=mid-1
  }
  return (.5f+low*.25f).sp
 }
}

/** The smallest size a running label may shrink to before it takes a second line: 80 % of its size, at least 11 sp. */
internal fun readingFloor(maxSp:Float):Float=minOf(maxSp,maxOf(11f,maxSp*.8f))

/**
 * One line at the largest size down to [floorSp]; below that, two lines broken only between words
 * (down to the floor again); only when even that does not fit does the text shrink further – in
 * whichever of one or two lines keeps it larger – so nothing ever runs out of its box. Sizes in steps of 0.25 sp, found by binary search.
 */
private data class ReadableAutoSize(val maxSp:Float,val floorSp:Float):TextAutoSize {
 override fun TextAutoSizeLayoutScope.getFontSize(constraints:Constraints,text:AnnotatedString):TextUnit {
  fun fits(size:Float,lines:Int):Boolean {
   val layout=performLayout(constraints,text,size.sp)
   return !layout.didOverflowWidth && !layout.didOverflowHeight && layout.lineCount<=lines && breaksOnlyBetweenWords(layout)
  }
  fun largest(from:Float,lines:Int):Float? {
   if(fits(maxSp,lines)) return maxSp
   if(!fits(from,lines)) return null
   var low=0;var high=((maxSp-from)/.25f).toInt()
   while(low<high) {val mid=(low+high+1)/2;if(fits(from+mid*.25f,lines)) low=mid else high=mid-1}
   return from+low*.25f
  }
  val words=text.text.trim().contains(' ')
  largest(floorSp,1)?.let {return it.sp}
  if(words) largest(floorSp,2)?.let {return it.sp}
  // Not even the floor fits: the larger of one line and (between words) two lines.
  val one=largest(.5f,1);val two=if(words) largest(.5f,2) else null
  return (listOfNotNull(one,two).maxOrNull() ?: .5f).sp
 }
}