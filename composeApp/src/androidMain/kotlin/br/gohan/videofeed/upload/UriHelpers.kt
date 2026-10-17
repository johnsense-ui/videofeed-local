package br.gohan.videofeed.upload

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

fun readBytesFromUri(context: Context, uri: Uri): ByteArray =
    context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: byteArrayOf()

fun getFilenameFromUri(context: Context, uri: Uri): String {
    var name = "video.mp4"
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && nameIndex != -1) {
            name = cursor.getString(nameIndex)
        }
    }
    return name
}

fun getMimeTypeFromUri(context: Context, uri: Uri): String =
    context.contentResolver.getType(uri) ?: "video/mp4"
