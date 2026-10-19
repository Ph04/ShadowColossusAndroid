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

    private lateinit var isoStatus: TextView
    private lateinit var runtimeStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var importButton: Button
    private lateinit var playButton: Button

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
            text = "PRÉVIA 0.2 · ANDROID ARM64"
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

        content.addView(TextView(this).apply {
            text = "O código recompilado do jogo é gerado a partir da sua própria ISO e não é distribuído pelo aplicativo."
            textSize = 13f
            setPadding(0, dp(20), 0, 0)
        }, matchWidthWrap())

        setContentView(scroll)
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

                val destination = isoFile
                if (destination.exists() && !destination.delete()) {
                    throw IOException("Não foi possível substituir a ISO anterior.")
                }
                if (!partial.renameTo(destination)) {
                    throw IOException("A cópia terminou, mas não foi possível finalizar a importação.")
                }

                runOnUiThread {
                    progressBar.isIndeterminate = false
                    progressBar.progress = 1000
                    isoStatus.text = "ISO copiada. A versão exata será verificada pelo runtime ao iniciar o jogo."
                    importButton.isEnabled = true
                    refreshIsoStatus()
                    refreshLaunchButton()
                }
            } catch (error: Exception) {
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

    private fun refreshIsoStatus() {
        isoStatus.text = if (isoFile.isFile && isoFile.length() == EXPECTED_ISO_BYTES) {
            "Imagem presente no armazenamento privado (${formatGiB(isoFile.length())} GiB). O runtime verificará boot ELF e módulos antes de executar."
        } else {
            "Nenhuma imagem com o tamanho esperado foi importada."
        }
    }

    private fun refreshRuntimeStatus() {
        runtimeStatus.text = try {
            nativeRuntimeStatus()
        } catch (error: UnsatisfiedLinkError) {
            "Falha ao carregar biblioteca nativa: ${error.message ?: "erro desconhecido"}"
        }
    }

    private fun refreshLaunchButton() {
        val hasIso = isoFile.isFile && isoFile.length() == EXPECTED_ISO_BYTES
        val hasEngine = try { nativeEngineAvailable() } catch (_: UnsatisfiedLinkError) { false }
        playButton.isEnabled = hasIso && hasEngine
        playButton.text = when {
            !hasIso -> "Importe a ISO para continuar"
            !hasEngine -> "Jogo indisponível — fontes recompiladas pendentes"
            else -> "Iniciar Shadow of the Colossus"
        }
    }

    private fun launchGame() {
        if (!isoFile.isFile || isoFile.length() != EXPECTED_ISO_BYTES) {
            refreshLaunchButton()
            return
        }
        if (!nativeEngineAvailable()) {
            Toast.makeText(this, "A engine do jogo ainda não foi compilada com as fontes geradas da sua ISO.", Toast.LENGTH_LONG).show()
            return
        }
        if (!nativeSetIsoPath(isoFile.absolutePath)) {
            Toast.makeText(this, "Não foi possível encaminhar a ISO à engine nativa.", Toast.LENGTH_LONG).show()
            return
        }
        startActivity(Intent(this, GameActivity::class.java))
    }

    private fun formatGiB(bytes: Long): String = String.format("%.2f", bytes / (1024.0 * 1024.0 * 1024.0))
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun matchWidthWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    )
}
