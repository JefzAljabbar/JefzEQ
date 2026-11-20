package com.jefz.eq

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round

/**
 * Auto calibration: compares RTA out (microphone) with the reference (RTA in, or ideal pink noise) per 1/3 octave
 * and adds Peaking bands (flag "cal") that cancel the difference. Closed loop, up to MAX_PASS corrections + 1 check.
 *
 * Channel rule (Tone): a frequency region whose Tone knob of a channel is at -100% is not allowed for that channel,
 * so no band is created for that channel there.
 */
object Calib {
    enum class Src(val label: String) { INTERNAL("Internal (RTA in)"), PINK("Pink Noise") }

    var src by mutableStateOf(Src.PINK)
    var running by mutableStateOf(false)
    var status by mutableStateOf("Siap.")
    var progress by mutableFloatStateOf(0f)

    private const val N = 24                       // 1/3 octave centers, 62.5 Hz .. 12.7 kHz
    private val FC = DoubleArray(N) { 1000.0 * 2.0.pow((it - 12) / 3.0) }
    private const val Q = 2.5
    private const val MAX_PASS = 3
    private const val MAX_NEW = 6                  // band edits per channel per pass
    private const val STOP_DB = 2.0                // converged when the worst error is below this
    private const val MIN_DB = 1.5                 // ignore errors smaller than this when fitting
    private const val DAMP = 0.8
    private const val MEASURE_SEC = 3.5
    private const val SETTLE_MS = 800L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var savedFft = 0
    private var genStarted = false
    private var savedLevel = -30f
    private var savedChan = Chan.LR

    fun stop() { job?.cancel() }

    fun start(requestRta: () -> Unit) {
        if (running) return
        running = true
        progress = 0f
        job = scope.launch {
            try {
                runCal(requestRta)
            } catch (e: CancellationException) {
                status = "Dibatalkan."
            } catch (e: Exception) {
                status = "Gagal: ${e.message}"
            } finally {
                if (savedFft != 0) { Rta.fft = savedFft; savedFft = 0 }
                if (genStarted) { Gen.stop(); Gen.levelDb = savedLevel; Gen.chan = savedChan; genStarted = false }
                running = false
                progress = 0f
            }
        }
    }

    private fun knobIdx(f: Double) = when {
        f < 80.0 -> 0
        f < 250.0 -> 1
        f < 2000.0 -> 2
        f < 6000.0 -> 3
        else -> 4
    }

    private fun allowed(s: EqState, ch: Chan, i: Int): Boolean {
        val k = knobIdx(FC[i])
        val v = if (ch == Chan.R) s.knobsR.getOrElse(k) { 0f } else s.knobsL.getOrElse(k) { 0f }
        return v > -99.5f
    }

    /** Channel a new band gets at bin i (null = nothing allowed there). */
    private fun chanFor(s: EqState, ch: Chan, i: Int): Chan? {
        if (ch != Chan.LR) return if (allowed(s, ch, i)) ch else null
        val l = allowed(s, Chan.L, i)
        val r = allowed(s, Chan.R, i)
        return when {
            l && r -> Chan.LR
            l -> Chan.L
            r -> Chan.R
            else -> null
        }
    }

    /** Band energy (dB, arbitrary reference) per 1/3 octave from per-bin dBFS. Pink noise comes out flat. */
    private fun levels(db: FloatArray, binHz: Float): DoubleArray = DoubleArray(N) { i ->
        val lo = FC[i] / 2.0.pow(1.0 / 6.0)
        val hi = FC[i] * 2.0.pow(1.0 / 6.0)
        var b0 = ceil(lo / binHz).toInt().coerceIn(1, db.size - 1)
        var b1 = floor(hi / binHz).toInt().coerceIn(1, db.size - 1)
        if (b1 < b0) { b0 = round(FC[i] / binHz).toInt().coerceIn(1, db.size - 1); b1 = b0 }
        var p = 0.0
        for (k in b0..b1) p += 10.0.pow(db[k] / 10.0)
        val mean = p / (b1 - b0 + 1)
        10.0 * log10(mean * ((hi - lo) / binHz) + 1e-15)
    }

