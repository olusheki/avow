package com.avow.app.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.avow.app.MainActivity
import com.avow.app.data.VowDataStore
import com.avow.app.util.NotificationStyle
import java.util.concurrent.TimeUnit

/**
 * Periodic worker that nudges a drifting user to set a new vow once they've been idle for a while
 * (see [ReminderPolicy]). It never fires during an active vow, and rate-limits itself.
 */
class ReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val dataStore = VowDataStore(applicationContext)
        val now = System.currentTimeMillis()

        if (!ReminderPolicy.shouldRemind(now, dataStore.readReminderInputs())) {
            return Result.success()
        }
        if (!hasNotificationPermission()) {
            // Can't nudge without the runtime permission; try again next cycle once it's granted.
            return Result.success()
        }

        showReminderNotification()
        dataStore.saveLastReminderShown(now)
        return Result.success()
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            applicationContext, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun showReminderNotification() {
        val ctx = applicationContext
        val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                ctx.getString(com.avow.app.R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = ctx.getString(com.avow.app.R.string.reminder_channel_description) }
            manager.createNotificationChannel(channel)
        }

        val launchIntent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            ctx, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setContentTitle(ctx.getString(com.avow.app.R.string.reminder_title))
            .setContentText(ctx.getString(com.avow.app.R.string.reminder_text))
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    ctx.getString(com.avow.app.R.string.reminder_big_text)
                )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
        val notification = NotificationStyle.applyBranding(builder, ctx).build()

        try {
            NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission revoked between our check and here; nothing to do.
        }
    }

    companion object {
        private const val CHANNEL_ID = "vow_reminders"
        private const val NOTIFICATION_ID = 4201
        private const val UNIQUE_WORK_NAME = "idle_vow_reminder"

        /** Enqueues the once-a-day idle check, keeping any existing schedule intact. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
