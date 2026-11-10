package dev.playagain32.app

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.util.zip.ZipFile

data class LibraryGame(val game: Game, val decision: ApkCompatibility.Decision, val version: String)

object OfflineLibrary {
    @Suppress("DEPRECATION")
    fun read(context: Context, file: File): LibraryGame {
        val info = context.packageManager.getPackageArchiveInfo(file.path, PackageManager.GET_META_DATA)
            ?: error("Android cannot read this APK. It may be incomplete or damaged.")
        require(info.splitNames.isNullOrEmpty()) { "Select a complete standalone APK; split APK sets are not supported yet." }
        val app = info.applicationInfo ?: error("The APK has no application information.")
        require(app.metaData?.getBoolean("com.android.vending.splits.required", false) != true) {
            "This game requires additional split APKs. Import a complete standalone APK instead."
        }
        app.sourceDir = file.path
        app.publicSourceDir = file.path
        val abis = ZipFile(file).use { zip ->
            zip.entries().asSequence().map { it.name }
                .filter { it.startsWith("lib/") && it.endsWith(".so") }
                .map { it.split('/').getOrElse(1) { "" } }.toSet()
        }
        val decision = ApkCompatibility.decide(abis, Build.SUPPORTED_ABIS.toSet(), app.minSdkVersion, Build.VERSION.SDK_INT, app.targetSdkVersion)
        return LibraryGame(
            Game(file, "Imported · ${file.length() / (1024 * 1024)} MiB", app.loadLabel(context.packageManager).toString(),
                info.packageName, info.longVersionCode, Games.iconBitmap(app.loadIcon(context.packageManager)),
                when (decision.route) {
                    ApkCompatibility.Route.TranslateArm32 -> Action.Convert
                    ApkCompatibility.Route.PrepareSdk -> Action.PrepareSdk
                    else -> Action.Install
                }),
            decision, info.versionName ?: info.longVersionCode.toString(),
        )
    }

    fun list(context: Context): List<LibraryGame> = File(context.filesDir, "games").listFiles()
        .orEmpty().filter { it.isFile && it.name.matches(Regex("[a-f0-9]{64}\\.apk")) }
        .sortedByDescending { it.lastModified() }
        .mapNotNull { file -> runCatching { read(context, file) }.getOrNull() }
        .distinctBy { it.game.packageName }
        .sortedBy { it.game.label.lowercase() }

    @Suppress("DEPRECATION")
    fun installed(context: Context, packageName: String): Boolean =
        runCatching { context.packageManager.getPackageInfo(packageName, 0) }.isSuccess

    fun runtimeProblem(context: Context): String? {
        if ("arm64-v8a" !in Build.SUPPORTED_ABIS) return "ARM32 translation needs a 64-bit ARM Android phone."
        val folder = File(context.applicationInfo.nativeLibraryDir)
        val required = listOf("libarm32_bridge.so", "libapkconvert.so", "libstandin.so")
        if (required.any { !File(folder, it).isFile }) return "This build is missing its translation runtime. Install a complete PlayAgain32 APK."
        return null
    }
}
