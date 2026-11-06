package com.unifiedstreaminghub.poc

import com.unifiedstreaminghub.poc.FakeIntentGateway.Companion.APP
import com.unifiedstreaminghub.poc.FakeIntentGateway.Companion.BROWSER
import com.unifiedstreaminghub.poc.domain.DetectionStatus
import com.unifiedstreaminghub.poc.domain.LaunchOutcome
import com.unifiedstreaminghub.poc.domain.Provider
import com.unifiedstreaminghub.poc.domain.ProviderLauncher
import com.unifiedstreaminghub.poc.domain.StartResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderLauncherTest {

    private val url = "https://title.example.org/watch/1"

    private fun launcher(gateway: FakeIntentGateway) = ProviderLauncher(gateway) { 1_000L }

    // ---- detection ----

    @Test
    fun `detect reports non-browser handler and excludes browsers from it`() {
        val g = FakeIntentGateway(handlersForTitleUrl = listOf(APP, BROWSER))
        val d = launcher(g).detect(url)
        assertEquals(DetectionStatus.NON_BROWSER_HANDLER, d.status)
        assertEquals(listOf(APP), d.nonBrowserHandlers)
        assertEquals(listOf(BROWSER), d.browserHandlers)
    }

    @Test
    fun `detect reports browser only`() {
        val d = launcher(FakeIntentGateway(handlersForTitleUrl = listOf(BROWSER))).detect(url)
        assertEquals(DetectionStatus.BROWSER_ONLY, d.status)
        assertTrue(d.nonBrowserHandlers.isEmpty())
    }

    @Test
    fun `detect reports no handler`() {
        val d = launcher(FakeIntentGateway(handlersForTitleUrl = emptyList())).detect(url)
        assertEquals(DetectionStatus.NO_HANDLER, d.status)
    }

    @Test
    fun `detect rejects invalid url without querying the package manager`() {
        val g = FakeIntentGateway()
        val d = launcher(g).detect("http://insecure.example.org")
        assertEquals(DetectionStatus.INVALID_URL, d.status)
        assertTrue(g.calls.isEmpty())
    }

    // ---- open ----

    @Test
    fun `open classifies app launch and never claims playback`() {
        val g = FakeIntentGateway(handlersForTitleUrl = listOf(APP), nonBrowserStart = StartResult.Started)
        val e = launcher(g).open(Provider.Netflix, url)
        assertEquals(LaunchOutcome.APP_LAUNCH_REQUESTED, e.outcome)
        val detail = e.detail.orEmpty()
        assertTrue(detail.contains(APP.packageName))
        assertTrue(detail.contains("does not prove"))
        assertFalse(g.calls.any { it.startsWith("any:") }) // no browser attempt after app success
    }

    @Test
    fun `open falls back to browser when no non-browser handler`() {
        val g = FakeIntentGateway(
            handlersForTitleUrl = listOf(BROWSER),
            nonBrowserStart = StartResult.NoActivity,
            anyStart = StartResult.Started,
        )
        val e = launcher(g).open(Provider.DisneyPlus, url)
        assertEquals(LaunchOutcome.BROWSER_FALLBACK, e.outcome)
        assertEquals(listOf("nonBrowser:$url", "any:$url"), g.calls.filter { it.startsWith("nonBrowser") || it.startsWith("any") })
    }

    @Test
    fun `open reports missing app when nothing can handle the url`() {
        val g = FakeIntentGateway(nonBrowserStart = StartResult.NoActivity, anyStart = StartResult.NoActivity)
        val e = launcher(g).open(Provider.PrimeVideo, url)
        assertEquals(LaunchOutcome.MISSING_APP, e.outcome)
    }

    @Test
    fun `open reports unknown when the first launch throws something unexpected`() {
        val g = FakeIntentGateway(nonBrowserStart = StartResult.Failed("SecurityException: boom"))
        val e = launcher(g).open(Provider.AppleTv, url)
        assertEquals(LaunchOutcome.UNKNOWN, e.outcome)
        assertEquals("SecurityException: boom", e.detail)
        assertFalse(g.calls.any { it.startsWith("any:") })
    }

    @Test
    fun `open reports unknown when browser fallback fails unexpectedly`() {
        val g = FakeIntentGateway(anyStart = StartResult.Failed("IllegalStateException: x"))
        val e = launcher(g).open(Provider.Netflix, url)
        assertEquals(LaunchOutcome.UNKNOWN, e.outcome)
    }

    @Test
    fun `open flags a prediction mismatch but still reports the app launch`() {
        val g = FakeIntentGateway(handlersForTitleUrl = listOf(BROWSER), nonBrowserStart = StartResult.Started)
        val e = launcher(g).open(Provider.Netflix, url)
        assertEquals(LaunchOutcome.APP_LAUNCH_REQUESTED, e.outcome)
        assertTrue(e.detail!!.contains("did not predict"))
    }

    @Test
    fun `open rejects invalid input and sends no intent`() {
        val g = FakeIntentGateway()
        val e = launcher(g).open(Provider.Netflix, "netflix://title/1")
        assertEquals(LaunchOutcome.REJECTED_INPUT, e.outcome)
        assertTrue(g.calls.isEmpty())
    }

    @Test
    fun `log entry carries provider url and clock time`() {
        val g = FakeIntentGateway(anyStart = StartResult.Started)
        val e = launcher(g).open(Provider.AppleTv, "  $url ")
        assertEquals(Provider.AppleTv, e.provider)
        assertEquals(url, e.url)
        assertEquals(1_000L, e.timeMillis)
    }
}
