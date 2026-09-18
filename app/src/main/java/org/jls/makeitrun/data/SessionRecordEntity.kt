package org.jls.makeitrun.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "session_records")
data class SessionRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val workoutId: Long?,
    val workoutName: String,
    val planJson: String,
    val startedAt: Long,
    val endedAt: Long?,
    val outcome: String,
    val failureReason: String?,
    val elapsedSeconds: Int,
    val distanceMeters: Int,
    val stepsCompleted: Int,
    val stepCount: Int,
    val treadmillDistanceMeters: Int?,
    val treadmillElapsedSeconds: Int?,
    val energyKcal: Int?,
)

@Entity(
    tableName = "session_samples",
    foreignKeys = [
        ForeignKey(
            entity = SessionRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class SessionSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: Long,
    val elapsedSeconds: Int,
    val distanceMeters: Int,
    val stepIndex: Int,
    val speedKmh: Double?,
    val inclinationPercent: Double?,
    val heartRateBpm: Int?,
    val smoothedBpm: Int?,
    val targetSpeedKmh: Double?,
    val zone: String?,
)

data class SessionSummaryRow(
    val id: Long,
    val workoutName: String,
    val startedAt: Long,
    val endedAt: Long?,
    val outcome: String,
    val elapsedSeconds: Int,
    val distanceMeters: Int,
    val stepsCompleted: Int,
    val stepCount: Int,
    val averageBpm: Double?,
    val maximumBpm: Int?,
    val secondsInZone: Int,
    val secondsWithZone: Int,
)
