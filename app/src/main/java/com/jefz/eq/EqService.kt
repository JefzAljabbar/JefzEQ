package com.jefz.eq

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
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

class EqService : Service() {
    companion object {
        const val ACTION_FX_START = "com.jefz.eq.FX_START"
        @Volatile var fxRunning = false
    }

    private val engine = EqEngine()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var notif: Notification
    private var proj: MediaProjection? = null
    private var wet: WetEngine? = null
    private var wetPkg: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    private fun fg(withProj: Boolean) {
        if (Build.VERSION.SDK_INT >= 29) {
            var t = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            if (withProj) t = t or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            startForeground(1, notif, t)
        } else {
            startForeground(1, notif)
        }
    }

    override fun onCreate() {
        super.onCreate()
        EqRepo.init(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("eq", "JefzEQ", NotificationManager.IMPORTANCE_LOW))
        notif = Notification.Builder(this, "eq")
            .setContentTitle("JefzEQ aktif")
            .setSmallIcon(R.drawable.ic_launcher)
            .setOngoing(true)
            .build()
        fg(false)
        scope.launch {
            EqRepo.state.collect { s ->
                if (s.power) {
                    if (!engine.running) engine.start()
                    engine.apply(s)
                } else {
                    engine.release()
                }
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
        if (intent?.action == ACTION_FX_START) startFx(intent)
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
