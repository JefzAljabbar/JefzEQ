package com.jefz.eq

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/** Test signal generator: Pink Noise and Tone (0 - 25 kHz). Plays through the normal output (so the EQ applies). */
object Gen {
    enum class Mode { OFF, PINK, TONE }
    enum class Wave(val label: String) { SINE("Sine"), SQUARE("Square"), TRI("Triangle"), SAW("Saw") }

    var mode by mutableStateOf(Mode.OFF)
    var wave by mutableStateOf(Wave.SINE)
    var freq by mutableFloatStateOf(1000f)
    var levelDb by mutableFloatStateOf(-30f)
    var chan by mutableStateOf(Chan.LR)

    fun resetOutput() { levelDb = -30f; chan = Chan.LR }
    fun resetTone() { freq = 1000f; wave = Wave.SINE }

    @Volatile private var running = false
    @Volatile private var muting = false
    private var thread: Thread? = null

    @Synchronized
    fun set(m: Mode) {
        halt()
        mode = m
        if (m != Mode.OFF) begin(m)
    }

    @Synchronized
    fun stop() {
        halt()
        mode = Mode.OFF
    }

    private fun halt() {
        if (thread == null) return
        muting = true
        try { Thread.sleep(90) } catch (_: InterruptedException) {}
        running = false
        try { thread?.join(500) } catch (_: Exception) {}
        thread = null
        muting = false
    }

    private class Pink(seed: Int) {
        private var s = (seed * -1640531527) or 1
        private var b0 = 0f; private var b1 = 0f; private var b2 = 0f
        private var b3 = 0f; private var b4 = 0f; private var b5 = 0f; private var b6 = 0f

        private fun white(): Float {
            s = s xor (s shl 13); s = s xor (s ushr 17); s = s xor (s shl 5)
            return s.toFloat() / 2147483648f
        }

        fun next(): Float {
            val w = white()
            b0 = 0.99886f * b0 + w * 0.0555179f
            b1 = 0.99332f * b1 + w * 0.0750759f
            b2 = 0.96900f * b2 + w * 0.1538520f
            b3 = 0.86650f * b3 + w * 0.3104856f
            b4 = 0.55000f * b4 + w * 0.5329522f
            b5 = -0.7616f * b5 - w * 0.0168980f
            val out = b0 + b1 + b2 + b3 + b4 + b5 + b6 + w * 0.5362f
            b6 = w * 0.115926f
            return out * 0.11f
        }
    }

    private fun osc(w: Wave, p: Double): Float = when (w) {
        Wave.SINE -> sin(2 * PI * p).toFloat()
        Wave.SQUARE -> if (p < 0.5) 0.7f else -0.7f
        Wave.TRI -> (4 * abs(p - 0.5) - 1).toFloat()
        Wave.SAW -> (0.7 * (2 * p - 1)).toFloat()
    }

    private fun begin(m: Mode) {
        // 96 kHz for the tone so that frequencies above 24 kHz can exist (actual output depends on hardware).
        val fs = if (m == Mode.TONE) 96000 else 48000
        val track: AudioTrack
        try {
            val minB = AudioTrack.getMinBufferSize(fs, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
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
                .setBufferSizeInBytes(max(minB, 16384))
                .build()
        } catch (e: Exception) {
            Log.w("JefzEQ", "gen init failed", e)
            mode = Mode.OFF
            return
        }
        running = true
        thread = Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            val frames = 1024
            val buf = ShortArray(frames * 2)
            val pl = Pink(1)
            val pr = Pink(7)
            var phase = 0.0
            var gain = 0f
            try {
                track.play()
                while (running) {
                    val lv = levelDb
                    val target = if (muting || lv <= -60f) 0f else 10f.pow(lv / 20f)
                    val ch = chan
                    val f = freq.toDouble()
                    val inc = f / fs
                    val w = wave
                    for (i in 0 until frames) {
                        gain += (target - gain) * 0.002f
                        var l: Float
                        var r: Float
                        if (m == Mode.TONE) {
                            val v = if (f < 1.0) 0f else osc(w, phase)
                            l = v; r = v
                            phase += inc
                            if (phase >= 1.0) phase -= 1.0
                        } else {
                            l = pl.next(); r = pr.next()
                        }
                        l *= gain; r *= gain
                        if (ch == Chan.R) l = 0f
                        if (ch == Chan.L) r = 0f
                        buf[2 * i] = (l * 32767f).coerceIn(-32768f, 32767f).toInt().toShort()
                        buf[2 * i + 1] = (r * 32767f).coerceIn(-32768f, 32767f).toInt().toShort()
                    }
                    track.write(buf, 0, buf.size)
                }
            } catch (e: Exception) {
                Log.w("JefzEQ", "gen loop failed", e)
            } finally {
                try { track.stop() } catch (_: Exception) {}
                track.release()
            }
        }.also { it.name = "JefzGen"; it.start() }
    }
}
