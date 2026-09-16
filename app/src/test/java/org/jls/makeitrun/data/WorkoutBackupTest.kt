package org.jls.makeitrun.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import org.jls.makeitrun.di.DataModule
import org.jls.makeitrun.ftms.TreadmillCapabilities
import org.jls.makeitrun.workout.model.RepeatBlock
import org.jls.makeitrun.workout.model.StepDuration
import org.jls.makeitrun.workout.model.StepType
import org.jls.makeitrun.workout.model.Workout
import org.jls.makeitrun.workout.model.WorkoutElement
import org.jls.makeitrun.workout.model.WorkoutStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutBackupTest {

    private val json = DataModule.provideJson()

    @Test
    fun `workouts survive an export followed by an import on an empty device`() = runTest {
        val source = backupOf(treadmill = knownTreadmill(), workouts = listOf(intervals(), easyRun()))
        val target = backupOf(treadmill = null)

        val exported = source.exportAll()
        val result = target.importAll(exported.text)

        assertEquals(2, exported.workoutCount)
        assertEquals(2, result.workoutCount)
        assertEquals(
            listOf(easyRun(), intervals()).map { it.withoutId() },
            target.storedWorkouts(),
        )
    }

    @Test
    fun `the treadmill profile travels with the backup`() = runTest {
        val source = backupOf(treadmill = knownTreadmill(), workouts = listOf(intervals()))
        val profiles = InMemoryTreadmillProfiles()
        val target = backupOf(profiles = profiles)

        val result = target.importAll(source.exportAll().text)

        assertTrue(result.treadmillRestored)
        assertEquals("AA:BB:CC:DD:EE:FF", profiles.current().address)
        assertEquals("Domyos Run500", profiles.current().name)
        assertEquals(22.0, profiles.current().capabilities?.speedRange?.maximum!!, 0.001)
        assertFalse(profiles.current().showPaceInsteadOfSpeed)
    }

    @Test
    fun `a treadmill already known is never replaced without asking`() = runTest {
        val source = backupOf(treadmill = knownTreadmill(), workouts = listOf(intervals()))
        val target = backupOf(profiles = alreadyKnownProfiles())

        val pending = target.read(source.exportAll().text)

        assertEquals("Domyos Run500", pending.conflict?.backupLabel)
        assertEquals("Tapis de la salle", pending.conflict?.currentLabel)
    }

    @Test
    fun `keeping the current treadmill still imports the workouts`() = runTest {
        val source = backupOf(treadmill = knownTreadmill(), workouts = listOf(intervals()))
        val profiles = alreadyKnownProfiles()
        val target = backupOf(profiles = profiles)

        val pending = target.read(source.exportAll().text)
        val result = target.apply(pending, replaceTreadmill = false)

        assertFalse(result.treadmillRestored)
        assertEquals("11:22:33:44:55:66", profiles.current().address)
        assertEquals(1, result.workoutCount)
    }

    @Test
    fun `choosing the backup treadmill replaces the current one`() = runTest {
        val source = backupOf(treadmill = knownTreadmill(), workouts = listOf(intervals()))
        val profiles = alreadyKnownProfiles()
        val target = backupOf(profiles = profiles)

        val pending = target.read(source.exportAll().text)
        val result = target.apply(pending, replaceTreadmill = true)

        assertTrue(result.treadmillRestored)
        assertEquals("AA:BB:CC:DD:EE:FF", profiles.current().address)
        assertEquals("Domyos Run500", profiles.current().name)
    }

    @Test
    fun `a backup written without a treadmill imports fine`() = runTest {
        val source = backupOf(treadmill = null, workouts = listOf(intervals()))
        val target = backupOf(treadmill = null)

        val exported = source.exportAll()
        val result = target.importAll(exported.text)

        assertNull(json.decodeFromString<WorkoutBackup>(exported.text).treadmill)
        assertFalse(result.treadmillRestored)
        assertEquals(1, result.workoutCount)
    }

    @Test
    fun `importing keeps the workouts already present`() = runTest {
        val source = backupOf(workouts = listOf(intervals()))
        val target = backupOf(workouts = listOf(easyRun()))

        target.importAll(source.exportAll().text)

        assertEquals(
            listOf("Sortie tranquille", "Fractionne"),
            target.storedWorkouts().map { it.name },
        )
    }

    @Test
    fun `a backup declares the format it was written with`() = runTest {
        val exported = backupOf(workouts = listOf(intervals())).exportAll()

        assertEquals(
            WorkoutBackup.CURRENT_FORMAT,
            json.decodeFromString<WorkoutBackup>(exported.text).format,
        )
    }

    @Test
    fun `a file that is not a backup is rejected`() = runTest {
        val repository = backupOf()

        assertThrows(SerializationException::class.java) {
            runBlocking { repository.read("ceci n'est pas une sauvegarde") }
        }
    }

    private suspend fun BackupRepository.importAll(content: String): ImportResult =
        apply(read(content), replaceTreadmill = false)

    private fun alreadyKnownProfiles() = InMemoryTreadmillProfiles().apply {
        runBlocking {
            rememberTreadmill(
                address = "11:22:33:44:55:66",
                name = "Tapis de la salle",
                capabilities = TreadmillCapabilities(
                    speedRange = TreadmillCapabilities.ValueRange(1.0, 18.0, 0.1),
                    inclinationRange = null,
                    canSetTargetSpeed = true,
                    canSetTargetInclination = false,
                    canStartAndStop = true,
                ),
            )
        }
    }

    private fun backupOf(
        workouts: List<Workout> = emptyList(),
        treadmill: BackupTreadmill? = null,
        profiles: InMemoryTreadmillProfiles = InMemoryTreadmillProfiles(),
    ): BackupRepository {
        val workoutRepository = WorkoutRepository(InMemoryWorkoutDao(), json)
        runBlocking {
            workouts.forEach { workoutRepository.save(it) }
            treadmill?.let {
                profiles.rememberTreadmill(it.address, it.name, it.capabilities?.toCapabilities())
                profiles.setShowPaceInsteadOfSpeed(it.showPaceInsteadOfSpeed)
            }
        }
        return BackupRepository(workoutRepository, profiles, json)
    }

    private suspend fun BackupRepository.storedWorkouts(): List<Workout> =
        json.decodeFromString<WorkoutBackup>(exportAll().text).workouts.map {
            Workout(id = 0L, name = it.name, elements = it.elements, createdAt = it.createdAt)
        }

    private fun knownTreadmill() = BackupTreadmill(
        address = "AA:BB:CC:DD:EE:FF",
        name = "Domyos Run500",
        showPaceInsteadOfSpeed = false,
        capabilities = BackupCapabilities.from(
            TreadmillCapabilities(
                speedRange = TreadmillCapabilities.ValueRange(1.0, 22.0, 0.1),
                inclinationRange = TreadmillCapabilities.ValueRange(0.0, 12.5, 0.5),
                canSetTargetSpeed = true,
                canSetTargetInclination = true,
                canStartAndStop = true,
            )
        ),
    )

    private fun Workout.withoutId() = copy(id = 0L)

    private fun intervals() = Workout(
        id = 0L,
        name = "Fractionne",
        createdAt = 1_700_000_000_000L,
        elements = listOf<WorkoutElement>(
            WorkoutStep(
                id = "warmup",
                type = StepType.WARM_UP,
                duration = StepDuration.Time(600),
                targetSpeedKmh = 8.0,
            ),
            RepeatBlock(
                id = "block",
                repetitions = 8,
                steps = listOf(
                    WorkoutStep(
                        id = "fast",
                        type = StepType.RUN,
                        duration = StepDuration.Distance(400),
                        targetSpeedKmh = 15.0,
                        inclinationPercent = 1.5,
                    ),
                    WorkoutStep(
                        id = "easy",
                        type = StepType.RECOVER,
                        duration = StepDuration.Time(90),
                    ),
                ),
            ),
        ),
    )

    private fun easyRun() = Workout(
        id = 0L,
        name = "Sortie tranquille",
        createdAt = 1_700_000_100_000L,
        elements = listOf<WorkoutElement>(
            WorkoutStep(
                id = "run",
                type = StepType.RUN,
                duration = StepDuration.Time(1_800),
                targetSpeedKmh = 10.0,
            ),
        ),
    )
}

