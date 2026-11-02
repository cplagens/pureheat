package de.cplagens.pureheat.data.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowDetectorTest {
    private val quarter = 15 * 60_000L

    private fun s(i: Int, t: Double, valve: Double = 0.0, rh: Int? = null) = RoomSample("r", i * quarter, t, 15.0, valve, rh)

    @Test
    fun detectsOpeningAndClosing() {
        // Stable, window opened (−0.5 K in 15 min), keeps cooling, closed (air warms up again).
        val temps = listOf(23.2, 23.2, 23.2, 22.7, 22.3, 22.0, 21.8, 21.6, 21.5, 21.8, 22.0)
        val events = WindowDetector.detect(temps.mapIndexed { i, t -> s(i, t) })
        val e = events.single()
        assertEquals(2 * quarter, e.start)
        assertEquals(9 * quarter, e.end)
        assertEquals(1.7, e.drop, 1e-9)
        assertTrue(e.closeDetected)
    }

    @Test
    fun slowCoolingIsNoWindow() {
        // Normal night cooling with the window closed: ~0.2 K/h.
        val samples = (0..32).map { s(it, 23.0 - it * 0.05) }
        assertTrue(WindowDetector.detect(samples).isEmpty())
    }

    @Test
    fun stillOpenAtTheEnd() {
        val temps = listOf(22.0, 22.0, 21.4, 21.1, 20.9)
        val e = WindowDetector.detect(temps.mapIndexed { i, t -> s(i, t) }).single()
        assertNull(e.end)
    }

    @Test
    fun humidityDropHelpsWithSmallTemperatureDrop() {
        val samples = listOf(s(0, 22.0, rh = 55), s(1, 21.75, rh = 48), s(2, 21.6, rh = 47))
        val e = WindowDetector.detect(samples).single()
        assertEquals(7, e.humidityDrop)
    }

    @Test
    fun noDetectionWhileHeating() {
        // Valve open: temperature changes are the heating's business.
        val samples = listOf(s(0, 22.0, valve = 1.0), s(1, 21.4, valve = 1.0))
        assertTrue(WindowDetector.detect(samples).isEmpty())
    }

    @Test
    fun heatingStartEndsEvent() {
        val samples = listOf(s(0, 22.0), s(1, 21.4), s(2, 21.0), s(3, 20.8, valve = 1.0))
        val e = WindowDetector.detect(samples).single()
        assertEquals(2 * quarter, e.end)
        // Heating started: we don't know whether the window was closed.
        assertTrue(!e.closeDetected)
    }

    @Test
    fun windowTimesAreExcludedFromPhases() {
        val temps = listOf(23.2, 23.2, 22.7, 22.3, 22.0, 21.8, 21.9, 22.1)
        val samples = temps.mapIndexed { i, t -> s(i, t) }
        val cleaned = WindowDetector.withoutWindowTimes(samples, WindowDetector.detect(samples))
        assertEquals(null, cleaned[3].valvePosition)
        assertEquals(0.0, cleaned[0].valvePosition!!, 0.0)
    }
}
