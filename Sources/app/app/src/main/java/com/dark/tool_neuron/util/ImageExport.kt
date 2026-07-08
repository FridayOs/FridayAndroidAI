package com.dark.tool_neuron.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

object ImageExport {

    suspend fun saveBitmapToGallery(
        context: Context,
        bitmap: Bitmap,
        // FRIDAY AI rebrand (FRI-531 M1-01). Default prefix is the new product brand.
        // The MediaStore RELATIVE_PATH below is intentionally kept as Pictures/ToolNeuron
        // for this milestone to avoid migrating existing on-device gallery entries; it is a
        // legacy storage path and is not surfaced to the user (see ImageViewerCard toast).
        displayName: String = "friday_ai_${System.currentTimeMillis()}",
    ): Result<Uri> = runCatching {
        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Legacy storage path — see comment on displayName above.
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/ToolNeuron",
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(collection, values)
            ?: throw IllegalStateException("MediaStore.insert returned null")
        resolver.openOutputStream(uri).use { os ->
            requireNotNull(os) { "openOutputStream returned null" }
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)) {
                throw IllegalStateException("Bitmap.compress returned false")
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        uri
    }
}
