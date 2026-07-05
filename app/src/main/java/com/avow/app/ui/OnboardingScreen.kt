package com.avow.app.ui

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.avow.app.service.BlockerService
import com.avow.app.ui.theme.*
import kotlinx.coroutines.delay

private const val ONBOARDING_SLIDE_COUNT = 6
// Average daily *social media* use (not total screen time), ~2h 20m.
private const val EVERYONE_ELSE_HOURS = 2.3f

/**
 * First-run onboarding flow ("Enforcer + Coach" voice). Six slides that set the stakes,
 * collect the initial block list, request the mandatory permissions, and hand off to the vault.
 */
@Composable
fun OnboardingFlow(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var slideIndex by remember { mutableStateOf(0) }

    // Shown before we ever send the user to enable the accessibility service (Play prominent-
    // disclosure requirement): the user must affirmatively consent first.
    var showAccessibilityDisclosure by remember { mutableStateOf(false) }

    // Slide 2 local state.
    var estimateHours by remember { mutableStateOf(2f) }
    var realityHours by remember { mutableStateOf<Float?>(null) }

    // Apps chosen on the apps & domains slide; seeded into the default quiet-hours block on finish.
    var selectedApps by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Final slide: one-tap recommended config + the first-vow duration picker.
    var recommendedApplied by remember { mutableStateOf(false) }
    var vowHours by remember { mutableStateOf(1) }
    var vowMinutes by remember { mutableStateOf(0) }

    // Live permission status — recomputed whenever the app returns from a settings screen.
    var permissionRefresh by remember { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionRefresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val accessibilityEnabled = remember(permissionRefresh) {
        isAccessibilityServiceEnabled(context, BlockerService::class.java)
    }
    val notificationsEnabled = remember(permissionRefresh) {
        hasNotificationPermission(context)
    }
    val usageAccessGranted = remember(permissionRefresh) {
        hasUsageStatsPermission(context)
    }

    // Pull a fresh reading whenever usage access is granted and we're on the screen-time slide.
    LaunchedEffect(usageAccessGranted, slideIndex) {
        if (usageAccessGranted && slideIndex == 1 && realityHours == null) {
            realityHours = querySocialMediaHours(context)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LightGraphiteBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        // Thin header: step counter.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "aVow",
                color = MonospaceText,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Text(
                text = String.format("%02d / %02d", slideIndex + 1, ONBOARDING_SLIDE_COUNT),
                color = SubtextGrey,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                letterSpacing = 2.sp
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(OutlineAccent)
        )

        // Slide content.
        Box(modifier = Modifier.weight(1f)) {
            when (slideIndex) {
                0 -> SlideIntro()
                1 -> SlideScreenTime(
                    estimateHours = estimateHours,
                    onEstimateChange = { estimateHours = it },
                    realityHours = realityHours,
                    usageAccessGranted = usageAccessGranted,
                    onScanClick = {
                        if (usageAccessGranted) {
                            realityHours = querySocialMediaHours(context)
                        } else {
                            context.startActivity(
                                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                )
                2 -> SlideAppsDomains(
                    banDomainSet = uiState.banDomainSet,
                    selectedApps = selectedApps,
                    installedApps = uiState.installedApps,
                    onAddDomain = { domain ->
                        viewModel.updateState {
                            copy(banDomainSet = banDomainSet + domain)
                        }
                    },
                    onRemoveDomain = { domain ->
                        viewModel.updateState {
                            copy(banDomainSet = banDomainSet - domain)
                        }
                    },
                    onToggleApp = { pkg ->
                        selectedApps = if (selectedApps.contains(pkg)) selectedApps - pkg else selectedApps + pkg
                    }
                )
                3 -> SlidePermissions(
                    notificationsEnabled = notificationsEnabled,
                    accessibilityEnabled = accessibilityEnabled,
                    onEnableAccessibility = { showAccessibilityDisclosure = true }
                )
                4 -> SlideFeatures()
                5 -> SlideReady(
                    recommendedApplied = recommendedApplied,
                    onApplyRecommended = {
                        viewModel.applyRecommendedSettings(selectedApps, uiState.banDomainSet)
                        recommendedApplied = true
                    },
                    vowHours = vowHours,
                    onVowHoursChange = { vowHours = it },
                    vowMinutes = vowMinutes,
                    onVowMinutesChange = { vowMinutes = it }
                )
            }
        }

        // Dots indicator.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(ONBOARDING_SLIDE_COUNT) { i ->
                val on = i == slideIndex
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .height(6.dp)
                        .width(if (on) 18.dp else 6.dp)
                        .background(if (on) MonospaceText else OutlineAccent, RectangleShape)
                )
            }
        }

        // Navigation controls.
        val continueBlocked = slideIndex == 3 && !accessibilityEnabled
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (slideIndex > 0) {
                SharpBorderButton(
                    text = "{ BACK }",
                    onClick = { slideIndex-- },
                    modifier = Modifier.weight(1f)
                )
            }
            if (slideIndex < ONBOARDING_SLIDE_COUNT - 1) {
                WarmCtaButton(
                    text = if (slideIndex == 0) "[ BEGIN ]" else "CONTINUE",
                    enabled = !continueBlocked,
                    onClick = { if (!continueBlocked) slideIndex++ },
                    modifier = Modifier.weight(if (slideIndex == 0) 1f else 2f)
                )
            } else {
                val vowSeconds = vowHours * 3600L + vowMinutes * 60L
                WarmCtaButton(
                    text = if (vowSeconds > 0) "INFLICT VOW & ENTER" else "ENTER THE VAULT",
                    enabled = true,
                    onClick = {
                        // If the user didn't tap "recommended", still seed the default block with
                        // the apps/domains they picked so the dashboard is pre-populated.
                        if (!recommendedApplied) {
                            viewModel.updateState {
                                val seededBlocks = if (vowBlocks.isNotEmpty()) {
                                    vowBlocks.mapIndexed { i, block ->
                                        if (i == 0) {
                                            block.copy(
                                                targetApps = block.targetApps + selectedApps,
                                                specificDomain = com.avow.app.util.DomainUtil.format(
                                                    com.avow.app.util.DomainUtil.parse(block.specificDomain) + banDomainSet.toList()
                                                )
                                            )
                                        } else {
                                            block
                                        }
                                    }
                                } else {
                                    vowBlocks
                                }
                                copy(vowBlocks = seededBlocks)
                            }
                        }
                        // Inflict the first vow from the picker, so enforcement actually starts
                        // (Passive rules do nothing without a running vow).
                        if (vowSeconds > 0) {
                            viewModel.addBindingTime(vowSeconds)
                        }
                        viewModel.completeOnboarding()
                    },
                    modifier = Modifier.weight(2f)
                )
            }
        }
    }

    if (showAccessibilityDisclosure) {
        AccessibilityDisclosureDialog(
            onAgree = {
                showAccessibilityDisclosure = false
                context.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
            onDismiss = { showAccessibilityDisclosure = false }
        )
    }
}

/* ----------------------------- Slides ----------------------------- */

@Composable
private fun SlideIntro() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(36.dp))
        // Breathing mascot: slow scale pulse.
        val transition = rememberInfiniteTransition(label = "breathe")
        val scale by transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000),
                repeatMode = RepeatMode.Reverse
            ),
            label = "breatheScale"
        )
        SmileyFaceOutline(
            modifier = Modifier
                .size(110.dp)
                .scale(scale)
        )
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = "A vow you can't take back.",
            color = MonospaceText,
            fontFamily = FontFamily.Monospace,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        TypewriterText(
            text = "aVow locks you out of the apps and sites that pull you in — and won't let you undo it on impulse. That's the point.",
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SlideScreenTime(
    estimateHours: Float,
    onEstimateChange: (Float) -> Unit,
    realityHours: Float?,
    usageAccessGranted: Boolean,
    onScanClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 24.dp)
    ) {
        SlideTitle("How much social media, really?")
        TypewriterText(
            text = "Guess your daily social media time. Then let aVow check the tape — most people are off by a lot.",
            fontSize = 13.sp,
            textAlign = TextAlign.Start
        )
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "YOUR GUESS: ${formatHours(estimateHours)} / DAY",
            color = MonospaceText,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Slider(
            value = estimateHours,
            onValueChange = onEstimateChange,
            valueRange = 0.5f..8f,
            steps = 14,
            colors = SliderDefaults.colors(
                thumbColor = MonospaceText,
                activeTrackColor = MonospaceText,
                inactiveTrackColor = OutlineAccent
            )
        )

        Spacer(modifier = Modifier.height(8.dp))
        SharpBorderButton(
            text = if (usageAccessGranted) "[ SCAN SOCIAL MEDIA ]" else "[ GRANT USAGE ACCESS ]",
            onClick = onScanClick,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))
        val maxVal = maxOf(estimateHours, realityHours ?: 0f, EVERYONE_ELSE_HOURS, 1f)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            ComparisonBar("ESTIMATE", estimateHours, maxVal, SubtextGrey, Modifier.weight(1f))
            ComparisonBar("REALITY", realityHours, maxVal, MonospaceText, Modifier.weight(1f))
            ComparisonBar("EVERYONE", EVERYONE_ELSE_HOURS, maxVal, OutlineAccent, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = comparePitch(estimateHours, realityHours),
            // The signal is whether you're above the average, not whether you guessed wrong.
            color = when {
                realityHours == null -> SubtextGrey
                realityHours > EVERYONE_ELSE_HOURS + 0.3f -> LockedRed
                realityHours < EVERYONE_ELSE_HOURS - 0.3f -> SageGreen
                else -> SubtextGrey
            },
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SlideAppsDomains(
    banDomainSet: Set<String>,
    selectedApps: Set<String>,
    installedApps: List<Pair<String, String>>,
    onAddDomain: (String) -> Unit,
    onRemoveDomain: (String) -> Unit,
    onToggleApp: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var domainInput by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 24.dp)
    ) {
        SlideTitle("What pulls you in?")
        Text(
            text = "Pick what costs you the most. You can add more later — never fewer while a vow is active.",
            color = SubtextGrey,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Domains — same add-field / chip pattern as the settings BAN DOMAIN SET.
        Text(
            text = "BLOCK DOMAINS",
            color = MonospaceText,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                MonospaceTextField(
                    value = domainInput,
                    onValueChange = { domainInput = it },
                    placeholder = "e.g. youtube.com"
                )
            }
            AddButton(onClick = {
                val d = domainInput.trim().lowercase()
                if (d.isNotEmpty()) {
                    onAddDomain(d)
                    domainInput = ""
                }
            })
        }
        if (banDomainSet.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                banDomainSet.forEach { domain ->
                    InputChip(text = domain, onRemove = { onRemoveDomain(domain) }, enabled = true)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "INSTALLED APPS",
            color = MonospaceText,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        MonospaceTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = "search apps…"
        )
        Spacer(modifier = Modifier.height(8.dp))
        // Browsers surface at the top; everything else stays alphabetical.
        val ordered = remember(query, installedApps) {
            val base = if (query.isBlank()) installedApps
            else installedApps.filter { it.second.contains(query, ignoreCase = true) }
            base.sortedWith(
                compareByDescending<Pair<String, String>> { isBrowserApp(it.first, it.second) }
                    .thenBy { it.second.lowercase() }
            )
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(ordered, key = { it.first }) { (pkg, label) ->
                val selected = selectedApps.contains(pkg)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleApp(pkg) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = label,
                        color = if (selected) MonospaceText else SubtextGrey,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                    Text(
                        text = if (selected) "[x]" else "[ ]",
                        color = if (selected) MonospaceText else SubtextGrey,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun SlidePermissions(
    notificationsEnabled: Boolean,
    accessibilityEnabled: Boolean,
    onEnableAccessibility: () -> Unit
) {
    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* status refreshed on resume */ }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 24.dp)
    ) {
        SlideTitle("Grant the essentials")
        Text(
            text = "aVow needs these to hold the line. Accessibility is required before you can continue.",
            color = SubtextGrey,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.height(20.dp))

        PermissionStatusCard(
            title = "ACCESSIBILITY SERVICE",
            detail = "Required to block apps and sites.",
            granted = accessibilityEnabled,
            onAction = onEnableAccessibility
        )
        Spacer(modifier = Modifier.height(14.dp))
        PermissionStatusCard(
            title = "NOTIFICATIONS",
            detail = "Required for doomscroll alerts.",
            granted = notificationsEnabled,
            onAction = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        )

        if (!accessibilityEnabled) {
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "[ CONTINUE LOCKED — enable accessibility ]",
                color = LockedRed,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SlideFeatures() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 24.dp)
    ) {
        SlideTitle("What aVow can do")
        FeatureRow("BINDING VOWS", "Set a timer and your rules lock in — hard to undo on impulse until it ends. That's the point.")
        FeatureRow("SCHEDULED BLOCKS", "Block chosen apps and sites during set hours — like 10pm–7am.")
        FeatureRow("USAGE LIMITS", "Give yourself a daily or hourly time budget for your apps.")
        FeatureRow("DOOMSCROLL SHIELD", "When you linger too long, it locks you out to cool off.")
        FeatureRow("FOCUS INSIGHTS", "See your focus sessions and a zen score over time.")

        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(OutlineAccent)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "PASSIVE vs ACTIVE",
            color = MonospaceText,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "PASSIVE (default): your rules only enforce while a vow timer is running. ACTIVE: your rules enforce on their own schedule with no vow needed. You can switch this anytime on the dashboard.",
            color = SubtextGrey,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Set all of this yourself later — or tap \"Use recommended settings\" on the next screen for a sensible start.",
            color = SubtextGrey,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
    }
}

@Composable
private fun FeatureRow(title: String, detail: String) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(
            text = title,
            color = MonospaceText,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = detail,
            color = SubtextGrey,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
    }
}

@Composable
private fun SlideReady(
    recommendedApplied: Boolean,
    onApplyRecommended: () -> Unit,
    vowHours: Int,
    onVowHoursChange: (Int) -> Unit,
    vowMinutes: Int,
    onVowMinutesChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        SmileyFaceOutline(modifier = Modifier.size(72.dp))
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Ready to make a vow?",
            color = MonospaceText,
            fontFamily = FontFamily.Monospace,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))

        // One-tap recommended config: passive, 10pm–7am block on your apps, 10 min/hour limit.
        if (recommendedApplied) {
            Text(
                text = "✓ RECOMMENDED SETTINGS APPLIED",
                color = SageGreen,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        } else {
            WarmCtaButton(
                text = "USE RECOMMENDED SETTINGS",
                enabled = true,
                onClick = onApplyRecommended,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Passive · 10pm–7am block · 10 min/hour combined across your apps.",
                color = SubtextGrey,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "SET YOUR FIRST VOW",
            color = MonospaceText,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Text(
            text = "Passive rules only enforce while a vow runs. Set a duration to start now.",
            color = SubtextGrey,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp,
            modifier = Modifier.padding(top = 4.dp, start = 12.dp, end = 12.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                WheelNumberPicker(range = 0..23, value = vowHours, onValueChange = onVowHoursChange)
                Spacer(modifier = Modifier.height(6.dp))
                Text("HH", color = SubtextGrey, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
            }
            Text(":", color = MonospaceText, fontSize = 20.sp, modifier = Modifier.padding(horizontal = 8.dp).padding(bottom = 14.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                WheelNumberPicker(range = 0..59, value = vowMinutes, onValueChange = onVowMinutesChange)
                Spacer(modifier = Modifier.height(6.dp))
                Text("MM", color = SubtextGrey, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/* ----------------------------- Reusable pieces ----------------------------- */

@Composable
private fun SlideTitle(text: String) {
    Text(
        text = text,
        color = MonospaceText,
        fontFamily = FontFamily.Monospace,
        fontSize = 19.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 24.sp
    )
    Spacer(modifier = Modifier.height(12.dp))
}

/**
 * Reveals [text] one character at a time, pausing longer on sentence punctuation. Re-types whenever
 * the text changes (i.e. on slide entry). Tap to reveal the whole thing immediately (skip).
 */
@Composable
private fun TypewriterText(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit = 13.sp,
    textAlign: TextAlign = TextAlign.Center
) {
    var visibleCount by remember(text) { mutableStateOf(0) }
    var skip by remember(text) { mutableStateOf(false) }
    LaunchedEffect(text) {
        visibleCount = 0
        for (i in 1..text.length) {
            if (skip) break
            visibleCount = i
            val c = text[i - 1]
            val pause = when (c) {
                '.', '!', '?', '—' -> 420L
                ',', ';', ':' -> 200L
                ' ' -> 46L
                else -> 30L
            }
            delay(pause)
        }
        if (skip) visibleCount = text.length
    }
    Text(
        text = text.take(visibleCount),
        color = SubtextGrey,
        fontFamily = FontFamily.Monospace,
        fontSize = fontSize,
        lineHeight = 19.sp,
        textAlign = textAlign,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                skip = true
                visibleCount = text.length
            }
    )
}

@Composable
private fun ComparisonBar(
    label: String,
    value: Float?,
    maxValue: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (value == null) "—" else formatHours(value),
            color = MonospaceText,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        // Bars grow within this weighted region only, so the value/label rows never get covered.
        val fraction = if (value == null || maxValue <= 0f) 0f else (value / maxValue).coerceIn(0.02f, 1f)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(fraction)
                    .background(color)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = SubtextGrey,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            letterSpacing = 1.sp
        )
    }
}

/** Standardized add button, matching the `{ ADD }` control used elsewhere in the app. */
@Composable
private fun AddButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(44.dp)
            .sharpBorder(1.dp, OutlineAccent)
            .background(DarkerSurfaceColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "{ ADD }",
            color = MonospaceText,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Heuristic: does this package/label look like a web browser? Used to float browsers to the top. */
private fun isBrowserApp(pkg: String, label: String): Boolean {
    val p = pkg.lowercase()
    val l = label.lowercase()
    val needles = listOf("chrome", "firefox", "browser", "opera", "brave", "duckduckgo", "sbrowser", "edge", "vivaldi", "ecosia", "samsungbrowser")
    return needles.any { p.contains(it) } || l.contains("browser") || l == "chrome"
}

@Composable
private fun PermissionStatusCard(
    title: String,
    detail: String,
    granted: Boolean,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MutedSurface)
            .sharpBorder(1.dp, if (granted) SageGreen else OutlineAccent)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MonospaceText,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = detail,
                color = SubtextGrey,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        if (granted) {
            Text(
                text = "GRANTED",
                color = SageGreen,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        } else {
            SharpBorderButton(
                text = "[ ENABLE ]",
                onClick = onAction,
                modifier = Modifier.width(96.dp)
            )
        }
    }
}

@Composable
private fun WarmCtaButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .then(if (enabled) Modifier.background(DarkerSurfaceColor) else Modifier)
            .sharpBorder(1.dp, if (enabled) MonospaceText else OutlineAccent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) MonospaceText else SubtextGrey,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

/* ----------------------------- Helpers ----------------------------- */

private fun formatHours(hours: Float): String = String.format("%.1fh", hours)

private fun comparePitch(estimate: Float, reality: Float?): String {
    if (reality == null) {
        return "Scan to see how your guess holds up — and how it compares to the ~${formatHours(EVERYONE_ELSE_HOURS)}/day the average person spends on social media."
    }
    val avg = EVERYONE_ELSE_HOURS
    val estGap = reality - estimate       // > 0 = more than you guessed
    val avgGap = reality - avg            // > 0 = above average

    // First clause: how the reality compares to the guess.
    val guessClause = when {
        estGap >= 0.5f -> "You guessed ${formatHours(estimate)}, but it's really ${formatHours(reality)}"
        estGap <= -0.5f -> "You guessed ${formatHours(estimate)} — it's actually less, ${formatHours(reality)}"
        else -> "You guessed ${formatHours(estimate)}, and you were right: ${formatHours(reality)}"
    }

    // Second clause: how the reality compares to the average, with a connector that flows
    // naturally from whichever guess clause preceded it.
    val aboveGuess = estGap >= 0.5f
    val avgClause = when {
        avgGap >= 0.3f ->
            (if (aboveGuess) " — and that's above" else ", which is still above") +
                " the ~${formatHours(avg)} average. That's the habit worth breaking."
        avgGap <= -0.3f ->
            (if (aboveGuess) ", but still under" else " — comfortably under") +
                " the ~${formatHours(avg)} average. Hold that line."
        else -> " — right about the ~${formatHours(avg)} average. Small cuts add up."
    }
    return guessClause + avgClause
}

private fun hasNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

private fun hasUsageStatsPermission(context: Context): Boolean {
    return try {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        mode == AppOpsManager.MODE_ALLOWED
    } catch (e: Exception) {
        false
    }
}

/** Aggregates foreground time across social media apps only, over the last 24h, in hours. */
private fun querySocialMediaHours(context: Context): Float =
    com.avow.app.util.SocialUsageStats.queryLastDayHours(context)

/**
 * Prominent-disclosure consent shown before we ever open the accessibility settings, per Google
 * Play policy. States plainly what the accessibility service reads, why, and that nothing leaves
 * the device — and requires an affirmative tap before proceeding.
 */
@Composable
private fun AccessibilityDisclosureDialog(
    onAgree: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(LightGraphiteBg)
                .sharpBorder(1.dp, OutlineAccent)
                .padding(20.dp)
        ) {
            Text(
                text = "aVow // ACCESSIBILITY ACCESS",
                color = MonospaceText,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(OutlineAccent)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "To hold your vows, aVow uses Android's Accessibility Service. It reads which " +
                    "app is currently in the foreground and your browser's address bar, so it can " +
                    "block the apps and websites you chose to restrict.\n\n" +
                    "This information is used only to enforce your own limits. aVow does not collect, " +
                    "store, or share it, and nothing ever leaves your device.\n\n" +
                    "On the next screen, find aVow in the list and turn it on.",
                color = SubtextGrey,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SharpBorderButton(
                    text = "{ NOT NOW }",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                WarmCtaButton(
                    text = "I AGREE",
                    enabled = true,
                    onClick = onAgree,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
