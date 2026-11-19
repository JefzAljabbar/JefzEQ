package com.jefz.eq

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

object EqRepo {
    private val _state = MutableStateFlow(EqState())
    val state: StateFlow<EqState> = _state.asStateFlow()
    private var prefs: SharedPreferences? = null

    @Synchronized
    fun init(ctx: Context) {
        if (prefs != null) return
        prefs = ctx.applicationContext.getSharedPreferences("jefzeq", Context.MODE_PRIVATE)
        prefs?.getString("state", null)?.let { runCatching { _state.value = parse(it) } }
    }

    fun update(f: (EqState) -> EqState) {
        _state.update(f)
        prefs?.edit()?.putString("state", toJson(_state.value))?.apply()
    }

    private fun toJson(s: EqState): String {
        val arr = JSONArray()
        s.bands.forEach {
            arr.put(JSONObject().put("id", it.id).put("type", it.type.name).put("chan", it.chan.name)
                .put("freq", it.freq.toDouble()).put("gain", it.gain.toDouble())
                .put("q", it.q.toDouble()).put("on", it.on))
        }
        return JSONObject().put("power", s.power).put("mode", s.mode.name).put("bands", arr).toString()
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String, def: T): T =
        runCatching { enumValueOf<T>(name) }.getOrDefault(def)

    private fun parse(js: String): EqState {
        val o = JSONObject(js)
        val arr = o.getJSONArray("bands")
        val bands = List(arr.length()) { i ->
            val b = arr.getJSONObject(i)
            Band(
                id = b.getLong("id"),
                type = enumOr(b.getString("type"), BandType.PEAK),
                chan = enumOr(b.getString("chan"), Chan.LR),
                freq = b.getDouble("freq").toFloat(),
                gain = b.getDouble("gain").toFloat(),
                q = b.getDouble("q").toFloat(),
                on = b.getBoolean("on")
            )
        }
        return EqState(o.getBoolean("power"), enumOr(o.getString("mode"), EqMode.CASCADE), bands)
    }
}
