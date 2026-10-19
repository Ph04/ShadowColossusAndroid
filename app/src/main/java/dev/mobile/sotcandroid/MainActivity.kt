package dev.mobile.sotcandroid

import android.content.Intent
import android.os.Bundle
import android.os.StatFs
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

class MainActivity : ComponentActivity() {
    companion object {
        private const val EXPECTED_ISO_BYTES = IsoIntegrity.EXPECTED_ISO_BYTES
        private const val COPY_BUFFER_BYTES = 1024 * 1024
        private const val PROGRESS_STEP_BYTES = 16L * 1024 * 1024

        init {
            System.loadLibrary("sotc_android_bridge")
        }
    }

    private external fun nativeRuntimeStatus(): String
    private external fun nativeEngineAvailable(): Boolean
    private external fun nativeSetIsoPath(path: String): Boolean
    private external fun nativeDiagnosticReport(): String

    private lateinit var isoStatus: TextView
    private lateinit var runtimeStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var importButton: Button
    private lateinit var playButton: Button
    private lateinit var engineModeStatus: TextView

    @Volatile private var isoImportRunning = false
    @Volatile private var isoValidationRunning = false
    @Volatile private var failedValidationForMtime: Long? = null

    private val isoPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(::importIso)
    }

    private val isoFile: File
        get() = File(File(filesDir, "game"), "SCES-53326.iso")

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
            text = "PRÉVIA 0.3 · ${if (packageName.endsWith(".diagnostic")) "DIAGNÓSTICO" else "ANDROID ARM64"}"
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(20))
        }, matchWidthWrap())

        content.addView(TextView(this).apply {
            text = "Importe sua própria ISO PAL europeia SCES-53326 v1.00. O launcher valida o tamanho e o SHA-256; o motor ARM64 precisa estar pré-compilado no APK."
            textSize = 15f
            gravity = Gravity.CENTER
        }, matchWidthWrap())

        importButton = Button(this).apply {
            text = "Selecionar ISO do jogo"
            setOnClickListener {
                isoPicker.launch(arrayOf("application/octet-stream", "application/x-iso9660-image", "*/*"))
            }
        }
        content.addView(importButton, matchWidthWrap().apply { topMargin = dp(16) })

        isoStatus = TextView(this).apply {
            textSize = 14f
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        content.addView(isoStatus, matchWidthWrap().apply { topMargin = dp(8) })

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progress = 0
            isIndeterminate = false
        }
        content.addView(progressBar, matchWidthWrap().apply {
            topMargin = dp(8)
            bottomMargin = dp(12)
        })

        playButton = Button(this).apply {
            text = "Engine do jogo ainda não integrada"
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
            text = "Modelo de motor pré-compilado"
            textSize = 16f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }, matchWidthWrap())
        engineModeCard.addView(TextView(this).apply {
            text = "Como no Budokai: a engine ARM64 é compilada antes de montar o APK. No celular, o launcher importa a ISO e inicia o motor já incluído; não há recompilação C++ durante a instalação."
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
                    isoFile = isoFile,
                    expectedIsoBytes = EXPECTED_ISO_BYTES,
                    runtimeStatus = { nativeRuntimeStatus() },
                    engineAvailable = { nativeEngineAvailable() },
                    nativeBuildReport = { nativeDiagnosticReport() },
                )
            }
        }, matchWidthWrap().apply { topMargin = dp(12) })

        content.addView(TextView(this).apply {
            text = "A ISO fica no armazenamento privado. O launcher verifica o SHA-256 durante a cópia e só aceita a versão PAL esperada. As builds automáticas de teste ainda não incluem a engine específica do jogo."
            textSize = 13f
            setPadding(0, dp(20), 0, 0)
        }, matchWidthWrap())

        setContentView(scroll)
        DiagnosticLogger.event(this, "app_start", "package=$packageName; version=${packageManager.getPackageInfo(packageName, 0).versionName}; android=${android.os.Build.VERSION.RELEASE}; model=${android.os.Build.MANUFACTURER}/${android.os.Build.MODEL}")
        refreshIsoStatus()
        refreshRuntimeStatus()
        refreshLaunchButton()
        validateExistingIsoIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        if (::isoStatus.isInitialized) {
            refreshIsoStatus()
            refreshLaunchButton()
            validateExistingIsoIfNeeded()
        }
    }

    private fun importIso(uri: android.net.Uri) {
        DiagnosticLogger.event(this, "iso_import_started", "uri_scheme=${uri.scheme ?: "unknown"}")
        isoImportRunning = true
        failedValidationForMtime = null
        importButton.isEnabled = false
        playButton.isEnabled = false
        progressBar.progress = 0
        progressBar.isIndeterminate = true
        isoStatus.text = "Preparando importação e validação SHA-256 de aproximadamente 4,64 GB."

        Thread {
            val folder = File(filesDir, "game")
            val partial = File(folder, "SCES-53326.iso.partial")
            try {
                if (!folder.exists() && !folder.mkdirs()) {
                    throw IOException("Não foi possível criar a pasta privada do jogo.")
                }
                val stat = StatFs(filesDir.absolutePath)
                val requiredSpace = EXPECTED_ISO_BYTES + 128L * 1024 * 1024
                DiagnosticLogger.event(this@MainActivity, "iso_space_check", "available_bytes=${stat.availableBytes}; required_bytes=$requiredSpace")
                if (stat.availableBytes < requiredSpace) {
                    throw IOException("Espaço insuficiente. Libere pelo menos 4,76 GB no armazenamento interno do aplicativo.")
                }

                partial.delete()
                var total = 0L
                var lastReported = 0L
                val digest = MessageDigest.getInstance("SHA-256")
                val input = contentResolver.openInputStream(uri)
                    ?: throw IOException("O Android não conseguiu abrir o arquivo selecionado.")

                input.use { source ->
                    FileOutputStream(partial).use { output ->
                        val buffer = ByteArray(COPY_BUFFER_BYTES)
                        while (true) {
                            val count = source.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue
                            total += count.toLong()
                            if (total > EXPECTED_ISO_BYTES) {
                                throw IOException("O arquivo é maior que a imagem suportada.")
                            }
                            digest.update(buffer, 0, count)
                            output.write(buffer, 0, count)

                            if (total - lastReported >= PROGRESS_STEP_BYTES) {
                                lastReported = total
                                DiagnosticLogger.event(this@MainActivity, "iso_copy_progress", "bytes=$total; expected_bytes=$EXPECTED_ISO_BYTES; sha256_in_progress=true")
                                val progress = (total * 1000L / EXPECTED_ISO_BYTES).toInt().coerceIn(0, 1000)
                                runOnUiThread {
                                    progressBar.isIndeterminate = false
                                    progressBar.progress = progress
                                    isoStatus.text = "Copiando e verificando ISO: ${(total * 100L / EXPECTED_ISO_BYTES).coerceIn(0, 100)}% — ${formatGiB(total)} / 4,64 GB"
                                }
                            }
                        }
                        output.fd.sync()
                    }
                }

                if (total != EXPECTED_ISO_BYTES) {
                    throw IOException("Tamanho incorreto: ${total} bytes; esperado ${EXPECTED_ISO_BYTES} bytes.")
                }

                val actualSha256 = IsoIntegrity.toHex(digest.digest())
                DiagnosticLogger.event(this@MainActivity, "iso_sha256_calculated", "sha256=$actualSha256; expected=${IsoIntegrity.EXPECTED_ISO_SHA256}")
                if (!actualSha256.equals(IsoIntegrity.EXPECTED_ISO_SHA256, ignoreCase = true)) {
                    throw IOException("O arquivo tem o tamanho esperado, mas o SHA-256 não corresponde à ISO PAL SCES-53326 v1.00 suportada. Selecione a imagem correta.")
                }

                val destination = isoFile
                if (destination.exists() && !destination.delete()) {
                    throw IOException("Não foi possível substituir a ISO anterior.")
                }
                if (!partial.renameTo(destination)) {
                    throw IOException("A cópia terminou, mas não foi possível finalizar a importação.")
                }
                IsoIntegrity.writeVerifiedMarker(destination, actualSha256)

                DiagnosticLogger.event(this, "iso_import_completed", "relative_path=game/SCES-53326.iso; bytes=$total; sha256_verified=true")
                runOnUiThread {
                    isoImportRunning = false
                    progressBar.isIndeterminate = false
                    progressBar.progress = 1000
                    importButton.isEnabled = true
                    refreshIsoStatus()
                    refreshLaunchButton()
                }
            } catch (error: Exception) {
                DiagnosticLogger.event(this@MainActivity, "iso_import_failed", "type=${error.javaClass.name}; message=${error.message ?: "no-message"}")
                partial.delete()
                runOnUiThread {
                    isoImportRunning = false
                    progressBar.isIndeterminate = false
                    progressBar.progress = 0
                    isoStatus.text = "Importação/validação não concluída: ${error.message ?: error.javaClass.simpleName}"
                    importButton.isEnabled = true
                    refreshLaunchButton()
                }
            }
        }.start()
    }

    /**
     * Validates an ISO imported by an older build. The first scan has byte-based progress;
     * a private marker bound to file size and modification time avoids rescanning each launch.
     */
    private fun validateExistingIsoIfNeeded() {
        if (!::isoStatus.isInitialized || isoImportRunning || isoValidationRunning) return
        if (!isoFile.isFile || isoFile.length() != EXPECTED_ISO_BYTES || IsoIntegrity.isVerified(isoFile)) return
        if (failedValidationForMtime == isoFile.lastModified()) return

        isoValidationRunning = true
        importButton.isEnabled = false
        progressBar.isIndeterminate = false
        progressBar.progress = 0
        isoStatus.text = "ISO encontrada. Validando SHA-256 antes de liberar a execução…"
        DiagnosticLogger.event(this, "iso_sha256_validation_started", "bytes=${isoFile.length()}")

        Thread {
            try {
                val actualSha256 = IsoIntegrity.calculateSha256(isoFile) { processed, total ->
                    DiagnosticLogger.event(this@MainActivity, "iso_sha256_validation_progress", "bytes=$processed; total_bytes=$total")
                    val progress = if (total > 0) (processed * 1000L / total).toInt().coerceIn(0, 1000) else 0
                    runOnUiThread {
                        progressBar.isIndeterminate = false
                        progressBar.progress = progress
                        isoStatus.text = "Validando SHA-256 da ISO: ${progress / 10}%"
                    }
                }
                if (!actualSha256.equals(IsoIntegrity.EXPECTED_ISO_SHA256, ignoreCase = true)) {
                    throw IOException("O SHA-256 não corresponde à ISO PAL SCES-53326 v1.00 suportada. Importe a imagem correta.")
                }
                IsoIntegrity.writeVerifiedMarker(isoFile, actualSha256)
                DiagnosticLogger.event(this@MainActivity, "iso_sha256_validation_completed", "sha256=$actualSha256")
                runOnUiThread {
                    isoValidationRunning = false
                    failedValidationForMtime = null
                    progressBar.isIndeterminate = false
                    progressBar.progress = 1000
                    importButton.isEnabled = true
                    refreshIsoStatus()
                    refreshLaunchButton()
                }
            } catch (error: Exception) {
                DiagnosticLogger.event(this@MainActivity, "iso_sha256_validation_failed", "type=${error.javaClass.name}; message=${error.message ?: "no-message"}")
                runOnUiThread {
                    isoValidationRunning = false
                    failedValidationForMtime = isoFile.lastModified()
                    progressBar.isIndeterminate = false
                    progressBar.progress = 0
                    importButton.isEnabled = true
                    isoStatus.text = "ISO não validada: ${error.message ?: error.javaClass.simpleName}"
                    refreshLaunchButton()
                }
            }
        }.start()
    }

    private fun refreshIsoStatus() {
        val matchesSize = isoFile.isFile && isoFile.length() == EXPECTED_ISO_BYTES
        val hashVerified = matchesSize && IsoIntegrity.isVerified(isoFile)
        DiagnosticLogger.event(
            this,
            "iso_status",
            "exists=${isoFile.isFile}; bytes=${if (isoFile.isFile) isoFile.length() else 0L}; exact_size_match=$matchesSize; sha256_verified=$hashVerified",
        )
        isoStatus.text = when {
            !matchesSize -> "Nenhuma imagem com o tamanho esperado foi importada."
            hashVerified -> "ISO validada: tamanho e SHA-256 correspondem à versão PAL SCES-53326 v1.00."
            isoValidationRunning -> "ISO encontrada. Validando SHA-256…"
            failedValidationForMtime == isoFile.lastModified() -> "A ISO não corresponde à versão esperada. Selecione a imagem correta."
            else -> "ISO presente (${formatGiB(isoFile.length())} GiB). A validação SHA-256 está pendente."
        }
    }

    private fun refreshRuntimeStatus() {
        runtimeStatus.text = try {
            nativeRuntimeStatus().also { DiagnosticLogger.event(this, "native_runtime_status", it) }
        } catch (error: UnsatisfiedLinkError) {
            DiagnosticLogger.event(this, "native_bridge_error", "type=${error.javaClass.name}; message=${error.message ?: "no-message"}")
            "Falha ao carregar biblioteca nativa: ${error.message ?: "erro desconhecido"}"
        }
        val hasEngine = try { nativeEngineAvailable() } catch (_: UnsatisfiedLinkError) { false }
        engineModeStatus.text = if (hasEngine) {
            "Engine específica do jogo pré-compilada e ligada neste APK. O telefone não precisa compilar C++."
        } else {
            "Engine específica de Shadow of the Colossus ausente nesta build. O código precisa ser gerado e compilado antes de montar o APK; este aplicativo não recompila o jogo."
        }
    }

    private fun refreshLaunchButton() {
        val hasExpectedSize = isoFile.isFile && isoFile.length() == EXPECTED_ISO_BYTES
        val hasVerifiedIso = hasExpectedSize && IsoIntegrity.isVerified(isoFile)
        val hasEngine = try { nativeEngineAvailable() } catch (_: UnsatisfiedLinkError) { false }
        playButton.isEnabled = hasVerifiedIso && hasEngine
        DiagnosticLogger.event(
            this,
            "launch_preflight",
            "iso_size_ok=$hasExpectedSize; iso_sha256_verified=$hasVerifiedIso; native_engine_available=$hasEngine; enabled=${playButton.isEnabled}",
        )
        playButton.text = when {
            !hasExpectedSize -> "Importe a ISO correta para continuar"
            !hasVerifiedIso && isoValidationRunning -> "Validando SHA-256 da ISO…"
            !hasVerifiedIso -> "Aguardando validação SHA-256"
            !hasEngine -> "Jogo indisponível — engine pré-compilada pendente"
            else -> "Iniciar Shadow of the Colossus"
        }
    }

    private fun launchGame() {
        DiagnosticLogger.event(this, "launch_requested", "iso_exists=${isoFile.isFile}; iso_bytes=${if (isoFile.isFile) isoFile.length() else 0L}")
        if (!isoFile.isFile || isoFile.length() != EXPECTED_ISO_BYTES) {
            DiagnosticLogger.event(this, "launch_blocked", "reason=iso_missing_or_wrong_size")
            refreshLaunchButton()
            return
        }
        if (!IsoIntegrity.isVerified(isoFile)) {
            DiagnosticLogger.event(this, "launch_blocked", "reason=iso_sha256_not_verified")
            Toast.makeText(this, "A ISO ainda não foi validada por SHA-256.", Toast.LENGTH_LONG).show()
            validateExistingIsoIfNeeded()
            refreshLaunchButton()
            return
        }
        if (!nativeEngineAvailable()) {
            DiagnosticLogger.event(this, "launch_blocked", "reason=game_engine_not_linked_into_apk")
            Toast.makeText(this, "A engine do jogo ainda não foi compilada com as fontes geradas da sua ISO.", Toast.LENGTH_LONG).show()
            return
        }
        if (!nativeSetIsoPath(isoFile.absolutePath)) {
            DiagnosticLogger.event(this, "launch_blocked", "reason=native_set_iso_path_failed")
            Toast.makeText(this, "Não foi possível encaminhar a ISO à engine nativa.", Toast.LENGTH_LONG).show()
            return
        }
        DiagnosticLogger.event(this, "launch_activity_starting", "activity=GameActivity")
        try {
            startActivity(Intent(this, GameActivity::class.java))
        } catch (error: Exception) {
            DiagnosticLogger.event(this, "launch_activity_failed", "type=${error.javaClass.name}; message=${error.message ?: "no-message"}")
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
