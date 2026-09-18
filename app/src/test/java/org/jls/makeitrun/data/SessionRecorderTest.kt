package org.jls.makeitrun.data

import kotlinx.coroutines.test.runTest
import org.jls.makeitrun.heartrate.HeartRateMeasurement
import org.jls.makeitrun.heartrate.HeartRateSample
import org.jls.makeitrun.history.model.SessionOutcome
import org.jls.makeitrun.session.SessionProgress
import org.jls.makeitrun.session.StepRemaining
import org.jls.makeitrun.workout.model.Repetition
import org.jls.makeitrun.workout.model.ResolvedStep
import org.jls.makeitrun.workout.model.StepDuration
import org.jls.makeitrun.workout.model.StepType
import org.jls.makeitrun.workout.model.WorkoutStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRecorderTest {

    @Test
    fun `only one sample per second reaches the database`() = runTest {
        val dao = FakeSessionHistoryDao(records = listOf(record()))
        val recorder = SessionRecorder(SESSION_ID, dao)

        repeat(4) { recorder.onTick(NOW, progress(second = 1), null) }
        recorder.onTick(NOW, progress(second = 2), null)
        recorder.finish(SessionOutcome.STOPPED, null)

        assertEquals(listOf(1, 2), dao.storedSamples.map { it.elapsedSeconds })
    }

    @Test
    fun `samples are written by batches rather than one by one`() = runTest {
        val dao = FakeSessionHistoryDao(records = listOf(record()))
        val recorder = SessionRecorder(SESSION_ID, dao)

        repeat(30) { second -> recorder.onTick(NOW, progress(second = second), null) }

        assertEquals(2, dao.insertSampleCalls)
        assertEquals(30, dao.storedSamples.size)
    }

    @Test
    fun `closing the session flushes the readings still pending`() = runTest {
        val dao = FakeSessionHistoryDao(records = listOf(record()))
        val recorder = SessionRecorder(SESSION_ID, dao)

        recorder.onTick(NOW, progress(second = 0), null)
        assertTrue(dao.storedSamples.isEmpty())

        recorder.finish(SessionOutcome.STOPPED, null)

        assertEquals(1, dao.storedSamples.size)
    }

    @Test
    fun `a heart rate older than ten seconds is left out`() = runTest {
        val dao = FakeSessionHistoryDao(records = listOf(record()))
        val recorder = SessionRecorder(SESSION_ID, dao)

        recorder.onTick(NOW, progress(second = 0), heartRate(150, receivedAt = NOW - 3_000))
        recorder.onTick(NOW, progress(second = 1), heartRate(150, receivedAt = NOW - 11_000))
        recorder.finish(SessionOutcome.STOPPED, null)

        assertEquals(listOf(150, null), dao.storedSamples.map { it.heartRateBpm })
    }

    @Test
    fun `a sensor reporting no skin contact is left out`() = runTest {
        val dao = FakeSessionHistoryDao(records = listOf(record()))
        val recorder = SessionRecorder(SESSION_ID, dao)

        val unusable = HeartRateSample(
            measurement = HeartRateMeasurement(
                beatsPerMinute = 150,
                sensorContact = HeartRateMeasurement.SensorContact.NOT_DETECTED,
                energyExpendedKiloJoules = null,
                rrIntervalsMillis = emptyList(),
            ),
            receivedAtElapsedMillis = NOW,
        )

        recorder.onTick(NOW, progress(second = 0), unusable)
        recorder.finish(SessionOutcome.STOPPED, null)

        assertNull(dao.storedSamples.single().heartRateBpm)
    }

    @Test
    fun `a completed session is credited with every step of its plan`() = runTest {
        val dao = FakeSessionHistoryDao(records = listOf(record(stepCount = 6)))
        val recorder = SessionRecorder(SESSION_ID, dao)

        recorder.onTick(NOW, progress(second = 30, stepIndex = 2), null)
        recorder.finish(SessionOutcome.COMPLETED, null)

        val stored = dao.storedRecords.single()
        assertEquals(6, stored.stepsCompleted)
        assertEquals(SessionOutcome.COMPLETED.name, stored.outcome)
    }

    @Test
    fun `an interrupted session keeps the step it stopped on and its reason`() = runTest {
        val dao = FakeSessionHistoryDao(records = listOf(record(stepCount = 6)))
        val recorder = SessionRecorder(SESSION_ID, dao)

        recorder.onTick(NOW, progress(second = 30, stepIndex = 2, meters = 120), null)
        recorder.finish(SessionOutcome.FAILED, "Le tapis ne repond plus")

        val stored = dao.storedRecords.single()
        assertEquals(2, stored.stepsCompleted)
        assertEquals(30, stored.elapsedSeconds)
        assertEquals(120, stored.distanceMeters)
        assertEquals("Le tapis ne repond plus", stored.failureReason)
    }

    @Test
    fun `a resumed session closed before its first reading keeps what was already stored`() =
        runTest {
            val dao = FakeSessionHistoryDao(
                records = listOf(record(elapsedSeconds = 600, distanceMeters = 1_800)),
            )
            val recorder = SessionRecorder(
                sessionId = SESSION_ID,
                dao = dao,
                lastRecordedSecond = 600,
                lastStepIndex = 3,
            )

            recorder.finish(SessionOutcome.STOPPED, null)

            val stored = dao.storedRecords.single()
            assertEquals(600, stored.elapsedSeconds)
            assertEquals(1_800, stored.distanceMeters)
            assertEquals(3, stored.stepsCompleted)
        }

    @Test
    fun `a resumed session ignores the seconds already written`() = runTest {
        val dao = FakeSessionHistoryDao(records = listOf(record()))
        val recorder = SessionRecorder(SESSION_ID, dao, lastRecordedSecond = 600)

        recorder.onTick(NOW, progress(second = 598), null)
        recorder.onTick(NOW, progress(second = 601), null)
        recorder.finish(SessionOutcome.STOPPED, null)

        assertEquals(listOf(601), dao.storedSamples.map { it.elapsedSeconds })
    }

    private fun heartRate(beats: Int, receivedAt: Long) = HeartRateSample(
        measurement = HeartRateMeasurement(
            beatsPerMinute = beats,
            sensorContact = HeartRateMeasurement.SensorContact.DETECTED,
            energyExpendedKiloJoules = null,
            rrIntervalsMillis = emptyList(),
        ),
        receivedAtElapsedMillis = receivedAt,
    )

    private fun record(
        stepCount: Int = 3,
        elapsedSeconds: Int = 0,
        distanceMeters: Int = 0,
    ) = SessionRecordEntity(
        id = SESSION_ID,
        workoutId = 7L,
        workoutName = "Fractionne",
        planJson = "[]",
        startedAt = 1_800_000_000_000L,
        endedAt = null,
        outcome = SessionOutcome.IN_PROGRESS.name,
        failureReason = null,
        elapsedSeconds = elapsedSeconds,
        distanceMeters = distanceMeters,
        stepsCompleted = 0,
        stepCount = stepCount,
        treadmillDistanceMeters = null,
        treadmillElapsedSeconds = null,
        energyKcal = null,
    )

    private fun progress(
        second: Int,
        stepIndex: Int = 0,
        meters: Int = second * 3,
    ) = SessionProgress(
        workoutName = "Fractionne",
        currentStep = resolvedStep(stepIndex),
        nextStep = null,
        stepIndex = stepIndex,
        stepCount = 3,
        stepElapsedSeconds = second,
        stepCoveredMeters = meters,
        remaining = StepRemaining.Seconds(60 - second),
        stepFraction = 0f,
        totalElapsedSeconds = second,
        totalDistanceMeters = meters,
        liveData = null,
    )

    private fun resolvedStep(index: Int) = ResolvedStep(
        step = WorkoutStep(
            id = "step-$index",
            type = StepType.RUN,
            duration = StepDuration.Time(60),
            targetSpeedKmh = 10.0,
        ),
        position = index,
        repetition = null as Repetition?,
    )

    private companion object {
        const val SESSION_ID = 1L
        const val NOW = 500_000L
    }
}
