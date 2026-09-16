package org.jls.makeitrun.heartrate

object HeartRateMeasurementParser {

    private const val FLAG_SIXTEEN_BIT_VALUE = 0
    private const val FLAG_CONTACT_DETECTED = 1
    private const val FLAG_CONTACT_SUPPORTED = 2
    private const val FLAG_ENERGY_EXPENDED = 3
    private const val FLAG_RR_INTERVALS = 4

    private const val RR_UNITS_PER_SECOND = 1024.0

    fun parse(payload: ByteArray): HeartRateMeasurement? {
        val cursor = ByteCursor(payload)
        val flags = cursor.uint8() ?: return null

        val beatsPerMinute = if (flags.isSet(FLAG_SIXTEEN_BIT_VALUE)) {
            cursor.uint16()
        } else {
            cursor.uint8()
        } ?: return null

        val energyExpended = if (flags.isSet(FLAG_ENERGY_EXPENDED)) cursor.uint16() else null

        val rrIntervals = if (flags.isSet(FLAG_RR_INTERVALS)) {
            generateSequence { cursor.uint16() }
                .map { (it * 1000 / RR_UNITS_PER_SECOND).toInt() }
                .toList()
        } else {
            emptyList()
        }

        return HeartRateMeasurement(
            beatsPerMinute = beatsPerMinute,
            sensorContact = flags.toSensorContact(),
            energyExpendedKiloJoules = energyExpended,
            rrIntervalsMillis = rrIntervals,
        )
    }

    private fun Int.toSensorContact(): HeartRateMeasurement.SensorContact = when {
        !isSet(FLAG_CONTACT_SUPPORTED) -> HeartRateMeasurement.SensorContact.NOT_SUPPORTED
        isSet(FLAG_CONTACT_DETECTED) -> HeartRateMeasurement.SensorContact.DETECTED
        else -> HeartRateMeasurement.SensorContact.NOT_DETECTED
    }

    private fun Int.isSet(bit: Int): Boolean = (this shr bit) and 1 == 1
}

private class ByteCursor(private val bytes: ByteArray) {

    private var offset = 0

    fun uint8(): Int? = read(1) { it[0] }

    fun uint16(): Int? = read(2) { it[0] or (it[1] shl 8) }

    private inline fun read(count: Int, combine: (IntArray) -> Int): Int? {
        if (offset + count > bytes.size) return null
        val unsigned = IntArray(count) { bytes[offset + it].toInt() and 0xFF }
        offset += count
        return combine(unsigned)
    }
}
