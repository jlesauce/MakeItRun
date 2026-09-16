package org.jls.makeitrun.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(entities = [WorkoutEntity::class], version = 1, exportSchema = true)
abstract class MakeItRunDatabase : RoomDatabase() {

    abstract fun workoutDao(): WorkoutDao

    companion object {
        const val NAME = "make-it-run.db"

        val MIGRATIONS: Array<Migration> = emptyArray()
    }
}
