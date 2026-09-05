package com.defectview.app.feature.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.defectview.domain.annotation.AnnotationGeometry
import com.defectview.domain.annotation.Corner
import com.defectview.domain.model.AnnotationType
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Maps between on-screen pixels and normalized (0f..1f) image coordinates, accounting for the
 * letterboxing produced by fitting the photo into the canvas while preserving its aspect ratio. */
private class ImageMapping(val canvasSize: IntSize, val bitmap: Bitmap) {
    val scale: Float = if (bitmap.width == 0 || bitmap.height == 0) 1f else
        minOf(canvasSize.width / bitmap.width.toFloat(), canvasSize.height / bitmap.height.toFloat())
    val drawWidth: Float = bitmap.width * scale
    val drawHeight: Float = bitmap.height * scale
    val offsetX: Float = (canvasSize.width - drawWidth) / 2f
    val offsetY: Float = (canvasSize.height - drawHeight) / 2f

    fun toNormalized(offset: Offset): Pair<Float, Float> {
        if (drawWidth <= 0f || drawHeight <= 0f) return 0f to 0f
        val x = ((offset.x - offsetX) / drawWidth).coerceIn(0f, 1f)
        val y = ((offset.y - offsetY) / drawHeight).coerceIn(0f, 1f)
        return x to y
    }

    fun toScreen(point: Pair<Float, Float>): Offset =
        Offset(offsetX + point.first * drawWidth, offsetY + point.second * drawHeight)

    fun normalizedDelta(dx: Float, dy: Float): Pair<Float, Float> =
        (if (drawWidth > 0f) dx / drawWidth else 0f) to (if (drawHeight > 0f) dy / drawHeight else 0f)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefectViewEditorScreen(
    imagePath: String,
    viewModel: AnnotationEditorViewModel,
    onDone: () -> Unit,
    onCancel: () -> Unit
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val bitmap = remember(imagePath) { BitmapFactory.decodeFile(imagePath) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var inProgressPoints by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
    var dragStartSnapshot by remember { mutableStateOf<List<AnnotationDraft>?>(null) }
    var activeResizeCorner by remember { mutableStateOf<Corner?>(null) }
    var pendingTextAnchor by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    val textMeasurer = rememberTextMeasurer()

    if (bitmap == null) {
        Scaffold(topBar = { TopAppBar(title = { Text("Defect View Editor") }) }) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Could not load this photo.")
            }
        }
        return
    }

