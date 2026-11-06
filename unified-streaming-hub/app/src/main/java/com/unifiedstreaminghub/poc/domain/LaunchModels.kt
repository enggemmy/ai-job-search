package com.unifiedstreaminghub.poc.domain

/** Result of "who on this phone can handle this URL?" */
enum class DetectionStatus(val label: String) {
    /** At least one non-browser app is registered for this URL. */
    NON_BROWSER_HANDLER("Non-browser app can handle this URL"),

    /** Only browsers can handle it: provider app absent, or it does not claim this link. */
    BROWSER_ONLY("Only a browser can handle this URL"),

    /** Nothing at all can handle it. */
    NO_HANDLER("No app can handle this URL"),

    /** The URL was rejected before querying. */
    INVALID_URL("URL rejected"),
}

data class Detection(
    val status: DetectionStatus,
    val nonBrowserHandlers: List<HandlerInfo> = emptyList(),
    val browserHandlers: List<HandlerInfo> = emptyList(),
    val note: String? = null,
)

/**
 * What happened when we tried to open a title.
 *
 * Every value describes only what Android reported about the *launch request*. None of them
 * means playback started, the title exists, or the user is signed in / entitled.
 */
enum class LaunchOutcome(val label: String) {
    /** A non-browser activity accepted the intent. */
    APP_LAUNCH_REQUESTED("App launch"),

    /** No non-browser handler; the link opened in a browser instead. */
    BROWSER_FALLBACK("Browser fallback"),

    /** Nothing on the device could handle the URL (no app, no browser). */
    MISSING_APP("Missing app"),

    /** Unexpected exception or contradictory signals. Outcome cannot be classified. */
    UNKNOWN("Unknown"),

    /** Input rejected before any intent was sent. */
    REJECTED_INPUT("Not attempted"),
}

data class LogEntry(
    val timeMillis: Long,
    val provider: Provider,
    val outcome: LaunchOutcome,
    val url: String,
    val summary: String,
    val detail: String? = null,
)
