package org.jls.makeitrun.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val elementsJson: String,
    val createdAt: Long,
)
