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
) : WorkoutElement

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
