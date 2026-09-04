package com.defectview.app.ui.navigation

sealed class Destination(val route: String) {
    data object Dashboard : Destination("dashboard")
    data object Projects : Destination("projects")
    data object ProjectEdit : Destination("projects/edit?projectId={projectId}") {
        fun route(projectId: Long? = null) = "projects/edit?projectId=${projectId ?: -1}"
    }
    data object Inspections : Destination("inspections")
    data object InspectionsForProject : Destination("projects/{projectId}/inspections") {
        fun route(projectId: Long) = "projects/$projectId/inspections"
    }
    data object NewInspection : Destination("inspections/new?projectId={projectId}") {
        fun route(projectId: Long) = "inspections/new?projectId=$projectId"
    }
    data object CameraCapture : Destination("camera")
    data object DefectEditor : Destination("defect_editor")
    data object DefectForm : Destination("defect_form")
    data object DefectDetail : Destination("defect_detail/{defectId}") {
        fun route(defectId: Long) = "defect_detail/$defectId"
    }
    data object Defects : Destination("defects")
    data object LearningCenter : Destination("learning_center")
    data object Reports : Destination("reports")
    data object Settings : Destination("settings")
}
