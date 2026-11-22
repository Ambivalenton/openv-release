// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.location


import dev.ambivalenton.openv.localization.tr

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationRequestCompat
import dev.ambivalenton.openv.data.model.DevicePresence
import dev.ambivalenton.openv.data.model.Location
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import dev.ambivalenton.openv.data.model.locationCacheMillis
import dev.ambivalenton.openv.data.model.locationProviderNames
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.ExperimentalCoroutinesApi

/** Platform location only: works on AOSP without a Google SDK or account. */
class DeviceLocationProvider(private val context: Context) : CurrentPositionSource {
    @Volatile var powerSaving:Boolean=false
        private set
    fun setPowerSaving(enabled:Boolean) {
        if(powerSaving==enabled)return
        powerSaving=enabled
    }
    override fun locationEnabled():Boolean = context.getSystemService(LocationManager::class.java)?.let {LocationManagerCompat.isLocationEnabled(it)}==true
    /**
     * The app asks for no position at all. Every request path checks
     * [hasPermission] first, so it reports "no access" here and no listener, fix or sensor starts.
     */
    @Volatile var locationOff:Boolean=false
        private set
    fun setLocationOff(enabled:Boolean) {
        if(locationOff==enabled)return
        locationOff=enabled
        if(enabled) {cached=null;measuredPresence=null;motion.stop()}
    }
    override fun hasPermission() = !locationOff &&
        listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    private val motion=InertialMotionTracker(context)
    override val lastMotion get()=motion.latest
    override val lastActivity get()=motion.activity
    fun startMotionTracking() {if(!locationOff)motion.start()}
    fun stopMotionTracking()=motion.stop()

    private data class Cached(val elapsed: Long, val location: Location, val precise: Boolean)
    private val mutex = Mutex()
    @Volatile private var cached: Cached? = null
    @Volatile private var journeyListeners=0
    override val continuousTracking:Boolean get()=journeyListeners>0
    @Volatile private var measuredPresence:DevicePresence?=null
    fun hasPrecisePermission()=ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
    override val lastPresence:DevicePresence? get()=measuredPresence?.takeIf {
        hasPermission() && locationEnabled() && (cached?.precise!=true || hasPrecisePermission())
    }

    /** One user-started foreground subscription keeps GNSS warm through train journeys.
     * Network fixes may fill a gap, but cannot replace a recent, much more accurate fix. */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Suppress("MissingPermission")
    fun journeyUpdates(intervalMillis:Long):Flow<DevicePresence> = callbackFlow {
        val manager=context.getSystemService(LocationManager::class.java)
        if(manager==null || !hasPermission() || !locationEnabled()){close();return@callbackFlow}
        val precise=ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
        val available=manager.getProviders(true)
        val providers=if(precise && LocationManager.GPS_PROVIDER in available)
            listOf(LocationManager.GPS_PROVIDER)+listOf(LocationManager.NETWORK_PROVIDER).filter {it in available}
        else locationProviderNames(available,precise,false,false).take(1)
        val listener=object:LocationListenerCompat {
            override fun onLocationChanged(fix:android.location.Location) {
                recordFix(fix,precise)?.let {trySend(it)}
            }
        }
        var registered=false
        for(provider in providers)try {
            val request=LocationRequestCompat.Builder(intervalMillis.coerceIn(2_000,30_000))
                .setMinUpdateIntervalMillis(intervalMillis.coerceIn(2_000,30_000)).setMinUpdateDistanceMeters(0f)
                .setQuality(if(provider==LocationManager.GPS_PROVIDER || provider=="fused")LocationRequestCompat.QUALITY_HIGH_ACCURACY else LocationRequestCompat.QUALITY_BALANCED_POWER_ACCURACY).build()
            LocationManagerCompat.requestLocationUpdates(manager,provider,request,ContextCompat.getMainExecutor(context),listener)
            registered=true
        }catch(_:SecurityException){}catch(_:IllegalArgumentException){}
        if(registered)journeyListeners++
        awaitClose {
            runCatching {LocationManagerCompat.removeUpdates(manager,listener)}
            if(registered)journeyListeners=(journeyListeners-1).coerceAtLeast(0)
        }
    }

