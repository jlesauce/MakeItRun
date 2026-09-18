package org.jls.makeitrun.session

import android.annotation.SuppressLint
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jls.makeitrun.data.SessionHistoryRepository
import org.jls.makeitrun.data.SessionRecorder
import org.jls.makeitrun.di.ApplicationScope
import org.jls.makeitrun.ftms.FtmsTreadmillClient
import org.jls.makeitrun.ftms.TreadmillCapabilities
import org.jls.makeitrun.heartrate.HeartRateClient
import org.jls.makeitrun.history.model.SessionOutcome
import org.jls.makeitrun.history.model.SessionResumePoint
import org.jls.makeitrun.workout.model.RegulationResponsiveness
import org.jls.makeitrun.workout.model.ResolvedStep
import org.jls.makeitrun.workout.model.Workout
import org.jls.makeitrun.workout.model.WorkoutPlan
import org.jls.makeitrun.workout.model.WorkoutStep
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.min

private data class StepOffsets(
    val elapsedSeconds: Int = 0,
    val distanceMeters: Int = 0,
)

@SuppressLint("MissingPermission")
@Singleton
class WorkoutSessionEngine @Inject constructor(
    private val client: FtmsTreadmillClient,
    private val heartRateClient: HeartRateClient,
    private val history: SessionHistoryRepository,
    @param:ApplicationScope private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
    val state = _state.asStateFlow()

    private var sessionJob: Job? = null
    private var speedGuardJob: Job? = null
    private val pauseRequested = MutableStateFlow(false)
    private val skipRequested = MutableStateFlow(false)

    private var startedWorkoutId: Long? = null

    private var beltHasMoved = false
    private var beltStoppedSinceMillis: Long? = null
    private var lowestSpeedSincePauseKmh: Double? = null
    private var beltRestartedSinceMillis: Long? = null
    private var pausedByTreadmill = false

    val runningWorkoutId: Long?
        get() = startedWorkoutId.takeIf { _state.value.isActive }

    fun start(
        workout: Workout,
        capabilities: TreadmillCapabilities?,
        responsiveness: RegulationResponsiveness = RegulationResponsiveness.DEFAULT,
        resumeFrom: SessionResumePoint? = null,
    ) {
        if (_state.value.isActive) {
            Timber.w("Une seance est deja en cours, demarrage ignore")
            return
        }
        sessionJob?.cancel()
        speedGuardJob?.cancel()
        pauseRequested.value = false
        skipRequested.value = false
        beltHasMoved = false
        beltStoppedSinceMillis = null
        lowestSpeedSincePauseKmh = null
        beltRestartedSinceMillis = null
        pausedByTreadmill = false
        startedWorkoutId = workout.id
        sessionJob = scope.launch {
            runSession(workout, capabilities, responsiveness, resumeFrom)
        }
    }

    fun reportFailure(reason: String) {
        if (_state.value.isActive) return
        _state.value = SessionState.Failed(reason)
    }

    fun pause() {
        pauseRequested.value = true
    }

    fun resume() {
        pausedByTreadmill = false
        pauseRequested.value = false
    }

    fun skipStep() {
        if (_state.value is SessionState.Running) skipRequested.value = true
    }

    fun stop() {
        cancelSession()
        scope.launch { runCatching { client.stop() } }
    }

    suspend fun stopAndAwaitTreadmill() {
        cancelSession()
        runCatching { client.stop() }
    }

    private fun cancelSession() {
        sessionJob?.cancel()
        sessionJob = null
        speedGuardJob?.cancel()
        speedGuardJob = null
        pauseRequested.value = false
        pausedByTreadmill = false
        _state.value = SessionState.Idle
    }

    fun acknowledge() {
        if (!_state.value.isActive) _state.value = SessionState.Idle
    }

    private suspend fun runSession(
        workout: Workout,
        capabilities: TreadmillCapabilities?,
        responsiveness: RegulationResponsiveness,
        resumeFrom: SessionResumePoint?,
    ) {
        val steps = WorkoutPlan.flatten(workout.elements)
        if (steps.isEmpty()) {
            _state.value = SessionState.Failed("Cet entrainement ne contient aucune etape.")
            return
        }

        var recorder: SessionRecorder? = null
        var outcome = SessionOutcome.STOPPED
        var failureReason: String? = null

        try {
            val control = client.requestControl()
            if (control?.isSuccess != true) {
                throw IllegalStateException(
                    "Le tapis n'a pas accepte la prise de controle. Verifiez qu'il est allume, " +
                        "qu'aucune autre application n'y est connectee, puis relancez la " +
                        "connexion depuis l'ecran d'accueil."
                )
            }

            countDown()
            recorder = if (resumeFrom == null) {
                history.startRecording(workout, steps.size)
            } else {
                history.resumeRecording(resumeFrom)
            }
            client.start()

            val sessionDistanceOrigin = currentTreadmillDistance()
            val sessionDistanceOffset = resumeFrom?.distanceMeters ?: 0
            val firstStepIndex = resumeFrom?.stepIndex ?: 0
            var completedStepsSeconds = resumeFrom?.completedStepsSeconds ?: 0

            for (index in firstStepIndex until steps.size) {
                applyStep(steps[index].step, capabilities)
                completedStepsSeconds += runStep(
                    workout = workout,
                    steps = steps,
                    index = index,
                    completedStepsSeconds = completedStepsSeconds,
                    sessionDistanceOrigin = sessionDistanceOrigin,
                    sessionDistanceOffset = sessionDistanceOffset,
                    stepOffsets = if (index == firstStepIndex) {
                        StepOffsets(
                            elapsedSeconds = resumeFrom?.stepElapsedSeconds ?: 0,
                            distanceMeters = resumeFrom?.stepDistanceMeters ?: 0,
                        )
                    } else {
                        StepOffsets()
                    },
                    capabilities = capabilities,
                    responsiveness = responsiveness,
                    recorder = recorder,
                )
            }

            client.stop()
            outcome = SessionOutcome.COMPLETED
            _state.value = SessionState.Finished(
                workoutName = workout.name,
                totalElapsedSeconds = completedStepsSeconds,
                totalDistanceMeters = sessionDistanceOffset +
                    (currentTreadmillDistance() - sessionDistanceOrigin).coerceAtLeast(0),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            Timber.e(failure, "Seance interrompue")
            runCatching { client.stop() }
            outcome = SessionOutcome.FAILED
            failureReason = failure.message ?: "Erreur pendant la seance"
            _state.value = SessionState.Failed(failureReason)
        } finally {
            withContext(NonCancellable) {
                runCatching { recorder?.finish(outcome, failureReason) }
                    .onFailure { Timber.e(it, "Seance non enregistree dans l'historique") }
            }
        }
    }

    private suspend fun countDown() {
        for (second in COUNTDOWN_SECONDS downTo 1) {
            _state.value = SessionState.CountingDown(second)
            delay(1_000)
        }
    }

    private suspend fun runStep(
        workout: Workout,
        steps: List<ResolvedStep>,
        index: Int,
        completedStepsSeconds: Int,
        sessionDistanceOrigin: Int,
        sessionDistanceOffset: Int,
        stepOffsets: StepOffsets,
        capabilities: TreadmillCapabilities?,
        responsiveness: RegulationResponsiveness,
        recorder: SessionRecorder?,
    ): Int {
        val resolved = steps[index]
        val stepDistanceOrigin = currentTreadmillDistance()
        val stepStartedAt = SystemClock.elapsedRealtime()
        skipRequested.value = false
        var pausedMillis = 0L
        var pauseStartedAt: Long? = null
        var pauseCommandSent = false

        val regulator = startRegulation(resolved.step, capabilities, responsiveness)
        var regulation: RegulationOutcome? = null

        while (true) {
            delay(TICK_MILLIS)

            val now = SystemClock.elapsedRealtime()
            followTreadmillMotion(now)
            val paused = pauseRequested.value

            if (paused) {
                if (pauseStartedAt == null) {
                    pauseStartedAt = now
                    if (!pausedByTreadmill) {
                        pauseCommandSent = true
                        runCatching { client.pause() }
                    }
                }
            } else if (pauseStartedAt != null) {
                pausedMillis += now - pauseStartedAt
                pauseStartedAt = null
                if (pauseCommandSent && !isBeltMoving()) runCatching { client.start() }
                pauseCommandSent = false
            }

            val pausedSoFar = pausedMillis + (pauseStartedAt?.let { now - it } ?: 0L)
            val elapsedSeconds = stepOffsets.elapsedSeconds +
                ((now - stepStartedAt - pausedSoFar) / 1_000).toInt()
            val coveredMeters = stepOffsets.distanceMeters +
                (currentTreadmillDistance() - stepDistanceOrigin).coerceAtLeast(0)

            if (regulator != null && !paused) {
                regulation = regulator.update(now, heartRateClient.sample.value)
                    .also { applyRegulation(it) }
            }

            val progress = SessionProgress(
                workoutName = workout.name,
                currentStep = resolved,
                nextStep = steps.getOrNull(index + 1),
                stepIndex = index,
                stepCount = steps.size,
                stepElapsedSeconds = elapsedSeconds,
                stepCoveredMeters = coveredMeters,
                remaining = StepProgress.remaining(resolved.step, elapsedSeconds, coveredMeters),
                stepFraction = StepProgress.fraction(resolved.step, elapsedSeconds, coveredMeters),
                totalElapsedSeconds = completedStepsSeconds + elapsedSeconds,
                totalDistanceMeters = sessionDistanceOffset +
                    (currentTreadmillDistance() - sessionDistanceOrigin).coerceAtLeast(0),
                liveData = client.treadmillData.value,
                regulation = regulation,
            )

            _state.value = if (paused) {
                SessionState.Paused(progress, isTreadmillStopped = pausedByTreadmill)
            } else {
                SessionState.Running(progress)
            }

            if (!paused) {
                recorder?.onTick(now, progress, heartRateClient.sample.value)
            }

            if (skipRequested.compareAndSet(expect = true, update = false)) {
                Timber.i("Etape %d sur %d passee a la demande", index + 1, steps.size)
                return elapsedSeconds
            }

            if (!paused &&
                StepProgress.isComplete(resolved.step, elapsedSeconds, coveredMeters)
            ) {
                return elapsedSeconds
            }
        }
    }

    private suspend fun startRegulation(
        step: WorkoutStep,
        capabilities: TreadmillCapabilities?,
        responsiveness: RegulationResponsiveness,
    ): HeartRateRegulator? {
        val target = step.heartRateTarget ?: return null
        val speedRange = capabilities?.speedRange ?: TreadmillCapabilities.UNKNOWN.speedRange!!
        val stepResponsiveness = step.regulationResponsiveness ?: responsiveness
        val initialSpeed = HeartRateRegulator.startingSpeedKmh(target, speedRange)

        val regulator = HeartRateRegulator(
            target = target,
            speedRange = speedRange,
            responsiveness = stepResponsiveness,
            initialSpeedKmh = initialSpeed,
        )
        Timber.i(
            "Etape %s : zone %d-%d bpm, correction %s, depart a %.1f km/h",
            step.type,
            target.minBpm,
            target.maxBpm,
            stepResponsiveness,
            initialSpeed,
        )
        setSpeed(initialSpeed)
        return regulator
    }

    private suspend fun applyRegulation(outcome: RegulationOutcome) {
        if (!outcome.speedChanged) return
        Timber.i(
            "Regulation : %d bpm lisses, nouvelle consigne %.1f km/h",
            outcome.smoothedBpm ?: 0,
            outcome.targetSpeedKmh,
        )
        setSpeed(outcome.targetSpeedKmh)
    }

    private suspend fun setSpeed(speedKmh: Double) {
        client.setTargetSpeed(speedKmh)
        speedGuardJob?.cancel()
        speedGuardJob = scope.launch { resendWhileIgnored(speedKmh) }
    }

    private suspend fun resendWhileIgnored(target: Double) {
        repeat(SPEED_RESEND_ATTEMPTS) {
            delay(SPEED_RESEND_DELAY_MILLIS)
            val measured = client.treadmillData.value?.instantaneousSpeedKmh ?: return
            if (abs(measured - target) <= SPEED_TOLERANCE_KMH) return
            Timber.w(
                "Le tapis tourne a %.1f km/h, consigne %.1f km/h renvoyee",
                measured,
                target,
            )
            client.setTargetSpeed(target)
        }
    }

    private suspend fun applyStep(step: WorkoutStep, capabilities: TreadmillCapabilities?) {
        step.inclinationPercent?.let { requested ->
            if (capabilities?.canSetTargetInclination == false) return@let
            val inclination = capabilities?.inclinationRange?.coerce(requested) ?: requested
            Timber.i("Etape %s : pente %.1f %%", step.type, inclination)
            client.setTargetInclination(inclination)
            delay(COMMAND_SETTLE_MILLIS)
        }

        step.targetSpeedKmh?.let { requested ->
            val speed = capabilities?.speedRange?.coerce(requested) ?: requested
            Timber.i("Etape %s : consigne %.1f km/h", step.type, speed)
            setSpeed(speed)
        }
    }

    private fun followTreadmillMotion(now: Long) {
        val speedKmh = client.treadmillData.value?.instantaneousSpeedKmh ?: return

        if (pauseRequested.value) {
            beltHasMoved = false
            beltStoppedSinceMillis = null
            if (!hasBeltRestarted(now, speedKmh)) return

            Timber.i("Tapis relance depuis sa console, reprise de la seance")
            pausedByTreadmill = false
            pauseRequested.value = false
            return
        }

        lowestSpeedSincePauseKmh = null
        beltRestartedSinceMillis = null
        if (!hasBeltStopped(now, speedKmh)) return

        Timber.i("Tapis arrete depuis sa console, seance mise en pause")
        pausedByTreadmill = true
        pauseRequested.value = true
    }

    private fun hasBeltStopped(now: Long, speedKmh: Double): Boolean {
        if (speedKmh > BELT_MOVING_THRESHOLD_KMH) {
            beltHasMoved = true
            beltStoppedSinceMillis = null
            return false
        }
        if (!beltHasMoved) return false

        val stoppedSince = beltStoppedSinceMillis ?: now.also { beltStoppedSinceMillis = it }
        return now - stoppedSince >= BELT_STOP_GRACE_MILLIS
    }

    private fun hasBeltRestarted(now: Long, speedKmh: Double): Boolean {
        val lowest = min(speedKmh, lowestSpeedSincePauseKmh ?: speedKmh)
        lowestSpeedSincePauseKmh = lowest

        if (speedKmh < lowest + BELT_RESTART_MARGIN_KMH) {
            beltRestartedSinceMillis = null
            return false
        }

        val risingSince = beltRestartedSinceMillis ?: now.also { beltRestartedSinceMillis = it }
        return now - risingSince >= BELT_RESTART_GRACE_MILLIS
    }

    private fun isBeltMoving(): Boolean =
        (client.treadmillData.value?.instantaneousSpeedKmh ?: 0.0) > BELT_MOVING_THRESHOLD_KMH

    private fun currentTreadmillDistance(): Int =
        client.treadmillData.value?.totalDistanceMeters ?: 0

    private companion object {
        const val COUNTDOWN_SECONDS = 5

        const val TICK_MILLIS = 250L

        const val COMMAND_SETTLE_MILLIS = 500L
        const val SPEED_RESEND_DELAY_MILLIS = 4_000L
        const val SPEED_RESEND_ATTEMPTS = 3
        const val SPEED_TOLERANCE_KMH = 0.05

        const val BELT_MOVING_THRESHOLD_KMH = 0.1
        const val BELT_STOP_GRACE_MILLIS = 3_000L
        const val BELT_RESTART_MARGIN_KMH = 0.5
        const val BELT_RESTART_GRACE_MILLIS = 1_500L
    }
}
