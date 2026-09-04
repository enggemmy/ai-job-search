package com.defectview.app.feature.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.defectview.app.R
import com.defectview.domain.model.Project

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectListScreen(
    viewModel: ProjectViewModel,
    onProjectClick: (Project) -> Unit,
    onNewProject: () -> Unit
) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.projects_title)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewProject) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.project_new))
            }
        }
    ) { padding ->
        if (projects.isEmpty()) {
            EmptyProjects(padding)
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(projects, key = { it.id }) { project ->
                    ProjectCard(project, onClick = { onProjectClick(project) })
                }
            }
        }
    }
}

@Composable
private fun EmptyProjects(padding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(stringResource(R.string.projects_empty))
    }
}

@Composable
private fun ProjectCard(project: Project, onClick: () -> Unit) {
    Card(onClick = onClick, elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(project.projectNumber, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
            Text(project.name, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            Text(project.client, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
            Text(project.location, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
        }
    }
}
