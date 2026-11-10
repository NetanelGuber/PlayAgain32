package dev.playagain32.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException

/** Call off the main thread after OpenDocument returns; the source document is never modified. */
object ApkImports {
    fun importApk(context: Context, uri: Uri): ImportedApk {
        var displayName: String? = null
        var declaredSize = -1L
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameColumn >= 0 && !cursor.isNull(nameColumn)) displayName = cursor.getString(nameColumn)
                if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) declaredSize = cursor.getLong(sizeColumn)
            }
        }
        if (displayName?.endsWith(".apk", ignoreCase = true) == false) {
            throw IOException("Select a single .apk file. Bundles and split APK sets are not supported yet.")
        }
        return context.contentResolver.openInputStream(uri)?.use { input ->
            ApkStore.importApk(input, File(context.filesDir, "games"), declaredSize)
        } ?: throw IOException("Cannot open the selected APK.")
    }
}
