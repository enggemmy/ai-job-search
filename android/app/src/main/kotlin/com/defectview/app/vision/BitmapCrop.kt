package com.defectview.app.vision

import android.graphics.Bitmap
import com.defectview.domain.model.BoundingBox

/** Crops to a normalized (0f..1f) region, clamping to the bitmap's actual pixel bounds. */
fun Bitmap.cropTo(box: BoundingBox): Bitmap {
    val left = (box.left * width).toInt().coerceIn(0, width - 1)
    val top = (box.top * height).toInt().coerceIn(0, height - 1)
    val right = (box.right * width).toInt().coerceIn(left + 1, width)
    val bottom = (box.bottom * height).toInt().coerceIn(top + 1, height)
    return Bitmap.createBitmap(this, left, top, right - left, bottom - top)
}
