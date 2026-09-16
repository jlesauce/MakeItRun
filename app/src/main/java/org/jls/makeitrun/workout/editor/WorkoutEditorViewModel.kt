package org.jls.makeitrun.workout.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jls.makeitrun.data.TreadmillProfileRepository
import org.jls.makeitrun.data.WorkoutRepository
import org.jls.makeitrun.ftms.TreadmillCapabilities
import org.jls.makeitrun.workout.model.RepeatBlock
import org.jls.makeitrun.workout.model.StepDuration
import org.jls.makeitrun.workout.model.StepType
import org.jls.makeitrun.workout.model.Workout
import org.jls.makeitrun.workout.model.WorkoutElement
import org.jls.makeitrun.workout.model.WorkoutStep
import java.util.UUID
import javax.inject.Inject

data class StepDraft(
    val id: String,
    val parentBlockId: String?,
    val isNew: Boolean,
    val type: StepType,
    val byDistance: Boolean,
    val minutes: String,
    val seconds: String,
    val meters: String,
    val hasTarget: Boolean,
    val targetSpeedKmh: Double,
    val inclinationPercent: Double?,
) {

    fun toStep(): WorkoutStep = WorkoutStep(
        id = id,
        type = type,
        duration = if (byDistance) {
            StepDuration.Distance(meters.toIntOrNull()?.coerceAtLeast(1) ?: DEFAULT_METERS)
        } else {
            val total = (minutes.toIntOrNull() ?: 0) * 60 + (seconds.toIntOrNull() ?: 0)
            StepDuration.Time(total.coerceAtLeast(1))
        },
        targetSpeedKmh = targetSpeedKmh.takeIf { hasTarget },
        inclinationPercent = inclinationPercent,
    )

    companion object {
        const val DEFAULT_METERS = 400

        fun from(step: WorkoutStep, parentBlockId: String?): StepDraft {
            val time = step.duration as? StepDuration.Time
            val distance = step.duration as? StepDuration.Distance
            return StepDraft(
                id = step.id,
                parentBlockId = parentBlockId,
                isNew = false,
                type = step.type,
                byDistance = distance != null,
                minutes = ((time?.seconds ?: 0) / 60).toString(),
                seconds = ((time?.seconds ?: 0) % 60).toString(),
                meters = (distance?.meters ?: DEFAULT_METERS).toString(),
                hasTarget = step.targetSpeedKmh != null,
                targetSpeedKmh = step.targetSpeedKmh ?: DEFAULT_SPEED_KMH,
                inclinationPercent = step.inclinationPercent,
            )
        }

        fun blank(parentBlockId: String?, type: StepType) = StepDraft(
            id = UUID.randomUUID().toString(),
            parentBlockId = parentBlockId,
            isNew = true,
            type = type,
            byDistance = false,
            minutes = "5",
            seconds = "0",
            meters = DEFAULT_METERS.toString(),
            hasTarget = true,
            targetSpeedKmh = DEFAULT_SPEED_KMH,
            inclinationPercent = null,
        )

        private const val DEFAULT_SPEED_KMH = 9.0
    }
}

data class EditorUiState(
    val workoutId: Long = 0L,
    val name: String = "",
    val elements: List<WorkoutElement> = emptyList(),
    val capabilities: TreadmillCapabilities = TreadmillCapabilities.UNKNOWN,
    val showPaceInsteadOfSpeed: Boolean = true,
    val draft: StepDraft? = null,
) {
    val isNew: Boolean get() = workoutId == 0L
    val canSave: Boolean get() = name.isNotBlank() && elements.isNotEmpty()
}

