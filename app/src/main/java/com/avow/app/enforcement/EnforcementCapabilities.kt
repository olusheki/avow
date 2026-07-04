package com.avow.app.enforcement

/**
 * Abstracts the device-owner-only powers so shared UI/logic in `src/main` never references
 * `DevicePolicyManager` or the `DeviceAdmin` receiver directly. The `full` flavor provides a real
 * implementation; the `lite` (Play) flavor provides a no-op one, so the lite APK ships with no
 * device-admin code or surface at all.
 *
 * Construct via `CapabilitiesFactory.create(context)` — the factory is defined per flavor.
 */
interface EnforcementCapabilities {

    /**
     * True in the full flavor (device-owner features *can* exist, once provisioned); false in lite.
     * Drives whether the device-owner UI rows are shown at all — lite omits them entirely rather
     * than showing permanently-disabled rows.
     */
    val supportsDeviceOwnerFeatures: Boolean

    /** True only when the app is genuinely Device Owner (full build, ADB-provisioned). Always false in lite. */
    val isDeviceOwnerActive: Boolean

    /**
     * Applies (or clears) the device-owner system locks for a binding vow. Returns an error message
     * to surface to the user, or null on success / when the capability isn't available (lite no-op).
     */
    fun assertBindingVow(
        activate: Boolean,
        secureFolderEnabled: Boolean,
        privateSpaceEnabled: Boolean,
        lockUninstall: Boolean,
        disallowDataWipe: Boolean,
        disableSafeBoot: Boolean,
        blockPlayStore: Boolean,
        deactivateUsbDebugging: Boolean
    ): String?

    /** Strips Device Owner so the app can be uninstalled. Returns an error message, or null. */
    fun deactivateDeviceOwner(): String?
}
