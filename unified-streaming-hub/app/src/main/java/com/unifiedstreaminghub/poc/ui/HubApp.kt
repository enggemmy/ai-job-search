package com.unifiedstreaminghub.poc.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.unifiedstreaminghub.poc.data.AndroidIntentGateway
import com.unifiedstreaminghub.poc.data.Diagnostics
import com.unifiedstreaminghub.poc.data.SharedPrefsUrlStore
import com.unifiedstreaminghub.poc.domain.Detection
import com.unifiedstreaminghub.poc.domain.DetectionStatus
import com.unifiedstreaminghub.poc.domain.InstallInfo
import com.unifiedstreaminghub.poc.domain.InstallState
import com.unifiedstreaminghub.poc.domain.LaunchOutcome
import com.unifiedstreaminghub.poc.domain.LogEntry
import com.unifiedstreaminghub.poc.domain.LogFormatter
import com.unifiedstreaminghub.poc.domain.Provider
import com.unifiedstreaminghub.poc.domain.ProviderLauncher
import java.util.Date

private const val TAB_PROVIDERS = 0
private const val TAB_LOG = 1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubApp() {
    val context = LocalContext.current
    val vm: ProviderTestViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ProviderTestViewModel(SharedPrefsUrlStore(context)) }
        },
    )
    val deviceInfo = remember { Diagnostics.deviceSummary() }
    // Bound to the current Activity context; recreated on configuration change.
    val launcher = remember(context) {
        ProviderLauncher(AndroidIntentGateway(context), deviceInfo, Diagnostics.logcat)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(TAB_PROVIDERS) }

    // The user may install/uninstall a provider app and come back: re-check every time we resume.
    LifecycleResumeEffect(launcher) {
        vm.refreshInstall(launcher)
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(title = { Text("Unified Streaming Hub") })
                PrimaryTabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == TAB_PROVIDERS, onClick = { tab = TAB_PROVIDERS }, text = { Text("Providers") })
                    Tab(
                        selected = tab == TAB_LOG,
                        onClick = { tab = TAB_LOG },
                        text = { Text("Log (${state.log.size})") },
                    )
                }
            }
        },
    ) { padding ->
        when (tab) {
            TAB_PROVIDERS -> ProvidersTab(Modifier.padding(padding), state, vm, launcher)
            else -> LogTab(Modifier.padding(padding), state.log, deviceInfo, vm::clearLog)
        }
    }
}

@Composable
private fun ProvidersTab(
    modifier: Modifier,
    state: ProviderTestState,
    vm: ProviderTestViewModel,
    launcher: ProviderLauncher,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Disclaimer() }
        items(Provider.entries, key = { it.name }) { provider ->
            val row = state.rows.getValue(provider)
            ProviderCard(
                provider = provider,
                url = row.url,
                install = row.install,
                detection = row.detection,
                last = state.lastTitleResult(provider),
                onUrlChange = { vm.setUrl(provider, it) },
                onDetect = { vm.detect(provider, launcher) },
                onLaunchApp = { vm.launchApp(provider, launcher) },
                onOpen = { vm.open(provider, launcher) },
            )
        }
    }
}

@Composable
private fun Disclaimer() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Text(
            "Paste an official title link copied from the provider's own site or app share sheet. " +
                "No deep-link scheme is built or guessed here. A successful launch only means Android " +
                "accepted the request; it does not prove the title opened or that playback started. " +
                "Check the phone screen yourself.",
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ProviderCard(
    provider: Provider,
    url: String,
    install: InstallInfo,
    detection: Detection?,
    last: LogEntry?,
    onUrlChange: (String) -> Unit,
    onDetect: () -> Unit,
    onLaunchApp: () -> Unit,
    onOpen: () -> Unit,
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(provider.displayName, style = MaterialTheme.typography.titleMedium)
                Text(install.state.label, style = MaterialTheme.typography.labelLarge, color = install.state.color())
            }
            Text(
                buildString {
                    append(provider.packageName)
                    install.versionName?.let { append("  ·  v$it") }
                },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
            install.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onLaunchApp,
                    enabled = install.state != InstallState.NOT_INSTALLED,
                ) { Text("Launch application") }
            }

            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Official title URL (https)") },
                placeholder = { Text("https://…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpen, enabled = url.isNotBlank()) { Text("Open specific title") }
                TextButton(onClick = onDetect, enabled = url.isNotBlank()) { Text("Check URL handlers") }
            }

            if (detection != null) DetectionView(detection)
            TitleLinkStatus(last, install)
        }
    }
}

/** Per-provider record of whether a title link worked on this device, from the latest attempt. */
@Composable
private fun TitleLinkStatus(last: LogEntry?, install: InstallInfo) {
    if (last == null) {
        Text(
            "Title link: not tested on this device yet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "Last title test: ${last.outcome.label}",
            style = MaterialTheme.typography.labelLarge,
            color = last.outcome.color(),
        )
        Text(last.summary, style = MaterialTheme.typography.bodySmall)
        if (last.outcome == LaunchOutcome.BROWSER_FALLBACK || last.outcome == LaunchOutcome.MISSING_APP) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Text(
                    "Title-specific launch is not supported for this link on this device" +
                        (if (install.state == InstallState.INSTALLED) " (the app is installed but did not take the URL)" else "") +
                        ". Fallback: use Launch application to open the app and find the title there.",
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
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
private fun LogTab(modifier: Modifier, log: List<LogEntry>, deviceInfo: String, onClear: () -> Unit) {
    val context = LocalContext.current
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Device: $deviceInfo", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { copyToClipboard(context, LogFormatter.format(deviceInfo, log)) },
                        enabled = log.isNotEmpty(),
                    ) { Text("Copy log") }
                    TextButton(onClick = onClear, enabled = log.isNotEmpty()) { Text("Clear") }
                }
                Text(
                    "Also in Logcat: adb logcat -s UnifiedHub",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        if (log.isEmpty()) {
            item { Text("No launches yet.", style = MaterialTheme.typography.bodyMedium) }
        }
        items(log) { LogCard(it) }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Unified Streaming Hub log", text))
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
                Text(
                    "${e.provider.displayName} · ${e.kind.label} · $time",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Text(e.summary, style = MaterialTheme.typography.bodyMedium)
            e.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            e.url?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            e.trail.forEach {
                Text(
                    "• $it",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

/** Fixed hues so classes stay distinguishable regardless of dynamic colour. */
private fun LaunchOutcome.color(): Color = when (this) {
    LaunchOutcome.APP_LAUNCH_REQUESTED -> Color(0xFF2E7D32)
    LaunchOutcome.BROWSER_FALLBACK -> Color(0xFFEF6C00)
    LaunchOutcome.MISSING_APP -> Color(0xFFC62828)
    LaunchOutcome.UNKNOWN -> Color(0xFF6A1B9A)
    LaunchOutcome.REJECTED_INPUT -> Color(0xFF546E7A)
}

private fun InstallState.color(): Color = when (this) {
    InstallState.INSTALLED -> Color(0xFF2E7D32)
    InstallState.NOT_INSTALLED -> Color(0xFFC62828)
    InstallState.UNKNOWN -> Color(0xFF6A1B9A)
}
