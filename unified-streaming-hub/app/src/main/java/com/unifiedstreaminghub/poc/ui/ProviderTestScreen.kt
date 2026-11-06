package com.unifiedstreaminghub.poc.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.unifiedstreaminghub.poc.data.AndroidIntentGateway
import com.unifiedstreaminghub.poc.data.SharedPrefsUrlStore
import com.unifiedstreaminghub.poc.domain.Detection
import com.unifiedstreaminghub.poc.domain.DetectionStatus
import com.unifiedstreaminghub.poc.domain.LaunchOutcome
import com.unifiedstreaminghub.poc.domain.LogEntry
import com.unifiedstreaminghub.poc.domain.Provider
import com.unifiedstreaminghub.poc.domain.ProviderLauncher
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderTestScreen() {
    val context = LocalContext.current
    val vm: ProviderTestViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ProviderTestViewModel(SharedPrefsUrlStore(context)) }
        },
    )
    // Bound to the current Activity context; recreated on configuration change.
    val launcher = remember(context) { ProviderLauncher(AndroidIntentGateway(context)) }
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Provider integration test") }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Disclaimer() }

            items(Provider.entries, key = { it.name }) { provider ->
                val row = state.rows.getValue(provider)
                ProviderCard(
                    provider = provider,
                    url = row.url,
                    detection = row.detection,
                    onUrlChange = { vm.setUrl(provider, it) },
                    onDetect = { vm.detect(provider, launcher) },
                    onOpen = { vm.open(provider, launcher) },
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Result log", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = vm::clearLog, enabled = state.log.isNotEmpty()) { Text("Clear") }
                }
            }

            if (state.log.isEmpty()) {
                item {
                    Text(
                        "No launches yet. Paste an official title URL above and tap Open Title.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            items(state.log) { LogCard(it) }
        }
    }
}

@Composable
private fun Disclaimer() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Text(
            "Paste an official title link copied from the provider's own site or app. " +
                "A successful launch only means Android accepted the request; it does not prove " +
                "the title opened or that playback started.",
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ProviderCard(
    provider: Provider,
    url: String,
    detection: Detection?,
    onUrlChange: (String) -> Unit,
    onDetect: () -> Unit,
    onOpen: () -> Unit,
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(provider.displayName, style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Test title URL (https)") },
                placeholder = { Text("https://…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDetect, enabled = url.isNotBlank()) { Text("Detect app") }
                Button(onClick = onOpen, enabled = url.isNotBlank()) { Text("Open Title") }
            }
            if (detection != null) DetectionView(detection)
        }
    }
}

@Composable
private fun DetectionView(d: Detection) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            d.status.label,
            style = MaterialTheme.typography.labelLarge,
            color = if (d.status == DetectionStatus.NON_BROWSER_HANDLER) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        if (d.nonBrowserHandlers.isNotEmpty()) {
            Text(
                "Handlers: " + d.nonBrowserHandlers.joinToString { "${it.label} (${it.packageName})" },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        d.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun LogCard(e: LogEntry) {
    val context = LocalContext.current
    val time = remember(e.timeMillis) {
        DateFormat.getTimeFormat(context).format(Date(e.timeMillis))
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(e.outcome.label, style = MaterialTheme.typography.labelLarge, color = e.outcome.color())
                Text("${e.provider.displayName} · $time", style = MaterialTheme.typography.labelMedium)
            }
            Text(e.summary, style = MaterialTheme.typography.bodyMedium)
            e.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text(e.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

/** Fixed hues so outcome classes stay distinguishable regardless of dynamic colour. */
private fun LaunchOutcome.color(): Color = when (this) {
    LaunchOutcome.APP_LAUNCH_REQUESTED -> Color(0xFF2E7D32)
    LaunchOutcome.BROWSER_FALLBACK -> Color(0xFFEF6C00)
    LaunchOutcome.MISSING_APP -> Color(0xFFC62828)
    LaunchOutcome.UNKNOWN -> Color(0xFF6A1B9A)
    LaunchOutcome.REJECTED_INPUT -> Color(0xFF546E7A)
}
