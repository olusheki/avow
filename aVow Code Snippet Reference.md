### 📋 REFERENCE CODE SNIPPETS FOR ANTIGRAVITY

These precise Kotlin blocks serve as structural references for the **Antigravity 2.0** agent. It will use these blueprints to assemble the core background services and system security overrides.

### 1. The Monotonic Brand Theme Configuration (`Color.kt` & `Type.kt`)

```kotlin
package com.avow.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Hyper-Minimalist Monotonic Grayscale Palette (Updated)
val LightGraphiteBg = Color(0xFF6E6E6E)    // Lighter graphite gray background
val MutedSurface = Color(0xFF7A7A7A)       // Accent layout box surfaces (slightly lighter)
val OutlineAccent = Color(0xFF8A8A8A)      // Accent borders and logo stroke vector lines
val MonospaceText = Color(0xFFF5F5F5)      // Highly visible text surfaces
val SubtextGrey = Color(0xFFB5B5B5)        // Muted labels and unit tags

// IBM Plex Mono Font Architecture Implementation
val MonospaceTypography = androidx.compose.material3.Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        letterSpacing = 0.5.sp,
        color = MonospaceText
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        letterSpacing = -0.5.sp,
        color = MonospaceText
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Light,
        fontSize = 11.sp,
        color = SubtextGrey
    )
)
```

### 2. Inescapable Device Owner Control Subsystem (`DeviceAdmin.kt`)

```kotlin
package com.avow.app.receiver

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.widget.Toast

class DeviceAdmin : DeviceAdminReceiver() {
    
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(context, "aVow: System Authority Established", Toast.LENGTH_SHORT).show()
    }

    companion object {
        
        /**
         * Asserts the ironclad system locks during an active binding vow.
         * Eliminates delete/redownload loops and closes Secure Folder backdoors.
         */
        fun assertBindingVow(context: Context, activate: Boolean) {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ComponentName(context, DeviceAdmin::class.java)
            
            if (dpm.isDeviceOwnerApp(context.packageName)) {
                // 1. Prevent the app from being deleted (Fixes the delete & redownload bypass)
                dpm.setUninstallBlocked(adminComponent, context.packageName, activate)
                
                if (activate) {
                    // 2. Clear app control (Stops user from going to Settings -> Clear Data to reset the app)
                    dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_APPS_CONTROL)
                    dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT)
                    dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
                    
                    // 3. Structural Profile Block (Freezes secondary profiles like Secure Folder/Private Space entirely)
                    dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_ADD_USER)
                    dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_MODIFY_ACCOUNTS)
                } else {
                    dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_APPS_CONTROL)
                    dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT)
                    dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
                    dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_ADD_USER)
                    dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_MODIFY_ACCOUNTS)
                }
            }
        }
    }
}
```
### 3. UI Window and Web URL Interception Engine (`BlockerService.kt`)

```kotlin
package com.avow.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.avow.app.ui.MainActivity

class BlockerService : AccessibilityService() {

    private val blockedPackages = setOf(
        "com.instagram.android", 
        "com.samsung.knox.securefolder", 
        "com.google.android.apps.privatespace"
    )
    private val blockedDomains = setOf("instagram.com", "facebook.com", "twitter.com")

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!isVowCurrentlyActiveOnDisk()) return

        val currentPackage = event.packageName?.toString()

        if (blockedPackages.contains(currentPackage)) {
            triggerBlackoutOverlay()
            return
        }

        if (currentPackage == "com.android.chrome" || currentPackage == "com.sec.android.app.sbrowser") {
            val rootNode = rootInActiveWindow ?: return
            val activeUrl = extractUrlFromNodeTree(rootNode)
            
            if (blockedDomains.any { domain -> activeUrl.contains(domain) }) {
                triggerBlackoutOverlay()
            }
        }
    }

    private fun extractUrlFromNodeTree(node: AccessibilityNodeInfo): String {
        if (node.className == "android.widget.EditText" || node.viewIdResourceName == "com.android.chrome:id/url_bar") {
            val textContext = node.text?.toString()
            if (!textContext.isNullOrBlank()) return textContext
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = extractUrlFromNodeTree(child)
            if (result.isNotEmpty()) return result
        }
        return ""
    }

    private fun triggerBlackoutOverlay() {
        val overlayIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(overlayIntent)
    }

    private fun isVowCurrentlyActiveOnDisk(): Boolean {
        return true 
    }

    override fun onInterrupt() {}
}
```
