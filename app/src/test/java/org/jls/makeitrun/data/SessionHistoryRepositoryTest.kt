package org.jls.makeitrun.data

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.jls.makeitrun.history.model.SessionOutcome
import org.jls.makeitrun.history.model.SessionResume
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SessionHistoryRepositoryTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "kind"
    }

    @Test
    fun `the resume point lands on the step the session stopped on`() = runTest {
        val dao = FakeSessionHistoryDao(
            records = listOf(record()),
            samples = listOf(
                sample(second = 0, meters = 0, stepIndex = 0),
                sample(second = 300, meters = 900, stepIndex = 0),
                sample(second = 301, meters = 903, stepIndex = 1),
                sample(second = 420, meters = 1_300, stepIndex = 1),
            ),
        )

        val point = SessionHistoryRepository(dao, json).findResumePoint(SESSION_ID)

        assertNotNull(point)
        assertEquals(1, point!!.stepIndex)
        assertEquals(119, point.stepElapsedSeconds)
        assertEquals(397, point.stepDistanceMeters)
        assertEquals(301, point.completedStepsSeconds)
        assertEquals(1_300, point.distanceMeters)
    }

    @Test
    fun `the time before the step and the time inside it add up to the session total`() = runTest {
        val dao = FakeSessionHistoryDao(
            records = listOf(record()),
            samples = listOf(
                sample(second = 0, meters = 0, stepIndex = 0),
                sample(second = 250, meters = 700, stepIndex = 1),
                sample(second = 613, meters = 1_900, stepIndex = 1),
            ),
        )

        val point = SessionHistoryRepository(dao, json).findResumePoint(SESSION_ID)!!

        assertEquals(613, point.completedStepsSeconds + point.stepElapsedSeconds)
    }

    @Test
    fun `the plan stored with the session is what gets resumed`() = runTest {
        val dao = FakeSessionHistoryDao(
            records = listOf(record()),
            samples = listOf(sample(second = 10, meters = 30, stepIndex = 0)),
        )

        val point = SessionHistoryRepository(dao, json).findResumePoint(SESSION_ID)!!

        assertEquals("Fractionne", point.workout.name)
        assertEquals(3, point.stepCount)
        assertEquals(7L, point.workout.id)
    }

    @Test
    fun `a workout deleted since the session falls back to no identifier`() = runTest {
        val dao = FakeSessionHistoryDao(
            records = listOf(record(workoutId = null)),
            samples = listOf(sample(second = 10, meters = 30, stepIndex = 0)),
        )

        val point = SessionHistoryRepository(dao, json).findResumePoint(SESSION_ID)!!

        assertEquals(0L, point.workout.id)
    }

    @Test
    fun `a session stopped beyond the window offers no resume point`() = runTest {
        val tooOld = System.currentTimeMillis() - SessionResume.WINDOW_MILLIS - 60_000
        val dao = FakeSessionHistoryDao(
            records = listOf(record(endedAt = tooOld)),
            samples = listOf(sample(second = 10, meters = 30, stepIndex = 0)),
        )

        assertNull(SessionHistoryRepository(dao, json).findResumePoint(SESSION_ID))
    }

    @Test
    fun `a session run to its end offers no resume point`() = runTest {
        val dao = FakeSessionHistoryDao(
            records = listOf(record(outcome = SessionOutcome.COMPLETED)),
            samples = listOf(sample(second = 420, meters = 1_300, stepIndex = 1)),
        )

        assertNull(SessionHistoryRepository(dao, json).findResumePoint(SESSION_ID))
    }

    @Test
    fun `a session still marked in progress offers no resume point`() = runTest {
        val dao = FakeSessionHistoryDao(
            records = listOf(record(outcome = SessionOutcome.IN_PROGRESS)),
            samples = listOf(sample(second = 420, meters = 1_300, stepIndex = 1)),
        )

        assertNull(SessionHistoryRepository(dao, json).findResumePoint(SESSION_ID))
    }

    @Test
    fun `a session without any reading offers no resume point`() = runTest {
        val dao = FakeSessionHistoryDao(records = listOf(record()))

        assertNull(SessionHistoryRepository(dao, json).findResumePoint(SESSION_ID))
    }

    @Test
    fun `an unknown session offers no resume point`() = runTest {
        val dao = FakeSessionHistoryDao()

        assertNull(SessionHistoryRepository(dao, json).findResumePoint(404L))
    }

    @Test
    fun `resuming reopens the original record instead of creating a second one`() = runTest {
        val dao = FakeSessionHistoryDao(
            records = listOf(record(failureReason = "Le tapis ne repond plus")),
            samples = listOf(sample(second = 420, meters = 1_300, stepIndex = 1)),
        )
        val repository = SessionHistoryRepository(dao, json)
        val point = repository.findResumePoint(SESSION_ID)!!

        val recorder = repository.resumeRecording(point)

        assertEquals(SESSION_ID, recorder.sessionId)
        assertEquals(1, dao.storedRecords.size)

        val reopened = dao.storedRecords.single()
        assertEquals(SessionOutcome.IN_PROGRESS.name, reopened.outcome)
        assertNull(reopened.endedAt)
        assertNull(reopened.failureReason)
    }

    @Test
    fun `a resumed recording carries on from the last second already stored`() = runTest {
        val dao = FakeSessionHistoryDao(
            records = listOf(record()),
            samples = listOf(sample(second = 420, meters = 1_300, stepIndex = 1)),
        )
        val repository = SessionHistoryRepository(dao, json)
        val point = repository.findResumePoint(SESSION_ID)!!

        repository.resumeRecording(point)
            .finish(SessionOutcome.STOPPED, null)

        val stored = dao.storedRecords.single()
        assertEquals(420, stored.elapsedSeconds)
        assertEquals(1_300, stored.distanceMeters)
        assertEquals(1, stored.stepsCompleted)
    }

    @Test
    fun `a session left open by a brutal shutdown is closed on its last reading`() = runTest {
        val dao = FakeSessionHistoryDao(
            records = listOf(record(endedAt = null, outcome = SessionOutcome.IN_PROGRESS)),
            samples = listOf(
                sample(second = 0, meters = 0, stepIndex = 0),
                sample(second = 742, meters = 2_400, stepIndex = 2),
            ),
        )

        SessionHistoryRepository(dao, json).closeInterruptedSessions()

        val stored = dao.storedRecords.single()
        assertEquals(SessionOutcome.STOPPED.name, stored.outcome)
        assertEquals(742, stored.elapsedSeconds)
        assertEquals(2_400, stored.distanceMeters)
        assertEquals(2, stored.stepsCompleted)
        assertEquals(STARTED_AT + 742_000L, stored.endedAt)
    }

    private fun record(
        workoutId: Long? = 7L,
        endedAt: Long? = System.currentTimeMillis() - 60_000,
        outcome: SessionOutcome = SessionOutcome.STOPPED,
        failureReason: String? = null,
    ) = SessionRecordEntity(
        id = SESSION_ID,
        workoutId = workoutId,
        workoutName = "Fractionne",
        planJson = PLAN_JSON,
        startedAt = STARTED_AT,
        endedAt = endedAt,
        outcome = outcome.name,
        failureReason = failureReason,
        elapsedSeconds = 420,
        distanceMeters = 1_300,
        stepsCompleted = 1,
        stepCount = 3,
        treadmillDistanceMeters = null,
        treadmillElapsedSeconds = null,
        energyKcal = null,
    )

    private fun sample(second: Int, meters: Int, stepIndex: Int) = SessionSampleEntity(
        sessionId = SESSION_ID,
        elapsedSeconds = second,
        distanceMeters = meters,
        stepIndex = stepIndex,
        speedKmh = 10.0,
        inclinationPercent = null,
        heartRateBpm = null,
        smoothedBpm = null,
        targetSpeedKmh = null,
        zone = null,
    )

    private companion object {
        const val SESSION_ID = 1L
        const val STARTED_AT = 1_800_000_000_000L

        val PLAN_JSON = """
            [
              {"kind":"step","id":"warmup","type":"WARM_UP",
               "duration":{"kind":"time","seconds":300},"targetSpeedKmh":6.0},
              {"kind":"step","id":"run","type":"RUN",
               "duration":{"kind":"time","seconds":600},"targetSpeedKmh":12.0},
              {"kind":"step","id":"cooldown","type":"COOL_DOWN",
               "duration":{"kind":"time","seconds":300},"targetSpeedKmh":5.0}
            ]
        """.trimIndent()
    }
}
