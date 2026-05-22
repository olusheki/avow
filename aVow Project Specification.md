## PROJECT_SPECIFICATION.md

### 📊 PRODUCT CAPABILITIES & FUNCTIONAL SPECIFICATIONS
* **Irreversible Hard Temporal Lock:** Enforces a rigid countdown clock based on system uptime and hardware epoch tracking. Once set, the temporal lock cannot be edited, shortened, or overridden by the user under any circumstances.
* **Monotonic Blackout Overlay Engine:** Instantly draws a solid, fullscreen `#6E6E6E` light graphite-grey layout blocking user input the moment a restricted package activity or browser URL match is detected.
* **Universal Profile Inclusion (Anti-Loophole Security):** Targets the core package names of native system-isolated vaults, specifically **Samsung Knox Secure Folder** (`com.samsung.knox.securefolder`) and **Android 15 Private Space** (`com.google.android.apps.privatespace`), rendering background app cloning exploits useless.
* **Localized Context Tracking:** Continuously monitors active foreground tasks and browser window URI trees directly on the device hardware. No telemetry, metrics tracking, or external network requests are executed.
* **Dynamic Rule Scheduler:** Parses explicit localized parameters to allow nuanced blocking behaviors (e.g., granting exactly `X` minutes of an app per hour before firing a precise `AlarmManager` block event).
* **Reboot Resiliency Engine:** Listens to core kernel broadcasts. If a device is hard-rebooted mid-vow, the application boots its protective services instantly prior to the home launcher initialization.
* **Irreversible Package Persistence (Anti-Deletion Security):** 
  Utilizes enterprise `DeviceOwner` status to flag the `aVow` package as a permanent system component. The OS completely strips the user's ability to drag-to-uninstall, clear app storage cache, or force-close the background service task window, entirely removing the "delete and redownload" exploit bypass vector.
* **Universal Profile Isolation Layer (Secure Folder Loophole Fix):** 
  Instead of treating Samsung Secure Folder (`com.samsung.knox.securefolder`) or Android 15 Private Space (`com.google.android.apps.privatespace`) as standard applications that can be occasionally bypassed, the app treats them as structural profile boundaries. The background interceptor targets the system component stubs directly. The moment the OS attempts to spin up or pass data context to these isolated vaults, the overlay activates immediately, freezing the entire vault launcher frame.

### ⚙️ ARCHITECTURAL DEEP-DIVE & SYSTEM INTEGRATION
The application achieves absolute control over Android's operational architecture by chaining three distinct system subsystems together:

* **A. DevicePolicyManager (The Privilege Foundation)**
  By leveraging Enterprise Mobile Device Management (MDM) API profiles, `aVow` requests a device state that bypasses standard operating constraints. When provisioned via Android Debug Bridge (ADB) as a **Device Owner**, the app registers a custom component extending `DeviceAdminReceiver`. This enables the following native security blocks:
  * `setUninstallBlocked(adminComponent, packageName, true)`: Eliminates the capability of deleting the app via the dragging interface or package settings menu.
  * `addUserRestriction(adminComponent, UserManager.DISALLOW_APPS_CONTROL)`: Greys out the "Force Stop" and "Clear Storage" options in the native OS Application Management interface.
  * `addUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT)`: Blocks the hardware boot sequence from launching into Safe Mode, sealing the primary backdoor used to bypass third-party services.
  * `addUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)`: Restricts hardware master data resets via settings, blocking desperate profile wipes.
* **B. AccessibilityService (The Surveillance Mechanism)**
  To intercept specific user interfaces without draining battery, `aVow` runs a targeted background `AccessibilityService`. It listens to events of type `TYPE_WINDOW_STATE_CHANGED` and `TYPE_WINDOW_CONTENT_CHANGED`.
  * *App Checking:* It evaluates `event.packageName`. If it detects an entry on the blocklist or a protected vault stub, it triggers the overlay.
  * *URL Tracking:* It scans the user-interface node tree of specified web browsers (e.g., Google Chrome, Samsung Internet) looking for specific UI node class identifiers (`android.widget.EditText`). It parses the string context of the address bar to match against domain blocklists.
* **C. Persistent Memory Architecture**
  Configuration parameters and active countdown timestamps are serialized locally via **Jetpack DataStore** using encrypted system preferences. On boot, the data layer instantly populates the state engine to verify if an active vow is currently written to disk.

### 📁 STRUCTURAL PROJECT ORGANIZATION
The Android codebase is meticulously structured inside the root project directory following standard clean architecture patterns:

```text
aVow/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/avow/app/
│   │   │   │   ├── data/
│   │   │   │   │   └── VowDataStore.kt       # Handles localized preferences, durations, and state saves
│   │   │   │   ├── receiver/
│   │   │   │   │   ├── BootReceiver.kt       # Triggers app reinitialization on device startup
│   │   │   │   │   └── DeviceAdmin.kt        # Interfaces with the DevicePolicyManager framework
│   │   │   │   ├── service/
│   │   │   │   │   └── BlockerService.kt     # Active AccessibilityService tracking packages & URLs
│   │   │   │   ├── ui/
│   │   │   │   │   ├── theme/
│   │   │   │   │   │   ├── Color.kt          # Lighter graphite-grey monotonic palette variables
│   │   │   │   │   │   └── Type.kt           # Explicit IBM Plex Mono font system configurations
│   │   │   │   │   ├── MainActivity.kt       # Single-page interface router and application entrypoint
│   │   │   │   │   └── MainScreen.kt         # Jetpack Compose UI layout (Configuration vs Countdown)
│   │   │   │   └── utils/
│   │   │   │       └── TimeCalc.kt           # Epoch time tracking, intervals, and scheduler math
│   │   │   └── AndroidManifest.xml           # System permissions declarations and security configurations
│   └── build.gradle.kts                      # Main application dependency configurations
└── build.gradle.kts                          # Top-level project build scripts
```

### 🏁 DEVELOPER ONBOARDING & DUAL-WIELDING GUIDE
Welcome to the `aVow` environment. To begin development with your specific dual-wield tooling system (**Android Studio** paired with **Antigravity 2.0**), execute this exact onboarding workflow:

* **Step 1: Clone and Synchronize the IDE Workspace**
  1. Launch Android Studio. Select **New Project** -> **Empty Activity** (Ensures Jetpack Compose dependencies are auto-injected).
  2. Configure the following parameters exactly: Name: `aVow`, Package Name: `com.avow.app`, Language: Kotlin, Minimum SDK: API 34 (Android 14) or higher (Required for robust Private Space hooks).
  3. Let Gradle complete its initial project sync. Once completed, close Android Studio.
* **Step 2: Provision the Antigravity 2.0 Workspace**
  1. Launch the **Antigravity 2.0 Desktop Application**.
  2. Select **Open Workspace** and point it to the root folder created by Android Studio.
  3. Open the Antigravity system settings and locate the **Agent Execution Rules**. Set script execution permissions to **"Request Review"**. This prevents the automated coding model from altering core system files without providing an explanatory overview first.
* **Step 3: Initiate the Vibecoding Loop**
  1. Position Android Studio open on one half of your workspace displaying the **UI Layout Preview** file.
  2. Position Antigravity 2.0 open on the alternate half displaying the **Manager Chat View**.
  3. To command the AI to begin constructing components, execute the specialized system queries detailed in your Feature Map.
