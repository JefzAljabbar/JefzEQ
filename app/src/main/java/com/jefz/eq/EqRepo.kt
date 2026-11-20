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
        prefs?.getString("state", null)?.let { runCatching { _state.value = deserialize(it) } }
    }

    fun update(f: (EqState) -> EqState) {
        _state.update(f)
        prefs?.edit()?.putString("state", serialize(_state.value))?.apply()
    }

    fun serialize(s: EqState): String {
        val arr = JSONArray()
        s.bands.forEach {
            arr.put(JSONObject().put("id", it.id).put("type", it.type.name).put("chan", it.chan.name)
                .put("freq", it.freq.toDouble()).put("gain", it.gain.toDouble())
                .put("q", it.q.toDouble()).put("on", it.on).put("c", compJson(it.comp)).put("cal", it.cal))
        }
        val c = s.comp
        return JSONObject().put("power", s.power).put("mode", s.mode.name).put("auto", s.bandsAuto).put("bands", arr)
            .put("kl", JSONArray(s.knobsL.map { it.toDouble() }))
            .put("kr", JSONArray(s.knobsR.map { it.toDouble() }))
            .put("pre", s.preamp.toDouble()).put("gain", s.gain.toDouble())
            .put("vol", s.volume.toDouble()).put("bal", s.balance.toDouble())
            .put("comp", JSONObject().put("on", c.on).put("th", c.threshold.toDouble())
                .put("ratio", c.ratio.toDouble()).put("atk", c.attack.toDouble())
                .put("rel", c.release.toDouble()).put("mk", c.makeup.toDouble()))
            .put("fx", JSONObject().put("rvOn", s.fx.rvOn).put("rvMix", s.fx.rvMix.toDouble())
                .put("rvRoom", s.fx.rvRoom.toDouble()).put("rvDamp", s.fx.rvDamp.toDouble())
                .put("ecOn", s.fx.ecOn).put("ecMix", s.fx.ecMix.toDouble())
                .put("ecTime", s.fx.ecTime.toDouble()).put("ecFb", s.fx.ecFb.toDouble()))
            .put("sl", JSONArray(s.sendL)).put("sr", JSONArray(s.sendR))
            .put("vl", s.vocalL.toDouble()).put("vr", s.vocalR.toDouble())
            .put("ll", s.lvlL.toDouble()).put("lr", s.lvlR.toDouble())
            .put("pkg", s.fxPkg)
            .put("pn", s.preset).put("ps", s.presetSig)
            .toString()
    }

    private fun compJson(c: Comp) = JSONObject().put("on", c.on).put("th", c.threshold.toDouble())
        .put("ratio", c.ratio.toDouble()).put("atk", c.attack.toDouble())
        .put("rel", c.release.toDouble()).put("mk", c.makeup.toDouble())

    private fun compParse(c: JSONObject?): Comp {
        val d = Comp()
        if (c == null) return d
        return Comp(
            on = c.optBoolean("on", d.on),
            threshold = c.optDouble("th", d.threshold.toDouble()).toFloat(),
            ratio = c.optDouble("ratio", d.ratio.toDouble()).toFloat(),
            attack = c.optDouble("atk", d.attack.toDouble()).toFloat(),
            release = c.optDouble("rel", d.release.toDouble()).toFloat(),
            makeup = c.optDouble("mk", d.makeup.toDouble()).toFloat()
        )
    }

    private fun bools(a: JSONArray?): List<Boolean> =
        if (a == null || a.length() != 5) List(5) { true } else List(5) { a.getBoolean(it) }

    private inline fun <reified T : Enum<T>> enumOr(name: String, def: T): T =
        runCatching { enumValueOf<T>(name) }.getOrDefault(def)

    private fun floats(a: JSONArray?): List<Float> =
        if (a == null || a.length() != 5) List(5) { 0f } else List(5) { a.getDouble(it).toFloat() }

    fun deserialize(js: String): EqState {
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
                on = b.getBoolean("on"),
                comp = compParse(b.optJSONObject("c")),
                cal = b.optBoolean("cal", false)
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
        val fd = Fx()
        val f = o.optJSONObject("fx")
        val fx = if (f == null) fd else Fx(
            rvOn = f.optBoolean("rvOn", fd.rvOn),
            rvMix = f.optDouble("rvMix", fd.rvMix.toDouble()).toFloat(),
            rvRoom = f.optDouble("rvRoom", fd.rvRoom.toDouble()).toFloat(),
            rvDamp = f.optDouble("rvDamp", fd.rvDamp.toDouble()).toFloat(),
            ecOn = f.optBoolean("ecOn", fd.ecOn),
            ecMix = f.optDouble("ecMix", fd.ecMix.toDouble()).toFloat(),
            ecTime = f.optDouble("ecTime", fd.ecTime.toDouble()).toFloat(),
            ecFb = f.optDouble("ecFb", fd.ecFb.toDouble()).toFloat()
        )
        return EqState(
            power = o.getBoolean("power"),
            mode = enumOr(o.getString("mode"), EqMode.CASCADE),
            bands = bands,
            bandsAuto = o.optBoolean("auto", false),
            knobsL = floats(o.optJSONArray("kl")),
            knobsR = floats(o.optJSONArray("kr")),
            preamp = o.optDouble("pre", 0.0).toFloat(),
            gain = o.optDouble("gain", 0.0).toFloat(),
            volume = o.optDouble("vol", 100.0).toFloat(),
            balance = o.optDouble("bal", 0.0).toFloat(),
            comp = comp,
            fx = fx,
            sendL = bools(o.optJSONArray("sl")),
            sendR = bools(o.optJSONArray("sr")),
            vocalL = o.optDouble("vl", 0.0).toFloat(),
            vocalR = o.optDouble("vr", 0.0).toFloat(),
            lvlL = o.optDouble("ll", 0.0).toFloat(),
            lvlR = o.optDouble("lr", 0.0).toFloat(),
            fxOn = false,
            fxPkg = o.optString("pkg", "com.google.android.youtube"),
            preset = o.optString("pn", ""),
            presetSig = o.optInt("ps", 0)
        )
    }
}
