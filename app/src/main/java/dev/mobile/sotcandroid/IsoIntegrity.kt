package dev.mobile.sotcandroid

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Full-file validation for the supported PAL SCES-53326 v1.00 image.
 * The marker is app-private and bound to size/modification time; changed files are re-hashed.
 */
object IsoIntegrity {
    const val EXPECTED_ISO_BYTES = 4_635_918_336L
    const val EXPECTED_ISO_SHA256 = "068925770645266189005f09c9a9a1fee25726bfc93c89b12f832f3673664ac4"

    private const val BUFFER_BYTES = 1024 * 1024
    private const val PROGRESS_STEP_BYTES = 16L * 1024 * 1024
    private const val HEX = "0123456789abcdef"

    private fun markerFile(isoFile: File): File =
        File(isoFile.parentFile, isoFile.name + ".validation")

    fun isVerified(isoFile: File): Boolean {
        if (!isoFile.isFile || isoFile.length() != EXPECTED_ISO_BYTES) return false
        val marker = markerFile(isoFile)
        if (!marker.isFile) return false
        val values = runCatching {
            marker.readLines(Charsets.US_ASCII).mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator <= 0) null else line.substring(0, separator) to line.substring(separator + 1)
            }.toMap()
        }.getOrNull() ?: return false
        return values["sha256"].equals(EXPECTED_ISO_SHA256, ignoreCase = true) &&
            values["size"] == isoFile.length().toString() &&
            values["last_modified"] == isoFile.lastModified().toString()
    }

    fun writeVerifiedMarker(isoFile: File, actualSha256: String) {
        require(actualSha256.equals(EXPECTED_ISO_SHA256, ignoreCase = true)) {
            "Refusing to mark an ISO with an unexpected SHA-256 as verified."
        }
        val marker = markerFile(isoFile)
        marker.parentFile?.mkdirs()
        marker.writeText(
            "sha256=$actualSha256\nsize=${isoFile.length()}\nlast_modified=${isoFile.lastModified()}\n",
            Charsets.US_ASCII,
        )
    }

    fun calculateSha256(file: File, onProgress: (Long, Long) -> Unit): String {
        require(file.isFile) { "ISO file does not exist." }
        val totalBytes = file.length()
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            var processed = 0L
            var lastReported = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                digest.update(buffer, 0, count)
                processed += count.toLong()
                if (processed - lastReported >= PROGRESS_STEP_BYTES || processed == totalBytes) {
                    onProgress(processed, totalBytes)
                    lastReported = processed
                }
            }
        }
        return toHex(digest.digest())
    }

    fun toHex(bytes: ByteArray): String = buildString(bytes.size * 2) {
        bytes.forEach { byte ->
            val value = byte.toInt() and 0xff
            append(HEX[value ushr 4])
            append(HEX[value and 0x0f])
        }
    }
}
