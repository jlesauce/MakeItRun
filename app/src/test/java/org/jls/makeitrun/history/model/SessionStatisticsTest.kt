package org.jls.makeitrun.history.model

import org.jls.makeitrun.session.ZoneStatus
import org.jls.makeitrun.workout.model.Repetition
import org.jls.makeitrun.workout.model.ResolvedStep
import org.jls.makeitrun.workout.model.StepDuration
import org.jls.makeitrun.workout.model.StepType
import org.jls.makeitrun.workout.model.WorkoutStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStatisticsTest {

    @Test
    fun `a session without any sample produces no step report`() {
        assertTrue(SessionStatistics.steps(plan(2), emptyList()).isEmpty())
    }

    @Test
    fun `each step only keeps the distance it covered itself`() {
        val samples = listOf(
            sample(second = 0, meters = 0, stepIndex = 0),
            sample(second = 1, meters = 30, stepIndex = 0),
            sample(second = 2, meters = 60, stepIndex = 0),
            sample(second = 3, meters = 100, stepIndex = 1),
            sample(second = 4, meters = 145, stepIndex = 1),
        )

        val reports = SessionStatistics.steps(plan(2), samples)

        assertEquals(listOf(60, 85), reports.map { it.distanceMeters })
        assertEquals(listOf(3, 2), reports.map { it.elapsedSeconds })
    }

    @Test
    fun `the planned step is attached to the report of the same index`() {
        val plan = plan(2)
        val samples = listOf(sample(second = 0, meters = 0, stepIndex = 1))

        val report = SessionStatistics.steps(plan, samples).single()

        assertEquals(1, report.index)
        assertEquals(plan[1], report.planned)
    }

    @Test
    fun `a step beyond the recorded plan is reported without a planned step`() {
        val samples = listOf(sample(second = 0, meters = 0, stepIndex = 4))

        assertNull(SessionStatistics.steps(plan(2), samples).single().planned)
    }

    @Test
    fun `the average speed ignores the seconds where the belt was stopped`() {
        val samples = listOf(
            sample(second = 0, meters = 0, stepIndex = 0, speedKmh = 0.0),
            sample(second = 1, meters = 3, stepIndex = 0, speedKmh = 10.0),
            sample(second = 2, meters = 6, stepIndex = 0, speedKmh = 12.0),
        )

        val report = SessionStatistics.steps(plan(1), samples).single()

        assertEquals(11.0, report.averageSpeedKmh!!, DELTA)
    }

    @Test
    fun `heart rate statistics ignore the seconds without a measurement`() {
        val samples = listOf(
            sample(second = 0, meters = 0, stepIndex = 0, beats = null),
            sample(second = 1, meters = 3, stepIndex = 0, beats = 140),
            sample(second = 2, meters = 6, stepIndex = 0, beats = 150),
        )

        val report = SessionStatistics.steps(plan(1), samples).single()

        assertEquals(145, report.averageBpm)
        assertEquals(150, report.maximumBpm)
    }

    @Test
    fun `a session without a heart rate zone has no share to report`() {
        val samples = listOf(sample(second = 0, meters = 0, stepIndex = 0, beats = 140))

        assertNull(SessionStatistics.zoneShare(samples))
    }

    @Test
    fun `the zone share counts only the seconds that were actually measured`() {
        val samples = listOf(
            sample(second = 0, meters = 0, stepIndex = 0, zone = null),
            sample(second = 1, meters = 3, stepIndex = 0, zone = ZoneStatus.BELOW),
            sample(second = 2, meters = 6, stepIndex = 0, zone = ZoneStatus.IN_ZONE),
            sample(second = 3, meters = 9, stepIndex = 0, zone = ZoneStatus.IN_ZONE),
        )

        assertEquals(2f / 3f, SessionStatistics.zoneShare(samples)!!, DELTA.toFloat())
    }

    @Test
    fun `the incline reported for a step is the one held most of the time`() {
        val samples = listOf(
            sample(second = 0, meters = 0, stepIndex = 0, inclination = 0.0),
            sample(second = 1, meters = 3, stepIndex = 0, inclination = 2.0),
            sample(second = 2, meters = 6, stepIndex = 0, inclination = 2.0),
        )

        val report = SessionStatistics.steps(plan(1), samples).single()

        assertEquals(2.0, report.inclinationPercent!!, DELTA)
    }

    private fun plan(size: Int) = List(size) { index ->
        ResolvedStep(
            step = WorkoutStep(
                id = "step-$index",
                type = StepType.RUN,
                duration = StepDuration.Time(60),
            ),
            position = index,
            repetition = null as Repetition?,
        )
    }

    private fun sample(
        second: Int,
        meters: Int,
        stepIndex: Int,
        speedKmh: Double? = 10.0,
        inclination: Double? = null,
        beats: Int? = null,
        zone: ZoneStatus? = null,
    ) = SessionSample(
        elapsedSeconds = second,
        distanceMeters = meters,
        stepIndex = stepIndex,
        speedKmh = speedKmh,
        inclinationPercent = inclination,
        heartRateBpm = beats,
        smoothedBpm = null,
        targetSpeedKmh = null,
        zone = zone,
    )

    private companion object {
        const val DELTA = 0.0001
    }
}
