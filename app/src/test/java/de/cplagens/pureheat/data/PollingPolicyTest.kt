package de.cplagens.pureheat.data

import de.cplagens.pureheat.data.history.RoomSample
import org.junit.Assert.assertEquals
import org.junit.Test

class PollingPolicyTest {
    private fun s(room: String, valve: Double?, setPoint: Double? = 21.0) = RoomSample(room, 0, 21.0, setPoint, valve)

    @Test
    fun valveMovingMeansActive() {
        val prev = listOf(s("a", 0.0), s("b", 0.5))
        val now = listOf(s("a", 0.0), s("b", 0.7))
        assertEquals(PollingPolicy.ACTIVE_MINUTES, PollingPolicy.nextDelayMinutes(now, prev, localHour = 3))
    }

    @Test
    fun setpointChangeMeansActive() {
        val prev = listOf(s("a", 0.0, 17.0))
        val now = listOf(s("a", 0.0, 21.0))
        assertEquals(PollingPolicy.ACTIVE_MINUTES, PollingPolicy.nextDelayMinutes(now, prev, localHour = 12))
    }

    @Test
    fun stableDayAndNight() {
        val prev = listOf(s("a", 1.0), s("b", 0.0))
        val now = listOf(s("a", 0.95), s("b", 0.0)) // small wobble stays "stable"
        assertEquals(PollingPolicy.STABLE_MINUTES, PollingPolicy.nextDelayMinutes(now, prev, localHour = 14))
        assertEquals(PollingPolicy.NIGHT_MINUTES, PollingPolicy.nextDelayMinutes(now, prev, localHour = 23))
        assertEquals(PollingPolicy.NIGHT_MINUTES, PollingPolicy.nextDelayMinutes(now, prev, localHour = 4))
        assertEquals(PollingPolicy.STABLE_MINUTES, PollingPolicy.nextDelayMinutes(now, prev, localHour = 5))
    }

    @Test
    fun noPreviousRecordingIsStable() {
        assertEquals(PollingPolicy.STABLE_MINUTES, PollingPolicy.nextDelayMinutes(listOf(s("a", 1.0)), emptyList(), 10))
    }

    @Test
    fun missingValuesAreIgnored() {
        val prev = listOf(s("a", null, null))
        val now = listOf(s("a", 1.0, 25.0))
        assertEquals(PollingPolicy.STABLE_MINUTES, PollingPolicy.nextDelayMinutes(now, prev, 10))
    }
}
