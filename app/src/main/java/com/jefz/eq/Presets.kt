package com.jefz.eq

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.File

/** Presets are JSON files in app storage. Power / capture state / source app are not part of a preset. */
object PresetStore {
    var names by mutableStateOf(listOf<String>())
    private var dir: File? = null

    fun init(ctx: Context) {
        dir = File(ctx.filesDir, "presets").apply { mkdirs() }
        refresh()
    }

    private fun refresh() {
        names = dir?.listFiles { f -> f.extension == "json" }
            ?.map { it.nameWithoutExtension }
            ?.sortedBy { it.lowercase() } ?: emptyList()
    }

    fun clean(n: String): String =
        n.trim().replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), "_").trimStart('.').take(40)

    private fun file(n: String) = File(dir!!, clean(n) + ".json")

    private fun encode(name: String, s: EqState): String =
        JSONObject().put("jefzeq_preset", 1).put("name", name)
            .put("state", JSONObject(EqRepo.serialize(s))).toString(2)

    private fun decode(text: String): Pair<String, EqState>? = try {
        val o = JSONObject(text)
        if (!o.has("jefzeq_preset")) null
        else Pair(o.optString("name", "Imported"), EqRepo.deserialize(o.getJSONObject("state").toString()))
    } catch (e: Exception) {
        null
    }

    fun save(name: String, s: EqState): Boolean {
        val n = clean(name)
        if (n.isEmpty()) return false
        return try {
            file(n).writeText(encode(n, s))
            refresh()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun read(name: String): EqState? = try { decode(file(name).readText())?.second } catch (e: Exception) { null }

    fun delete(name: String) {
        try { file(name).delete() } catch (_: Exception) {}
        refresh()
    }

    fun exportText(name: String): String? = try { file(name).readText() } catch (e: Exception) { null }

    /** Returns the stored name, or null when the text is not a JefzEQ preset. */
    fun importText(text: String): String? {
        val d = decode(text) ?: return null
        val base = clean(d.first).ifEmpty { "Imported" }
        var n = base
        var i = 2
        while (file(n).exists()) { n = "$base ($i)"; i++ }
        return if (save(n, d.second)) n else null
    }
}
