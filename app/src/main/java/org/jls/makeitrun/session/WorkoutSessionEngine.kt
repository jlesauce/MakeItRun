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
import org.jls.makeitrun.workout.model.ResolvedStep
import org.jls.makeitrun.workout.model.Workout
import org.jls.makeitrun.workout.model.WorkoutPlan
import org.jls.makeitrun.workout.model.WorkoutStep
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@SuppressLint("MissingPermission")
@Singleton
class WorkoutSessionEngine @Inject constructor(
    private val client: FtmsTreadmillClient,
    @param:ApplicationScope private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
    val state = _state.asStateFlow()

    private var sessionJob: Job? = null
    private val pauseRequested = MutableStateFlow(false)

    fun start(workout: Workout, capabilities: TreadmillCapabilities?) {
        if (_state.value.isActive) {
            Timber.w("Une seance est deja en cours, demarrage ignore")
            return
        }
        sessionJob?.cancel()
        pauseRequested.value = false
        sessionJob = scope.launch { runSession(workout, capabilities) }
    }

    fun pause() {
        pauseRequested.value = true
    }

    fun resume() {
        pauseRequested.value = false
    }

    fun stop() {
        sessionJob?.cancel()
        sessionJob = null
        pauseRequested.value = false
        scope.launch { runCatching { client.stop() } }
        _state.value = SessionState.Idle
    }

    fun acknowledge() {
        if (!_state.value.isActive) _state.value = SessionState.Idle
    }

    private suspend fun runSession(workout: Workout, capabilities: TreadmillCapabilities?) {
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

            val sessionStartedAt = SystemClock.elapsedRealtime()
            val sessionDistanceOrigin = currentTreadmillDistance()
            var completedStepsSeconds = 0

            steps.forEachIndexed { index, resolved ->
                applyStep(resolved.step, capabilities)
                completedStepsSeconds += runStep(
                    workout = workout,
                    steps = steps,
                    index = index,
                    completedStepsSeconds = completedStepsSeconds,
                    sessionStartedAt = sessionStartedAt,
                    sessionDistanceOrigin = sessionDistanceOrigin,
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
        sessionStartedAt: Long,
        sessionDistanceOrigin: Int,
    ): Int {
        val resolved = steps[index]
        val stepDistanceOrigin = currentTreadmillDistance()
        val stepStartedAt = SystemClock.elapsedRealtime()
        var pausedMillis = 0L
        var pauseStartedAt: Long? = null

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
            )

            _state.value = if (pauseRequested.value) {
                SessionState.Paused(progress)
            } else {
                SessionState.Running(progress)
            }

            if (!pauseRequested.value &&
                StepProgress.isComplete(resolved.step, elapsedSeconds, coveredMeters)
            ) {
                return elapsedSeconds
            }
        }
    }

    private suspend fun applyStep(step: WorkoutStep, capabilities: TreadmillCapabilities?) {
        step.targetSpeedKmh?.let { requested ->
            val speed = capabilities?.speedRange?.coerce(requested) ?: requested
            Timber.i("Etape %s : consigne %.1f km/h", step.type, speed)
            client.setTargetSpeed(speed)
        }

        step.inclinationPercent?.let { requested ->
            if (capabilities?.canSetTargetInclination == false) return@let
            val inclination = capabilities?.inclinationRange?.coerce(requested) ?: requested
            client.setTargetInclination(inclination)
        }
    }

    private fun currentTreadmillDistance(): Int =
        client.treadmillData.value?.totalDistanceMeters ?: 0

    private companion object {
        const val COUNTDOWN_SECONDS = 5

        const val TICK_MILLIS = 250L
    }
}
