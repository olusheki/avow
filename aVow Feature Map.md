### 🗺️ AVOW — FEATURE MAP & AGENTIC TASK PLAN
This roadmap is designed explicitly for execution inside **Antigravity 2.0**. You can paste individual task blocks into the agent panel to systematically build the application components.

---

```text
[ ] TASK 1: ENVIRONMENT SCAFFOLDING & THEME ENGINE CREATION
================================================================================
* AI PERSONA / EXPERTISE: UI/UX Architect & Jetpack Compose Engineer
* PRIORITY:               High
* COMPLEXITY:             Low
* DEPENDENCIES:           None
* ALLOWED FILE MODS:      - app/src/main/java/com/avow/app/ui/theme/Color.kt
                          - app/src/main/java/com/avow/app/ui/theme/Type.kt
                          - app/src/main/java/com/avow/app/ui/theme/Theme.kt
--------------------------------------------------------------------------------
* TASK SPECIFICATION:     Initialize the absolute monotonic design tokens for aVow. 
                          Configure a light graphite-grey background (#6E6E6E), 
                          matte off-white text surfaces (#F5F5F5), and a slightly 
                          lighter grey color (#8A8A8A) for accents, borders, and 
                          vector line layers. Integrate the IBM Plex Mono 
                          typography system across all font styles. Ensure no 
                          vibrant material colors are linked.
--------------------------------------------------------------------------------
* DEFINITION OF DONE:     The workspace successfully compiles, and the local Android 
                          Studio layout preview correctly renders text inside a pure 
                          light graphite-grey box utilizing monospace formatting.
* TASK INTERLOCKING:      Unblocks Task 2 and Task 5.
* VERBOSE AI DECISIONS:   [Record tool actions here]
================================================================================
````

```
[ ] TASK 2: SINGLE-PAGE STATE-MACHINE UI DEPLOYMENT
================================================================================
* AI PERSONA / EXPERTISE: Front-End State Specialist & Reactive UI Developer
* PRIORITY:               High
* COMPLEXITY:             Medium
* DEPENDENCIES:           Task 1
* ALLOWED FILE MODS:      - app/src/main/java/com/avow/app/ui/MainActivity.kt
                          - app/src/main/java/com/avow/app/ui/MainScreen.kt
--------------------------------------------------------------------------------
* TASK SPECIFICATION:     Build a single-page view structure that reads an active 
                          boolean state variable (isVowActive). When false, render 
                          the configuration workspace: a scrollable list of system 
                          packages with checkmarks, a clean monospace text field for 
                          custom domains, and a massive button reading [ INFLICT VOW ]. 
                          When true, instantly wipe the display layout clean and show 
                          the modern outline smiley logo alongside a central, large 
                          countdown clock ticking down to zero.
--------------------------------------------------------------------------------
* DEFINITION OF DONE:     Toggling the state flag locally cleanly switches the user 
                          interface between the target selector setup and the blacked-out 
                          countdown mode instantly without rendering glitches.
* TASK INTERLOCKING:      Unblocks Task 4 and Task 6.
* VERBOSE AI DECISIONS:   [Record tool actions here]
================================================================================
```

```
[ ] TASK 3: DEVICE ADMINISTRATION PROFILE SCAFFOLD
================================================================================
* AI PERSONA / EXPERTISE: Android Security Specialist & System Kernel Engineer
* PRIORITY:               Critical
* COMPLEXITY:             Medium
* DEPENDENCIES:           None
* ALLOWED FILE MODS:      - app/src/main/java/com/avow/app/receiver/DeviceAdmin.kt
                          - app/src/AndroidManifest.xml
--------------------------------------------------------------------------------
* TASK SPECIFICATION:     Implement the structural boilerplate architecture required 
                          to declare aVow an administrative asset. Create a DeviceAdmin 
                          class extending DeviceAdminReceiver. Write the required XML 
                          configuration file declaring tracking metadata. Update the core 
                          AndroidManifest.xml manifest structure with proper permissions headers.
--------------------------------------------------------------------------------
* DEFINITION OF DONE:     The build compiles perfectly, and the system accurately registers 
                          the app inside the local settings layout options as a valid device 
                          administration endpoint.
* TASK INTERLOCKING:      Unblocks Task 4.
* VERBOSE AI DECISIONS:   [Record tool actions here]
================================================================================
```

```
[ ] TASK 4: INESCAPABLE ENTERPRISE DEVICE OWNER INTEGRATION
================================================================================
* AI PERSONA / EXPERTISE: Mobile Enterprise Architect & Security Hardening Agent
* PRIORITY:               Critical
* COMPLEXITY:             High
* DEPENDENCIES:           Task 2, Task 3
* ALLOWED FILE MODS:      - app/src/main/java/com/avow/app/receiver/DeviceAdmin.kt
                          - app/src/main/java/com/avow/app/ui/MainScreen.kt
