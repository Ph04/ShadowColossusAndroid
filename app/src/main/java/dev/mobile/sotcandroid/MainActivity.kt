package dev.mobile.sotcandroid

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    companion object {
        init {
            System.loadLibrary("sotc_android_bridge")
        }
    }

    private external fun nativeRuntimeStatus(): String
    private lateinit var status: TextView

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
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }, matchWidthWrap())

        content.addView(TextView(this).apply {
            text = "ANDROID ARM64 · BASE DE DESENVOLVIMENTO"
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(24))
        }, matchWidthWrap())

        content.addView(TextView(this).apply {
            text = "Esta compilação verifica o aplicativo, a NDK e a comunicação JNI. Ainda não é uma versão jogável e não contém dados do jogo."
            textSize = 16f
            gravity = Gravity.CENTER
        }, matchWidthWrap())

        status = TextView(this).apply {
            textSize = 14f
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val statusCard = LinearLayout(this).apply {
            setPadding(dp(16), dp(12), dp(16), dp(12))
            addView(status, matchWidthWrap())
            setBackgroundColor(0xFFECEFF1.toInt())
        }
        val cardParams = matchWidthWrap().apply { topMargin = dp(24) }
        content.addView(statusCard, cardParams)
        updateStatus()

        content.addView(Button(this).apply {
            text = "Verificar biblioteca nativa"
            setOnClickListener { updateStatus() }
        }, matchWidthWrap().apply { topMargin = dp(20) })

        content.addView(TextView(this).apply {
            text = "Próximos marcos: portar o runtime recompilado, adaptar renderização e áudio, integrar controles de toque e validar gameplay real."
            textSize = 14f
            setPadding(0, dp(28), 0, 0)
        }, matchWidthWrap())

        setContentView(scroll)
    }

    private fun updateStatus() {
        status.text = try {
            nativeRuntimeStatus()
        } catch (error: UnsatisfiedLinkError) {
            "Falha ao carregar a biblioteca nativa: ${error.message ?: "erro desconhecido"}"
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun matchWidthWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    )
}
