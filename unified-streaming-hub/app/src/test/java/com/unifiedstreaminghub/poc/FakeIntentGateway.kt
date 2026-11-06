package com.unifiedstreaminghub.poc

import com.unifiedstreaminghub.poc.domain.HandlerInfo
import com.unifiedstreaminghub.poc.domain.InstallInfo
import com.unifiedstreaminghub.poc.domain.InstallState
import com.unifiedstreaminghub.poc.domain.IntentGateway
import com.unifiedstreaminghub.poc.domain.ProviderLauncher
import com.unifiedstreaminghub.poc.domain.StartResult

/**
 * Scripted gateway. Handler packages here are synthetic test names, not real provider IDs;
 * install state applies to whatever package the launcher asks about.
 */
class FakeIntentGateway(
    private val handlersForTitleUrl: List<HandlerInfo> = emptyList(),
    private val browsers: List<HandlerInfo> = listOf(BROWSER),
    var install: InstallInfo = InstallInfo(InstallState.NOT_INSTALLED),
    var launchAppStart: StartResult = StartResult.Started,
    var packageStart: StartResult = StartResult.NoActivity,
    var nonBrowserStart: StartResult = StartResult.NoActivity,
    var anyStart: StartResult = StartResult.Started,
) : IntentGateway {

    val calls = mutableListOf<String>()

    override fun installInfo(packageName: String): InstallInfo {
        calls += "install:$packageName"
        return install
    }

    override fun resolveHandlers(url: String): List<HandlerInfo> {
        calls += "resolve:$url"
        return if (url == ProviderLauncher.BROWSER_PROBE_URL) browsers else handlersForTitleUrl
    }

    override fun launchApp(packageName: String): StartResult {
        calls += "launchApp:$packageName"
        return launchAppStart
    }

    override fun startInPackage(packageName: String, url: String): StartResult {
        calls += "package:$packageName:$url"
        return packageStart
    }

    override fun startRequiringNonBrowser(url: String): StartResult {
        calls += "nonBrowser:$url"
        return nonBrowserStart
    }

    override fun startAny(url: String): StartResult {
        calls += "any:$url"
        return anyStart
    }

    /** Calls that actually tried to start something. */
    fun starts(): List<String> = calls.filter {
        it.startsWith("launchApp:") || it.startsWith("package:") ||
            it.startsWith("nonBrowser:") || it.startsWith("any:")
    }

    companion object {
        val BROWSER = HandlerInfo("test.browser", "Test Browser")
        val APP = HandlerInfo("test.provider.app", "Test Provider App")
        val INSTALLED = InstallInfo(InstallState.INSTALLED, versionName = "1.2.3")
    }
}
