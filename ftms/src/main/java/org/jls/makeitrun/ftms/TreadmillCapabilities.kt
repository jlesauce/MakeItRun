package org.jls.makeitrun.ftms

data class TreadmillCapabilities(
    val speedRange: ValueRange?,
    val inclinationRange: ValueRange?,
    val canSetTargetSpeed: Boolean,
    val canSetTargetInclination: Boolean,
    val canStartAndStop: Boolean,
) {

    data class ValueRange(
        val minimum: Double,
        val maximum: Double,
        val increment: Double,
    ) {

        fun coerce(value: Double): Double {
            val clamped = value.coerceIn(minimum, maximum)
            if (increment <= 0.0) return clamped
            val steps = Math.round((clamped - minimum) / increment)
            return (minimum + steps * increment).coerceIn(minimum, maximum)
        }
    }

    companion object {
        val UNKNOWN = TreadmillCapabilities(
            speedRange = ValueRange(minimum = 1.0, maximum = 16.0, increment = 0.1),
            inclinationRange = null,
            canSetTargetSpeed = true,
            canSetTargetInclination = false,
            canStartAndStop = true,
        )
    }
}
