package com.dien.terminal.core.logging

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LogEntryDao {
    @Insert
    suspend fun insert(entry: LogEntry): Long

    @Update
    suspend fun update(entry: LogEntry)

    @Query("SELECT * FROM log_entries ORDER BY startTime DESC")
    fun observeAll(): Flow<List<LogEntry>>

    @Query("SELECT * FROM log_entries ORDER BY startTime DESC")
    suspend fun getAll(): List<LogEntry>

    @Query("SELECT * FROM log_entries WHERE id = :id")
    suspend fun getById(id: Long): LogEntry?

    @Query("SELECT * FROM log_entries WHERE processId = :processId")
    suspend fun getByProcessId(processId: String): LogEntry?

    @Query("DELETE FROM log_entries")
    suspend fun clearAll()

    @Query("SELECT * FROM log_entries WHERE startTime < :olderThan")
    suspend fun findOlderThan(olderThan: Long): List<LogEntry>

    @Query("DELETE FROM log_entries WHERE id = :id")
    suspend fun delete(id: Long)
}
