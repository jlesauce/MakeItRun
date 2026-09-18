package org.jls.makeitrun.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        WorkoutEntity::class,
        SessionRecordEntity::class,
        SessionSampleEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class MakeItRunDatabase : RoomDatabase() {

    abstract fun workoutDao(): WorkoutDao

    abstract fun sessionHistoryDao(): SessionHistoryDao

    companion object {
        const val NAME = "make-it-run.db"

        private val ADD_SESSION_HISTORY = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `session_records` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`workoutId` INTEGER, " +
                        "`workoutName` TEXT NOT NULL, " +
                        "`planJson` TEXT NOT NULL, " +
                        "`startedAt` INTEGER NOT NULL, " +
                        "`endedAt` INTEGER, " +
                        "`outcome` TEXT NOT NULL, " +
                        "`failureReason` TEXT, " +
                        "`elapsedSeconds` INTEGER NOT NULL, " +
                        "`distanceMeters` INTEGER NOT NULL, " +
                        "`stepsCompleted` INTEGER NOT NULL, " +
                        "`stepCount` INTEGER NOT NULL, " +
                        "`treadmillDistanceMeters` INTEGER, " +
                        "`treadmillElapsedSeconds` INTEGER, " +
                        "`energyKcal` INTEGER)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `session_samples` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`sessionId` INTEGER NOT NULL, " +
                        "`elapsedSeconds` INTEGER NOT NULL, " +
                        "`distanceMeters` INTEGER NOT NULL, " +
                        "`stepIndex` INTEGER NOT NULL, " +
                        "`speedKmh` REAL, " +
                        "`inclinationPercent` REAL, " +
                        "`heartRateBpm` INTEGER, " +
                        "`smoothedBpm` INTEGER, " +
                        "`targetSpeedKmh` REAL, " +
                        "`zone` TEXT, " +
                        "FOREIGN KEY(`sessionId`) REFERENCES `session_records`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_session_samples_sessionId` " +
                        "ON `session_samples` (`sessionId`)"
                )
            }
        }

        val MIGRATIONS: Array<Migration> = arrayOf(ADD_SESSION_HISTORY)
    }
}
