package com.unifiedstreaminghub.poc

import com.unifiedstreaminghub.poc.domain.LaunchKind
import com.unifiedstreaminghub.poc.domain.LaunchOutcome
import com.unifiedstreaminghub.poc.domain.LogEntry
import com.unifiedstreaminghub.poc.domain.LogFormatter
import com.unifiedstreaminghub.poc.domain.Provider
import org.junit.Assert.assertTrue
import org.junit.Test

class LogFormatterTest {

    @Test
    fun `export includes device, outcome, url and every trail step`() {
        val entry = LogEntry(
            timeMillis = 0L,
            provider = Provider.Netflix,
            kind = LaunchKind.TITLE_URL,
            outcome = LaunchOutcome.BROWSER_FALLBACK,
            url = "https://title.example.org/1",
            summary = "opened in a browser",
            detail = "detail text",
            trail = listOf("step one", "step two"),
        )
        val text = LogFormatter.format("Samsung SM-TEST, Android 15 (API 35)", listOf(entry))
        listOf(
            "Device: Samsung SM-TEST", "Netflix / Open title -> Browser fallback",
            "opened in a browser", "detail text", "URL: https://title.example.org/1",
            "- step one", "- step two",
        ).forEach { assertTrue("missing '$it'", text.contains(it)) }
    }

    @Test
    fun `app home entries have no url line`() {
        val entry = LogEntry(0L, Provider.AppleTv, LaunchKind.APP_HOME, LaunchOutcome.MISSING_APP, null, "s")
        assertTrue(!LogFormatter.format("", listOf(entry)).contains("URL:"))
    }
}
