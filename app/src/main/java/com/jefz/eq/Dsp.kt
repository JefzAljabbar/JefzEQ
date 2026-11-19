package com.jefz.eq

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** RBJ biquad magnitude response (dB). Used for UI curve and to feed the system EQ. */
object Dsp {
    private const val FS = 48000.0
    const val MIN_DB = -60.0
    const val MAX_DB = 24.0

    fun bandDb(b: Band, f: Double): Double {
        val f0 = b.freq.toDouble().coerceIn(10.0, 22000.0)
        val q = b.q.toDouble().coerceIn(0.1, 20.0)
        val g = b.gain.toDouble()
        val w0 = 2 * PI * f0 / FS
        val c = cos(w0)
        val s = sin(w0)
        val alpha = s / (2 * q)
        val a = 10.0.pow(g / 40.0)
        val sa = 2 * sqrt(a) * alpha

        val b0: Double; val b1: Double; val b2: Double
        val a0: Double; val a1: Double; val a2: Double
        when (b.type) {
            BandType.PEAK -> {
                b0 = 1 + alpha * a; b1 = -2 * c; b2 = 1 - alpha * a
                a0 = 1 + alpha / a; a1 = -2 * c; a2 = 1 - alpha / a
            }
            BandType.LOW_PASS -> {
                b0 = (1 - c) / 2; b1 = 1 - c; b2 = (1 - c) / 2
                a0 = 1 + alpha; a1 = -2 * c; a2 = 1 - alpha
            }
            BandType.HIGH_PASS -> {
                b0 = (1 + c) / 2; b1 = -(1 + c); b2 = (1 + c) / 2
                a0 = 1 + alpha; a1 = -2 * c; a2 = 1 - alpha
            }
            BandType.BAND_PASS -> {
                b0 = alpha; b1 = 0.0; b2 = -alpha
                a0 = 1 + alpha; a1 = -2 * c; a2 = 1 - alpha
            }
            BandType.LOW_SHELF -> {
                b0 = a * ((a + 1) - (a - 1) * c + sa)
                b1 = 2 * a * ((a - 1) - (a + 1) * c)
                b2 = a * ((a + 1) - (a - 1) * c - sa)
                a0 = (a + 1) + (a - 1) * c + sa
                a1 = -2 * ((a - 1) + (a + 1) * c)
                a2 = (a + 1) + (a - 1) * c - sa
            }
            BandType.HIGH_SHELF -> {
                b0 = a * ((a + 1) + (a - 1) * c + sa)
                b1 = -2 * a * ((a - 1) + (a + 1) * c)
                b2 = a * ((a + 1) + (a - 1) * c - sa)
                a0 = (a + 1) - (a - 1) * c + sa
                a1 = 2 * ((a - 1) - (a + 1) * c)
                a2 = (a + 1) - (a - 1) * c - sa
            }
        }

        val w = 2 * PI * f.coerceIn(1.0, FS / 2 * 0.999) / FS
        val cw = cos(w); val c2 = cos(2 * w); val sw = sin(w); val s2 = sin(2 * w)
        val nr = b0 + b1 * cw + b2 * c2
        val ni = b1 * sw + b2 * s2
        val dr = a0 + a1 * cw + a2 * c2
        val di = a1 * sw + a2 * s2
        val db = 10 * log10((nr * nr + ni * ni + 1e-20) / (dr * dr + di * di + 1e-20))
        // LP/HP/BP have no native gain: gain sets the passband level.
        return when (b.type) {
            BandType.LOW_PASS, BandType.HIGH_PASS, BandType.BAND_PASS -> db + g
            else -> db
        }
    }

    private val LO = doubleArrayOf(Double.NaN, 80.0, 250.0, 2000.0, 6000.0)
    private val HI = doubleArrayOf(80.0, 250.0, 2000.0, 6000.0, Double.NaN)

    private fun smooth(t: Double): Double {
        val x = t.coerceIn(0.0, 1.0)
        return x * x * (3 - 2 * x)
    }

    /** Knob percent (-100..100) to dB. -100% = -60 dB (mute). */
    fun knobDb(p: Float): Double =
        if (p >= 0f) p * 0.12 else -60.0 * (abs(p) / 100.0).pow(2.0)

    private fun knobWeight(k: Int, f: Double): Double {
        val x = ln(f.coerceAtLeast(1.0)) / ln(2.0)
        val lo = if (LO[k].isNaN()) 1.0 else smooth((x - (ln(LO[k]) / ln(2.0) - 0.5)) / 1.0)
        val hi = if (HI[k].isNaN()) 1.0 else 1.0 - smooth((x - (ln(HI[k]) / ln(2.0) - 0.5)) / 1.0)
        return lo * hi
    }

    private fun knobsDb(knobs: List<Float>, f: Double): Double {
        var sum = 0.0
        for (k in 0 until 5) {
            val v = knobs.getOrElse(k) { 0f }
            if (v != 0f) sum += knobWeight(k, f) * knobDb(v)
        }
        return sum
    }

    /** Combined response of one channel (L or R) at the given frequencies, in dB. */
    fun curve(s: EqState, ch: Chan, freqs: DoubleArray): DoubleArray {
        val knobs = if (ch == Chan.R) s.knobsR else s.knobsL
        val active = s.bands.filter { it.on && (it.chan == Chan.LR || it.chan == ch) }
        return DoubleArray(freqs.size) { i ->
            var out = 0.0
            if (s.mode == EqMode.CASCADE) {
                for (b in active) out += bandDb(b, freqs[i])
            } else {
                var best = 0.0
                for (b in active) {
                    val v = bandDb(b, freqs[i])
                    if (abs(v) > abs(best)) best = v
                }
                out = best
            }
            (out + knobsDb(knobs, freqs[i])).coerceIn(MIN_DB, MAX_DB)
        }
    }
}
