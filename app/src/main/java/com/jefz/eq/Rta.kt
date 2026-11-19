package com.jefz.eq

import android.content.Context
import android.graphics.Bitmap
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** One analysis frame. Values are dBFS per FFT bin (0..n/2). */
class Spec(
    val binHz: Float,
    val mic: FloatArray,
    val internal: FloatArray?,
    val peak: FloatArray,
    val peakHz: Float,
    val peakDb: Float
)

/** Single-writer / single-reader mono ring buffer. */
class SampleRing(private val size: Int) {
    private val buf = FloatArray(size)
    @Volatile private var wp = 0L

    fun push(src: ShortArray, count: Int, stereo: Boolean) {
        var w = wp
        if (stereo) {
            var i = 0
            while (i + 1 < count) {
                buf[(w % size).toInt()] = (src[i] + src[i + 1]) / 65536f
                w++; i += 2
            }
        } else {
            for (i in 0 until count) {
                buf[(w % size).toInt()] = src[i] / 32768f
                w++
            }
        }
        wp = w
    }

    fun snapshot(dst: FloatArray) {
        val end = wp
        for (i in dst.indices) dst[i] = buf[Math.floorMod(end - dst.size + i, size.toLong()).toInt()]
    }
}

class Fft(val n: Int) {
    private val cosT = FloatArray(n / 2) { cos(2 * PI * it / n).toFloat() }
    private val sinT = FloatArray(n / 2) { sin(2 * PI * it / n).toFloat() }
    private val win = FloatArray(n) { (0.5 - 0.5 * cos(2 * PI * it / (n - 1))).toFloat() }
    private val rev = IntArray(n).also { r ->
        val bits = Integer.numberOfTrailingZeros(n)
        for (i in 0 until n) r[i] = Integer.reverse(i) ushr (32 - bits)
    }
    private val re = FloatArray(n)
    private val im = FloatArray(n)

    /** x (n samples, -1..1) -> out (n/2+1 bins, dBFS). */
    fun db(x: FloatArray, out: FloatArray) {
        for (i in 0 until n) { re[rev[i]] = x[i] * win[i]; im[rev[i]] = 0f }
        var len = 2
        while (len <= n) {
            val half = len / 2
            val step = n / len
            var i = 0
            while (i < n) {
                for (j in 0 until half) {
                    val k = j * step
                    val wr = cosT[k]; val wi = -sinT[k]
                    val a = i + j; val b = a + half
                    val vr = re[b] * wr - im[b] * wi
                    val vi = re[b] * wi + im[b] * wr
                    re[b] = re[a] - vr; im[b] = im[a] - vi
                    re[a] += vr; im[a] += vi
                }
                i += len
            }
            len = len shl 1
        }
        val ref = n / 4f
        for (k in 0..n / 2) {
            val mag = sqrt(re[k] * re[k] + im[k] * im[k])
            out[k] = (20f * log10(mag / ref + 1e-6f)).coerceIn(-120f, 0f)
        }
    }
}

object Rta {
    const val SR = 48000

    var on by mutableStateOf(false)
    var fft by mutableIntStateOf(4096)
    var log by mutableStateOf(true)
    var showMic by mutableStateOf(true)
    var showInt by mutableStateOf(true)
    var showCurve by mutableStateOf(true)
    var peakHold by mutableStateOf(true)
    var frame by mutableStateOf<Spec?>(null)

    val micRing = SampleRing(16384)
    val intRing = SampleRing(16384)
    @Volatile var internalActive = false

    @Volatile private var running = false
    private var thread: Thread? = null
    private var micThread: Thread? = null
    private var micRec: AudioRecord? = null

    @Synchronized
    fun start(ctx: Context) {
        if (running) return
        running = true
        if (!startMic(ctx.applicationContext)) {
            running = false
            on = false
            return
        }
        thread = Thread { loop() }.also { it.name = "JefzRta"; it.start() }
    }

    @Synchronized
    fun stop() {
        running = false
        try { thread?.join(400) } catch (_: Exception) {}
        try { micThread?.join(400) } catch (_: Exception) {}
        thread = null; micThread = null
        try { micRec?.stop() } catch (_: Exception) {}
        micRec?.release(); micRec = null
    }

