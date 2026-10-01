package com.dien.terminal.core.logging

import android.content.Context
import com.dien.terminal.util.Prefs
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Tracks one running operation: writes its raw output to a dedicated log
 * file (non-blocking relative to the UI thread - callers already run this
 * from a background coroutine) and keeps a summary row in Room (FR-12).
 * Also enforces the retention policy (NFR: log rotation) on each new run.
 */
class OperationLogger(private val context: Context) {

    private val dao = AppDatabase.get(context).logEntryDao()
    private val logsDir = File(context.filesDir, "logs").apply { mkdirs() }

    inner class Session(
        private val entryId: Long,
        private val processId: String,
        private val writer: BufferedWriter,
        private val logFile: File
    ) {
        val id: String get() = processId

        fun appendLine(line: String) {
            writer.write(line)
            writer.newLine()
            writer.flush()
        }

        suspend fun finish(status: OperationStatus, summary: String, fixed: Int = 0, failed: Int = 0) {
            runCatching { writer.close() }
            val entry = dao.getById(entryId) ?: return
            entry.endTime = System.currentTimeMillis()
            entry.status = status.name
            entry.summary = summary
            entry.packagesFixed = fixed
            entry.packagesFailed = failed
            dao.update(entry)
        }
    }

    suspend fun start(type: OperationType, title: String): Session {
        pruneOldLogs()
        val processId = UUID.randomUUID().toString()
        val logFile = File(logsDir, "$processId.log")
        val entry = LogEntry(
            processId = processId,
            type = type.name,
            title = title,
            startTime = System.currentTimeMillis(),
            status = OperationStatus.RUNNING.name,
            logFilePath = logFile.absolutePath
        )
        val id = dao.insert(entry)
        return Session(id, processId, BufferedWriter(FileWriter(logFile, true)), logFile)
    }

    private suspend fun pruneOldLogs() {
        val retentionDays = Prefs.get(context).logRetentionDays
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays.toLong())
        val old = dao.findOlderThan(cutoff)
        for (e in old) {
            runCatching { File(e.logFilePath).delete() }
            dao.delete(e.id)
        }
    }

    suspend fun clearAll() {
        for (e in dao.getAll()) {
            runCatching { File(e.logFilePath).delete() }
        }
        dao.clearAll()
    }
}
