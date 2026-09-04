package com.defectview.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
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
import com.defectview.app.feature.defects.DefectListScreen
import com.defectview.app.feature.defects.DefectListViewModel
import com.defectview.app.feature.inspections.InspectionListScreen
import com.defectview.app.feature.inspections.InspectionViewModel
import com.defectview.app.feature.inspections.NewInspectionScreen
import com.defectview.app.feature.projects.ProjectEditScreen
import com.defectview.app.feature.projects.ProjectListScreen
import com.defectview.app.feature.projects.ProjectViewModel
import kotlinx.coroutines.launch

private val bottomDestinations = listOf(
    Destination.Dashboard to (Icons.Filled.Dashboard to "Dashboard"),
    Destination.Projects to (Icons.Filled.Folder to "Projects"),
    Destination.Defects to (Icons.Filled.ReportProblem to "Defects"),
    Destination.LearningCenter to (Icons.Filled.School to "Learning"),
    Destination.Reports to (Icons.Filled.Assessment to "Reports"),
    Destination.Settings to (Icons.Filled.Settings to "Settings")
)

@Composable
fun DefectViewNavHost(container: AppContainer) {
    val navController = rememberNavController()
    var capturedPhotoPath by remember { mutableStateOf<String?>(null) }

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
                arguments = listOf(navArgument("projectId") { defaultValue = -1L; type = androidx.navigation.NavType.LongType })
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
                arguments = listOf(navArgument("projectId") { type = androidx.navigation.NavType.LongType })
            ) { entry ->
                val projectId = entry.arguments?.getLong("projectId") ?: return@composable
                val vm: InspectionViewModel = viewModel(
                    factory = InspectionViewModel.Factory(container.inspectionRepository, container.projectRepository)
                )
                InspectionListScreen(
                    viewModel = vm,
                    projectId = projectId,
                    onInspectionClick = { /* Inspection detail / Defect View editor: Phase 2 */ },
                    onNewInspection = { navController.navigate(Destination.NewInspection.route(projectId)) }
                )
            }

            composable(
                Destination.NewInspection.route,
                arguments = listOf(navArgument("projectId") { defaultValue = -1L; type = androidx.navigation.NavType.LongType })
            ) { entry ->
                val projectId = entry.arguments?.getLong("projectId")?.takeIf { it >= 0 }
                val vm: InspectionViewModel = viewModel(
                    factory = InspectionViewModel.Factory(container.inspectionRepository, container.projectRepository)
                )
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                NewInspectionScreen(
                    viewModel = vm,
                    preselectedProjectId = projectId,
                    capturedPhotoPath = capturedPhotoPath,
                    onCapturePhoto = { navController.navigate(Destination.CameraCapture.route) },
                    onImportPhoto = { /* System photo picker wiring: Phase 2 */ },
                    onSaved = { inspectionId ->
                        capturedPhotoPath?.let { path ->
                            scope.launch { container.attachmentRepository.attachToInspection(inspectionId, path) }
                        }
                        capturedPhotoPath = null
                        navController.popBackStack()
                    }
                )
            }

            composable(Destination.CameraCapture.route) {
                CameraCaptureScreen(
                    imageStorageManager = container.imageStorageManager,
                    onCaptured = { path ->
                        capturedPhotoPath = path
                        navController.popBackStack()
                    },
                    onCancel = { navController.popBackStack() }
                )
            }

            composable(Destination.Defects.route) {
                val vm: DefectListViewModel = viewModel(factory = DefectListViewModel.Factory(container.defectRepository))
                DefectListScreen(vm, onDefectClick = { /* Defect detail / Defect View editor: Phase 2 */ })
            }

            composable(Destination.LearningCenter.route) {
                NotYetImplementedScreen(
                    title = "Learning Center",
                    phaseNote = "The verified-example learning workflow (approve/correct/reject, similarity search, model versioning) is implemented in the domain layer and lands in the app UI in Phase 5."
                )
            }

            composable(Destination.Reports.route) {
                NotYetImplementedScreen(
                    title = "Reports",
                    phaseNote = "PDF report generation (Defect View, Daily Inspection, Open Defects, Defect Register, Before/After, Quality Summary) is planned for Phase 6."
                )
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
