package dev.playagain32.app

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

data class ImportedApk(val file: File, val inspection: ApkInspection, val sha256: String)

/** Publishes only fully copied and inspected APKs; callers retain ownership of the input stream. */
object ApkStore {
    @Synchronized
    fun importApk(input: InputStream, directory: File, declaredSize: Long = -1): ImportedApk {
        if (declaredSize > ApkInspector.MAX_APK_BYTES) throw IOException("APK must be at most 512 MiB.")
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create the imported games folder.")
        val reserve = 16L * 1024 * 1024
        if (directory.usableSpace < maxOf(0L, declaredSize) + reserve) throw IOException("Not enough free space to import this APK.")
        val temporary = File.createTempFile("import-", ".part", directory)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            FileOutputStream(temporary).use { target ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    if (count.toLong() > ApkInspector.MAX_APK_BYTES - total) throw IOException("APK must be at most 512 MiB.")
                    if (directory.usableSpace < reserve + count) throw IOException("Not enough free space to import this APK.")
                    target.write(buffer, 0, count)
                    digest.update(buffer, 0, count)
                    total += count
                }
                target.fd.sync()
            }
            val inspection = ApkInspector.inspect(temporary)
            val hash = digest.digest()
            val sha256 = hash.joinToString("") { "%02x".format(it.toInt() and 255) }
            val destination = File(directory, "$sha256.apk")
            if (destination.exists()) {
                if (destination.length() != total) throw IOException("An imported APK is damaged. Remove it before importing again.")
                val oldDigest = MessageDigest.getInstance("SHA-256")
                destination.inputStream().use { source ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        if (count > 0) oldDigest.update(buffer, 0, count)
                    }
                }
                if (!MessageDigest.isEqual(hash, oldDigest.digest())) throw IOException("An imported APK is damaged. Remove it before importing again.")
            } else if (!temporary.renameTo(destination)) {
                throw IOException("Cannot save the imported APK.")
            }
            return ImportedApk(destination, inspection, sha256)
        } finally {
            temporary.delete()
        }
    }
}
