package org.jls.makeitrun.ftms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TreadmillCapabilitiesParserTest {

    @Test
    fun `speed range is decoded in kilometres per hour`() {
        val capabilities = TreadmillCapabilitiesParser.parse(
            featurePayload = null,
            speedRangePayload = bytes(0x32, 0x00, 0xD0, 0x07, 0x0A, 0x00),
            inclinationRangePayload = null,
        )

        val range = capabilities.speedRange!!
        assertEquals(0.5, range.minimum, DELTA)
        assertEquals(20.0, range.maximum, DELTA)
        assertEquals(0.1, range.increment, DELTA)
    }

    @Test
    fun `inclination range handles negative slopes`() {
        val capabilities = TreadmillCapabilitiesParser.parse(
            featurePayload = null,
            speedRangePayload = null,
            inclinationRangePayload = bytes(0xE2, 0xFF, 0x96, 0x00, 0x05, 0x00),
        )

        val range = capabilities.inclinationRange!!
        assertEquals(-3.0, range.minimum, DELTA)
        assertEquals(15.0, range.maximum, DELTA)
        assertEquals(0.5, range.increment, DELTA)
    }

    @Test
    fun `target setting features are read from the second word`() {
        val capabilities = TreadmillCapabilitiesParser.parse(
            featurePayload = bytes(0x00, 0x00, 0x00, 0x00, 0x03, 0x00, 0x00, 0x00),
            speedRangePayload = null,
            inclinationRangePayload = null,
        )

        assertTrue(capabilities.canSetTargetSpeed)
        assertTrue(capabilities.canSetTargetInclination)
    }

    @Test
    fun `a machine without inclination control is detected`() {
        val capabilities = TreadmillCapabilitiesParser.parse(
            featurePayload = bytes(0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00),
            speedRangePayload = null,
            inclinationRangePayload = null,
        )

        assertTrue(capabilities.canSetTargetSpeed)
        assertFalse(capabilities.canSetTargetInclination)
    }

    @Test
    fun `unreadable characteristics leave the ranges unknown`() {
        val capabilities = TreadmillCapabilitiesParser.parse(null, null, null)

        assertNull(capabilities.speedRange)
        assertNull(capabilities.inclinationRange)
        assertTrue(capabilities.canSetTargetSpeed)
    }

    @Test
    fun `values are aligned on the increment declared by the machine`() {
        val range = TreadmillCapabilities.ValueRange(
            minimum = 1.0,
            maximum = 20.0,
            increment = 0.5,
        )

        assertEquals(12.5, range.coerce(12.4), DELTA)
        assertEquals(20.0, range.coerce(25.0), DELTA)
        assertEquals(1.0, range.coerce(0.2), DELTA)
    }

    private fun bytes(vararg values: Int): ByteArray =
        ByteArray(values.size) { values[it].toByte() }

    private companion object {
        const val DELTA = 0.001
    }
}
