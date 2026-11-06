package com.unifiedstreaminghub.poc.domain

/**
 * Detects install state / URL handlers for a provider and launches it, classifying each attempt.
 *
 * Title launch routes, tried in order (all documented Android behaviour; no custom schemes):
 *  1. ACTION_VIEW restricted to the provider's verified package. Success means that exact app
 *     has an activity registered for the URL and the system accepted the request.
 *  2. ACTION_VIEW with FLAG_ACTIVITY_REQUIRE_NON_BROWSER. Success means some *other* non-browser
 *     app took it (reported as such, never as the provider app).
 *  3. Plain ACTION_VIEW, which resolves to a browser. Reported as browser fallback.
 *
 * What the log text repeats on purpose:
 *  - Every success means "Android accepted the request". It is not evidence that the title
 *    screen appeared or that playback started; only a person looking at the phone can tell.
 *  - Route 1 can fail even though the app is installed (the app does not register this URL);
 *    that is how "title-specific launch unsupported for this link" is detected, per provider.
 *  - NOT_INSTALLED relies on package visibility; apps in another profile (for example Samsung
 *    Secure Folder) are invisible and would be reported as not installed.
 *
 * Every step is appended to the entry's trail and also sent to [diag] (Logcat in production).
 */
class ProviderLauncher(
    private val gateway: IntentGateway,
    private val deviceInfo: String = "",
    private val diag: (String) -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun installInfo(provider: Provider): InstallInfo = gateway.installInfo(provider.packageName)

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

    /** Launch the provider app's home screen (no title). */
    fun launchApp(provider: Provider): LogEntry {
        val t = Trace(provider)
        val pkg = provider.packageName
        t.add("action: launch app home, package $pkg")
        val install = gateway.installInfo(pkg)
        t.add("install state: ${describe(install)}")

        if (install.state == InstallState.NOT_INSTALLED) {
            return t.entry(
                LaunchKind.APP_HOME, LaunchOutcome.MISSING_APP, null,
                "${provider.displayName} is not installed", "No intent sent.",
            )
        }
        return when (val r = gateway.launchApp(pkg)) {
            StartResult.Started -> {
                t.add("launch intent accepted by system")
                t.entry(
                    LaunchKind.APP_HOME, LaunchOutcome.APP_LAUNCH_REQUESTED, null,
                    "Launch request for the ${provider.displayName} app accepted",
                    "This only starts the app. It says nothing about sign-in or playback.",
                )
            }

            StartResult.NoActivity -> {
                t.add("no launcher activity returned for $pkg")
                val outcome = if (install.state == InstallState.INSTALLED) {
                    LaunchOutcome.UNKNOWN
                } else {
                    LaunchOutcome.MISSING_APP
                }
                t.entry(
                    LaunchKind.APP_HOME, outcome, null,
                    "Could not find a launcher activity for ${provider.displayName}",
                    if (install.state == InstallState.INSTALLED) {
                        "Reported installed but not launchable (disabled, restricted profile, or no launcher activity)."
                    } else {
                        "Install state was unknown, so this may simply mean the app is not installed."
                    },
                )
            }

            is StartResult.Failed -> {
                t.add("launch failed: ${r.error}")
                t.entry(LaunchKind.APP_HOME, LaunchOutcome.UNKNOWN, null, "Launch failed", r.error)
            }
        }
    }

    /** Open a title URL in the provider app if it accepts it, otherwise fall back. */
    fun open(provider: Provider, rawUrl: String): LogEntry {
        val t = Trace(provider)
        val pkg = provider.packageName

        val url = when (val check = UrlValidator.validate(rawUrl)) {
            is UrlCheck.Invalid -> {
                t.add("input rejected: ${check.reason}")
                return t.entry(
                    LaunchKind.TITLE_URL, LaunchOutcome.REJECTED_INPUT, rawUrl.trim(),
                    "No intent sent", check.reason,
                )
            }

            is UrlCheck.Valid -> {
                t.add("action: open title, host ${check.host}")
                check.url
            }
        }

        val install = gateway.installInfo(pkg)
        t.add("install state ($pkg): ${describe(install)}")
        val detection = detect(url)
        t.add(
            "handlers for URL: non-browser=${detection.nonBrowserHandlers.map { it.packageName }}, " +
                "browsers=${detection.browserHandlers.size}",
        )

        // Route 1: the verified provider package itself.
        if (install.state == InstallState.NOT_INSTALLED) {
            t.add("route 1 (verified package) skipped: not installed")
        } else {
            when (val r = gateway.startInPackage(pkg, url)) {
                StartResult.Started -> {
                    t.add("route 1: $pkg accepted ACTION_VIEW")
                    return t.entry(
                        LaunchKind.TITLE_URL, LaunchOutcome.APP_LAUNCH_REQUESTED, url,
                        "${provider.displayName} app accepted the title URL",
                        "Explicit launch into $pkg. $NOT_PLAYBACK",
                    )
                }

                StartResult.NoActivity ->
                    t.add("route 1: $pkg has no activity registered for this URL")

                is StartResult.Failed -> {
                    t.add("route 1 failed: ${r.error}")
                    return t.entry(
                        LaunchKind.TITLE_URL, LaunchOutcome.UNKNOWN, url,
                        "Launch into ${provider.displayName} failed", r.error,
                    )
                }
            }
        }

        // Route 2: some other non-browser app.
        when (val r = gateway.startRequiringNonBrowser(url)) {
            StartResult.Started -> {
                t.add("route 2: a non-browser app accepted ACTION_VIEW")
                val who = detection.nonBrowserHandlers.joinToString { "${it.label} (${it.packageName})" }
                return t.entry(
                    LaunchKind.TITLE_URL, LaunchOutcome.APP_LAUNCH_REQUESTED, url,
                    "A non-browser app other than the verified ${provider.displayName} package accepted the URL",
                    "Expected handler(s): ${who.ifEmpty { "none predicted" }}. $NOT_PLAYBACK",
                )
            }

            StartResult.NoActivity -> t.add("route 2: no non-browser app handles this URL")
            is StartResult.Failed -> {
                t.add("route 2 failed: ${r.error}")
                return t.entry(
                    LaunchKind.TITLE_URL, LaunchOutcome.UNKNOWN, url,
                    "Launch failed before a handler was chosen", r.error,
                )
            }
        }

        // Route 3: browser.
        return when (val r = gateway.startAny(url)) {
            StartResult.Started -> {
                t.add("route 3: sent to a browser")
                t.entry(
                    LaunchKind.TITLE_URL, LaunchOutcome.BROWSER_FALLBACK, url,
                    when (install.state) {
                        InstallState.INSTALLED ->
                            "${provider.displayName} is installed but does not accept this URL; opened in a browser"

                        InstallState.NOT_INSTALLED ->
                            "${provider.displayName} is not installed; opened in a browser"

                        InstallState.UNKNOWN ->
                            "No app took this URL; opened in a browser (install state unknown)"
                    },
                    "Title-specific launch is not available for this link on this device. " +
                        "Use Launch application to open the app, or test another official link. $NOT_PLAYBACK",
                )
            }

            StartResult.NoActivity -> {
                t.add("route 3: no browser or app can handle the URL")
                t.entry(
                    LaunchKind.TITLE_URL, LaunchOutcome.MISSING_APP, url,
                    "Nothing on this device can handle the URL",
                    "No app and no browser matched. Pre-check: ${detection.status.label}.",
                )
            }

            is StartResult.Failed -> {
                t.add("route 3 failed: ${r.error}")
                t.entry(
                    LaunchKind.TITLE_URL, LaunchOutcome.UNKNOWN, url,
                    "Browser fallback failed unexpectedly", r.error,
                )
            }
        }
    }

    private fun describe(i: InstallInfo): String = buildString {
        append(i.state.label)
        i.versionName?.let { append(", version $it") }
        i.detail?.let { append(" ($it)") }
    }

    /** Collects the diagnostic steps of one attempt and forwards each to [diag]. */
    private inner class Trace(private val provider: Provider) {
        private val steps = mutableListOf<String>()

        init {
            if (deviceInfo.isNotBlank()) add("device: $deviceInfo")
        }

        fun add(step: String) {
            steps += step
            diag("[${provider.displayName}] $step")
        }

        fun entry(
            kind: LaunchKind,
            outcome: LaunchOutcome,
            url: String?,
            summary: String,
            detail: String?,
        ): LogEntry {
            add("result: ${outcome.label}")
            return LogEntry(clock(), provider, kind, outcome, url, summary, detail, steps.toList())
        }
    }

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
