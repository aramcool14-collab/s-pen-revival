package com.spenrevival

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.view.accessibility.AccessibilityEvent

/**
 * Provides an optional floating shortcut panel.
 * It deliberately does not subscribe to or intercept stylus/touch motion events.
 */
class SpenAccessibilityService : AccessibilityService() {
    private var windowManager: WindowManager? = null
    private var floatingRoot: LinearLayout? = null
    private var menu: LinearLayout? = null
    private var menuVisible = false
    private lateinit var commands: CommandManager

    override fun onServiceConnected() {
        super.onServiceConnected()
        commands = CommandManager(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        showFloatingMenuIfAllowed()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // A window change can happen when the user returns from Android's overlay settings.
        showFloatingMenuIfAllowed()
    }

    override fun onInterrupt() = Unit

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
        addMenuAction(panel, "Back") { performGlobalAction(GLOBAL_ACTION_BACK) }
        addMenuAction(panel, "Home") { performGlobalAction(GLOBAL_ACTION_HOME) }
        addMenuAction(panel, "Recent apps") { performGlobalAction(GLOBAL_ACTION_RECENTS) }
        addMenuAction(panel, "Volume up") { commands.volumeUp(); true }
        addMenuAction(panel, "Volume down") { commands.volumeDown(); true }
        addMenuAction(panel, "Play / pause") { commands.mediaPlayPause(); true }
        addMenuAction(panel, "Open camera") { commands.launchCamera() }
        root.addView(panel, LinearLayout.LayoutParams(dp(220), WindowManager.LayoutParams.WRAP_CONTENT).apply {
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
    }

    override fun onDestroy() {
        removeFloatingMenu()
        super.onDestroy()
    }
}