package com.jefz.eq

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class EqService : Service() {
    companion object {
        const val ACTION_FX_START = "com.jefz.eq.FX_START"
        const val ACTION_TOGGLE = "com.jefz.eq.TOGGLE"
        const val ACTION_VOL_UP = "com.jefz.eq.VOL_UP"
        const val ACTION_VOL_DOWN = "com.jefz.eq.VOL_DOWN"
        private const val VOL_STEP = 10f
        @Volatile var fxRunning = false
    }

    private val engine = EqEngine()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lastKey = ""
    private var proj: MediaProjection? = null
    private var wet: WetEngine? = null
    private var wetPkg: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    private fun fg(withProj: Boolean) {
        if (Build.VERSION.SDK_INT >= 29) {
            var t = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            if (withProj) t = t or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            startForeground(1, buildNotif(EqRepo.state.value), t)
        } else {
            startForeground(1, buildNotif(EqRepo.state.value))
        }
    }

    private fun act(action: String, code: Int): PendingIntent =
        PendingIntent.getForegroundService(
            this, code, Intent(this, EqService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun notifKey(s: EqState) = "${s.power}|${s.volume.roundToInt()}|${presetLabel(s)}"

    private fun buildNotif(s: EqState): Notification {
        lastKey = notifKey(s)
        val icon = Icon.createWithResource(this, R.drawable.ic_launcher)
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, "eq")
            .setContentTitle(if (s.power) "JefzEQ • ON" else "JefzEQ • OFF")
            .setContentText("Preset: ${presetLabel(s)}  •  Vol ${s.volume.roundToInt()}%")
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(icon, if (s.power) "Matikan" else "Nyalakan", act(ACTION_TOGGLE, 1)).build())
            .addAction(Notification.Action.Builder(icon, "Vol −10", act(ACTION_VOL_DOWN, 2)).build())
            .addAction(Notification.Action.Builder(icon, "Vol +10", act(ACTION_VOL_UP, 3)).build())
            .build()
    }

    /** Re-post only when something shown changed (avoids spamming while a knob is dragged). */
    private fun refreshNotif(s: EqState) {
        if (notifKey(s) == lastKey) return
        getSystemService(NotificationManager::class.java).notify(1, buildNotif(s))
    }

    override fun onCreate() {
        super.onCreate()
        EqRepo.init(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("eq", "JefzEQ", NotificationManager.IMPORTANCE_LOW))
        fg(false)
        scope.launch {
            EqRepo.state.collect { s ->
                if (s.power) {
                    if (!engine.running) engine.start()
                    engine.apply(s)
                } else {
                    engine.release()
                    if (s.fxOn) EqRepo.update { it.copy(fxOn = false) } // capture follows power
                }
                refreshNotif(s)
                synchronized(this@EqService) {
                    if (wet != null) {
                        if (!s.fxOn) stopFx()
                        else if (s.fxPkg != wetPkg) startWet(s.fxPkg)
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_FX_START -> startFx(intent!!)
            ACTION_TOGGLE -> EqRepo.update { it.copy(power = !it.power) }
            ACTION_VOL_UP -> EqRepo.update { it.copy(volume = (it.volume + VOL_STEP).coerceAtMost(100f)) }
            ACTION_VOL_DOWN -> EqRepo.update { it.copy(volume = (it.volume - VOL_STEP).coerceAtLeast(0f)) }
        }
        return START_STICKY
    }

    @Synchronized
    private fun startFx(i: Intent) {
        val code = i.getIntExtra("code", 0)
        @Suppress("DEPRECATION")
        val data = if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra("data", Intent::class.java)
        else i.getParcelableExtra<Intent>("data")
        if (data == null) { failFx(); return }
        try {
            stopFx()
            fg(true)
            val mpm = getSystemService(MediaProjectionManager::class.java)
            val p = mpm.getMediaProjection(code, data)
            p.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { EqRepo.update { it.copy(fxOn = false) } }
            }, Handler(Looper.getMainLooper()))
            proj = p
            EqRepo.update { it.copy(fxOn = true) }
            startWet(EqRepo.state.value.fxPkg)
        } catch (e: Exception) {
            failFx()
        }
    }

    @Synchronized
    private fun startWet(pkg: String) {
        val p = proj ?: return failFx()
        wet?.stop()
        val uid = try { packageManager.getApplicationInfo(pkg, 0).uid } catch (e: Exception) { return failFx() }
        val w = WetEngine(p)
        if (w.start(uid)) {
            wet = w; wetPkg = pkg; fxRunning = true
        } else {
            failFx()
        }
    }

    @Synchronized
    private fun stopFx() {
        wet?.stop(); wet = null; wetPkg = null
        fxRunning = false
        val p = proj; proj = null
        try { p?.stop() } catch (_: Exception) {}
        try { fg(false) } catch (_: Exception) {}
    }

    private fun failFx() {
        EqRepo.update { it.copy(fxOn = false) }
        stopFx()
    }

    override fun onDestroy() {
        scope.cancel()
        synchronized(this) { stopFx() }
        engine.release()
        super.onDestroy()
    }
}
