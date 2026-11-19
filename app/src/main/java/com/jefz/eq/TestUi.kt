package com.jefz.eq

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun fmtHz(f: Float) = if (f >= 1000f) "%.3f kHz".format(f / 1000f) else "%.1f Hz".format(f)

private fun setFreq(f: Float) { Gen.freq = f.coerceIn(0f, 25000f) }

@Composable
fun TestTab() {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Section("Output") {
                LSlider("Level", Gen.levelDb, -60f..-6f, if (Gen.levelDb <= -60f) "Off" else "%.0f dBFS".format(Gen.levelDb)) { Gen.levelDb = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chan.values().forEach { c ->
                        FilterChip(selected = Gen.chan == c, onClick = { Gen.chan = c }, label = { Text(c.label) })
                    }
                }
                Text(
                    "Mulai dari level rendah. Sinyal tes melewati EQ, jadi bisa dipantau di RTA (dekatkan mikrofon ke speaker).",
                    color = Color(0xFF8A94A3), fontSize = 11.sp
                )
            }
        }
        item {
            Section("Pink Noise") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("On / Off", color = Color.White, modifier = Modifier.weight(1f))
                    Switch(
                        checked = Gen.mode == Gen.Mode.PINK,
                        onCheckedChange = { Gen.set(if (it) Gen.Mode.PINK else Gen.Mode.OFF) }
                    )
                }
            }
        }
        item {
            Section("Tone Generator (0 Hz – 25 kHz)") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("On / Off", color = Color.White, modifier = Modifier.weight(1f))
                    Switch(
                        checked = Gen.mode == Gen.Mode.TONE,
                        onCheckedChange = { Gen.set(if (it) Gen.Mode.TONE else Gen.Mode.OFF) }
                    )
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Gen.Wave.values().forEach { w ->
                        FilterChip(selected = Gen.wave == w, onClick = { Gen.wave = w }, label = { Text(w.label) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Knob(Gen.freq.coerceAtLeast(1f), 1f, 25000f, "Freq", fmtHz(Gen.freq), log = true) { setFreq(it) }
                    Text(fmtHz(Gen.freq), color = Color.White, fontSize = 20.sp)
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(-100f, -10f, -1f, 1f, 10f, 100f).forEach { d ->
                        OutlinedButton(
                            onClick = { setFreq(Gen.freq + d) },
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) { Text(if (d > 0) "+${d.toInt()}" else "${d.toInt()}", fontSize = 12.sp) }
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0f, 20f, 50f, 100f, 440f, 1000f, 5000f, 10000f, 15000f, 20000f, 22000f, 25000f).forEach { v ->
                        FilterChip(
                            selected = Gen.freq == v,
                            onClick = { setFreq(v) },
                            label = { Text(if (v >= 1000f) "${(v / 1000f).toString().removeSuffix(".0")}k" else "${v.toInt()}", fontSize = 12.sp) }
                        )
                    }
                }
                Text(
                    "Tone dibuat di 96 kHz agar 25 kHz bisa dihasilkan, tapi yang keluar tetap dibatasi hardware/DAC HP (umumnya ≤ 20–24 kHz). 0 Hz = senyap.",
                    color = Color(0xFF8A94A3), fontSize = 11.sp
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
