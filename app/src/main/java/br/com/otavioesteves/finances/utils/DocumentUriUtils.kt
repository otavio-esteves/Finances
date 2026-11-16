package br.com.otavioesteves.finances.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.WorkerThread

/** Resolves the human-readable file name behind a SAF-picked [Uri]. */
@WorkerThread
fun queryDisplayName(context: Context, uri: Uri): String? {
    return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
    } ?: uri.lastPathSegment
}
