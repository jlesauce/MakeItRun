package org.jls.makeitrun.ftms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TreadmillDataParserTest {

    @Test
    fun `speed is present when the more data flag is cleared`() {
        val data = parse(0x00, 0x00, 0x2C, 0x01)

        assertEquals(3.0, data.instantaneousSpeedKmh!!, DELTA)
        assertNull(data.averageSpeedKmh)
        assertNull(data.totalDistanceMeters)
    }

    @Test
    fun `speed is absent when the more data flag is set`() {
        val data = parse(0x01, 0x00, 0x2C, 0x01)

        assertNull(data.instantaneousSpeedKmh)
    }

    @Test
    fun `distance and elapsed time are decoded after the speed`() {
        val data = parse(
            0x04, 0x04,
            0x5E, 0x01,
            0xD2, 0x04, 0x00,
            0x41, 0x00,
        )

        assertEquals(3.5, data.instantaneousSpeedKmh!!, DELTA)
        assertEquals(1234, data.totalDistanceMeters)
        assertEquals(65, data.elapsedTimeSeconds)
    }

    @Test
    fun `negative inclination is decoded as a signed value`() {
        val data = parse(
            0x08, 0x00,
            0x5E, 0x01,
            0xE7, 0xFF,
            0x0A, 0x00,
        )

        assertEquals(-2.5, data.inclinationPercent!!, DELTA)
        assertEquals(1.0, data.rampAngleDegrees!!, DELTA)
    }

    @Test
    fun `heart rate is decoded when announced`() {
        val data = parse(0x00, 0x01, 0x5E, 0x01, 0x8A)

        assertEquals(138, data.heartRateBpm)
    }

    @Test
    fun `a frame shorter than announced leaves the missing fields empty`() {
        val data = parse(0x04, 0x00, 0x5E, 0x01)

        assertEquals(3.5, data.instantaneousSpeedKmh!!, DELTA)
        assertNull(data.totalDistanceMeters)
    }

    @Test
    fun `a frame too short to hold the flags is rejected`() {
        assertNull(TreadmillDataParser.parse(byteArrayOf(0x00)))
    }

    @Test
    fun `pace is derived from the instantaneous speed`() {
        val data = parse(0x00, 0x00, 0xB0, 0x04)

        assertEquals(300, data.paceSecondsPerKm)
    }

    @Test
    fun `pace is unavailable when the treadmill is stopped`() {
        val data = parse(0x00, 0x00, 0x00, 0x00)

        assertNull(data.paceSecondsPerKm)
    }

    private fun parse(vararg bytes: Int): TreadmillData =
        TreadmillDataParser.parse(ByteArray(bytes.size) { bytes[it].toByte() })!!

    private companion object {
        const val DELTA = 0.001
    }
}
