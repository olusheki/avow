package com.avow.app.ui

import kotlin.random.Random

/**
 * Single source of truth for the mascot speech-bubble copy.
 *
 * Messages are intentionally kept as plain data here so the tone can be edited in one place.
 * Semantic emphasis is expressed with inline markers ([[s]]..[[/s]] for sage/encouragement,
 * [[r]]..[[/r]] for red/danger) that the speech-bubble UI parses into coloured spans. All other
 * text stays monochrome, matching the "color = emotion only" design direction.
 */
object MascotMessages {

    // Inline emphasis markers parsed by the speech bubble.
    const val SAGE_OPEN = "[[s]]"
    const val SAGE_CLOSE = "[[/s]]"
    const val RED_OPEN = "[[r]]"
    const val RED_CLOSE = "[[/r]]"

    // --- Locked (an active vow is holding) ---
    val lockedGreetings = listOf("system secure.", "vow holds.", "standing guard.", "status check.")
    val lockedContexts = listOf("You're holding the line.", "The lock is absolute.", "Focus is active.")
    val lockedTaglines = listOf("Stand tall.", "Stay serious.", "No compromises.", "Keep going.")

    // --- Unlocked (no vow active) ---
    val unlockedGreetings = listOf("vault open.", "at rest.", "system idle.")
    val unlockedContexts = listOf("Ready when you are.", "No active restrictions.", "Establish a new vow.")
    val unlockedTaglines = listOf("Make a vow.", "Keep it tight.", "Prepare your focus.")

    private fun plural(n: Int, unit: String) = if (n == 1) unit else "${unit}s"

    /** Streak celebration — shown when the user has a running streak. */
    fun streakMessage(streakDays: Int): String =
        "Day $streakDays. You are ${SAGE_OPEN}holding the line$SAGE_CLOSE."

    /**
     * Locked-state line built from the live vow countdown shown on the dashboard. Highlights the
     * remaining time in sage and appends an enforcer tagline.
     */
    fun vowCountdownMessage(
        days: Int,
        hours: Int,
        minutes: Int,
        seconds: Int,
        random: Random = Random.Default
    ): String {
        val phrase = when {
            days > 0 -> "$days ${plural(days, "day")} left"
            hours > 0 -> "$hours ${plural(hours, "hour")} left"
            minutes > 0 -> "$minutes ${plural(minutes, "minute")} left"
            else -> "$seconds ${plural(seconds, "second")} left"
        }
        return "$SAGE_OPEN$phrase$SAGE_CLOSE — ${lockedTaglines.random(random)}"
    }

    /**
     * Chooses a speech-bubble line from everything we can currently derive: the composed greeting,
     * the live vow countdown (when a timed vow is holding), and the zen-score trend (when at least
     * two focus sessions have been recorded). [zenDelta] is the latest session's zen score minus
     * the average of prior sessions, in points; null when there isn't enough history.
     */
    fun generateLine(
        isLocked: Boolean,
        days: Int,
        hours: Int,
        minutes: Int,
        seconds: Int,
        zenDelta: Int? = null,
        random: Random = Random.Default
    ): String {
        val hasCountdown = isLocked && (days + hours + minutes + seconds) > 0
        val candidates = buildList {
            add(generateMessage(isLocked, random = random))
            if (hasCountdown) add(vowCountdownMessage(days, hours, minutes, seconds, random))
            if (zenDelta != null) add(zenScoreMessage(zenDelta))
        }
        return candidates.random(random)
    }

    /**
     * Reflection on week-over-week screen time. Negative/zero [percentChange] means improvement
     * (down that many percent, sage); a positive value means a regression (up, red).
     */
    fun reflectionMessage(percentChange: Int): String =
        if (percentChange <= 0)
            "You're ${SAGE_OPEN}down ${-percentChange}% screentime$SAGE_CLOSE from last week. Good job."
        else
            "You're ${RED_OPEN}up $percentChange% screentime$RED_CLOSE than before. Let's work on that."

    /** Reflection on the zen score. */
    fun zenScoreMessage(percentChange: Int): String =
        if (percentChange >= 0)
            "Your zen score is ${SAGE_OPEN}$percentChange% higher$SAGE_CLOSE than before."
        else
            "Your zen score is ${RED_OPEN}${-percentChange}% lower$RED_CLOSE than before."

    /**
     * Builds a single speech-bubble line.
     *
     * When [streakDays] is positive a streak template is returned; otherwise a fresh line is
     * composed by drawing one greeting, one context and one tagline from the pool matching the
     * lock state. Because each part is drawn from a distinct pool, no element repeats within a line.
     */
    fun generateMessage(
        isLocked: Boolean,
        streakDays: Int = 0,
        random: Random = Random.Default
    ): String {
        if (streakDays > 0) return streakMessage(streakDays)

        val greetings = if (isLocked) lockedGreetings else unlockedGreetings
        val contexts = if (isLocked) lockedContexts else unlockedContexts
        val taglines = if (isLocked) lockedTaglines else unlockedTaglines

        return "${greetings.random(random)} ${contexts.random(random)} ${taglines.random(random)}"
    }
}
