package com.spenrevival

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import kotlin.math.abs

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var lastGesture: TextView
    private lateinit var lastAction: TextView
    private lateinit var detector: StylusDetector
    private lateinit var gestureSwitch: Switch
    private lateinit var gestureList: LinearLayout

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
    private val userGestureIds = mutableListOf<String>()
    private val customSpinners = mutableListOf<Spinner>()

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
        root.addView(label("Shortcuts, custom gestures and camera controls.", 14f, muted, false, 5))

        val cameraCard = card()
        cameraCard.addView(label("S PEN CAMERA", 12f, accent, true))
        cameraCard.addView(label("A dedicated camera with touch controls for zoom, exposure, flash and more.", 14f, Color.WHITE, false, 9))
        addButton(cameraCard, "Open S Pen Camera  ›", true) { startActivity(Intent(this, CameraActivity::class.java)) }
        root.addView(cameraCard, params(20))

        val setupCard = card()
        setupCard.addView(label("AIR COMMAND", 12f, accent, true))
        setupCard.addView(label("Keep a floating shortcut bubble above your apps. Gesture drawing is available in this app; Android does not provide ordinary apps with unrestricted pen-motion access over every other app.", 14f, Color.WHITE, false, 8))
        addButton(setupCard, "Allow floating menu", false) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        addButton(setupCard, "Accessibility service settings", false) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        root.addView(setupCard, params(14))

        val statusCard = card()
        status = label("S Pen input is detected while this app is open.", 14f, Color.WHITE, true)
        lastGesture = label("Last gesture: none", 13f, muted, false, 10)
        lastAction = label("Last action: none", 13f, muted, false, 5)
        statusCard.addView(status); statusCard.addView(lastGesture); statusCard.addView(lastAction)
        root.addView(statusCard, params(14))

        val customCard = card()
        customCard.addView(label("GESTURE STUDIO", 12f, accent, true))
        customCard.addView(label("Draw your own symbol with your finger or S Pen, give it a name, then choose its action. Create as many as you like.", 14f, Color.WHITE, false, 8))
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
        customCard.addView(label("${ActionCatalog.actions.size} actions available", 13f, muted, true, 14))
        for (i in gestureKeys.indices) {
            customCard.addView(label(gestureNames[i], 14f, Color.WHITE, true, 13))
            val spinner = actionSpinner(prefs.getString(gestureKeys[i], defaultActionFor(i)) ?: defaultActionFor(i), gestureKeys[i])
            customCard.addView(spinner, params(4))
        }

        customCard.addView(label("MY DRAWN GESTURES", 14f, Color.WHITE, true, 18))
        customCard.addView(label("Tap Add gesture, draw a shape, name it, and assign an action. The app compares the shape you draw with your saved pattern.", 12f, muted, false, 4))
        gestureList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        customCard.addView(gestureList, params(8))
        addButton(customCard, "+ Add my own gesture", true) { showGestureCreator() }
        root.addView(customCard, params(14))
        refreshCustomGestures()
        updateGestureControlsEnabled()

        val quick = card()
        quick.addView(label("QUICK ACTIONS", 12f, accent, true))
        listOf("Camera" to "Open S Pen Revival camera", "Play / pause" to "Play / pause media",
            "Volume up" to "Volume up", "Volume down" to "Volume down",
            "Next track" to "Next track", "Previous track" to "Previous track").forEach { (title, action) ->
            addButton(quick, title, false) { runQuick(action) }
        }
        root.addView(quick, params(14))
        root.addView(label(
            "Important: custom drawn gestures are recognized while S Pen Revival is open. The floating Air Command bubble can appear over other apps, but Android does not let this app secretly monitor pen movement across every app. The S25 Ultra S Pen also has no Bluetooth Air Actions hardware.",
            12f, muted, false, 18
        ))

        val engine = GestureEngine { gesture ->
            runOnUiThread {
                lastGesture.text = "Last gesture: ${gesture.name.replace('_', ' ').lowercase()}"
                if (!prefs.getBoolean("gestures_enabled", true)) lastAction.text = "Last action: custom gestures are off"
                else {
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

    private fun actionSpinner(saved: String, key: String): Spinner = Spinner(this).apply {
        adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, ActionCatalog.actions)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        setSelection(ActionCatalog.actions.indexOf(saved).coerceAtLeast(0))
        isEnabled = gestureSwitch.isChecked
        onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                prefs.edit().putString(key, ActionCatalog.actions[position]).apply()
            }
        }
    }

    private fun showGestureCreator() {
        val dialogRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(12))
            setBackgroundColor(page)
        }
        val nameInput = android.widget.EditText(this).apply {
            hint = "Gesture name (e.g. Star)"
            setTextColor(Color.WHITE); setHintTextColor(muted)
            setSingleLine(true)
        }
        val drawing = GestureDrawingView(this)
        val hint = label("Draw one continuous shape below. Try to make it recognizable.", 13f, muted, false, 8)
        val clear = Button(this).apply { text = "Clear drawing"; setOnClickListener { drawing.clearDrawing() } }
        val actionLabel = label("When I draw this gesture, do:", 14f, Color.WHITE, true, 10)
        val action = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, ActionCatalog.actions)
                .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        }
        dialogRoot.addView(nameInput)
        dialogRoot.addView(hint)
        dialogRoot.addView(drawing, LinearLayout.LayoutParams(-1, dp(220)).apply { topMargin = dp(8) })
        dialogRoot.addView(clear)
        dialogRoot.addView(actionLabel)
        dialogRoot.addView(action)
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Create a gesture")
            .setView(dialogRoot)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameInput.text.toString().trim()
                val points = drawing.points
                if (name.isBlank()) {
                    nameInput.error = "Give your gesture a name"
                    return@setOnClickListener
                }
                if (points.size < 8) {
                    android.widget.Toast.makeText(this, "Draw a bigger shape first", android.widget.Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val id = "custom_${System.currentTimeMillis()}"
                val encoded = GesturePattern.encode(points)
                val actionName = ActionCatalog.actions[action.selectedItemPosition]
                prefs.edit()
                    .putString("${id}_name", name)
                    .putString("${id}_pattern", encoded)
                    .putString("${id}_action", actionName)
                    .apply()
                refreshCustomGestures()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun refreshCustomGestures() {
        if (!::gestureList.isInitialized) return
        gestureList.removeAllViews()
        val ids = prefs.all.keys.filter { it.startsWith("custom_") && it.endsWith("_name") }
            .map { it.removeSuffix("_name") }.sorted()
        userGestureIds.clear()
        userGestureIds.addAll(ids)
        customSpinners.clear()
        for (id in ids) {
            val row = card()
            val name = prefs.getString("${id}_name", "My gesture") ?: "My gesture"
            row.addView(label(name, 15f, Color.WHITE, true))
            row.addView(label("Draw the same shape in this app to run its action.", 12f, muted, false, 4))
            val spinner = actionSpinner(prefs.getString("${id}_action", "Do nothing") ?: "Do nothing", "${id}_action")
            customSpinners.add(spinner)
            row.addView(spinner, params(6))
            addButton(row, "Delete gesture", false) {
                prefs.edit().remove("${id}_name").remove("${id}_pattern").remove("${id}_action").apply()
                refreshCustomGestures()
            }
            gestureList.addView(row, params(8))
        }
        if (ids.isEmpty()) gestureList.addView(label("No custom gestures yet.", 12f, muted, false))
    }

    private fun matchCustomGesture(points: List<GesturePoint>): Boolean {
        if (!prefs.getBoolean("gestures_enabled", true)) return false
        for (id in userGestureIds) {
            val template = GesturePattern.decode(prefs.getString("${id}_pattern", null))
            if (GesturePattern.matches(points, template)) {
                val name = prefs.getString("${id}_name", "Custom gesture") ?: "Custom gesture"
                val action = prefs.getString("${id}_action", "Do nothing") ?: "Do nothing"
                lastGesture.text = "Last gesture: $name"
                val ok = ActionCatalog.execute(this, action)
                lastAction.text = if (ok) "Last action: $action" else "$action isn't available on this phone"
                return true
            }
        }
        return false
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
        customSpinners.forEach { it.isEnabled = enabled }
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        background = rounded(panel, 24)
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean, top: Int = 0) =
        TextView(this).apply {
            text = value; textSize = size; setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            if (top > 0) setPadding(0, dp(top), 0, 0)
        }

    private fun addButton(parent: LinearLayout, title: String, primary: Boolean, action: () -> Unit) {
        val button = Button(this).apply {
            text = title; isAllCaps = false; textSize = 14f; setTextColor(Color.WHITE)
            background = rounded(if (primary) Color.rgb(49, 105, 205) else Color.rgb(43, 57, 80), 16)
            setOnClickListener { action() }
        }
        parent.addView(button, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(8) })
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }

    private fun params(top: Int) = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT)
        .apply { topMargin = dp(top) }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        detector.handle(event)
        if (event.actionMasked == MotionEvent.ACTION_DOWN && event.getToolType(0) != MotionEvent.TOOL_TYPE_STYLUS) {
            customStart(event)
        } else if (event.actionMasked == MotionEvent.ACTION_MOVE && customDrawing) {
            customMove(event)
        } else if (event.actionMasked == MotionEvent.ACTION_UP && customDrawing) {
            customEnd()
        }
        return super.dispatchTouchEvent(event)
    }

    private var customDrawing = false
    private val activePoints = mutableListOf<GesturePoint>()
    private var lastCustomAt = 0L
    private fun customStart(event: MotionEvent) {
        customDrawing = true
        activePoints.clear()
        activePoints.add(GesturePoint(event.x, event.y))
    }
    private fun customMove(event: MotionEvent) {
        val p = GesturePoint(event.x, event.y)
        val previous = activePoints.lastOrNull()
        if (previous == null || abs(previous.x - p.x) + abs(previous.y - p.y) > dp(5)) activePoints.add(p)
    }
    private fun customEnd() {
        customDrawing = false
        if (activePoints.size >= 8 && System.currentTimeMillis() - lastCustomAt > 900) {
            lastCustomAt = System.currentTimeMillis()
            matchCustomGesture(activePoints.toList())
        }
        activePoints.clear()
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        detector.handle(event)
        if (event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS) {
            when (event.actionMasked) {
                MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                    if (event.buttonState and (MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_STYLUS_SECONDARY) != 0) {
                        if (!customDrawing) {
                            customDrawing = true
                            activePoints.clear()
                        }
                        customMove(event)
                    }
                }
                MotionEvent.ACTION_HOVER_EXIT -> if (customDrawing) customEnd()
            }
        }
        return super.dispatchGenericMotionEvent(event)
    }
}

private class GestureDrawingView(context: android.content.Context) : View(context) {
    val points = mutableListOf<GesturePoint>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(111, 164, 255); strokeWidth = 8f
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(55, 68, 90); strokeWidth = 1f
    }
    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        val step = 36f
        var x = 0f
        while (x < width) { canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint); x += step }
        var y = 0f
        while (y < height) { canvas.drawLine(0f, y, width.toFloat(), y, gridPaint); y += step }
        if (points.size > 1) {
            val path = Path()
            path.moveTo(points[0].x, points[0].y)
            points.drop(1).forEach { path.lineTo(it.x, it.y) }
            canvas.drawPath(path, paint)
        }
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { points.clear(); points.add(GesturePoint(event.x, event.y)); invalidate(); return true }
            MotionEvent.ACTION_MOVE -> { points.add(GesturePoint(event.x, event.y)); invalidate(); return true }
            MotionEvent.ACTION_UP -> { points.add(GesturePoint(event.x, event.y)); invalidate(); performClick(); return true }
        }
        return true
    }
    fun clearDrawing() { points.clear(); invalidate() }
    override fun performClick(): Boolean { super.performClick(); return true }
}