@HiltViewModel
class WorkoutEditorViewModel @Inject constructor(
    private val repository: WorkoutRepository,
    private val profileRepository: TreadmillProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            profileRepository.profile.collect { profile ->
                _uiState.update {
                    it.copy(
                        capabilities = profile.capabilities ?: TreadmillCapabilities.UNKNOWN,
                        showPaceInsteadOfSpeed = profile.showPaceInsteadOfSpeed,
                    )
                }
            }
        }
    }

    fun load(workoutId: Long) {
        if (workoutId == 0L) {
            _uiState.update { it.copy(workoutId = 0L) }
            return
        }
        viewModelScope.launch {
            val workout = repository.find(workoutId) ?: return@launch
            _uiState.update {
                it.copy(
                    workoutId = workout.id,
                    name = workout.name,
                    elements = workout.elements,
                )
            }
        }
    }

    fun setName(name: String) = _uiState.update { it.copy(name = name) }

    fun toggleUnit() {
        viewModelScope.launch {
            profileRepository.setShowPaceInsteadOfSpeed(!_uiState.value.showPaceInsteadOfSpeed)
        }
    }

    fun addStep(parentBlockId: String? = null) {
        val type = if (_uiState.value.elements.isEmpty()) StepType.WARM_UP else StepType.RUN
        _uiState.update { it.copy(draft = StepDraft.blank(parentBlockId, type)) }
    }

    fun editStep(step: WorkoutStep, parentBlockId: String?) {
        _uiState.update { it.copy(draft = StepDraft.from(step, parentBlockId)) }
    }

    fun updateDraft(draft: StepDraft) = _uiState.update { it.copy(draft = draft) }

    fun cancelDraft() = _uiState.update { it.copy(draft = null) }

    fun commitDraft() {
        val draft = _uiState.value.draft ?: return
        val step = draft.toStep()

        _uiState.update { state ->
            val elements = if (draft.parentBlockId == null) {
                state.elements.upsertTopLevel(step, draft.isNew)
            } else {
                state.elements.mapBlock(draft.parentBlockId) { block ->
                    block.copy(steps = block.steps.upsertStep(step, draft.isNew))
                }
            }
            state.copy(elements = elements, draft = null)
        }
    }

    fun addRepeatBlock() {
        val block = RepeatBlock(
            id = UUID.randomUUID().toString(),
            repetitions = DEFAULT_REPETITIONS,
            steps = emptyList(),
        )
        _uiState.update { it.copy(elements = it.elements + block) }
    }

    fun setRepetitions(blockId: String, repetitions: Int) {
        _uiState.update { state ->
            state.copy(
                elements = state.elements.mapBlock(blockId) {
                    it.copy(repetitions = repetitions.coerceIn(1, MAX_REPETITIONS))
                }
            )
        }
    }

    fun remove(elementId: String, parentBlockId: String? = null) {
        _uiState.update { state ->
            val elements = if (parentBlockId == null) {
                state.elements.filterNot { it.id == elementId }
            } else {
                state.elements.mapBlock(parentBlockId) { block ->
                    block.copy(steps = block.steps.filterNot { it.id == elementId })
                }
            }
            state.copy(elements = elements)
        }
    }

    fun move(elementId: String, offset: Int, parentBlockId: String? = null) {
        _uiState.update { state ->
            val elements = if (parentBlockId == null) {
                state.elements.moved(elementId, offset)
            } else {
                state.elements.mapBlock(parentBlockId) { block ->
                    block.copy(steps = block.steps.moved(elementId, offset))
                }
            }
            state.copy(elements = elements)
        }
    }

    fun save(onSaved: () -> Unit) {
        val state = _uiState.value
        if (!state.canSave) return
        viewModelScope.launch {
            repository.save(
                Workout(
                    id = state.workoutId,
                    name = state.name.trim(),
                    elements = state.elements,
                )
            )
            onSaved()
        }
    }

    private fun List<WorkoutElement>.upsertTopLevel(step: WorkoutStep, isNew: Boolean) =
        if (isNew) this + step else map { if (it.id == step.id) step else it }

    private fun List<WorkoutStep>.upsertStep(step: WorkoutStep, isNew: Boolean) =
        if (isNew) this + step else map { if (it.id == step.id) step else it }

    private fun List<WorkoutElement>.mapBlock(
        blockId: String,
        transform: (RepeatBlock) -> RepeatBlock,
    ) = map { element ->
        if (element is RepeatBlock && element.id == blockId) transform(element) else element
    }

    private fun <T : WorkoutElement> List<T>.moved(elementId: String, offset: Int): List<T> {
        val index = indexOfFirst { it.id == elementId }
        val target = index + offset
        if (index < 0 || target !in indices) return this
        return toMutableList().apply { add(target, removeAt(index)) }
    }

    private companion object {
        const val DEFAULT_REPETITIONS = 4
        const val MAX_REPETITIONS = 30
    }
}
