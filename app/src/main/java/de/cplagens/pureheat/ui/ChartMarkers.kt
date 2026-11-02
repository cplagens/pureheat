package de.cplagens.pureheat.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import de.cplagens.pureheat.data.history.RoomSample
import de.cplagens.pureheat.data.history.WindowEvent
import android.content.Context
import de.cplagens.pureheat.R
import kotlin.math.abs
import kotlin.math.roundToLong

/** An event worth a time label above a chart. */
data class ChartMarker(val time: Long, val label: String)

private const val QUARTER_MS = 15 * 60_000L

/**
 * Window opened/closed and setpoint changes, with the time rounded to 15 min (the samples are
 * up to an hour apart, so the exact minute would suggest more precision than we have).
 */
fun chartMarkers(
    ctx: Context,
    samples: List<RoomSample>,
    windows: List<WindowEvent>,
    start: Long,
    end: Long,
): List<ChartMarker> {
    val out = mutableListOf<ChartMarker>()
    windows.forEach { w ->
        if (w.start in start..end) out += ChartMarker(w.start, ctx.getString(R.string.marker_window_open, clock(ctx, w.start)))
        // Only claim "closed" when we saw it; otherwise we just know it was open at least until then.
        w.end?.takeIf { it in start..end && w.closeDetected }?.let {
            out += ChartMarker(it, ctx.getString(R.string.marker_window_closed, clock(ctx, it)))
        }
    }
    var previous: Double? = null
    samples.filter { it.timestamp in start..end }.forEach { s ->
        val sp = s.setPointTemperature ?: return@forEach
        val p = previous
        if (p != null && abs(sp - p) >= 0.5) {
            out += ChartMarker(s.timestamp, ctx.getString(R.string.marker_setpoint, sp.deg(), clock(ctx, s.timestamp)))
        }
        previous = sp
    }
    return out.sortedBy { it.time }
}

private fun round15(t: Long) = (t.toDouble() / QUARTER_MS).roundToLong() * QUARTER_MS

/** Time in the user's 12/24 h format, rounded to 15 min. */
private fun clock(ctx: Context, t: Long): String =
    android.text.format.DateFormat.getTimeFormat(ctx).format(java.util.Date(round15(t)))

private fun Double.deg() = if (this % 1.0 == 0.0) "${toInt()}°" else String.format("%.1f°", this)

/**
 * Time labels on two lanes above a chart with the same [start]/[end]. A label goes to the
 * second lane if it would overlap the first, and is skipped if both are taken.
 */
@Composable
fun MarkerStrip(markers: List<ChartMarker>, start: Long, end: Long, modifier: Modifier = Modifier) {
    if (markers.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val tick = MaterialTheme.colorScheme.outline
    Canvas(modifier.fillMaxWidth().height(30.dp)) {
        val span = (end - start).coerceAtLeast(1)
        val laneHeight = size.height / 2
        val laneEnds = floatArrayOf(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY)
        markers.forEach { m ->
            val x = (m.time - start).toFloat() / span * size.width
            val layout = measurer.measure(m.label, style)
            val w = layout.size.width.toFloat()
            // Keep the label inside the strip; anchor at the tick where possible.
            val left = (x - w / 2).coerceIn(0f, (size.width - w).coerceAtLeast(0f))
            val lane = laneEnds.indexOfFirst { left > it + 4.dp.toPx() }
            if (lane < 0) return@forEach
            laneEnds[lane] = left + w
            val top = lane * laneHeight
            drawText(layout, topLeft = Offset(left, top))
            drawLine(tick, Offset(x, top + layout.size.height), Offset(x, size.height), strokeWidth = 1.dp.toPx())
        }
    }
}
