package com.defectview.app.feature.inspections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.defectview.app.R
import com.defectview.domain.model.Inspection
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectionListScreen(
    viewModel: InspectionViewModel,
    projectId: Long,
    onInspectionClick: (Inspection) -> Unit,
    onNewInspection: () -> Unit
) {
    var inspections by remember { mutableStateOf<List<Inspection>>(emptyList()) }
    LaunchedEffect(projectId) {
        viewModel.inspectionsForProject(projectId).collect { inspections = it }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.inspections_title)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewInspection) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.inspection_new))
            }
        }
    ) { padding ->
        if (inspections.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.inspections_empty))
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(inspections, key = { it.id }) { inspection ->
                    Card(onClick = { onInspectionClick(inspection) }) {
                        androidx.compose.foundation.layout.Column(modifier = Modifier.padding(16.dp)) {
                            Text(inspection.location, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${inspection.trade.name.replace('_', ' ')} · ${inspection.area}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                                    .format(Date(inspection.createdAt)),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}