    private fun median(v: List<Double>): Double {
        val s = v.sorted()
        return if (s.isEmpty()) 0.0 else s[s.size / 2]
    }

    /** Average mic (and internal) band levels for MEASURE_SEC. Returns (in or null) to out, or null if no frames came. */
    private suspend fun measure(base: Float, span: Float, wantIn: Boolean): Pair<DoubleArray?, DoubleArray>? {
        val so = DoubleArray(N)
        val si = DoubleArray(N)
        var n = 0
        var ni = 0
        var last: Spec? = null
        val t0 = System.currentTimeMillis()
        val total = (MEASURE_SEC * 1000).toLong()
        while (true) {
            val el = System.currentTimeMillis() - t0
            if (el >= total) break
            progress = base + span * (el.toFloat() / total)
            delay(40)
            val f = Rta.frame ?: continue
            if (f === last) continue
            last = f
            val lo = levels(f.mic, f.binHz)
            for (i in 0 until N) so[i] += 10.0.pow(lo[i] / 10.0)
            n++
            val ia = f.internal
            if (wantIn && ia != null) {
                val li = levels(ia, f.binHz)
                for (i in 0 until N) si[i] += 10.0.pow(li[i] / 10.0)
                ni++
            }
        }
        if (n < 8) return null
        val out = DoubleArray(N) { 10.0 * log10(so[it] / n + 1e-15) }
        val inn = if (wantIn && ni >= 8) DoubleArray(N) { 10.0 * log10(si[it] / ni + 1e-15) } else null
        return inn to out
    }

    /** Greedy peak fitting of the error curve into Peaking bands (existing calibration bands are refined, not duplicated). */
    private fun fit(
        bands: MutableList<Band>, e: DoubleArray, ch: Chan, s: EqState,
        calIdx: MutableMap<Pair<Chan, Int>, Long>, nextId: LongArray
    ): Int {
        val use = BooleanArray(N) { chanFor(s, ch, it) != null }
        val res = DoubleArray(N) { if (use[it]) e[it] else 0.0 }
        var cnt = 0
        while (cnt < MAX_NEW) {
            var bi = -1
            var bv = 0.0
            for (i in 0 until N) if (abs(res[i]) > abs(bv)) { bv = res[i]; bi = i }
            if (bi < 0 || abs(bv) < MIN_DB) break
            val g = (bv * DAMP).coerceIn(-12.0, 12.0)
            val bch = chanFor(s, ch, bi) ?: break
            val key = bch to bi
            val id = calIdx[key]
            val idx = if (id != null) bands.indexOfFirst { it.id == id } else -1
            if (idx >= 0) {
                bands[idx] = bands[idx].copy(gain = (bands[idx].gain + g.toFloat()).coerceIn(-20f, 20f))
            } else {
                val nb = Band(id = nextId[0]++, chan = bch, freq = FC[bi].toFloat(), gain = g.toFloat(), q = Q.toFloat(), cal = true)
                bands.add(nb)
                calIdx[key] = nb.id
            }
            val d = Band(id = -1, freq = FC[bi].toFloat(), gain = g.toFloat(), q = Q.toFloat())
            for (i in 0 until N) if (use[i]) res[i] -= Dsp.bandDb(d, FC[i])
            cnt++
        }
        return cnt
    }