    @Synchronized private fun recordFix(fix:android.location.Location,precise:Boolean):DevicePresence? {
        if(!hasPermission() || !locationEnabled() || !isFreshPlatformFix(fix) || !fix.hasAccuracy() || !fix.accuracy.isFinite() || fix.accuracy<0)return null
        if(precise && ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return null
        val fixElapsed=fix.elapsedRealtimeNanos/1_000_000
        val previous=cached
        if(previous!=null && previous.precise==precise && fixElapsed<=previous.elapsed)return null
        val old=lastPresence
        if(previous!=null && old!=null && fixElapsed-previous.elapsed<25_000 && fix.accuracy>maxOf(100f,old.accuracyMeters*3f))return null
        val place=Location(tr("Aktueller Standort"),latitude=fix.latitude,longitude=fix.longitude)
        val presence=DevicePresence(place,System.currentTimeMillis()-(SystemClock.elapsedRealtime()-fixElapsed).coerceAtLeast(0),fix.accuracy,
            fix.speed.takeIf {fix.hasSpeed() && it.isFinite() && it in 0f..110f && (!fix.hasSpeedAccuracy() || fix.speedAccuracyMetersPerSecond<=maxOf(2f,it*.4f))})
        cached=Cached(fixElapsed,place,precise);measuredPresence=presence
        return presence
    }

    override suspend fun current(): Location = measure(false)
    override suspend fun currentFresh(): Location = measure(true)

    private suspend fun measure(force: Boolean): Location = mutex.withLock {
        check(hasPermission()) {
            tr("Standortfreigabe fehlt. Bitte erlauben oder einen manuellen Start wählen.")
        }
        val manager = context.getSystemService(LocationManager::class.java)
        check(manager != null && LocationManagerCompat.isLocationEnabled(manager)) {
            tr("Der Gerätestandort ist ausgeschaltet. Bitte in Android aktivieren oder manuell suchen.")
        }
        val precise = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        cached?.takeIf { !force && it.precise == precise && SystemClock.elapsedRealtime() - it.elapsed in 0 until locationCacheMillis(powerSaving) }
            ?.let { return@withLock it.location }
        // An approximate permission must never reuse a formerly precise fix.
        if(cached?.precise!=precise){cached=null;measuredPresence=null}
        // A fresh continuous fix already fulfils this request; do not stop/restart the receiver.
        cached?.takeIf {!force && continuousTracking && it.precise==precise && SystemClock.elapsedRealtime()-it.elapsed in 0..5_000}
            ?.let {return@withLock it.location}
        val providers=locationProviderNames(manager.getProviders(true),precise,powerSaving,force)
        val requestedAt = SystemClock.elapsedRealtimeNanos()
        var fallbackFix: android.location.Location?=null
        val requestedFix = withTimeoutOrNull(if(force && precise) 5_000L else 14_000L) {
            coroutineScope {
                val responses = Channel<Pair<String,android.location.Location?>>(providers.size.coerceAtLeast(1))
                val jobs = providers.map { provider -> launch {
                    val result = try { if (force) awaitNewFix(manager, provider, requestedAt) else awaitFix(manager, provider) }
                    catch (e: CancellationException) { throw e }
                    catch (_: SecurityException) { null }
                    catch (_: IllegalArgumentException) { null }
                    responses.send(provider to result?.takeIf(::isFreshPlatformFix))
                } }
                try {
                    repeat(providers.size) {
                        val (provider,result)=responses.receive()
                        if(result!=null) {
                            if(fallbackFix==null || (result.hasAccuracy() && result.accuracy<fallbackFix!!.accuracy)) fallbackFix=result
                            if(!force || !precise || (result.hasAccuracy() && result.accuracy<=100f && (provider==LocationManager.GPS_PROVIDER || result.accuracy<=25f))) return@coroutineScope result
                        }
                    }
                    fallbackFix
                } finally {
                    jobs.forEach { it.cancel() }
                    responses.close()
                }
            }
        }
        val fix=requestedFix ?: fallbackFix
        checkNotNull(fix) {
            tr("Keine aktuelle Position erhalten. Bitte erneut versuchen oder den Start manuell wählen.")
        }
        check(hasPermission() && (!precise || ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)) {
            tr("Standortfreigabe fehlt. Bitte erlauben oder einen manuellen Start wählen.")
        }
        recordFix(fix,precise)
        cached?.takeIf {it.precise==precise}?.location ?: Location(tr("Aktueller Standort"),latitude=fix.latitude,longitude=fix.longitude)
    }

    /** Wait for an observation made after the gesture, not a last-known platform result. */
    @Suppress("MissingPermission")
    private suspend fun awaitNewFix(manager: LocationManager, provider: String, afterNanos: Long): android.location.Location? =
        suspendCancellableCoroutine { continuation ->
            val listener = object : LocationListenerCompat {
                override fun onLocationChanged(fix: android.location.Location) {
                    if (fix.elapsedRealtimeNanos >= afterNanos && isFreshPlatformFix(fix) && continuation.isActive) {
                        runCatching {LocationManagerCompat.removeUpdates(manager,this)}
                        continuation.resume(fix)
                    }
                }
            }
            continuation.invokeOnCancellation { runCatching {LocationManagerCompat.removeUpdates(manager,listener)} }
            try {
                val request = LocationRequestCompat.Builder(1_000L).setMinUpdateIntervalMillis(0)
                    .setQuality(if (provider==LocationManager.GPS_PROVIDER || provider=="fused") LocationRequestCompat.QUALITY_HIGH_ACCURACY
                        else LocationRequestCompat.QUALITY_BALANCED_POWER_ACCURACY).build()
                LocationManagerCompat.requestLocationUpdates(manager,provider,request,ContextCompat.getMainExecutor(context),listener)
                if (!continuation.isActive) runCatching {LocationManagerCompat.removeUpdates(manager,listener)}
            } catch (e: Exception) {
                runCatching {LocationManagerCompat.removeUpdates(manager,listener)}
                throw e
            }
        }

    @Suppress("MissingPermission")
    private suspend fun awaitFix(manager: LocationManager, provider: String): android.location.Location? =
        suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            try {
                LocationManagerCompat.getCurrentLocation(manager, provider, signal,
                    ContextCompat.getMainExecutor(context)) { fix ->
                    if (continuation.isActive) continuation.resume(fix)
                }
            } catch (e: Exception) {
                signal.cancel()
                throw e
            }
        }
}

/** Reject stale, future-dated or invalid fixes rather than silently routing from an old position. */
internal fun isFreshPlatformFix(fix: android.location.Location): Boolean =
    fix.latitude.isFinite() && fix.latitude in -90.0..90.0 &&
        fix.longitude.isFinite() && fix.longitude in -180.0..180.0 &&
        SystemClock.elapsedRealtime() - fix.elapsedRealtimeNanos / 1_000_000 in 0..60_000
