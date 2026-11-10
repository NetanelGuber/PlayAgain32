package dev.playagain32.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.system.Os
import android.system.OsConstants
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Forest = Color(0xFF83DBB5)
private val Ink = Color(0xFF101815)
private val Panel = Color(0xFF1B2722)

/** Offline launcher; games execute in their own installed Android packages. */
class OfflineActivity : ComponentActivity() {
    companion object {
        private var installsAllowedAtStart: Boolean? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (installsAllowedAtStart == null) installsAllowedAtStart = packageManager.canRequestPackageInstalls()
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Forest, background = Ink, surface = Panel)) {
                Surface(Modifier.fillMaxSize(), color = Ink) { LibraryScreen() }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Confirmation.appVisible = true
        Confirmation.waiting.getAndSet(null)?.let { startActivity(it) }
    }

    override fun onStop() {
        Confirmation.appVisible = false
        super.onStop()
    }

    @Composable
    private fun LibraryScreen() {
        val scope = rememberCoroutineScope()
        var library by remember { mutableStateOf<List<LibraryGame>>(emptyList()) }
        val importing = Imports.busy
        var refreshing by remember { mutableStateOf(false) }
        var message by remember { mutableStateOf<String?>(null) }
        var removal by remember { mutableStateOf<LibraryGame?>(null) }
        var dataPackage by rememberSaveable { mutableStateOf<String?>(null) }
        var report by rememberSaveable { mutableStateOf("") }
        var installsAllowed by remember { mutableStateOf(packageManager.canRequestPackageInstalls()) }
        val busy = importing || Conversions.busy
        val visibleMessage = message ?: Imports.message
        val runtimeProblem = remember { OfflineLibrary.runtimeProblem(this) }
        val pageSize = remember { runCatching { Os.sysconf(OsConstants._SC_PAGESIZE) }.getOrDefault(4096) }
        @Suppress("DEPRECATION")
        val appVersion = remember { packageManager.getPackageInfo(packageName, 0).versionName.orEmpty() }

        fun refresh() {
            if (refreshing) return
            refreshing = true
            scope.launch {
                try {
                    library = withContext(Dispatchers.IO) { OfflineLibrary.list(applicationContext) }
                } catch (failure: Exception) {
                    message = failure.message ?: "Could not read the imported games."
                } finally {
                    refreshing = false
                }
            }
        }

        fun diagnosticReport(): String = buildString {
            appendLine("${getString(R.string.app_name)} $appVersion — local diagnostic report")
            appendLine("Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
            appendLine("ABIs: ${Build.SUPPORTED_ABIS.joinToString()}; page size: $pageSize")
            appendLine("Runtime: ${runtimeProblem ?: "packaged"}")
            appendLine("Last message: ${visibleMessage ?: "none"}")
            library.forEach { item ->
                appendLine("${item.game.packageName} ${item.version}: ${item.decision.route}")
                val state = Conversions.states[item.game.packageName]
                if (state is CardState.Failed) appendLine("Failure: ${state.reason}")
            }
            appendLine("Compatibility has not been verified for every game/device.")
        }

        val exportReport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri != null) scope.launch {
                message = withContext(Dispatchers.IO) {
                    runCatching {
                        contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(report) }
                            ?: error("Cannot open the selected report file.")
                        "Diagnostic report exported."
                    }.getOrElse { it.message ?: "Could not export the report." }
                }
            }
        }
        val importApk = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null && !busy) {
                message = null
                Imports.apk(applicationContext, uri)
            }
        }
        val importData = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val selectedPackage = dataPackage
            dataPackage = null
            if (uri != null && selectedPackage != null && !busy) {
                message = null
                Imports.data(applicationContext, uri, selectedPackage)
            }
        }

        LaunchedEffect(Imports.revision) { refresh() }

        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            installsAllowed = packageManager.canRequestPackageInstalls()
            Confirmation.waiting.getAndSet(null)?.let { startActivity(it) }
            if (!busy) refresh()
        }
        DisposableEffect(busy) {
            if (busy) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Image(painterResource(R.drawable.playagain32_mark), contentDescription = null,
                        modifier = Modifier.size(88.dp).clip(RoundedCornerShape(22.dp)))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.app_name), color = Forest, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.app_tagline), fontSize = 14.sp, color = Color(0xFFB6C6BD))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Bring your old Android games to this phone. Import, prepare, and play.", color = Color(0xFFB6C6BD))
                Spacer(Modifier.height(16.dp))
                Surface(color = Panel, shape = RoundedCornerShape(24.dp)) {
                    Text("●  Offline on your phone  ·  No account", Modifier.padding(horizontal = 14.dp, vertical = 9.dp), color = Forest, fontSize = 12.sp)
                }
            }
            if (runtimeProblem != null) item { Notice(runtimeProblem) }
            if (pageSize > 4096) item { Notice("This phone uses ${pageSize / 1024} KB memory pages. Native translation on this configuration is experimental; game compatibility needs device testing.") }
            item {
                Button(onClick = { importApk.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream", "application/zip")) },
                    enabled = !busy, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                    Text(if (importing) "Checking your file…" else "Import a game APK", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Text("Standalone APKs up to 512 MiB. Your original files stay untouched.", fontSize = 12.sp, color = Color(0xFFB6C6BD))
            }
            if (importing || refreshing) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (visibleMessage != null) item { Notice(visibleMessage) }
            if (!installsAllowed) item {
                Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Allow game installation", fontWeight = FontWeight.Bold)
                        Text("Android asks you to approve each install. Enable this once when you're ready.", Modifier.padding(top = 6.dp), fontSize = 13.sp)
                        TextButton(onClick = {
                            runCatching { startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))) }
                                .onFailure { message = "Open Android Settings → Apps → Special app access → Install unknown apps → PlayAgain32." }
                        }) { Text("Open Android settings") }
                    }
                }
            }
            if (installsAllowed && installsAllowedAtStart == false) item {
                Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Finish enabling game data access", fontWeight = FontWeight.Bold)
                        Text("Restart PlayAgain32 before installing games with OBB expansion files. Your imports are saved.", Modifier.padding(top = 6.dp), fontSize = 13.sp)
                        TextButton(onClick = {
                            startActivity(Intent.makeRestartActivityTask(android.content.ComponentName(this@OfflineActivity, OfflineActivity::class.java)))
                            Runtime.getRuntime().exit(0)
                        }, enabled = !busy) { Text("Restart PlayAgain32") }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Your library", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("${library.size} games", color = Forest, fontSize = 13.sp)
                }
            }
            if (library.isEmpty() && !refreshing) item {
                Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                    Column(Modifier.padding(24.dp)) {
                        Text("Start with a game you own", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        Text("Select its APK from Files. We'll check which route it needs. Add its OBB expansion file if the game uses one.")
                    }
                }
            }
            items(library, key = { it.game.packageName }) { item ->
                val game = item.game
                val displaySettings = remember { getSharedPreferences("game-display", MODE_PRIVATE) }
                var fullScreen by remember(game.packageName) { mutableStateOf(displaySettings.getBoolean(game.packageName, false)) }
                val state = Conversions.states[game.packageName]
                val installed = OfflineLibrary.installed(this@OfflineActivity, game.packageName)
                Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            game.icon?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.size(48.dp)) }
                            Column(Modifier.weight(1f)) {
                                Text(game.label, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                                Text("Version ${item.version} · ${game.place}", fontSize = 12.sp, color = Color(0xFFB6C6BD))
                            }
                        }
                        Text(item.decision.explanation, fontSize = 13.sp)
                        when (state) {
                            is CardState.Working -> {
                                LinearProgressIndicator(progress = { state.progress.percent / 100f }, modifier = Modifier.fillMaxWidth())
                                Text(state.progress.text, color = Forest, fontSize = 13.sp)
                                Text("Keep PlayAgain32 open while preparing this game.", fontSize = 12.sp)
                            }
                            is CardState.Failed -> Notice(state.reason)
                            is CardState.OtherCopy -> Notice("Another signing identity is installed. PlayAgain32 kept that game and its saves. Android cannot update it with a re-signed copy.")
                            else -> Unit
                        }
                        if (state !is CardState.Working) {
                            val supported = item.decision.route != ApkCompatibility.Route.Unsupported
                            val needsPreparation = game.action != Action.Install || fullScreen
                            val runtimeReady = !needsPreparation || runtimeProblem == null
                            if (supported) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Fill screen", fontWeight = FontWeight.Bold)
                                        Text("Applied when you prepare and install. Turn off if the game's layout breaks.", fontSize = 12.sp)
                                    }
                                    Switch(checked = fullScreen, modifier = Modifier.semantics { contentDescription = "Fill screen for ${game.label}" }, onCheckedChange = {
                                        fullScreen = it
                                        displaySettings.edit().putBoolean(game.packageName, it).apply()
                                    }, enabled = !busy)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (installed) Button(onClick = {
                                    packageManager.getLaunchIntentForPackage(game.packageName)?.let { startActivity(it) }
                                        ?: run { message = "Android could not find a launch screen for this game." }
                                }, enabled = !busy) { Text("Play") }
                                OutlinedButton(onClick = {
                                    val action = if (fullScreen && game.action == Action.Install) Action.PrepareSdk else game.action
                                    Conversions.convert(this@OfflineActivity, game.copy(action = action, fullScreen = fullScreen), replace = false)
                                },
                                    enabled = !busy && supported && runtimeReady && installsAllowed) {
                                    Text(if (needsPreparation) "Prepare & install" else "Install original")
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { dataPackage = game.packageName; importData.launch(arrayOf("*/*")) }, enabled = !busy) { Text("Add OBB data") }
                                TextButton(onClick = { removal = item }, enabled = !busy) { Text("Remove import") }
                            }
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(4.dp))
                Text("EARLY ACCESS", color = Forest, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("The ARM32 runtime is experimental. Installation isn't a guarantee of playability. Missing servers, original-signature checks, and some engines still prevent games from working.", Modifier.padding(top = 8.dp), fontSize = 12.sp, color = Color(0xFFB6C6BD))
                Text("Prepared games are re-signed with a key on this phone. Keep PlayAgain32 installed to retain that key for updates.", Modifier.padding(top = 8.dp), fontSize = 12.sp, color = Color(0xFFB6C6BD))
                TextButton(onClick = { report = diagnosticReport(); exportReport.launch("playagain32-report.txt") }, enabled = !busy) { Text("Export diagnostic report") }
                Text("Built on Game Bridge · GPL-2.0-or-later\nNo ads. No network permission. No phone-wide file access.", fontSize = 11.sp, color = Color(0xFFB6C6BD))
            }
        }
        removal?.let { selected ->
            AlertDialog(onDismissRequest = { removal = null }, title = { Text("Remove imported APK?") },
                text = { Text("${selected.game.label} will leave this library. Its installed game, saves, expansion data, and original source file stay on your phone.") },
                confirmButton = { TextButton(onClick = {
                    removal = null
                    scope.launch {
                        val removed = withContext(Dispatchers.IO) { selected.game.file?.delete() == true }
                        message = if (removed) "Imported APK removed." else "The imported APK could not be removed."
                        refresh()
                    }
                }) { Text("Remove import") } },
                dismissButton = { TextButton(onClick = { removal = null }) { Text("Keep") } })
        }
    }
}

@Composable
private fun Notice(text: String) {
    Surface(color = Color(0xFF29362F), shape = RoundedCornerShape(12.dp)) {
        SelectionContainer { Text(text, Modifier.fillMaxWidth().padding(14.dp), fontSize = 13.sp) }
    }
}
