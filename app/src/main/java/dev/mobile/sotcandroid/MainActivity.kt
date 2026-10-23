package dev.mobile.sotcandroid

import android.content.Intent
import android.net.Uri
import android.database.Cursor
import android.os.Bundle
import android.os.StatFs
import android.provider.OpenableColumns
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MainActivity : ComponentActivity() {
    companion object {
        private const val COPY_BUFFER_BYTES = 1024 * 1024
        private const val PROGRESS_STEP_BYTES = 16L * 1024 * 1024
        private const val MIN_ISO_BYTES = 256L * 1024 * 1024
        private const val MAX_ISO_BYTES = 10L * 1024 * 1024 * 1024
        private const val SAFETY_SPACE_BYTES = 512L * 1024 * 1024

        init { System.loadLibrary("sotc_android_bridge") }
    }

    private external fun nativeRuntimeStatus(): String
    private external fun nativeEngineAvailable(): Boolean
    private external fun nativeSetIsoPath(path: String): Boolean
    private external fun nativeDiagnosticReport(): String

    private lateinit var gameStatus: TextView
    private lateinit var runtimeStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var importButton: Button
    private lateinit var playButton: Button
    private lateinit var engineModeStatus: TextView

    @Volatile private var importRunning = false

    private val isoPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(::importIso)
    }

    private val exportPicker = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(::exportCelosiaBundle)
    }

    private val gameFolder: File get() = File(filesDir, "game")
    private val discFolder: File get() = File(gameFolder, IsoIntegrity.DISC_DIRECTORY)
    private val bootElf: File get() = File(discFolder, IsoIntegrity.BOOT_ELF)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(32), dp(24), dp(24))
        }
        val scroll = ScrollView(this).apply { addView(content) }

        content.addView(TextView(this).apply {
            text = "SHADOW OF THE COLOSSUS"
            textSize = 24f
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }, matchWidthWrap())
        content.addView(TextView(this).apply {
            text = "ANDROID ARM64 · CELOSIA USA"
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(20))
        }, matchWidthWrap())
        content.addView(TextView(this).apply {
            text = "Importe uma ISO da sua própria cópia americana (SCUS-97472). O launcher verifica a estrutura ISO-9660, procura SCUS_974.72 e os três módulos XFF e extrai os arquivos para o armazenamento privado."
            textSize = 15f
            gravity = Gravity.CENTER
        }, matchWidthWrap())

        importButton = Button(this).apply {
            text = "Selecionar ISO americana"
            setOnClickListener { isoPicker.launch(arrayOf("application/octet-stream", "application/x-iso9660-image", "*/*")) }
        }
        content.addView(importButton, matchWidthWrap().apply { topMargin = dp(16) })

        gameStatus = TextView(this).apply {
            textSize = 14f
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        content.addView(gameStatus, matchWidthWrap().apply { topMargin = dp(8) })
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progress = 0
            isIndeterminate = false
        }
        content.addView(progressBar, matchWidthWrap().apply { topMargin = dp(8); bottomMargin = dp(12) })

        playButton = Button(this).apply {
            text = "Engine Celosia ainda não integrada"
            isEnabled = false
            setOnClickListener { launchGame() }
        }
        content.addView(playButton, matchWidthWrap())

        val runtimeCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundColor(0xFFECEFF1.toInt())
        }
        runtimeStatus = TextView(this).apply { textSize = 14f }
        runtimeCard.addView(TextView(this).apply {
            text = "Estado da engine"
            textSize = 16f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }, matchWidthWrap())
        runtimeCard.addView(runtimeStatus, matchWidthWrap().apply { topMargin = dp(8) })
        content.addView(runtimeCard, matchWidthWrap().apply { topMargin = dp(24) })

        val engineModeCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundColor(0xFFF2F4F7.toInt())
        }
        engineModeCard.addView(TextView(this).apply {
            text = "Engine recompilada antes da instalação"
            textSize = 16f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }, matchWidthWrap())
        engineModeCard.addView(TextView(this).apply {
            text = "A geração C++ dos módulos USA ocorre no computador de desenvolvimento. O telefone recebe a ISO do usuário, extrai os dados e executa o código ARM64 incluído no APK."
            textSize = 13f
            setPadding(0, dp(6), 0, dp(8))
        }, matchWidthWrap())
        engineModeStatus = TextView(this).apply {
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        engineModeCard.addView(engineModeStatus, matchWidthWrap())
        content.addView(engineModeCard, matchWidthWrap().apply { topMargin = dp(16) })

        content.addView(Button(this).apply {
            text = "Gerar e compartilhar diagnóstico completo"
            setOnClickListener {
                DiagnosticLogger.shareReport(
                    activity = this@MainActivity,
                    gameFolder = gameFolder,
                    runtimeStatus = { nativeRuntimeStatus() },
                    engineAvailable = { nativeEngineAvailable() },
                    nativeBuildReport = { nativeDiagnosticReport() },
                )
            }
        }, matchWidthWrap().apply { topMargin = dp(12) })

        content.addView(Button(this).apply {
            text = "Exportar pacote para geração Celosia"
            setOnClickListener {
                if (!IsoIntegrity.isVerified(gameFolder)) {
                    Toast.makeText(this@MainActivity, "Importe primeiro a ISO USA.", Toast.LENGTH_LONG).show()
                } else {
                    exportPicker.launch("Celosia-USA-preparation.zip")
                }
            }
        }, matchWidthWrap().apply { topMargin = dp(8) })

        content.addView(TextView(this).apply {
            text = "O pacote inclui apenas os arquivos principais extraídos do disco, logs e eventuais saídas de geração colocadas em files/celosia-output. Não inclui a ISO inteira nem os saves. A exportação não executa por si só a recompilação Celosia."
            textSize = 12f
            setPadding(0, dp(6), 0, 0)
        }, matchWidthWrap())

        content.addView(TextView(this).apply {
            text = "Após a importação, a ISO temporária é removida para economizar espaço. Os arquivos extraídos e os saves permanecem no armazenamento privado. A build automática sem geração local ainda é apenas diagnóstica."
            textSize = 13f
            setPadding(0, dp(20), 0, 0)
        }, matchWidthWrap())

        setContentView(scroll)
        DiagnosticLogger.event(this, "app_start", "package=" + packageName + "; version=" + packageManager.getPackageInfo(packageName, 0).versionName + "; android=" + android.os.Build.VERSION.RELEASE + "; model=" + android.os.Build.MANUFACTURER + "/" + android.os.Build.MODEL)
        refreshGameStatus()
        refreshRuntimeStatus()
        refreshLaunchButton()
    }

    override fun onResume() {
        super.onResume()
        if (::gameStatus.isInitialized) {
            refreshGameStatus()
            refreshLaunchButton()
        }
    }

    private fun queryDeclaredSize(uri: android.net.Uri): Long {
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor: Cursor ->
                if (cursor.moveToFirst()) {
                    val column = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (column >= 0 && !cursor.isNull(column)) cursor.getLong(column) else -1L
                } else -1L
            } ?: -1L
        } catch (_: Exception) { -1L }
    }

    private fun importIso(uri: android.net.Uri) {
        if (importRunning) return
        importRunning = true
        importButton.isEnabled = false
        playButton.isEnabled = false
        progressBar.progress = 0
        progressBar.isIndeterminate = true
        gameStatus.text = "Preparando importação da ISO americana…"
        DiagnosticLogger.event(this, "usa_iso_import_started", "uri_scheme=" + (uri.scheme ?: "unknown"))

        Thread {
            val sourceIso = File(gameFolder, "source.iso.partial")
            val stagedDisc = File(gameFolder, "disc.partial")
            val previousDisc = File(gameFolder, "disc.previous")
            var movedPrevious = false
            var publishedNewDisc = false
            var markerHadPrevious = false
            var previousMarker: String? = null
            try {
                if (!gameFolder.exists() && !gameFolder.mkdirs()) throw IOException("Não foi possível criar a pasta privada do jogo.")
                val declaredSize = queryDeclaredSize(uri)
                if (declaredSize > MAX_ISO_BYTES || (declaredSize > 0L && declaredSize < MIN_ISO_BYTES)) {
                    throw IOException("O tamanho informado não é compatível com uma imagem de DVD do PS2.")
                }
                val statBefore = StatFs(filesDir.absolutePath)
                val estimated = if (declaredSize > 0L) declaredSize * 2L + SAFETY_SPACE_BYTES else 3L * 1024 * 1024 * 1024
                DiagnosticLogger.event(this@MainActivity, "usa_iso_space_check", "available_bytes=" + statBefore.availableBytes + "; estimated_required_bytes=" + estimated + "; declared_size=" + declaredSize)
                if (statBefore.availableBytes < estimated) {
                    throw IOException("Espaço insuficiente para importar e extrair o disco. Libere aproximadamente " + formatGiB(estimated) + " GiB.")
                }

                sourceIso.delete()
                stagedDisc.deleteRecursively()
                var total = 0L
                var lastReported = 0L
                val digest = MessageDigest.getInstance("SHA-256")
                val stream = contentResolver.openInputStream(uri) ?: throw IOException("O Android não conseguiu abrir o arquivo selecionado.")
                stream.use { input ->
                    FileOutputStream(sourceIso).use { output ->
                        val buffer = ByteArray(COPY_BUFFER_BYTES)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue
                            total += count.toLong()
                            if (total > MAX_ISO_BYTES) throw IOException("A imagem excede o limite de tamanho suportado para uma ISO de DVD.")
                            digest.update(buffer, 0, count)
                            output.write(buffer, 0, count)
                            if (total - lastReported >= PROGRESS_STEP_BYTES) {
                                lastReported = total
                                val progress = if (declaredSize > 0L) (total * 500L / declaredSize).toInt().coerceIn(0, 500) else 0
                                runOnUiThread {
                                    progressBar.isIndeterminate = declaredSize <= 0L
                                    progressBar.progress = progress
                                    gameStatus.text = if (declaredSize > 0L) {
                                        "Copiando ISO: " + (total * 100L / declaredSize).coerceIn(0, 100) + "% — " + formatGiB(total) + " GiB"
                                    } else {
                                        "Copiando ISO: " + formatGiB(total) + " GiB"
                                    }
                                }
                            }
                        }
                        output.fd.sync()
                    }
                }
                if (total < MIN_ISO_BYTES || total % 2048L != 0L) {
                    throw IOException("O arquivo selecionado não tem o tamanho/alinhamento de uma ISO de DVD válida.")
                }
                if (declaredSize > 0L && declaredSize != total) {
                    DiagnosticLogger.event(this@MainActivity, "usa_iso_declared_size_mismatch", "provider_size=" + declaredSize + "; actual_size=" + total)
                }
                val sourceSha256 = IsoIntegrity.toHex(digest.digest())
                DiagnosticLogger.event(this@MainActivity, "usa_iso_sha256_calculated", "bytes=" + total + "; sha256=" + sourceSha256 + "; expected_hash=unknown_for_this_revision")
                runOnUiThread {
                    progressBar.isIndeterminate = false
                    progressBar.progress = 500
                    gameStatus.text = "Validando a estrutura ISO-9660 e extraindo os arquivos da edição USA…"
                }

                val summary = Iso9660Disc.extract(sourceIso, stagedDisc) { done, payload ->
                    val progress = if (payload > 0L) (500L + done * 500L / payload).toInt().coerceIn(500, 1000) else 1000
                    runOnUiThread {
                        progressBar.isIndeterminate = false
                        progressBar.progress = progress
                        val percent = if (payload > 0L) (done * 100L / payload).coerceIn(0, 100) else 100
                        gameStatus.text = "Extraindo conteúdo do disco: " + percent + "%"
                    }
                }

                val marker = IsoIntegrity.markerFile(gameFolder)
                markerHadPrevious = marker.isFile
                previousMarker = if (markerHadPrevious) runCatching { marker.readText(Charsets.US_ASCII) }.getOrNull() else null
                if (previousDisc.exists()) previousDisc.deleteRecursively()
                if (discFolder.exists()) {
                    if (!discFolder.renameTo(previousDisc)) throw IOException("Não foi possível preservar temporariamente a pasta do disco anterior.")
                    movedPrevious = true
                }
                if (!stagedDisc.renameTo(discFolder)) throw IOException("Extração concluída, mas não foi possível publicar os arquivos.")
                publishedNewDisc = true
                IsoIntegrity.writeVerifiedMarker(gameFolder, total, sourceSha256, summary)
                if (previousDisc.exists()) previousDisc.deleteRecursively()
                sourceIso.delete()
                File(gameFolder, "SCES-53326.iso").delete()
                File(gameFolder, "SCES-53326.iso.validation").delete()

                DiagnosticLogger.event(this@MainActivity, "usa_disc_import_completed", "source_bytes=" + total + "; source_sha256=" + sourceSha256 + "; files=" + summary.fileCount + "; extracted_bytes=" + summary.extractedBytes + "; boot_bytes=" + summary.bootElfBytes + "; structural_validation=true")
                runOnUiThread {
                    importRunning = false
                    progressBar.isIndeterminate = false
                    progressBar.progress = 1000
                    importButton.isEnabled = true
                    refreshGameStatus()
                    refreshLaunchButton()
                }
            } catch (error: Exception) {
                DiagnosticLogger.event(this@MainActivity, "usa_disc_import_failed", "type=" + error.javaClass.name + "; message=" + (error.message ?: "no-message"))
                sourceIso.delete()
                stagedDisc.deleteRecursively()
                if (publishedNewDisc) discFolder.deleteRecursively()
                if (movedPrevious && previousDisc.exists()) previousDisc.renameTo(discFolder)
                val marker = IsoIntegrity.markerFile(gameFolder)
                if (markerHadPrevious && previousMarker != null) {
                    runCatching { marker.writeText(previousMarker!!, Charsets.US_ASCII) }
                } else if (!IsoIntegrity.isVerified(gameFolder)) {
                    marker.delete()
                }
                runOnUiThread {
                    importRunning = false
                    progressBar.isIndeterminate = false
                    progressBar.progress = 0
                    gameStatus.text = "Importação/validação não concluída: " + (error.message ?: error.javaClass.simpleName)
                    importButton.isEnabled = true
                    refreshLaunchButton()
                }
            }
        }.start()
    }

    private fun refreshGameStatus() {
        val marker = IsoIntegrity.readMarker(gameFolder)
        val verified = IsoIntegrity.isVerified(gameFolder)
        val requiredNames = listOf("SCUS_974.72", "KERNEL.XFF", "MANAGER.XFF", "GAMECORE.XFF", "SYSTEM.CNF")
        val missing = requiredNames.filterNot { File(discFolder, it).isFile }
        DiagnosticLogger.event(
            this, "usa_disc_status",
            "disc_directory=" + discFolder.isDirectory +
                "; marker_present=" + IsoIntegrity.markerFile(gameFolder).isFile +
                "; structurally_verified=" + verified +
                "; source_iso_bytes=" + (marker["source_iso_bytes"] ?: "0") +
                "; source_iso_sha256=" + (marker["source_iso_sha256"] ?: "unknown") +
                "; missing=" + missing.joinToString(","),
        )
        gameStatus.text = when {
            importRunning -> "Importação do disco em andamento…"
            verified -> "Edição USA reconhecida pela estrutura: SCUS_974.72 + KERNEL/MANAGER/GAMECORE.XFF. Arquivos extraídos no armazenamento privado."
            missing.isNotEmpty() -> "Nenhum disco USA validado. Importe sua própria ISO americana SCUS-97472."
            else -> "Arquivos do disco presentes, mas a validação está ausente ou desatualizada. Importe a ISO novamente."
        }
    }

    private fun refreshRuntimeStatus() {
        runtimeStatus.text = try {
            nativeRuntimeStatus().also { DiagnosticLogger.event(this, "native_runtime_status", it) }
        } catch (error: UnsatisfiedLinkError) {
            DiagnosticLogger.event(this, "native_bridge_error", "type=" + error.javaClass.name + "; message=" + (error.message ?: "no-message"))
            "Falha ao carregar biblioteca nativa: " + (error.message ?: "erro desconhecido")
        }
        val hasEngine = try { nativeEngineAvailable() } catch (_: UnsatisfiedLinkError) { false }
        engineModeStatus.text = if (hasEngine) {
            "Fontes do jogo recompiladas e ligadas nesta build. Ainda é necessário validar boot e renderização no aparelho."
        } else {
            "Esta build ainda não contém o código C++ recompilado da ISO. O código é gerado e compilado no computador antes de produzir o APK."
        }
    }

    private fun refreshLaunchButton() {
        val verified = IsoIntegrity.isVerified(gameFolder)
        val hasEngine = try { nativeEngineAvailable() } catch (_: UnsatisfiedLinkError) { false }
        playButton.isEnabled = verified && hasEngine
        DiagnosticLogger.event(this, "launch_preflight", "usa_disc_structurally_verified=" + verified + "; native_engine_available=" + hasEngine + "; enabled=" + playButton.isEnabled)
        playButton.text = when {
            !verified -> "Importe e valide a ISO americana"
            !hasEngine -> "Engine da edição USA ausente nesta build"
            else -> "Iniciar Shadow of the Colossus"
        }
    }

    /**
     * Exports the minimum USA boot/module inputs and diagnostics for the
     * external Celosia code-generation pipeline. The ISO itself is never copied.
     * Optional generated files are picked up from files/celosia-output/{runner,vu1rec,captures}.
     */
    private fun exportCelosiaBundle(destination: Uri) {
        if (!IsoIntegrity.isVerified(gameFolder)) {
            Toast.makeText(this, "A estrutura USA não está validada.", Toast.LENGTH_LONG).show()
            return
        }
        importButton.isEnabled = false
        progressBar.isIndeterminate = true
        gameStatus.text = "Preparando pacote de exportação Celosia…"
        DiagnosticLogger.event(this, "celosia_export_started")

        Thread {
            try {
                val files = mutableListOf<Pair<File, String>>()
                val requiredDisc = listOf("SCUS_974.72", "KERNEL.XFF", "MANAGER.XFF", "GAMECORE.XFF", "SYSTEM.CNF")
                requiredDisc.forEach { name ->
                    val file = File(discFolder, name)
                    if (!file.isFile || file.length() <= 0L) throw IOException("Arquivo USA necessário ausente: " + name)
                    files += file to ("disc/" + name)
                }
                val logsDir = File(filesDir, "logs")
                listOf("sotc-app.log", "sotc-boot.log").forEach { name ->
                    val file = File(logsDir, name)
                    if (file.isFile) files += file to ("logs/" + name)
                }
                val marker = IsoIntegrity.markerFile(gameFolder)
                if (marker.isFile) files += marker to ("metadata/" + marker.name)

                val generatedRoot = File(filesDir, "celosia-output")
                var generatedCount = 0
                listOf("runner", "vu1rec", "captures").forEach { folderName ->
                    val folder = File(generatedRoot, folderName)
                    if (folder.isDirectory) {
                        folder.walkTopDown().filter { file ->
                            file.isFile && when (folderName) {
                                "runner" -> file.extension.lowercase() in setOf("cpp", "h", "hpp")
                                "vu1rec" -> file.extension.lowercase() in setOf("cpp", "h", "hpp")
                                "captures" -> file.extension.lowercase() == "bin"
                                else -> false
                            }
                        }.forEach { file ->
                            val relative = file.relativeTo(generatedRoot).invariantSeparatorsPath
                            files += file to ("generated/" + relative)
                            generatedCount++
                        }
                    }
                }

                val manifest = buildString {
                    appendLine("package=ShadowColossusAndroid-Celosia-USA")
                    appendLine("format=1")
                    appendLine("edition=USA-SCUS_974.72")
                    appendLine("source_iso_included=false")
                    appendLine("generated_artifact_count=" + generatedCount)
                    appendLine("generated_artifact_root=files/celosia-output/{runner,vu1rec,captures}")
                    appendLine("note=This package is an input/export bundle. It does not claim that code generation ran on Android.")
                    files.forEach { (file, archivePath) ->
                        appendLine("file=" + archivePath + ";bytes=" + file.length() + ";sha256=" + sha256(file))
                    }
                }

                val output = contentResolver.openOutputStream(destination, "w")
                    ?: throw IOException("O Android não conseguiu abrir o destino do ZIP.")
                output.use { raw ->
                    ZipOutputStream(BufferedOutputStream(raw)).use { zip ->
                        files.forEachIndexed { index, pair ->
                            val (file, archivePath) = pair
                            zip.putNextEntry(ZipEntry(archivePath))
                            BufferedInputStream(file.inputStream()).use { input ->
                                val buffer = ByteArray(256 * 1024)
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    if (count > 0) zip.write(buffer, 0, count)
                                }
                            }
                            zip.closeEntry()
                            if (index % 2 == 0) {
                                runOnUiThread { gameStatus.text = "Empacotando arquivos Celosia: " + (index + 1) + "/" + files.size }
                            }
                        }
                        zip.putNextEntry(ZipEntry("manifest.txt"))
                        zip.write(manifest.toByteArray(Charsets.UTF_8))
                        zip.closeEntry()
                    }
                }
                DiagnosticLogger.event(this, "celosia_export_completed", "file_count=" + files.size + "; generated_artifact_count=" + generatedCount)
                runOnUiThread {
                    progressBar.isIndeterminate = false
                    progressBar.progress = 1000
                    importButton.isEnabled = true
                    gameStatus.text = "Pacote Celosia exportado. Fontes C++ encontradas: " + generatedCount
                    Toast.makeText(this, "Pacote Celosia salvo. Fontes geradas encontradas: " + generatedCount, Toast.LENGTH_LONG).show()
                }
            } catch (error: Exception) {
                DiagnosticLogger.event(this, "celosia_export_failed", "type=" + error.javaClass.name + "; message=" + (error.message ?: "no-message"))
                runOnUiThread {
                    progressBar.isIndeterminate = false
                    importButton.isEnabled = true
                    gameStatus.text = "Exportação falhou: " + (error.message ?: error.javaClass.simpleName)
                    Toast.makeText(this, "Falha ao exportar pacote Celosia.", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        BufferedInputStream(file.inputStream()).use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return IsoIntegrity.toHex(digest.digest())
    }

    private fun launchGame() {
        DiagnosticLogger.event(this, "launch_requested", "boot_elf_exists=" + bootElf.isFile + "; game_folder=" + gameFolder.absolutePath)
        if (!IsoIntegrity.isVerified(gameFolder)) {
            DiagnosticLogger.event(this, "launch_blocked", "reason=usa_disc_not_verified")
            Toast.makeText(this, "A estrutura do disco americano não está validada.", Toast.LENGTH_LONG).show()
            refreshLaunchButton()
            return
        }
        if (!nativeEngineAvailable()) {
            DiagnosticLogger.event(this, "launch_blocked", "reason=celosia_game_code_not_linked")
            Toast.makeText(this, "Esta build não contém a engine Celosia recompilada a partir da ISO americana.", Toast.LENGTH_LONG).show()
            return
        }
        if (!nativeSetIsoPath(bootElf.absolutePath)) {
            DiagnosticLogger.event(this, "launch_blocked", "reason=native_set_boot_elf_failed")
            Toast.makeText(this, "Não foi possível encaminhar o executável americano à engine nativa.", Toast.LENGTH_LONG).show()
            return
        }
        DiagnosticLogger.event(this, "launch_activity_starting", "activity=GameActivity; boot_elf=" + bootElf.absolutePath)
        try {
            startActivity(Intent(this, GameActivity::class.java))
        } catch (error: Exception) {
            DiagnosticLogger.event(this, "launch_activity_failed", "type=" + error.javaClass.name + "; message=" + (error.message ?: "no-message"))
            throw error
        }
    }

    private fun formatGiB(bytes: Long): String = String.format("%.2f", bytes / (1024.0 * 1024.0 * 1024.0))
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun matchWidthWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    )
}
