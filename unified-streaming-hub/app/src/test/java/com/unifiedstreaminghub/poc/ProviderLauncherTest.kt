package com.unifiedstreaminghub.poc

import com.unifiedstreaminghub.poc.FakeIntentGateway.Companion.APP
import com.unifiedstreaminghub.poc.FakeIntentGateway.Companion.BROWSER
import com.unifiedstreaminghub.poc.FakeIntentGateway.Companion.INSTALLED
import com.unifiedstreaminghub.poc.domain.DetectionStatus
import com.unifiedstreaminghub.poc.domain.InstallInfo
import com.unifiedstreaminghub.poc.domain.InstallState
import com.unifiedstreaminghub.poc.domain.LaunchKind
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
    private val pkg = Provider.Netflix.packageName

    private fun launcher(g: FakeIntentGateway, diag: (String) -> Unit = {}) =
        ProviderLauncher(g, deviceInfo = "TestPhone, Android 15 (API 35)", diag = diag) { 1_000L }

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
        assertEquals(DetectionStatus.NO_HANDLER, launcher(FakeIntentGateway()).detect(url).status)
    }

    @Test
    fun `detect rejects invalid url without querying the package manager`() {
        val g = FakeIntentGateway()
        assertEquals(DetectionStatus.INVALID_URL, launcher(g).detect("http://insecure.example.org").status)
        assertTrue(g.calls.isEmpty())
    }

    // ---- open title: route 1 (verified package) ----

    @Test
    fun `open uses the verified package first and stops when it accepts`() {
        val g = FakeIntentGateway(install = INSTALLED, packageStart = StartResult.Started)
        val e = launcher(g).open(Provider.Netflix, url)
        assertEquals(LaunchOutcome.APP_LAUNCH_REQUESTED, e.outcome)
        assertEquals(LaunchKind.TITLE_URL, e.kind)
        assertEquals(listOf("package:$pkg:$url"), g.starts())
        val detail = e.detail.orEmpty()
        assertTrue(detail.contains(pkg))
        assertTrue(detail.contains("does not prove"))
    }

    @Test
    fun `open skips the package route when the app is not installed`() {
        val g = FakeIntentGateway(install = InstallInfo(InstallState.NOT_INSTALLED), anyStart = StartResult.Started)
        val e = launcher(g).open(Provider.Netflix, url)
        assertEquals(LaunchOutcome.BROWSER_FALLBACK, e.outcome)
        assertFalse(g.starts().any { it.startsWith("package:") })
        assertTrue(e.summary.contains("not installed"))
    }

    @Test
    fun `open tries the package route when install state is unknown`() {
        val g = FakeIntentGateway(
            install = InstallInfo(InstallState.UNKNOWN, detail = "boom"),
            packageStart = StartResult.Started,
        )
        assertEquals(LaunchOutcome.APP_LAUNCH_REQUESTED, launcher(g).open(Provider.Netflix, url).outcome)
    }

    @Test
    fun `installed app that rejects the url falls back to browser with a clear message`() {
        val g = FakeIntentGateway(
            handlersForTitleUrl = listOf(BROWSER),
            install = INSTALLED,
            packageStart = StartResult.NoActivity,
            nonBrowserStart = StartResult.NoActivity,
            anyStart = StartResult.Started,
        )
        val e = launcher(g).open(Provider.DisneyPlus, url)
        assertEquals(LaunchOutcome.BROWSER_FALLBACK, e.outcome)
        assertTrue(e.summary.contains("installed but does not accept this URL"))
        assertTrue(e.detail!!.contains("Title-specific launch is not available"))
        assertEquals(
            listOf("package:${Provider.DisneyPlus.packageName}:$url", "nonBrowser:$url", "any:$url"),
            g.starts(),
        )
    }

    // ---- open title: routes 2 and 3 ----

    @Test
    fun `a different non-browser app is reported as such and not as the provider`() {
        val g = FakeIntentGateway(
            handlersForTitleUrl = listOf(APP),
            install = INSTALLED,
            packageStart = StartResult.NoActivity,
            nonBrowserStart = StartResult.Started,
        )
        val e = launcher(g).open(Provider.Netflix, url)
        assertEquals(LaunchOutcome.APP_LAUNCH_REQUESTED, e.outcome)
        assertTrue(e.summary.contains("other than the verified Netflix package"))
        assertTrue(e.detail!!.contains(APP.packageName))
        assertFalse(g.starts().any { it.startsWith("any:") })
    }

    @Test
    fun `open reports missing app when nothing can handle the url`() {
        val g = FakeIntentGateway(anyStart = StartResult.NoActivity)
        assertEquals(LaunchOutcome.MISSING_APP, launcher(g).open(Provider.PrimeVideo, url).outcome)
    }

    @Test
    fun `open reports unknown when the package route throws unexpectedly`() {
        val g = FakeIntentGateway(install = INSTALLED, packageStart = StartResult.Failed("SecurityException: boom"))
        val e = launcher(g).open(Provider.AppleTv, url)
        assertEquals(LaunchOutcome.UNKNOWN, e.outcome)
        assertEquals("SecurityException: boom", e.detail)
        assertEquals(1, g.starts().size) // no further routes tried
    }

    @Test
    fun `open reports unknown when the non-browser route throws`() {
        val g = FakeIntentGateway(nonBrowserStart = StartResult.Failed("x"))
        assertEquals(LaunchOutcome.UNKNOWN, launcher(g).open(Provider.Netflix, url).outcome)
    }

    @Test
    fun `open reports unknown when browser fallback fails unexpectedly`() {
        val g = FakeIntentGateway(anyStart = StartResult.Failed("IllegalStateException: x"))
        assertEquals(LaunchOutcome.UNKNOWN, launcher(g).open(Provider.Netflix, url).outcome)
    }

    @Test
    fun `open rejects invalid input and sends no intent`() {
        val g = FakeIntentGateway()
        val e = launcher(g).open(Provider.Netflix, "netflix://title/1")
        assertEquals(LaunchOutcome.REJECTED_INPUT, e.outcome)
        assertTrue(g.calls.isEmpty())
    }

    @Test
    fun `log entry carries provider trimmed url and clock time`() {
        val e = launcher(FakeIntentGateway()).open(Provider.AppleTv, "  $url ")
        assertEquals(Provider.AppleTv, e.provider)
        assertEquals(url, e.url)
        assertEquals(1_000L, e.timeMillis)
    }

    // ---- launch app ----

    @Test
    fun `launch app succeeds for an installed app`() {
        val g = FakeIntentGateway(install = INSTALLED, launchAppStart = StartResult.Started)
        val e = launcher(g).launchApp(Provider.PrimeVideo)
        assertEquals(LaunchOutcome.APP_LAUNCH_REQUESTED, e.outcome)
        assertEquals(LaunchKind.APP_HOME, e.kind)
        assertEquals(null, e.url)
        assertEquals(listOf("launchApp:${Provider.PrimeVideo.packageName}"), g.starts())
    }

    @Test
    fun `launch app sends no intent when not installed`() {
        val g = FakeIntentGateway(install = InstallInfo(InstallState.NOT_INSTALLED))
        val e = launcher(g).launchApp(Provider.Netflix)
        assertEquals(LaunchOutcome.MISSING_APP, e.outcome)
        assertTrue(g.starts().isEmpty())
    }

    @Test
    fun `installed but not launchable is unknown, not missing`() {
        val g = FakeIntentGateway(install = INSTALLED, launchAppStart = StartResult.NoActivity)
        assertEquals(LaunchOutcome.UNKNOWN, launcher(g).launchApp(Provider.Netflix).outcome)
    }

    @Test
    fun `launch app with unknown install state and no launcher is missing`() {
        val g = FakeIntentGateway(install = InstallInfo(InstallState.UNKNOWN), launchAppStart = StartResult.NoActivity)
        assertEquals(LaunchOutcome.MISSING_APP, launcher(g).launchApp(Provider.Netflix).outcome)
    }

    @Test
    fun `launch app failure is unknown`() {
        val g = FakeIntentGateway(install = INSTALLED, launchAppStart = StartResult.Failed("SecurityException"))
        assertEquals(LaunchOutcome.UNKNOWN, launcher(g).launchApp(Provider.Netflix).outcome)
    }

    // ---- diagnostics ----

    @Test
    fun `every attempt records an ordered trail and forwards each step to the diagnostic sink`() {
        val sink = mutableListOf<String>()
        val g = FakeIntentGateway(install = INSTALLED, packageStart = StartResult.Started)
        val e = launcher(g) { sink += it }.open(Provider.Netflix, url)

        assertTrue(e.trail.first().startsWith("device: TestPhone"))
        assertTrue(e.trail.any { it.startsWith("install state") })
        assertTrue(e.trail.any { it.contains("route 1") })
        assertEquals("result: App launch", e.trail.last())
        assertEquals(e.trail.map { "[Netflix] $it" }, sink)
    }

    @Test
    fun `rejected input is still traced`() {
        val e = launcher(FakeIntentGateway()).open(Provider.Netflix, "")
        assertTrue(e.trail.any { it.startsWith("input rejected") })
    }
}
