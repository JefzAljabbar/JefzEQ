package com.jefz.eq

import kotlin.math.pow
import kotlin.math.roundToInt

enum class BandType(val label: String) {
    PEAK("Peaking"), LOW_SHELF("LowShelf"), HIGH_SHELF("HighShelf"),
    LOW_PASS("LowPass"), HIGH_PASS("HighPass"), BAND_PASS("BandPass")
}

enum class Chan(val label: String) { L("L"), R("R"), LR("L+R") }

enum class EqMode(val label: String) { CASCADE("Cascade"), ENVELOPE("Envelope") }

data class Band(
    val id: Long,
    val type: BandType = BandType.PEAK,
    val chan: Chan = Chan.LR,
    val freq: Float = 1000f,
    val gain: Float = 0f,
    val q: Float = 1f,
    val on: Boolean = true
)

data class EqState(
    val power: Boolean = false,
    val mode: EqMode = EqMode.CASCADE,
    val bands: List<Band> = defaultBands()
)

fun defaultBands(): List<Band> = List(24) { i ->
    val f = 25.0 * (18000.0 / 25.0).pow(i / 23.0)
    Band(id = i.toLong(), freq = f.roundToInt().toFloat(), q = 3f)
}
