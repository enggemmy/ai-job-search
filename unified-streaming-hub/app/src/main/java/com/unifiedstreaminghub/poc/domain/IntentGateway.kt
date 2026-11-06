package com.unifiedstreaminghub.poc.domain

/** An activity-owning app that Android reports as able to handle a URL. */
data class HandlerInfo(val packageName: String, val label: String)

sealed interface StartResult {
    /** The system accepted the intent and started an activity. Says nothing about playback. */
    data object Started : StartResult

    /** No activity matched (ActivityNotFoundException, or no launch intent). */
    data object NoActivity : StartResult

    /** Any other failure (SecurityException, bad state, ...). */
    data class Failed(val error: String) : StartResult
}

/**
 * Thin seam over the Android framework so launch classification can be unit-tested.
 * The production implementation is [com.unifiedstreaminghub.poc.data.AndroidIntentGateway].
 */
interface IntentGateway {
    /** Whether [packageName] is installed. Needs the package in the manifest `<queries>`. */
    fun installInfo(packageName: String): InstallInfo

    /** Apps that can handle an ACTION_VIEW / BROWSABLE intent for [url] (needs `<queries>`). */
    fun resolveHandlers(url: String): List<HandlerInfo>

    /** Start the app's launcher activity (app home, no title). */
    fun launchApp(packageName: String): StartResult

    /** ACTION_VIEW for [url] restricted to [packageName]: succeeds only if that app has a matching activity. */
    fun startInPackage(packageName: String, url: String): StartResult

    /**
     * Start ACTION_VIEW with Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER: succeeds only if a
     * non-browser app handles the URL, otherwise [StartResult.NoActivity].
     */
    fun startRequiringNonBrowser(url: String): StartResult

    /** Start plain ACTION_VIEW; a browser is an acceptable handler. */
    fun startAny(url: String): StartResult
}
