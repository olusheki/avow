package com.avow.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.avow.app.MainActivity
import com.avow.app.data.VowDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("BootReceiver", "onReceive triggered with action: $action")
        
        if (action == Intent.ACTION_BOOT_COMPLETED || 
            action == "android.intent.action.LOCKED_BOOT_COMPLETED" ||
            action == Intent.ACTION_USER_UNLOCKED) {
            
            val userManager = context.getSystemService(Context.USER_SERVICE) as android.os.UserManager
            if (!userManager.isUserUnlocked) {
                Log.w("BootReceiver", "Device locked (Direct-Boot phase). Deferring initialization until ACTION_USER_UNLOCKED.")
                return
            }

            val vowDataStore = VowDataStore(context.applicationContext)
            val pendingResult = goAsync()
            
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val prefs = vowDataStore.preferencesFlow.first()
                    val isVowActive = prefs[VowDataStore.IS_VOW_ACTIVE] ?: false
                    Log.d("BootReceiver", "Checked DataStore. isVowActive: $isVowActive")

                    // A doomscroll cooldown end time is elapsedRealtime-based and meaningless after a
                    // reboot (uptime restarts near zero), so it would massively over-enforce. Clear it.
                    if ((prefs[VowDataStore.TEMPORARY_LOCKOUT_END_TIME] ?: 0L) > 0L) {
                        vowDataStore.saveTemporaryLockoutEndTime(0L)
                    }
                    
                    if (isVowActive) {
                        Log.d("BootReceiver", "Vow active, launching MainActivity")
                        val activityIntent = Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        context.startActivity(activityIntent)
                    }
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Error retrieving Vow state from DataStore", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