    val mapping = ImageMapping(canvasSize, bitmap)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Defect View Editor") },
                actions = {
                    IconButton(onClick = { viewModel.undo() }, enabled = ui.canUndo) {
                        Icon(Icons.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = { viewModel.redo() }, enabled = ui.canRedo) {
                        Icon(Icons.Filled.Redo, contentDescription = "Redo")
                    }
                    IconButton(onClick = { viewModel.deleteSelected() }, enabled = ui.selectedAnnotationKey != null) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete selected")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(ui.selectedTool) {
                        detectTapGestures { tapOffset ->
                            val (nx, ny) = mapping.toNormalized(tapOffset)
                            when (ui.selectedTool) {
                                EditorTool.SELECT -> viewModel.selectAnnotationAt(nx, ny)
                                EditorTool.TEXT_CALLOUT -> pendingTextAnchor = nx to ny
                                else -> Unit
                            }
                        }
                    }
                    .pointerInput(ui.selectedTool, ui.selectedAnnotationKey) {
                        detectDragGestures(
                            onDragStart = { start ->
                                val (nx, ny) = mapping.toNormalized(start)
                                when (ui.selectedTool) {
                                    EditorTool.SELECT -> {
                                        dragStartSnapshot = viewModel.currentSnapshot()
                                        val selected = ui.annotations.find { it.localKey == ui.selectedAnnotationKey }
                                        activeResizeCorner = selected?.let { AnnotationGeometry.cornerAt(it.points, nx, ny) }
                                    }
                                    EditorTool.CIRCLE, EditorTool.RECTANGLE, EditorTool.ARROW ->
                                        inProgressPoints = listOf(nx to ny, nx to ny)
                                    EditorTool.FREEHAND -> inProgressPoints = listOf(nx to ny)
                                    EditorTool.TEXT_CALLOUT -> Unit
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                when (ui.selectedTool) {
                                    EditorTool.SELECT -> {
                                        val corner = activeResizeCorner
                                        if (corner != null) {
                                            val (nx, ny) = mapping.toNormalized(change.position)
                                            viewModel.resizeSelected(corner, nx, ny)
                                        } else {
                                            val (dnx, dny) = mapping.normalizedDelta(dragAmount.x, dragAmount.y)
                                            viewModel.moveSelected(dnx, dny)
                                        }
                                    }
                                    EditorTool.CIRCLE, EditorTool.RECTANGLE, EditorTool.ARROW -> {
                                        val (nx, ny) = mapping.toNormalized(change.position)
                                        val start = inProgressPoints.firstOrNull() ?: (nx to ny)
                                        inProgressPoints = listOf(start, nx to ny)
                                    }
                                    EditorTool.FREEHAND -> {
                                        val (nx, ny) = mapping.toNormalized(change.position)
                                        inProgressPoints = inProgressPoints + (nx to ny)
                                    }
                                    EditorTool.TEXT_CALLOUT -> Unit
                                }
                            },
                            onDragEnd = {
                                when (ui.selectedTool) {
                                    EditorTool.SELECT -> {
                                        dragStartSnapshot?.let { viewModel.commitTransform(it) }
                                        dragStartSnapshot = null
                                        activeResizeCorner = null
                                    }
                                    EditorTool.CIRCLE, EditorTool.RECTANGLE, EditorTool.ARROW -> {
                                        if (inProgressPoints.size == 2) {
                                            val type = when (ui.selectedTool) {
                                                EditorTool.CIRCLE -> AnnotationType.CIRCLE
                                                EditorTool.RECTANGLE -> AnnotationType.RECTANGLE
                                                else -> AnnotationType.ARROW
                                            }
                                            viewModel.commitNewAnnotation(type, inProgressPoints)
                                        }
                                        inProgressPoints = emptyList()
                                    }
                                    EditorTool.FREEHAND -> {
                                        if (inProgressPoints.size >= 2) {
                                            viewModel.commitNewAnnotation(AnnotationType.FREEHAND, inProgressPoints)
                                        }
                                        inProgressPoints = emptyList()
                                    }
                                    EditorTool.TEXT_CALLOUT -> Unit
                                }
                            }
                        )
                    }
            ) {
                drawImage(
                    image = bitmap.asImageBitmap(),
                    dstOffset = androidx.compose.ui.unit.IntOffset(mapping.offsetX.toInt(), mapping.offsetY.toInt()),
                    dstSize = IntSize(mapping.drawWidth.toInt().coerceAtLeast(1), mapping.drawHeight.toInt().coerceAtLeast(1))
                )

                ui.annotations.forEach { annotation ->
                    drawAnnotation(annotation, mapping, textMeasurer, isSelected = annotation.localKey == ui.selectedAnnotationKey)
                }

                if (inProgressPoints.isNotEmpty()) {
                    val draftType = when (ui.selectedTool) {
                        EditorTool.CIRCLE -> AnnotationType.CIRCLE
                        EditorTool.RECTANGLE -> AnnotationType.RECTANGLE
                        EditorTool.ARROW -> AnnotationType.ARROW
                        EditorTool.FREEHAND -> AnnotationType.FREEHAND
                        else -> null
                    }
                    draftType?.let {
                        drawAnnotation(
                            AnnotationDraft(-1, it, colorArgb = ui.selectedColor, points = inProgressPoints),
                            mapping,
                            textMeasurer,
                            isSelected = false
                        )
                    }
                }
            }

            EditorToolbar(ui.selectedTool, onSelectTool = viewModel::selectTool)
            ColorPalette(ui.selectedColor, onSelectColor = viewModel::selectColor)

            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                androidx.compose.material3.OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                Button(onClick = onDone, modifier = Modifier.weight(1f)) {
                    Text("Done")
                }
            }
        }
    }

    val anchor = pendingTextAnchor
    if (anchor != null) {
        TextCalloutDialog(
            onConfirm = { text ->
                if (text.isNotBlank()) viewModel.commitNewAnnotation(AnnotationType.TEXT_CALLOUT, listOf(anchor), text)
                pendingTextAnchor = null
            },
            onDismiss = { pendingTextAnchor = null }
        )
    }
}

@Composable
private fun TextCalloutDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add callout text") },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { Button(onClick = { onConfirm(text) }) { Text("Add") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun EditorToolbar(selected: EditorTool, onSelectTool: (EditorTool) -> Unit) {
    val tools = listOf(
        EditorTool.SELECT to Icons.Filled.NearMe,
        EditorTool.CIRCLE to Icons.Filled.Circle,
        EditorTool.RECTANGLE to Icons.Filled.CropSquare,
        EditorTool.ARROW to Icons.Filled.ArrowForward,
        EditorTool.FREEHAND to Icons.Filled.Gesture,
        EditorTool.TEXT_CALLOUT to Icons.Filled.TextFields
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        tools.forEach { (tool, icon) ->
            IconToggleButton(checked = selected == tool, onCheckedChange = { onSelectTool(tool) }) {
                Icon(icon, contentDescription = tool.name)
            }
        }
    }
}

