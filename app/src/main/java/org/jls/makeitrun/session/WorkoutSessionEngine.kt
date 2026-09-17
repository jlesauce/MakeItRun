package org.jls.makeitrun.session

import android.annotation.SuppressLint
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jls.makeitrun.di.ApplicationScope
import org.jls.makeitrun.ftms.FtmsTreadmillClient
import org.jls.makeitrun.ftms.TreadmillCapabilities
import org.jls.makeitrun.heartrate.HeartRateClient
import org.jls.makeitrun.workout.model.RegulationResponsiveness
import org.jls.makeitrun.workout.model.ResolvedStep
import org.jls.makeitrun.workout.model.Workout
import org.jls.makeitrun.workout.model.WorkoutPlan
import org.jls.makeitrun.workout.model.WorkoutStep
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@SuppressLint("MissingPermission")
@Singleton
class WorkoutSessionEngine @Inject constructor(
    private val client: FtmsTreadmillClient,
    private val heartRateClient: HeartRateClient,
    @param:ApplicationScope private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
    val state = _state.asStateFlow()

    private var sessionJob: Job? = null
    private var speedGuardJob: Job? = null
    private val pauseRequested = MutableStateFlow(false)
    private val skipRequested = MutableStateFlow(false)

    private var startedWorkoutId: Long? = null

    val runningWorkoutId: Long?
        get() = startedWorkoutId.takeIf { _state.value.isActive }

    fun start(
        workout: Workout,
        capabilities: TreadmillCapabilities?,
        responsiveness: RegulationResponsiveness = RegulationResponsiveness.DEFAULT,
    ) {
        if (_state.value.isActive) {
            Timber.w("Une seance est deja en cours, demarrage ignore")
            return
        }
        sessionJob?.cancel()
        speedGuardJob?.cancel()
        pauseRequested.value = false
        skipRequested.value = false
        startedWorkoutId = workout.id
        sessionJob = scope.launch { runSession(workout, capabilities, responsiveness) }
    }

    fun pause() {
        pauseRequested.value = true
    }

    fun resume() {
        pauseRequested.value = false
    }

    fun skipStep() {
        if (_state.value is SessionState.Running) skipRequested.value = true
    }

    fun stop() {
        sessionJob?.cancel()
        sessionJob = null
        speedGuardJob?.cancel()
        speedGuardJob = null
        pauseRequested.value = false
        scope.launch { runCatching { client.stop() } }
        _state.value = SessionState.Idle
    }

    fun acknowledge() {
        if (!_state.value.isActive) _state.value = SessionState.Idle
    }

    private suspend fun runSession(
        workout: Workout,
        capabilities: TreadmillCapabilities?,
        responsiveness: RegulationResponsiveness,
    ) {
        val steps = WorkoutPlan.flatten(workout.elements)
        if (steps.isEmpty()) {
            _state.value = SessionState.Failed("Cet entrainement ne contient aucune etape.")
            return
        }

        try {
            countDown()

            val control = client.requestControl()
            if (control?.isSuccess != true) {
                _state.value = SessionState.Failed(
                    "Le tapis a refuse la prise de controle. Verifiez qu'aucune autre " +
                        "application n'y est connectee."
                )
                return
            }
            client.start()

            val sessionDistanceOrigin = currentTreadmillDistance()
            var completedStepsSeconds = 0

            steps.forEachIndexed { index, resolved ->
                applyStep(resolved.step, capabilities)
                completedStepsSeconds += runStep(
                    workout = workout,
                    steps = steps,
                    index = index,
                    completedStepsSeconds = completedStepsSeconds,
                    sessionDistanceOrigin = sessionDistanceOrigin,
                    capabilities = capabilities,
                    responsiveness = responsiveness,
                )
            }

            client.stop()
            _state.value = SessionState.Finished(
                workoutName = workout.name,
                totalElapsedSeconds = completedStepsSeconds,
                totalDistanceMeters = currentTreadmillDistance() - sessionDistanceOrigin,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Timber.e(error, "Seance interrompue")
            runCatching { client.stop() }
            _state.value = SessionState.Failed(error.message ?: "Erreur pendant la seance")
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
        capabilities: TreadmillCapabilities?,
        responsiveness: RegulationResponsiveness,
    ): Int {
        val resolved = steps[index]
        val stepDistanceOrigin = currentTreadmillDistance()
        val stepStartedAt = SystemClock.elapsedRealtime()
        skipRequested.value = false
        var pausedMillis = 0L
        var pauseStartedAt: Long? = null

        val regulator = startRegulation(resolved.step, capabilities, responsiveness)
        var regulation: RegulationOutcome? = null

        while (true) {
            delay(TICK_MILLIS)

            val now = SystemClock.elapsedRealtime()
            if (pauseRequested.value) {
                if (pauseStartedAt == null) {
                    pauseStartedAt = now
                    runCatching { client.pause() }
                }
            } else if (pauseStartedAt != null) {
                pausedMillis += now - pauseStartedAt
                pauseStartedAt = null
                runCatching { client.start() }
            }

            val pausedSoFar = pausedMillis + (pauseStartedAt?.let { now - it } ?: 0L)
            val elapsedSeconds = ((now - stepStartedAt - pausedSoFar) / 1_000).toInt()
            val coveredMeters = (currentTreadmillDistance() - stepDistanceOrigin).coerceAtLeast(0)

            if (regulator != null && !pauseRequested.value) {
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
                totalDistanceMeters =
                    (currentTreadmillDistance() - sessionDistanceOrigin).coerceAtLeast(0),
                liveData = client.treadmillData.value,
                regulation = regulation,
            )

            _state.value = if (pauseRequested.value) {
                SessionState.Paused(progress)
            } else {
                SessionState.Running(progress)
            }

            if (skipRequested.compareAndSet(expect = true, update = false)) {
                Timber.i("Etape %d sur %d passee a la demande", index + 1, steps.size)
                return elapsedSeconds
            }

            if (!pauseRequested.value &&
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

    private fun currentTreadmillDistance(): Int =
        client.treadmillData.value?.totalDistanceMeters ?: 0

    private companion object {
        const val COUNTDOWN_SECONDS = 5

        const val TICK_MILLIS = 250L

        const val COMMAND_SETTLE_MILLIS = 500L
        const val SPEED_RESEND_DELAY_MILLIS = 4_000L
        const val SPEED_RESEND_ATTEMPTS = 3
        const val SPEED_TOLERANCE_KMH = 0.05
    }
}
