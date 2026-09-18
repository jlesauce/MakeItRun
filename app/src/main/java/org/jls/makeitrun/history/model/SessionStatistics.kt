package org.jls.makeitrun.history.model

import org.jls.makeitrun.session.ZoneStatus
import org.jls.makeitrun.workout.model.ResolvedStep
import kotlin.math.roundToInt

object SessionStatistics {

    fun steps(
        plan: List<ResolvedStep>,
        samples: List<SessionSample>,
    ): List<SessionStepReport> {
        if (samples.isEmpty()) return emptyList()

        val ordered = samples.sortedBy { it.elapsedSeconds }
        var previousStepDistance = 0

        return ordered.groupBy { it.stepIndex }
            .toSortedMap()
            .map { (index, stepSamples) ->
                val lastDistance = stepSamples.maxOf { it.distanceMeters }
                val report = report(
                    index = index,
                    planned = plan.getOrNull(index),
                    samples = stepSamples,
                    distanceMeters = (lastDistance - previousStepDistance).coerceAtLeast(0),
                )
                previousStepDistance = lastDistance
                report
            }
    }

    fun zoneShare(samples: List<SessionSample>): Float? {
        val measured = samples.count { it.zone != null }
        if (measured == 0) return null
        return samples.count { it.zone == ZoneStatus.IN_ZONE }.toFloat() / measured
    }

    private fun report(
        index: Int,
        planned: ResolvedStep?,
        samples: List<SessionSample>,
        distanceMeters: Int,
    ): SessionStepReport {
        val beats = samples.mapNotNull { it.heartRateBpm }
        val speeds = samples.mapNotNull { it.speedKmh }.filter { it > 0.0 }
        val inclinations = samples.mapNotNull { it.inclinationPercent }

        return SessionStepReport(
            index = index,
            planned = planned,
            elapsedSeconds = samples.size,
            distanceMeters = distanceMeters,
            averageSpeedKmh = speeds.takeIf { it.isNotEmpty() }?.average(),
            inclinationPercent = inclinations.mostFrequent(),
            averageBpm = beats.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            maximumBpm = beats.maxOrNull(),
            zoneShare = zoneShare(samples),
        )
    }

    private fun List<Double>.mostFrequent(): Double? =
        groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
}
