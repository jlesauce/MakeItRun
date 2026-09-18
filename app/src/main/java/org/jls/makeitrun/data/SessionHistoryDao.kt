package org.jls.makeitrun.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionHistoryDao {

    @Query(
        """
        SELECT r.id AS id,
               r.workoutName AS workoutName,
               r.startedAt AS startedAt,
               r.endedAt AS endedAt,
               r.outcome AS outcome,
               r.elapsedSeconds AS elapsedSeconds,
               r.distanceMeters AS distanceMeters,
               r.stepsCompleted AS stepsCompleted,
               r.stepCount AS stepCount,
               AVG(s.heartRateBpm) AS averageBpm,
               MAX(s.heartRateBpm) AS maximumBpm,
               IFNULL(SUM(CASE WHEN s.zone = 'IN_ZONE' THEN 1 ELSE 0 END), 0) AS secondsInZone,
               IFNULL(SUM(CASE WHEN s.zone IS NOT NULL THEN 1 ELSE 0 END), 0) AS secondsWithZone
        FROM session_records r
        LEFT JOIN session_samples s ON s.sessionId = r.id
        WHERE r.outcome <> :excludedOutcome
        GROUP BY r.id
        ORDER BY r.startedAt DESC
        """
    )
    fun observeSummaries(excludedOutcome: String): Flow<List<SessionSummaryRow>>

    @Query("SELECT * FROM session_records WHERE id = :id")
    suspend fun findRecord(id: Long): SessionRecordEntity?

    @Query("SELECT * FROM session_records WHERE outcome = :outcome")
    suspend fun findRecordsWithOutcome(outcome: String): List<SessionRecordEntity>

    @Insert
    suspend fun insertRecord(record: SessionRecordEntity): Long

    @Update
    suspend fun updateRecord(record: SessionRecordEntity)

    @Query("DELETE FROM session_records WHERE id = :id")
    suspend fun deleteRecord(id: Long)

    @Insert
    suspend fun insertSamples(samples: List<SessionSampleEntity>)

    @Query("SELECT * FROM session_samples WHERE sessionId = :sessionId ORDER BY elapsedSeconds")
    suspend fun findSamples(sessionId: Long): List<SessionSampleEntity>

    @Query(
        "SELECT * FROM session_samples WHERE sessionId = :sessionId " +
            "ORDER BY elapsedSeconds DESC LIMIT 1"
    )
    suspend fun findLastSample(sessionId: Long): SessionSampleEntity?
}
