package com.defectview.app.feature.defects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.defectview.app.feature.editor.AnnotationDraft
import com.defectview.domain.model.Defect
import com.defectview.domain.model.DefectCategory
import com.defectview.domain.model.DefectPriority
import com.defectview.domain.model.DefectSeverity
import com.defectview.domain.model.Trade

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private inline fun <reified T : Enum<T>> EnumDropdown(
    label: String,
    selected: T,
    noinline onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.name.replace('_', ' '),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            enumValues<T>().forEach { value ->
                DropdownMenuItem(text = { Text(value.name.replace('_', ' ')) }, onClick = { onSelect(value); expanded = false })
            }
        }
    }
}

/**
 * Full Defect Record entry form (spec section 5D). Reached after the Defect View editor - the
 * annotations already drawn there are passed through unchanged and saved together with these
 * fields. AI-suggested severity, when present, is always labeled as a suggestion the inspector
 * must confirm (spec section 7) - see the severityIsAiSuggested flag threaded through to save().
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefectFormScreen(
    viewModel: DefectFormViewModel,
    projectId: Long,
    inspectionId: Long?,
    location: String,
    originalPhotoPath: String,
    annotations: List<AnnotationDraft>,
    reportedBy: String,
    onSaved: (Defect) -> Unit,
    onCancel: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(DefectCategory.WORKMANSHIP) }
    var trade by remember { mutableStateOf(Trade.MASONRY) }
    var severity by remember { mutableStateOf(DefectSeverity.MEDIUM) }
    var priority by remember { mutableStateOf(DefectPriority.NORMAL) }
    var recommendation by remember { mutableStateOf("Verify against the approved project specification and method statement.") }
    var responsibleParty by remember { mutableStateOf("") }
    var inspectorComments by remember { mutableStateOf("") }

    val saveState by viewModel.saveState.collectAsStateWithLifecycle()
    LaunchedEffect(saveState) {
        val state = saveState
        if (state is DefectFormViewModel.SaveState.Saved) {
            viewModel.resetSaveState()
            onSaved(state.defect)
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("New Defect") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Suggested — Inspector confirmation required", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)

            OutlinedTextField(title, { title = it }, label = { Text("Defect title") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(description, { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), minLines = 3)

            EnumDropdown("Category", category) { category = it }
            EnumDropdown("Trade", trade) { trade = it }
            EnumDropdown("Severity", severity) { severity = it }
            EnumDropdown("Priority", priority) { priority = it }

            OutlinedTextField(recommendation, { recommendation = it }, label = { Text("Recommendation") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            OutlinedTextField(responsibleParty, { responsibleParty = it }, label = { Text("Responsible party") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(inspectorComments, { inspectorComments = it }, label = { Text("Inspector comments") }, modifier = Modifier.fillMaxWidth(), minLines = 2)

            if (saveState is DefectFormViewModel.SaveState.Error) {
                Text((saveState as DefectFormViewModel.SaveState.Error).message, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = {
                    viewModel.create(
                        projectId = projectId,
                        inspectionId = inspectionId,
                        location = location,
                        originalPhotoPath = originalPhotoPath,
                        annotations = annotations,
                        title = title,
                        description = description,
                        category = category,
                        trade = trade,
                        severity = severity,
                        severityIsAiSuggested = false,
                        recommendation = recommendation,
                        responsibleParty = responsibleParty,
                        priority = priority,
                        reportedBy = reportedBy,
                        inspectorComments = inspectorComments
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Defect")
            }

            androidx.compose.material3.OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel")
            }
        }
    }
}
