package com.jefz.eq

import android.media.audiofx.DynamicsProcessing
import android.util.Log
import kotlin.math.pow

/**
 * System-wide EQ via DynamicsProcessing on global session 0 (affects YouTube and other apps).
 * The user's parametric bands are combined into one curve per channel and sampled
 * at fixed log-spaced engine bands.
 */
class EqEngine {
    private var dp: DynamicsProcessing? = null
    private var freqs = DoubleArray(0)
    val running: Boolean get() = dp != null

    fun start(): Boolean {
        release()
        for (cnt in intArrayOf(48, 24)) {
            try {
                val cfg = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2, true, cnt, false, 0, false, 0, false
                ).build()
                val d = DynamicsProcessing(0, 0, cfg)
                d.setPreEqAllChannelsTo(DynamicsProcessing.Eq(true, true, cnt))
                d.setEnabled(true)
                freqs = DoubleArray(cnt) { 20.0 * 1000.0.pow(it / (cnt - 1.0)) }
                dp = d
                return true
            } catch (e: Exception) {
                Log.w("JefzEQ", "DynamicsProcessing $cnt bands failed", e)
            }
        }
        return false
    }

    fun apply(s: EqState) {
        val d = dp ?: return
        val l = Dsp.curve(s, Chan.L, freqs)
        val r = Dsp.curve(s, Chan.R, freqs)
        try {
            for (i in freqs.indices) {
                val f = freqs[i].toFloat()
                d.setPreEqBandByChannelIndex(0, i, DynamicsProcessing.EqBand(true, f, l[i].toFloat()))
                d.setPreEqBandByChannelIndex(1, i, DynamicsProcessing.EqBand(true, f, r[i].toFloat()))
            }
        } catch (e: Exception) {
            Log.w("JefzEQ", "apply failed", e)
        }
    }

    fun release() {
        try { dp?.setEnabled(false); dp?.release() } catch (_: Exception) {}
        dp = null
    }
}
