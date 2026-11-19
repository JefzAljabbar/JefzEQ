package com.jefz.eq

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PresetTab(s: EqState, onExport: (String) -> Unit, onImport: () -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf("") }
    var confirmAll by remember { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Section("Simpan Preset") {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Nama preset") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val ok = PresetStore.save(name, s)
                        Toast.makeText(ctx, if (ok) "Preset tersimpan" else "Isi nama preset", Toast.LENGTH_SHORT).show()
                    }) { Text("Simpan") }
                    OutlinedButton(onClick = onImport) { Text("Import") }
                }
                Text(
                    "Isi preset: semua band, Tone, Vocal, Master, Compressor, FX, dan chip FX. " +
                        "Status power dan app sumber tidak ikut. Nama sama = ditimpa.",
                    color = Color(0xFF8A94A3), fontSize = 11.sp
                )
            }
        }
        if (PresetStore.names.isEmpty()) {
            item { Text("Belum ada preset.", color = Color(0xFF8A94A3), fontSize = 13.sp) }
        }
        items(PresetStore.names, key = { it }) { n ->
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF181D25)), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(n, color = Color.White, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        val loaded = PresetStore.read(n)
                        if (loaded != null) {
                            EqRepo.update { cur -> loaded.copy(power = cur.power, fxOn = cur.fxOn, fxPkg = cur.fxPkg) }
                            name = n
                            Toast.makeText(ctx, "Preset dimuat", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(ctx, "Gagal memuat", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("Muat") }
                    TextButton(onClick = { onExport(n) }) { Text("Export") }
                    TextButton(onClick = { PresetStore.delete(n) }) { Text("✕", color = Color(0xFFFF5252)) }
                }
            }
        }
        item {
            Section("Reset Semua") {
                Text(
                    "Mengembalikan semua pengaturan ke default (band kosong). Preset tersimpan tidak terhapus.",
                    color = Color(0xFF8A94A3), fontSize = 11.sp
                )
                OutlinedButton(onClick = { confirmAll = true }) { Text("Reset semua pengaturan") }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text("Reset semua?") },
            text = { Text("Semua band, Tone, Vocal, Master, Compressor, dan FX kembali ke default.") },
            confirmButton = {
                TextButton(onClick = {
                    EqRepo.update { cur -> EqState(power = cur.power, fxOn = cur.fxOn, fxPkg = cur.fxPkg) }
                    Rta.resetView(); Gen.resetOutput(); Gen.resetTone()
                    confirmAll = false
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("Batal") } }
        )
    }
}
