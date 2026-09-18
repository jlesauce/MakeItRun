package org.jls.makeitrun.workout.model

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

class FormatsTest {

    private val original = Locale.getDefault()

    @Before
    fun fixLocale() = Locale.setDefault(Locale.UK)

    @After
    fun restoreLocale() = Locale.setDefault(original)

    @Test
    fun `a duration under an hour is written in minutes and seconds`() {
        assertEquals("0:00", Formats.duration(0))
        assertEquals("0:09", Formats.duration(9))
        assertEquals("5:30", Formats.duration(330))
        assertEquals("59:59", Formats.duration(3599))
    }

    @Test
    fun `an hour or more brings the hours in`() {
        assertEquals("1:00:00", Formats.duration(3600))
        assertEquals("1:02:03", Formats.duration(3723))
    }

    @Test
    fun `a negative duration reads as zero rather than a minus sign`() {
        assertEquals("0:00", Formats.duration(-42))
    }

    @Test
    fun `a distance under a kilometre stays in metres`() {
        assertEquals("0 m", Formats.distance(0))
        assertEquals("999 m", Formats.distance(999))
    }

    @Test
    fun `a distance of a kilometre or more switches to kilometres`() {
        assertEquals("1.00 km", Formats.distance(1_000))
        assertEquals("6.20 km", Formats.distance(6_200))
    }

    @Test
    fun `a free step shows its label instead of a speed`() {
        assertEquals("Libre", Formats.target(null, showPace = false, freeLabel = "Libre"))
        assertEquals("Libre", Formats.target(null, showPace = true, freeLabel = "Libre"))
    }

    @Test
    fun `an imposed step reads as a speed or as a pace depending on the preference`() {
        assertEquals("12.0 km/h", Formats.target(12.0, showPace = false, freeLabel = "Libre"))
        assertEquals("5:00 /km", Formats.target(12.0, showPace = true, freeLabel = "Libre"))
    }
}
