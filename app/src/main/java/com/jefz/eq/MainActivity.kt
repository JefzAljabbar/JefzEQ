package com.jefz.eq

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val projLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val data = r.data
        if (r.resultCode == RESULT_OK && data != null) {
            EqRepo.update { it.copy(power = true) }
            val i = Intent(this, EqService::class.java)
                .setAction(EqService.ACTION_FX_START)
                .putExtra("code", r.resultCode)
                .putExtra("data", data)
            ContextCompat.startForegroundService(this, i)
        }
    }

    private val micLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) launchProjection()
    }

    private val rtaPermLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) { Rta.on = true; Rta.start(this) }
    }

    private fun setRta(on: Boolean) {
        if (!on) { Rta.on = false; Rta.stop(); return }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            rtaPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            Rta.on = true
            Rta.start(this)
        }
    }

    private var pendingExport: String? = null

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val n = pendingExport
        pendingExport = null
        if (uri != null && n != null) {
            val t = PresetStore.exportText(n)
            try {
                if (t == null) throw IllegalStateException()
                contentResolver.openOutputStream(uri)?.use { it.write(t.toByteArray()) }
                Toast.makeText(this, "Preset diekspor", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Gagal ekspor", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val t = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                val n = t?.let { PresetStore.importText(it) }
                Toast.makeText(this, if (n != null) "Diimpor: $n" else "Bukan file preset JefzEQ", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Gagal impor", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun exportPreset(name: String) {
        pendingExport = name
        exportLauncher.launch("JefzEQ_$name.json")
    }

    private fun importPreset() {
        importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*"))
    }

    override fun onStart() {
        super.onStart()
        if (Rta.on) Rta.start(this)
    }

    override fun onStop() {
        Calib.stop()
        Gen.stop()
        Rta.stop()
        super.onStop()
    }

    private fun launchProjection() {
        val mpm = getSystemService(MediaProjectionManager::class.java)
        projLauncher.launch(mpm.createScreenCaptureIntent())
    }

    private fun setFx(on: Boolean) {
        if (!on) { EqRepo.update { it.copy(fxOn = false) }; return }
        if (Build.VERSION.SDK_INT < 29) {
            Toast.makeText(this, "Reverb/Echo butuh Android 10+", Toast.LENGTH_LONG).show()
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            launchProjection()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EqRepo.init(this)
        PresetStore.init(this)
        Locks.init(this)
        Thread { Apps.load(packageManager) }.start()
        if (!EqService.fxRunning) EqRepo.update { it.copy(fxOn = false) }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF00E5FF),
                    background = Color(0xFF0E1116),
                    surface = Color(0xFF181D25)
                )
            ) {
                var unlocked by remember { mutableStateOf(LockStore.isUnlocked(this@MainActivity)) }
                if (!unlocked) {
                    PasswordScreen(onUnlocked = { unlocked = true })
                } else {
                    val state by EqRepo.state.collectAsState()
                    // Service stays alive (also when EQ is Off) so the status-bar On/Off + volume controls work.
                    LaunchedEffect(Unit) {
                        ContextCompat.startForegroundService(this@MainActivity, Intent(this@MainActivity, EqService::class.java))
                    }
                    EqScreen(
                        state,
                        onFxToggle = { setFx(it) },
                        onRtaToggle = { setRta(it) },
                        onExport = { exportPreset(it) },
                        onImport = { importPreset() }
                    )
                }
            }
        }
    }
}
