package com.defectview.app.feature.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.defectview.app.R
import com.defectview.domain.model.Project

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectEditScreen(
    viewModel: ProjectViewModel,
    existingProjectId: Long?,
    onSaved: () -> Unit,
    onCancel: () -> Unit
) {
    var projectNumber by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var client by remember { mutableStateOf("") }
    var consultant by remember { mutableStateOf("") }
    var contractor by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    LaunchedEffect(existingProjectId) {
        if (existingProjectId != null) {
            viewModel.loadProject(existingProjectId) { project: Project? ->
                project?.let {
                    projectNumber = it.projectNumber
                    name = it.name
                    client = it.client
                    consultant = it.consultant
                    contractor = it.contractor
                    location = it.location
                    description = it.description
                }
            }
        }
    }

    val saveState by viewModel.saveState.collectAsStateWithLifecycle()
    LaunchedEffect(saveState) {
        if (saveState is ProjectViewModel.SaveState.Saved) {
            viewModel.resetSaveState()
            onSaved()
        }
    }

    val titleRes = if (existingProjectId == null) R.string.project_new else R.string.project_edit
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(titleRes)) }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(projectNumber, { projectNumber = it }, label = { Text(stringResource(R.string.project_number)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.project_name)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(client, { client = it }, label = { Text(stringResource(R.string.project_client)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(consultant, { consultant = it }, label = { Text(stringResource(R.string.project_consultant)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(contractor, { contractor = it }, label = { Text(stringResource(R.string.project_contractor)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(location, { location = it }, label = { Text(stringResource(R.string.project_location)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(description, { description = it }, label = { Text(stringResource(R.string.project_description)) }, modifier = Modifier.fillMaxWidth(), minLines = 3)

            if (saveState is ProjectViewModel.SaveState.Error) {
                Text((saveState as ProjectViewModel.SaveState.Error).message, color = androidx.compose.material3.MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = {
                    viewModel.save(existingProjectId, projectNumber, name, client, consultant, contractor, location, description)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}