    private suspend fun runCal(requestRta: () -> Unit) {
        val usePink = src == Src.PINK
        var s = EqRepo.state.value
        if (!s.power) { status = "Nyalakan EQ dulu (switch kanan atas)."; return }
        if (s.mode != EqMode.CASCADE) { status = "Kalibrasi butuh mode Cascade (tab Bands)."; return }
        if (!Rta.on) {
            status = "Menyalakan RTA…"
            Handler(Looper.getMainLooper()).post { requestRta() }
        }
        val w0 = System.currentTimeMillis()
        while ((!Rta.on || Rta.frame == null) && System.currentTimeMillis() - w0 < 25000) delay(200)
        if (!Rta.on) { status = "RTA tidak aktif (izin mikrofon?)."; return }

        val chans = if (usePink) listOf(Chan.L, Chan.R) else listOf(Chan.LR)
        savedFft = Rta.fft
        Rta.fft = 8192
        if (usePink) {
            savedLevel = Gen.levelDb; savedChan = Gen.chan
            Gen.levelDb = -24f
            Gen.chan = chans[0]
            Gen.set(Gen.Mode.PINK)
            genStarted = true
        } else {
            delay(700)
            if (Rta.frame?.internal == null) {
                status = "RTA in belum aktif. Nyalakan capture app (tab FX), putar musik, lalu ulangi."
                return
            }
        }
        delay(SETTLE_MS)

        val calIdx = HashMap<Pair<Chan, Int>, Long>()
        var firstErr = Double.NaN
        var lastErr = 0.0
        var touched = 0
        val totalSteps = (MAX_PASS + 1) * chans.size
        var step = 0
        for (pass in 1..MAX_PASS + 1) {
            s = EqRepo.state.value
            var worst = 0.0
            val errs = ArrayList<Pair<Chan, DoubleArray>>()
            for (ch in chans) {
                val usable = BooleanArray(N) { chanFor(s, ch, it) != null }
                if (usable.none { it }) { step++; continue }
                if (usePink) { Gen.chan = ch; delay(SETTLE_MS) }
                status = "Pass $pass/${MAX_PASS + 1}: mengukur ${if (usePink) ch.label else "L+R"}…"
                val m = measure(step.toFloat() / totalSteps, 1f / totalSteps, !usePink)
                step++
                if (m == null) { status = "RTA tidak memberi data (app di background?)."; return }
                val (inn, out) = m
                if (!usePink && inn == null) { status = "RTA in kosong. Pastikan musik dari app yang di-capture sedang diputar."; return }
                val raw = DoubleArray(N) { (inn?.get(it) ?: 0.0) - out[it] }
                val outMean = (0 until N).filter { usable[it] }.map { out[it] }.average()
                if (outMean < -100.0) { status = "Sinyal mikrofon terlalu lemah. Dekatkan mic ke speaker / naikkan volume."; return }
                val med = median((0 until N).filter { usable[it] }.map { raw[it] })
                val e = DoubleArray(N) { if (usable[it]) raw[it] - med else 0.0 }
                worst = maxOf(worst, e.maxOf { abs(it) })
                errs.add(ch to e)
            }
            if (errs.isEmpty()) { status = "Semua channel -100% di Tone, tidak ada yang bisa dikalibrasi."; return }
            if (firstErr.isNaN()) firstErr = worst
            lastErr = worst
            if (worst < STOP_DB || pass == MAX_PASS + 1) break
            val bands = s.bands.toMutableList()
            val nextId = longArrayOf((bands.maxOfOrNull { it.id } ?: -1L) + 1)
            for ((ch, e) in errs) touched += fit(bands, e, ch, s, calIdx, nextId)
            EqRepo.update { it.copy(bands = bands) }
            delay(SETTLE_MS)
        }
        progress = 1f
        val nCal = EqRepo.state.value.bands.count { it.cal }
        status = if (lastErr < STOP_DB)
            "Selesai. Selisih maks %.1f → %.1f dB, %d band kalibrasi.".format(firstErr, lastErr, nCal)
        else
            "Selesai (batas pass). Selisih maks %.1f → %.1f dB, %d band kalibrasi. Ulangi bila perlu.".format(firstErr, lastErr, nCal)
    }
}
