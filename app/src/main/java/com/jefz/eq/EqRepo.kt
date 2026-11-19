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
        val c = s.comp
        return JSONObject().put("power", s.power).put("mode", s.mode.name).put("bands", arr)
            .put("kl", JSONArray(s.knobsL.map { it.toDouble() }))
            .put("kr", JSONArray(s.knobsR.map { it.toDouble() }))
            .put("pre", s.preamp.toDouble()).put("gain", s.gain.toDouble())
            .put("vol", s.volume.toDouble()).put("bal", s.balance.toDouble())
            .put("comp", JSONObject().put("on", c.on).put("th", c.threshold.toDouble())
                .put("ratio", c.ratio.toDouble()).put("atk", c.attack.toDouble())
                .put("rel", c.release.toDouble()).put("mk", c.makeup.toDouble()))
            .toString()
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String, def: T): T =
        runCatching { enumValueOf<T>(name) }.getOrDefault(def)

    private fun floats(a: JSONArray?): List<Float> =
        if (a == null || a.length() != 5) List(5) { 0f } else List(5) { a.getDouble(it).toFloat() }

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
        val d = Comp()
        val c = o.optJSONObject("comp")
        val comp = if (c == null) d else Comp(
            on = c.optBoolean("on", d.on),
            threshold = c.optDouble("th", d.threshold.toDouble()).toFloat(),
            ratio = c.optDouble("ratio", d.ratio.toDouble()).toFloat(),
            attack = c.optDouble("atk", d.attack.toDouble()).toFloat(),
            release = c.optDouble("rel", d.release.toDouble()).toFloat(),
            makeup = c.optDouble("mk", d.makeup.toDouble()).toFloat()
        )
        return EqState(
            power = o.getBoolean("power"),
            mode = enumOr(o.getString("mode"), EqMode.CASCADE),
            bands = bands,
            knobsL = floats(o.optJSONArray("kl")),
            knobsR = floats(o.optJSONArray("kr")),
            preamp = o.optDouble("pre", 0.0).toFloat(),
            gain = o.optDouble("gain", 0.0).toFloat(),
            volume = o.optDouble("vol", 100.0).toFloat(),
            balance = o.optDouble("bal", 0.0).toFloat(),
            comp = comp
        )
    }
}
