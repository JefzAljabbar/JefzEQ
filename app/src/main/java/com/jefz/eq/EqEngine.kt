package com.jefz.eq

import android.media.audiofx.DynamicsProcessing
import android.util.Log
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/**
 * System-wide chain via DynamicsProcessing on global session 0:
 * input gain (PreAmp x Volume x Balance x Channel level) -> Pre-EQ -> MBC -> Limiter (+Gain).
 *
 * MBC = per-band compressors: each active band compressor covers the frequency region of its band;
 * the gaps use the Master compressor (if on). Max 8 band compressors per channel.
 */
class EqEngine {
    companion object {
        const val MAX_COMP = 8
        const val MBC_N = 2 * MAX_COMP + 1
    }

    private data class Seg(val cutoff: Float, val c: Comp?)

    private var dp: DynamicsProcessing? = null
    private var freqs = DoubleArray(0)
    private var full = true
    private var mbcN = 0
    private var lastL: List<Seg>? = null
    private var lastR: List<Seg>? = null
    val running: Boolean get() = dp != null

    fun start(): Boolean {
        release()
        val combos = listOf(true to MBC_N, true to 1, false to 0)
        for ((useFull, mbc) in combos) {
            for (cnt in intArrayOf(48, 24)) {
                try {
                    val cfg = DynamicsProcessing.Config.Builder(
                        DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                        2, true, cnt, useFull, mbc, false, 0, useFull
                    ).build()
                    val d = DynamicsProcessing(0, 0, cfg)
                    d.setPreEqAllChannelsTo(DynamicsProcessing.Eq(true, true, cnt))
                    if (useFull) d.setMbcAllChannelsTo(DynamicsProcessing.Mbc(true, mbc == 1, mbc))
                    d.setEnabled(true)
                    freqs = DoubleArray(cnt) { 20.0 * 1000.0.pow(it / (cnt - 1.0)) }
                    full = useFull
                    mbcN = mbc
                    lastL = null; lastR = null
                    dp = d
                    return true
                } catch (e: Exception) {
                    Log.w("JefzEQ", "DynamicsProcessing full=$useFull mbc=$mbc bands=$cnt failed", e)
                }
            }
        }
        return false
    }

    private fun lin2db(x: Double) = 20 * log10(max(x, 0.001))

    private fun off(cutoff: Float) =
        DynamicsProcessing.MbcBand(false, cutoff, 10f, 100f, 1f, 0f, 0f, -90f, 1f, 0f, 0f)

    private fun partition(s: EqState, ch: Chan): List<Seg> {
        val master = if (s.comp.on) s.comp else null
        val regs = s.bands
            .filter { it.on && it.comp.on && (it.chan == Chan.LR || it.chan == ch) }
            .take(MAX_COMP)
            .map { b -> val r = Dsp.region(b); Triple(r.first, r.second, b.comp) }
            .sortedBy { it.first }
        val segs = ArrayList<Seg>()
        var cur = 30.0
        for ((lo0, hi, c) in regs) {
            val lo = max(lo0, cur)
            if (hi - lo < lo * 0.05) continue
            if (lo > cur * 1.001) segs.add(Seg(lo.toFloat(), master))
            segs.add(Seg(hi.toFloat(), c))
            cur = hi
        }
        if (cur < 19999.0) segs.add(Seg(20000f, master))
        return segs
    }

    private fun applyMbc(d: DynamicsProcessing, ch: Int, segs: List<Seg>) {
        val anyOn = segs.any { it.c != null }
        d.setMbcByChannelIndex(ch, DynamicsProcessing.Mbc(true, anyOn, MBC_N))
        val pad = MBC_N - segs.size
        for (i in 0 until MBC_N) {
            val band = if (i < pad) {
                off((i + 1).toFloat())
            } else {
                val sg = segs[i - pad]
                val c = sg.c
                if (c == null) off(sg.cutoff)
                else DynamicsProcessing.MbcBand(
                    true, sg.cutoff, c.attack, c.release, c.ratio, c.threshold, 6f, -90f, 1f, 0f, c.makeup
                )
            }
            d.setMbcBandByChannelIndex(ch, i, band)
        }
    }

    fun apply(s: EqState) {
        val d = dp ?: return
        try {
            val volDb = lin2db(s.volume / 100.0)
            val bal = (s.balance / 100.0).coerceIn(-1.0, 1.0)
            val lDb = s.preamp + volDb + lin2db(if (bal > 0) 1 - bal else 1.0) + s.lvlL
            val rDb = s.preamp + volDb + lin2db(if (bal < 0) 1 + bal else 1.0) + s.lvlR
            d.setInputGainbyChannel(0, lDb.coerceIn(-80.0, 24.0).toFloat())
            d.setInputGainbyChannel(1, rDb.coerceIn(-80.0, 24.0).toFloat())

            val l = Dsp.curve(s, Chan.L, freqs)
            val r = Dsp.curve(s, Chan.R, freqs)
            for (i in freqs.indices) {
                val f = freqs[i].toFloat()
                d.setPreEqBandByChannelIndex(0, i, DynamicsProcessing.EqBand(true, f, l[i].toFloat()))
                d.setPreEqBandByChannelIndex(1, i, DynamicsProcessing.EqBand(true, f, r[i].toFloat()))
            }
        } catch (e: Exception) {
            Log.w("JefzEQ", "eq apply failed", e)
        }
        if (!full) return
        try {
            d.setLimiterAllChannelsTo(DynamicsProcessing.Limiter(true, true, 0, 1f, 60f, 20f, -1f, s.gain))
        } catch (e: Exception) {
            Log.w("JefzEQ", "limiter failed", e)
        }
        try {
            if (mbcN <= 1) {
                val c = s.comp
                d.setMbcBandAllChannelsTo(
                    0,
                    DynamicsProcessing.MbcBand(c.on, 20000f, c.attack, c.release, c.ratio, c.threshold, 6f, -90f, 1f, 0f, c.makeup)
                )
            } else {
                val sl = partition(s, Chan.L)
                val sr = partition(s, Chan.R)
                if (sl != lastL || sr != lastR) {
                    applyMbc(d, 0, sl)
                    applyMbc(d, 1, sr)
                    lastL = sl; lastR = sr
                }
            }
        } catch (e: Exception) {
            Log.w("JefzEQ", "mbc failed", e)
        }
    }

    fun release() {
        try { dp?.setEnabled(false); dp?.release() } catch (_: Exception) {}
        dp = null
        lastL = null; lastR = null
    }
}
