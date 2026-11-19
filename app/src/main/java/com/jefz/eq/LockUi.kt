package com.jefz.eq

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PasswordScreen(onUnlocked: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var recovery by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var show by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(500); now = System.currentTimeMillis() } }
    val remain = ((LockStore.lockedUntil(ctx) - now) / 1000).coerceAtLeast(0)
    val locked = remain > 0
    val ready = if (recovery) input.count { it.isLetterOrDigit() } >= 16 else input.isNotEmpty()

    Column(
        Modifier.fillMaxSize().background(Color(0xFF0E1116)).statusBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("JefzEQ", fontSize = 28.sp, color = Color(0xFF00E5FF))
        Spacer(Modifier.height(24.dp))
        Text(if (recovery) "Masukkan Kode Pemulihan" else "Masukkan Password", color = Color.White)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = input,
            onValueChange = { v ->
                input = if (recovery) v.take(24) else v.take(64)
                msg = ""
            },
            singleLine = true,
            visualTransformation = if (recovery || show) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = if (recovery) KeyboardType.Ascii else KeyboardType.Password),
            enabled = !busy && !locked,
            modifier = Modifier.fillMaxWidth()
        )
        if (msg.isNotEmpty()) Text(msg, color = Color(0xFFFF5252), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        if (locked) Text("Terlalu banyak percobaan. Coba lagi dalam $remain detik.", color = Color(0xFFFFB74D), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        if (!recovery) TextButton(onClick = { show = !show }, enabled = !busy) { Text(if (show) "Sembunyikan" else "Tampilkan") }
        Spacer(Modifier.height(8.dp))
        Button(
            enabled = ready && !busy && !locked,
            onClick = {
                busy = true
                val attempt = input
                scope.launch {
                    val ok = withContext(Dispatchers.Default) {
                        if (recovery) LockStore.checkRecovery(attempt) else LockStore.checkPassword(attempt)
                    }
                    busy = false
                    if (ok) {
                        LockStore.unlock(ctx)
                        onUnlocked()
                    } else {
                        LockStore.registerFail(ctx)
                        msg = if (recovery) "Kode pemulihan salah" else "Password salah"
                        input = ""
                    }
                }
            }
        ) { Text(if (busy) "Memeriksa…" else "Buka") }
        TextButton(onClick = { recovery = !recovery; input = ""; msg = "" }, enabled = !busy) {
            Text(if (recovery) "Kembali ke Password" else "Lupa Password?")
        }
        if (recovery) {
            Text(
                "Masukkan Kode Pemulihan 16 karakter yang Anda simpan (format XXXX-XXXX-XXXX-XXXX).",
                color = Color(0xFF8A94A3), fontSize = 11.sp
            )
        }
    }
}
