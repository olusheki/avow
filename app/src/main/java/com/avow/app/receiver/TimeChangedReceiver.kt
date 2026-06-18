package com.avow.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.avow.app.data.VowDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TimeChangedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("TimeChangedReceiver", "Time change detected: $action")
        
        if (action == Intent.ACTION_TIME_CHANGED || action == Intent.ACTION_TIMEZONE_CHANGED) {
            val vowDataStore = VowDataStore(context.applicationContext)
            val pendingResult = goAsync()
            
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val prefs = vowDataStore.preferencesFlow.first()
                    val isVowActive = prefs[VowDataStore.IS_VOW_ACTIVE] ?: false
                    
                    if (isVowActive) {
                        Log.w("TimeChangedReceiver", "System time manipulated while Vow is active! Punishing with Temporary Lockout.")
                        vowDataStore.saveTemporaryLockoutEndTime(SystemClock.elapsedRealtime() + 60L * 60L * 1000L)
                    }
                } catch (e: Exception) {
                    Log.e("TimeChangedReceiver", "Error retrieving Vow state", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
