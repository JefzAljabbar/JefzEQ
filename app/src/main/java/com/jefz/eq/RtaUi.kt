package com.jefz.eq

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BG = Color(0xFF181D25)
private val MIC = Color(0xFF00E5FF)
private val INT = Color(0xFF69F0AE)
private val EQL = Color(0xFFFFEB3B)
private val EQR = Color(0xFFE040FB)

/** Spectroid-style RTA (mic = output, internal = input) with EQ curve overlay. */
@Composable
fun RtaView(state: EqState, modifier: Modifier) {
    val log = Rta.log
    val freqs = remember(log) { DoubleArray(240) { freqAt(it / 239.0, log) } }
    val cl = remember(state, log) { Dsp.curve(state, Chan.L, freqs) }
    val cr = remember(state, log) { Dsp.curve(state, Chan.R, freqs) }
    val paint = remember { android.graphics.Paint().apply { isAntiAlias = true } }

    Canvas(modifier.background(BG)) {
        val w = size.width
        val h = size.height
        val sp = Rta.frame
        val grid = Color(0xFF2A313B)
        fun yDb(db: Float) = h * (-(db.coerceIn(-100f, 0f)) / 100f)
        fun yEq(db: Double) = (h / 2f - db.coerceIn(-24.0, 24.0) / 24.0 * h / 2f).toFloat()
        fun x(f: Double) = (w * xOf(f, log)).toFloat()
        paint.textSize = 10.sp.toPx()

        fun label(t: String, px: Float, py: Float, c: Color) {
            paint.color = android.graphics.Color.argb((c.alpha * 255).toInt(), (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())
            drawIntoCanvas { it.nativeCanvas.drawText(t, px, py, paint) }
        }

        for (d in intArrayOf(-20, -40, -60, -80)) {
            drawLine(grid, Offset(0f, yDb(d.toFloat())), Offset(w, yDb(d.toFloat())), 1.dp.toPx())
            label("$d", 2.dp.toPx(), yDb(d.toFloat()) - 2.dp.toPx(), Color(0xFF5A6572))
        }
        val marks = if (log) doubleArrayOf(50.0, 100.0, 200.0, 500.0, 1000.0, 2000.0, 5000.0, 10000.0)
        else doubleArrayOf(4000.0, 8000.0, 12000.0, 16000.0, 20000.0)
        for (f in marks) {
            drawLine(grid, Offset(x(f), 0f), Offset(x(f), h), 1.dp.toPx())
            label(if (f >= 1000) "${(f / 1000).toInt()}k" else "${f.toInt()}", x(f) + 2.dp.toPx(), h - 3.dp.toPx(), Color(0xFF5A6572))
        }

        if (Rta.on && sp != null) {
            val cols = (w / 2.dp.toPx()).toInt().coerceIn(60, 500)
            val buf = FloatArray(cols)
            fun path(db: FloatArray, close: Boolean): Path {
                mapColumns(db, sp.binHz, cols, log, buf)
                return Path().apply {
                    if (close) moveTo(0f, h)
                    for (c in 0 until cols) {
                        val px = w * (c + 0.5f) / cols
                        if (c == 0 && !close) moveTo(px, yDb(buf[c])) else lineTo(px, yDb(buf[c]))
                    }
                    if (close) { lineTo(w, h); close() }
                }
            }
            if (Rta.showMic) {
                drawPath(path(sp.mic, true), Brush.verticalGradient(listOf(MIC.copy(alpha = 0.55f), MIC.copy(alpha = 0.05f))))
                drawPath(path(sp.mic, false), MIC, style = Stroke(1.5.dp.toPx()))
                if (Rta.peakHold) drawPath(path(sp.peak, false), Color.White.copy(alpha = 0.6f), style = Stroke(1.dp.toPx()))
            }
            val ia = sp.internal
            if (Rta.showInt && ia != null) drawPath(path(ia, false), INT, style = Stroke(1.5.dp.toPx()))
            if (Rta.showMic) {
                val t = if (sp.peakDb > -90f) "Peak %.0f Hz  %.1f dB".format(sp.peakHz, sp.peakDb) else "Peak --"
                label(t, w - 130.dp.toPx(), 12.dp.toPx(), Color.White)
            }
            var lx = 6.dp.toPx() + 14.dp.toPx()
            if (Rta.showMic) { label("Mic (out)", lx, 12.dp.toPx(), MIC); lx += 60.dp.toPx() }
            if (Rta.showInt) label(if (ia != null) "Int (in)" else "Int: capture off", lx, 12.dp.toPx(), INT)
        }

        if (Rta.showCurve) {
            fun curve(c: DoubleArray, col: Color) {
                val p = Path()
                c.forEachIndexed { i, d ->
                    val px = w * i / (c.size - 1)
                    if (i == 0) p.moveTo(px, yEq(d)) else p.lineTo(px, yEq(d))
                }
                drawPath(p, col, style = Stroke(2.dp.toPx()))
            }
            drawLine(Color.White.copy(alpha = 0.15f), Offset(0f, h / 2f), Offset(w, h / 2f), 1.dp.toPx())
            curve(cl, EQL)
            curve(cr, EQR)
            val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
            state.bands.filter { it.on && kotlin.math.abs(it.gain) > 0.05f }.forEach { b ->
                val bx = x(b.freq.toDouble().coerceIn(20.0, 20000.0))
                val col = when (b.chan) { Chan.L -> EQL; Chan.R -> EQR; else -> Color.White }
                drawLine(col.copy(alpha = 0.35f), Offset(bx, 0f), Offset(bx, h), 1.dp.toPx(), pathEffect = dash)
                val ch = if (b.chan == Chan.R) cr else cl
                val idx = (xOf(b.freq.toDouble().coerceIn(20.0, 20000.0), log) * (ch.size - 1)).toInt().coerceIn(0, ch.size - 1)
                drawCircle(col, 3.5.dp.toPx(), Offset(bx, yEq(ch[idx])))
            }
        }
    }
}

@Composable
fun RtaTab(s: EqState, onFxToggle: (Boolean) -> Unit, onRtaToggle: (Boolean) -> Unit) {
    val sg = remember { SpectroBuf(300, 110) }
    var ver by remember { mutableIntStateOf(0) }
    val frame = Rta.frame
    LaunchedEffect(frame) {
        val sp = frame ?: return@LaunchedEffect
        if (!Rta.on) return@LaunchedEffect
        val src = if (Rta.showMic || sp.internal == null) sp.mic else sp.internal
        val row = FloatArray(300)
        mapColumns(src, sp.binHz, 300, Rta.log, row)
        sg.push(row)
        ver++
    }
    val img = remember(sg) { sg.bmp.asImageBitmap() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = BG), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("RTA (mikrofon)", color = Color.White, modifier = Modifier.weight(1f))
                        Switch(checked = Rta.on, onCheckedChange = onRtaToggle)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(Rta.showMic, { Rta.showMic = !Rta.showMic }, { Text("Mic (output)") })
                        FilterChip(Rta.showInt, { Rta.showInt = !Rta.showInt }, { Text("Internal (input)") })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(Rta.showCurve, { Rta.showCurve = !Rta.showCurve }, { Text("EQ") })
                        FilterChip(Rta.peakHold, { Rta.peakHold = !Rta.peakHold }, { Text("Peak") })
                        FilterChip(Rta.log, { Rta.log = !Rta.log }, { Text(if (Rta.log) "Log" else "Linear") })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        intArrayOf(2048, 4096, 8192).forEach { n ->
                            FilterChip(Rta.fft == n, { Rta.fft = n }, { Text("FFT $n") })
                        }
                    }
                    if (!s.fxOn) {
                        Text(
                            "Grafik Internal (input) butuh capture app. Aktifkan dulu agar bisa dibandingkan dengan Mic (output).",
                            color = Color(0xFF8A94A3), fontSize = 11.sp
                        )
                        Button(onClick = { onFxToggle(true) }) { Text("Aktifkan capture internal") }
                    }
                }
            }
        }
        item {
            Text("Spectrogram", color = Color(0xFF00E5FF), fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Canvas(Modifier.fillMaxWidth().height(140.dp).background(Color.Black)) {
                val v = ver
                if (v >= 0) drawImage(img, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
