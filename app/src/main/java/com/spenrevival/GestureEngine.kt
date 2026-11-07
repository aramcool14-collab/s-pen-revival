package com.spenrevival

import android.view.MotionEvent
import kotlin.math.abs

enum class PenGesture {
    BUTTON_TAP, BUTTON_SWIPE_LEFT, BUTTON_SWIPE_RIGHT, BUTTON_SWIPE_UP, BUTTON_SWIPE_DOWN
}

/**
 * Recognizes only side-button gestures delivered to this app.
 * Unmodified pen taps and swipes are never converted into shortcuts.
 */
class GestureEngine(
    private val swipeThresholdPx: Float = 72f,
    private val onGesture: (PenGesture) -> Unit
) {
    private var downX = 0f
    private var downY = 0f
    private var trackingButtonGesture = false

    fun onMotionEvent(event: MotionEvent): Boolean {
        val stylusIndex = (0 until event.pointerCount).firstOrNull {
            event.getToolType(it) == MotionEvent.TOOL_TYPE_STYLUS ||
                event.getToolType(it) == MotionEvent.TOOL_TYPE_ERASER
        } ?: return false

        val buttonPressed = event.buttonState and (
            MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_STYLUS_SECONDARY
        ) != 0

        when (event.actionMasked) {
            MotionEvent.ACTION_BUTTON_PRESS -> {
                downX = event.getX(stylusIndex)
                downY = event.getY(stylusIndex)
                trackingButtonGesture = true
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_HOVER_MOVE -> {
                if (buttonPressed && !trackingButtonGesture) {
                    downX = event.getX(stylusIndex)
                    downY = event.getY(stylusIndex)
                    trackingButtonGesture = true
                }
            }
            MotionEvent.ACTION_BUTTON_RELEASE -> {
                if (trackingButtonGesture) {
                    emitButtonGesture(
                        event.getX(stylusIndex) - downX,
                        event.getY(stylusIndex) - downY
                    )
                }
                trackingButtonGesture = false
            }
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (buttonPressed) {
                    downX = event.getX(stylusIndex)
                    downY = event.getY(stylusIndex)
                    trackingButtonGesture = true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (trackingButtonGesture && buttonPressed) {
                    emitButtonGesture(
                        event.getX(stylusIndex) - downX,
                        event.getY(stylusIndex) - downY
                    )
                }
                trackingButtonGesture = false
            }
            MotionEvent.ACTION_CANCEL -> trackingButtonGesture = false
        }
        return true
    }

    private fun emitButtonGesture(dx: Float, dy: Float) {
        val horizontal = abs(dx) > abs(dy)
        val isTap = maxOf(abs(dx), abs(dy)) < swipeThresholdPx
        val gesture = when {
            isTap -> PenGesture.BUTTON_TAP
            horizontal && dx > 0 -> PenGesture.BUTTON_SWIPE_RIGHT
            horizontal -> PenGesture.BUTTON_SWIPE_LEFT
            dy > 0 -> PenGesture.BUTTON_SWIPE_DOWN
            else -> PenGesture.BUTTON_SWIPE_UP
        }
        onGesture(gesture)
    }
}
