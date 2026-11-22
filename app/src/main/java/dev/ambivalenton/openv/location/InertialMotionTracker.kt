// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import dev.ambivalenton.openv.data.model.*
import kotlin.math.*

/** Optional, permission-free motion sensors run only during the user-started journey service.
 * Only step cadence is needed; footsteps never become fictitious GPS fixes. */
class InertialMotionTracker(context:Context):SensorEventListener {
    private val manager=context.getSystemService(SensorManager::class.java)
    private val gravity=FloatArray(3);private val cadence=StepCadenceEstimator()
    private var registered=false
    @Volatile var latest:DeviceMotion?=null;private set
    @Volatile var activity:DeviceActivityEvidence?=null;private set
    fun start() {
        if(registered) return
        val m=manager ?: return
        val acceleration=m.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) ?: m.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        registered=m.registerListener(this,acceleration,SensorManager.SENSOR_DELAY_GAME)
        if(!registered) return
    }
    fun stop() {manager?.unregisterListener(this);registered=false;latest=null;activity=null;cadence.reset();gravity.fill(0f)}
    override fun onAccuracyChanged(sensor:Sensor?,accuracy:Int) {
        if(accuracy==SensorManager.SENSOR_STATUS_UNRELIABLE) {latest=null;activity=null;cadence.reset()}
    }
    override fun onSensorChanged(event:SensorEvent) {
        if(event.accuracy==SensorManager.SENSOR_STATUS_UNRELIABLE) return
        if(event.sensor.type !in setOf(Sensor.TYPE_LINEAR_ACCELERATION,Sensor.TYPE_ACCELEROMETER)) return
        val input=event.values;if(input.size<3 || input.take(3).any {!it.isFinite()}) return
        val values=if(event.sensor.type==Sensor.TYPE_ACCELEROMETER) FloatArray(3) {i->
            gravity[i]=.82f*gravity[i]+.18f*input[i];input[i]-gravity[i]
        } else input
        val timestamp=System.currentTimeMillis()-(SystemClock.elapsedRealtimeNanos()-event.timestamp).coerceAtLeast(0)/1_000_000
        val magnitude=sqrt(values.take(3).sumOf {it.toDouble()*it})
        activity=cadence.observe(magnitude,event.timestamp/1_000_000,timestamp)
    }
}
