package com.unifiedstreaminghub.poc.ui

import androidx.lifecycle.ViewModel
import com.unifiedstreaminghub.poc.data.UrlStore
import com.unifiedstreaminghub.poc.domain.Detection
import com.unifiedstreaminghub.poc.domain.LogEntry
import com.unifiedstreaminghub.poc.domain.Provider
import com.unifiedstreaminghub.poc.domain.ProviderLauncher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ProviderRow(
    val url: String = "",
    val detection: Detection? = null,
)

data class ProviderTestState(
    val rows: Map<Provider, ProviderRow>,
    /** Newest first. */
    val log: List<LogEntry> = emptyList(),
)

/**
 * Holds URLs, last detection and the result log. It never touches the Android framework: the
 * UI passes a [ProviderLauncher] (bound to the current Activity) into each action, so the
 * ViewModel cannot leak an Activity and stays JVM-testable.
 */
class ProviderTestViewModel(private val store: UrlStore) : ViewModel() {

    private val _state = MutableStateFlow(
        ProviderTestState(Provider.entries.associateWith { ProviderRow(url = store.get(it)) }),
    )
    val state: StateFlow<ProviderTestState> = _state.asStateFlow()

    fun setUrl(provider: Provider, url: String) {
        store.set(provider, url)
        // Editing the URL invalidates any earlier detection result for that row.
        updateRow(provider) { it.copy(url = url, detection = null) }
    }

    fun detect(provider: Provider, launcher: ProviderLauncher) {
        val result = launcher.detect(_state.value.rows.getValue(provider).url)
        updateRow(provider) { it.copy(detection = result) }
    }

    fun open(provider: Provider, launcher: ProviderLauncher) {
        val url = _state.value.rows.getValue(provider).url
        val entry = launcher.open(provider, url)
        _state.update { it.copy(log = listOf(entry) + it.log) }
    }

    fun clearLog() {
        _state.update { it.copy(log = emptyList()) }
    }

    private fun updateRow(provider: Provider, transform: (ProviderRow) -> ProviderRow) {
        _state.update { s ->
            s.copy(rows = s.rows + (provider to transform(s.rows.getValue(provider))))
        }
    }
}
