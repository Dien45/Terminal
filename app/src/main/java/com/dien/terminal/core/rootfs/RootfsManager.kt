package com.dien.terminal.core.rootfs

import android.content.Context
import android.os.Build
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Downloads, verifies and extracts the Alpine Linux minirootfs used as the
 * proot guest filesystem (FR-1). Alpine is fetched at runtime from the
 * official CDN instead of being bundled in the APK, matching the arch of the
 * running device.
 */
class RootfsManager(private val context: Context) {

    companion object {
        private const val ALPINE_BASE = "https://dl-cdn.alpinelinux.org/alpine/latest-stable/releases"
    }

    data class Progress(val phase: String, val percent: Int, val detail: String = "")

    val rootfsDir: File get() = File(context.filesDir, "alpine/rootfs")
    val stagingDir: File get() = File(context.filesDir, "alpine/.staging")
    private val cacheTarball: File get() = File(context.cacheDir, "alpine-minirootfs.tar.gz")

    fun isInstalled(): Boolean = File(rootfsDir, "etc/alpine-release").exists()

    /** Maps Android's ABI name to the directory name Alpine publishes releases under. */
    fun alpineArch(): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull { it in setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86") }
            ?: Build.SUPPORTED_ABIS.first()
        return when (abi) {
            "arm64-v8a" -> "aarch64"
            "armeabi-v7a" -> "armv7"
            "x86_64" -> "x86_64"
            "x86" -> "x86"
            else -> throw IllegalStateException("Unsupported ABI: $abi")
        }
    }

    fun prootAbiDir(): String = when (Build.SUPPORTED_ABIS.firstOrNull { it in setOf("arm64-v8a", "armeabi-v7a", "x86_64") }) {
        "arm64-v8a" -> "arm64-v8a"
        "armeabi-v7a" -> "armeabi-v7a"
        "x86_64" -> "x86_64"
        else -> throw IllegalStateException("No supported proot build for this device's ABI (${Build.SUPPORTED_ABIS.joinToString()})")
    }

    /**
     * Full setup pipeline: resolve the latest minirootfs filename for this
     * arch, download it, verify its SHA-256 against Alpine's published
     * checksum, then extract it. Safe to call from a background thread only.
     */
    @Throws(IOException::class)
    fun setup(onProgress: (Progress) -> Unit) {
        val arch = alpineArch()
        onProgress(Progress("resolve", 0, "Mencari versi Alpine terbaru untuk $arch..."))

        val listingUrl = URL("$ALPINE_BASE/$arch/")
        val listing = httpGetText(listingUrl)
        val fileName = Regex("alpine-minirootfs-[0-9.]+-$arch\\.tar\\.gz")
            .find(listing)?.value
            ?: throw IOException("Tidak dapat menemukan rootfs Alpine untuk arsitektur $arch")

        val tarballUrl = URL("$ALPINE_BASE/$arch/$fileName")
        val sha256Url = URL("$ALPINE_BASE/$arch/$fileName.sha256")

        onProgress(Progress("download", 5, "Mengunduh $fileName..."))
        downloadTo(tarballUrl, cacheTarball) { pct ->
            onProgress(Progress("download", 5 + (pct * 55 / 100), "Mengunduh $fileName... $pct%"))
        }

        onProgress(Progress("verify", 62, "Memverifikasi checksum SHA-256..."))
        val expectedSha256 = runCatching { httpGetText(sha256Url).trim().split(Regex("\\s+")).first() }.getOrNull()
        val actualSha256 = sha256Of(cacheTarball)
        if (expectedSha256 != null && !expectedSha256.equals(actualSha256, ignoreCase = true)) {
            cacheTarball.delete()
            throw IOException("Checksum tidak cocok - file unduhan kemungkinan korup. Silakan coba lagi.")
        }

        onProgress(Progress("extract", 68, "Mengekstrak rootfs Alpine..."))
        if (stagingDir.exists()) stagingDir.deleteRecursively()
        stagingDir.mkdirs()
        extractTarGz(cacheTarball, stagingDir) { pct ->
            onProgress(Progress("extract", 68 + (pct * 25 / 100), "Mengekstrak rootfs Alpine... $pct%"))
        }

        onProgress(Progress("finalize", 95, "Menyiapkan konfigurasi dasar..."))
        writeBaseConfig(stagingDir)

        if (rootfsDir.exists()) rootfsDir.deleteRecursively()
        rootfsDir.parentFile?.mkdirs()
        if (!stagingDir.renameTo(rootfsDir)) {
            stagingDir.copyRecursively(rootfsDir, overwrite = true)
            stagingDir.deleteRecursively()
        }
        cacheTarball.delete()

        onProgress(Progress("done", 100, "Selesai"))
    }

