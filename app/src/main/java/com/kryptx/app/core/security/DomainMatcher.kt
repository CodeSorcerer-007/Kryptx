package com.kryptx.app.core.security

/**
 * Phishing-resistant domain and app package matcher for Android Autofill.
 *
 * Enforces strict host and subdomain boundary verification to prevent credential leakage
 * to lookalike, homograph, or substring attacker domains (e.g. `evil-paypal.com` vs `paypal.com`).
 */
object DomainMatcher {

    /**
     * Normalizes a URI, URL, or host string into a clean, canonical hostname.
     * Strips schemes (`https://`, `http://`), paths, query params, fragments, port numbers,
     * and leading `www.` subdomains.
     */
    fun normalizeHost(uriOrHost: String?): String {
        if (uriOrHost.isNullOrBlank()) return ""
        var cleaned = uriOrHost.trim().lowercase()

        // Strip URI schemes
        if (cleaned.startsWith("https://")) {
            cleaned = cleaned.removePrefix("https://")
        } else if (cleaned.startsWith("http://")) {
            cleaned = cleaned.removePrefix("http://")
        }

        // Strip path, query params, and anchors
        cleaned = cleaned.substringBefore('/').substringBefore('?').substringBefore('#')

        // Handle IPv6 bracket notation (e.g. [::1]:8080 or [2001:db8::1])
        if (cleaned.startsWith('[') && cleaned.contains(']')) {
            val bracketEnd = cleaned.indexOf(']')
            cleaned = cleaned.substring(1, bracketEnd)
        } else if (cleaned.count { it == ':' } == 1) {
            // Strip port number only for hostnames/IPv4 with single colon (e.g. localhost:8080 or domain.com:443)
            // IPv6 addresses without brackets have multiple colons (e.g. ::1 or 2001:db8::1) and must not be cut
            cleaned = cleaned.substringBefore(':')
        }

        // Strip leading www. prefixes
        while (cleaned.startsWith("www.")) {
            cleaned = cleaned.removePrefix("www.")
        }

        cleaned = cleaned.trim()

        // Strip trailing and repeated dots
        while (cleaned.endsWith('.')) {
            cleaned = cleaned.removeSuffix(".")
        }
        while (cleaned.contains("..")) {
            cleaned = cleaned.replace("..", ".")
        }

        // Normalize IDN to ASCII Punycode to defeat homograph attacks
        cleaned = try {
            java.net.IDN.toASCII(cleaned, java.net.IDN.ALLOW_UNASSIGNED)
        } catch (_: IllegalArgumentException) {
            cleaned
        }

        return cleaned.trim().lowercase()
    }

    private fun isIpAddress(host: String): Boolean {
        return host.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")) ||
               host.contains(':') ||
               (host.startsWith('[') && host.endsWith(']'))
    }

    /**
     * Evaluates whether a requested target web domain matches an item's website.
     * Enforces strict host equality or subdomain boundary matching.
     *
     * Rules:
     * 1. Exact host match: `paypal.com` matches `paypal.com`
     * 2. Subdomain match: `login.paypal.com` ends with `.paypal.com` -> MATCH
     * 3. Target parent match: `paypal.com` with item `signin.paypal.com` -> MATCH
     * 4. Lookalike / Substring rejection: `evil-paypal.com` does NOT match `paypal.com` -> REJECTED
     * 5. Suffix spoofing rejection: `paypal.com.attacker.org` does NOT match `paypal.com` -> REJECTED
     * 6. IP address literal strictness: `192.168.1.1` does NOT match `1.1` -> REJECTED
     * 7. Bare TLD wildcard rejection: `com` does NOT match `google.com` -> REJECTED
     */
    fun isDomainMatch(targetDomain: String?, itemWebsite: String?): Boolean {
        val targetHost = normalizeHost(targetDomain)
        val itemHost = normalizeHost(itemWebsite)

        if (targetHost.isEmpty() || itemHost.isEmpty()) return false

        // Exact match (including identical IP addresses)
        if (targetHost == itemHost) return true

        // IP address literals must match strictly by exact equality — reject suffix spoofing like 192.168.1.1 vs 1.1
        if (isIpAddress(targetHost) || isIpAddress(itemHost)) {
            return false
        }

        // Reject bare TLDs or dotless domains from matching as wildcard parent domains
        // e.g. target="com", item="google.com" -> REJECTED
        if (!targetHost.contains('.') || !itemHost.contains('.')) {
            return false
        }

        // Target domain is a subdomain of the vault item's domain
        // e.g. target="identity.apple.com", item="apple.com" -> target.endsWith(".apple.com") == true
        if (targetHost.endsWith(".$itemHost")) return true

        // Vault item domain is a specific subdomain of the target domain
        // e.g. target="apple.com", item="identity.apple.com" -> item.endsWith(".apple.com") == true
        if (itemHost.endsWith(".$targetHost")) return true

        return false
    }

    /**
     * Evaluates whether an Android native app package matches a vault item.
     * Enforces strict reverse-domain prefix validation (e.g. `twitter.com` -> `com.twitter`)
     * to prevent lookalike packages (e.g. `com.evil.twitter.stealer`) from hijacking autofill.
     */
    fun isPackageMatch(targetPackage: String?, itemWebsite: String?, itemTitle: String?): Boolean {
        if (targetPackage.isNullOrBlank()) return false
        val pkgClean = targetPackage.trim().lowercase()

        // 1. Check if the package starts with the reverse domain of the item website (e.g. com.twitter.android vs twitter.com)
        val itemHost = normalizeHost(itemWebsite)
        if (itemHost.isNotEmpty() && !isIpAddress(itemHost)) {
            val hostParts = itemHost.split('.')
            if (hostParts.size >= 2) {
                // e.g. "twitter.com" -> "com.twitter", "auth.github.com" -> "com.github.auth"
                val reverseDomain = hostParts.reversed().joinToString(".")
                if (pkgClean == reverseDomain || pkgClean.startsWith("$reverseDomain.")) {
                    return true
                }
                // Base domain fallback for subdomains: e.g. "auth.github.com" -> base "github.com" -> "com.github"
                // Guard against 2-part ccTLDs like .co.uk, .com.au, .co.jp, .org.uk where takeLast(2) would yield "uk.co"
                val secondToLast = hostParts.getOrNull(hostParts.size - 2)
                val last = hostParts.last()
                val isTwoPartCctld = last.length == 2 && secondToLast in setOf("co", "com", "org", "net", "gov", "edu", "ac", "ne", "or")
                val basePartsCount = if (isTwoPartCctld) 3 else 2
                if (hostParts.size > basePartsCount) {
                    val baseParts = hostParts.takeLast(basePartsCount)
                    val reverseBase = baseParts.reversed().joinToString(".")
                    if (pkgClean == reverseBase || pkgClean.startsWith("$reverseBase.")) {
                        return true
                    }
                }
            }
        }

        // 2. Check title matching against package leaf token (e.g. "Authenticator" vs "com.google.android.apps.authenticator2")
        // Requires minimum 5-character title to prevent false positives on generic titles ("Mail", "Pay", "App")
        // and strictly matches only the terminal package segment, preventing middle-token spoofing (e.g. com.evil.mail.stealer).
        if (!itemTitle.isNullOrBlank()) {
            val titleClean = itemTitle.trim().lowercase().replace(" ", "").replace("-", "").replace("_", "")
            if (titleClean.length >= 5) {
                val lastSegment = pkgClean.substringAfterLast('.')
                val segmentBase = lastSegment.trimEnd { it.isDigit() }
                if (segmentBase == titleClean) {
                    return true
                }
            }
        }

        return false
    }
}
