package com.dien.terminal.core.backup

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dien.terminal.R
import com.dien.terminal.core.logging.OperationLogger
import com.dien.terminal.core.logging.OperationStatus
import com.dien.terminal.core.logging.OperationType
import com.dien.terminal.core.rootfs.ProotLauncher
import com.dien.terminal.core.rootfs.RootfsManager
import com.dien.terminal.ui.logs.LogDetailActivity
import com.dien.terminal.util.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Runs Backup (FR-7/8) or Restore (FR-9/10/11) on a background thread while
 * keeping a foreground notification alive so the OS doesn't kill the work
 * if the user leaves the app (NFR: long operations must not block the UI).
 */
class BackupRestoreService : Service() {

    companion object {
        const val ACTION_BACKUP = "com.dien.terminal.action.BACKUP"
        const val ACTION_RESTORE = "com.dien.terminal.action.RESTORE"
        const val EXTRA_URI = "uri"
    }

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notifications.ensureChannels(this)
        val uri: Uri? = intent?.getParcelableExtra(EXTRA_URI)
        when (intent?.action) {
            ACTION_BACKUP -> if (uri != null) runBackup(uri) else stopSelf()
            ACTION_RESTORE -> if (uri != null) runRestore(uri) else stopSelf()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun runBackup(destTree: Uri) = scope.launch {
        val logger = OperationLogger(this@BackupRestoreService)
        val session = logger.start(OperationType.BACKUP, "Backup rootfs")
        startForeground(Notifications.ID_PROGRESS, progressNotif("Membuat backup...", 0))
        try {
            val manager = BackupManager(this@BackupRestoreService, RootfsManager(this@BackupRestoreService))
            val result = manager.backup(destTree) { pct, text ->
                session.appendLine("[$pct%] $text")
                updateProgress(text, pct)
            }
            session.finish(OperationStatus.SUCCESS, "Backup ${result.fileName} (${result.sizeBytes} bytes)")
            notifyResult("Backup selesai", "${result.fileName} berhasil disimpan", session.id)
        } catch (e: Exception) {
            session.finish(OperationStatus.FAILED, "Error: ${e.message}")
            notifyResult("Backup gagal", e.message ?: "Terjadi kesalahan", session.id)
        } finally {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun runRestore(backupFile: Uri) = scope.launch {
        val logger = OperationLogger(this@BackupRestoreService)
        val session = logger.start(OperationType.RESTORE, "Restore rootfs")
        startForeground(Notifications.ID_PROGRESS, progressNotif("Memulihkan backup...", 0))
        try {
            val rootfsManager = RootfsManager(this@BackupRestoreService)
            val launcher = ProotLauncher(this@BackupRestoreService, rootfsManager)
            val manager = RestoreManager(this@BackupRestoreService, rootfsManager, launcher)
            val outcome = manager.restore(backupFile) { pct, text ->
                session.appendLine("[$pct%] $text")
                updateProgress(text, pct)
            }
            session.appendLine(outcome.fixOutput)
            val checksumNote = when {
                !outcome.checksum.verified -> "checksum tidak tersedia"
                outcome.checksum.matched -> "checksum valid"
                else -> "PERINGATAN: checksum tidak cocok"
            }
            session.finish(
                OperationStatus.SUCCESS,
                "Restore selesai ($checksumNote). Diperbaiki: ${outcome.fixedCount}, Gagal: ${outcome.failedCount}",
                outcome.fixedCount, outcome.failedCount
            )
            notifyResult(
                "Restore selesai",
                "Diperbaiki: ${outcome.fixedCount}, Gagal: ${outcome.failedCount} ($checksumNote)",
                session.id
            )
        } catch (e: Exception) {
            session.finish(OperationStatus.FAILED, "Error: ${e.message}")
            notifyResult("Restore gagal", e.message ?: "Terjadi kesalahan", session.id)
        } finally {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun progressNotif(text: String, pct: Int): android.app.Notification =
        Notifications.progressBuilder(this, getString(R.string.app_name), text)
            .setProgress(100, pct, pct <= 0).build()

    private fun updateProgress(text: String, pct: Int) {
        val nm = NotificationManagerCompat.from(this)
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED || android.os.Build.VERSION.SDK_INT < 33
        ) {
            nm.notify(Notifications.ID_PROGRESS, progressNotif(text, pct))
        }
    }

    private fun notifyResult(title: String, text: String, processId: String) {
        val nm = NotificationManagerCompat.from(this)
        val openLogIntent = Intent(this, LogDetailActivity::class.java).apply {
            putExtra(LogDetailActivity.EXTRA_PROCESS_ID, processId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pending = android.app.PendingIntent.getActivity(
            this, 0, openLogIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, Notifications.CHANNEL_RESULTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .addAction(0, getString(R.string.action_open_log), pending)
            .build()
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED || android.os.Build.VERSION.SDK_INT < 33
        ) {
            nm.notify(Notifications.ID_RESULT, notif)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}
