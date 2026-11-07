package com.spenrevival

import android.view.MotionEvent
import kotlin.math.abs

enum class PenGesture {
    TAP, SWIPE_LEFT, SWIPE_RIGHT, SWIPE_UP, SWIPE_DOWN,
    BUTTON_TAP, BUTTON_SWIPE_LEFT, BUTTON_SWIPE_RIGHT, BUTTON_SWIPE_UP, BUTTON_SWIPE_DOWN
}

/** Recognizes stylus taps, swipes, and side-button gestures delivered to this app. */
class GestureEngine(
    private val swipeThresholdPx: Float = 72f,
    private val onGesture: (PenGesture) -> Unit
) {
    private var downX = 0f
    private var downY = 0f
    private var tracking = false
    private var buttonWasPressed = false
    private var lastButtonState = 0

    fun onMotionEvent(event: MotionEvent): Boolean {
        val stylusIndex = (0 until event.pointerCount).firstOrNull {
            event.getToolType(it) == MotionEvent.TOOL_TYPE_STYLUS ||
                event.getToolType(it) == MotionEvent.TOOL_TYPE_ERASER
        } ?: return false

        val pressed = event.buttonState and (
            MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_STYLUS_SECONDARY
        ) != 0
        lastButtonState = event.buttonState

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                downX = event.getX(stylusIndex)
                downY = event.getY(stylusIndex)
                tracking = true
                buttonWasPressed = pressed
            }
            MotionEvent.ACTION_BUTTON_PRESS -> {
                if (!tracking) {
                    downX = event.getX(stylusIndex)
                    downY = event.getY(stylusIndex)
                    tracking = true
                }
                buttonWasPressed = true
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_HOVER_MOVE -> {
                if (pressed) buttonWasPressed = true
            }
            MotionEvent.ACTION_BUTTON_RELEASE -> {
                if (tracking && buttonWasPressed) {
                    emitGesture(event.getX(stylusIndex) - downX, event.getY(stylusIndex) - downY, true)
                    tracking = false
                    buttonWasPressed = false
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (!tracking) return true
                emitGesture(event.getX(stylusIndex) - downX, event.getY(stylusIndex) - downY, buttonWasPressed || pressed)
                tracking = false
                buttonWasPressed = false
            }
            MotionEvent.ACTION_CANCEL -> {
                tracking = false
                buttonWasPressed = false
            }
        }
        return true
    }

    private fun emitGesture(dx: Float, dy: Float, withButton: Boolean) {
        val horizontal = abs(dx) > abs(dy)
        val isTap = maxOf(abs(dx), abs(dy)) < swipeThresholdPx
        val gesture = when {
            isTap && withButton -> PenGesture.BUTTON_TAP
            isTap -> PenGesture.TAP
            horizontal && dx > 0 && withButton -> PenGesture.BUTTON_SWIPE_RIGHT
            horizontal && dx < 0 && withButton -> PenGesture.BUTTON_SWIPE_LEFT
            !horizontal && dy > 0 && withButton -> PenGesture.BUTTON_SWIPE_DOWN
            !horizontal && dy < 0 && withButton -> PenGesture.BUTTON_SWIPE_UP
            horizontal && dx > 0 -> PenGesture.SWIPE_RIGHT
            horizontal -> PenGesture.SWIPE_LEFT
            dy > 0 -> PenGesture.SWIPE_DOWN
            else -> PenGesture.SWIPE_UP
        }
        onGesture(gesture)
    }
}