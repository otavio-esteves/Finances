package br.com.otavioesteves.finances.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/** Resolves the human-readable file name behind a SAF-picked [Uri]. */
fun queryDisplayName(context: Context, uri: Uri): String? {
    return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
    } ?: uri.lastPathSegment
}
