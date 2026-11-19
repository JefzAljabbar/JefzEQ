package com.jefz.eq

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.content.pm.PackageManager
import kotlin.math.ln
import kotlin.math.pow

private val CYAN = Color(0xFF00E5FF)
private val ORANGE = Color(0xFFFF9100)
private val CARD = Color(0xFF181D25)
private val TABS = listOf("Bands", "Tone", "FX", "Master")

private fun update(b: Band) = EqRepo.update { s ->
    s.copy(bands = s.bands.map { if (it.id == b.id) b else it })
}

private fun <T> List<T>.with(i: Int, v: T): List<T> = toMutableList().also { it[i] = v }

private fun fmtFreq(f: Float) = if (f >= 1000f) "%.2f kHz".format(f / 1000f) else "%.0f Hz".format(f)

@Composable
fun EqScreen(state: EqState, onFxToggle: (Boolean) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(Color(0xFF0E1116)).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("JefzEQ", fontSize = 22.sp, color = CYAN, modifier = Modifier.weight(1f))
            Switch(checked = state.power, onCheckedChange = { p -> EqRepo.update { it.copy(power = p) } })
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TABS.forEachIndexed { i, t ->
                FilterChip(selected = tab == i, onClick = { tab = i }, label = { Text(t) })
            }
        }
        ResponseGraph(state, Modifier.fillMaxWidth().height(170.dp).padding(16.dp))
        when (tab) {
            0 -> BandsTab(state)
            1 -> ToneTab(state)
            2 -> FxTab(state, onFxToggle)
            else -> MasterTab(state)
        }
    }
}

@Composable
fun BandsTab(state: EqState) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EqMode.values().forEach { m ->
                    FilterChip(
                        selected = state.mode == m,
                        onClick = { EqRepo.update { it.copy(mode = m) } },
                        label = { Text(m.label) }
                    )
                }
            }
        }
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
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = CARD), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = CYAN, fontSize = 14.sp)
            content()
        }
    }
}

@Composable
private fun KnobRow(
    vals: List<Float>, sends: List<Boolean>,
    onChange: (Int, Float) -> Unit, onSend: (Int, Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        KNOB_NAMES.forEachIndexed { i, n ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Knob(vals[i], -100f, 100f, n, "%+.0f%%".format(vals[i]), reset = 0f) { onChange(i, it) }
                FilterChip(
                    selected = sends[i],
                    onClick = { onSend(i, !sends[i]) },
                    label = { Text("FX", fontSize = 11.sp) }
                )
            }
        }
    }
}

