package com.jefz.eq

import kotlin.math.pow
import kotlin.math.roundToInt

enum class BandType(val label: String) {
    PEAK("Peaking"), LOW_SHELF("LowShelf"), HIGH_SHELF("HighShelf"),
    LOW_PASS("LowPass"), HIGH_PASS("HighPass"), BAND_PASS("BandPass")
}

enum class Chan(val label: String) { L("L"), R("R"), LR("L+R") }

enum class EqMode(val label: String) { CASCADE("Cascade"), ENVELOPE("Envelope") }

val KNOB_NAMES = listOf("Sub", "Bass", "Mid", "Treble", "High")

data class Band(
    val id: Long,
    val type: BandType = BandType.PEAK,
    val chan: Chan = Chan.LR,
    val freq: Float = 1000f,
    val gain: Float = 0f,
    val q: Float = 1f,
    val on: Boolean = true
)

data class Comp(
    val on: Boolean = false,
    val threshold: Float = -24f, // dB
    val ratio: Float = 3f,
    val attack: Float = 10f,     // ms
    val release: Float = 120f,   // ms
    val makeup: Float = 0f       // dB
)

data class Fx(
    val rvOn: Boolean = false,
    val rvMix: Float = 30f,   // %
    val rvRoom: Float = 60f,  // %
    val rvDamp: Float = 50f,  // %
    val ecOn: Boolean = false,
    val ecMix: Float = 30f,   // %
    val ecTime: Float = 300f, // ms
    val ecFb: Float = 35f     // %
)

data class EqState(
    val power: Boolean = false,
    val mode: EqMode = EqMode.CASCADE,
    val bands: List<Band> = defaultBands(),
    val knobsL: List<Float> = List(5) { 0f }, // -100..+100 %
    val knobsR: List<Float> = List(5) { 0f },
    val preamp: Float = 0f,   // dB, before EQ
    val gain: Float = 0f,     // dB, output stage
    val volume: Float = 100f, // %
    val balance: Float = 0f,  // -100 (L) .. +100 (R)
    val comp: Comp = Comp(),
    val fx: Fx = Fx(),
    val sendL: List<Boolean> = List(5) { true }, // per-knob FX send, channel L
    val sendR: List<Boolean> = List(5) { true },
    val vocalL: Float = 0f,  // -100..+100 %
    val vocalR: Float = 0f,
    val lvlL: Float = 0f,    // channel level dB (-60 = mute)
    val lvlR: Float = 0f,
    val fxOn: Boolean = false,                   // wet capture running (not persisted)
    val fxPkg: String = "com.google.android.youtube"
)

fun defaultBands(): List<Band> = List(24) { i ->
    val f = 25.0 * (18000.0 / 25.0).pow(i / 23.0)
    Band(id = i.toLong(), freq = f.roundToInt().toFloat(), q = 3f)
}
