package com.defectview.app.feature.inspections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.defectview.app.R
import com.defectview.domain.model.Project
import com.defectview.domain.model.Trade

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewInspectionScreen(
    viewModel: InspectionViewModel,
    preselectedProjectId: Long?,
    capturedPhotoPath: String?,
    onCapturePhoto: () -> Unit,
    onImportPhoto: () -> Unit,
    onSaved: (inspectionId: Long) -> Unit,
    inspectorName: String = "Site Inspector"
) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    var selectedProject by remember { mutableStateOf<Project?>(null) }
    var projectMenuExpanded by remember { mutableStateOf(false) }
    var location by remember { mutableStateOf("") }
    var area by remember { mutableStateOf("") }
    var trade by remember { mutableStateOf(Trade.MASONRY) }
    var tradeMenuExpanded by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }

    LaunchedEffect(projects, preselectedProjectId) {
        if (selectedProject == null && preselectedProjectId != null) {
            selectedProject = projects.find { it.id == preselectedProjectId }
        }
    }

    val saveState by viewModel.saveState.collectAsStateWithLifecycle()
    LaunchedEffect(saveState) {
        val state = saveState
        if (state is InspectionViewModel.SaveState.Saved) {
            viewModel.resetSaveState()
            onSaved(state.inspectionId)
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.inspection_new)) }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ExposedDropdownMenuBox(expanded = projectMenuExpanded, onExpandedChange = { projectMenuExpanded = it }) {
                OutlinedTextField(
                    value = selectedProject?.let { "${it.projectNumber} — ${it.name}" } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.inspection_select_project)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = projectMenuExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                androidx.compose.material3.ExposedDropdownMenu(expanded = projectMenuExpanded, onDismissRequest = { projectMenuExpanded = false }) {
                    projects.forEach { project ->
                        DropdownMenuItem(
                            text = { Text("${project.projectNumber} — ${project.name}") },
                            onClick = {
                                selectedProject = project
                                projectMenuExpanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(location, { location = it }, label = { Text(stringResource(R.string.inspection_location)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(area, { area = it }, label = { Text(stringResource(R.string.inspection_area)) }, modifier = Modifier.fillMaxWidth())

            ExposedDropdownMenuBox(expanded = tradeMenuExpanded, onExpandedChange = { tradeMenuExpanded = it }) {
                OutlinedTextField(
                    value = trade.name.replace('_', ' '),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.inspection_trade)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = tradeMenuExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                androidx.compose.material3.ExposedDropdownMenu(expanded = tradeMenuExpanded, onDismissRequest = { tradeMenuExpanded = false }) {
                    Trade.entries.forEach { t ->
                        DropdownMenuItem(text = { Text(t.name.replace('_', ' ')) }, onClick = { trade = t; tradeMenuExpanded = false })
                    }
                }
            }

            OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.inspection_notes)) }, modifier = Modifier.fillMaxWidth(), minLines = 3)

            if (capturedPhotoPath != null) {
                AsyncImage(
                    model = capturedPhotoPath,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCapturePhoto, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.inspection_capture_photo))
                }
                OutlinedButton(onClick = onImportPhoto, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.inspection_import_photo))
                }
                if (capturedPhotoPath != null) {
                    val analysisState by viewModel.analysisState.collectAsStateWithLifecycle()
                    OutlinedButton(
                        onClick = { viewModel.analyzePhoto(capturedPhotoPath) },
                        enabled = analysisState !is InspectionViewModel.AnalysisState.Analyzing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.inspection_analyze))
                    }
                    AnalysisResults(analysisState)
                }
            }

            if (saveState is InspectionViewModel.SaveState.Error) {
                Text((saveState as InspectionViewModel.SaveState.Error).message, color = androidx.compose.material3.MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = {
                    viewModel.save(selectedProject?.id, location, area, trade, notes, inspectorName, analyzedPhotoPath = capturedPhotoPath)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

@Composable
private fun AnalysisResults(state: InspectionViewModel.AnalysisState) {
    when (state) {
        is InspectionViewModel.AnalysisState.Idle -> Unit
        is InspectionViewModel.AnalysisState.Analyzing -> Text(stringResource(R.string.loading))
        is InspectionViewModel.AnalysisState.Done -> {
            if (state.detections.isEmpty()) {
                Text("No irregular areas found by the local vision engine.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.detections.forEach { detection ->
                        val confidenceText = com.defectview.domain.confidence.ConfidenceClassifier.displayText(detection.confidenceScore)
                        androidx.compose.material3.Card {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("${stringResource(R.string.ai_suggestion_label)}: $confidenceText", style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                                Text(detection.evidence, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
