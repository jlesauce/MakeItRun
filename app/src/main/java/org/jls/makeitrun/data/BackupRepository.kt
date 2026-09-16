package org.jls.makeitrun.data

import kotlinx.serialization.json.Json
import org.jls.makeitrun.workout.model.Workout
import javax.inject.Inject
import javax.inject.Singleton

data class BackupContent(val text: String, val workoutCount: Int)

data class ImportResult(val workoutCount: Int, val treadmillRestored: Boolean)

data class TreadmillConflict(val backupLabel: String, val currentLabel: String)

class PendingImport internal constructor(
    internal val backup: WorkoutBackup,
    val conflict: TreadmillConflict?,
)

@Singleton
class BackupRepository @Inject constructor(
    private val workouts: WorkoutRepository,
    private val treadmill: TreadmillProfileStore,
    private val json: Json,
) {

    private val readableJson = Json(from = json) { prettyPrint = true }

    suspend fun exportAll(): BackupContent {
        val stored = workouts.findAll()
        val backup = WorkoutBackup(
            exportedAt = System.currentTimeMillis(),
            treadmill = treadmill.current().toBackup(),
            workouts = stored.map {
                BackupWorkout(name = it.name, createdAt = it.createdAt, elements = it.elements)
            },
        )
        return BackupContent(
            text = readableJson.encodeToString(backup),
            workoutCount = stored.size,
        )
    }

    suspend fun read(content: String): PendingImport {
        val backup = json.decodeFromString<WorkoutBackup>(content)
        val current = treadmill.current()
        val conflict = backup.treadmill
            ?.takeUnless { current.isEmpty }
            ?.let { TreadmillConflict(backupLabel = it.label(), currentLabel = current.label()) }
        return PendingImport(backup, conflict)
    }

    suspend fun apply(pending: PendingImport, replaceTreadmill: Boolean): ImportResult {
        val backup = pending.backup

        workouts.saveAll(
            backup.workouts.map {
                Workout(id = 0L, name = it.name, elements = it.elements, createdAt = it.createdAt)
            }
        )

        val stored = backup.treadmill?.takeIf { pending.conflict == null || replaceTreadmill }
        if (stored != null) {
            treadmill.rememberTreadmill(
                address = stored.address,
                name = stored.name,
                capabilities = stored.capabilities?.toCapabilities(),
            )
            treadmill.setShowPaceInsteadOfSpeed(stored.showPaceInsteadOfSpeed)
        }

        return ImportResult(
            workoutCount = backup.workouts.size,
            treadmillRestored = stored != null,
        )
    }

    private fun TreadmillProfile.toBackup(): BackupTreadmill? {
        val knownAddress = address ?: return null
        return BackupTreadmill(
            address = knownAddress,
            name = name,
            showPaceInsteadOfSpeed = showPaceInsteadOfSpeed,
            capabilities = capabilities?.let(BackupCapabilities::from),
        )
    }

    private fun TreadmillProfile.label(): String = name ?: address.orEmpty()

    private fun BackupTreadmill.label(): String = name ?: address
}
