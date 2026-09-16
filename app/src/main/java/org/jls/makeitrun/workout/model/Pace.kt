package org.jls.makeitrun.workout.model

import kotlin.math.roundToInt

object Pace {

    private const val SECONDS_PER_HOUR = 3600.0

    fun secondsPerKmFrom(speedKmh: Double): Int? =
        if (speedKmh <= 0.0) null else (SECONDS_PER_HOUR / speedKmh).roundToInt()

    fun speedKmhFrom(secondsPerKm: Int): Double? =
        if (secondsPerKm <= 0) null else SECONDS_PER_HOUR / secondsPerKm

    fun format(secondsPerKm: Int): String = "%d:%02d".format(secondsPerKm / 60, secondsPerKm % 60)

    fun formatFromSpeed(speedKmh: Double): String =
        secondsPerKmFrom(speedKmh)?.let { format(it) } ?: "—"
}
