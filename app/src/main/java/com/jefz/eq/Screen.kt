package com.jefz.eq

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ln
import kotlin.math.pow

private val CYAN = Color(0xFF00E5FF)
private val ORANGE = Color(0xFFFF9100)
private val CARD = Color(0xFF181D25)

private fun update(b: Band) = EqRepo.update { s ->
    s.copy(bands = s.bands.map { if (it.id == b.id) b else it })
}

private fun fmtFreq(f: Float) = if (f >= 1000f) "%.2f kHz".format(f / 1000f) else "%.0f Hz".format(f)

@Composable
fun EqScreen(state: EqState) {
    Column(Modifier.fillMaxSize().background(Color(0xFF0E1116)).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("JefzEQ", fontSize = 22.sp, color = CYAN, modifier = Modifier.weight(1f))
            Switch(checked = state.power, onCheckedChange = { p -> EqRepo.update { it.copy(power = p) } })
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EqMode.values().forEach { m ->
                FilterChip(
                    selected = state.mode == m,
                    onClick = { EqRepo.update { it.copy(mode = m) } },
                    label = { Text(m.label) }
                )
            }
        }
        ResponseGraph(state, Modifier.fillMaxWidth().height(170.dp).padding(16.dp))
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(state.bands, key = { _, b -> b.id }) { i, b -> BandCard(i + 1, b) }
            item {
                Button(
                    onClick = {
                        EqRepo.update { s ->
                            val id = (s.bands.maxOfOrNull { it.id } ?: -1L) + 1
                            s.copy(bands = s.bands + Band(id = id))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("+ Tambah Band") }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun BandCard(index: Int, b: Band) {
    Card(colors = CardDefaults.cardColors(containerColor = CARD), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Band $index", color = Color.White, modifier = Modifier.weight(1f))
                Switch(checked = b.on, onCheckedChange = { update(b.copy(on = it)) })
                TextButton(onClick = {
                    EqRepo.update { s -> s.copy(bands = s.bands.filter { it.id != b.id }) }
                }) { Text("✕", color = Color(0xFFFF5252)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                var open by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { open = true }) { Text(b.type.label, fontSize = 12.sp) }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        BandType.values().forEach { t ->
                            DropdownMenuItem(text = { Text(t.label) }, onClick = { update(b.copy(type = t)); open = false })
                        }
                    }
                }
                Chan.values().forEach { c ->
                    FilterChip(selected = b.chan == c, onClick = { update(b.copy(chan = c)) }, label = { Text(c.label) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Gain %+.1f dB".format(b.gain), color = Color.White, fontSize = 12.sp)
                    Slider(value = b.gain, onValueChange = { update(b.copy(gain = it)) }, valueRange = -20f..20f)
                }
                Knob(b.freq, 20f, 20000f, "Freq", fmtFreq(b.freq), log = true) { update(b.copy(freq = it)) }
                Knob(b.q, 0.1f, 10f, "Q", "%.2f".format(b.q), log = true) { update(b.copy(q = it)) }
            }
        }
    }
}

@Composable
fun ResponseGraph(state: EqState, modifier: Modifier) {
    val freqs = remember { DoubleArray(240) { 20.0 * 1000.0.pow(it / 239.0) } }
    val l = remember(state) { Dsp.curve(state, Chan.L, freqs) }
    val r = remember(state) { Dsp.curve(state, Chan.R, freqs) }
    Canvas(modifier.background(CARD)) {
        val w = size.width
        val h = size.height
        fun y(db: Double) = (h / 2f - (db.coerceIn(-24.0, 24.0) / 24.0 * h / 2f)).toFloat()
        fun x(f: Double) = (w * ln(f / 20.0) / ln(1000.0)).toFloat()
        val grid = Color(0xFF2A313B)
        for (db in intArrayOf(-12, 0, 12)) drawLine(grid, Offset(0f, y(db.toDouble())), Offset(w, y(db.toDouble())), 1.dp.toPx())
        for (f in doubleArrayOf(100.0, 1000.0, 10000.0)) drawLine(grid, Offset(x(f), 0f), Offset(x(f), h), 1.dp.toPx())
        fun path(c: DoubleArray) = Path().apply {
            c.forEachIndexed { i, d ->
                val px = w * i / (c.size - 1)
                if (i == 0) moveTo(px, y(d)) else lineTo(px, y(d))
            }
        }
        drawPath(path(l), CYAN, style = Stroke(2.5.dp.toPx()))
        drawPath(path(r), ORANGE, style = Stroke(2.5.dp.toPx()))
        state.bands.filter { it.on }.forEach { b ->
            val bx = x(b.freq.toDouble().coerceIn(20.0, 20000.0))
            val ch = if (b.chan == Chan.R) r else l
            val idx = ((bx / w) * (ch.size - 1)).toInt().coerceIn(0, ch.size - 1)
            drawCircle(Color.White, 3.dp.toPx(), Offset(bx, y(ch[idx])))
        }
    }
}
