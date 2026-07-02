package com.avow.app.util

/**
 * Domains for a single scope (usage limits, or one scheduled block) are stored as a
 * comma-separated string so the DataStore schema and tamper signature stay unchanged, while the
 * UI presents them as a multi-domain chip list — consistent with the global BAN DOMAIN SET.
 */
object DomainUtil {

    /** Parses a comma-separated domain string into a clean, de-duplicated, lowercased list. */
    fun parse(csv: String): List<String> =
        csv.split(',')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .distinct()

    /** Formats a list of domains back into the canonical comma-separated storage string. */
    fun format(domains: List<String>): String =
        domains.map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(",")

    /** True if [url] contains any of the domains encoded in [csv] (case-insensitive). */
    fun matches(url: String, csv: String): Boolean =
        parse(csv).any { url.contains(it, ignoreCase = true) }
}
