package com.spenrevival

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.view.accessibility.AccessibilityEvent

/**
 * Provides the floating shortcut panel and an opt-in-by-enabling-the-service
 * stylus event listener. It requests only SOURCE_STYLUS, never finger touch
 * input or screen contents.
 */
class SpenAccessibilityService : AccessibilityService() {
    private var windowManager: WindowManager? = null
    private var floatingRoot: LinearLayout? = null
    private var menu: LinearLayout? = null
    private var menuVisible = false
    private var listenerStatus: TextView? = null
    private var captureToggle: TextView? = null
    private var stylusCaptureEnabled = false
    private lateinit var commands: CommandManager

    private val prefs by lazy { getSharedPreferences("spen_settings", Context.MODE_PRIVATE) }
    private val capturedPoints = mutableListOf<GesturePoint>()
    private var capturingGesture = false
    private var lastPointX = Float.NaN
    private var lastPointY = Float.NaN

    override fun onServiceConnected() {
        super.onServiceConnected()
        commands = CommandManager(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // Global stylus capture stays OFF until the user enables it in the
        // floating menu. Android routes subscribed stylus events away from
        // foreground apps, so this tradeoff must be explicit.
        setStylusCapture(false)
        showFloatingMenuIfAllowed()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Keep the overlay available when the user returns from Android settings.
        showFloatingMenuIfAllowed()
    }

    /**
     * Android 14+ routes the explicitly requested stylus motion source here.
     * Finger/touchscreen sources are deliberately not requested.
     */
    override fun onMotionEvent(event: MotionEvent) {
        if (Build.VERSION.SDK_INT < 34) return
        val stylusIndex = (0 until event.pointerCount).firstOrNull {
            event.getToolType(it) == MotionEvent.TOOL_TYPE_STYLUS ||
                event.getToolType(it) == MotionEvent.TOOL_TYPE_ERASER
        } ?: return

        val action = event.actionMasked
        val buttonMask = MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_STYLUS_SECONDARY
        val buttonHeld = event.buttonState and buttonMask != 0

        when {
            action == MotionEvent.ACTION_BUTTON_PRESS -> {
                beginGesture(event, stylusIndex)
                appendPoint(event, stylusIndex)
                updateListenerStatus("S Pen button detected — draw your saved shape")
            }
            action == MotionEvent.ACTION_BUTTON_RELEASE -> {
                if (capturingGesture) appendPoint(event, stylusIndex)
                finishGesture()
            }
            buttonHeld -> {
                if (!capturingGesture) beginGesture(event, stylusIndex)
                if (action == MotionEvent.ACTION_HOVER_MOVE ||
                    action == MotionEvent.ACTION_MOVE ||
                    action == MotionEvent.ACTION_DOWN ||
                    action == MotionEvent.ACTION_UP ||
                    action == MotionEvent.ACTION_HOVER_ENTER) {
                    appendPoint(event, stylusIndex)
                }
                updateListenerStatus("S Pen gesture in progress")
            }
            capturingGesture && (action == MotionEvent.ACTION_HOVER_EXIT ||
                action == MotionEvent.ACTION_CANCEL ||
                action == MotionEvent.ACTION_UP) -> {
                appendPoint(event, stylusIndex)
                finishGesture()
            }
            action == MotionEvent.ACTION_HOVER_ENTER -> updateListenerStatus("S Pen detected — hold its button to draw")
        }
    }

    override fun onInterrupt() = Unit

    private fun beginGesture(event: MotionEvent, index: Int) {
        capturingGesture = true
        capturedPoints.clear()
        lastPointX = Float.NaN
        lastPointY = Float.NaN
        appendPoint(event, index)
    }

    private fun appendPoint(event: MotionEvent, index: Int) {
        val x = event.getX(index)
        val y = event.getY(index)
        if (lastPointX.isNaN() || kotlin.math.abs(x - lastPointX) + kotlin.math.abs(y - lastPointY) >= 1.5f) {
            capturedPoints.add(GesturePoint(x, y))
            lastPointX = x
            lastPointY = y
        }
    }

    private fun finishGesture() {
        if (!capturingGesture) return
        capturingGesture = false
        val points = capturedPoints.toList()
        capturedPoints.clear()
        if (!prefs.getBoolean("gestures_enabled", true)) {
            updateListenerStatus("Custom gestures are switched off")
            return
        }
        if (points.size < 6) {
            updateListenerStatus("S Pen detected — not enough movement for a gesture")
            return
        }

        val ids = prefs.all.keys
            .filter { it.startsWith("custom_") && it.endsWith("_name") }
            .map { it.removeSuffix("_name") }
            .sorted()

        for (id in ids) {
            val template = GesturePattern.decode(prefs.getString("${id}_pattern", null))
            if (GesturePattern.matches(points, template)) {
                val name = prefs.getString("${id}_name", "Custom gesture") ?: "Custom gesture"
                val action = prefs.getString("${id}_action", "Do nothing") ?: "Do nothing"
                val succeeded = ActionCatalog.execute(this, action)
                updateListenerStatus(if (succeeded) "Matched $name — $action" else "Matched $name — action unavailable")
                return
            }
        }
        updateListenerStatus("S Pen gesture received — no saved shape matched")
    }

    private fun updateListenerStatus(value: String) {
        prefs.edit().putString("global_stylus_status", value).apply()
        listenerStatus?.text = value
    }

    private fun showFloatingMenuIfAllowed() {
        if (!Settings.canDrawOverlays(this) || floatingRoot != null) return
        val wm = windowManager ?: return

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }

        val bubble = TextView(this).apply {
            text = "✦"
            textSize = 27f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(45, 105, 210), 28)
            elevation = dp(8).toFloat()
            setOnClickListener { toggleMenu() }
        }
        root.addView(bubble, LinearLayout.LayoutParams(dp(56), dp(56)))

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(Color.rgb(20, 27, 42), 20)
            visibility = View.GONE
            elevation = dp(12).toFloat()
        }
        menu = panel
        listenerStatus = TextView(this).apply {
            text = prefs.getString("global_stylus_status", "Waiting for S Pen input") ?: "Waiting for S Pen input"
            textSize = 12f
            setTextColor(Color.rgb(174, 187, 207))
            setPadding(dp(8), dp(8), dp(8), dp(12))
        }
        panel.addView(listenerStatus)
        captureToggle = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(Color.rgb(45, 105, 130), 12)
            setOnClickListener {
                setStylusCapture(!stylusCaptureEnabled)
                menuVisible = false
                menu?.visibility = View.GONE
            }
        }
        panel.addView(captureToggle, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) })
        refreshCaptureToggle()
        addMenuAction(panel, "Back") { performGlobalAction(GLOBAL_ACTION_BACK) }
        addMenuAction(panel, "Home") { performGlobalAction(GLOBAL_ACTION_HOME) }
        addMenuAction(panel, "Recent apps") { performGlobalAction(GLOBAL_ACTION_RECENTS) }
        addMenuAction(panel, "Volume up") { commands.volumeUp(); true }
        addMenuAction(panel, "Volume down") { commands.volumeDown(); true }
        addMenuAction(panel, "Play / pause") { commands.mediaPlayPause(); true }
        addMenuAction(panel, "Open camera") { commands.launchCamera() }
        root.addView(panel, LinearLayout.LayoutParams(dp(240), WindowManager.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8)
        })

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(12)
            y = dp(180)
        }

        try {
            wm.addView(root, params)
            floatingRoot = root
        } catch (_: Exception) {
            floatingRoot = null
        }
    }

    private fun toggleMenu() {
        menuVisible = !menuVisible
        menu?.visibility = if (menuVisible) View.VISIBLE else View.GONE
        if (menuVisible) refreshCaptureToggle()
    }

    private fun refreshCaptureToggle() {
        captureToggle?.text = if (stylusCaptureEnabled) {
            "Stop S Pen gesture capture"
        } else {
            "Start S Pen gesture capture"
        }
    }

    private fun setStylusCapture(enabled: Boolean) {
        if (Build.VERSION.SDK_INT < 34) {
            stylusCaptureEnabled = false
            updateListenerStatus("Global S Pen gestures need Android 14 or newer")
            refreshCaptureToggle()
            return
        }
        try {
            val info = serviceInfo
            info.setMotionEventSources(if (enabled) InputDevice.SOURCE_STYLUS else 0)
            setServiceInfo(info)
            stylusCaptureEnabled = enabled
            updateListenerStatus(
                if (enabled) "S Pen capture ON — pen strokes may not reach the app underneath"
                else "S Pen capture OFF — normal pen input restored"
            )
        } catch (_: Exception) {
            stylusCaptureEnabled = false
            updateListenerStatus("Could not change S Pen capture")
        }
        refreshCaptureToggle()
    }

    private fun addMenuAction(parent: LinearLayout, title: String, action: () -> Boolean) {
        val item = TextView(this).apply {
            text = title
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(11), dp(12), dp(11))
            background = rounded(Color.rgb(38, 52, 76), 12)
            setOnClickListener {
                action()
                menuVisible = false
                menu?.visibility = View.GONE
            }
        }
        parent.addView(item, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(6) })
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun removeFloatingMenu() {
        val root = floatingRoot ?: return
        try { windowManager?.removeView(root) } catch (_: Exception) { }
        floatingRoot = null
        menu = null
        listenerStatus = null
    }

    override fun onDestroy() {
        removeFloatingMenu()
        super.onDestroy()
    }
}
