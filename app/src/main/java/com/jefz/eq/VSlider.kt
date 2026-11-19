package com.jefz.eq

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun yToValue(y: Float, h: Float, pad: Float, lo: Float, hi: Float): Float {
    val t = 1f - ((y - pad) / (h - 2 * pad)).coerceIn(0f, 1f)
    return lo + t * (hi - lo)
}

/** Vertical slider. Drag or tap to set, double-tap = reset. */
@Composable
fun VSlider(
    value: Float, range: ClosedFloatingPointRange<Float>,
    label: String, text: String,
    resetTo: Float = 0f,
    onChange: (Float) -> Unit
) {
    val cb by rememberUpdatedState(onChange)
    val lo = range.start
    val hi = range.endInclusive
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, color = Color.White, fontSize = 12.sp)
        Canvas(
            Modifier.width(64.dp).height(220.dp)
                .pointerInput(Unit) {
                    val pad = 14.dp.toPx()
                    detectTapGestures(
                        onDoubleTap = { cb(resetTo) },
                        onTap = { o -> cb(yToValue(o.y, size.height.toFloat(), pad, lo, hi)) }
                    )
                }
                .pointerInput(Unit) {
                    val pad = 14.dp.toPx()
                    detectVerticalDragGestures { change, _ ->
                        change.consume()
                        cb(yToValue(change.position.y, size.height.toFloat(), pad, lo, hi))
                    }
                }
        ) {
            val pad = 14.dp.toPx()
            val top = pad
            val bot = size.height - pad
            val cx = size.width / 2f
            fun yOf(v: Float) = bot - ((v - lo) / (hi - lo)).coerceIn(0f, 1f) * (bot - top)
            val ty = yOf(value)
            drawLine(Color(0xFF333A44), Offset(cx, top), Offset(cx, bot), 6.dp.toPx(), cap = StrokeCap.Round)
            drawLine(Color(0xFF00E5FF), Offset(cx, bot), Offset(cx, ty), 6.dp.toPx(), cap = StrokeCap.Round)
            val zy = yOf(resetTo)
            drawLine(Color.White.copy(alpha = 0.5f), Offset(cx - 16.dp.toPx(), zy), Offset(cx + 16.dp.toPx(), zy), 1.5.dp.toPx())
            drawCircle(Color.White, 11.dp.toPx(), Offset(cx, ty))
        }
        Text(label, color = Color(0xFF8A94A3), fontSize = 12.sp)
    }
}
