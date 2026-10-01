package com.dien.terminal.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import java.io.RandomAccessFile

/** Simple CPU/RAM/Disk snapshot for the Dashboard tab (FR-2). */
object DeviceInfo {

    data class Snapshot(
        val cpuAbi: String,
        val cpuCores: Int,
        val ramUsedMb: Long,
        val ramTotalMb: Long,
        val diskUsedMb: Long,
        val diskTotalMb: Long
    )

    fun snapshot(context: Context): Snapshot {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val ramTotalMb = memInfo.totalMem / (1024 * 1024)
        val ramUsedMb = ramTotalMb - memInfo.availMem / (1024 * 1024)

        val stat = StatFs(context.filesDir.absolutePath)
        val diskTotalMb = (stat.blockCountLong * stat.blockSizeLong) / (1024 * 1024)
        val diskAvailMb = (stat.availableBlocksLong * stat.blockSizeLong) / (1024 * 1024)
        val diskUsedMb = diskTotalMb - diskAvailMb

        return Snapshot(
            cpuAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
            cpuCores = Runtime.getRuntime().availableProcessors(),
            ramUsedMb = ramUsedMb,
            ramTotalMb = ramTotalMb,
            diskUsedMb = diskUsedMb,
            diskTotalMb = diskTotalMb
        )
    }
}
