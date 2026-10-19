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

class MainActivity : ComponentActivity() {
    companion object {
        private const val EXPECTED_ISO_BYTES = 4_635_918_336L
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
    private lateinit var generationButton: Button
    private lateinit var generationProgressBar: ProgressBar
    private lateinit var generationProgressLabel: TextView
    private lateinit var generationStatus: TextView
    private lateinit var generationDetails: TextView

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
            text = "Importe sua própria ISO PAL europeia SCES-53326 v1.00. O tamanho é verificado aqui; o runtime valida arquivos internos do disco ao iniciar."
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

        // Honest preflight UI: this APK has the PS2 runtime but not the on-device
        // generator/compiler. The progress bar below is for prerequisite checks only.
        val generationCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundColor(0xFFF2F4F7.toInt())
        }
        generationCard.addView(TextView(this).apply {
            text = "Preparação para recompilação no celular"
            textSize = 16f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }, matchWidthWrap())
        generationCard.addView(TextView(this).apply {
            text = "A barra acompanha a verificação dos requisitos. Ela não representa porcentagem de compilação: o gerador e o compilador para Android ainda precisam ser integrados."
            textSize = 13f
            setPadding(0, dp(6), 0, dp(8))
        }, matchWidthWrap())
        generationProgressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progress = 0
            isIndeterminate = false
        }
        generationCard.addView(generationProgressBar, matchWidthWrap().apply {
            topMargin = dp(4)
            bottomMargin = dp(4)
        })
        generationProgressLabel = TextView(this).apply {
            text = "Verificação prévia: aguardando"
            textSize = 12f
        }
        generationCard.addView(generationProgressLabel, matchWidthWrap())
        generationStatus = TextView(this).apply {
            text = "A recompilação ainda não está disponível nesta build."
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, dp(8), 0, dp(4))
        }
        generationCard.addView(generationStatus, matchWidthWrap())
        generationDetails = TextView(this).apply {
            text = "Toque em verificar para ver quais requisitos já existem no aparelho e exatamente onde o processo fica bloqueado."
            textSize = 12f
        }
        generationCard.addView(generationDetails, matchWidthWrap())
        generationButton = Button(this).apply {
            text = "Verificar requisitos da recompilação"
            setOnClickListener { startRecompilationPreflight() }
        }
        generationCard.addView(generationButton, matchWidthWrap().apply { topMargin = dp(8) })
        content.addView(generationCard, matchWidthWrap().apply { topMargin = dp(16) })

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
            text = "A ISO continua privada. Nesta versão, a verificação visualiza os pré-requisitos, mas ainda não gera nem compila o código do jogo no celular."
            textSize = 13f
            setPadding(0, dp(20), 0, 0)
        }, matchWidthWrap())

        setContentView(scroll)
        DiagnosticLogger.event(this, "app_start", "package=$packageName; version=${packageManager.getPackageInfo(packageName, 0).versionName}; android=${android.os.Build.VERSION.RELEASE}; model=${android.os.Build.MANUFACTURER}/${android.os.Build.MODEL}")
        refreshIsoStatus()
        refreshRuntimeStatus()
        refreshLaunchButton()
    }

    override fun onResume() {
        super.onResume()
        if (::isoStatus.isInitialized) {
            refreshIsoStatus()
            refreshLaunchButton()
        }
    }

    private fun importIso(uri: android.net.Uri) {
        DiagnosticLogger.event(this, "iso_import_started", "uri_scheme=${uri.scheme ?: "unknown"}")
        importButton.isEnabled = false
        playButton.isEnabled = false
        progressBar.progress = 0
        progressBar.isIndeterminate = true
        isoStatus.text = "Preparando importação de aproximadamente 4,64 GB. Mantenha espaço livre suficiente."

        Thread {
            val folder = File(filesDir, "game")
            val partial = File(folder, "SCES-53326.iso.partial")
            try {
                if (!folder.exists() && !folder.mkdirs()) {
                    throw IOException("Não foi possível criar a pasta privada do jogo.")
                }
                val stat = StatFs(filesDir.absolutePath)
                val requiredSpace = EXPECTED_ISO_BYTES + 128L * 1024 * 1024
                DiagnosticLogger.event(this, "iso_space_check", "available_bytes=${stat.availableBytes}; required_bytes=$requiredSpace")
                if (stat.availableBytes < requiredSpace) {
                    throw IOException("Espaço insuficiente. Libere pelo menos 4,76 GB no armazenamento interno do aplicativo.")
                }

                partial.delete()
                var total = 0L
                var lastReported = 0L
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
                            output.write(buffer, 0, count)

                            if (total - lastReported >= PROGRESS_STEP_BYTES) {
                                lastReported = total
                                DiagnosticLogger.event(this@MainActivity, "iso_copy_progress", "bytes=$total; expected_bytes=$EXPECTED_ISO_BYTES")
                                val progress = (total * 1000L / EXPECTED_ISO_BYTES).toInt().coerceIn(0, 1000)
                                runOnUiThread {
                                    progressBar.isIndeterminate = false
                                    progressBar.progress = progress
                                    isoStatus.text = "Copiando ISO: ${(total * 100L / EXPECTED_ISO_BYTES).coerceIn(0, 100)}% — ${formatGiB(total)} / 4,64 GB"
                                }
                            }
                        }
                        output.fd.sync()
                    }
                }

                if (total != EXPECTED_ISO_BYTES) {
                    throw IOException("Tamanho incorreto: ${total} bytes; esperado ${EXPECTED_ISO_BYTES} bytes.")
                }

                DiagnosticLogger.event(this, "iso_copy_size_valid", "copied_bytes=$total")
                val destination = isoFile
                if (destination.exists() && !destination.delete()) {
                    throw IOException("Não foi possível substituir a ISO anterior.")
                }
                if (!partial.renameTo(destination)) {
                    throw IOException("A cópia terminou, mas não foi possível finalizar a importação.")
                }

                DiagnosticLogger.event(this, "iso_import_completed", "relative_path=game/SCES-53326.iso; bytes=$total")
                runOnUiThread {
                    progressBar.isIndeterminate = false
                    progressBar.progress = 1000
                    isoStatus.text = "ISO copiada. A versão exata será verificada pelo runtime ao iniciar o jogo."
                    importButton.isEnabled = true
                    refreshIsoStatus()
                    refreshLaunchButton()
                }
            } catch (error: Exception) {
                DiagnosticLogger.event(this, "iso_import_failed", "type=${error.javaClass.name}; message=${error.message ?: "no-message"}")
                partial.delete()
                runOnUiThread {
                    progressBar.isIndeterminate = false
                    progressBar.progress = 0
                    isoStatus.text = "Importação não concluída: ${error.message ?: error.javaClass.simpleName}"
                    importButton.isEnabled = true
                    refreshLaunchButton()
                }
            }
        }.start()
    }

    /**
     * Checks genuine prerequisites for on-device recompilation and reports each stage.
     * The percentage belongs only to this preflight; this APK does not run a compiler.
     */
    private fun startRecompilationPreflight() {
        if (!::generationButton.isInitialized || !generationButton.isEnabled) return
        generationButton.isEnabled = false
        updateGenerationPreflight(
            progress = 0,
            label = "Verificação prévia: iniciando",
            status = "Inspecionando os requisitos do aparelho…",
            details = "Nenhum código do jogo está sendo compilado nesta etapa.",
            indeterminate = true,
        )

        Thread {
            try {
                updateGenerationPreflight(
                    150,
                    "Verificação prévia: etapa 1/4",
                    "Verificando a ISO importada",
                    "Conferindo arquivo privado e tamanho esperado.",
                    indeterminate = true,
                )
                val isoBytes = if (isoFile.isFile) isoFile.length() else 0L
                if (!isoFile.isFile || isoBytes != EXPECTED_ISO_BYTES) {
                    updateGenerationPreflight(
                        1000,
                        "Verificação prévia concluída: 100%",
                        "Bloqueado: a ISO está ausente ou tem tamanho incorreto.",
                        "Arquivo: ${isoFile.isFile}\nTamanho encontrado: ${isoBytes} bytes\nTamanho esperado: ${EXPECTED_ISO_BYTES} bytes\nA verificação de tamanho não confirma o hash nem a edição da ISO.",
                    )
                    DiagnosticLogger.event(this@MainActivity, "codegen_preflight_blocked", "reason=iso_missing_or_wrong_size; bytes=$isoBytes")
                    return@Thread
                }

                updateGenerationPreflight(
                    300,
                    "Verificação prévia: etapa 2/4",
                    "ISO encontrada",
                    "O arquivo tem o tamanho esperado (${isoBytes} bytes). A integridade dos módulos ainda não foi validada nesta tela.",
                    indeterminate = true,
                )
                val stat = StatFs(filesDir.absolutePath)
                val availableBytes = stat.availableBytes
                updateGenerationPreflight(
                    450,
                    "Verificação prévia: etapa 3/4",
                    "Armazenamento consultado",
                    "Espaço livre: ${formatGiB(availableBytes)} GiB. O launcher ainda não consegue estimar com precisão o espaço temporário necessário para gerar e compilar todas as fontes.",
                    indeterminate = true,
                )

                val abis = android.os.Build.SUPPORTED_ABIS.toList()
                val hasArm64 = abis.contains("arm64-v8a")
                if (!hasArm64) {
                    updateGenerationPreflight(
                        1000,
                        "Verificação prévia concluída: 100%",
                        "Bloqueado: esta instalação não anuncia suporte ARM64.",
                        "ABIs encontradas: ${abis.joinToString()}",
                    )
                    DiagnosticLogger.event(this@MainActivity, "codegen_preflight_blocked", "reason=arm64_abi_missing; abis=${abis.joinToString(",")}")
                    return@Thread
                }

                updateGenerationPreflight(
                    650,
                    "Verificação prévia: etapa 4/4",
                    "Arquitetura ARM64 confirmada",
                    "ABIs anunciadas: ${abis.joinToString()}. Conferindo se o APK inclui o gerador e o compilador necessários.",
                    indeterminate = true,
                )
                val nativeDir = File(applicationInfo.nativeLibraryDir)
                val codegenLibrary = File(nativeDir, "libsotc_codegen.so")
                val compilerInPrivateTools = File(filesDir, "sotc-toolchain/bin/clang")
                val compilerInNativeDir = File(nativeDir, "libclang.so")
                val hasCodegen = codegenLibrary.isFile
                val hasCompiler = compilerInPrivateTools.isFile || compilerInNativeDir.isFile

                val missing = mutableListOf<String>()
                if (!hasCodegen) missing += "gerador PS2Recomp integrado ao Android (libsotc_codegen.so)"
                if (!hasCompiler) missing += "toolchain de C/C++ para gerar a biblioteca nativa"
                if (missing.isNotEmpty()) {
                    val details = buildString {
                        appendLine("ISO: encontrada; tamanho correto.")
                        appendLine("ABI: ARM64 disponível.")
                        appendLine("Espaço livre: ${formatGiB(availableBytes)} GiB.")
                        appendLine("Gerador Android: ${if (hasCodegen) "encontrado" else "ausente"}.")
                        appendLine("Compilador C/C++: ${if (hasCompiler) "encontrado" else "ausente"}.")
                        appendLine()
                        appendLine("A recompilação não começou. Falta integrar:")
                        missing.forEach { appendLine("• $it") }
                        appendLine()
                        append("Os 100% indicam apenas que a verificação de pré-requisitos terminou, não que o jogo foi recompilado.")
                    }
                    updateGenerationPreflight(
                        1000,
                        "Verificação prévia concluída: 100%",
                        "Recompilação bloqueada antes da geração de código.",
                        details,
                    )
                    DiagnosticLogger.event(this@MainActivity, "codegen_preflight_blocked", "reason=android_generator_or_compiler_missing; generator=$hasCodegen; compiler=$hasCompiler; free_bytes=$availableBytes")
                    return@Thread
                }

                updateGenerationPreflight(
                    1000,
                    "Verificação prévia concluída: 100%",
                    "As ferramentas foram detectadas, mas o executor de recompilação ainda não está conectado.",
                    "A presença de bibliotecas, por si só, não significa que o fluxo de geração, compilação e carregamento esteja implementado.",
                )
                DiagnosticLogger.event(this@MainActivity, "codegen_preflight_tools_found_but_executor_missing")
            } catch (error: Exception) {
                updateGenerationPreflight(
                    1000,
                    "Verificação prévia encerrada",
                    "Falha ao verificar os requisitos.",
                    "${error.javaClass.simpleName}: ${error.message ?: "sem detalhe"}",
                )
                DiagnosticLogger.event(this@MainActivity, "codegen_preflight_error", "type=${error.javaClass.name}; message=${error.message ?: "no-message"}")
            } finally {
                runOnUiThread { generationButton.isEnabled = true }
            }
        }.start()
    }

    private fun updateGenerationPreflight(
        progress: Int,
        label: String,
        status: String,
        details: String,
        indeterminate: Boolean = false,
    ) {
        DiagnosticLogger.event(
            this,
            "codegen_preflight_progress",
            "progress=${progress.coerceIn(0, 1000)}; label=$label; status=$status",
        )
        runOnUiThread {
            generationProgressBar.isIndeterminate = indeterminate
            generationProgressBar.progress = progress.coerceIn(0, 1000)
            generationProgressLabel.text = label
            generationStatus.text = status
            generationDetails.text = details
        }
    }

    private fun refreshIsoStatus() {
        val matchesSize = isoFile.isFile && isoFile.length() == EXPECTED_ISO_BYTES
        DiagnosticLogger.event(this, "iso_status", "exists=${isoFile.isFile}; bytes=${if (isoFile.isFile) isoFile.length() else 0L}; exact_size_match=$matchesSize; content_hashes=not_checked")
        isoStatus.text = if (matchesSize) {
            "Imagem presente no armazenamento privado (${formatGiB(isoFile.length())} GiB). O runtime verificará boot ELF e módulos antes de executar."
        } else {
            "Nenhuma imagem com o tamanho esperado foi importada."
        }
    }

    private fun refreshRuntimeStatus() {
        runtimeStatus.text = try {
            nativeRuntimeStatus().also { DiagnosticLogger.event(this, "native_runtime_status", it) }
        } catch (error: UnsatisfiedLinkError) {
            DiagnosticLogger.event(this, "native_bridge_error", "type=${error.javaClass.name}; message=${error.message ?: "no-message"}")
            "Falha ao carregar biblioteca nativa: ${error.message ?: "erro desconhecido"}"
        }
    }

    private fun refreshLaunchButton() {
        val hasIso = isoFile.isFile && isoFile.length() == EXPECTED_ISO_BYTES
        val hasEngine = try { nativeEngineAvailable() } catch (_: UnsatisfiedLinkError) { false }
        playButton.isEnabled = hasIso && hasEngine
        DiagnosticLogger.event(this, "launch_preflight", "iso_size_ok=$hasIso; native_engine_available=$hasEngine; enabled=${playButton.isEnabled}")
        playButton.text = when {
            !hasIso -> "Importe a ISO para continuar"
            !hasEngine -> "Jogo indisponível — fontes recompiladas pendentes"
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
