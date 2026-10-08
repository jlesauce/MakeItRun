package org.jls.makeitrun.history.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionChartTest {

    @Test
    fun `the speed axis rounds to readable steps around the values`() {
        val axis = ChartAxis.covering(6.0, 14.0)

        assertEquals(6.0, axis.minimum, DELTA)
        assertEquals(2.0, axis.step, DELTA)
        assertEquals(14.0, axis.maximum, DELTA)
    }

    @Test
    fun `the heart rate axis covers the whole range with round values`() {
        val axis = ChartAxis.covering(103.0, 176.0)

        assertEquals(100.0, axis.minimum, DELTA)
        assertEquals(20.0, axis.step, DELTA)
        assertTrue(axis.maximum >= 176.0)
    }

    @Test
    fun `a flat series still gets an axis with a span`() {
        val axis = ChartAxis.covering(10.0, 10.0)

        assertTrue(axis.maximum > axis.minimum)
        assertTrue(axis.minimum <= 10.0 && axis.maximum >= 10.0)
    }

    @Test
    fun `each axis has one label per grid line`() {
        assertEquals(
            listOf("6", "8", "10", "12", "14"),
            ChartAxis.covering(6.0, 14.0).labels(),
        )
    }

    @Test
    fun `time ticks fall on round minutes and stay few`() {
        assertEquals(listOf(0, 600, 1_200, 1_800), timeTicks(37 * 60))
        assertEquals(listOf(0, 60, 120, 180, 240), timeTicks(4 * 60 + 30))
        assertTrue(timeTicks(3 * 3_600).size <= 6)
    }

    private companion object {
        const val DELTA = 0.0001
    }
}
