package com.unifiedstreaminghub.poc.domain

enum class InstallState(val label: String) {
    INSTALLED("Installed"),
    NOT_INSTALLED("Not installed"),

    /** Not checked yet, or the check failed for a reason other than "package not found". */
    UNKNOWN("Unknown"),
}

data class InstallInfo(
    val state: InstallState,
    val versionName: String? = null,
    val detail: String? = null,
)

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
 * What happened when we tried to launch.
 *
 * Every value describes only what Android reported about the *launch request*. None of them
 * means playback started, the title screen appeared, or the user is signed in / entitled.
 */
enum class LaunchOutcome(val label: String) {
    /** A non-browser activity accepted the intent. */
    APP_LAUNCH_REQUESTED("App launch"),

    /** The provider app did not take the URL; the link went to a browser instead. */
    BROWSER_FALLBACK("Browser fallback"),

    /** Nothing on the device could handle the request (not installed / no browser). */
    MISSING_APP("Missing app"),

    /** Unexpected exception or contradictory signals. Outcome cannot be classified. */
    UNKNOWN("Unknown"),

    /** Input rejected before any intent was sent. */
    REJECTED_INPUT("Not attempted"),
}

enum class LaunchKind(val label: String) {
    APP_HOME("Launch app"),
    TITLE_URL("Open title"),
}

data class LogEntry(
    val timeMillis: Long,
    val provider: Provider,
    val kind: LaunchKind,
    val outcome: LaunchOutcome,
    /** Null for [LaunchKind.APP_HOME]. */
    val url: String?,
    val summary: String,
    val detail: String? = null,
    /** Ordered diagnostic steps for this attempt, oldest first. */
    val trail: List<String> = emptyList(),
)
