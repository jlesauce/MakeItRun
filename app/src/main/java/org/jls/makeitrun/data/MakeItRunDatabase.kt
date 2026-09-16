package org.jls.makeitrun.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [WorkoutEntity::class], version = 1, exportSchema = false)
abstract class MakeItRunDatabase : RoomDatabase() {

    abstract fun workoutDao(): WorkoutDao

    companion object {
        const val NAME = "make-it-run.db"
    }
}
