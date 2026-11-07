package com.spenrevival

import android.view.MotionEvent
import kotlin.math.abs

enum class PenGesture {
    BUTTON_TAP, BUTTON_DOUBLE_TAP, BUTTON_HOLD,
    BUTTON_SWIPE_LEFT, BUTTON_SWIPE_RIGHT, BUTTON_SWIPE_UP, BUTTON_SWIPE_DOWN
}

/**
 * Recognizes only side-button gestures delivered to this app.
 * Ordinary pen input is passed through unchanged.
 */
class GestureEngine(
    private val swipeThresholdPx: Float = 72f,
    private val onGesture: (PenGesture) -> Unit
) {
    private var downX = 0f
    private var downY = 0f
    private var downAt = 0L
    private var trackingButtonGesture = false
    private var lastTapAt = 0L

    fun onMotionEvent(event: MotionEvent): Boolean {
        val stylusIndex = (0 until event.pointerCount).firstOrNull {
            event.getToolType(it) == MotionEvent.TOOL_TYPE_STYLUS ||
                event.getToolType(it) == MotionEvent.TOOL_TYPE_ERASER
        } ?: return false

        val buttonPressed = event.buttonState and (
            MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_STYLUS_SECONDARY
        ) != 0

        when (event.actionMasked) {
            MotionEvent.ACTION_BUTTON_PRESS -> begin(event, stylusIndex)
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_HOVER_MOVE -> {
                if (buttonPressed && !trackingButtonGesture) begin(event, stylusIndex)
            }
            MotionEvent.ACTION_BUTTON_RELEASE -> finish(event, stylusIndex)
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (buttonPressed) begin(event, stylusIndex)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (trackingButtonGesture && buttonPressed) finish(event, stylusIndex)
                else if (trackingButtonGesture && !buttonPressed) finish(event, stylusIndex)
            }
            MotionEvent.ACTION_CANCEL -> trackingButtonGesture = false
        }
        return true
    }

    private fun begin(event: MotionEvent, index: Int) {
        downX = event.getX(index)
        downY = event.getY(index)
        downAt = event.eventTime
        trackingButtonGesture = true
    }

    private fun finish(event: MotionEvent, index: Int) {
        if (!trackingButtonGesture) return
        val dx = event.getX(index) - downX
        val dy = event.getY(index) - downY
        val duration = (event.eventTime - downAt).coerceAtLeast(0L)
        trackingButtonGesture = false
        emitButtonGesture(dx, dy, duration, event.eventTime)
    }

    private fun emitButtonGesture(dx: Float, dy: Float, duration: Long, eventTime: Long) {
        val horizontal = abs(dx) > abs(dy)
        val isTap = maxOf(abs(dx), abs(dy)) < swipeThresholdPx
        val gesture = when {
            !isTap && horizontal && dx > 0 -> PenGesture.BUTTON_SWIPE_RIGHT
            !isTap && horizontal -> PenGesture.BUTTON_SWIPE_LEFT
            !isTap && dy > 0 -> PenGesture.BUTTON_SWIPE_DOWN
            !isTap -> PenGesture.BUTTON_SWIPE_UP
            duration >= 550L -> PenGesture.BUTTON_HOLD
            eventTime - lastTapAt in 1..350 -> {
                lastTapAt = 0L
                PenGesture.BUTTON_DOUBLE_TAP
            }
            else -> {
                lastTapAt = eventTime
                PenGesture.BUTTON_TAP
            }
        }
        onGesture(gesture)
    }
}
