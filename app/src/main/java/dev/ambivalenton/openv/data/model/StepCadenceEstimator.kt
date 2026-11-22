// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

/** Magnitude pulses with a refractory period; vibration/shaking alone cannot confirm alighting. */
class StepCadenceEstimator {
    private val pulses=ArrayDeque<Long>();private var high=false;private var previous:Long?=null
    fun reset() {pulses.clear();high=false;previous=null}
    fun observe(magnitude:Double,elapsedMillis:Long,wallMillis:Long):DeviceActivityEvidence {
        if(previous?.let {elapsedMillis<it}==true) reset()
        // Long enough for sustained walking evidence, but stop promptly when footsteps cease.
        if(pulses.isNotEmpty() && elapsedMillis-pulses.last()>2_500)pulses.clear()
        while(pulses.isNotEmpty() && elapsedMillis-pulses.first()>16_000) pulses.removeFirst()
        if(magnitude.isFinite() && magnitude in 0.0..18.0) {
            if(magnitude<.55) high=false
            if(!high && magnitude>1.3) {
                high=true
                if(previous?.let {elapsedMillis-it>=300}!=false) {pulses.addLast(elapsedMillis);previous=elapsedMillis}
            }
        }
        val span=if(pulses.size>=2) pulses.last()-pulses.first() else 0
        return DeviceActivityEvidence(wallMillis,pulses.size,span,if(span>0) (pulses.size-1)*1000f/span else 0f)
    }
}
