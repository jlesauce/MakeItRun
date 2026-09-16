package org.jls.makeitrun.workout.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class StepType {
    WARM_UP,
    RUN,
    RECOVER,
    COOL_DOWN,
}

@Serializable
sealed interface StepDuration {

    @Serializable
    @SerialName("time")
    data class Time(val seconds: Int) : StepDuration

    @Serializable
    @SerialName("distance")
    data class Distance(val meters: Int) : StepDuration
}

@Serializable
data class HeartRateTarget(
    val minBpm: Int,
    val maxBpm: Int,
) {

    val centerBpm: Int
        get() = (minBpm + maxBpm) / 2

    operator fun contains(beatsPerMinute: Int): Boolean = beatsPerMinute in minBpm..maxBpm

    companion object {
        const val LOWEST_BPM = 60
        const val HIGHEST_BPM = 220
        const val NARROWEST_WIDTH_BPM = 5

        fun isValid(minBpm: Int, maxBpm: Int): Boolean =
            minBpm >= LOWEST_BPM &&
                maxBpm <= HIGHEST_BPM &&
                maxBpm - minBpm >= NARROWEST_WIDTH_BPM
    }
}

@Serializable
sealed interface WorkoutElement {
    val id: String
}

@Serializable
@SerialName("step")
data class WorkoutStep(
    override val id: String,
    val type: StepType,
    val duration: StepDuration,
    val targetSpeedKmh: Double? = null,
    val inclinationPercent: Double? = null,
    val heartRateTarget: HeartRateTarget? = null,
) : WorkoutElement {

    val isHeartRateDriven: Boolean
        get() = heartRateTarget != null
}

@Serializable
@SerialName("repeat")
data class RepeatBlock(
    override val id: String,
    val repetitions: Int,
    val steps: List<WorkoutStep>,
) : WorkoutElement

data class Workout(
    val id: Long = 0L,
    val name: String,
    val elements: List<WorkoutElement>,
    val createdAt: Long = System.currentTimeMillis(),
)
