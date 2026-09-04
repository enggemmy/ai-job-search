package com.defectview.app.feature.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.defectview.domain.annotation.AnnotationGeometry
import com.defectview.domain.annotation.Corner
import com.defectview.domain.model.AnnotationType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

enum class EditorTool { SELECT, CIRCLE, RECTANGLE, ARROW, FREEHAND, TEXT_CALLOUT }

/** An annotation being edited, before it has a real Room row id. [localKey] is stable across
 * edits so Compose can key list items and drag state correctly. */
data class AnnotationDraft(
    val localKey: Long,
    val type: AnnotationType,
    val label: String = "",
    val colorArgb: Long,
    val points: List<Pair<Float, Float>>,
    val strokeWidth: Float = 4f
)

val DEFAULT_ANNOTATION_COLORS = listOf(
    0xFFB3261EL, // critical red
    0xFFE4572EL, // safety orange
    0xFFC79100L, // amber
    0xFF3F7D3FL, // green
    0xFF3A6EA5L, // blue
    0xFF13181FL  // near-black
)

data class EditorUiState(
    val annotations: List<AnnotationDraft> = emptyList(),
    val selectedTool: EditorTool = EditorTool.SELECT,
    val selectedColor: Long = DEFAULT_ANNOTATION_COLORS.first(),
    val selectedAnnotationKey: Long? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false
)

/**
 * Holds the in-progress annotation set for one defect photo. Nothing is persisted to Room until
 * the caller reads [annotationsForSave] and writes them alongside the Defect record - the
 * editor itself is pure in-memory state plus undo/redo history (spec section 5C: undo/redo,
 * editable AI locations, multiple defects per photo).
 */
class AnnotationEditorViewModel(initialAnnotations: List<AnnotationDraft> = emptyList()) : ViewModel() {

    private val keyGenerator = AtomicLong(initialAnnotations.maxOfOrNull { it.localKey }?.plus(1) ?: 0)

    private val undoStack = ArrayDeque<List<AnnotationDraft>>()
    private val redoStack = ArrayDeque<List<AnnotationDraft>>()

    private val _state = MutableStateFlow(EditorUiState(annotations = initialAnnotations))
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    fun selectTool(tool: EditorTool) {
        _state.update { it.copy(selectedTool = tool, selectedAnnotationKey = null) }
    }

    fun selectColor(colorArgb: Long) {
        _state.update { current ->
            val selectedKey = current.selectedAnnotationKey
            if (selectedKey != null) {
                pushUndoSnapshot()
                current.copy(
                    selectedColor = colorArgb,
                    annotations = current.annotations.map { if (it.localKey == selectedKey) it.copy(colorArgb = colorArgb) else it }
                )
            } else {
                current.copy(selectedColor = colorArgb)
            }
        }
    }

    /** Commits a freshly drawn shape (called when the user lifts their finger). */
    fun commitNewAnnotation(type: AnnotationType, points: List<Pair<Float, Float>>, label: String = "") {
        if (points.isEmpty()) return
        pushUndoSnapshot()
        val draft = AnnotationDraft(
            localKey = keyGenerator.getAndIncrement(),
            type = type,
            label = label,
            colorArgb = _state.value.selectedColor,
            points = AnnotationGeometry.clampToUnitSquare(points)
        )
        _state.update { it.copy(annotations = it.annotations + draft, selectedAnnotationKey = draft.localKey) }
    }

    fun selectAnnotationAt(x: Float, y: Float) {
        val hit = _state.value.annotations.lastOrNull { AnnotationGeometry.hitTest(it.type, it.points, x, y) }
        _state.update { it.copy(selectedAnnotationKey = hit?.localKey) }
    }

    fun moveSelected(dx: Float, dy: Float) {
        val key = _state.value.selectedAnnotationKey ?: return
        _state.update { current ->
            current.copy(
                annotations = current.annotations.map {
                    if (it.localKey == key) it.copy(points = AnnotationGeometry.clampToUnitSquare(AnnotationGeometry.translate(it.points, dx, dy))) else it
                }
            )
        }
    }

    /** Call once when a move/resize gesture ends, so it becomes a single undo step. */
    fun commitTransform(beforeSnapshot: List<AnnotationDraft>) {
        undoStack.addLast(beforeSnapshot)
        redoStack.clear()
        syncUndoRedoFlags()
    }

    fun resizeSelected(corner: Corner, x: Float, y: Float) {
        val key = _state.value.selectedAnnotationKey ?: return
        _state.update { current ->
            current.copy(
                annotations = current.annotations.map {
                    if (it.localKey == key) it.copy(points = AnnotationGeometry.clampToUnitSquare(AnnotationGeometry.resizeCorner(it.points, corner, x, y))) else it
                }
            )
        }
    }

    fun deleteSelected() {
        val key = _state.value.selectedAnnotationKey ?: return
        pushUndoSnapshot()
        _state.update { it.copy(annotations = it.annotations.filterNot { a -> a.localKey == key }, selectedAnnotationKey = null) }
    }

    fun currentSnapshot(): List<AnnotationDraft> = _state.value.annotations

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(_state.value.annotations)
        _state.update { it.copy(annotations = previous, selectedAnnotationKey = null) }
        syncUndoRedoFlags()
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(_state.value.annotations)
        _state.update { it.copy(annotations = next, selectedAnnotationKey = null) }
        syncUndoRedoFlags()
    }

    private fun pushUndoSnapshot() {
        undoStack.addLast(_state.value.annotations)
        redoStack.clear()
        syncUndoRedoFlags()
    }

    private fun syncUndoRedoFlags() {
        _state.update { it.copy(canUndo = undoStack.isNotEmpty(), canRedo = redoStack.isNotEmpty()) }
    }

    fun annotationsForSave(): List<AnnotationDraft> = _state.value.annotations

    class Factory(private val initialAnnotations: List<AnnotationDraft> = emptyList()) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AnnotationEditorViewModel(initialAnnotations) as T
    }
}