@Composable
fun ToneTab(s: EqState) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Section("Tone — Channel L") {
                KnobRow(s.knobsL, s.sendL, { i, v -> EqRepo.update { it.copy(knobsL = it.knobsL.with(i, v)) } },
                    { i, b -> EqRepo.update { it.copy(sendL = it.sendL.with(i, b)) } })
            }
        }
        item {
            Section("Tone — Channel R") {
                KnobRow(s.knobsR, s.sendR, { i, v -> EqRepo.update { it.copy(knobsR = it.knobsR.with(i, v)) } },
                    { i, b -> EqRepo.update { it.copy(sendR = it.sendR.with(i, b)) } })
            }
        }
        item {
            Text("Geser knob ke atas/bawah. Double-tap = reset ke 0. -100% = mati. Chip FX = kirim band itu ke Reverb/Echo.",
                color = Color(0xFF8A94A3), fontSize = 11.sp)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, text: String, onChange: (Float) -> Unit) {
    Column {
        Text("$label  $text", color = Color.White, fontSize = 12.sp)
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
fun MasterTab(s: EqState) {
    val c = s.comp
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Section("Level") {
                LSlider("PreAmp", s.preamp, -12f..12f, "%+.1f dB".format(s.preamp)) { v -> EqRepo.update { it.copy(preamp = v) } }
                LSlider("Gain", s.gain, -12f..12f, "%+.1f dB".format(s.gain)) { v -> EqRepo.update { it.copy(gain = v) } }
                LSlider("Volume", s.volume, 0f..100f, "%.0f%%".format(s.volume)) { v -> EqRepo.update { it.copy(volume = v) } }
                val bt = when {
                    s.balance < -0.5f -> "L %.0f%%".format(-s.balance)
                    s.balance > 0.5f -> "R %.0f%%".format(s.balance)
                    else -> "Center"
                }
                LSlider("Balance", s.balance, -100f..100f, bt) { v -> EqRepo.update { it.copy(balance = v) } }
            }
        }
        item {
            Section("Compressor") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("On / Off", color = Color.White, modifier = Modifier.weight(1f))
                    Switch(checked = c.on, onCheckedChange = { v -> EqRepo.update { it.copy(comp = it.comp.copy(on = v)) } })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Knob(c.threshold, -60f, 0f, "Thresh", "%.0f dB".format(c.threshold)) { v -> EqRepo.update { it.copy(comp = it.comp.copy(threshold = v)) } }
                    Knob(c.ratio, 1f, 20f, "Ratio", "%.1f:1".format(c.ratio)) { v -> EqRepo.update { it.copy(comp = it.comp.copy(ratio = v)) } }
                    Knob(c.attack, 1f, 200f, "Attack", "%.0f ms".format(c.attack), log = true) { v -> EqRepo.update { it.copy(comp = it.comp.copy(attack = v)) } }
                    Knob(c.release, 10f, 1000f, "Release", "%.0f ms".format(c.release), log = true) { v -> EqRepo.update { it.copy(comp = it.comp.copy(release = v)) } }
                    Knob(c.makeup, 0f, 24f, "Makeup", "%.1f dB".format(c.makeup)) { v -> EqRepo.update { it.copy(comp = it.comp.copy(makeup = v)) } }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
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

private fun fxUpdate(f: (Fx) -> Fx) = EqRepo.update { it.copy(fx = f(it.fx)) }

private fun loadApps(pm: PackageManager): List<Pair<String, String>> {
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(i, 0)
        .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
        .distinctBy { it.second }
        .sortedBy { it.first.lowercase() }
}

@Composable
fun FxTab(s: EqState, onFxToggle: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val apps = remember { loadApps(ctx.packageManager) }
    var pick by remember { mutableStateOf(false) }
    val f = s.fx
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Section("Sumber Efek") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Aktifkan Reverb / Echo", color = Color.White, modifier = Modifier.weight(1f))
                    Switch(checked = s.fxOn, onCheckedChange = onFxToggle)
                }
                val label = apps.firstOrNull { it.second == s.fxPkg }?.first ?: s.fxPkg
                OutlinedButton(onClick = { pick = true }) { Text("App: $label") }
                Text(
                    "Efek hanya memproses suara app terpilih (butuh izin rekam audio + capture layar). " +
                        "Hanya sinyal efek (wet) yang ditambahkan, suara asli tidak digandakan. EQ utama tetap global.",
                    color = Color(0xFF8A94A3), fontSize = 11.sp
                )
            }
        }
        item {
            Section("Reverb") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("On / Off", color = Color.White, modifier = Modifier.weight(1f))
                    Switch(checked = f.rvOn, onCheckedChange = { v -> fxUpdate { it.copy(rvOn = v) } })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Knob(f.rvMix, 0f, 100f, "Mix", "%.0f%%".format(f.rvMix)) { v -> fxUpdate { it.copy(rvMix = v) } }
                    Knob(f.rvRoom, 0f, 100f, "Room", "%.0f%%".format(f.rvRoom)) { v -> fxUpdate { it.copy(rvRoom = v) } }
                    Knob(f.rvDamp, 0f, 100f, "Damp", "%.0f%%".format(f.rvDamp)) { v -> fxUpdate { it.copy(rvDamp = v) } }
                }
            }
        }
        item {
            Section("Echo") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("On / Off", color = Color.White, modifier = Modifier.weight(1f))
                    Switch(checked = f.ecOn, onCheckedChange = { v -> fxUpdate { it.copy(ecOn = v) } })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Knob(f.ecMix, 0f, 100f, "Mix", "%.0f%%".format(f.ecMix)) { v -> fxUpdate { it.copy(ecMix = v) } }
                    Knob(f.ecTime, 50f, 1000f, "Time", "%.0f ms".format(f.ecTime), log = true) { v -> fxUpdate { it.copy(ecTime = v) } }
                    Knob(f.ecFb, 0f, 100f, "Feedback", "%.0f%%".format(f.ecFb)) { v -> fxUpdate { it.copy(ecFb = v) } }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
    if (pick) {
        AlertDialog(
            onDismissRequest = { pick = false },
            confirmButton = { TextButton(onClick = { pick = false }) { Text("Tutup") } },
            title = { Text("Pilih App") },
            text = {
                LazyColumn(Modifier.height(360.dp)) {
                    items(apps) { a ->
                        TextButton(
                            onClick = { EqRepo.update { it.copy(fxPkg = a.second) }; pick = false },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(a.first, modifier = Modifier.fillMaxWidth()) }
                    }
                }
            }
        )
    }
}