    private fun startMic(ctx: Context): Boolean {
        val am = ctx.getSystemService(AudioManager::class.java)
        val unprocessed = am.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        val src = if (unprocessed) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.MIC
        val minB = AudioRecord.getMinBufferSize(SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        return try {
            val r = AudioRecord(src, SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(minB, 8192))
            if (r.state != AudioRecord.STATE_INITIALIZED) { r.release(); return false }
            r.startRecording()
            micRec = r
            micThread = Thread {
                val b = ShortArray(1024)
                while (running) {
                    val n = r.read(b, 0, b.size)
                    if (n < 0) break
                    if (n > 0) micRing.push(b, n, false)
                }
            }.also { it.name = "JefzMic"; it.start() }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun loop() {
        var n = 0
        var f: Fft? = null
        var x = FloatArray(0)
        var tmp = FloatArray(0)
        var micDb = FloatArray(0)
        var intDb = FloatArray(0)
        var peak = FloatArray(0)
        while (running) {
            val size = fft
            if (size != n) {
                n = size
                f = Fft(n)
                x = FloatArray(n)
                tmp = FloatArray(n / 2 + 1)
                micDb = FloatArray(n / 2 + 1) { -120f }
                intDb = FloatArray(n / 2 + 1) { -120f }
                peak = FloatArray(n / 2 + 1) { -120f }
            }
            val fx = f!!
            micRing.snapshot(x)
            fx.db(x, tmp)
            for (k in tmp.indices) {
                micDb[k] = 0.55f * micDb[k] + 0.45f * tmp[k]
                peak[k] = max(micDb[k], peak[k] - 0.5f)
            }
            val intOk = internalActive
            if (intOk) {
                intRing.snapshot(x)
                fx.db(x, tmp)
                for (k in tmp.indices) intDb[k] = 0.55f * intDb[k] + 0.45f * tmp[k]
            }
            val binHz = SR.toFloat() / n
            var pi = (20f / binHz).toInt().coerceAtLeast(1)
            var best = -120f
            val hi = (20000f / binHz).toInt().coerceAtMost(micDb.size - 1)
            for (k in pi..hi) if (micDb[k] > best) { best = micDb[k]; pi = k }
            frame = Spec(binHz, micDb.copyOf(), if (intOk) intDb.copyOf() else null, peak.copyOf(), pi * binHz, best)
            try { Thread.sleep(33) } catch (_: InterruptedException) {}
        }
    }
}

fun freqAt(t: Double, log: Boolean): Double =
    if (log) 20.0 * 1000.0.pow(t) else 20.0 + t * 19980.0

fun xOf(f: Double, log: Boolean): Double =
    if (log) ln(f / 20.0) / ln(1000.0) else (f - 20.0) / 19980.0

fun mapColumns(db: FloatArray, binHz: Float, cols: Int, log: Boolean, out: FloatArray) {
    for (c in 0 until cols) {
        val f0 = freqAt(c.toDouble() / cols, log)
        val f1 = freqAt((c + 1.0) / cols, log)
        val b0 = (f0 / binHz).toFloat()
        val b1 = (f1 / binHz).toFloat()
        out[c] = if (b1 - b0 < 1f) {
            val pos = (b0 + b1) / 2f
            val i = pos.toInt().coerceIn(0, db.size - 2)
            val fr = (pos - i).coerceIn(0f, 1f)
            db[i] * (1 - fr) + db[i + 1] * fr
        } else {
            var m = -120f
            val e = minOf(b1.toInt() + 1, db.size - 1)
            for (i in b0.toInt().coerceAtLeast(0)..e) if (db[i] > m) m = db[i]
            m
        }
    }
}

/** Scrolling spectrogram (newest row on top). */
class SpectroBuf(val w: Int, val h: Int) {
    private val px = IntArray(w * h)
    val bmp: Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

    private fun color(db: Float): Int {
        val t = ((db + 100f) / 80f).coerceIn(0f, 1f)
        val r = (255 * ((t - 0.5f) * 2f).coerceIn(0f, 1f)).toInt()
        val g = (255 * ((t - 0.25f) * 1.6f).coerceIn(0f, 1f)).toInt()
        val b = (255 * (t * 3f).coerceIn(0f, 1f) * (1f - ((t - 0.5f) * 2f).coerceIn(0f, 1f))).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun push(row: FloatArray) {
        System.arraycopy(px, 0, px, w, w * (h - 1))
        for (c in 0 until w) px[c] = color(row[c])
        bmp.setPixels(px, 0, w, 0, 0, w, h)
    }
}
