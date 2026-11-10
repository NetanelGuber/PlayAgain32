package dev.playagain32.app

import java.io.File
import java.io.IOException
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class ApkInspectionTest {
    private fun inspect(vararg entries: Pair<String, ByteArray>): ApkInspection {
        val file = File.createTempFile("apk-inspection-", ".apk")
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                for ((name, bytes) in entries) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
            return ApkInspector.inspect(file)
        } finally {
            file.delete()
        }
    }

    private val manifest = "AndroidManifest.xml" to byteArrayOf(3, 0, 8, 0)

    private fun elf(is64: Boolean = false, machine: Int = if (is64) 183 else 40) = ByteArray(64).apply {
        this[0] = 127
        this[1] = 69
        this[2] = 76
        this[3] = 70
        this[4] = if (is64) 2 else 1
        this[5] = 1
        this[6] = 1
        this[16] = 3
        this[18] = machine.toByte()
    }

    private fun rejected(vararg entries: Pair<String, ByteArray>): IOException =
        assertThrows(IOException::class.java) { inspect(*entries) }

    @Test fun noPackagedLibrariesIsJavaOnly() {
        val result = inspect(manifest, "classes.dex" to byteArrayOf(1, 2, 3))
        assertTrue(result.javaOnly)
        assertFalse(result.arm32Only)
    }

    @Test fun readsElfArchitectureInsteadOfTrustingFolderName() {
        val result = inspect(manifest, "lib/armeabi-v7a/libgame.so" to elf())
        assertEquals(setOf("armeabi-v7a"), result.nativeAbis)
        assertTrue(result.arm32Only)
        rejected(manifest, "lib/arm64-v8a/libgame.so" to elf())
        rejected(manifest, "lib/armeabi-v7a/libgame.so" to byteArrayOf(1, 2, 3))
    }

    @Test fun universalArmApkAlreadyHas64BitCode() {
        val result = inspect(manifest, "lib/armeabi-v7a/libgame.so" to elf(), "lib/arm64-v8a/libgame.so" to elf(true))
        assertFalse(result.javaOnly)
        assertFalse(result.arm32Only)
        assertEquals(2, result.nativeAbis.size)
    }

    @Test fun convertedGuestLibrariesRequireAValidBridgeAndMatchingLoader() {
        val bridge = "lib/arm64-v8a/libarm32_bridge.so" to elf(true)
        val loader = "lib/arm64-v8a/libgame.so" to elf(true)
        val guest = "lib/arm64-v8a/libgame-arm32.so" to elf()
        assertEquals(setOf("arm64-v8a"), inspect(manifest, bridge, loader, guest).nativeAbis)
        rejected(manifest, loader, guest)
        rejected(manifest, bridge, guest)
        rejected(manifest, bridge, "lib/arm64-v8a/libgame.so" to elf(), guest)
        rejected(manifest, bridge, loader, "lib/arm64-v8a/libgame-arm32.so" to elf(true))
    }

    @Test fun unsupportedOrMisplacedLibrariesAreRejected() {
        rejected(manifest, "lib/unknown/libgame.so" to elf())
        rejected(manifest, "assets/libgame.so" to elf())
        rejected(manifest, "lib/armeabi-v7a/nested/libgame.so" to elf())
    }

    @Test fun obsoleteExtraAbisDoNotRejectAnOtherwiseCompatibleUniversalApk() {
        val result = inspect(manifest, "lib/arm64-v8a/libgame.so" to elf(true),
            "lib/mips/libgame.so" to elf(machine = 8), "lib/mips64/libgame.so" to elf(true, 8))
        assertEquals(setOf("arm64-v8a", "mips", "mips64"), result.nativeAbis)
        rejected(manifest, "lib/mips/libgame.so" to elf(machine = 40))
    }

    @Test fun pathTraversalAndAmbiguousZipPathsAreRejected() {
        for (path in listOf("../escape", "/escape", "assets/../escape", "assets\\escape", "assets//escape", "C:/escape")) {
            rejected(manifest, path to byteArrayOf(1))
        }
    }

    @Test fun bundlesAndMissingManifestAreRejected() {
        rejected("classes.dex" to byteArrayOf(1))
        rejected("AndroidManifest.xml" to byteArrayOf())
        rejected(manifest, "base.apk" to byteArrayOf(1))
    }

    @Test fun highlyCompressedGameTexturesWithinExpansionLimitAreAccepted() {
        val result = inspect(manifest, "assets/data/images/texture.pvr" to ByteArray(1396788))
        assertTrue(result.javaOnly)
    }

    @Test fun declaredZipBombExpansionIsRejectedBeforeInflation() {
        val file = File.createTempFile("apk-expansion-", ".apk")
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
                zip.write(manifest.second)
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("assets/bomb"))
                zip.write(byteArrayOf(0))
                zip.closeEntry()
            }
            val bytes = file.readBytes()
            val signature = byteArrayOf(0x50, 0x4b, 0x01, 0x02)
            val headers = (0..bytes.size - 46).filter { index ->
                signature.indices.all { bytes[index + it] == signature[it] }
            }
            assertEquals(2, headers.size)
            // The central directory declares 2 GiB + 1, without allocating/inflating it.
            val sizeOffset = headers.last() + 24
            byteArrayOf(1, 0, 0, 0x80.toByte()).copyInto(bytes, sizeOffset)
            file.writeBytes(bytes)
            val error = assertThrows(IOException::class.java) { ApkInspector.inspect(file) }
            assertTrue(error.message!!.contains("2 GiB"))
        } finally {
            file.delete()
        }
    }

    @Test fun duplicateZipNamesAreRejected() {
        val file = File.createTempFile("apk-duplicate-", ".apk")
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                for (name in listOf("AndroidManifest.xml", "one.txt", "two.txt")) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(byteArrayOf(1))
                    zip.closeEntry()
                }
            }
            val bytes = file.readBytes()
            val from = "two.txt".toByteArray()
            val to = "one.txt".toByteArray()
            for (index in 0..bytes.size - from.size) {
                if (from.indices.all { bytes[index + it] == from[it] }) to.copyInto(bytes, index)
            }
            file.writeBytes(bytes)
            val error = assertThrows(IOException::class.java) { ApkInspector.inspect(file) }
            assertTrue(error.message!!.contains("duplicate"))
        } finally {
            file.delete()
        }
    }

    @Test fun damagedStoredEntryIsRejectedByCrc() {
        val file = File.createTempFile("apk-corruption-", ".apk")
        try {
            val payload = byteArrayOf(41, 42, 43, 44)
            ZipOutputStream(file.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("AndroidManifest.xml").apply {
                    method = ZipEntry.STORED
                    size = payload.size.toLong()
                    compressedSize = size
                    crc = CRC32().apply { update(payload) }.value
                })
                zip.write(payload)
                zip.closeEntry()
            }
            val bytes = file.readBytes()
            val dataOffset = 30 + "AndroidManifest.xml".length
            bytes[dataOffset] = 99
            file.writeBytes(bytes)
            assertThrows(IOException::class.java) { ApkInspector.inspect(file) }
        } finally {
            file.delete()
        }
    }
}
