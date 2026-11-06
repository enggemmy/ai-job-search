package com.unifiedstreaminghub.poc.domain

import java.net.URI
import java.net.URISyntaxException

sealed interface UrlCheck {
    data class Valid(val url: String, val host: String) : UrlCheck
    data class Invalid(val reason: String) : UrlCheck
}

/**
 * Minimal input hygiene for tester-supplied URLs.
 *
 * Only https is accepted: it is what Android App Links verification applies to, and it avoids
 * launching arbitrary custom schemes pasted from untrusted sources. This does NOT check that
 * the host belongs to a given provider; that is the tester's responsibility (use official links).
 */
object UrlValidator {
    fun validate(raw: String): UrlCheck {
        val url = raw.trim()
        if (url.isEmpty()) return UrlCheck.Invalid("URL is empty")
        if (url.any { it.isWhitespace() }) return UrlCheck.Invalid("URL contains whitespace")

        val uri = try {
            URI(url)
        } catch (e: URISyntaxException) {
            return UrlCheck.Invalid("Malformed URL: ${e.reason}")
        }
        if (!uri.scheme.equals("https", ignoreCase = true)) {
            return UrlCheck.Invalid("Only https:// URLs are accepted (got '${uri.scheme ?: "no scheme"}')")
        }
        val host = uri.host
        if (host.isNullOrBlank()) return UrlCheck.Invalid("URL has no host")
        return UrlCheck.Valid(url, host)
    }
}
