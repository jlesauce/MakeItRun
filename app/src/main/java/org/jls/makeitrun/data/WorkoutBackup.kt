package org.jls.makeitrun.data

import kotlinx.serialization.Serializable
import org.jls.makeitrun.ftms.TreadmillCapabilities
import org.jls.makeitrun.workout.model.WorkoutElement

@Serializable
data class WorkoutBackup(
    val format: Int = CURRENT_FORMAT,
    val exportedAt: Long,
    val treadmill: BackupTreadmill? = null,
    val workouts: List<BackupWorkout>,
) {
    companion object {
        const val CURRENT_FORMAT = 2
    }
}

@Serializable
data class BackupWorkout(
    val name: String,
    val createdAt: Long,
    val elements: List<WorkoutElement>,
)

@Serializable
data class BackupTreadmill(
    val address: String,
    val name: String? = null,
    val showPaceInsteadOfSpeed: Boolean = true,
    val capabilities: BackupCapabilities? = null,
)

@Serializable
data class BackupCapabilities(
    val speedMin: Double? = null,
    val speedMax: Double? = null,
    val speedIncrement: Double? = null,
    val inclinationMin: Double? = null,
    val inclinationMax: Double? = null,
    val inclinationIncrement: Double? = null,
    val canSetTargetSpeed: Boolean = false,
    val canSetTargetInclination: Boolean = false,
    val canStartAndStop: Boolean = false,
) {
    fun toCapabilities() = TreadmillCapabilities(
        speedRange = range(speedMin, speedMax, speedIncrement),
        inclinationRange = range(inclinationMin, inclinationMax, inclinationIncrement),
        canSetTargetSpeed = canSetTargetSpeed,
        canSetTargetInclination = canSetTargetInclination,
        canStartAndStop = canStartAndStop,
    )

    private fun range(min: Double?, max: Double?, increment: Double?) =
        if (min != null && max != null && increment != null) {
            TreadmillCapabilities.ValueRange(min, max, increment)
        } else {
            null
        }

    companion object {
        fun from(capabilities: TreadmillCapabilities) = BackupCapabilities(
            speedMin = capabilities.speedRange?.minimum,
            speedMax = capabilities.speedRange?.maximum,
            speedIncrement = capabilities.speedRange?.increment,
            inclinationMin = capabilities.inclinationRange?.minimum,
            inclinationMax = capabilities.inclinationRange?.maximum,
            inclinationIncrement = capabilities.inclinationRange?.increment,
            canSetTargetSpeed = capabilities.canSetTargetSpeed,
            canSetTargetInclination = capabilities.canSetTargetInclination,
            canStartAndStop = capabilities.canStartAndStop,
        )
    }
}
