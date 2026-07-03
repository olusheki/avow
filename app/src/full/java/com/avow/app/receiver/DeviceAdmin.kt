package com.avow.app.receiver

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.util.Log

class DeviceAdmin : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d("DeviceAdmin", "aVow: System Authority Established")
    }

    companion object {
        private const val TAG = "DeviceAdmin"

        /**
         * Asserts the ironclad system locks during an active binding vow.
         * Eliminates delete/redownload loops and closes Secure Folder backdoors.
         */
        fun assertBindingVow(
            context: Context,
            activate: Boolean,
            secureFolderEnabled: Boolean,
            privateSpaceEnabled: Boolean,
            lockUninstall: Boolean,
            disallowDataWipe: Boolean,
            disableSafeBoot: Boolean,
            blockPlayStore: Boolean,
            deactivateUsbDebugging: Boolean
        ): String? {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ComponentName(context, DeviceAdmin::class.java)

            val isAdminActive = dpm.isAdminActive(adminComponent)
            val isDeviceOwner = dpm.isDeviceOwnerApp(context.packageName)

            if (!isAdminActive) {
                Log.w(TAG, "Device Administrator is not active. Proceeding with Accessibility-only mode.")
                return null
            }

            try {
                // 1. Prevent the app from being deleted (conditional on Device Owner check)
                if (isDeviceOwner) {
                    dpm.setUninstallBlocked(adminComponent, context.packageName, activate && lockUninstall)
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Failed setUninstallBlocked", e)
            }

            try {
                if (activate) {
                    if (isDeviceOwner) {
                        // 2. Clear app control (Stops user from going to Settings -> Clear Data to reset the app)
                        if (disallowDataWipe) {
                            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_APPS_CONTROL)
                        }
                        
                        // Safe Boot (Device Owner only)
                        if (disableSafeBoot) {
                            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT)
                        }

                        // Factory Reset (Device Owner only)
                        dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)

                        // Play Store Blocking (DISALLOW_INSTALL_APPS)
                        if (blockPlayStore) {
                            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_INSTALL_APPS)
                        }

                        // USB Debugging (Device Owner only)
                        if (deactivateUsbDebugging) {
                            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_DEBUGGING_FEATURES)
                        }

                        // Structural Profile Block (Freezes secondary profiles like Secure Folder/Private Space entirely)
                        dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_ADD_USER)
                        dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_MODIFY_ACCOUNTS)
                        dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_REMOVE_USER)
                    }
                } else {
                    // Clear restrictions
                    dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_APPS_CONTROL)
                    dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT)
                    dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
                    dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_INSTALL_APPS)
                    dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_DEBUGGING_FEATURES)
                    dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_ADD_USER)
                    dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_MODIFY_ACCOUNTS)
                    dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_REMOVE_USER)
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Failed to apply/clear user restrictions", e)
            }

            // 3. Dynamic Suspend Targets (Samsung Secure Folder & Private Space stubs)
            val suspendedPackages = mutableListOf<String>()
            if (secureFolderEnabled) suspendedPackages.add("com.samsung.knox.securefolder")
            if (privateSpaceEnabled) suspendedPackages.add("com.google.android.apps.privatespace")

            if (suspendedPackages.isNotEmpty()) {
                try {
                    dpm.setPackagesSuspended(adminComponent, suspendedPackages.toTypedArray(), activate)
                } catch (e: SecurityException) {
                    Log.e(TAG, "Failed to toggle package suspension", e)
                }
            }

            // 4. Natively disable Chrome Incognito mode via application restrictions
            try {
                if (isDeviceOwner) {
                    val restrictions = android.os.Bundle().apply {
                        putInt("IncognitoModeAvailability", 1) // 1 = IncognitoModeDisabled
                    }
                    if (activate) {
                        dpm.setApplicationRestrictions(adminComponent, "com.android.chrome", restrictions)
                    } else {
                        dpm.setApplicationRestrictions(adminComponent, "com.android.chrome", android.os.Bundle())
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Failed to apply application restrictions on Chrome", e)
            }

            return null
        }

        /**
         * Clears all system restrictions and programmatically strips Device Owner status,
         * allowing the application to be uninstalled natively from the system.
         */
        fun deactivateDeviceOwner(context: Context): String? {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ComponentName(context, DeviceAdmin::class.java)
            
            try {
                dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_APPS_CONTROL)
                dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT)
                dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
                dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_INSTALL_APPS)
                dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_DEBUGGING_FEATURES)
                dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_ADD_USER)
                dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_MODIFY_ACCOUNTS)
                dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_REMOVE_USER)
                dpm.setUninstallBlocked(adminComponent, context.packageName, false)
                
                dpm.clearDeviceOwnerApp(context.packageName)
                return null
            } catch (e: SecurityException) {
                Log.e(TAG, "Failed to clear Device Owner", e)
                return "SecurityException: Device Owner privileges required to deactivate."
            }
        }
    }
}
