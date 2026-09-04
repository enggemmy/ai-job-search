package com.defectview.app.feature.defects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.defectview.app.ui.theme.SeverityCritical
import com.defectview.app.ui.theme.SeverityHigh
import com.defectview.app.ui.theme.SeverityLow
import com.defectview.app.ui.theme.SeverityMedium
import com.defectview.domain.model.Defect
import com.defectview.domain.model.DefectSeverity
import com.defectview.domain.model.DefectStatus

private fun severityColor(severity: DefectSeverity) = when (severity) {
    DefectSeverity.CRITICAL -> SeverityCritical
    DefectSeverity.HIGH -> SeverityHigh
    DefectSeverity.MEDIUM -> SeverityMedium
    DefectSeverity.LOW -> SeverityLow
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefectListScreen(viewModel: DefectListViewModel, onDefectClick: (Defect) -> Unit) {
    val defects by viewModel.defects.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_defects)) }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = filter.query,
                onValueChange = { viewModel.setQuery(it) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                label = { Text("Search defects") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(DefectStatus.entries.toList()) { status ->
                    FilterChip(
                        selected = filter.status == status,
                        onClick = { viewModel.setStatusFilter(if (filter.status == status) null else status) },
                        label = { Text(status.name.replace('_', ' ')) }
                    )
                }
            }

            if (defects.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (filter.query.isBlank() && filter.status == null) {
                            "No defects recorded yet. Defects are created from the Defect View editor after an inspection photo is captured and reviewed."
                        } else {
                            "No defects match this search/filter."
                        }
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(defects, key = { it.id }) { defect ->
                        Card(onClick = { onDefectClick(defect) }, modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(severityColor(defect.severity), CircleShape)
                                )
                                Column(modifier = Modifier.padding(start = 12.dp)) {
                                    Text(defect.defectId, style = MaterialTheme.typography.labelLarge)
                                    Text(defect.title, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "${defect.status.name.replace('_', ' ')} · ${defect.location}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
