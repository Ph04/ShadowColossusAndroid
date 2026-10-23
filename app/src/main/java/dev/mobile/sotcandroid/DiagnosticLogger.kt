package dev.mobile.sotcandroid

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.StatFs
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipFile

/** Diagnostic helper shared by the launcher and ISO import worker. */
object DiagnosticLogger {
    private const val TAG = "SOTC_DIAG"

    @Synchronized
    fun event(context: Context, event: String, detail: String = "") {
        val clean = detail.replace('\n', ' ').replace('\r', ' ')
        val logFile = File(File(context.filesDir, "logs"), "sotc-app.log")
        try {
            logFile.parentFile?.mkdirs()
            FileOutputStream(logFile, true).bufferedWriter(Charsets.UTF_8).use {
                it.appendLine(timestamp() + " [" + event + "] " + clean)
            }
        } catch (error: Exception) {
            Log.e(TAG, "Cannot append app diagnostic event: " + event, error)
        }
        Log.i(TAG, event + ": " + clean)
    }

    fun shareReport(
        activity: Activity,
        gameFolder: File,
        runtimeStatus: () -> String,
        engineAvailable: () -> Boolean,
        nativeBuildReport: () -> String,
    ) {
        event(activity, "diagnostic_report_requested")
        val report = buildReport(activity, gameFolder, runtimeStatus, engineAvailable, nativeBuildReport)
        event(activity, "diagnostic_report_ready", "characters=" + report.length)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Shadow of the Colossus Android — diagnóstico Celosia USA")
            putExtra(Intent.EXTRA_TEXT, report.takeLast(180_000))
        }
        activity.startActivity(Intent.createChooser(send, "Compartilhar relatório de diagnóstico"))
    }

    private fun buildReport(
        context: Context,
        gameFolder: File,
        runtimeStatus: () -> String,
        engineAvailable: () -> Boolean,
        nativeBuildReport: () -> String,
    ): String {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memory = ActivityManager.MemoryInfo()
        manager?.getMemoryInfo(memory)
        val stat = StatFs(context.filesDir.absolutePath)
        val libraries = runCatching {
            ZipFile(context.applicationInfo.sourceDir).use { apk ->
                listOf(
                    "libsotc_android_bridge.so",
                    "libsotc_android_touch.so",
                    "libps2EntryRunner.so",
                ).joinToString("\n") { name ->
                    val entry = apk.getEntry("lib/arm64-v8a/" + name)
                    name + ": packaged=" + (entry != null) + "; bytes=" + (entry?.size ?: 0L)
                }
            }
        }.getOrElse {
            "APK_NATIVE_LIBRARY_CHECK_ERROR: " + it.javaClass.name + ": " + it.message
        }
        val engine = safe("native_engine_available") { engineAvailable().toString() }
        val runtime = safe("native_runtime_status", runtimeStatus)
        val nativeReport = safe("native_build_report", nativeBuildReport)
        val marker = IsoIntegrity.readMarker(gameFolder)
        val disc = File(gameFolder, IsoIntegrity.DISC_DIRECTORY)
        val importantFiles = listOf("SCUS_974.72", "KERNEL.XFF", "MANAGER.XFF", "GAMECORE.XFF", "SYSTEM.CNF")
        val fileInventory = importantFiles.joinToString("\n") { name ->
            val file = File(disc, name)
            name + ": exists=" + file.isFile + "; bytes=" + (if (file.isFile) file.length() else 0L)
        }
        val dataInventory = gameFolder.listFiles()?.sortedBy { it.name }?.joinToString("\n") { item ->
            item.name + ": " + (if (item.isDirectory) "directory" else "file") +
                "; bytes=" + (if (item.isFile) item.length() else -1L)
        } ?: "(game directory absent)"
        val logsDirectory = File(context.filesDir, "logs")
        val logsEntries = logsDirectory.listFiles()?.sortedBy { it.name }?.joinToString("\n") { item ->
            item.name + ": exists=" + item.isFile + "; bytes=" + (if (item.isFile) item.length() else 0L)
        } ?: "(logs directory absent)"
        val appLog = readTail(File(logsDirectory, "sotc-app.log"), 45_000)
        val bootLog = readTail(File(logsDirectory, "sotc-boot.log"), 70_000)
        val packageInfo = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        val availableMemory = if (memory.availMem > 0) memory.availMem else -1L
        val totalMemory = if (memory.totalMem > 0) memory.totalMem else -1L
        val buildVariant = when {
            context.packageName.endsWith(".diagnostic") -> "diagnostic"
            context.packageName.endsWith(".debug") -> "debug"
            else -> "release"
        }
        return buildString {
            appendLine("SHADOW COLOSSUS ANDROID — CELOSIA USA DIAGNOSTIC REPORT v2")
            appendLine("timestamp=" + timestamp())
            appendLine("app_version=" + (packageInfo?.versionName ?: "unknown"))
            appendLine("package=" + context.packageName)
            appendLine("build_variant=" + buildVariant)
            appendLine("android_release=" + Build.VERSION.RELEASE)
            appendLine("android_sdk=" + Build.VERSION.SDK_INT)
            appendLine("manufacturer=" + Build.MANUFACTURER)
            appendLine("model=" + Build.MODEL)
            appendLine("supported_abis=" + Build.SUPPORTED_ABIS.joinToString(","))
            appendLine("native_library_dir=" + context.applicationInfo.nativeLibraryDir)
            appendLine("files_dir=" + context.filesDir.absolutePath)
            appendLine("available_storage_bytes=" + stat.availableBytes)
            appendLine("total_memory_bytes=" + totalMemory)
            appendLine("available_memory_bytes=" + availableMemory)
            appendLine("low_memory=" + memory.lowMemory)
            appendLine("runtime_heap_total=" + Runtime.getRuntime().totalMemory())
            appendLine("runtime_heap_free=" + Runtime.getRuntime().freeMemory())
            appendLine("usa_disc_structurally_verified=" + IsoIntegrity.isVerified(gameFolder))
            appendLine("usa_disc_directory=" + disc.absolutePath)
            appendLine("source_iso_bytes=" + (marker["source_iso_bytes"] ?: "unknown"))
            appendLine("source_iso_sha256=" + (marker["source_iso_sha256"] ?: "unknown"))
            appendLine("source_iso_hash_is_official_authenticity_proof=false")
            appendLine("extracted_file_count=" + (marker["file_count"] ?: "unknown"))
            appendLine("extracted_payload_bytes=" + (marker["extracted_bytes"] ?: "unknown"))
            appendLine("--- required_usa_disc_files ---")
            appendLine(fileInventory)
            appendLine("engine_available=" + engine)
            appendLine("--- native_runtime_status ---")
            appendLine(runtime)
            appendLine("--- native_build_report ---")
            appendLine(nativeReport)
            appendLine("--- native_library_files_in_apk ---")
            appendLine(libraries)
            appendLine("--- private_game_data_inventory ---")
            appendLine(dataInventory)
            appendLine("--- log_file_inventory ---")
            appendLine(logsEntries)
            appendLine("--- app_log_tail ---")
            appendLine(if (appLog.isBlank()) "(no app log yet)" else appLog)
            appendLine("--- boot_log_tail ---")
            appendLine(if (bootLog.isBlank()) "(no native boot log; engine entry point may not have run)" else bootLog)
            appendLine("--- end_report ---")
        }
    }

    private fun safe(label: String, block: () -> String): String =
        runCatching(block).getOrElse {
            "ERROR [" + label + "]: " + it.javaClass.name + ": " + it.message
        }

    private fun readTail(file: File, maxChars: Int): String {
        if (!file.isFile) return ""
        return runCatching { file.readText(Charsets.UTF_8).takeLast(maxChars) }
            .getOrElse { "LOG_READ_ERROR " + it.javaClass.name + ": " + it.message }
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(Date())
}
