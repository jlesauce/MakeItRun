package org.jls.makeitrun.workout.model

import kotlinx.serialization.Serializable

@Serializable
enum class RegulationResponsiveness(
    val settleMillis: Long,
    val speedStepKmh: Double,
) {
    GENTLE(settleMillis = 60_000L, speedStepKmh = 0.3),
    NORMAL(settleMillis = 45_000L, speedStepKmh = 0.5),
    BRISK(settleMillis = 30_000L, speedStepKmh = 0.8);

    companion object {
        val DEFAULT = NORMAL

        fun fromName(name: String?): RegulationResponsiveness =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
