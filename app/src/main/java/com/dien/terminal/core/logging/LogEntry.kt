package com.dien.terminal.core.logging

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One row per operation (install/update/upgrade/fix/backup/restore/setup) - FR-12. */
@Entity(tableName = "log_entries")
data class LogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val processId: String,
    val type: String,
    val title: String,
    val startTime: Long,
    var endTime: Long? = null,
    var status: String,
    var summary: String = "",
    var packagesFixed: Int = 0,
    var packagesFailed: Int = 0,
    val logFilePath: String
)
