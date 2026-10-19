package dev.mobile.sotcandroid

import android.net.Uri
import android.os.Bundle
import android.os.StatFs
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

class MainActivity : ComponentActivity() {
    companion object {
        private const val EXPECTED_ISO_BYTES = 4_635_918_336L
        private const val EXPECTED_ISO_SHA256 =
            "068925770645266189005f09c9a9a1fee25726bfc93c89b12f832f3673664ac4"
        private const val COPY_BUFFER_BYTES = 1024 * 1024
        private const val PROGRESS_STEP_BYTES = 16L * 1024 * 1024

        init {
            System.loadLibrary("sotc_android_bridge")
        }
    }

    private external fun nativeRuntimeStatus(): String

    private lateinit var isoStatus: TextView
    private lateinit var runtimeStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var importButton: Button
    private lateinit var playButton: Button

    private val isoPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
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
            text = "ANDROID ARM64 · PORTE EM DESENVOLVIMENTO"
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(20))
        }, matchWidthWrap())

        content.addView(TextView(this).apply {
            text = "Importe sua própria imagem PAL europeia SCES-53326 v1.00. O aplicativo verifica o tamanho e o SHA-256 antes de aceitar o arquivo."
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
            text = "Jogar — engine ainda não integrada"
            isEnabled = false
        }
        content.addView(playButton, matchWidthWrap())

        val runtimeCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundColor(0xFFECEFF1.toInt())
        }
        runtimeStatus = TextView(this).apply { textSize = 14f }
        runtimeCard.addView(TextView(this).apply {
            text = "Diagnóstico nativo"
            textSize = 16f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }, matchWidthWrap())
        runtimeCard.addView(runtimeStatus, matchWidthWrap().apply { topMargin = dp(8) })
        content.addView(runtimeCard, matchWidthWrap().apply { topMargin = dp(24) })

        content.addView(TextView(this).apply {
            text = "Próximas etapas: portar o runtime recompilado para ARM64, adaptar o renderizador GS para Android e integrar áudio e controles."
            textSize = 13f
            setPadding(0, dp(20), 0, 0)
        }, matchWidthWrap())

        setContentView(scroll)
        refreshIsoStatus()
        refreshRuntimeStatus()
    }

    private fun importIso(uri: Uri) {
        importButton.isEnabled = false
        playButton.isEnabled = false
        progressBar.progress = 0
        progressBar.isIndeterminate = true
        isoStatus.text = "Preparando a importação. O arquivo tem aproximadamente 4,64 GB; mantenha espaço livre suficiente."

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
                    throw IOException("Espaço insuficiente no armazenamento interno do aplicativo. Libere pelo menos 4,76 GB.")
                }

                partial.delete()
                val digest = MessageDigest.getInstance("SHA-256")
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
                                throw IOException("O arquivo é maior que a ISO oficial suportada.")
                            }

                            digest.update(buffer, 0, count)
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
                    throw IOException("Tamanho incorreto: ${total} bytes. A versão suportada possui ${EXPECTED_ISO_BYTES} bytes.")
                }
                val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
                if (!sha256.equals(EXPECTED_ISO_SHA256, ignoreCase = true)) {
                    throw IOException("O SHA-256 não corresponde à versão PAL SCES-53326 v1.00 esperada. A ISO não foi aceita.")
                }

                val destination = isoFile
                if (destination.exists() && !destination.delete()) {
                    throw IOException("Não foi possível substituir a ISO anterior.")
                }
                if (!partial.renameTo(destination)) {
                    throw IOException("A ISO foi verificada, mas não foi possível finalizar a importação.")
                }

                runOnUiThread {
                    progressBar.isIndeterminate = false
                    progressBar.progress = 1000
                    isoStatus.text = "ISO importada e verificada. SHA-256 correto. Arquivo guardado no armazenamento privado do aplicativo."
                    importButton.isEnabled = true
                }
            } catch (error: Exception) {
                partial.delete()
                runOnUiThread {
                    progressBar.isIndeterminate = false
                    progressBar.progress = 0
                    isoStatus.text = "Importação não concluída: ${error.message ?: error.javaClass.simpleName}"
                    importButton.isEnabled = true
                }
            }
        }.start()
    }

    private fun refreshIsoStatus() {
        isoStatus.text = if (isoFile.isFile && isoFile.length() == EXPECTED_ISO_BYTES) {
            "ISO encontrada no armazenamento privado (${formatGiB(isoFile.length())} GB). Ela foi verificada durante a importação."
        } else {
            "Nenhuma ISO validada importada."
        }
    }

    private fun refreshRuntimeStatus() {
        runtimeStatus.text = try {
            nativeRuntimeStatus()
        } catch (error: UnsatisfiedLinkError) {
            "Falha ao carregar biblioteca nativa: ${error.message ?: "erro desconhecido"}"
        }
    }

    private fun formatGiB(bytes: Long): String = String.format("%.2f", bytes / (1024.0 * 1024.0 * 1024.0))

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun matchWidthWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    )
}
