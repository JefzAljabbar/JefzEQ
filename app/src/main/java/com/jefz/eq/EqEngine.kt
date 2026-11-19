package com.jefz.eq

import android.media.audiofx.DynamicsProcessing
import android.util.Log
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/**
 * System-wide effect chain via DynamicsProcessing on global session 0:
 * input gain (PreAmp x Volume x Balance) -> Pre-EQ -> MBC (1 band compressor) -> Limiter (+Gain).
 */
class EqEngine {
    private var dp: DynamicsProcessing? = null
    private var freqs = DoubleArray(0)
    private var full = true
    val running: Boolean get() = dp != null

    fun start(): Boolean {
        release()
        for (useFull in booleanArrayOf(true, false)) {
            for (cnt in intArrayOf(48, 24)) {
                try {
                    val cfg = DynamicsProcessing.Config.Builder(
                        DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                        2, true, cnt, useFull, if (useFull) 1 else 0, false, 0, useFull
                    ).build()
                    val d = DynamicsProcessing(0, 0, cfg)
                    d.setPreEqAllChannelsTo(DynamicsProcessing.Eq(true, true, cnt))
                    if (useFull) d.setMbcAllChannelsTo(DynamicsProcessing.Mbc(true, true, 1))
                    d.setEnabled(true)
                    freqs = DoubleArray(cnt) { 20.0 * 1000.0.pow(it / (cnt - 1.0)) }
                    full = useFull
                    dp = d
                    return true
                } catch (e: Exception) {
                    Log.w("JefzEQ", "DynamicsProcessing full=$useFull bands=$cnt failed", e)
                }
            }
        }
        return false
    }

    private fun lin2db(x: Double) = 20 * log10(max(x, 0.001))

    fun apply(s: EqState) {
        val d = dp ?: return
        try {
            // PreAmp + Volume + Balance -> per-channel input gain
            val volDb = lin2db(s.volume / 100.0)
            val bal = (s.balance / 100.0).coerceIn(-1.0, 1.0)
            val lDb = s.preamp + volDb + lin2db(if (bal > 0) 1 - bal else 1.0)
            val rDb = s.preamp + volDb + lin2db(if (bal < 0) 1 + bal else 1.0)
            d.setInputGainbyChannel(0, lDb.coerceIn(-80.0, 24.0).toFloat())
            d.setInputGainbyChannel(1, rDb.coerceIn(-80.0, 24.0).toFloat())

            // EQ curve
            val l = Dsp.curve(s, Chan.L, freqs)
            val r = Dsp.curve(s, Chan.R, freqs)
            for (i in freqs.indices) {
                val f = freqs[i].toFloat()
                d.setPreEqBandByChannelIndex(0, i, DynamicsProcessing.EqBand(true, f, l[i].toFloat()))
                d.setPreEqBandByChannelIndex(1, i, DynamicsProcessing.EqBand(true, f, r[i].toFloat()))
            }

            if (full) {
                val c = s.comp
                d.setMbcBandAllChannelsTo(
                    0,
                    DynamicsProcessing.MbcBand(
                        c.on, 20000f, c.attack, c.release, c.ratio, c.threshold,
                        6f, -90f, 1f, 0f, c.makeup
                    )
                )
                d.setLimiterAllChannelsTo(
                    DynamicsProcessing.Limiter(true, true, 0, 1f, 60f, 20f, -1f, s.gain)
                )
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