--------------------------------------------------------------------------------
================================================================================
* TASK SPECIFICATION:     Connect the [ INFLICT VOW ] click trigger to the DevicePolicyManager 
                          runtime environment. When the binding state activates, invoke 
                          absolute system restrictions: block package uninstalls natively using 
                          setUninstallBlocked(), hide application controls using DISALLOW_APPS_CONTROL 
                          (to prevent data wiping/cache clearing exploits), suppress Safe Mode boot 
                          procedures, and kill device factory resetting capabilities. Explicitly declare 
                          the system package profiles for Samsung Secure Folder and Android 15 Private 
                          Space as primary administrative block targets to prevent background cloning exploits.
--------------------------------------------------------------------------------
* DEFINITION OF DONE:     The application successfully intercepts and blocks all uninstallation 
                          attempts, grays out "Force Stop/Clear Data" options within the OS App Info settings 
                          menu, and continuously and reliably suppresses access to Samsung Secure Folder and 
                          Android Private Space profiles without delay or intermittent leaks.
================================================================================
* TASK INTERLOCKING:      Blocked by Task 3. Unblocks Task 7.
* VERBOSE AI DECISIONS:   [Record tool actions here]
================================================================================
```

```
[ ] TASK 5: ACCESSIBILITY SERVICE PACKAGE AND URL INTERCEPTOR
================================================================================
* AI PERSONA / EXPERTISE: OS Background Processes Automator & UI Tree Inspector
* PRIORITY:               High
* COMPLEXITY:             High
* DEPENDENCIES:           Task 1
* ALLOWED FILE MODS:      - app/src/main/java/com/avow/app/service/BlockerService.kt
                          - app/src/AndroidManifest.xml
--------------------------------------------------------------------------------
* TASK SPECIFICATION:     Construct the continuous monitoring foreground service. Subclass 
                          AccessibilityService to capture window changes. Write a recursive node 
                          evaluation function that can dig through the layout hierarchy of browsers 
                          (Chrome/Samsung Internet) to grab context text from address rows. If the 
                          current package matches an entry in your blocklist array, or if a browser 
                          web string includes a restricted domain target, immediately spawn a blocking 
                          overlay intent.
--------------------------------------------------------------------------------
* DEFINITION OF DONE:     Opening a restricted application or typing a blacklisted domain instantly 
                          breaks the user experience and draws the solid aVow overlay right on top of 
                          the screen workspace.
* TASK INTERLOCKING:      Unblocks Task 6.
* VERBOSE AI DECISIONS:   [Record tool actions here]
================================================================================
```

```
[ ] TASK 6: INTERVAL ALLOWANCE MATH & PERSISTENCE MANAGEMENT
================================================================================
* AI PERSONA / EXPERTISE: Core Data Developer & Algorithm Optimization Specialist
* PRIORITY:               Medium
* COMPLEXITY:             Medium
* DEPENDENCIES:           Task 2, Task 5
* ALLOWED FILE MODS:      - app/src/main/java/com/avow/app/data/VowDataStore.kt
                          - app/src/main/java/com/avow/app/utils/TimeCalc.kt
--------------------------------------------------------------------------------
* TASK SPECIFICATION:     Deploy Jetpack DataStore to save configuration choices directly to local 
                          physical disk space. Build a background math analyzer that keeps track of 
                          app usage during an active hour. If a user sets a rule to allow an app for 
                          5 minutes per hour, calculate total active milliseconds. Once the millisecond 
                          calculation surpasses 5 minutes, signal the BlockerService to activate the 
                          overlay for the remaining duration of that hour.
--------------------------------------------------------------------------------
* DEFINITION OF DONE:     Application usage parameters accurately survive forced app closing sequences 
                          and correctly cycle lock boundaries exactly on chronological hourly transitions.
* TASK INTERLOCKING:      Blocked by Task 2 and Task 5. Unblocks Task 7.
* VERBOSE AI DECISIONS:   [Record tool actions here]
================================================================================
```

```
[ ] TASK 7: HARDWARE REBOOT INTERCEPTION TRIPWIRE
================================================================================
* AI PERSONA / EXPERTISE: Android Kernel Event Listener
* PRIORITY:               High
* COMPLEXITY:             Low
* DEPENDENCIES:           Task 4, Task 6
* ALLOWED FILE MODS:      - app/src/main/java/com/avow/app/receiver/BootReceiver.kt
                          - app/src/AndroidManifest.xml
--------------------------------------------------------------------------------
* TASK SPECIFICATION:     Create a BroadcastReceiver listening for android.intent.action.BOOT_COMPLETED. 
                          Upon receiving the system notification, immediately read your DataStore. 
                          If an active countdown lock timestamp is detected, instantly spin the persistent 
                          BlockerService back into memory before the device can draw the default user 
                          workspace launcher.
--------------------------------------------------------------------------------
* DEFINITION OF DONE:     Hard restarting the test device while a focus lock is active results in the app 
                          automatically starting up and asserting its blocking overlays immediately on startup.
* TASK INTERLOCKING:      Blocked by Task 4 and Task 6.
* VERBOSE AI DECISIONS:   [Record tool actions here]
================================================================================
```
