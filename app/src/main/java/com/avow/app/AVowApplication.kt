package com.avow.app

import android.app.Application
import android.os.UserManager
import android.util.Log
import com.avow.app.worker.ReminderWorker

class AVowApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // The process also starts during direct-boot (locked) to host the directBootAware
        // BlockerService/BootReceiver. WorkManager's initializer is NOT direct-boot aware, so
        // touching it then throws and crashes the whole process — killing enforcement after a
        // reboot. Only schedule once unlocked; a later unlocked start re-runs this (KEEP = no-op).
        val unlocked = try {
            getSystemService(UserManager::class.java)?.isUserUnlocked ?: true
        } catch (e: Throwable) { true }
        if (unlocked) {
            try {
                ReminderWorker.schedule(this)
            } catch (e: Throwable) {
                Log.e("AVowApplication", "Failed to schedule reminder worker", e)
            }
        }
    }
}
