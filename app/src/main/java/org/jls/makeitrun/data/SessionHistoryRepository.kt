package org.jls.makeitrun.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import org.jls.makeitrun.history.model.SessionOutcome
import org.jls.makeitrun.history.model.SessionReport
import org.jls.makeitrun.history.model.SessionResume
import org.jls.makeitrun.history.model.SessionResumePoint
import org.jls.makeitrun.history.model.SessionSample
import org.jls.makeitrun.history.model.SessionStatistics
import org.jls.makeitrun.history.model.SessionSummary
import org.jls.makeitrun.session.ZoneStatus
import org.jls.makeitrun.workout.model.Workout
import org.jls.makeitrun.workout.model.WorkoutElement
import org.jls.makeitrun.workout.model.WorkoutPlan
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class SessionHistoryRepository @Inject constructor(
    private val dao: SessionHistoryDao,
    private val json: Json,
) {

    fun observeSummaries(): Flow<List<SessionSummary>> =
        dao.observeSummaries(SessionOutcome.IN_PROGRESS.name)
            .map { rows -> rows.map { it.toSummary() } }

    suspend fun findReport(id: Long): SessionReport? {
        val record = dao.findRecord(id) ?: return null
        val samples = dao.findSamples(id).map { it.toSample() }
        val plan = WorkoutPlan.flatten(record.elements())

        return SessionReport(
            summary = record.toSummary(samples),
            failureReason = record.failureReason,
            treadmillDistanceMeters = record.treadmillDistanceMeters,
            treadmillElapsedSeconds = record.treadmillElapsedSeconds,
            energyKcal = record.energyKcal,
            steps = SessionStatistics.steps(plan, samples),
            samples = samples,
        )
    }

    suspend fun delete(id: Long) = dao.deleteRecord(id)

    suspend fun startRecording(workout: Workout, stepCount: Int): SessionRecorder {
        val id = dao.insertRecord(
            SessionRecordEntity(
                workoutId = workout.id.takeIf { it != 0L },
                workoutName = workout.name,
                planJson = json.encodeToString<List<WorkoutElement>>(workout.elements),
                startedAt = System.currentTimeMillis(),
                endedAt = null,
                outcome = SessionOutcome.IN_PROGRESS.name,
                failureReason = null,
                elapsedSeconds = 0,
                distanceMeters = 0,
                stepsCompleted = 0,
                stepCount = stepCount,
                treadmillDistanceMeters = null,
                treadmillElapsedSeconds = null,
                energyKcal = null,
            )
        )
        Timber.i("Enregistrement de la seance %d : %s", id, workout.name)
        return SessionRecorder(id, dao)
    }

    suspend fun findResumePoint(id: Long): SessionResumePoint? {
        val record = dao.findRecord(id) ?: return null
        if (!SessionResume.isResumableOutcome(SessionOutcome.parse(record.outcome))) return null
        if (!SessionResume.isWithinWindow(record.endedAt, System.currentTimeMillis())) return null

        val elements = record.elements()
        val stepCount = WorkoutPlan.flatten(elements).size
        val samples = dao.findSamples(id)
        val last = samples.lastOrNull() ?: return null
        if (last.stepIndex >= stepCount) return null

        val stepStart = samples.first { it.stepIndex == last.stepIndex }

        return SessionResumePoint(
            sessionId = id,
            workout = Workout(
                id = record.workoutId ?: 0L,
                name = record.workoutName,
                elements = elements,
                createdAt = record.startedAt,
            ),
            stepCount = stepCount,
            stepIndex = last.stepIndex,
            stepElapsedSeconds = last.elapsedSeconds - stepStart.elapsedSeconds,
            stepDistanceMeters = last.distanceMeters - stepStart.distanceMeters,
            completedStepsSeconds = stepStart.elapsedSeconds,
            distanceMeters = last.distanceMeters,
        )
    }

    suspend fun resumeRecording(point: SessionResumePoint): SessionRecorder {
        dao.findRecord(point.sessionId)?.let { record ->
            dao.updateRecord(
                record.copy(
                    endedAt = null,
                    outcome = SessionOutcome.IN_PROGRESS.name,
                    failureReason = null,
                )
            )
        }
        Timber.i(
            "Reprise de la seance %d a l'etape %d sur %d",
            point.sessionId,
            point.stepIndex + 1,
            point.stepCount,
        )
        return SessionRecorder(
            sessionId = point.sessionId,
            dao = dao,
            lastRecordedSecond = point.completedStepsSeconds + point.stepElapsedSeconds,
            lastStepIndex = point.stepIndex,
        )
    }

    suspend fun closeInterruptedSessions() {
        val interrupted = dao.findRecordsWithOutcome(SessionOutcome.IN_PROGRESS.name)
        if (interrupted.isEmpty()) return

        interrupted.forEach { record ->
            val last = dao.findLastSample(record.id)
            dao.updateRecord(
                record.copy(
                    outcome = SessionOutcome.STOPPED.name,
                    endedAt = record.startedAt + (last?.elapsedSeconds ?: 0) * MILLIS_PER_SECOND,
                    elapsedSeconds = last?.elapsedSeconds ?: 0,
                    distanceMeters = last?.distanceMeters ?: 0,
                    stepsCompleted = last?.stepIndex ?: 0,
                )
            )
        }
        Timber.i("%d seance(s) restee(s) ouverte(s) refermee(s)", interrupted.size)
    }

    private fun SessionRecordEntity.elements(): List<WorkoutElement> = runCatching {
        json.decodeFromString<List<WorkoutElement>>(planJson)
    }.onFailure {
        Timber.e(it, "Plan de la seance %d illisible", id)
    }.getOrDefault(emptyList())

    private fun SessionRecordEntity.toSummary(samples: List<SessionSample>): SessionSummary {
        val beats = samples.mapNotNull { it.heartRateBpm }
        return SessionSummary(
            id = id,
            workoutName = workoutName,
            startedAt = startedAt,
            endedAt = endedAt,
            outcome = SessionOutcome.parse(outcome),
            elapsedSeconds = elapsedSeconds,
            distanceMeters = distanceMeters,
            stepsCompleted = stepsCompleted,
            stepCount = stepCount,
            averageBpm = if (beats.isEmpty()) null else beats.average().roundToInt(),
            maximumBpm = beats.maxOrNull(),
            zoneShare = SessionStatistics.zoneShare(samples),
        )
    }

    private fun SessionSummaryRow.toSummary() = SessionSummary(
        id = id,
        workoutName = workoutName,
        startedAt = startedAt,
        endedAt = endedAt,
        outcome = SessionOutcome.parse(outcome),
        elapsedSeconds = elapsedSeconds,
        distanceMeters = distanceMeters,
        stepsCompleted = stepsCompleted,
        stepCount = stepCount,
        averageBpm = averageBpm?.roundToInt(),
        maximumBpm = maximumBpm,
        zoneShare = if (secondsWithZone == 0) {
            null
        } else {
            secondsInZone.toFloat() / secondsWithZone
        },
    )

    private fun SessionSampleEntity.toSample() = SessionSample(
        elapsedSeconds = elapsedSeconds,
        distanceMeters = distanceMeters,
        stepIndex = stepIndex,
        speedKmh = speedKmh,
        inclinationPercent = inclinationPercent,
        heartRateBpm = heartRateBpm,
        smoothedBpm = smoothedBpm,
        targetSpeedKmh = targetSpeedKmh,
        zone = zone?.let { name -> ZoneStatus.entries.firstOrNull { it.name == name } },
    )

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
    }
}
