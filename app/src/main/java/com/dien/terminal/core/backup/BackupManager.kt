package com.dien.terminal.core.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.dien.terminal.core.rootfs.RootfsManager
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import java.io.File
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Compresses the Alpine rootfs into a `.tar.gz` and writes it (plus a
 * `.sha256` sidecar for later integrity checks) to a user-chosen folder via
 * Storage Access Framework (FR-7/FR-8).
 */
class BackupManager(private val context: Context, private val rootfsManager: RootfsManager) {

    data class Result(val fileName: String, val sizeBytes: Long, val sha256: String)

    fun backup(destTreeUri: Uri, onProgress: (Int, String) -> Unit): Result {
        val tree = DocumentFile.fromTreeUri(context, destTreeUri)
            ?: throw IllegalStateException("Folder tujuan backup tidak valid")

        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val fileName = "terminal-backup-$stamp.tar.gz"

        onProgress(0, "Menyiapkan file backup...")
        val outDoc = tree.createFile("application/gzip", fileName)
            ?: throw IllegalStateException("Gagal membuat file di folder tujuan")

        val digest = MessageDigest.getInstance("SHA-256")
        var written = 0L
        val rootPath = rootfsManager.rootfsDir

        context.contentResolver.openOutputStream(outDoc.uri)?.use { rawOut ->
            DigestOutputStream(rawOut, digest).use { digestOut ->
                GzipCompressorOutputStream(digestOut).use { gz ->
                    TarArchiveOutputStream(gz).use { tar ->
                        tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU)
                        tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_STAR)
                        val allFiles = rootPath.walkTopDown().toList()
                        val total = allFiles.size.coerceAtLeast(1)
                        allFiles.forEachIndexed { index, file ->
                            val relPath = file.relativeTo(rootPath).path
                            if (relPath.isEmpty()) return@forEachIndexed

                            // android.system.Os.lstat (API 21+) is used instead of
                            // java.nio.file (API 26+, would crash on minSdk-24 devices) to
                            // both detect symlinks without following them and to read back
                            // the real owner/group/other bits so executables (busybox, apk,
                            // /bin/sh, ...) keep their +x permission once restored.
                            val lstat = runCatching { android.system.Os.lstat(file.absolutePath) }.getOrNull()
                            val isSymlink = lstat != null && android.system.OsConstants.S_ISLNK(lstat.st_mode)

                            when {
                                isSymlink -> {
                                    val linkTarget = runCatching { android.system.Os.readlink(file.absolutePath) }.getOrNull()
                                    if (linkTarget != null) {
                                        val entry = TarArchiveEntry(relPath, TarArchiveEntry.LF_SYMLINK)
                                        entry.linkName = linkTarget
                                        entry.mode = (lstat!!.st_mode and 0x1FF)
                                        tar.putArchiveEntry(entry)
                                        tar.closeArchiveEntry()
                                    }
                                }
                                file.isDirectory -> {
                                    val entry = TarArchiveEntry(file, relPath)
                                    entry.mode = if (lstat != null) (lstat.st_mode and 0x1FF) else entry.mode
                                    tar.putArchiveEntry(entry)
                                    tar.closeArchiveEntry()
                                }
                                file.isFile -> {
                                    val entry = TarArchiveEntry(file, relPath)
                                    // Preserve the real executable/permission bits captured by
                                    // lstat; commons-compress's File constructor otherwise
                                    // defaults every regular file to 0644 regardless of +x.
                                    entry.mode = if (lstat != null) (lstat.st_mode and 0x1FF) else if (file.canExecute()) 0x1ED else entry.mode
                                    tar.putArchiveEntry(entry)
                                    file.inputStream().use { it.copyTo(tar) }
                                    tar.closeArchiveEntry()
                                    written += file.length()
                                }
                                else -> {
                                    // device nodes / fifos / sockets: not representable safely, skip.
                                }
                            }
                            if (index % 25 == 0) {
                                onProgress((index * 90 / total), "Mengompresi... ($index/$total)")
                            }
                        }
                    }
                }
            }
        } ?: throw IllegalStateException("Gagal membuka stream tujuan backup")

        val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
        onProgress(95, "Menulis checksum...")
        tree.createFile("text/plain", "$fileName.sha256")?.let { sideCar ->
            context.contentResolver.openOutputStream(sideCar.uri)?.use {
                it.write("$sha256  $fileName".toByteArray())
            }
        }

        onProgress(100, "Backup selesai")
        return Result(fileName, outDoc.length(), sha256)
    }
}
