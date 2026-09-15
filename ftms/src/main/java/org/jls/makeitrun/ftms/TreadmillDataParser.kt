package org.jls.makeitrun.ftms

/**
 * Decode les trames de la caracteristique Treadmill Data (0x2ACD).
 *
 * La trame commence par 16 bits de drapeaux, suivis des seuls champs annonces par ces drapeaux,
 * dans l'ordre fixe par la specification. Toutes les valeurs multi-octets sont en little-endian.
 *
 * Attention au premier drapeau : contrairement aux autres, "More Data" indique la presence de la
 * vitesse instantanee lorsqu'il vaut **zero**.
 */
object TreadmillDataParser {

    private const val FLAG_MORE_DATA = 0
    private const val FLAG_AVERAGE_SPEED = 1
    private const val FLAG_TOTAL_DISTANCE = 2
    private const val FLAG_INCLINATION = 3
    private const val FLAG_ELEVATION_GAIN = 4
    private const val FLAG_INSTANTANEOUS_PACE = 5
    private const val FLAG_AVERAGE_PACE = 6
    private const val FLAG_EXPENDED_ENERGY = 7
    private const val FLAG_HEART_RATE = 8
    private const val FLAG_METABOLIC_EQUIVALENT = 9
    private const val FLAG_ELAPSED_TIME = 10
    private const val FLAG_REMAINING_TIME = 11
    private const val FLAG_FORCE_ON_BELT = 12

    /**
     * @return les mesures decodees, ou `null` si la trame est trop courte pour contenir
     * ne serait-ce que les drapeaux.
     */
    fun parse(payload: ByteArray): TreadmillData? {
        val cursor = ByteCursor(payload)
        val flags = cursor.uint16() ?: return null

        // Une machine peut annoncer plus de champs qu'elle n'en envoie reellement. Le curseur
        // renvoie alors null et les champs suivants restent simplement non renseignes.
        return TreadmillData(
            instantaneousSpeedKmh = if (!flags.isSet(FLAG_MORE_DATA)) cursor.uint16()?.hundredths() else null,
            averageSpeedKmh = if (flags.isSet(FLAG_AVERAGE_SPEED)) cursor.uint16()?.hundredths() else null,
            totalDistanceMeters = if (flags.isSet(FLAG_TOTAL_DISTANCE)) cursor.uint24() else null,
            inclinationPercent = if (flags.isSet(FLAG_INCLINATION)) cursor.sint16()?.tenths() else null,
            rampAngleDegrees = if (flags.isSet(FLAG_INCLINATION)) cursor.sint16()?.tenths() else null,
            positiveElevationGainMeters = if (flags.isSet(FLAG_ELEVATION_GAIN)) cursor.uint16()?.tenths() else null,
            negativeElevationGainMeters = if (flags.isSet(FLAG_ELEVATION_GAIN)) cursor.uint16()?.tenths() else null,
            instantaneousPaceKmPerMin = if (flags.isSet(FLAG_INSTANTANEOUS_PACE)) cursor.uint8()?.tenths() else null,
            averagePaceKmPerMin = if (flags.isSet(FLAG_AVERAGE_PACE)) cursor.uint8()?.tenths() else null,
            totalEnergyKcal = if (flags.isSet(FLAG_EXPENDED_ENERGY)) cursor.uint16() else null,
            energyPerHourKcal = if (flags.isSet(FLAG_EXPENDED_ENERGY)) cursor.uint16() else null,
            energyPerMinuteKcal = if (flags.isSet(FLAG_EXPENDED_ENERGY)) cursor.uint8() else null,
            heartRateBpm = if (flags.isSet(FLAG_HEART_RATE)) cursor.uint8() else null,
            metabolicEquivalent = if (flags.isSet(FLAG_METABOLIC_EQUIVALENT)) cursor.uint8()?.tenths() else null,
            elapsedTimeSeconds = if (flags.isSet(FLAG_ELAPSED_TIME)) cursor.uint16() else null,
            remainingTimeSeconds = if (flags.isSet(FLAG_REMAINING_TIME)) cursor.uint16() else null,
            forceOnBeltNewtons = if (flags.isSet(FLAG_FORCE_ON_BELT)) cursor.sint16() else null,
            powerOutputWatts = if (flags.isSet(FLAG_FORCE_ON_BELT)) cursor.sint16() else null,
        )
    }

    private fun Int.isSet(bit: Int): Boolean = (this shr bit) and 1 == 1

    private fun Int.hundredths(): Double = this / 100.0

    private fun Int.tenths(): Double = this / 10.0
}

/**
 * Lecteur sequentiel little-endian. Renvoie `null` des qu'il ne reste plus assez d'octets,
 * ce qui evite de faire planter l'application sur une trame inattendue.
 */
private class ByteCursor(private val bytes: ByteArray) {

    private var offset = 0

    fun uint8(): Int? = read(1) { it[0] }

    fun uint16(): Int? = read(2) { it[0] or (it[1] shl 8) }

    fun uint24(): Int? = read(3) { it[0] or (it[1] shl 8) or (it[2] shl 16) }

    fun sint16(): Int? = uint16()?.let { if (it >= 0x8000) it - 0x10000 else it }

    private inline fun read(count: Int, combine: (IntArray) -> Int): Int? {
        if (offset + count > bytes.size) return null
        val unsigned = IntArray(count) { bytes[offset + it].toInt() and 0xFF }
        offset += count
        return combine(unsigned)
    }
}
