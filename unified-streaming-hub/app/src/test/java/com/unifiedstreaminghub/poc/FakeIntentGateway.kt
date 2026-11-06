package com.unifiedstreaminghub.poc

import com.unifiedstreaminghub.poc.domain.HandlerInfo
import com.unifiedstreaminghub.poc.domain.IntentGateway
import com.unifiedstreaminghub.poc.domain.ProviderLauncher
import com.unifiedstreaminghub.poc.domain.StartResult

/**
 * Scripted gateway. Handler packages here are synthetic test names, not real provider IDs.
 */
class FakeIntentGateway(
    private val handlersForTitleUrl: List<HandlerInfo> = emptyList(),
    private val browsers: List<HandlerInfo> = listOf(BROWSER),
    var nonBrowserStart: StartResult = StartResult.NoActivity,
    var anyStart: StartResult = StartResult.Started,
) : IntentGateway {

    val calls = mutableListOf<String>()

    override fun resolveHandlers(url: String): List<HandlerInfo> {
        calls += "resolve:$url"
        return if (url == ProviderLauncher.BROWSER_PROBE_URL) browsers else handlersForTitleUrl
    }

    override fun startRequiringNonBrowser(url: String): StartResult {
        calls += "nonBrowser:$url"
        return nonBrowserStart
    }

    override fun startAny(url: String): StartResult {
        calls += "any:$url"
        return anyStart
    }

    companion object {
        val BROWSER = HandlerInfo("test.browser", "Test Browser")
        val APP = HandlerInfo("test.provider.app", "Test Provider App")
    }
}
