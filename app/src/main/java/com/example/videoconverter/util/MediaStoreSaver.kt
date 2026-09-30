package com.example.videoconverter.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.io.File

object MediaStoreSaver {
    fun save(context: Context, file: File, audioOnly: Boolean): Uri {
        val resolver = context.contentResolver
        val collection = if (audioOnly) {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val extension = if (audioOnly) "m4a" else "mp4"

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "converted_${System.currentTimeMillis()}.$extension")
            put(MediaStore.MediaColumns.MIME_TYPE, if (audioOnly) "audio/mp4" else "video/mp4")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                if (audioOnly) "Music/VideoConverter" else "Movies/VideoConverter"
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: error("Could not create media entry")

        resolver.openOutputStream(uri)?.use { out ->
            file.inputStream().use { it.copyTo(out) }
        } ?: error("Could not open output stream")

        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }
}