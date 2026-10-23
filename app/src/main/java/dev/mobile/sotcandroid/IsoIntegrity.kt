package dev.mobile.sotcandroid

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale

/**
 * Tracks a successful structural import of the USA disc. There is intentionally
 * no hard-coded USA image size/hash: we validate the ISO-9660 contents instead.
 * The marker also records the source ISO hash for diagnostics, not authenticity.
 */
object IsoIntegrity {
    const val DISC_DIRECTORY = "disc"
    const val BOOT_ELF = "SCUS_974.72"
    const val MARKER_NAME = ".sotc-usa-disc.validation"

    private const val BUFFER_BYTES = 1024 * 1024
    private const val PROGRESS_STEP_BYTES = 16L * 1024 * 1024
    private const val HEX = "0123456789abcdef"
    private val REQUIRED_FILES = listOf(BOOT_ELF, "KERNEL.XFF", "MANAGER.XFF", "GAMECORE.XFF", "SYSTEM.CNF")

    fun markerFile(gameFolder: File): File = File(gameFolder, MARKER_NAME)

    fun isVerified(gameFolder: File): Boolean {
        val marker = markerFile(gameFolder)
        val disc = File(gameFolder, DISC_DIRECTORY)
        if (!marker.isFile || !disc.isDirectory) return false
        val values = readMarker(gameFolder)
        if (values["schema"] != "1" || values["edition"] != "USA-SCUS_974.72") return false
        val sourceSize = values["source_iso_bytes"]?.toLongOrNull() ?: return false
        val sourceHash = values["source_iso_sha256"] ?: return false
        if (sourceSize <= 0L || !sourceHash.matches(Regex("[0-9a-fA-F]{64}"))) return false
        for (name in REQUIRED_FILES) {
            val file = File(disc, name)
            val storedSize = values["file_" + name.replace('.', '_').lowercase(Locale.ROOT) + "_bytes"]?.toLongOrNull()
                ?: return false
            if (!file.isFile || file.length() <= 0L || file.length() != storedSize) return false
        }
        return (values["file_count"]?.toIntOrNull() ?: 0) > 0 &&
            (values["extracted_bytes"]?.toLongOrNull() ?: 0L) > 0L
    }

    fun readMarker(gameFolder: File): Map<String, String> {
        val marker = markerFile(gameFolder)
        if (!marker.isFile) return emptyMap()
        return runCatching {
            marker.readLines(Charsets.US_ASCII).mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator <= 0) null else line.substring(0, separator) to line.substring(separator + 1)
            }.toMap()
        }.getOrDefault(emptyMap())
    }

    fun writeVerifiedMarker(gameFolder: File, sourceSize: Long, sourceSha256: String, summary: Iso9660Disc.DiscSummary) {
        require(sourceSize > 0L) { "Source ISO size must be positive." }
        require(sourceSha256.matches(Regex("[0-9a-fA-F]{64}"))) { "Source ISO SHA-256 must be 64 hex digits." }
        val disc = File(gameFolder, DISC_DIRECTORY)
        require(disc.isDirectory) { "USA disc directory is missing." }

        val fileSizes = REQUIRED_FILES.associateWith { name ->
            val item = File(disc, name)
            require(item.isFile && item.length() > 0L) { "Required USA disc file missing: " + name }
            item.length()
        }
        val marker = markerFile(gameFolder)
        if (!gameFolder.exists() && !gameFolder.mkdirs()) throw IllegalStateException("Could not create private game folder.")
        val temporary = File(gameFolder, MARKER_NAME + ".partial")
        val values = linkedMapOf(
            "schema" to "1",
            "edition" to "USA-SCUS_974.72",
            "source_iso_bytes" to sourceSize.toString(),
            "source_iso_sha256" to sourceSha256.lowercase(Locale.ROOT),
            "file_count" to summary.fileCount.toString(),
            "extracted_bytes" to summary.extractedBytes.toString(),
            "file_scus_974_72_bytes" to fileSizes.getValue(BOOT_ELF).toString(),
            "file_kernel_xff_bytes" to fileSizes.getValue("KERNEL.XFF").toString(),
            "file_manager_xff_bytes" to fileSizes.getValue("MANAGER.XFF").toString(),
            "file_gamecore_xff_bytes" to fileSizes.getValue("GAMECORE.XFF").toString(),
            "file_system_cnf_bytes" to fileSizes.getValue("SYSTEM.CNF").toString(),
            "boot_elf_bytes" to summary.bootElfBytes.toString(),
            "created_epoch_ms" to System.currentTimeMillis().toString(),
        )
        temporary.writeText(values.entries.joinToString(separator = "\n", postfix = "\n") { it.key + "=" + it.value }, Charsets.US_ASCII)
        if (marker.exists() && !marker.delete()) {
            temporary.delete()
            throw IllegalStateException("Could not replace the previous disc validation marker.")
        }
        if (!temporary.renameTo(marker)) {
            temporary.delete()
            throw IllegalStateException("Could not finalize the disc validation marker.")
        }
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
