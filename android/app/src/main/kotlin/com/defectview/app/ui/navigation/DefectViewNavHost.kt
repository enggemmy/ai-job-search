package com.defectview.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.defectview.app.di.AppContainer
import com.defectview.app.feature.camera.CameraCaptureScreen
import com.defectview.app.feature.common.NotYetImplementedScreen
import com.defectview.app.feature.dashboard.DashboardScreen
import com.defectview.app.feature.dashboard.DashboardViewModel
import com.defectview.app.feature.defects.DefectDetailScreen
import com.defectview.app.feature.defects.DefectDetailViewModel
import com.defectview.app.feature.defects.DefectFormScreen
import com.defectview.app.feature.defects.DefectFormViewModel
import com.defectview.app.feature.defects.DefectListScreen
import com.defectview.app.feature.defects.DefectListViewModel
import com.defectview.app.feature.editor.AnnotationDraft
import com.defectview.app.feature.editor.AnnotationEditorViewModel
import com.defectview.app.feature.editor.DefectViewEditorScreen
import com.defectview.app.feature.editor.toSeedAnnotationDrafts
import com.defectview.app.feature.inspections.InspectionListScreen
import com.defectview.app.feature.learning.LearningCenterScreen
import com.defectview.app.feature.learning.LearningCenterViewModel
import com.defectview.app.feature.inspections.InspectionViewModel
import com.defectview.app.feature.inspections.NewInspectionScreen
import com.defectview.app.feature.projects.ProjectEditScreen
import com.defectview.app.feature.projects.ProjectListScreen
import com.defectview.app.feature.projects.ProjectViewModel
import com.defectview.app.feature.reportcenter.ReportsScreen
import com.defectview.app.feature.reportcenter.ReportsViewModel
import com.defectview.domain.model.DefectDetection
import com.defectview.domain.reasoning.ReasoningResult
import kotlinx.coroutines.launch

private val bottomDestinations = listOf(
    Destination.Dashboard to (Icons.Filled.Dashboard to "Dashboard"),
    Destination.Projects to (Icons.Filled.Folder to "Projects"),
    Destination.Defects to (Icons.Filled.ReportProblem to "Defects"),
    Destination.LearningCenter to (Icons.Filled.School to "Learning"),
    Destination.Reports to (Icons.Filled.Assessment to "Reports"),
    Destination.Settings to (Icons.Filled.Settings to "Settings")
)

/** Which in-flight flow the camera screen's result should be routed back to. */
private sealed class CaptureTarget {
    data object NewInspectionPhoto : CaptureTarget()
    data object DefectAfterPhoto : CaptureTarget()
}

/** The photo + context carried from "capture/save inspection" through the editor into the defect form. */
private data class DefectDraftContext(val projectId: Long, val inspectionId: Long?, val location: String, val photoPath: String)

/** Whether the (project-knowledge-aware) reasoning result for the Defect Form's prefill has
 * finished loading yet - see the DefectForm composable below for why this can't just be a
 * `remember` computed synchronously. */
private sealed class ReasoningLoadState {
    data object Loading : ReasoningLoadState()
    data class Ready(val reasoning: ReasoningResult?) : ReasoningLoadState()
}

private const val DEFAULT_INSPECTOR_NAME = "Site Inspector"

