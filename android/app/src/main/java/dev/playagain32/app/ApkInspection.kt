package dev.playagain32.app

import java.io.File
import java.io.IOException
import java.util.zip.CRC32
import java.util.zip.ZipFile

data class ApkInspection(val nativeAbis: Set<String>) {
    /** No native libraries in standard APK paths; downloaded/asset-packed code is not classified. */
    val javaOnly get() = nativeAbis.isEmpty()
    val arm32Only get() = nativeAbis.any { it == "armeabi" || it == "armeabi-v7a" } &&
        "arm64-v8a" !in nativeAbis
}

/** Offline structural inspection; Android's package parser still validates the manifest. */
object ApkInspector {
    const val MAX_APK_BYTES = 512L * 1024 * 1024
    private const val MAX_EXPANDED_BYTES = 2L * 1024 * 1024 * 1024
    private const val MAX_ENTRIES = 50_000

    // shortcut: single APK imports only; add split/bundle support after atomic multi-APK tests.
    fun inspect(file: File): ApkInspection {
        requireApk(file.length() in 1..MAX_APK_BYTES, "APK must be at most 512 MiB.")
        val abis = linkedSetOf<String>()
        ZipFile(file).use { zip ->
            val names = HashSet<String>()
            val entries = zip.entries()
            var expanded = 0L
            var hasManifest = false
            val buffer = ByteArray(64 * 1024)
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name
                requireApk(names.size < MAX_ENTRIES, "APK has too many ZIP entries.")
                requireApk(name.length <= 4096 && safeEntry(name), "APK contains an unsafe ZIP path.")
                requireApk(names.add(name), "APK contains duplicate ZIP entries.")
                requireApk(!name.endsWith(".apk", ignoreCase = true), "APK bundles are not supported yet. Select a single APK.")
                requireApk(entry.size >= 0 && entry.compressedSize >= 0, "APK has invalid ZIP entry sizes.")
                requireApk(entry.compressedSize <= file.length(), "APK has invalid compressed entry sizes.")
                requireApk(entry.size <= MAX_EXPANDED_BYTES - expanded, "Expanded APK exceeds 2 GiB.")
                // Old game textures can compress extremely well; bound actual expansion instead.
                val native = name.startsWith("lib/") && name.endsWith(".so") && !entry.isDirectory
                requireApk(!name.endsWith(".so") || native || entry.isDirectory,
                    "Native libraries outside the APK lib folder are not supported yet.")
                if (name == "AndroidManifest.xml" && !entry.isDirectory && entry.size > 0) hasManifest = true
                val header = if (native) ByteArray(64) else null
                var headerBytes = 0
                var readBytes = 0L
                val crc = CRC32()
                zip.getInputStream(entry).use { input ->
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        requireApk(count.toLong() <= MAX_EXPANDED_BYTES - expanded, "Expanded APK exceeds 2 GiB.")
                        expanded += count
                        readBytes += count
                        requireApk(readBytes <= entry.size, "APK ZIP entry exceeds its declared size.")
                        crc.update(buffer, 0, count)
                        if (header != null && headerBytes < header.size) {
                            val copied = minOf(count, header.size - headerBytes)
                            buffer.copyInto(header, headerBytes, 0, copied)
                            headerBytes += copied
                        }
                    }
                }
                requireApk(readBytes == entry.size && crc.value == entry.crc, "APK contains a damaged ZIP entry.")
                if (header != null) {
                    val parts = name.split('/')
                    requireApk(parts.size == 3, "APK has an invalid native library path.")
                    val abi = parts[1]
                    val guest = abi in setOf("arm64-v8a", "x86_64") && parts[2].endsWith("-arm32.so")
                    if (guest) {
                        val folder = "lib/$abi/"
                        val standin = parts[2].removeSuffix("-arm32.so") + ".so"
                        requireApk(zip.getEntry(folder + "libarm32_bridge.so") != null &&
                            zip.getEntry(folder + standin) != null, "Guest ARM32 code needs its bridge and matching native loader.")
                    }
                    validateElf(header, headerBytes, if (guest) "armeabi-v7a" else abi)
                    abis.add(abi)
                }
            }
            requireApk(hasManifest, "This file has no base APK manifest.")
        }
        return ApkInspection(abis)
    }

    private fun safeEntry(name: String): Boolean {
        if (name.isEmpty() || name.startsWith('/') || '\\' in name || ':' in name || '\u0000' in name) return false
        return name.removeSuffix("/").split('/').none { it.isEmpty() || it == "." || it == ".." }
    }

    private fun validateElf(header: ByteArray, count: Int, abi: String) {
        fun byte(index: Int) = header[index].toInt() and 255
        requireApk(count >= 52 && byte(0) == 127 && byte(1) == 69 && byte(2) == 76 && byte(3) == 70,
            "Native library is not a valid ELF file.")
        requireApk(byte(5) == 1 && byte(6) == 1, "Native library uses an unsupported ELF format.")
        val expected = when (abi) {
            "armeabi", "armeabi-v7a" -> 1 to 40
            "arm64-v8a" -> 2 to 183
            "x86" -> 1 to 3
            "x86_64" -> 2 to 62
            // Older universal APKs can retain MIPS alongside supported ARM code.
            "mips" -> 1 to 8
            "mips64" -> 2 to 8
            else -> throw IOException("Unsupported native ABI: $abi.")
        }
        requireApk(byte(4) == expected.first && byte(18) + (byte(19) shl 8) == expected.second,
            "Native library architecture does not match its APK folder.")
        requireApk(byte(16) == 3 && byte(17) == 0 && (byte(4) != 2 || count >= 64),
            "Native library has an invalid ELF header.")
    }

    private fun requireApk(condition: Boolean, message: String) {
        if (!condition) throw IOException(message)
    }
}
