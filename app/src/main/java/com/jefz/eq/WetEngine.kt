package com.jefz.eq

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.projection.MediaProjection
import android.util.Log
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Wet-only effects: captures audio of ONE app (AudioPlaybackCapture), runs it through
 * per-channel 5-band send -> Reverb (Freeverb) + Echo, and plays ONLY the wet signal.
 * The original (dry) sound keeps playing natively, so nothing is doubled.
 */
class WetEngine(private val proj: MediaProjection) {
    @Volatile private var running = false
    private var thread: Thread? = null

    fun start(uid: Int): Boolean {
        stop()
        val fs = 48000
        var rec: AudioRecord? = null
        var track: AudioTrack? = null
        try {
            val cfg = AudioPlaybackCaptureConfiguration.Builder(proj).addMatchingUid(uid).build()
            val inFmt = AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(fs).setChannelMask(AudioFormat.CHANNEL_IN_STEREO).build()
            val minIn = AudioRecord.getMinBufferSize(fs, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT)
            rec = AudioRecord.Builder().setAudioPlaybackCaptureConfig(cfg).setAudioFormat(inFmt)
                .setBufferSizeInBytes(max(minIn, 16384)).build()
            val minOut = AudioTrack.getMinBufferSize(fs, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
                )
                .setAudioFormat(
                    AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(fs).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build()
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(max(minOut, 16384))
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
        } catch (e: Exception) {
            Log.w("JefzEQ", "wet init failed", e)
            rec?.release(); track?.release()
            return false
        }
        val r = rec!!
        val t = track!!
        running = true
        thread = Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            val chL = Chain(fs, 0)
            val chR = Chain(fs, 23)
            val frames = 512
            val inB = ShortArray(frames * 2)
            val outB = ShortArray(frames * 2)
            try {
                r.startRecording()
                t.play()
                while (running) {
                    val n = r.read(inB, 0, inB.size)
                    if (n < 0) break
                    if (n == 0) continue
                    val s = EqRepo.state.value
                    chL.configure(s, s.sendL, fs)
                    chR.configure(s, s.sendR, fs)
                    val nf = n / 2
                    for (i in 0 until nf) {
                        val wl = chL.step(inB[2 * i] / 32768f)
                        val wr = chR.step(inB[2 * i + 1] / 32768f)
                        outB[2 * i] = clip(wl)
                        outB[2 * i + 1] = clip(wr)
                    }
                    t.write(outB, 0, nf * 2)
                }
            } catch (e: Exception) {
                Log.w("JefzEQ", "wet loop failed", e)
            } finally {
                try { r.stop() } catch (_: Exception) {}
                try { t.stop() } catch (_: Exception) {}
                r.release(); t.release()
            }
        }.also { it.name = "JefzWet"; it.start() }
        return true
    }

    fun stop() {
        running = false
        try { thread?.join(800) } catch (_: Exception) {}
        thread = null
    }

    private fun clip(v: Float): Short = (v * 32768f).coerceIn(-32768f, 32767f).toInt().toShort()

    // ---------- DSP ----------

    private class Lp(fs: Int, fc: Double) {
        val b0: Float; val b1: Float; val b2: Float; val a1: Float; val a2: Float
        var z1 = 0f; var z2 = 0f
        init {
            val w0 = 2 * PI * fc / fs
            val alpha = sin(w0) / (2 * 0.7071)
            val c = cos(w0)
            val a0 = 1 + alpha
            b0 = (((1 - c) / 2) / a0).toFloat()
            b1 = ((1 - c) / a0).toFloat()
            b2 = b0
            a1 = ((-2 * c) / a0).toFloat()
            a2 = ((1 - alpha) / a0).toFloat()
        }
        fun p(x: Float): Float {
            val y = b0 * x + z1
            z1 = b1 * x - a1 * y + z2
            z2 = b2 * x - a2 * y
            return y
        }
    }

    private class Comb(n: Int) {
        val buf = FloatArray(n); var i = 0; var store = 0f
        var fb = 0.84f; var d1 = 0.2f
        fun p(x: Float): Float {
            val o = buf[i]
            store = o * (1f - d1) + store * d1
            buf[i] = x + store * fb
            if (++i >= buf.size) i = 0
            return o
        }
    }

    private class AllPass(n: Int) {
        val buf = FloatArray(n); var i = 0
        fun p(x: Float): Float {
            val b = buf[i]
            val o = -x + b
            buf[i] = x + b * 0.5f
            if (++i >= buf.size) i = 0
            return o
        }
    }

    /** One channel: 5-band send split -> reverb + echo (wet only). */
    private class Chain(fs: Int, spread: Int) {
        private val lp = arrayOf(Lp(fs, 80.0), Lp(fs, 250.0), Lp(fs, 2000.0), Lp(fs, 6000.0))
        private val combs: Array<Comb>
        private val aps: Array<AllPass>
        private val echo = FloatArray(fs + fs / 5)
        private var wp = 0
        private val g = FloatArray(5)
        private var rvMix = 0f
        private var ecMix = 0f
        private var ecIn = 0f
        private var delay = 1
        private var fb = 0f

        init {
            val ct = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
            val at = intArrayOf(556, 441, 341, 225)
            combs = Array(8) { Comb((ct[it] + spread) * fs / 44100) }
            aps = Array(4) { AllPass((at[it] + spread) * fs / 44100) }
        }

        fun configure(s: EqState, send: List<Boolean>, fs: Int) {
            for (k in 0 until 5) g[k] = if (send.getOrElse(k) { true }) 1f else 0f
            val f = s.fx
            rvMix = if (f.rvOn) f.rvMix / 100f else 0f
            val feedback = 0.7f + 0.28f * (f.rvRoom / 100f)
            val damp = f.rvDamp / 100f * 0.4f
            for (c in combs) { c.fb = feedback; c.d1 = damp }
            ecMix = if (f.ecOn) f.ecMix / 100f else 0f
            ecIn = if (f.ecOn) 1f else 0f
            delay = (f.ecTime / 1000f * fs).toInt().coerceIn(1, echo.size - 1)
            fb = (f.ecFb / 100f * 0.9f).coerceIn(0f, 0.9f)
        }

        fun step(x: Float): Float {
            val y1 = lp[0].p(x); val y2 = lp[1].p(x); val y3 = lp[2].p(x); val y4 = lp[3].p(x)
            val send = g[0] * y1 + g[1] * (y2 - y1) + g[2] * (y3 - y2) + g[3] * (y4 - y3) + g[4] * (x - y4)
            val inp = send * 0.015f
            var s = 0f
            for (c in combs) s += c.p(inp)
            for (a in aps) s = a.p(s)
            val rv = s * 3f * rvMix
            val rp = (wp - delay + echo.size) % echo.size
            val d = echo[rp]
            echo[wp] = send * ecIn + d * fb
            wp = (wp + 1) % echo.size
            return rv + d * ecMix
        }
    }
}
