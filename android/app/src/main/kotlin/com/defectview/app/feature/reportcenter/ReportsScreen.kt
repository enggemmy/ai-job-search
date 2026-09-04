package com.defectview.app.feature.reportcenter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.defectview.domain.model.Project
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(viewModel: ReportsViewModel) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val reportState by viewModel.reportState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(topBar = { TopAppBar(title = { Text("Reports") }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                "Defect Register — one tabular PDF per project, generated from the live defect table. " +
                    "Individual Defect View PDFs are generated from a defect's own detail screen. " +
                    "Daily Inspection, Open Defects, Before/After, and Quality Summary reports are not implemented yet.",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium
            )

            when (val state = reportState) {
                is ReportsViewModel.ReportState.Generating -> Text("Generating…", modifier = Modifier.padding(horizontal = 16.dp))
                is ReportsViewModel.ReportState.Ready -> {
                    Row(modifier = Modifier.padding(16.dp)) {
                        Button(onClick = {
                            val file = File(state.filePath)
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, "Share Defect Register"))
                            viewModel.resetReportState()
                        }) { Text("Share generated PDF") }
                    }
                }
                is ReportsViewModel.ReportState.Error -> Text(state.message, modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error)
                is ReportsViewModel.ReportState.Idle -> Unit
            }

            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(projects, key = { it.id }) { project: Project ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(project.name, style = MaterialTheme.typography.titleMedium)
                            Text(project.projectNumber, style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = { viewModel.generateDefectRegister(project) }, modifier = Modifier.fillMaxWidth()) {
                                Text("Generate Defect Register")
                            }
                        }
                    }
                }
            }
        }
    }
}
