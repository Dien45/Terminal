package com.dien.terminal.core.pm

import android.content.Context
import com.dien.terminal.core.logging.LogLineClassifier
import com.dien.terminal.core.logging.OperationLogger
import com.dien.terminal.core.logging.OperationStatus
import com.dien.terminal.core.logging.OperationType
import com.dien.terminal.core.rootfs.ProotLauncher

data class PackageSearchResult(val name: String, val version: String)

/**
 * Wraps `apk` (Alpine Package Keeper) operations driven from the Dashboard
 * tab (FR-4/5/6), logging every run via [OperationLogger] (FR-12).
 * All functions are blocking and must be called from a background thread.
 */
class PackageManagerOps(private val context: Context, private val launcher: ProotLauncher) {

    private val logger = OperationLogger(context)

    data class OpOutcome(val success: Boolean, val output: String, val fixed: Int = 0, val failed: Int = 0)

    suspend fun update(): OpOutcome = runLogged(OperationType.UPDATE, "apk update", "apk update")

    suspend fun upgrade(): OpOutcome = runLogged(OperationType.UPGRADE, "apk upgrade", "apk upgrade")

    suspend fun fix(): OpOutcome = runLogged(OperationType.FIX, "apk fix", "apk fix") { out ->
        LogLineClassifier.countFixResult(out)
    }

    suspend fun install(pkg: String): OpOutcome =
        runLogged(OperationType.INSTALL, "Install: $pkg", "apk add $pkg")

    suspend fun search(term: String): List<PackageSearchResult> {
        val session = logger.start(OperationType.SEARCH, "Cari paket: $term")
        val result = launcher.runCommand("apk search -v $term") { session.appendLine(it) }
        session.finish(
            if (result.exitCode == 0) OperationStatus.SUCCESS else OperationStatus.FAILED,
            "${result.output.lines().count { it.isNotBlank() }} hasil ditemukan"
        )
        return result.output.lines()
            .filter { it.isNotBlank() }
            .map { parseSearchLine(it) }
    }

    private fun parseSearchLine(line: String): PackageSearchResult {
        val m = Regex("^(.*)-(\\d[^-]*(?:-r\\d+)?)$").find(line.trim())
        return if (m != null) {
            PackageSearchResult(m.groupValues[1], m.groupValues[2])
        } else {
            PackageSearchResult(line.trim(), "")
        }
    }

    private suspend fun runLogged(
        type: OperationType,
        title: String,
        command: String,
        countResult: ((String) -> Pair<Int, Int>)? = null
    ): OpOutcome {
        val session = logger.start(type, title)
        val result = launcher.runCommand(command) { session.appendLine(it) }
        val success = result.exitCode == 0
        val (fixed, failed) = countResult?.invoke(result.output) ?: (0 to 0)
        session.finish(
            if (success) OperationStatus.SUCCESS else OperationStatus.FAILED,
            if (countResult != null) "Diperbaiki: $fixed, Gagal: $failed" else if (success) "Berhasil" else "Gagal (exit ${result.exitCode})",
            fixed, failed
        )
        return OpOutcome(success, result.output, fixed, failed)
    }
}
