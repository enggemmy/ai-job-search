package com.defectview.domain.vision

import com.defectview.domain.model.AiEngineInfo
import com.defectview.domain.model.DefectDetection

/**
 * Abstraction over a local, on-device image analysis engine. Implementations live in the
 * `:app` module because they need Android's Bitmap/graphics APIs (and, in the future, a
 * LiteRT/TensorFlow Lite or ONNX Runtime interpreter) - this interface is what the rest of the
 * app depends on so the concrete engine is swappable without touching call sites.
 *
 * Every implementation MUST return results for low-confidence cases rather than silently
 * dropping them (the caller decides what to show, per ConfidenceClassifier), and must never
 * fabricate a detection when it found nothing - an empty list is a valid, honest result.
 */
interface VisionEngine {
    val info: AiEngineInfo

    /**
     * Analyzes one photo, identified by an opaque, engine-specific handle (an Android Bitmap in
     * the real implementation, a decoded pixel buffer in tests). Returns zero or more raw
     * detections; downstream reasoning turns these into structured defect suggestions.
     */
    suspend fun analyze(image: ImageSample): List<DefectDetection>
}

/**
 * A minimal, platform-agnostic image representation so this interface (and its contract tests)
 * do not need to depend on android.graphics.Bitmap. [pixels] is row-major ARGB.
 */
data class ImageSample(
    val width: Int,
    val height: Int,
    val pixels: IntArray
) {
    init {
        require(width > 0 && height > 0) { "width/height must be positive" }
        require(pixels.size == width * height) { "pixels size must equal width*height" }
    }
}
