### 🎨 AESTHETICS, BRAND IDENTITY & UI PROMPT MATRIX
`aVow` intentionally strips away typical modern mobile application interface patterns (like vibrant accents, rounded cards, fluid swipe interactions, or friendly conversational loading dialogs). The aesthetic is built to feel clear, stark, and structured—resembling a technical laboratory runtime environment.

### 1. Monotonic Color Palette Tokens
* **The Backdrop Layer:** `#6E6E6E` (Light Graphite Gray) — Extends across the entirety of the primary window real estate including page boundaries, margins, and active lock background screens.
* **The Structural Panels:** `#7A7A7A` (Slightly Lighter Gray Surface) — Creates low-contrast, sharp-cornered input containers, text blocks, and selection list matrices.
* **The Grid Dividers & Outlines:** `#8A8A8A` (Accent Border Gray) — Used for thin lines between layout blocks, active check markings, and vector stroke paths instead of shadows or gradients.
* **The Typographic Face:** `#F5F5F5` (Matte Ice White) — Used for high-priority countdown digits and critical data labels.
* **The Sub-Labels:** `#B5B5B5` (Muted Graphite Carbon) — Used for inactive configuration options, system instructions, and secondary parameters.

### 2. Typography Guidelines
* **The Type Family:** Native monospace system formatting, explicitly mapped to **IBM Plex Mono** or an equivalent clean industrial monospace type look.
* **The Layout Formatting Rules:** All block text and option titles must be wrapped in sharp bracket syntax (e.g., `[ STATUS: ACTIVE ]`, `[ DISALLOW_BYPASS ]`). Headers avoid traditional heavy bold font smoothing; they rely on uppercase letter-spacing to build contrast.

### 3. Modern Minimal Logo Architecture
The application branding mark does not use letters, branding tags, or varying line weights[cite: 3]. It features a clean, perfectly uniform, abstract structural line art smiley face icon built directly into your interface vector tracks:

```text
    .-------------------.
   /    .-----------.    \
  /    /             \    \
 /    /   __     __   \    \
|    |   [##]   [##]   |    |
|    |                 |    |
|    |   \         /   |    |
 \    \   '-------'   /    /
  \    \             /    /
   \    '-----------'    /
    '-------------------'
    [   THE BINDING VOW   ]
````

- **App Launcher Presentation:** On your Galaxy Z Fold 7 app tray, the app shows up as a flat, square box of `#6E6E6E` gray containing a center-aligned minimal smiley face track drawn entirely with a constant-width `#8A8A8A` accent line layer.

### 4. Interception Overlay UI Architecture (App Intrusion State)

When the background `AccessibilityService` intercepts an attempt to bypass the vow (such as trying to open a cloned social media app in your Secure Folder or typing a restricted URL into Google Chrome), it forcefully pops open the `MainActivity` overlay interface.

- **Visual Presentation:** The interface drops into an absolute, full-window blackout layout. There are no status readouts, no timers, no text labels, and no window grids.
    
- **The Intercept View Layout:** The display renders as a solid, uninterrupted canvas of flat light graphite gray (`#6E6E6E`). Positioned precisely in the absolute dead-center of the display is the minimal smiley face outline logo drawn in the `#8A8A8A` accent gray uniform stroke. The screen offers zero touch targets, entirely blocking you from seeing or interacting with the underlying restricted app.

When spinning up your initial component layout canvas inside **Google Stitch**, paste this exact engineering instruction block into the generator prompt window:

### 5. The Google Stitch Generative Vibe Prompt

When spinning up your initial component layout canvas inside **Google Stitch**, paste this exact engineering instruction block into the generator prompt window:

```text

Create a single-screen design system layout for an Android application titled "aVow". 
The entire canvas background must be a solid, flat light graphite gray (#6E6E6E) with no gradients, shadows, or fluid lines. 
All UI components use a sharp grid framework divided by thin 1px accent lines colored in (#8A8A8A). 
All typography must utilize an uppercase IBM Plex Mono monospace type system, with high-contrast surfaces rendered in matte white (#F5F5F5) and muted options in a carbon grey (#B5B5B5).

The design must incorporate a minimal, uniform line weight smiley face outline logo drawn using the (#8A8A8A) accent grey line. The line weight of the smile and eyes must match the boundary borders exactly, with no modern tapering or artistic brush weights.

The interface must handle a three-state transition matrix:
- State A (Configuration Workspace): The top bar reads "[ aVow // STATUS: OPEN ]". Below it, present a sharp list row displaying package names with basic checkbox brackets "[ ]". Include a minimal text input container labeled "BAN_DOMAIN_URI >" with an active text cursor line. The bottom action button is a massive, sharp rectangular row containing the text "[ INFLICT BINDING VOW ]".
- State B (Countdown Vault): The screen drops all configuration inputs and collapses into solid graphite gray (#6E6E6E). The top bar reads "[ aVow // STATUS: LOCKED ]". The center of the layout displays the accent grey uniform smiley logo outline, and directly beneath it, a large digital countdown clock formatted as "DD:HH:MM:SS" rendered in high-contrast crisp monospace white text (#F5F5F5), ticking down relentlessly.
- State C (Intrusion Intercept Block): This view triggers when a user tries to access an unauthorized app or website. The entire window layout completely clears away all text headers, clocks, sub-menus, boundaries, and status bars. It renders as an uninterrupted, solid canvas of flat graphite gray (#6E6E6E). Positioned in the mathematical center of the screen is the abstract minimal smiley face outline logo, rendered cleanly in the (#8A8A8A) accent grey line. No other elements are visible.
```