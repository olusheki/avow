package com.avow.app.ui.theme

import androidx.compose.ui.graphics.Color

val LightGraphiteBg = Color(0xFF6E6E6E)    // Lighter graphite gray background
val MutedSurface = Color(0xFF7A7A7A)       // Accent layout box surfaces (slightly lighter)
val OutlineAccent = Color(0xFF8A8A8A)      // Accent borders and logo stroke vector lines
val MonospaceText = Color(0xFFF5F5F5)      // Highly visible text surfaces
// Muted labels and unit tags. Lightened from B5B5B5 (~2.7:1) to ~3.2:1 on LightGraphiteBg. True AA
// 4.5:1 is unreachable for a muted grey on this mid-grey background without going near-white (which
// would flatten the label hierarchy) — closing that gap needs a design call (bigger text or signoff).
val SubtextGrey = Color(0xFFCCCCCC)
val LockedRed = Color(0xFFE57373)          // Muted red for "LOCKED" status text / danger
val SageGreen = Color(0xFF8FB88C)          // Muted sage for success / encouragement (the coach)
val DarkerSurfaceColor = Color(0xFF5A5A5A) // Recessed/pressable surfaces (add rows, dropdowns)
val InkText = Color(0xFF1C1C1C)            // Near-black ink for text/icons on light-selected surfaces

// Bracket convention for control labels (terminal aesthetic): { } wraps utility/navigation controls
// (BACK, BEGIN, ADD, CANCEL, SETTINGS); [ ] wraps prominent CTAs and status readouts (VIEW INSIGHTS,
// ENFORCEMENT OFFLINE, DOOMSCROLL COOLDOWN). Keep new labels consistent with their neighbors.
