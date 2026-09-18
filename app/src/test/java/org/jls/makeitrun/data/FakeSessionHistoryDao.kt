package org.jls.makeitrun.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeSessionHistoryDao(
    records: List<SessionRecordEntity> = emptyList(),
    samples: List<SessionSampleEntity> = emptyList(),
) : SessionHistoryDao {

    private val records = records.associateBy { it.id }.toMutableMap()
    private val samples = samples.toMutableList()

    private var nextRecordId = (this.records.keys.maxOrNull() ?: 0L) + 1

    val storedRecords: List<SessionRecordEntity>
        get() = records.values.toList()

    val storedSamples: List<SessionSampleEntity>
        get() = samples.toList()

    var insertSampleCalls = 0
        private set

    override fun observeSummaries(excludedOutcome: String): Flow<List<SessionSummaryRow>> =
        flowOf(emptyList())

    override suspend fun findRecord(id: Long): SessionRecordEntity? = records[id]

    override suspend fun findRecordsWithOutcome(outcome: String): List<SessionRecordEntity> =
        records.values.filter { it.outcome == outcome }

    override suspend fun insertRecord(record: SessionRecordEntity): Long {
        val id = nextRecordId++
        records[id] = record.copy(id = id)
        return id
    }

    override suspend fun updateRecord(record: SessionRecordEntity) {
        records[record.id] = record
    }

    override suspend fun deleteRecord(id: Long) {
        records.remove(id)
        samples.removeAll { it.sessionId == id }
    }

    override suspend fun insertSamples(samples: List<SessionSampleEntity>) {
        insertSampleCalls++
        this.samples += samples
    }

    override suspend fun findSamples(sessionId: Long): List<SessionSampleEntity> =
        samples.filter { it.sessionId == sessionId }.sortedBy { it.elapsedSeconds }

    override suspend fun findLastSample(sessionId: Long): SessionSampleEntity? =
        findSamples(sessionId).lastOrNull()
}
