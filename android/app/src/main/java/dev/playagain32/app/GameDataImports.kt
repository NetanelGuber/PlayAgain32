package dev.playagain32.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Imports expansion files without accessing another app's storage. */
object GameDataImports {
    fun validName(name: String, packageName: String) =
        Regex("(main|patch)\\.[0-9]+\\.${Regex.escape(packageName)}\\.obb").matches(name)

    @Synchronized
    fun importData(context: Context, uri: Uri, packageName: String): File {
        require(packageName.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+"))) { "Invalid game package name." }
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: throw IOException("Cannot read the expansion file name.")
        require(validName(name, packageName)) { "Choose a main.<version>.$packageName.obb or patch.<version>.$packageName.obb file for this game." }
        val folder = File(context.filesDir, "game-data/$packageName").apply {
            if (!isDirectory && !mkdirs()) throw IOException("Cannot create the game data folder.")
        }
        val target = File(folder, name)
        require(!target.exists()) { "This expansion file is already imported. Existing game data was kept." }
        val partial = File.createTempFile("data-", ".part", folder)
        try {
            val source = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open the game data.")
            source.use { input ->
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    var nextSpaceCheck = 0L
                    while (true) {
                        val size = input.read(buffer)
                        if (size < 0) break
                        if (size == 0) continue
                        require(size.toLong() <= 2L * 1024 * 1024 * 1024 - total) { "Expansion files larger than 2 GiB are not supported yet." }
                        if (total >= nextSpaceCheck) {
                            if (folder.usableSpace < 16L * 1024 * 1024) throw IOException("Not enough free storage for game data.")
                            nextSpaceCheck = total + 8L * 1024 * 1024
                        }
                        output.write(buffer, 0, size)
                        total += size
                    }
                    require(total > 0) { "The selected expansion file is empty." }
                    output.fd.sync()
                }
            }
            if (!partial.renameTo(target)) throw IOException("Cannot save the game data. Your original file was kept.")
            return target
        } finally {
            partial.delete()
        }
    }
}
