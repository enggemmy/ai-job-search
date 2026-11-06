package com.unifiedstreaminghub.poc.domain

/**
 * Detects handlers for a provider title URL and opens it, classifying the outcome honestly.
 *
 * Strategy (all documented Android behaviour, no provider-specific knowledge):
 *  1. Ask the package manager which activities can handle `ACTION_VIEW <url>`.
 *  2. Ask the same for a neutral probe URL to learn which of those are generic browsers.
 *     Handlers of the title URL that are not in that set are "non-browser handlers".
 *  3. To open: try `FLAG_ACTIVITY_REQUIRE_NON_BROWSER`; if nothing non-browser matches,
 *     fall back to a plain VIEW (browser); if even that fails, report a missing handler.
 *
 * Limits that the log text repeats on purpose:
 *  - A non-browser handler is not proof that it is the provider's app; the package name is
 *    shown so the tester can judge.
 *  - "Browser fallback" cannot distinguish "app not installed" from "app installed but does not
 *    claim this link" (unverified App Link, or link-handling switched off in system settings).
 *  - startActivity() success means the system accepted the request. It says nothing about
 *    playback, the title existing, or the account being entitled.
 */
class ProviderLauncher(
    private val gateway: IntentGateway,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun detect(rawUrl: String): Detection {
        val url = when (val check = UrlValidator.validate(rawUrl)) {
            is UrlCheck.Invalid -> return Detection(DetectionStatus.INVALID_URL, note = check.reason)
            is UrlCheck.Valid -> check.url
        }

        val handlers = gateway.resolveHandlers(url)
        val browserPackages = gateway.resolveHandlers(BROWSER_PROBE_URL).map { it.packageName }.toSet()
        val (browsers, nonBrowsers) = handlers.partition { it.packageName in browserPackages }

        val status = when {
            nonBrowsers.isNotEmpty() -> DetectionStatus.NON_BROWSER_HANDLER
            browsers.isNotEmpty() -> DetectionStatus.BROWSER_ONLY
            else -> DetectionStatus.NO_HANDLER
        }
        val note = if (status == DetectionStatus.NON_BROWSER_HANDLER) {
            "Package shown is a URL handler, not proof it is the provider's app."
        } else {
            null
        }
        return Detection(status, nonBrowsers, browsers, note)
    }

    fun open(provider: Provider, rawUrl: String): LogEntry {
        val url = when (val check = UrlValidator.validate(rawUrl)) {
            is UrlCheck.Invalid -> return entry(
                provider, LaunchOutcome.REJECTED_INPUT, rawUrl.trim(),
                "No intent sent", check.reason,
            )
            is UrlCheck.Valid -> check.url
        }

        val detection = detect(url)
        val handlerText = describeHandlers(detection)

        return when (val first = gateway.startRequiringNonBrowser(url)) {
            StartResult.Started -> {
                val mismatch = if (detection.status != DetectionStatus.NON_BROWSER_HANDLER) {
                    " Pre-check found no non-browser handler, so the system chose a handler we did not predict."
                } else {
                    ""
                }
                entry(
                    provider, LaunchOutcome.APP_LAUNCH_REQUESTED, url,
                    "A non-browser app accepted the launch request",
                    "$handlerText $NOT_PLAYBACK$mismatch".trim(),
                )
            }

            StartResult.NoActivity -> openInBrowser(provider, url, detection)
            is StartResult.Failed -> entry(
                provider, LaunchOutcome.UNKNOWN, url,
                "Launch failed before a handler was chosen", first.error,
            )
        }
    }

    private fun openInBrowser(provider: Provider, url: String, detection: Detection): LogEntry =
        when (val second = gateway.startAny(url)) {
            StartResult.Started -> entry(
                provider, LaunchOutcome.BROWSER_FALLBACK, url,
                "No non-browser app handled the URL; sent to a browser",
                "The provider app is either not installed or does not claim this link " +
                    "(unverified App Link or link-handling disabled); this test cannot tell which. " +
                    NOT_PLAYBACK,
            )

            StartResult.NoActivity -> entry(
                provider, LaunchOutcome.MISSING_APP, url,
                "Nothing on this device can handle the URL",
                "No app and no browser matched. Pre-check: ${detection.status.label}.",
            )

            is StartResult.Failed -> entry(
                provider, LaunchOutcome.UNKNOWN, url,
                "Browser fallback failed unexpectedly", second.error,
            )
        }

    private fun describeHandlers(detection: Detection): String =
        if (detection.nonBrowserHandlers.isEmpty()) {
            "Pre-check: ${detection.status.label}."
        } else {
            "Non-browser handler(s): " +
                detection.nonBrowserHandlers.joinToString { "${it.label} (${it.packageName})" } + "."
        }

    private fun entry(
        provider: Provider,
        outcome: LaunchOutcome,
        url: String,
        summary: String,
        detail: String?,
    ) = LogEntry(clock(), provider, outcome, url, summary, detail)

    companion object {
        /**
         * Neutral probe used only to learn which installed apps are generic web browsers.
         * example.com is reserved by RFC 2606 and is not a provider address.
         */
        const val BROWSER_PROBE_URL = "https://example.com/"

        const val NOT_PLAYBACK =
            "Android accepted the request; this does not prove the title opened or playback started."
    }
}
