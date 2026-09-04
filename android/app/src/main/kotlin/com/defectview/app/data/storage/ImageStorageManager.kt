package com.defectview.app.data.storage

import android.content.Context
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * All inspection photos live under the app's own private files directory - nothing is uploaded
 * automatically (spec section 11: offline-first, no cloud storage by default).
 */
class ImageStorageManager(private val context: Context) {

    private val photosDir: File by lazy {
        File(context.filesDir, "inspection_photos").apply { mkdirs() }
    }

    private val cameraTempDir: File by lazy {
        File(context.cacheDir, "camera_temp").apply { mkdirs() }
    }

    /** Creates a new, empty file for a fresh camera capture and returns its content:// Uri. */
    fun createCaptureTarget(): CaptureTarget {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(cameraTempDir, "DV_$timestamp.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return CaptureTarget(file, uri)
    }

    /** Moves a captured/imported photo out of the temp cache into permanent storage and returns its absolute path. */
    fun persist(sourceFile: File, prefix: String = "photo"): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val destination = File(photosDir, "${prefix}_$timestamp.jpg")
        sourceFile.copyTo(destination, overwrite = true)
        if (sourceFile.parentFile == cameraTempDir) {
            sourceFile.delete()
        }
        return destination.absolutePath
    }

    /** Encodes an in-memory (e.g. freshly annotated) bitmap straight to permanent storage. */
    fun saveBitmap(bitmap: Bitmap, prefix: String = "annotated"): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val destination = File(photosDir, "${prefix}_$timestamp.jpg")
        FileOutputStream(destination).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        return destination.absolutePath
    }

    fun deletePhoto(path: String) {
        File(path).takeIf { it.exists() && it.parentFile == photosDir }?.delete()
    }

    fun uriFor(path: String) = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path))
}

data class CaptureTarget(val file: File, val uri: android.net.Uri)