@Composable
fun DefectViewNavHost(container: AppContainer) {
    val navController = rememberNavController()
    var capturedPhotoPath by remember { mutableStateOf<String?>(null) }
    var captureTarget by remember { mutableStateOf<CaptureTarget>(CaptureTarget.NewInspectionPhoto) }
    var draftContext by remember { mutableStateOf<DefectDraftContext?>(null) }
    var draftAnnotations by remember { mutableStateOf<List<AnnotationDraft>>(emptyList()) }
    var draftDetections by remember { mutableStateOf<List<DefectDetection>>(emptyList()) }
    var pendingAfterPhotoPath by remember { mutableStateOf<String?>(null) }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            val isTopLevel = bottomDestinations.any { (dest, _) -> currentRoute?.hierarchy?.any { it.route == dest.route } == true }
            if (isTopLevel) {
                NavigationBar {
                    bottomDestinations.forEach { (destination, iconLabel) ->
                        val (icon, label) = iconLabel
                        NavigationBarItem(
                            selected = currentRoute?.hierarchy?.any { it.route == destination.route } == true,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { androidx.compose.material3.Icon(icon, contentDescription = label) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Dashboard.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Destination.Dashboard.route) {
                val vm: DashboardViewModel = viewModel(
                    factory = DashboardViewModel.Factory(container.projectRepository, container.inspectionRepository, container.defectRepository)
                )
                DashboardScreen(vm)
            }

            composable(Destination.Projects.route) {
                val vm: ProjectViewModel = viewModel(factory = ProjectViewModel.Factory(container.projectRepository))
                ProjectListScreen(
                    viewModel = vm,
                    onProjectClick = { project -> navController.navigate(Destination.InspectionsForProject.route(project.id)) },
                    onNewProject = { navController.navigate(Destination.ProjectEdit.route()) }
                )
            }

            composable(
                Destination.ProjectEdit.route,
                arguments = listOf(navArgument("projectId") { defaultValue = -1L; type = NavType.LongType })
            ) { entry ->
                val projectId = entry.arguments?.getLong("projectId")?.takeIf { it >= 0 }
                val vm: ProjectViewModel = viewModel(factory = ProjectViewModel.Factory(container.projectRepository))
                ProjectEditScreen(
                    viewModel = vm,
                    existingProjectId = projectId,
                    onSaved = { navController.popBackStack() },
                    onCancel = { navController.popBackStack() }
                )
            }

            composable(
                Destination.InspectionsForProject.route,
                arguments = listOf(navArgument("projectId") { type = NavType.LongType })
            ) { entry ->
                val projectId = entry.arguments?.getLong("projectId") ?: return@composable
                val vm: InspectionViewModel = viewModel(
                    factory = InspectionViewModel.Factory(container.inspectionRepository, container.projectRepository, container.visionEngine, container.aiAnalysisRepository)
                )
                InspectionListScreen(
                    viewModel = vm,
                    projectId = projectId,
                    onInspectionClick = { /* Revisiting a past inspection to add more defects: not yet implemented (Phase 2 gap - see README). */ },
                    onNewInspection = { navController.navigate(Destination.NewInspection.route(projectId)) }
                )
            }

            composable(
                Destination.NewInspection.route,
                arguments = listOf(navArgument("projectId") { defaultValue = -1L; type = NavType.LongType })
            ) { entry ->
                val projectId = entry.arguments?.getLong("projectId")?.takeIf { it >= 0 }
                val vm: InspectionViewModel = viewModel(
                    factory = InspectionViewModel.Factory(container.inspectionRepository, container.projectRepository, container.visionEngine, container.aiAnalysisRepository)
                )
                val scope = rememberCoroutineScope()
                NewInspectionScreen(
                    viewModel = vm,
                    preselectedProjectId = projectId,
                    capturedPhotoPath = capturedPhotoPath,
                    onCapturePhoto = {
                        captureTarget = CaptureTarget.NewInspectionPhoto
                        navController.navigate(Destination.CameraCapture.route)
                    },
                    onImportPhoto = { /* System photo picker wiring: Phase 2 follow-up */ },
                    onSaved = { inspectionId ->
                        val photo = capturedPhotoPath
                        if (photo != null) {
                            scope.launch { container.attachmentRepository.attachToInspection(inspectionId, photo) }
                            draftContext = DefectDraftContext(
                                projectId = projectId ?: 0,
                                inspectionId = inspectionId,
                                location = "",
                                photoPath = photo
                            )
                            draftDetections = (vm.analysisState.value as? InspectionViewModel.AnalysisState.Done)?.detections.orEmpty()
                            capturedPhotoPath = null
                            navController.navigate(Destination.DefectEditor.route) {
                                popUpTo(Destination.InspectionsForProject.route(projectId ?: 0))
                            }
                        } else {
                            capturedPhotoPath = null
                            navController.popBackStack()
                        }
                    }
                )
            }

            composable(Destination.CameraCapture.route) {
                CameraCaptureScreen(
                    imageStorageManager = container.imageStorageManager,
                    onCaptured = { path ->
                        when (captureTarget) {
                            CaptureTarget.NewInspectionPhoto -> capturedPhotoPath = path
                            CaptureTarget.DefectAfterPhoto -> pendingAfterPhotoPath = path
                        }
                        navController.popBackStack()
                    },
                    onCancel = { navController.popBackStack() }
                )
            }

            composable(Destination.DefectEditor.route) {
                val context = draftContext
                if (context == null) {
                    navController.popBackStack()
                } else {
                    val initialAnnotations = draftAnnotations.ifEmpty { draftDetections.toSeedAnnotationDrafts() }
                    val vm: AnnotationEditorViewModel = viewModel(factory = AnnotationEditorViewModel.Factory(initialAnnotations))
                    DefectViewEditorScreen(
                        imagePath = context.photoPath,
                        viewModel = vm,
                        onDone = {
                            draftAnnotations = vm.annotationsForSave()
                            navController.navigate(Destination.DefectForm.route)
                        },
                        onCancel = {
                            draftContext = null
                            draftAnnotations = emptyList()
                            draftDetections = emptyList()
                            navController.popBackStack()
                        }
                    )
                }
            }

            composable(Destination.DefectForm.route) {
                val context = draftContext
                if (context == null) {
                    navController.popBackStack()
                } else {
                    val vm: DefectFormViewModel = viewModel(
                        factory = DefectFormViewModel.Factory(
                            container.defectRepository,
                            container.annotationRepository,
                            container.attachmentRepository,
                            container.imageStorageManager,
                            container.verifiedExampleRepository,
                            container.visionEngine.info.modelVersion
                        )
                    )
                    val topDetection = draftDetections.maxByOrNull { it.confidenceScore }
                    // Loads project knowledge (if any) before the form's fields are first
                    // composed, rather than after - DefectFormScreen seeds its text fields once
                    // from initialReasoning, so recomputing it post-composition wouldn't refresh
                    // an already-typed default. Skipped entirely when there's no AI detection to
                    // reason about, so a manual (no-AI) defect never waits on a DB read.
                    val reasoningState by produceState<ReasoningLoadState>(
                        initialValue = if (topDetection == null) ReasoningLoadState.Ready(null) else ReasoningLoadState.Loading,
                        key1 = topDetection,
                        key2 = context.projectId
                    ) {
                        if (topDetection != null) {
                            val knowledge = container.projectKnowledgeRepository.loadForReasoning(context.projectId)
                            value = ReasoningLoadState.Ready(container.reasoningEngine.reason(topDetection, knowledge))
                        }
                    }

                    when (val state = reasoningState) {
                        is ReasoningLoadState.Loading -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        is ReasoningLoadState.Ready -> {
                            DefectFormScreen(
                                viewModel = vm,
                                projectId = context.projectId,
                                inspectionId = context.inspectionId,
                                location = context.location,
                                originalPhotoPath = context.photoPath,
                                annotations = draftAnnotations,
                                reportedBy = DEFAULT_INSPECTOR_NAME,
                                initialReasoning = state.reasoning,
                                sourceDetection = topDetection,
                                onSaved = { defect ->
                                    draftContext = null
                                    draftAnnotations = emptyList()
                                    draftDetections = emptyList()
                                    navController.navigate(Destination.DefectDetail.route(defect.id)) {
                                        popUpTo(Destination.Dashboard.route)
                                    }
                                },
                                onCancel = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }

            composable(
                Destination.DefectDetail.route,
                arguments = listOf(navArgument("defectId") { type = NavType.LongType })
            ) { entry ->
                val defectId = entry.arguments?.getLong("defectId") ?: return@composable
                val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
                val vm: DefectDetailViewModel = viewModel(
                    factory = DefectDetailViewModel.Factory(
                        defectId,
                        container.defectRepository,
                        container.projectRepository,
                        container.attachmentRepository,
                        appContext
                    )
                )
                DefectDetailScreen(
                    viewModel = vm,
                    onCaptureAfterPhoto = {
                        captureTarget = CaptureTarget.DefectAfterPhoto
                        navController.navigate(Destination.CameraCapture.route)
                    },
                    pendingAfterPhotoPath = pendingAfterPhotoPath,
                    onConsumePendingAfterPhoto = { pendingAfterPhotoPath = null }
                )
            }

            composable(Destination.Defects.route) {
                val vm: DefectListViewModel = viewModel(factory = DefectListViewModel.Factory(container.defectRepository))
                DefectListScreen(vm, onDefectClick = { defect -> navController.navigate(Destination.DefectDetail.route(defect.id)) })
            }

            composable(Destination.LearningCenter.route) {
                val vm: LearningCenterViewModel = viewModel(
                    factory = LearningCenterViewModel.Factory(
                        container.verifiedExampleRepository,
                        container.learningQueueRepository,
                        container.modelVersionRepository
                    )
                )
                LearningCenterScreen(vm)
            }

            composable(Destination.Reports.route) {
                val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
                val vm: ReportsViewModel = viewModel(
                    factory = ReportsViewModel.Factory(container.projectRepository, container.defectRepository, appContext)
                )
                ReportsScreen(vm)
            }

            composable(Destination.Settings.route) {
                NotYetImplementedScreen(
                    title = "Settings",
                    phaseNote = "Project knowledge base, language, and model-version settings land alongside their owning features in later phases."
                )
            }
        }
    }
}