    /** Wipes the rootfs entirely so the user can start fresh (Settings -> Reset rootfs). */
    fun reset() {
        if (rootfsDir.exists()) rootfsDir.deleteRecursively()
        if (stagingDir.exists()) stagingDir.deleteRecursively()
    }

    /**
     * Replaces the current rootfs with the contents of [tarGz] (used by
     * Restore, FR-9). Caller is responsible for the surrounding checksum
     * validation UX; this just performs the swap.
     */
    @Throws(IOException::class)
    fun restoreFrom(tarGz: File, onProgress: (Progress) -> Unit) {
        if (stagingDir.exists()) stagingDir.deleteRecursively()
        stagingDir.mkdirs()
        onProgress(Progress("extract", 10, "Mengekstrak backup..."))
        extractTarGz(tarGz, stagingDir) { pct ->
            onProgress(Progress("extract", 10 + (pct * 70 / 100), "Mengekstrak backup... $pct%"))
        }
        onProgress(Progress("swap", 85, "Mengganti rootfs lama..."))
        if (rootfsDir.exists()) rootfsDir.deleteRecursively()
        rootfsDir.parentFile?.mkdirs()
        if (!stagingDir.renameTo(rootfsDir)) {
            stagingDir.copyRecursively(rootfsDir, overwrite = true)
            stagingDir.deleteRecursively()
        }
        onProgress(Progress("done", 100, "Rootfs berhasil dipulihkan"))
    }

    private fun writeBaseConfig(root: File) {
        runCatching {
            File(root, "etc/resolv.conf").writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")
        }
        runCatching {
            val hosts = File(root, "etc/hosts")
            if (!hosts.exists()) hosts.writeText("127.0.0.1 localhost\n::1 localhost\n")
        }
        runCatching { File(root, "root").mkdirs() }
        runCatching { File(root, "tmp").mkdirs() }
    }

    // ---------------------------------------------------------------------
    // Low level helpers
    // ---------------------------------------------------------------------

    private fun httpGetText(url: URL): String {
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.inputStream.use { return it.readBytes().toString(Charsets.UTF_8) }
    }

    private fun downloadTo(url: URL, dest: File, onPercent: (Int) -> Unit) {
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        val total = conn.contentLengthLong
        conn.inputStream.use { input ->
            FileOutputStream(dest).use { output ->
                val buf = ByteArray(64 * 1024)
                var readTotal = 0L
                var lastPct = -1
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    output.write(buf, 0, n)
                    readTotal += n
                    if (total > 0) {
                        val pct = (readTotal * 100 / total).toInt()
                        if (pct != lastPct) {
                            lastPct = pct
                            onPercent(pct)
                        }
                    }
                }
            }
        }
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun extractTarGz(tarGz: File, destDir: File, onPercent: (Int) -> Unit) {
        var entryCount = 0
        GzipCompressorInputStream(tarGz.inputStream().buffered()).use { gz ->
            TarArchiveInputStream(gz).use { tar ->
                var entry: TarArchiveEntry? = tar.nextTarEntry
                while (entry != null) {
                    val outFile = File(destDir, entry.name)
                    if (!outFile.canonicalPath.startsWith(destDir.canonicalPath)) {
                        entry = tar.nextTarEntry
                        continue // zip-slip guard
                    }
                    when {
                        entry.isDirectory -> {
                            outFile.mkdirs()
                            applyTarMode(outFile, entry.mode)
                        }
                        entry.isSymbolicLink -> {
                            outFile.parentFile?.mkdirs()
                            // android.system.Os works down to API 21, unlike java.nio.file.Files
                            // (API 26+), which would crash on the PRD's minSdk 24 devices.
                            runCatching { android.system.Os.symlink(entry.linkName, outFile.absolutePath) }
                        }
                        else -> {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { out -> tar.copyTo(out) }
                            // Alpine's binaries (apk, busybox, /bin/sh, ...) must keep their
                            // executable bit from the tar entry, otherwise proot's exec into
                            // the rootfs fails immediately - FileOutputStream alone creates
                            // files without any exec permission.
                            applyTarMode(outFile, entry.mode)
                        }
                    }
                    entryCount++
                    if (entryCount % 50 == 0) onPercent((entryCount / 20).coerceAtMost(99))
                    entry = tar.nextTarEntry
                }
            }
        }
        onPercent(100)
    }

    /** Restores the owner/group/other rwx bits recorded in a tar entry onto the extracted file. */
    private fun applyTarMode(file: File, mode: Int) {
        runCatching {
            android.system.Os.chmod(file.absolutePath, mode and 0x1FF)
        }.onFailure {
            // Fallback for environments without android.system.Os (shouldn't happen on API 24+,
            // but keep the rootfs usable even if the syscall wrapper is unavailable for some reason).
            file.setReadable(true, false)
            file.setExecutable((mode and 0b001_000_000) != 0, false)
            file.setWritable((mode and 0b010_000_000) != 0, true)
        }
    }
}
