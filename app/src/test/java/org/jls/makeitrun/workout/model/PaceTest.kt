package org.jls.makeitrun.workout.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaceTest {

    @Test
    fun `twelve kilometres per hour is a five minute kilometre`() {
        assertEquals(300, Pace.secondsPerKmFrom(12.0))
        assertEquals(12.0, Pace.speedKmhFrom(300)!!, DELTA)
    }

    @Test
    fun `rounding a pace to the second costs less than a tenth of a kilometre per hour`() {
        listOf(6.0, 8.5, 10.0, 13.7, 16.0).forEach { speed ->
            val pace = Pace.secondsPerKmFrom(speed)!!
            assertEquals(speed, Pace.speedKmhFrom(pace)!!, 0.1)
        }
    }

    @Test
    fun `a standstill has no pace`() {
        assertNull(Pace.secondsPerKmFrom(0.0))
        assertNull(Pace.secondsPerKmFrom(-1.0))
        assertNull(Pace.speedKmhFrom(0))
        assertNull(Pace.speedKmhFrom(-60))
    }

    @Test
    fun `the seconds of a pace are padded to two digits`() {
        assertEquals("5:00", Pace.format(300))
        assertEquals("4:05", Pace.format(245))
        assertEquals("10:30", Pace.format(630))
    }

    @Test
    fun `a pace beyond an hour per kilometre keeps counting in minutes`() {
        assertEquals("75:00", Pace.format(4_500))
    }

    @Test
    fun `a speed with no pace is written as a dash`() {
        assertEquals("—", Pace.formatFromSpeed(0.0))
        assertEquals("5:00", Pace.formatFromSpeed(12.0))
    }

    private companion object {
        const val DELTA = 0.0001
    }
}
