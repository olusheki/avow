## README.md

### 📵 AVOW: AN UNYIELDING DIGITAL BINDING VOW FOR ABSOLUTE FOCUS

`aVow` is an uncompromising, hyper-minimalist, open-source Android application designed for individuals who require absolute, ironclad barriers against digital distraction. It converts your mobile device into a single-purpose workspace by enforcing an inescapable, mathematically final countdown lock.

Unlike conventional productivity utilities that rely on easily bypassed user-space overlays, `aVow` integrates deeply into Android’s system security frameworks via **Enterprise Device Owner** privileges. Once a focus duration (up to 90 days) is activated, the application renders itself entirely un-uninstallable, immune to force-stops, and insulated against device setting overrides or Safe Mode workarounds. There are no emergency bypasses, no "quit buttons," and no administrative loopholes. The device remains locked down until the epoch timestamp lapses.

### 💡 WHAT IT IS FOR

- **Eliminating the Safety Valve:** Modern mobile operating systems are engineered for perpetual engagement. Existing app blockers fail because they leave a conscious safety valve open: a user under intense cognitive fatigue can always input an emergency password, clear app data, turn off accessibility permissions, or uninstall the blocker entirely.
    
- **Radical Commitment Strategy:** `aVow` is built as an instrument of radical commitment—a structural **"Binding Vow"** that eliminates the daily exhaustion of willpower by removing the technical _possibility_ of failure.
    
- **Target Audience Workloads:** Designed specifically for deep, multi-week research cycles, intense academic preparation, or critical software engineering sprints. It works to de-fractionate attention spans deeply damaged by algorithmic micro-content and establishes an absolute, non-negotiable commitment contract with your hardware.
    

### 🛠️ WHO BUILT IT

- **Independent Concept:** `aVow` was conceived and designed by Daniel Olusheki as a deeply personalized tool for extreme focus.
    
- **Development Toolkit:** The architecture and codebases were scaffolded and assembled utilizing **Google Antigravity 2.0** (an agentic, AI-native command center) dual-wielded with **Android Studio**, using **Google Stitch** for front-end design generation.
    

### 📲 HOW TO USE IT

The application runs purely locally and offline, featuring an austere, monotonic single-page user interface that shifts states based on your active vow status:

- **State 1: The Configuration Sanctuary (Inactive Lock)**
    
    1. _Target Selection:_ Tap any application from the populated system package list (including deep system wrappers like Samsung's _Secure Folder_ or Android 15's _Private Space_) to mark it for restriction.
        
    2. _Web Domain Blacklisting:_ Type specific root URLs (e.g., `instagram.com`, `twitter.com`) into the monochrome input field.
        
    3. _Rule Configuration:_ Define custom window behaviors if desired (e.g., completely blocked between 10:00 PM and 6:00 AM, or an automated allowance of exactly 5 minutes per hour).
        
    4. _The Vow Activation:_ Select your macro lock duration (ranging from 1 hour to 90 days) using the tactile slider.
        
    5. _Striking the Vow:_ Press and hold the primary `[ INFLICT VOW ]` button for 3 seconds. A system confirmation prompt will appear, warning you that this action is technically irreversible. Upon confirmation, the lock drops instantly.
        
- **State 2: The Monolithic Countdown (Active Lock)**
    
    - The entire user interface completely collapses into an opaque, solid light graphite-grey display.
        
    - All configuration fields, buttons, and app lists vanish.
        
    - The screen displays a single, crisp, center-aligned digital clock ticking down to the exact second the binding vow expires. You cannot open settings, you cannot clear storage, and you cannot open restricted applications or domains.
        

### 🚀 HOW TO DEPLOY ON GITHUB

Because `aVow` interacts with powerful Android administrative controls, your public GitHub repository must be structured securely to avoid leaking sensitive build signatures or target configurations.

- **1. Repository Initial Setup**
    
    Bash
    
    ```
    cd path/to/your/project/vowblocker
    git init
    git remote add origin https://github.com/YOUR_USERNAME/aVow.git
    ```
    

* **2. The `.gitignore` Security Matrix**
  Ensure your local configurations are hidden from public view. Create a `.gitignore` file in your root folder and include the following explicit blocks:

```text
  # Android Studio / Gradle defaults
  .gradle/
  build/
  captures/
  .externalNativeBuild/
  .cxx/
  local.properties

  # Signing Configurations (CRITICAL: Never leak your production keys)
  *.jks
  *.keystore
  signing/

  # OS Specifics
  .DS_Store
  Thumbs.db
* **3. Commit and Push**
  ```bash
  git add .
  git commit -m "feat: complete initial localized release framework for aVow"
  git branch -M main
  git push -u origin main
```
