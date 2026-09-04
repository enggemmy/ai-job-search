package com.defectview.app.feature.defects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.defectview.app.ui.theme.SeverityCritical
import com.defectview.app.ui.theme.SeverityHigh
import com.defectview.app.ui.theme.SeverityLow
import com.defectview.app.ui.theme.SeverityMedium
import com.defectview.domain.model.Defect
import com.defectview.domain.model.DefectSeverity
import com.defectview.domain.model.DefectStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefectDetailScreen(
    viewModel: DefectDetailViewModel,
    onCaptureAfterPhoto: () -> Unit,
    pendingAfterPhotoPath: String?,
    onConsumePendingAfterPhoto: () -> Unit
) {
    val defect by viewModel.defect.collectAsStateWithLifecycle()
    var showCloseDialog by remember { mutableStateOf(false) }

    LaunchedEffect(pendingAfterPhotoPath) {
        if (pendingAfterPhotoPath != null) {
            viewModel.setAfterPhoto(pendingAfterPhotoPath)
            onConsumePendingAfterPhoto()
        }
    }

    val current = defect
    Scaffold(topBar = { TopAppBar(title = { Text(current?.defectId ?: "Defect") }) }) { padding ->
        if (current == null) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                Text("Loading…")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text(current.severity.name) }, colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(labelColor = severityColor(current.severity)))
                AssistChip(onClick = {}, label = { Text(current.status.name.replace('_', ' ')) })
            }
            if (current.severityIsAiSuggested) {
                Text("Suggested — Inspector confirmation required", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
            }

            Text(current.title, style = MaterialTheme.typography.titleLarge)
            Text(current.description, style = MaterialTheme.typography.bodyLarge)

            Card { Column(modifier = Modifier.padding(12.dp)) {
                LabeledValue("Location", current.location)
                LabeledValue("Category", current.category.name)
                LabeledValue("Trade", current.trade.name.replace('_', ' '))
                LabeledValue("Recommendation", current.recommendation)
                LabeledValue("Responsible party", current.responsibleParty.ifBlank { "—" })
                LabeledValue("Reported by", current.reportedBy)
            } }

            Text("Before / After", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PhotoSlot("Before", current.beforePhotoPath, Modifier.weight(1f))
                PhotoSlot("After", current.afterPhotoPath, Modifier.weight(1f))
            }
            if (current.afterPhotoPath == null) {
                OutlinedButton(onClick = onCaptureAfterPhoto, modifier = Modifier.fillMaxWidth()) {
                    Text("Capture rectification photo")
                }
            }

            if (current.closureComments.isNotBlank()) {
                Card { Column(modifier = Modifier.padding(12.dp)) {
                    Text("Closure comments", style = MaterialTheme.typography.titleMedium)
                    Text(current.closureComments)
                } }
            }

            Text("Status", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (current.status != DefectStatus.IN_PROGRESS) {
                    OutlinedButton(onClick = { viewModel.transitionStatus(DefectStatus.IN_PROGRESS) }) { Text("In Progress") }
                }
                if (current.status != DefectStatus.PENDING_VERIFICATION) {
                    OutlinedButton(onClick = { viewModel.transitionStatus(DefectStatus.PENDING_VERIFICATION) }) { Text("Pending Verification") }
                }
                if (current.status != DefectStatus.REJECTED) {
                    OutlinedButton(onClick = { viewModel.transitionStatus(DefectStatus.REJECTED) }) { Text("Reject") }
                }
            }
            if (current.status != DefectStatus.CLOSED) {
                Button(onClick = { showCloseDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Verify & Close")
                }
            }
        }
    }

    if (showCloseDialog) {
        CloseDefectDialog(
            onConfirm = { comments ->
                viewModel.verifyAndClose(comments)
                showCloseDialog = false
            },
            onDismiss = { showCloseDialog = false }
        )
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PhotoSlot(label: String, path: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(4.dp))
        if (path != null) {
            AsyncImage(
                model = path,
                contentDescription = label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(8.dp))
            )
        } else {
            Card(modifier = Modifier.fillMaxWidth().height(140.dp)) {
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    Text("Not captured yet", modifier = Modifier.padding(8.dp))
                }
            }
        }
    }
}

@Composable
private fun CloseDefectDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var comments by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Verify & close this defect?") },
        text = {
            Column {
                Text("This confirms the rectification has been verified.")
                OutlinedTextField(comments, { comments = it }, label = { Text("Closure comments") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { onConfirm(comments) }) { Text("Close Defect") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun severityColor(severity: DefectSeverity) = when (severity) {
    DefectSeverity.CRITICAL -> SeverityCritical
    DefectSeverity.HIGH -> SeverityHigh
    DefectSeverity.MEDIUM -> SeverityMedium
    DefectSeverity.LOW -> SeverityLow
}
