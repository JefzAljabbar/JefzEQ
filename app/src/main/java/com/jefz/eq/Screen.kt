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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.SliderDefaults
import kotlinx.coroutines.delay
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
private val TABS = listOf("Bands", "Tone", "FX", "Master", "RTA", "Test", "Preset")

private fun update(b: Band) = EqRepo.update { s ->
    s.copy(bands = s.bands.map { if (it.id == b.id) b else it })
}

private fun <T> List<T>.with(i: Int, v: T): List<T> = toMutableList().also { it[i] = v }

private fun fmtFreq(f: Float) = if (f >= 1000f) "%.2f kHz".format(f / 1000f) else "%.0f Hz".format(f)

@Composable
fun EqScreen(
    state: EqState,
    onFxToggle: (Boolean) -> Unit,
    onRtaToggle: (Boolean) -> Unit,
    onExport: (String) -> Unit,
    onImport: () -> Unit
) {
    val pager = rememberPagerState(pageCount = { TABS.size })
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().background(Color(0xFF0E1116)).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("JefzEQ", fontSize = 22.sp, color = CYAN)
                Text("Preset: ${presetLabel(state)}", fontSize = 11.sp, color = Color(0xFF8A94A3), maxLines = 1)
            }
            FilterChip(
                selected = Locks.master, onClick = { Locks.toggleMaster() },
                label = { Text(if (Locks.master) "🔒" else "🔓") }
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(selected = Rta.on, onClick = { onRtaToggle(!Rta.on) }, label = { Text("RTA") })
            Spacer(Modifier.width(8.dp))
            Switch(checked = state.power, onCheckedChange = { p -> EqRepo.update { it.copy(power = p) } })
        }
        RtaView(state, Modifier.fillMaxWidth().height(190.dp).padding(horizontal = 12.dp, vertical = 8.dp))
        HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            when (page) {
                0 -> BandsTab(state)
                1 -> ToneTab(state)
                2 -> FxTab(state, onFxToggle)
                3 -> MasterTab(state)
                4 -> RtaTab(state, onFxToggle, onRtaToggle)
                5 -> TestTab(state, onRtaToggle)
                else -> PresetTab(state, onExport, onImport)
            }
        }
        BottomTabs(pager.currentPage) { i -> scope.launch { pager.animateScrollToPage(i) } }
    }
}

