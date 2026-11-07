package com.spenrevival

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var lastGesture: TextView
    private lateinit var lastAction: TextView
    private lateinit var detector: StylusDetector
    private lateinit var gestureSwitch: Switch

    private val page = Color.rgb(8, 12, 22)
    private val panel = Color.rgb(24, 31, 47)
    private val accent = Color.rgb(111, 164, 255)
    private val muted = Color.rgb(174, 187, 207)
    private val prefs by lazy { getSharedPreferences("spen_settings", MODE_PRIVATE) }
    private val gestureKeys = listOf(
        "button_tap", "button_double_tap", "button_hold",
        "button_swipe_left", "button_swipe_right", "button_swipe_up", "button_swipe_down"
    )
    private val gestureNames = listOf(
        "Side button + tap", "Side button + double tap", "Side button + hold",
        "Side button + swipe left", "Side button + swipe right",
        "Side button + swipe up", "Side button + swipe down"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = page
        window.navigationBarColor = page

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(28))
            setBackgroundColor(page)
        }
        val scroll = ScrollView(this).apply { addView(root) }

        root.addView(label("S PEN REVIVAL  •  ONE UI EDITION", 12f, accent, true))
        root.addView(label("Make your S Pen yours.", 29f, Color.WHITE, true, 10))
        root.addView(label("Shortcuts, gestures and camera controls.", 14f, muted, false, 5))

        val cameraCard = card()
        cameraCard.addView(label("S PEN CAMERA", 12f, accent, true))
        cameraCard.addView(label("A dedicated camera with touch controls for zoom, exposure, flash and more.", 14f, Color.WHITE, false, 9))
        addButton(cameraCard, "Open S Pen Camera  ›", true) {
            startActivity(Intent(this, CameraActivity::class.java))
        }
        root.addView(cameraCard, params(20))

        val setupCard = card()
        setupCard.addView(label("AIR COMMAND", 12f, accent, true))
        setupCard.addView(label("Keep a floating shortcut bubble above your apps. It does not need split screen.", 14f, Color.WHITE, false, 8))
        addButton(setupCard, "Allow floating menu", false) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        addButton(setupCard, "Accessibility service settings", false) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        setupCard.addView(label("The service provides visible shortcuts. It does not read screen contents or intercept normal pen movement.", 12f, muted, false, 8))
        root.addView(setupCard, params(14))

        val statusCard = card()
        status = label("S Pen input is detected while this app is open.", 14f, Color.WHITE, true)
        lastGesture = label("Last gesture: none", 13f, muted, false, 10)
        lastAction = label("Last action: none", 13f, muted, false, 5)
        statusCard.addView(status)
        statusCard.addView(lastGesture)
        statusCard.addView(lastAction)
        root.addView(statusCard, params(14))

        val customCard = card()
        customCard.addView(label("GESTURE STUDIO", 12f, accent, true))
        customCard.addView(label("Choose a gesture, then choose what happens. You can customize every gesture independently.", 14f, Color.WHITE, false, 8))
        gestureSwitch = Switch(this).apply {
            text = "Custom gestures enabled"
            textSize = 15f
            setTextColor(Color.WHITE)
            isChecked = prefs.getBoolean("gestures_enabled", true)
        }
        customCard.addView(gestureSwitch, params(12))
        gestureSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("gestures_enabled", checked).apply()
            updateGestureControlsEnabled()
        }
        customCard.addView(label("${ActionCatalog.actions.size} available actions", 13f, muted, true, 14))
        for (i in gestureKeys.indices) {
            customCard.addView(label(gestureNames[i], 14f, Color.WHITE, true, 13))
            val spinner = Spinner(this).apply {
                adapter = ArrayAdapter(
                    this@MainActivity,
                    android.R.layout.simple_spinner_item,
                    ActionCatalog.actions
                ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
                val saved = prefs.getString(gestureKeys[i], defaultActionFor(i)) ?: defaultActionFor(i)
                setSelection(ActionCatalog.actions.indexOf(saved).coerceAtLeast(0))
                isEnabled = gestureSwitch.isChecked
                onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
                    override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                        prefs.edit().putString(gestureKeys[i], ActionCatalog.actions[position]).apply()
                    }
                }
            }
            customCard.addView(spinner, params(4))
        }
        root.addView(customCard, params(14))
        updateGestureControlsEnabled()

        val quick = card()
        quick.addView(label("QUICK ACTIONS", 12f, accent, true))
        addButton(quick, "Camera", false) { startActivity(Intent(this, CameraActivity::class.java)) }
        addButton(quick, "Play / pause", false) { runQuick("Play / pause media") }
        addButton(quick, "Volume up", false) { runQuick("Volume up") }
        addButton(quick, "Volume down", false) { runQuick("Volume down") }
        addButton(quick, "Next track", false) { runQuick("Next track") }
        addButton(quick, "Previous track", false) { runQuick("Previous track") }
        root.addView(quick, params(14))

        root.addView(label(
            "Compatibility: gesture detection works only for S Pen events Android delivers to this app while it is open. The S25 Ultra S Pen has no Bluetooth Air Actions hardware, so remote S24-style gestures cannot be added by an app.",
            12f, muted, false, 18
        ))

        val engine = GestureEngine { gesture ->
            runOnUiThread {
                lastGesture.text = "Last gesture: ${gesture.name.replace('_', ' ').lowercase()}"
                if (!prefs.getBoolean("gestures_enabled", true)) {
                    lastAction.text = "Last action: custom gestures are off"
                } else {
                    val actionName = prefs.getString(gestureKeyFor(gesture), "Do nothing") ?: "Do nothing"
                    val ok = ActionCatalog.execute(this, actionName)
                    lastAction.text = "Last action: " + if (ok) actionName else "${actionName} isn't available on this phone"
                }
            }
        }
        detector = StylusDetector(
            onStylusEvent = { message -> runOnUiThread { status.text = message } },
            gestureEngine = engine
        )
        setContentView(scroll)
    }

    private fun defaultActionFor(index: Int) = when (index) {
        0 -> "Play / pause media"
        1 -> "Open S Pen Revival camera"
        2 -> "Open Samsung Camera"
        3 -> "Previous track"
        4 -> "Next track"
        5 -> "Volume up"
        6 -> "Volume down"
        else -> "Do nothing"
    }

    private fun gestureKeyFor(gesture: PenGesture) = when (gesture) {
        PenGesture.BUTTON_TAP -> "button_tap"
        PenGesture.BUTTON_DOUBLE_TAP -> "button_double_tap"
        PenGesture.BUTTON_HOLD -> "button_hold"
        PenGesture.BUTTON_SWIPE_LEFT -> "button_swipe_left"
        PenGesture.BUTTON_SWIPE_RIGHT -> "button_swipe_right"
        PenGesture.BUTTON_SWIPE_UP -> "button_swipe_up"
        PenGesture.BUTTON_SWIPE_DOWN -> "button_swipe_down"
    }

    private fun runQuick(action: String) {
        val ok = ActionCatalog.execute(this, action)
        lastAction.text = if (ok) "Last action: $action" else "Last action: unavailable"
    }

    private fun updateGestureControlsEnabled() {
        val parent = gestureSwitch.parent as? LinearLayout ?: return
        val enabled = gestureSwitch.isChecked
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child is Spinner) child.isEnabled = enabled
        }
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        background = rounded(panel, 24)
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean, top: Int = 0) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            if (top > 0) setPadding(0, dp(top), 0, 0)
        }

    private fun addButton(parent: LinearLayout, title: String, primary: Boolean, action: () -> Unit) {
        val button = Button(this).apply {
            text = title
            isAllCaps = false
            textSize = 14f
            setTextColor(Color.WHITE)
            background = rounded(if (primary) Color.rgb(49, 105, 205) else Color.rgb(43, 57, 80), 16)
            setOnClickListener { action() }
        }
        parent.addView(button, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(50)
        ).apply { topMargin = dp(8) })
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun params(top: Int) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(top) }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        detector.handle(event)
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        detector.handle(event)
        return super.dispatchGenericMotionEvent(event)
    }
}