private class InMemoryTreadmillProfiles : TreadmillProfileStore {

    override val profile = MutableStateFlow(
        TreadmillProfile(
            address = null,
            name = null,
            capabilities = null,
            showPaceInsteadOfSpeed = true,
        )
    )

    override suspend fun current(): TreadmillProfile = profile.value

    override suspend fun rememberTreadmill(
        address: String,
        name: String?,
        capabilities: TreadmillCapabilities?,
    ) {
        profile.value = profile.value.copy(
            address = address,
            name = name ?: profile.value.name,
            capabilities = capabilities ?: profile.value.capabilities,
        )
    }

    override suspend fun setShowPaceInsteadOfSpeed(showPace: Boolean) {
        profile.value = profile.value.copy(showPaceInsteadOfSpeed = showPace)
    }
}

private class InMemoryWorkoutDao : WorkoutDao {

    private val rows = MutableStateFlow<List<WorkoutEntity>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<WorkoutEntity>> = rows

    override suspend fun findAll(): List<WorkoutEntity> = rows.value.sortedByDescending { it.createdAt }

    override suspend fun findById(id: Long): WorkoutEntity? = rows.value.firstOrNull { it.id == id }

    override suspend fun insert(workout: WorkoutEntity): Long {
        val id = nextId++
        rows.value += workout.copy(id = id)
        return id
    }

    override suspend fun insertAll(workouts: List<WorkoutEntity>) {
        workouts.forEach { insert(it) }
    }

    override suspend fun update(workout: WorkoutEntity) {
        rows.value = rows.value.map { if (it.id == workout.id) workout else it }
    }

    override suspend fun deleteById(id: Long) {
        rows.value = rows.value.filterNot { it.id == id }
    }
}
