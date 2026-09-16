package org.jls.makeitrun.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import org.jls.makeitrun.workout.model.Workout
import org.jls.makeitrun.workout.model.WorkoutElement
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkoutRepository @Inject constructor(
    private val dao: WorkoutDao,
    private val json: Json,
) {

    fun observeAll(): Flow<List<Workout>> =
        dao.observeAll().map { entities -> entities.map { it.toWorkout() } }

    suspend fun find(id: Long): Workout? = dao.findById(id)?.toWorkout()

    suspend fun save(workout: Workout): Long {
        val entity = workout.toEntity()
        return if (workout.id == 0L) {
            dao.insert(entity)
        } else {
            dao.update(entity)
            workout.id
        }
    }

    suspend fun delete(id: Long) = dao.deleteById(id)

    private fun Workout.toEntity() = WorkoutEntity(
        id = id,
        name = name,
        elementsJson = json.encodeToString<List<WorkoutElement>>(elements),
        createdAt = createdAt,
    )

    private fun WorkoutEntity.toWorkout() = Workout(
        id = id,
        name = name,
        elements = runCatching {
            json.decodeFromString<List<WorkoutElement>>(elementsJson)
        }.onFailure {
            Timber.e(it, "Entrainement %d illisible, il sera affiche vide", id)
        }.getOrDefault(emptyList()),
        createdAt = createdAt,
    )
}
