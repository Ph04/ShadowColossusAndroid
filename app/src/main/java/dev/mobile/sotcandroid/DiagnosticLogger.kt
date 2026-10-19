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

/**
 * Diagnostic helper shared by the launcher and ISO import worker.
 * The launcher only checks the ISO size; the PC generation pipeline authenticates
 * the boot ELF and module hashes. The report explicitly distinguishes those steps.
 */
object DiagnosticLogger {
    private const val TAG = "SOTC_DIAG"

    @Synchronized
    fun event(context: Context, event: String, detail: String = "") {
        val clean = detail.replace('\n', ' ').replace('\r', ' ')
        val logFile = File(File(context.filesDir, "logs"), "sotc-app.log")
        try {
            logFile.parentFile?.mkdirs()
            FileOutputStream(logFile, true).bufferedWriter(Charsets.UTF_8).use {
                it.appendLine("${timestamp()} [$event] $clean")
            }
        } catch (error: Exception) {
            Log.e(TAG, "Cannot append app diagnostic event: $event", error)
        }
        Log.i(TAG, "$event: $clean")
    }

    fun shareReport(
        activity: Activity,
        isoFile: File,
        expectedIsoBytes: Long,
        runtimeStatus: () -> String,
        engineAvailable: () -> Boolean,
        nativeBuildReport: () -> String,
    ) {
        event(activity, "diagnostic_report_requested")
        val report = buildReport(
            activity, isoFile, expectedIsoBytes, runtimeStatus, engineAvailable, nativeBuildReport,
        )
        event(activity, "diagnostic_report_ready", "characters=${report.length}")
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Shadow of the Colossus Android — relatório completo de diagnóstico")
            putExtra(Intent.EXTRA_TEXT, report.takeLast(180_000))
        }
        activity.startActivity(Intent.createChooser(send, "Compartilhar relatório de diagnóstico"))
    }

    private fun buildReport(
        context: Context,
        isoFile: File,
        expectedIsoBytes: Long,
        runtimeStatus: () -> String,
        engineAvailable: () -> Boolean,
        nativeBuildReport: () -> String,
    ): String {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memory = ActivityManager.MemoryInfo()
        manager?.getMemoryInfo(memory)
        val stat = StatFs(context.filesDir.absolutePath)
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val libraries = runCatching {
            ZipFile(context.applicationInfo.sourceDir).use { apk ->
                listOf(
                    "libsotc_android_bridge.so",
                    "libsotc_android_touch.so",
                    "libps2EntryRunner.so",
                ).joinToString("\n") { name ->
                    val entry = apk.getEntry("lib/arm64-v8a/$name")
                    "$name: packaged=${entry != null}; bytes=${entry?.size ?: 0L}"
                }
            }
        }.getOrElse { "APK_NATIVE_LIBRARY_CHECK_ERROR: ${it.javaClass.name}: ${it.message}" }
        val nativeEngine = safe("native_engine_available") { engineAvailable().toString() }
        val runtime = safe("native_runtime_status", runtimeStatus)
        val nativeReport = safe("native_build_report", nativeBuildReport)
        val isoExists = isoFile.isFile
        val isoBytes = if (isoExists) isoFile.length() else 0L
        val appLog = readTail(File(File(context.filesDir, "logs"), "sotc-app.log"), 45_000)
        val bootLog = readTail(File(File(context.filesDir, "logs"), "sotc-boot.log"), 70_000)
        val privateEntries = context.filesDir.listFiles()?.sortedBy { it.name }?.joinToString("\n") {
            "${it.name}: ${if (it.isDirectory) "directory" else "file"}; bytes=${if (it.isFile) it.length() else -1L}"
        } ?: "(directory listing failed)"
        val logsEntries = File(context.filesDir, "logs").listFiles()?.sortedBy { it.name }?.joinToString("\n") {
            "${it.name}: exists=${it.isFile}; bytes=${if (it.isFile) it.length() else 0L}"
        } ?: "(logs directory absent)"
        val packageInfo = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        val version = packageInfo?.versionName ?: "unknown"
        val availableMemory = if (memory.availMem > 0) memory.availMem else -1L
        val totalMemory = if (memory.totalMem > 0) memory.totalMem else -1L
        val buildVariant = when {
            context.packageName.endsWith(".diagnostic") -> "diagnostic"
            context.packageName.endsWith(".debug") -> "debug"
            else -> "release"
        }
        return buildString {
            appendLine("SHADOW COLOSSUS ANDROID — DIAGNOSTIC REPORT v1")
            appendLine("timestamp=${timestamp()}")
            appendLine("app_version=$version")
            appendLine("package=${context.packageName}")
            appendLine("build_variant=$buildVariant")
            appendLine("android_release=${Build.VERSION.RELEASE}")
            appendLine("android_sdk=${Build.VERSION.SDK_INT}")
            appendLine("manufacturer=${Build.MANUFACTURER}")
            appendLine("model=${Build.MODEL}")
            appendLine("supported_abis=${Build.SUPPORTED_ABIS.joinToString(",")}")
            appendLine("native_library_dir=${context.applicationInfo.nativeLibraryDir}")
            appendLine("files_dir=${context.filesDir.absolutePath}")
            appendLine("available_storage_bytes=${stat.availableBytes}")
            appendLine("total_memory_bytes=$totalMemory")
            appendLine("available_memory_bytes=$availableMemory")
            appendLine("low_memory=${memory.lowMemory}")
            appendLine("runtime_heap_total=${Runtime.getRuntime().totalMemory()}")
            appendLine("runtime_heap_free=${Runtime.getRuntime().freeMemory()}")
            appendLine("iso_exists=$isoExists")
            appendLine("iso_path=${isoFile.absolutePath}")
            appendLine("iso_bytes=$isoBytes")
            appendLine("iso_expected_bytes=$expectedIsoBytes")
            appendLine("iso_exact_size_match=${isoExists && isoBytes == expectedIsoBytes}")
            appendLine("iso_content_validation=NOT_DONE_BY_LAUNCHER; size check is not a hash/module validation")
            appendLine("engine_available=$nativeEngine")
            appendLine("--- native_runtime_status ---")
            appendLine(runtime)
            appendLine("--- native_build_report ---")
            appendLine(nativeReport)
            appendLine("--- native_library_files ---")
            appendLine(libraries)
            appendLine("--- private_data_entries ---")
            appendLine(privateEntries)
            appendLine("--- log_file_inventory ---")
            appendLine(logsEntries)
            appendLine("--- app_log_tail ---")
            appendLine(appLog.ifBlank { "(no app log yet)" })
            appendLine("--- boot_log_tail ---")
            appendLine(bootLog.ifBlank { "(no native boot log; engine entry point may not have run)" })
            appendLine("--- end_report ---")
        }
    }

    private fun safe(label: String, block: () -> String): String =
        runCatching(block).getOrElse { "ERROR [$label]: ${it.javaClass.name}: ${it.message}" }

    private fun readTail(file: File, maxChars: Int): String {
        if (!file.isFile) return ""
        return runCatching { file.readText(Charsets.UTF_8).takeLast(maxChars) }
            .getOrElse { "LOG_READ_ERROR ${it.javaClass.name}: ${it.message}" }
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(Date())
