package dev.playagain32.app

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class ApkStoreTest {
    private fun apk(): ByteArray = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zip.write(byteArrayOf(3, 0, 8, 0))
            zip.closeEntry()
        }
    }.toByteArray()

    private fun inDirectory(test: (File) -> Unit) {
        val directory = Files.createTempDirectory("apk-store-test-").toFile()
        try { test(directory) } finally { directory.deleteRecursively() }
    }

    @Test fun preservesOriginalAndDeduplicatesByContent() = inDirectory { directory ->
        val bytes = apk()
        val original = File(directory, "original.apk").apply { writeBytes(bytes) }
        val imported = File(directory, "games")
        val first = original.inputStream().use { ApkStore.importApk(it, imported, bytes.size.toLong()) }
        val second = bytes.inputStream().use { ApkStore.importApk(it, imported) }
        val expectedHash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        assertEquals("$expectedHash.apk", first.file.name)
        assertEquals(first.file, second.file)
        assertArrayEquals(bytes, original.readBytes())
        assertArrayEquals(bytes, first.file.readBytes())
        assertEquals(1, imported.listFiles()!!.size)
    }

    @Test fun interruptedCopyNeverPublishesOrLeavesPartialFile() = inDirectory { directory ->
        val failure = object : InputStream() {
            var reads = 0
            override fun read(): Int = throw IOException("document provider disconnected")
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (reads++ > 0) throw IOException("document provider disconnected")
                buffer[offset] = 42
                return 1
            }
        }
        assertThrows(IOException::class.java) { ApkStore.importApk(failure, directory) }
        assertEquals(0, directory.listFiles()!!.size)
    }

    @Test fun invalidArchiveNeverPublishes() = inDirectory { directory ->
        assertThrows(IOException::class.java) { ApkStore.importApk(byteArrayOf(1, 2, 3).inputStream(), directory) }
        assertEquals(0, directory.listFiles()!!.size)
    }

    @Test fun corruptedDuplicateDoesNotOverwriteExistingData() = inDirectory { directory ->
        val bytes = apk()
        val imported = ApkStore.importApk(bytes.inputStream(), directory)
        val corrupted = imported.file.readBytes().apply { this[0] = 99 }
        imported.file.writeBytes(corrupted)
        assertThrows(IOException::class.java) { ApkStore.importApk(bytes.inputStream(), directory) }
        assertArrayEquals(corrupted, imported.file.readBytes())
        assertEquals(1, directory.listFiles()!!.size)
    }

    @Test fun oversizedDeclaredSourceIsRejectedWithoutReading() = inDirectory { directory ->
        val input = object : InputStream() {
            override fun read(): Int = throw AssertionError("must not read oversized source")
        }
        assertThrows(IOException::class.java) { ApkStore.importApk(input, directory, ApkInspector.MAX_APK_BYTES + 1) }
        assertEquals(0, directory.listFiles()!!.size)
    }

    @Test fun fullStorageIsRejectedBeforeReadingOrCreatingPartialFile() = inDirectory { directory ->
        val full = object : File(directory.path) {
            override fun getUsableSpace(): Long = 0
        }
        val input = object : InputStream() {
            override fun read(): Int = throw AssertionError("must not read on full storage")
        }
        assertThrows(IOException::class.java) { ApkStore.importApk(input, full) }
        assertEquals(0, directory.listFiles()!!.size)
    }
}
