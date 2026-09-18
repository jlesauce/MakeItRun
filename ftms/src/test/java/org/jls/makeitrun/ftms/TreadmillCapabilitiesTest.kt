package org.jls.makeitrun.ftms

import org.jls.makeitrun.ftms.TreadmillCapabilities.ValueRange
import org.junit.Assert.assertEquals
import org.junit.Test

class TreadmillCapabilitiesTest {

    @Test
    fun `a speed inside the range is snapped to the nearest step the treadmill accepts`() {
        val range = ValueRange(minimum = 1.0, maximum = 16.0, increment = 0.5)

        assertEquals(12.0, range.coerce(12.1), DELTA)
        assertEquals(12.5, range.coerce(12.4), DELTA)
        assertEquals(12.5, range.coerce(12.5), DELTA)
    }

    @Test
    fun `a speed outside the range is brought back to its closest bound`() {
        val range = ValueRange(minimum = 1.0, maximum = 16.0, increment = 0.5)

        assertEquals(1.0, range.coerce(0.2), DELTA)
        assertEquals(16.0, range.coerce(25.0), DELTA)
    }

    @Test
    fun `snapping never steps past the maximum`() {
        val range = ValueRange(minimum = 0.0, maximum = 15.8, increment = 0.5)

        assertEquals(15.8, range.coerce(15.8), DELTA)
        assertEquals(15.5, range.coerce(15.6), DELTA)
    }

    @Test
    fun `a range whose steps do not start at zero counts them from its minimum`() {
        val range = ValueRange(minimum = 0.8, maximum = 20.0, increment = 0.2)

        assertEquals(10.0, range.coerce(10.05), DELTA)
        assertEquals(0.8, range.coerce(0.8), DELTA)
    }

    @Test
    fun `a treadmill declaring no step keeps the value as it is`() {
        val range = ValueRange(minimum = 1.0, maximum = 16.0, increment = 0.0)

        assertEquals(12.34, range.coerce(12.34), DELTA)
        assertEquals(16.0, range.coerce(20.0), DELTA)
    }

    @Test
    fun `the fallback used for an unknown treadmill stays within common speeds`() {
        val range = TreadmillCapabilities.UNKNOWN.speedRange!!

        assertEquals(1.0, range.coerce(0.0), DELTA)
        assertEquals(16.0, range.coerce(30.0), DELTA)
        assertEquals(10.0, range.coerce(10.02), DELTA)
    }

    private companion object {
        const val DELTA = 0.0001
    }
}
