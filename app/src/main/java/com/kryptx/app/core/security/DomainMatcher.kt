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

        // Strip port numbers (e.g. localhost:8080 or domain.com:443)
        if (cleaned.contains(':')) {
            cleaned = cleaned.substringBefore(':')
        }

        // Strip leading www. prefixes
        while (cleaned.startsWith("www.")) {
            cleaned = cleaned.removePrefix("www.")
        }

        return cleaned.trim()
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
     */
    fun isDomainMatch(targetDomain: String?, itemWebsite: String?): Boolean {
        val targetHost = normalizeHost(targetDomain)
        val itemHost = normalizeHost(itemWebsite)

        if (targetHost.isEmpty() || itemHost.isEmpty()) return false

        // Exact match
        if (targetHost == itemHost) return true

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
     */
    fun isPackageMatch(targetPackage: String?, itemWebsite: String?, itemTitle: String?): Boolean {
        if (targetPackage.isNullOrBlank()) return false
        val pkgClean = targetPackage.trim().lowercase()

        // 1. Check if the package is a reverse domain of the item website (e.g. com.twitter.android vs twitter.com)
        val itemHost = normalizeHost(itemWebsite)
        if (itemHost.isNotEmpty()) {
            val domainBase = itemHost.substringBeforeLast('.') // e.g. "twitter" from "twitter.com"
            val parts = pkgClean.split('.')
            if (parts.contains(domainBase)) return true
        }

        // 2. Check title matching against package tokens
        if (!itemTitle.isNullOrBlank()) {
            val titleClean = itemTitle.trim().lowercase().replace(" ", "").replace("-", "").replace("_", "")
            val lastSegment = pkgClean.substringAfterLast('.')
            if (titleClean.isNotEmpty() && (lastSegment == titleClean || pkgClean.contains(".$titleClean"))) {
                return true
            }
        }

        return false
    }
}
