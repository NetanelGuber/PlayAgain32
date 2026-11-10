package dev.playagain32.app

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Retains blocking file jobs and their busy state across activity recreation. */
object Imports {
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var revision by mutableStateOf(0)
        private set
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun apk(context: Context, uri: Uri) {
        val app = context.applicationContext
        start {
            val imported = ApkImports.importApk(app, uri)
            val item = OfflineLibrary.read(app, imported.file)
            "${item.game.label} imported. ${item.decision.explanation}"
        }
    }

    fun data(context: Context, uri: Uri, packageName: String) {
        val app = context.applicationContext
        start {
            val file = GameDataImports.importData(app, uri, packageName)
            "${file.name} imported. Prepare or install the game to copy its data."
        }
    }

    private fun start(runImport: () -> String) {
        if (busy || Conversions.busy) return
        busy = true
        message = null
        scope.launch {
            try {
                message = withContext(Dispatchers.IO) { runImport() }
            } catch (failure: Exception) {
                message = failure.message ?: "Import failed. Your original source file was kept."
            } finally {
                revision++
                busy = false
            }
        }
    }
}
