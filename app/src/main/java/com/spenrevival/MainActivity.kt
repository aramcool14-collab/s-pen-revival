package com.spenrevival

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
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
    private lateinit var commands: CommandManager
    private lateinit var gestureSwitch: Switch

    private val page = Color.rgb(10, 14, 24)
    private val panel = Color.rgb(23, 30, 45)
    private val accent = Color.rgb(91, 151, 255)
    private val muted = Color.rgb(166, 180, 202)
    private val prefs by lazy { getSharedPreferences("spen_settings", MODE_PRIVATE) }
    private val gestureOptions = listOf(
        "Do nothing", "Open camera", "Play / pause", "Next track",
        "Previous track", "Volume up", "Volume down"
    )
    private val gestureKeys = listOf(
        "button_tap", "button_swipe_left", "button_swipe_right",
        "button_swipe_up", "button_swipe_down"
    )
    private val gestureNames = listOf(
        "Side button + tap", "Side button + swipe left",
        "Side button + swipe right", "Side button + swipe up",
        "Side button + swipe down"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = page
        window.navigationBarColor = page
        commands = CommandManager(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 24, 20, 24)
            setBackgroundColor(page)
        }
        val scroll = ScrollView(this).apply { addView(root) }

        root.addView(label("S PEN REVIVAL", 13f, accent, true))
        root.addView(label("Your pen. Your shortcuts.", 27f, Color.WHITE, true, 8))
        root.addView(label("A personal Air Command experience", 14f, muted, false, 5))

        val setupCard = card()
        setupCard.addView(label("FLOATING AIR COMMAND", 13f, accent, true))
        setupCard.addView(label(
            "No split screen or dock. Allow the floating menu to appear above your other apps.",
            14f, Color.WHITE, false, 10
        ))
        addSetupButton(setupCard, "Allow floating menu") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
        }
        addSetupButton(setupCard, "Enable S Pen Revival service") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        setupCard.addView(label(
            "The floating menu currently provides system shortcuts. It does not read screen content or take over normal pen input.",
            12f, muted, false, 8
        ))
        root.addView(setupCard, params(top = 20))

        val statusCard = card()
        status = label("S Pen input is detected while this app is open.", 15f, Color.WHITE, true)
        lastGesture = label("Last gesture: none", 14f, muted, false, 12)
        lastAction = label("Last action: none", 14f, muted, false, 6)
        statusCard.addView(status)
        statusCard.addView(lastGesture)
        statusCard.addView(lastAction)
        root.addView(statusCard, params(top = 14))

        val customCard = card()
        customCard.addView(label("CUSTOM PEN GESTURES", 13f, accent, true))
        customCard.addView(label(
            "Turn this on to run your own shortcuts while holding the S Pen side button. Let go of the button and the pen works normally.",
            14f, Color.WHITE, false, 9
        ))
        gestureSwitch = Switch(this).apply {
            text = "Enable custom gestures"
            textSize = 15f
            setTextColor(Color.WHITE)
            isChecked = prefs.getBoolean("gestures_enabled", true)
        }
        customCard.addView(gestureSwitch, params(top = 12))
        gestureSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("gestures_enabled", checked).apply()
            updateGestureControlsEnabled()
        }
        customCard.addView(label("Choose what each button gesture does:", 13f, muted, false, 14))

        for (i in gestureKeys.indices) {
            customCard.addView(label(gestureNames[i], 14f, Color.WHITE, true, 12))
            val spinner = Spinner(this).apply {
                adapter = ArrayAdapter(
                    this@MainActivity,
                    android.R.layout.simple_spinner_item,
                    gestureOptions
                ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
                val saved = prefs.getString(gestureKeys[i], defaultActionFor(i)) ?: defaultActionFor(i)
                setSelection(gestureOptions.indexOf(saved).coerceAtLeast(0))
                isEnabled = gestureSwitch.isChecked
                onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
                    override fun onItemSelected(
                        parent: android.widget.AdapterView<*>?, view: android.view.View?,
                        position: Int, id: Long
                    ) {
                        prefs.edit().putString(gestureKeys[i], gestureOptions[position]).apply()
                    }
                }
            }
            customCard.addView(spinner, params(top = 3))
        }
        root.addView(customCard, params(top = 14))
        updateGestureControlsEnabled()

        val controls = card()
        controls.addView(label("QUICK CONTROLS", 13f, accent, true))
        addActionButton(controls, "Open camera", "Camera opened") { commands.launchCamera() }
        addActionButton(controls, "Volume up", "Volume up") { commands.volumeUp(); true }
        addActionButton(controls, "Volume down", "Volume down") { commands.volumeDown(); true }
        addActionButton(controls, "Play / pause", "Media play / pause") { commands.mediaPlayPause(); true }
        addActionButton(controls, "Next track", "Next track") { commands.mediaNext(); true }
        addActionButton(controls, "Previous track", "Previous track") { commands.mediaPrevious(); true }
        root.addView(controls, params(top = 14))

        root.addView(label(
            "Compatibility note: custom pen gestures are currently detected while this app is open. The floating menu works above other apps, but Android does not automatically deliver every S Pen movement to a normal app in the background.",
            12f, muted, false, 18
        ))

        val engine = GestureEngine { gesture ->
            runOnUiThread {
                lastGesture.text = "Last gesture: ${gesture.name.replace('_', ' ')}"
                if (!prefs.getBoolean("gestures_enabled", true)) {
                    lastAction.text = "Last action: custom gestures are off"
                } else {
                    val actionName = prefs.getString(gestureKeyFor(gesture), "Do nothing") ?: "Do nothing"
                    val result = runGestureAction(actionName)
                    lastAction.text = "Last action: " + if (result.second) result.first else "Not available"
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
        0 -> "Play / pause"
        1 -> "Previous track"
        2 -> "Next track"
        3 -> "Volume up"
        4 -> "Volume down"
        else -> "Do nothing"
    }

    private fun gestureKeyFor(gesture: PenGesture) = when (gesture) {
        PenGesture.BUTTON_TAP -> "button_tap"
        PenGesture.BUTTON_SWIPE_LEFT -> "button_swipe_left"
        PenGesture.BUTTON_SWIPE_RIGHT -> "button_swipe_right"
        PenGesture.BUTTON_SWIPE_UP -> "button_swipe_up"
        PenGesture.BUTTON_SWIPE_DOWN -> "button_swipe_down"
        else -> "button_tap"
    }

    private fun runGestureAction(action: String): Pair<String, Boolean> = when (action) {
        "Open camera" -> "Open camera" to commands.launchCamera()
        "Play / pause" -> { commands.mediaPlayPause(); "Play / pause" to true }
        "Next track" -> { commands.mediaNext(); "Next track" to true }
        "Previous track" -> { commands.mediaPrevious(); "Previous track" to true }
        "Volume up" -> { commands.volumeUp(); "Volume up" to true }
        "Volume down" -> { commands.volumeDown(); "Volume down" to true }
        else -> "Do nothing" to true
    }

    private fun updateGestureControlsEnabled() {
        val enabled = gestureSwitch.isChecked
        for (i in 0 until (gestureSwitch.parent as? LinearLayout)?.childCount.orZero()) {
            val child = (gestureSwitch.parent as LinearLayout).getChildAt(i)
            if (child is Spinner) child.isEnabled = enabled
        }
    }

    private fun Int?.orZero() = this ?: 0

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(18, 18, 18, 18)
        background = rounded(panel, 22)
    }

    private fun label(textValue: String, size: Float, color: Int, bold: Boolean, top: Int = 0) =
        TextView(this).apply {
            text = textValue
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            if (top > 0) setPadding(0, top, 0, 0)
        }

    private fun addSetupButton(parent: LinearLayout, text: String, action: () -> Unit) {
        val button = Button(this).apply {
            this.text = text
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(39, 75, 125), 15)
            setOnClickListener { action() }
        }
        parent.addView(button, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 8 })
    }

    private fun addActionButton(parent: LinearLayout, text: String, actionName: String, action: () -> Boolean) {
        val button = Button(this).apply {
            this.text = text
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(39, 57, 84), 15)
            setOnClickListener {
                val ok = action()
                lastAction.text = "Last action: " + if (ok) actionName else "Not available"
            }
        }
        parent.addView(button, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 7 })
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius * resources.displayMetrics.density
    }

    private fun params(top: Int) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = top }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        detector.handle(event)
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        detector.handle(event)
        return super.dispatchGenericMotionEvent(event)
    }
}
