package com.jefz.eq

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin

@Composable
fun Knob(
    value: Float, min: Float, max: Float,
    label: String, text: String,
    log: Boolean = false,
    reset: Float? = null,
    onChange: (Float) -> Unit
) {
    val curV by rememberUpdatedState(value)
    val curCb by rememberUpdatedState(onChange)
    fun toN(v: Float) = if (log) ln(v / min) / ln(max / min) else (v - min) / (max - min)
    fun fromN(n: Float) = if (log) min * (max / min).pow(n) else min + n * (max - min)
    val n = toN(value).coerceIn(0f, 1f)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier.size(56.dp).pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { reset?.let { curCb(it) } })
            }.pointerInput(Unit) {
                detectVerticalDragGestures { change, dy ->
                    change.consume()
                    curCb(fromN((toN(curV) - dy / 300f).coerceIn(0f, 1f)))
                }
            }
        ) {
            val stroke = 6.dp.toPx()
            val tl = Offset(stroke / 2, stroke / 2)
            val sz = Size(size.width - stroke, size.height - stroke)
            drawArc(Color(0xFF333A44), 135f, 270f, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(Color(0xFF00E5FF), 135f, 270f * n, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
            val ang = Math.toRadians(135.0 + 270.0 * n)
            val r = size.width / 2 - stroke * 1.8f
            drawLine(
                Color.White, center,
                Offset(center.x + cos(ang).toFloat() * r, center.y + sin(ang).toFloat() * r),
                strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round
            )
        }
        Text(text, fontSize = 11.sp, color = Color.White)
        Text(label, fontSize = 10.sp, color = Color(0xFF8A94A3))
    }
}
