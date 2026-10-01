package com.dien.terminal.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.dien.terminal.R

object Notifications {
    const val CHANNEL_PROGRESS = "progress"
    const val CHANNEL_RESULTS = "results"

    const val ID_PROGRESS = 1001
    const val ID_RESULT = 1002

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_PROGRESS, "Proses sistem", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RESULTS, "Hasil proses", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    fun progressBuilder(context: Context, title: String, text: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, 0, true)
}
