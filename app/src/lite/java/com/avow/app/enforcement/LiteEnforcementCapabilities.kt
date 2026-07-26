package com.avow.app.enforcement

import android.content.Context

/**
 * Lite ("aVow", Play) build: a Play-installed app can never be Device Owner, so every device-owner
 * power reports unavailable / no-ops. Blocking in lite relies on the accessibility service, overlay,
 * usage-stats and (for domains) the local VpnService instead.
 */
class LiteEnforcementCapabilities(context: Context) : EnforcementCapabilities {

    override val supportsDeviceOwnerFeatures: Boolean = false

    override val isDeviceOwnerActive: Boolean = false

    override fun assertBindingVow(
        activate: Boolean,
        secureFolderEnabled: Boolean,
        privateSpaceEnabled: Boolean,
        lockUninstall: Boolean,
        disallowDataWipe: Boolean,
        disableSafeBoot: Boolean,
        blockPlayStore: Boolean,
        deactivateUsbDebugging: Boolean
    ): String? = null

    override fun deactivateDeviceOwner(): String? = null

    // No device-owner powers in lite: pop-out evasion falls back to the cooldown lockout alone.
    override fun setAppsSuspended(packages: Set<String>, suspended: Boolean) = Unit
}
