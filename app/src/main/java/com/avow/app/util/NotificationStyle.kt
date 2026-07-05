package com.avow.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.avow.app.R

/**
 * Shared aVow notification look so every notification (idle reminder, doomscroll warning, …) wears
 * the same face: the mascot smiley as the status-bar/header silhouette, a sage accent tint, and the
 * full-color mascot as the large icon on the right.
 */
object NotificationStyle {
    const val ACCENT_SAGE = 0xFF8FB88C.toInt() // SageGreen — the coach accent

    /** Applies the shared branding (small icon, accent, large icon) and returns the same builder. */
    fun applyBranding(builder: NotificationCompat.Builder, context: Context): NotificationCompat.Builder =
        builder
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ACCENT_SAGE)
            .setLargeIcon(mascotLargeIcon(context))

    /** Rasterizes the full-color mascot vector into a bitmap for setLargeIcon(). */
    fun mascotLargeIcon(context: Context): Bitmap {
        val size = (64 * context.resources.displayMetrics.density).toInt().coerceAtLeast(96)
        val drawable = ContextCompat.getDrawable(context, R.drawable.ic_avow_mascot)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable?.setBounds(0, 0, size, size)
        drawable?.draw(canvas)
        return bitmap
    }
}
