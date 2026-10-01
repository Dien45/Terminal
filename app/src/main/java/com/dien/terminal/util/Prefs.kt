package com.dien.terminal.util

import android.content.Context

/** Lightweight SharedPreferences wrapper for Settings tab (FR-15). */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("terminal_prefs", Context.MODE_PRIVATE)

    var terminalFontSize: Int
        get() = sp.getInt("font_size", 14)
        set(value) = sp.edit().putInt("font_size", value).apply()

    /** Accent shade within the blue-white theme family (index into a small preset list). */
    var accentPreset: Int
        get() = sp.getInt("accent_preset", 0)
        set(value) = sp.edit().putInt("accent_preset", value).apply()

    /** Log retention in days before old entries/files are pruned automatically. */
    var logRetentionDays: Int
        get() = sp.getInt("log_retention_days", 7)
        set(value) = sp.edit().putInt("log_retention_days", value).apply()

    var lastBackupUri: String?
        get() = sp.getString("last_backup_uri", null)
        set(value) = sp.edit().putString("last_backup_uri", value).apply()

    companion object {
        @Volatile private var instance: Prefs? = null
        fun get(context: Context): Prefs =
            instance ?: synchronized(this) { instance ?: Prefs(context).also { instance = it } }
    }
}
