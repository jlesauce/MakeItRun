package org.jls.makeitrun.ftms

object TreadmillCapabilitiesParser {

    private const val TARGET_SPEED_BIT = 0
    private const val TARGET_INCLINATION_BIT = 1

    fun parse(
        featurePayload: ByteArray?,
        speedRangePayload: ByteArray?,
        inclinationRangePayload: ByteArray?,
    ): TreadmillCapabilities {
        val targetFeatures = featurePayload?.let { readUInt32(it, offset = 4) }

        return TreadmillCapabilities(
            speedRange = parseSpeedRange(speedRangePayload),
            inclinationRange = parseInclinationRange(inclinationRangePayload),
            canSetTargetSpeed = targetFeatures?.isSet(TARGET_SPEED_BIT) ?: true,
            canSetTargetInclination = targetFeatures?.isSet(TARGET_INCLINATION_BIT) ?: false,
            canStartAndStop = true,
        )
    }

    private fun parseSpeedRange(payload: ByteArray?): TreadmillCapabilities.ValueRange? {
        if (payload == null || payload.size < 6) return null
        return TreadmillCapabilities.ValueRange(
            minimum = readUInt16(payload, 0) / 100.0,
            maximum = readUInt16(payload, 2) / 100.0,
            increment = readUInt16(payload, 4) / 100.0,
        )
    }

    private fun parseInclinationRange(payload: ByteArray?): TreadmillCapabilities.ValueRange? {
        if (payload == null || payload.size < 6) return null
        return TreadmillCapabilities.ValueRange(
            minimum = readSInt16(payload, 0) / 10.0,
            maximum = readSInt16(payload, 2) / 10.0,
            increment = readUInt16(payload, 4) / 10.0,
        )
    }

    private fun readUInt16(payload: ByteArray, offset: Int): Int =
        (payload[offset].toInt() and 0xFF) or ((payload[offset + 1].toInt() and 0xFF) shl 8)

    private fun readSInt16(payload: ByteArray, offset: Int): Int =
        readUInt16(payload, offset).let { if (it >= 0x8000) it - 0x10000 else it }

    private fun readUInt32(payload: ByteArray, offset: Int): Long? {
        if (payload.size < offset + 4) return null
        var value = 0L
        for (index in 0 until 4) {
            value = value or ((payload[offset + index].toLong() and 0xFF) shl (8 * index))
        }
        return value
    }

    private fun Long.isSet(bit: Int): Boolean = (this shr bit) and 1L == 1L
}
