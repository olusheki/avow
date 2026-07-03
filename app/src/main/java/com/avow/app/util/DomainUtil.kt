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

    /**
     * Extracts the lowercased host from address-bar text, or "" if there isn't one. Tolerant of the
     * partial forms browsers show: bare "instagram.com", "instagram.com/p/1", "https://www.x.com/…",
     * "host:443", "user@host". Search-box text (no real host) yields something that won't match a
     * configured domain, which is the point — we no longer false-match a domain mentioned in a path
     * or a search query.
     */
    fun hostOf(url: String): String {
        var s = url.trim()
        val schemeIdx = s.indexOf("://")
        if (schemeIdx >= 0) s = s.substring(schemeIdx + 3)
        s = s.substringBefore('/').substringBefore('?').substringBefore('#')
        s = s.substringAfterLast('@').substringBefore(':')
        return s.trim().lowercase()
    }

    /** True if [url]'s host equals [domain] or is a subdomain of it (label-boundary match). */
    fun matchesHost(url: String, domain: String): Boolean {
        val d = domain.trim().lowercase()
        if (d.isEmpty()) return false
        val host = hostOf(url)
        if (host.isEmpty()) return false
        return host == d || host.endsWith(".$d")
    }

    /** True if [url]'s host matches any of the domains encoded in [csv]. */
    fun matches(url: String, csv: String): Boolean =
        parse(csv).any { matchesHost(url, it) }
}
