package com.dien.terminal.core.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.dien.terminal.core.pm.PackageManagerOps
import com.dien.terminal.core.rootfs.ProotLauncher
import com.dien.terminal.core.rootfs.RootfsManager
import java.io.File
import java.security.MessageDigest

/**
 * Restores an Alpine rootfs from a backup produced by [BackupManager],
 * including cross-CPU-architecture restores (FR-9): the extracted rootfs is
 * architecture independent (it's just files), only the `proot` launcher
 * binary and interpreter differ, so after extraction we always run an
 * automatic `apk fix` (FR-10) to repair anything arch-specific (compiled
 * native packages) and summarize the outcome (FR-11).
 */
class RestoreManager(
    private val context: Context,
    private val rootfsManager: RootfsManager,
    private val launcher: ProotLauncher
) {
    data class ChecksumStatus(val verified: Boolean, val matched: Boolean)

    data class RestoreOutcome(
        val checksum: ChecksumStatus,
        val fixedCount: Int,
        val failedCount: Int,
        val fixOutput: String
    )

    fun restore(backupUri: Uri, onProgress: (Int, String) -> Unit): RestoreOutcome {
        onProgress(0, "Menyalin file backup...")
        val tmp = File(context.cacheDir, "restore-incoming.tar.gz")
        context.contentResolver.openInputStream(backupUri)?.use { input ->
            tmp.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("Tidak bisa membaca file backup yang dipilih")

        onProgress(10, "Memverifikasi checksum...")
        val checksum = verifyChecksum(backupUri, tmp)

        rootfsManager.restoreFrom(tmp) { p ->
            onProgress(10 + (p.percent * 70 / 100), p.detail)
        }
        tmp.delete()

        onProgress(82, "Menjalankan apk fix otomatis...")
        val pm = PackageManagerOps(context, launcher)
        val fixResult = kotlinx.coroutines.runBlocking { pm.fix() }

        onProgress(100, "Restore selesai")
        return RestoreOutcome(checksum, fixResult.fixed, fixResult.failed, fixResult.output)
    }

    private fun verifyChecksum(backupUri: Uri, downloadedFile: File): ChecksumStatus {
        val sidecarText = findSidecarSha256(backupUri) ?: return ChecksumStatus(verified = false, matched = false)
        val expected = sidecarText.trim().split(Regex("\\s+")).firstOrNull() ?: return ChecksumStatus(false, false)
        val digest = MessageDigest.getInstance("SHA-256")
        downloadedFile.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        return ChecksumStatus(verified = true, matched = actual.equals(expected, ignoreCase = true))
    }

    private fun findSidecarSha256(backupUri: Uri): String? {
        return try {
            val doc = DocumentFile.fromSingleUri(context, backupUri) ?: return null
            val parent = doc.parentFile ?: return null
            val sidecar = parent.listFiles().firstOrNull { it.name == "${doc.name}.sha256" } ?: return null
            context.contentResolver.openInputStream(sidecar.uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (e: Exception) {
            null
        }
    }
}
