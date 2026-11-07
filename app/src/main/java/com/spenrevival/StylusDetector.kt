package com.spenrevival

import android.view.MotionEvent

/** Reports stylus events, including side-button state, delivered to this app's window. */
class StylusDetector(
    private val onStylusEvent: (String) -> Unit,
    private val gestureEngine: GestureEngine
) {
    fun handle(event: MotionEvent): Boolean {
        val index = (0 until event.pointerCount).firstOrNull {
            event.getToolType(it) == MotionEvent.TOOL_TYPE_STYLUS ||
                event.getToolType(it) == MotionEvent.TOOL_TYPE_ERASER
        } ?: return false

        val tool = if (event.getToolType(index) == MotionEvent.TOOL_TYPE_ERASER) "eraser" else "stylus"
        val primary = event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY != 0
        val secondary = event.buttonState and MotionEvent.BUTTON_STYLUS_SECONDARY != 0
        val button = when {
            primary -> " · side button held"
            secondary -> " · secondary button held"
            event.actionMasked == MotionEvent.ACTION_BUTTON_RELEASE -> " · button released"
            event.actionMasked == MotionEvent.ACTION_BUTTON_PRESS -> " · button pressed"
            else -> ""
        }
        val action = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> "touch started"
            MotionEvent.ACTION_MOVE -> "moving"
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> "touch ended"
            MotionEvent.ACTION_HOVER_ENTER -> "hover entered"
            MotionEvent.ACTION_HOVER_MOVE -> "hovering"
            MotionEvent.ACTION_HOVER_EXIT -> "hover ended"
            MotionEvent.ACTION_BUTTON_PRESS -> "button pressed"
            MotionEvent.ACTION_BUTTON_RELEASE -> "button released"
            else -> "event"
        }
        onStylusEvent("S Pen $tool: $action$button")
        gestureEngine.onMotionEvent(event)
        return true
    }
}