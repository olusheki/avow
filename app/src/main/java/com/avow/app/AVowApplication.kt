package com.avow.app

import android.app.Application
import com.avow.app.worker.ReminderWorker

class AVowApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Register the daily idle-nudge check. KEEP policy means this is a no-op if already scheduled.
        ReminderWorker.schedule(this)
    }
}
