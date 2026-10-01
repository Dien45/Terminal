package com.dien.terminal

import android.app.Application
import com.dien.terminal.util.Notifications

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
    }
}