@Composable
private fun BottomTabs(selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color(0xFF12161C))) {
        TABS.forEachIndexed { i, t ->
            val sel = i == selected
            Column(
                Modifier.weight(1f).clickable { onSelect(i) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.fillMaxWidth().height(3.dp).background(if (sel) CYAN else Color.Transparent))
                Spacer(Modifier.height(10.dp))
                Text(t, fontSize = 11.sp, maxLines = 1, color = if (sel) CYAN else Color(0xFF8A94A3))
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

private fun autoSort(l: List<Band>): List<Band> =
    l.sortedWith(compareBy<Band>({ when (it.chan) { Chan.L -> 0; Chan.R -> 1; Chan.LR -> 2 } }, { it.freq }))

private fun chanTitle(c: Chan) = when (c) { Chan.L -> "Channel L"; Chan.R -> "Channel R"; Chan.LR -> "Channel L+R" }

/** Manual move: switches the order mode to Manual. */
private fun moveBand(id: Long, d: Int) = EqRepo.update { s ->
    val l = s.bands.toMutableList()
    val i = l.indexOfFirst { it.id == id }
    val j = i + d
    if (i < 0 || j < 0 || j >= l.size) s
    else { java.util.Collections.swap(l, i, j); s.copy(bands = l, bandsAuto = false) }
}

@Composable
fun BandsTab(state: EqState) {
    var confirm by remember { mutableStateOf(false) }
    // Auto order: re-sort shortly after the user stops changing freq / channel (so cards don't jump under the finger).
    val sig = state.bands.map { Triple(it.id, it.chan, it.freq) }
    LaunchedEffect(sig, state.bandsAuto) {
        if (state.bandsAuto) {
            delay(700)
            EqRepo.update { s ->
                if (!s.bandsAuto) s
                else {
                    val n = autoSort(s.bands)
                    if (n.map { it.id } == s.bands.map { it.id }) s else s.copy(bands = n)
                }
            }
        }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Section("Mode & Susunan") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    EqMode.values().forEach { m ->
                        FilterChip(
                            selected = state.mode == m,
                            onClick = { EqRepo.update { it.copy(mode = m) } },
                            label = { Text(m.label) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = state.bandsAuto,
                        onClick = {
                            EqRepo.update { s ->
                                if (s.bandsAuto) s.copy(bandsAuto = false)
                                else s.copy(bandsAuto = true, bands = autoSort(s.bands))
                            }
                        },
                        label = { Text("Susun otomatis") }
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { confirm = true }) {
                        Text("Reset Bands", fontSize = 12.sp, color = Color(0xFF8A94A3))
                    }
                }
                Text(
                    "Otomatis: dikelompokkan Channel L, R, lalu L+R, tiap grup urut dari Frequency terkecil. " +
                        "Kalau Anda memindah band dengan ▲ / ▼, susunan otomatis berubah ke Manual.",
                    color = Color(0xFF8A94A3), fontSize = 11.sp
                )
            }
        }
        if (state.bands.isEmpty()) {
            item { Text("Belum ada band. Tekan + Tambah Band.", color = Color(0xFF8A94A3), fontSize = 13.sp) }
        }
        state.bands.forEachIndexed { i, b ->
            if (state.bandsAuto && (i == 0 || state.bands[i - 1].chan != b.chan)) {
                item(key = "h_$i") { Text(chanTitle(b.chan), color = Color(0xFF8A94A3), fontSize = 12.sp) }
            }
            item(key = b.id) { BandCard(i + 1, b, i > 0, i < state.bands.size - 1) }
        }
        item {
            Button(
                enabled = !Locks.isLocked("Mode & Susunan"),
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
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Reset Bands?") },
            text = { Text("Semua band dihapus dan mode kembali ke Cascade.") },
            confirmButton = {
                TextButton(onClick = {
                    if (!Locks.isLocked("Mode & Susunan")) {
                        EqRepo.update { it.copy(bands = emptyList(), mode = EqMode.CASCADE) }
                    }
                    confirm = false
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Batal") } }
        )
    }
}

@Composable
fun BandCard(index: Int, b: Band, canUp: Boolean, canDown: Boolean) {
    val key = "band:${b.id}"
    val locked = Locks.isLocked(key)
    // Slider color follows the channel line color in the RTA graph (L yellow, R magenta, L+R white).
    val col = when (b.chan) { Chan.L -> EQL; Chan.R -> EQR; Chan.LR -> Color.White }
    Card(colors = CardDefaults.cardColors(containerColor = CARD), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (b.cal) "Band $index • kalibrasi" else "Band $index",
                    color = Color.White, modifier = Modifier.weight(1f)
                )
                LockButton(key)
                Switch(checked = b.on, onCheckedChange = { update(b.copy(on = it)) }, enabled = !locked)
                TextButton(enabled = !locked, onClick = {
                    EqRepo.update { s -> s.copy(bands = s.bands.filter { it.id != b.id }) }
                }) { Text("✕", color = Color(0xFFFF5252)) }
            }
            LockedBox(locked, gap = 6.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(enabled = canUp, onClick = { moveBand(b.id, -1) }) { Text("▲ Naik", fontSize = 12.sp) }
                    TextButton(enabled = canDown, onClick = { moveBand(b.id, 1) }) { Text("▼ Turun", fontSize = 12.sp) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = {
                        update(b.copy(type = BandType.PEAK, chan = Chan.LR, freq = 1000f, gain = 0f, q = 1f, on = true, comp = Comp()))
                    }) { Text("Reset", fontSize = 12.sp, color = Color(0xFF8A94A3)) }
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
                        Text("Gain %+.1f dB".format(b.gain), color = col, fontSize = 12.sp)
                        Slider(
                            value = b.gain, onValueChange = { update(b.copy(gain = it)) }, valueRange = -20f..20f,
                            colors = SliderDefaults.colors(
                                thumbColor = col, activeTrackColor = col, inactiveTrackColor = col.copy(alpha = 0.25f)
                            )
                        )
                    }
                    Knob(b.freq, 20f, 20000f, "Freq", fmtFreq(b.freq), log = true) { update(b.copy(freq = it)) }
                    Knob(b.q, 0.1f, 10f, "Q", "%.2f".format(b.q), log = true) { update(b.copy(q = it)) }
                }
                var cOpen by remember { mutableStateOf(false) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = b.comp.on,
                        onClick = { update(b.copy(comp = b.comp.copy(on = !b.comp.on))) },
                        label = { Text("Compressor") }
                    )
                    TextButton(onClick = { cOpen = !cOpen }) { Text(if (cOpen) "Sembunyikan ▴" else "Atur ▾", fontSize = 12.sp) }
                }
                if (cOpen) {
                    val c = b.comp
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Knob(c.threshold, -60f, 0f, "Thresh", "%.0f dB".format(c.threshold), reset = -24f) { v -> update(b.copy(comp = c.copy(threshold = v))) }
                        Knob(c.ratio, 1f, 20f, "Ratio", "%.1f:1".format(c.ratio), reset = 3f) { v -> update(b.copy(comp = c.copy(ratio = v))) }
                        Knob(c.attack, 1f, 200f, "Attack", "%.0f ms".format(c.attack), log = true, reset = 10f) { v -> update(b.copy(comp = c.copy(attack = v))) }
                        Knob(c.release, 10f, 1000f, "Release", "%.0f ms".format(c.release), log = true, reset = 120f) { v -> update(b.copy(comp = c.copy(release = v))) }
                        Knob(c.makeup, 0f, 24f, "Makeup", "%.1f dB".format(c.makeup), reset = 0f) { v -> update(b.copy(comp = c.copy(makeup = v))) }
                    }
                    Text(
                        "Berlaku pada rentang frekuensi band ini (setelah EQ). Maks 8 band compressor aktif per channel.",
                        color = Color(0xFF8A94A3), fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
fun Section(title: String, onReset: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val locked = Locks.isLocked(title)
    Card(colors = CardDefaults.cardColors(containerColor = CARD), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = CYAN, fontSize = 14.sp, modifier = Modifier.weight(1f))
                if (onReset != null) {
                    TextButton(onClick = onReset, enabled = !locked) { Text("Reset", fontSize = 12.sp, color = Color(0xFF8A94A3)) }
                }
                LockButton(title)
            }
            LockedBox(locked) { content() }
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
            Section("Tone — Channel L", onReset = { EqRepo.update { it.copy(knobsL = List(5) { 0f }, sendL = List(5) { true }) } }) {
                KnobRow(s.knobsL, s.sendL, { i, v -> EqRepo.update { it.copy(knobsL = it.knobsL.with(i, v)) } },
                    { i, b -> EqRepo.update { it.copy(sendL = it.sendL.with(i, b)) } })
            }
        }
        item {
            Section("Tone — Channel R", onReset = { EqRepo.update { it.copy(knobsR = List(5) { 0f }, sendR = List(5) { true }) } }) {
                KnobRow(s.knobsR, s.sendR, { i, v -> EqRepo.update { it.copy(knobsR = it.knobsR.with(i, v)) } },
                    { i, b -> EqRepo.update { it.copy(sendR = it.sendR.with(i, b)) } })
            }
        }
        item {
            Section("Vocal (200 Hz – 4 kHz)", onReset = { EqRepo.update { it.copy(vocalL = 0f, vocalR = 0f) } }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Knob(s.vocalL, -100f, 100f, "Vocal L", "%+.0f%%".format(s.vocalL), reset = 0f) { v -> EqRepo.update { it.copy(vocalL = v) } }
                    Knob(s.vocalR, -100f, 100f, "Vocal R", "%+.0f%%".format(s.vocalR), reset = 0f) { v -> EqRepo.update { it.copy(vocalR = v) } }
                }
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
fun LSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, text: String, onChange: (Float) -> Unit) {
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
            Section("Level", onReset = { EqRepo.update { it.copy(preamp = 0f, gain = 0f, volume = 100f, balance = 0f) } }) {
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
            Section("Level Channel L / R", onReset = { EqRepo.update { it.copy(lvlL = 0f, lvlR = 0f) } }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    VSlider(s.lvlL, -60f..12f, "L", lvlText(s.lvlL)) { v -> EqRepo.update { it.copy(lvlL = v) } }
                    VSlider(s.lvlR, -60f..12f, "R", lvlText(s.lvlR)) { v -> EqRepo.update { it.copy(lvlR = v) } }
                }
                Text("Double-tap slider = reset 0 dB. Paling bawah = mute.", color = Color(0xFF8A94A3), fontSize = 11.sp)
            }
        }
        item {
            Section("Compressor Master", onReset = { EqRepo.update { it.copy(comp = Comp()) } }) {
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
    var apps by remember { mutableStateOf(Apps.list) }
    LaunchedEffect(Unit) {
        if (!Apps.loaded) {
            withContext(Dispatchers.Default) { Apps.load(ctx.packageManager) }
            apps = Apps.list
        }
    }
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
                    Text("Capture app (RTA internal & efek)", color = Color.White, modifier = Modifier.weight(1f))
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
            Section("Reverb", onReset = { val d = Fx(); fxUpdate { it.copy(rvOn = d.rvOn, rvMix = d.rvMix, rvRoom = d.rvRoom, rvDamp = d.rvDamp) } }) {
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
            Section("Echo", onReset = { val d = Fx(); fxUpdate { it.copy(ecOn = d.ecOn, ecMix = d.ecMix, ecTime = d.ecTime, ecFb = d.ecFb) } }) {
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

private fun lvlText(v: Float) = if (v <= -59.5f) "Mute" else "%+.1f dB".format(v)
