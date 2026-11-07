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
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var lastGesture: TextView
    private lateinit var lastAction: TextView
    private lateinit var detector: StylusDetector
    private lateinit var commands: CommandManager

    private val page = Color.rgb(10, 14, 24)
    private val panel = Color.rgb(23, 30, 45)
    private val accent = Color.rgb(91, 151, 255)
    private val muted = Color.rgb(166, 180, 202)

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
        root.addView(label("Air Command-inspired controls", 14f, muted, false, 5))

        val setupCard = card()
        setupCard.addView(label("USE IT OVER OTHER APPS", 13f, accent, true))
        setupCard.addView(label(
            "No split screen. Turn on the two Android permissions below, then a floating ✦ button can stay above your apps.",
            14f, Color.WHITE, false, 10
        ))
        addSetupButton(setupCard, "1 · Allow floating menu") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
        }
        addSetupButton(setupCard, "2 · Enable S Pen Revival controls") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        setupCard.addView(label(
            "The floating menu provides Back, Home, Recents, volume, media and camera shortcuts. It does not read your screen or intercept normal touch/pen input.",
            12f, muted, false, 8
        ))
        root.addView(setupCard, params(top = 20))

        val statusCard = card()
        status = label("S Pen input is detected while this app is open.", 15f, Color.WHITE, true)
        lastGesture = label("Last input: none", 14f, muted, false, 12)
        lastAction = label("Last action: none", 14f, muted, false, 6)
        statusCard.addView(status)
        statusCard.addView(lastGesture)
        statusCard.addView(lastAction)
        root.addView(statusCard, params(top = 14))

        val gestureCard = card()
        gestureCard.addView(label("IN-APP GESTURES", 13f, accent, true))
        gestureCard.addView(label("Tip tap → Camera · Button tap → Play / pause", 14f, Color.WHITE, false, 10))
        gestureCard.addView(label("Swipe left / right → Previous / next track", 14f, Color.WHITE, false, 5))
        gestureCard.addView(label("Swipe up / down → Volume up / down", 14f, Color.WHITE, false, 5))
        root.addView(gestureCard, params(top = 14))

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
            "Android limitation: a regular app cannot safely observe every S Pen swipe in every other app without taking over input. This build keeps normal pen and finger input working, and uses a floating menu for system-wide shortcuts instead.",
            12f, muted, false, 18
        ))

        val engine = GestureEngine { gesture ->
            runOnUiThread {
                lastGesture.text = "Last input: ${gesture.name.replace('_', ' ')}"
                val result = when (gesture) {
                    PenGesture.TAP -> commands.launchCamera().let { "Open camera" to it }
                    PenGesture.BUTTON_TAP -> { commands.mediaPlayPause(); "Play / pause" to true }
                    PenGesture.SWIPE_LEFT, PenGesture.BUTTON_SWIPE_LEFT -> { commands.mediaPrevious(); "Previous track" to true }
                    PenGesture.SWIPE_RIGHT, PenGesture.BUTTON_SWIPE_RIGHT -> { commands.mediaNext(); "Next track" to true }
                    PenGesture.SWIPE_UP, PenGesture.BUTTON_SWIPE_UP -> { commands.volumeUp(); "Volume up" to true }
                    PenGesture.SWIPE_DOWN, PenGesture.BUTTON_SWIPE_DOWN -> { commands.volumeDown(); "Volume down" to true }
                }
                lastAction.text = "Last action: " + if (result.second) result.first else "Not available"
            }
        }
        detector = StylusDetector(
            onStylusEvent = { message -> runOnUiThread { status.text = message } },
            gestureEngine = engine
        )
        setContentView(scroll)
    }

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