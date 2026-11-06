package com.unifiedstreaminghub.poc.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Plain-text export of the result log, for pasting into a bug report. */
object LogFormatter {
    fun format(deviceInfo: String, entries: List<LogEntry>): String = buildString {
        appendLine("Unified Streaming Hub log")
        if (deviceInfo.isNotBlank()) appendLine("Device: $deviceInfo")
        appendLine("Entries: ${entries.size} (newest first)")
        entries.forEach { e ->
            appendLine()
            appendLine("[${timestamp(e.timeMillis)}] ${e.provider.displayName} / ${e.kind.label} -> ${e.outcome.label}")
            appendLine("  ${e.summary}")
            e.detail?.let { appendLine("  $it") }
            e.url?.let { appendLine("  URL: $it") }
            e.trail.forEach { appendLine("  - $it") }
        }
    }

    private fun timestamp(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(millis))
}
