package com.defectview.app.feature.learning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.defectview.app.data.db.entity.LearningQueueEntity
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearningCenterScreen(viewModel: LearningCenterViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Learning Center") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Verified examples", style = MaterialTheme.typography.titleMedium)
                    Text("${state.totalVerifiedExamples} total — ${state.approvedCount} approved as-is, ${state.correctedCount} corrected by an inspector")
                    if (state.examplesByTrade.isNotEmpty()) {
                        Text("By trade:", style = MaterialTheme.typography.labelLarge)
                        state.examplesByTrade.forEach { (trade, count) -> Text("  $trade: $count") }
                    }
                }
            }

            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Model version", style = MaterialTheme.typography.titleMedium)
                    val active = state.activeVersion
                    if (active == null) {
                        Text("No learning update has run yet - the app is using only the built-in taxonomy and classical-CV heuristics.")
                    } else {
                        Text("Active: ${active.versionLabel} — ${active.trainingExampleCount} verified example(s)")
                        Text("Last update: ${DateFormat.getDateTimeInstance().format(Date(active.createdAt))}")
                    }
                    Text(
                        "\"Run local learning update\" consolidates newly verified examples into the similarity-search dataset. It does not retrain a neural network - there isn't one in this app yet (spec section 8).",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = { viewModel.runLocalLearningUpdate() },
                        enabled = state.queuedForTraining.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Run local learning update (${state.queuedForTraining.size} queued)")
                    }
                }
            }

            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Pending verification queue", style = MaterialTheme.typography.titleMedium)
                    if (state.pendingQueue.isEmpty()) {
                        Text("Nothing pending.")
                    } else {
                        state.pendingQueue.forEach { entry ->
                            QueueRow(entry, onQueue = { viewModel.queueForTraining(entry) }, onDiscard = { viewModel.discard(entry) })
                        }
                    }
                }
            }

            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Learning history", style = MaterialTheme.typography.titleMedium)
                    if (state.modelVersions.isEmpty()) {
                        Text("No versions recorded yet.")
                    } else {
                        state.modelVersions.forEach { version ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("${version.versionLabel}${if (version.isActive) " (active)" else ""}")
                                    Text(version.notes, style = MaterialTheme.typography.bodyMedium)
                                }
                                if (!version.isActive) {
                                    OutlinedButton(onClick = { viewModel.rollbackTo(version.id) }) { Text("Roll back") }
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueRow(entry: LearningQueueEntity, onQueue: () -> Unit, onDiscard: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Verified example #${entry.verifiedExampleId}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onQueue) { Text("Queue") }
            OutlinedButton(onClick = onDiscard) { Text("Discard") }
        }
    }
}
