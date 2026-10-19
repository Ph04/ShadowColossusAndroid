package dev.mobile.sotcandroid

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.min

/**
 * Transparent, multi-touch overlay. A touch outside a control is deliberately
 * passed through to the NativeActivity so raylib can still receive normal input.
 */
class TouchControlsView(
    context: Context,
    private val onButton: (mask: Int, pressed: Boolean) -> Unit,
    private val onStick: (stick: Int, x: Float, y: Float, active: Boolean) -> Unit,
) : View(context) {
    companion object {
        private const val SELECT = 1 shl 0
        private const val L3 = 1 shl 1
        private const val R3 = 1 shl 2
        private const val START = 1 shl 3
        private const val UP = 1 shl 4
        private const val RIGHT = 1 shl 5
        private const val DOWN = 1 shl 6
        private const val LEFT = 1 shl 7
        private const val L2 = 1 shl 8
        private const val R2 = 1 shl 9
        private const val L1 = 1 shl 10
        private const val R1 = 1 shl 11
        private const val TRIANGLE = 1 shl 12
        private const val CIRCLE = 1 shl 13
        private const val CROSS = 1 shl 14
        private const val SQUARE = 1 shl 15
        private val ALL_BUTTONS = intArrayOf(
            SELECT, L3, R3, START, UP, RIGHT, DOWN, LEFT, L2, R2, L1, R1,
            TRIANGLE, CIRCLE, CROSS, SQUARE,
        )
    }

    private enum class Kind { LEFT_STICK, RIGHT_STICK, BUTTON, TOGGLE }
    private data class Target(val kind: Kind, val mask: Int = 0)
    private data class ButtonPosition(val mask: Int, val label: String, val x: Float, val y: Float, val radius: Float)
    private data class StickPosition(val x: Float, val y: Float, val radius: Float)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(2f) }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dp(13f)
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
    }
    private val activePointers = linkedMapOf<Int, Target>()
    private val currentlyPressed = mutableSetOf<Int>()
    private var controlsVisible = true
    private var leftStickX = 0f
    private var leftStickY = 0f
    private var rightStickX = 0f
    private var rightStickY = 0f
    private var togglePointer = -1

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun leftPosition() = StickPosition(width * 0.15f, height * 0.78f, min(width, height) * 0.13f)
    private fun rightPosition() = StickPosition(width * 0.62f, height * 0.78f, min(width, height) * 0.12f)
    private fun buttonRadius() = min(width, height) * 0.047f

    private fun buttons(): List<ButtonPosition> {
        val r = buttonRadius()
        val shoulderR = min(width, height) * 0.043f
        return listOf(
            ButtonPosition(L1, "L1", width * 0.08f, height * 0.14f, shoulderR),
            ButtonPosition(L2, "L2", width * 0.18f, height * 0.14f, shoulderR),
            ButtonPosition(R1, "R1", width * 0.80f, height * 0.14f, shoulderR),
            ButtonPosition(R2, "R2", width * 0.90f, height * 0.14f, shoulderR),
            ButtonPosition(SELECT, "SELECT", width * 0.44f, height * 0.075f, shoulderR * 0.9f),
            ButtonPosition(START, "START", width * 0.56f, height * 0.075f, shoulderR * 0.9f),
            ButtonPosition(TRIANGLE, "△", width * 0.86f, height * 0.64f, r),
            ButtonPosition(SQUARE, "□", width * 0.81f, height * 0.78f, r),
            ButtonPosition(CIRCLE, "○", width * 0.91f, height * 0.78f, r),
            ButtonPosition(CROSS, "×", width * 0.86f, height * 0.91f, r),
        )
    }

    private fun toggleRect(): RectF {
        val w = dp(92f)
        val h = dp(30f)
        return RectF(width * 0.5f - w / 2f, dp(8f), width * 0.5f + w / 2f, dp(8f) + h)
    }

    private fun targetAt(x: Float, y: Float): Target? {
        if (toggleRect().contains(x, y)) return Target(Kind.TOGGLE)
        if (!controlsVisible) return null

        val button = buttons().asReversed().firstOrNull {
            hypot(x - it.x, y - it.y) <= it.radius * 1.65f
        }
        if (button != null) return Target(Kind.BUTTON, button.mask)

        val left = leftPosition()
        if (hypot(x - left.x, y - left.y) <= left.radius * 1.6f &&
            activePointers.values.none { it.kind == Kind.LEFT_STICK }) {
            return Target(Kind.LEFT_STICK)
        }
        val right = rightPosition()
        if (hypot(x - right.x, y - right.y) <= right.radius * 1.6f &&
            activePointers.values.none { it.kind == Kind.RIGHT_STICK }) {
            return Target(Kind.RIGHT_STICK)
        }
        return null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                val id = event.getPointerId(index)
                val target = targetAt(event.getX(index), event.getY(index)) ?: return false
                activePointers[id] = target
                if (target.kind == Kind.TOGGLE) {
                    togglePointer = id
                    controlsVisible = !controlsVisible
                    if (!controlsVisible) {
                        releaseAllControls()
                        activePointers.clear()
                        activePointers[id] = target
                    }
                } else {
                    updateTarget(id, event.getX(index), event.getY(index))
                    syncButtons()
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (activePointers.isEmpty()) return false
                for (index in 0 until event.pointerCount) {
                    val id = event.getPointerId(index)
                    if (activePointers.containsKey(id)) updateTarget(id, event.getX(index), event.getY(index))
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                val index = event.actionIndex
                val id = event.getPointerId(index)
                val target = activePointers.remove(id)
                if (target != null) {
                    when (target.kind) {
                        Kind.LEFT_STICK -> {
                            leftStickX = 0f; leftStickY = 0f
                            if (activePointers.values.none { it.kind == Kind.LEFT_STICK }) onStick(0, 0f, 0f, false)
                        }
                        Kind.RIGHT_STICK -> {
                            rightStickX = 0f; rightStickY = 0f
                            if (activePointers.values.none { it.kind == Kind.RIGHT_STICK }) onStick(1, 0f, 0f, false)
                        }
                        Kind.BUTTON -> syncButtons()
                        Kind.TOGGLE -> if (togglePointer == id) togglePointer = -1
                    }
                    syncButtons()
                    invalidate()
                    return true
                }
                return activePointers.isNotEmpty()
            }

            MotionEvent.ACTION_CANCEL -> {
                releaseAllControls()
                activePointers.clear()
                togglePointer = -1
                invalidate()
                return true
            }
        }
        return activePointers.isNotEmpty()
    }

    private fun updateTarget(id: Int, x: Float, y: Float) {
        when (activePointers[id]?.kind) {
            Kind.LEFT_STICK -> {
                val pos = leftPosition()
                val vector = normalized(x - pos.x, y - pos.y, pos.radius)
                leftStickX = vector.first; leftStickY = vector.second
                onStick(0, leftStickX, leftStickY, true)
            }
            Kind.RIGHT_STICK -> {
                val pos = rightPosition()
                val vector = normalized(x - pos.x, y - pos.y, pos.radius)
                rightStickX = vector.first; rightStickY = vector.second
                onStick(1, rightStickX, rightStickY, true)
            }
            else -> Unit
        }
    }

    private fun normalized(dx: Float, dy: Float, radius: Float): Pair<Float, Float> {
        var x = (dx / radius).coerceIn(-1f, 1f)
        var y = (dy / radius).coerceIn(-1f, 1f)
        val length = hypot(x, y)
        if (length > 1f) { x /= length; y /= length }
        if (hypot(x, y) < 0.10f) return 0f to 0f
        return x to y
    }

    private fun syncButtons() {
        val now = activePointers.values
            .filter { it.kind == Kind.BUTTON }
            .map { it.mask }
            .toSet()
        for (mask in ALL_BUTTONS) {
            val wasPressed = currentlyPressed.contains(mask)
            val isPressed = now.contains(mask)
            if (wasPressed != isPressed) onButton(mask, isPressed)
        }
        currentlyPressed.clear()
        currentlyPressed.addAll(now)
    }

    private fun releaseAllControls() {
        currentlyPressed.forEach { onButton(it, false) }
        currentlyPressed.clear()
        leftStickX = 0f; leftStickY = 0f
        rightStickX = 0f; rightStickY = 0f
        onStick(0, 0f, 0f, false)
        onStick(1, 0f, 0f, false)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawToggle(canvas)
        if (!controlsVisible) return

        drawStick(canvas, leftPosition(), leftStickX, leftStickY, "MOVIMENTO")
        drawStick(canvas, rightPosition(), rightStickX, rightStickY, "CÂMERA")
        val active = currentlyPressed
        for (button in buttons()) {
            val pressed = active.contains(button.mask)
            fill.color = if (pressed) 0xAADDFFFF.toInt() else 0x55606060
            canvas.drawCircle(button.x, button.y, button.radius, fill)
            stroke.color = if (pressed) 0xFFFFFFFF.toInt() else 0x99FFFFFF.toInt()
            canvas.drawCircle(button.x, button.y, button.radius, stroke)
            text.color = if (pressed) 0xFF17212B.toInt() else 0xFFFFFFFF.toInt()
            text.textSize = if (button.label.length > 3) dp(9f) else dp(14f)
            canvas.drawText(button.label, button.x, button.y + text.textSize * 0.35f, text)
        }
    }

    private fun drawToggle(canvas: Canvas) {
        val rect = toggleRect()
        fill.color = 0x77505050
        canvas.drawRoundRect(rect, dp(12f), dp(12f), fill)
        stroke.color = 0x99FFFFFF.toInt()
        canvas.drawRoundRect(rect, dp(12f), dp(12f), stroke)
        text.textSize = dp(10f)
        text.color = 0xFFFFFFFF.toInt()
        canvas.drawText(if (controlsVisible) "CONTROLES  ON" else "CONTROLES  OFF",
            rect.centerX(), rect.centerY() + text.textSize * 0.35f, text)
    }

    private fun drawStick(canvas: Canvas, pos: StickPosition, x: Float, y: Float, label: String) {
        fill.color = 0x44303030
        canvas.drawCircle(pos.x, pos.y, pos.radius, fill)
        stroke.color = 0x99FFFFFF.toInt()
        canvas.drawCircle(pos.x, pos.y, pos.radius, stroke)
        val knobRadius = pos.radius * 0.42f
        fill.color = 0x99FFFFFF.toInt()
        canvas.drawCircle(pos.x + x * pos.radius * 0.62f, pos.y + y * pos.radius * 0.62f, knobRadius, fill)
        text.color = 0xDDFFFFFF.toInt()
        text.textSize = dp(9f)
        canvas.drawText(label, pos.x, pos.y + pos.radius + dp(13f), text)
    }
}