@Composable
private fun ColorPalette(selected: Long, onSelectColor: (Long) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(DEFAULT_ANNOTATION_COLORS) { colorLong ->
            val color = Color(colorLong)
            val borderWidth = if (colorLong == selected) 3.dp else 0.dp
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(borderWidth, Color.Black, CircleShape)
                    .pointerInput(colorLong) {
                        detectTapGestures(onTap = { onSelectColor(colorLong) })
                    }
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAnnotation(
    annotation: AnnotationDraft,
    mapping: ImageMapping,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    isSelected: Boolean
) {
    val color = Color(annotation.colorArgb)
    val stroke = Stroke(width = annotation.strokeWidth * (if (isSelected) 1.6f else 1f), cap = StrokeCap.Round)

    when (annotation.type) {
        AnnotationType.RECTANGLE -> {
            val box = AnnotationGeometry.boundingBox(annotation.points) ?: return
            val topLeft = mapping.toScreen(box.left to box.top)
            val bottomRight = mapping.toScreen(box.right to box.bottom)
            drawRect(color, topLeft = topLeft, size = androidx.compose.ui.geometry.Size(bottomRight.x - topLeft.x, bottomRight.y - topLeft.y), style = stroke)
        }
        AnnotationType.CIRCLE -> {
            val box = AnnotationGeometry.boundingBox(annotation.points) ?: return
            val topLeft = mapping.toScreen(box.left to box.top)
            val bottomRight = mapping.toScreen(box.right to box.bottom)
            drawOval(color, topLeft = topLeft, size = androidx.compose.ui.geometry.Size(bottomRight.x - topLeft.x, bottomRight.y - topLeft.y), style = stroke)
        }
        AnnotationType.ARROW -> {
            if (annotation.points.size < 2) return
            val start = mapping.toScreen(annotation.points.first())
            val end = mapping.toScreen(annotation.points.last())
            drawLine(color, start, end, strokeWidth = stroke.width, cap = StrokeCap.Round)
            val angle = atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())
            val headLength = 18f
            val headAngle = Math.PI / 7
            val p1 = Offset(end.x - (headLength * cos(angle - headAngle)).toFloat(), end.y - (headLength * sin(angle - headAngle)).toFloat())
            val p2 = Offset(end.x - (headLength * cos(angle + headAngle)).toFloat(), end.y - (headLength * sin(angle + headAngle)).toFloat())
            drawLine(color, end, p1, strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(color, end, p2, strokeWidth = stroke.width, cap = StrokeCap.Round)
        }
        AnnotationType.FREEHAND -> {
            if (annotation.points.size < 2) return
            val path = androidx.compose.ui.graphics.Path()
            val first = mapping.toScreen(annotation.points.first())
            path.moveTo(first.x, first.y)
            annotation.points.drop(1).forEach { path.lineTo(mapping.toScreen(it).x, mapping.toScreen(it).y) }
            drawPath(path, color, style = stroke)
        }
        AnnotationType.TEXT_CALLOUT -> {
            if (annotation.points.isEmpty()) return
            val anchor = mapping.toScreen(annotation.points.first())
            drawCircle(color, radius = 6f, center = anchor)
            if (annotation.label.isNotBlank()) {
                val layout = textMeasurer.measure(annotation.label)
                val bgTopLeft = Offset(anchor.x + 10f, anchor.y - layout.size.height / 2f)
                drawRect(
                    color = Color.White.copy(alpha = 0.85f),
                    topLeft = Offset(bgTopLeft.x - 4f, bgTopLeft.y - 2f),
                    size = androidx.compose.ui.geometry.Size(layout.size.width + 8f, layout.size.height + 4f)
                )
                drawText(layout, topLeft = bgTopLeft, color = color)
            }
        }
    }

    if (isSelected && (annotation.type == AnnotationType.RECTANGLE || annotation.type == AnnotationType.CIRCLE)) {
        val box = AnnotationGeometry.boundingBox(annotation.points) ?: return
        val handleRadius = 10f
        listOf(box.left to box.top, box.right to box.top, box.left to box.bottom, box.right to box.bottom).forEach { corner ->
            val point = mapping.toScreen(corner)
            drawCircle(Color.White, radius = handleRadius, center = point)
            drawCircle(color, radius = handleRadius, center = point, style = Stroke(width = 3f))
        }
    }
}
