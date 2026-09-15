package org.jls.makeitrun.ftms

import kotlin.math.roundToInt

/**
 * Instantane des mesures publiees par un tapis via la caracteristique Treadmill Data (0x2ACD).
 *
 * Chaque champ est optionnel : la machine choisit, trame par trame, les mesures qu'elle transmet
 * via son champ de drapeaux. Un champ a `null` signifie donc "non transmis", et non "zero".
 */
data class TreadmillData(
    val instantaneousSpeedKmh: Double? = null,
    val averageSpeedKmh: Double? = null,
    val totalDistanceMeters: Int? = null,
    val inclinationPercent: Double? = null,
    val rampAngleDegrees: Double? = null,
    val positiveElevationGainMeters: Double? = null,
    val negativeElevationGainMeters: Double? = null,
    val instantaneousPaceKmPerMin: Double? = null,
    val averagePaceKmPerMin: Double? = null,
    val totalEnergyKcal: Int? = null,
    val energyPerHourKcal: Int? = null,
    val energyPerMinuteKcal: Int? = null,
    val heartRateBpm: Int? = null,
    val metabolicEquivalent: Double? = null,
    val elapsedTimeSeconds: Int? = null,
    val remainingTimeSeconds: Int? = null,
    val forceOnBeltNewtons: Int? = null,
    val powerOutputWatts: Int? = null,
) {

    /**
     * Allure instantanee en secondes par kilometre, l'unite habituelle en course a pied.
     * Vaut `null` a l'arret, ou si la vitesse n'est pas transmise.
     */
    val paceSecondsPerKm: Int?
        get() = instantaneousSpeedKmh
            ?.takeIf { it > 0.0 }
            ?.let { (SECONDS_PER_HOUR / it).roundToInt() }

    private companion object {
        const val SECONDS_PER_HOUR = 3600.0
    }
}
