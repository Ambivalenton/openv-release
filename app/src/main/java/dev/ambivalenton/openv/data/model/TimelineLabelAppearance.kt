// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

enum class TimelineLabelEffect { PLAIN, SHADOW, BADGE }
data class TimelineLabelAppearance(
    val scale:Float,
    val opacity:Float,
    val effect:TimelineLabelEffect=TimelineLabelEffect.PLAIN,
)

const val TIMELINE_LABEL_NORMAL=1f
const val TIMELINE_LABEL_SHADOW=1.25f
const val TIMELINE_LABEL_BADGE=1.5f
val timelineLabelDetents=listOf(TIMELINE_LABEL_NORMAL,TIMELINE_LABEL_SHADOW,TIMELINE_LABEL_BADGE)

fun normalizedTimelineLabelControl(value:Float)=if(value.isFinite()) value.coerceIn(0f,TIMELINE_LABEL_BADGE) else 1f

/** Values below 100% remain continuous. Only the effect region is quantized. */
fun snappedTimelineLabelControl(value:Float):Float {
    val p=normalizedTimelineLabelControl(value)
    return when {
        p<1f->p
        p<1.125f->TIMELINE_LABEL_NORMAL
        p<1.375f->TIMELINE_LABEL_SHADOW
        else->TIMELINE_LABEL_BADGE
    }
}

/** Small hysteresis prevents haptic chatter when a finger rests between two effect stops. */
class TimelineLabelSnapController(initial:Float) {
    private var current=snappedTimelineLabelControl(initial)
    fun dragTo(raw:Float):Float {
        val p=normalizedTimelineLabelControl(raw)
        val next=snappedTimelineLabelControl(p)
        val boundary=(current+next)/2f
        val holding=current>=1f && next>=1f && current!=next &&
            kotlin.math.abs(p-boundary)<.018f
        if(!holding) current=next
        return current
    }
}

/** The jump in opacity at zero is intentional; scale still grows continuously. */
fun timelineLabelAppearance(value:Float):TimelineLabelAppearance {
    val p=snappedTimelineLabelControl(value)
    return when {
        p==TIMELINE_LABEL_BADGE->TimelineLabelAppearance(1f,1f,TimelineLabelEffect.BADGE)
        p==TIMELINE_LABEL_SHADOW->TimelineLabelAppearance(1f,1f,TimelineLabelEffect.SHADOW)
        p==0f->TimelineLabelAppearance(0f,0f)
        p<=.75f->TimelineLabelAppearance(p/.75f,.5f)
        else->TimelineLabelAppearance(1f,.5f+(p-.75f)*2f)
    }
}
