### 🏁 STRATEGIC OPERATIONAL ROADMAP
Follow this procedural blueprint to methodically build, secure, and deploy `aVow` on your computer and your Galaxy Z Fold 7. For me, Daniel Olusheki, to do in real life.

### PHASE 1: ENVIRONMENT SCAFFOLDING (DAY 1)
1. **Launch Android Studio:** Choose **New Project** -> **Empty Activity**. Define your workspace name as `aVow` and confirm the internal package path is exactly `com.avow.app`. Set the minimum target SDK profile to **API 34 (Android 14)**.
2. **Generate Your Layout Canvas:** Head to `stitch.withgoogle.com`, load the AI prompt file from your Aesthetics Spec, and generate your custom UI layout. 
3. **Bridge Design to Code:** Export the completed layout vectors straight from Google Stitch into your local theme code files.
4. **Launch Your Agent Tools:** Open the **Antigravity 2.0 Desktop Application**, link it directly to your root project folder sandbox, and verify that the Script Execution rules are set to **"Request Review"**.

### PHASE 2: CORE ENGINEERING & TESTING LOOPS (DAYS 2 - 7)
1. **Configure Theme Frameworks:** Instruct Antigravity: `/goal Apply the monotonic theme colors and IBM Plex Mono typeface rules inside our UI theme package directories.`
2. **Assemble Interface States:** Command the agent: `/goal Build the single-page MainScreen layout structure using Jetpack Compose, supporting dynamic state transitions through an internal isVowActive boolean flag.`
3. **Deploy Background Watchdog:** Command the agent: `/goal Implement the core BlockerService class expanding AccessibilityService to scan window state activities and catch target browser address URLs.`
4. **Build Your Security Base:** Command the agent: `/goal Write the DeviceAdmin boilerplate class extending DeviceAdminReceiver and inject the system-level manifest parameters into AndroidManifest.xml.`
5. **Lock down System Controls:** Command the agent: `/goal Wire the DevicePolicyManager settings triggers into our primary execution button to disable uninstalls, settings modification menus, factory resets, and Safe Mode boots.`

### PHASE 3: HARDWARE PROVISIONING & DEPLOYMENT (DAY 8)
1. **Prep Your Galaxy Z Fold 7:** Open system settings -> **About Phone** -> **Software Information**. Tap **Build Number** 7 times to unlock the hidden development menu. Enter **Developer Options** and turn on **USB Debugging**.
2. **Clear Security Account Hurdles:** Open **Settings > Accounts and Backup > Manage Accounts**. Temporarily **delete all linked Google and Samsung accounts** from your phone profile workspace. *(Crucial: The enterprise deployment terminal command will throw an execution failure if an active personal profile sync is detected).*
3. **Deploy the App Binary:** Connect your Z Fold 7 to your computer using a USB-C cable. Run the compilation build task inside your terminal workspace to side-load the production application package onto your hardware:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

4. **Activate the Binding Vow:** Lock the app directly into your phone's operating kernel by elevating it to global Device Owner status. Run this command in your computer's terminal:

```bash
adb shell dpm set-device-owner com.avow.app/com.avow.app.receiver.DeviceAdmin
```


5. **Restore Regular Accounts:** Once the terminal flashes a `Success` confirmation message, your application holds irreversible system power. You can now immediately sign back into your Google and Samsung profiles. Your phone is fully protected.
