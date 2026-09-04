package com.defectview.app.vision

import android.graphics.Bitmap
import com.defectview.domain.vision.ImageSample

/**
 * Bridges android.graphics.Bitmap to the platform-agnostic [ImageSample] the domain module's
 * [com.defectview.domain.vision.VisionEngine] operates on. Downsamples large photos first -
 * the classical-CV heuristics only need coarse tile statistics, and analyzing a full 12MP photo
 * pixel-by-pixel on a mid-range site device would be needlessly slow.
 */
fun Bitmap.toImageSample(maxDimension: Int = 640): ImageSample {
    val scale = (maxDimension.toFloat() / maxOf(width, height)).coerceAtMost(1f)
    val targetWidth = (width * scale).toInt().coerceAtLeast(1)
    val targetHeight = (height * scale).toInt().coerceAtLeast(1)

    val scaled = if (scale < 1f) Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true) else this
    val pixels = IntArray(targetWidth * targetHeight)
    scaled.getPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)
    if (scaled !== this) scaled.recycle()

    return ImageSample(targetWidth, targetHeight, pixels)
}
