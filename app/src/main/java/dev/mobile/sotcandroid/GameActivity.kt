package dev.mobile.sotcandroid

import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.app.NativeActivity

class GameActivity : NativeActivity() {
    companion object {
        init {
            System.loadLibrary("sotc_android_touch")
        }
    }

    private external fun nativeSetTouchButton(buttonMask: Int, pressed: Boolean)
    private external fun nativeSetTouchStick(stick: Int, x: Float, y: Float, active: Boolean)
    private external fun nativeResetTouch()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyImmersiveMode()

        window.decorView.post {
            val content = findViewById<ViewGroup>(android.R.id.content)
            if (content.findViewWithTag<android.view.View>("sotc-touch-overlay") != null) return@post

            val overlay = TouchControlsView(
                context = this,
                onButton = { mask, pressed -> nativeSetTouchButton(mask, pressed) },
                onStick = { stick, x, y, active -> nativeSetTouchStick(stick, x, y, active) },
            ).apply { tag = "sotc-touch-overlay" }
            content.addView(
                overlay,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersiveMode()
    }

    override fun onPause() {
        nativeResetTouch()
        super.onPause()
    }

    override fun onDestroy() {
        nativeResetTouch()
        super.onDestroy()
    }

    private fun applyImmersiveMode() {
        window.decorView.systemUiVisibility = (
            android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
    }
}
