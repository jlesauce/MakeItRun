package org.jls.makeitrun.session

import org.jls.makeitrun.ftms.TreadmillData
import org.jls.makeitrun.workout.model.ResolvedStep

data class SessionProgress(
    val workoutName: String,
    val currentStep: ResolvedStep,
    val nextStep: ResolvedStep?,
    val stepIndex: Int,
    val stepCount: Int,
    val stepElapsedSeconds: Int,
    val stepCoveredMeters: Int,
    val remaining: StepRemaining,
    val stepFraction: Float,
    val totalElapsedSeconds: Int,
    val totalDistanceMeters: Int,
    val liveData: TreadmillData?,
    val regulation: RegulationOutcome? = null,
)

sealed interface SessionState {

    data object Idle : SessionState

    data class CountingDown(val secondsRemaining: Int) : SessionState

    data class Running(val progress: SessionProgress) : SessionState

    data class Paused(val progress: SessionProgress) : SessionState

    data class Finished(
        val workoutName: String,
        val totalElapsedSeconds: Int,
        val totalDistanceMeters: Int,
    ) : SessionState

    data class Failed(val reason: String) : SessionState

    val isActive: Boolean
        get() = this is CountingDown || this is Running || this is Paused
}
