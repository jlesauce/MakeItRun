package org.jls.makeitrun.workout.model

object Formats {

    fun duration(totalSeconds: Int): String {
        val seconds = totalSeconds.coerceAtLeast(0)
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val remaining = seconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, remaining)
        } else {
            "%d:%02d".format(minutes, remaining)
        }
    }

    fun distance(meters: Int): String =
        if (meters < 1_000) "$meters m" else "%.2f km".format(meters / 1_000.0)

    fun speed(speedKmh: Double): String = "%.1f km/h".format(speedKmh)

    fun target(speedKmh: Double?, showPace: Boolean, freeLabel: String): String = when {
        speedKmh == null -> freeLabel
        showPace -> "${Pace.formatFromSpeed(speedKmh)} /km"
        else -> speed(speedKmh)
    }
}
