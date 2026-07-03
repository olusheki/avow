package com.avow.app.enforcement

import android.app.admin.DevicePolicyManager
import android.content.Context
import com.avow.app.receiver.DeviceAdmin

/** Full ("aVow Plus") build: real device-owner powers via [DeviceAdmin]. */
class FullEnforcementCapabilities(private val context: Context) : EnforcementCapabilities {

    override val isDeviceOwnerActive: Boolean
        get() = try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            dpm.isDeviceOwnerApp(context.packageName)
        } catch (e: Exception) {
            false
        }

    override fun assertBindingVow(
        activate: Boolean,
        secureFolderEnabled: Boolean,
        privateSpaceEnabled: Boolean,
        lockUninstall: Boolean,
        disallowDataWipe: Boolean,
        disableSafeBoot: Boolean,
        blockPlayStore: Boolean,
        deactivateUsbDebugging: Boolean
    ): String? = DeviceAdmin.assertBindingVow(
        context = context,
        activate = activate,
        secureFolderEnabled = secureFolderEnabled,
        privateSpaceEnabled = privateSpaceEnabled,
        lockUninstall = lockUninstall,
        disallowDataWipe = disallowDataWipe,
        disableSafeBoot = disableSafeBoot,
        blockPlayStore = blockPlayStore,
        deactivateUsbDebugging = deactivateUsbDebugging
    )

    override fun deactivateDeviceOwner(): String? = DeviceAdmin.deactivateDeviceOwner(context)
}
