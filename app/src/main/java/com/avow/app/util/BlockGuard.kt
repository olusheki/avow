package com.avow.app.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.telecom.TelecomManager

/**
 * Packages aVow must never block, target, or treat as a doomscroll app.
 *
 * Blocking the home launcher or the phone/dialer can trap the user on an aVow-only device for the
 * length of a vow (up to 99 days) — and in the full flavor, with factory reset disallowed, the only
 * escape would be a factory reset the app itself forbids. The dialer must always stay reachable so
 * the user can place or answer calls (a genuine safety issue), and the launcher so they can leave
 * the blocked app at all.
 *
 * Enforcement guards on this set (the safety net), and the app pickers filter it out (so these can't
 * be chosen in the first place). Resolved once from the current defaults; cheap and fail-soft.
 */
object BlockGuard {

    fun neverBlockablePackages(context: Context): Set<String> {
        val result = mutableSetOf(context.packageName)

        // Current home / launcher.
        try {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            context.packageManager
                .resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
                ?.let { result.add(it) }
        } catch (_: Throwable) { /* leave the launcher out rather than crash */ }

        // Default phone / dialer (public, no permission).
        try {
            (context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager)
                ?.defaultDialerPackage
                ?.let { result.add(it) }
        } catch (_: Throwable) { /* some OEMs throw; skip the dialer rather than crash */ }

        return result
    }
}
