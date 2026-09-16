package org.jls.makeitrun.heartrate

import org.jls.makeitrun.heartrate.HeartRateMeasurement.SensorContact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartRateMeasurementParserTest {

    @Test
    fun `a heart rate on one byte is decoded`() {
        val measurement = parse(0x00, 0x8A)

        assertEquals(138, measurement.beatsPerMinute)
        assertEquals(SensorContact.NOT_SUPPORTED, measurement.sensorContact)
        assertNull(measurement.energyExpendedKiloJoules)
        assertTrue(measurement.rrIntervalsMillis.isEmpty())
    }

    @Test
    fun `a heart rate above 255 is decoded on two bytes`() {
        val measurement = parse(0x01, 0x2C, 0x01)

        assertEquals(300, measurement.beatsPerMinute)
    }

    @Test
    fun `a sensor that reports contact is distinguished from one that cannot`() {
        assertEquals(SensorContact.DETECTED, parse(0x06, 0x8A).sensorContact)
        assertEquals(SensorContact.NOT_DETECTED, parse(0x04, 0x8A).sensorContact)
        assertEquals(SensorContact.NOT_SUPPORTED, parse(0x02, 0x8A).sensorContact)
    }

    @Test
    fun `energy expended is decoded after the heart rate`() {
        val measurement = parse(0x08, 0x8A, 0xE8, 0x03)

        assertEquals(1000, measurement.energyExpendedKiloJoules)
    }

    @Test
    fun `rr intervals are converted from 1024ths of a second to milliseconds`() {
        val measurement = parse(0x10, 0x8A, 0x00, 0x02, 0x00, 0x04)

        assertEquals(listOf(500, 1000), measurement.rrIntervalsMillis)
    }

    @Test
    fun `rr intervals are decoded after the energy expended`() {
        val measurement = parse(0x18, 0x8A, 0xE8, 0x03, 0x00, 0x02)

        assertEquals(1000, measurement.energyExpendedKiloJoules)
        assertEquals(listOf(500), measurement.rrIntervalsMillis)
    }

    @Test
    fun `a frame truncated after the flags is rejected`() {
        assertNull(HeartRateMeasurementParser.parse(byteArrayOf(0x00)))
    }

    @Test
    fun `an empty frame is rejected`() {
        assertNull(HeartRateMeasurementParser.parse(byteArrayOf()))
    }

    @Test
    fun `a frame announcing a 16 bit value but holding only one is rejected`() {
        assertNull(HeartRateMeasurementParser.parse(byteArrayOf(0x01, 0x8A.toByte())))
    }

    @Test
    fun `a measurement taken off the wrist is not usable`() {
        assertFalse(parse(0x04, 0x8A).isUsable)
    }

    @Test
    fun `a zero heart rate is not usable even when contact is reported`() {
        assertFalse(parse(0x06, 0x00).isUsable)
    }

    @Test
    fun `a measurement from a sensor without contact detection is usable`() {
        assertTrue(parse(0x00, 0x8A).isUsable)
    }

    private fun parse(vararg bytes: Int): HeartRateMeasurement =
        HeartRateMeasurementParser.parse(ByteArray(bytes.size) { bytes[it].toByte() })!!
}
