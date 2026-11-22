// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.tracking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.net.toUri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import dev.ambivalenton.openv.MainActivity
import dev.ambivalenton.openv.OpenVApp
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.PinnedJourneyController
import dev.ambivalenton.openv.localization.tr
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine

/** User-started navigation. No alarms, boot receiver or remote push/Google dependency. */
class PinnedJourneyService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val controller get() = (application as OpenVApp).koin.get<PinnedJourneyController>()
    private var tracking: Job? = null
    private var notifications: Job? = null
    private var telemetry: Job? = null
    private var alerts: Job? = null
    private var locations:Job?=null
    private var foregroundType:Int?=null
    private fun updateForegroundType() {
        val device=(application as OpenVApp).deviceLocation
        val available=device.hasPermission() && device.locationEnabled()
        val type=if(Build.VERSION.SDK_INT>=29) {if(available)ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC} else 0
        if(type!=foregroundType){ServiceCompat.startForeground(this,NOTIFICATION,notification(controller.state.value),type);foregroundType=type}
    }

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, tr("Reisebegleiter"), NotificationManager.IMPORTANCE_LOW).apply {
                description = tr("Live-Status deiner angepinnten Verbindung")
                setShowBadge(false)
            }
        )
        configureAlerts()
    }

    private fun configureAlerts() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(ALERT_CHANNEL,tr("Reisehinweise: Ein-, Aus- und Umstieg"),NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true);vibrationPattern=longArrayOf(0,200,100,300)
                description=tr("Wichtige Hinweise während einer gestarteten Reisebegleitung")
                setShowBadge(false);lockscreenVisibility=Notification.VISIBILITY_PRIVATE
            })
    }

    /** Persist once per session/leg/action across service restarts; content updates remain silent. */
    internal fun showAlert(state:PinnedJourneyState) {
        val manager=getSystemService(NotificationManager::class.java)
        val alert=state.alert?.takeIf {state.status==PinnedTrackingStatus.RUNNING}
        if(alert==null) {manager.cancel(ALERT_NOTIFICATION);return}
        if(!androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled()) return
        if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(this,android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val prefs=getSharedPreferences("journey-alerts",Context.MODE_PRIVATE)
        val session=state.session?.id ?: return
        val seen=if(prefs.getString("session",null)==session) prefs.getStringSet("seen",emptySet()).orEmpty().toSet() else emptySet()
        val already=alert.key in seen
        val builder=NotificationCompat.Builder(this,ALERT_CHANNEL)
            .setSmallIcon(R.drawable.ic_train).setContentTitle(alert.title).setContentText(alert.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.message))
            .apply {if(TimetableProfile.sourceNotice) setSubText(tr(dev.ambivalenton.openv.data.model.TimetableServices.active.attribution))}
            .setContentIntent(notification(state).contentIntent).setAutoCancel(true).setLocalOnly(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setPriority(NotificationCompat.PRIORITY_HIGH).setOnlyAlertOnce(already).setSilent(already)
        try {
            manager.notify(ALERT_NOTIFICATION,builder.build())
            if(!already) prefs.edit().putString("session",session).putStringSet("seen",(seen+alert.key).takeLastSet(256)).apply()
        } catch(_:SecurityException) { /* Permission may be revoked between checking and posting. */ }
    }

    private fun Set<String>.takeLastSet(limit:Int)=toList().takeLast(limit).toSet()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if(intent?.action==ACTION_STOP_JOURNEY) {
            val expected=intent.getStringExtra(EXTRA_PINNED_ID)
            scope.launch {
                controller.restore()
                runCatching { controller.close(expected) }
                val remaining=controller.state.value.session
                // A full disk must never keep a requested journey running. A stale action must
                // also never pause a newer journey which replaced the notification's session.
                if(remaining==null || remaining.id==expected) {
                    if(remaining!=null) controller.pause(tr("Reisebegleitung beendet. Die lokale Speicherung ist fehlgeschlagen."))
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
            return START_NOT_STICKY
        }
        // Notification action "Bubble anzeigen": reopens the closed bubble of this pin. It changes
        // only the overlay decision; tracking continues unchanged below.
        if (intent?.action == ACTION_SHOW_BUBBLE) intent.getStringExtra(EXTRA_PINNED_ID)?.let { id ->
            scope.launch { runCatching { (application as OpenVApp).koin.get<PinnedBubbleCoordinator>().reopen(id) } }
        }
        try {
            val app = application as OpenVApp
            updateForegroundType()
            app.deviceLocation.startMotionTracking() // Optional local cadence, confined to the started journey service.
        } catch (_: RuntimeException) {
            controller.pause(tr("Android konnte das Hintergrund-Tracking nicht starten. Tippe auf Fortsetzen."))
            stopSelf()
            return START_NOT_STICKY
        }
        if (tracking?.isActive != true) tracking = scope.launch {
            controller.restore()
            if (controller.state.value.session == null) { stopSelf(); return@launch }
            notifications?.cancel()
            notifications = launch {
                val bubbleMode=(application as OpenVApp).locations.state.map { it.settings.pinnedBubble }.distinctUntilChanged()
                combine(controller.state.map { state -> state.session?.let { Triple(it.id,compactPinnedNotification(it,System.currentTimeMillis(),state.presence,state.displayFocus),it.bubbleDismissed) } },
                    bubbleMode) { copy, mode -> copy?.let { it to mode } }
                    .distinctUntilChanged().collectLatest { value ->
                        if(value==null) stopSelf()
                        else getSystemService(NotificationManager::class.java).notify(NOTIFICATION,
                            notification(controller.state.value,bubbleOffer=value.second && value.first.third && overlayAllowed(this@PinnedJourneyService)))
                    }
            }
            alerts?.cancel()
            alerts=launch {controller.state.map {it}.collectLatest {showAlert(it)}}
            telemetry?.cancel()
            telemetry=launch {
                controller.powerMode.collectLatest {saving->
                    while(isActive && controller.state.value.session!=null) {
                        runCatching {updateForegroundType()}
                        controller.telemetryTick()
                        delay(if(saving)30_000 else 2_000)
                    }
                }
            }
            locations?.cancel()
            locations=launch {
                val device=(application as OpenVApp).deviceLocation
                controller.state.map {state->Triple(state.session?.routing?.powerSaving!=false,
                    assistantLocationInterval(state.session,System.currentTimeMillis()),
                    (device.hasPermission() && device.locationEnabled()) to device.hasPrecisePermission())}
                    .distinctUntilChanged().collectLatest {(saving,interval,permissions)->
                        val enabled=permissions.first
                        if(!saving && enabled)device.journeyUpdates(interval).collect {controller.telemetryTick()}
                    }
            }
            controller.track()
        }
        return START_STICKY
    }

    /** [bubbleOffer]: the bubble mode is on but the bubble was closed for this pin; offer the way back. */
    internal fun notification(state: PinnedJourneyState, bubbleOffer: Boolean = false): Notification {
        val session = state.session
        val copy = session?.let { compactPinnedNotification(it, System.currentTimeMillis(),state.presence,state.displayFocus) }
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = "openv://overview".toUri()
            putExtra(EXTRA_PINNED_ID, session?.id)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pending = PendingIntent.getActivity(this, 410, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this, CHANNEL)
        if(session!=null)builder.addAction(R.drawable.ic_close,tr("Reise beenden"),
            PendingIntent.getService(this,413,Intent(this,PinnedJourneyService::class.java).setAction(ACTION_STOP_JOURNEY)
                .putExtra(EXTRA_PINNED_ID,session.id),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        // Keep the data source alongside the end-journey action when a notice is provided.
        if(TimetableProfile.sourceNotice) builder.addAction(R.drawable.ic_train,tr(dev.ambivalenton.openv.data.model.TimetableServices.active.attribution),
            PendingIntent.getActivity(this,414,Intent(Intent.ACTION_VIEW,dev.ambivalenton.openv.data.model.TimetableServices.active.sourceUrl.toUri()),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        if (bubbleOffer && session != null) builder.addAction(R.drawable.ic_bubble, tr("Schwebende Reisekarte anzeigen"),
            PendingIntent.getService(this, 412, Intent(this, PinnedJourneyService::class.java).setAction(ACTION_SHOW_BUBBLE)
                .putExtra(EXTRA_PINNED_ID, session.id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        return builder
            .setSmallIcon(R.drawable.ic_train)
            .setContentTitle(copy?.title ?: tr("Reisebegleiter"))
            .setContentText(copy?.text ?: tr("Live-Tracking wird gestartet …"))
            .apply {if(TimetableProfile.sourceNotice) setSubText(tr(dev.ambivalenton.openv.data.model.TimetableServices.active.attribution))}
            .setContentIntent(pending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setLocalOnly(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .build()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        controller.pause(tr("Android hat das Hintergrund-Tracking zeitlich begrenzt. Öffne die App und tippe auf Fortsetzen."))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        (application as OpenVApp).deviceLocation.stopMotionTracking()
        scope.cancel()
        getSystemService(NotificationManager::class.java).cancel(ALERT_NOTIFICATION)
        controller.pause()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_PINNED_ID = "openv_pinned_session"
        const val ACTION_SHOW_BUBBLE = "dev.ambivalenton.openv.action.SHOW_BUBBLE"
        const val ACTION_STOP_JOURNEY = "dev.ambivalenton.openv.action.STOP_JOURNEY"
        const val CHANNEL = "pinned-journey"
        const val ALERT_CHANNEL = "pinned-journey-alerts-v1"
        private const val ALERT_NOTIFICATION = 411
        private const val NOTIFICATION = 410
        fun start(context: Context): Boolean = try {
            ContextCompat.startForegroundService(context, Intent(context, PinnedJourneyService::class.java))
            true
        } catch (_: RuntimeException) { false }
        fun stop(context: Context) { context.stopService(Intent(context, PinnedJourneyService::class.java)) }
    }
}

