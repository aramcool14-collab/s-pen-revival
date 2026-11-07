package com.spenrevival

import android.view.MotionEvent
import kotlin.math.abs

enum class PenGesture {
    TAP, SWIPE_LEFT, SWIPE_RIGHT, SWIPE_UP, SWIPE_DOWN, BUTTON_TAP
}

/** Recognizes simple gestures made with a stylus touching the display. */
class GestureEngine(
    private val swipeThresholdPx: Float = 72f,
    private val onGesture: (PenGesture) -> Unit
) {
    private var downX = 0f
    private var downY = 0f
    private var tracking = false
    private var buttonWasPressed = false

    fun onMotionEvent(event: MotionEvent): Boolean {
        val stylusIndex = (0 until event.pointerCount).firstOrNull {
            event.getToolType(it) == MotionEvent.TOOL_TYPE_STYLUS ||
                event.getToolType(it) == MotionEvent.TOOL_TYPE_ERASER
        } ?: return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                downX = event.getX(stylusIndex)
                downY = event.getY(stylusIndex)
                tracking = true
                buttonWasPressed =
                    event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY != 0
            }
            MotionEvent.ACTION_MOVE -> {
                if (tracking && event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY != 0) {
                    buttonWasPressed = true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (!tracking) return true
                val dx = event.getX(stylusIndex) - downX
                val dy = event.getY(stylusIndex) - downY
                tracking = false
                if (buttonWasPressed) {
                    onGesture(PenGesture.BUTTON_TAP)
                } else if (maxOf(abs(dx), abs(dy)) < swipeThresholdPx) {
                    onGesture(PenGesture.TAP)
                } else if (abs(dx) > abs(dy)) {
                    onGesture(if (dx > 0) PenGesture.SWIPE_RIGHT else PenGesture.SWIPE_LEFT)
                } else {
                    onGesture(if (dy > 0) PenGesture.SWIPE_DOWN else PenGesture.SWIPE_UP)
                }
            }
            MotionEvent.ACTION_CANCEL -> tracking = false
        }
        return true
    }
}
