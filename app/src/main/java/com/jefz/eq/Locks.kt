package com.jefz.eq

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Per-feature lockers + master locker. Locked content ignores taps/drags (scrolling still works). Persisted. */
object Locks {
    var master by mutableStateOf(false)
    private val keys = mutableStateMapOf<String, Boolean>()
    private var prefs: SharedPreferences? = null

    @Synchronized
    fun init(ctx: Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences("jz_locks", Context.MODE_PRIVATE)
        prefs = p
        master = p.getBoolean("m", false)
        p.getStringSet("k", emptySet())?.forEach { keys[it] = true }
    }

    fun isOwn(key: String): Boolean = keys[key] == true
    fun isLocked(key: String): Boolean = master || keys[key] == true

    fun toggle(key: String) {
        if (keys[key] == true) keys.remove(key) else keys[key] = true
        save()
    }

    fun toggleMaster() {
        master = !master
        save()
    }

    private fun save() {
        prefs?.edit()?.putBoolean("m", master)?.putStringSet("k", keys.keys.toSet())?.apply()
    }
}

@Composable
fun LockButton(key: String) {
    val locked = Locks.isLocked(key)
    TextButton(
        onClick = { Locks.toggle(key) },
        enabled = !Locks.master,
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) { Text(if (locked) "🔒" else "🔓", fontSize = 16.sp) }
}

/** Dims the content and swallows taps when locked. Vertical drags are not consumed, so lists still scroll. */
@Composable
fun LockedBox(locked: Boolean, gap: Dp = 8.dp, content: @Composable () -> Unit) {
    Box {
        Column(Modifier.alpha(if (locked) 0.5f else 1f), verticalArrangement = Arrangement.spacedBy(gap)) { content() }
        if (locked) Box(Modifier.matchParentSize().pointerInput(Unit) { detectTapGestures { } })
    }
}
