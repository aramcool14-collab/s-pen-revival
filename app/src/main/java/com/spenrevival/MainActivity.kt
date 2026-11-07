package com.spenrevival

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var lastGesture: TextView
    private lateinit var detector: StylusDetector
    private lateinit var commands: CommandManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        commands = CommandManager(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 36, 28, 24)
        }
        val title = TextView(this).apply {
            text = "S Pen Revival · 0.1.0"
            textSize = 24f
        }
        status = TextView(this).apply {
            text = "Ready. Touch the screen with your S Pen."
            textSize = 16f
            setPadding(0, 28, 0, 16)
        }
        lastGesture = TextView(this).apply {
            text = "Last gesture: none"
            textSize = 18f
            setPadding(0, 8, 0, 24)
        }
        val hint = TextView(this).apply {
            text = "Keep this app open while testing. Android only sends stylus events to the app window receiving the touch."
            textSize = 14f
            setPadding(0, 0, 0, 20)
        }

        val engine = GestureEngine { gesture ->
            runOnUiThread { lastGesture.text = "Last gesture: ${gesture.name.replace('_', ' ')}" }
        }
        detector = StylusDetector(
            onStylusEvent = { message -> runOnUiThread { status.text = message } },
            gestureEngine = engine
        )

        root.addView(title)
        root.addView(status)
        root.addView(lastGesture)
        root.addView(hint)
        addButton(root, "Open camera", commands::launchCamera)
        addButton(root, "Volume up", commands::volumeUp)
        addButton(root, "Volume down", commands::volumeDown)
        addButton(root, "Play / pause", commands::mediaPlayPause)
        addButton(root, "Next track", commands::mediaNext)
        addButton(root, "Previous track", commands::mediaPrevious)

        setContentView(root, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
    }

    private fun addButton(parent: LinearLayout, label: String, action: () -> Any?) {
        val button = Button(this).apply {
            text = label
            setOnClickListener {
                val result = action()
                if (result is Boolean && !result) status.text = "That action isn't available on this device."
            }
        }
        parent.addView(button, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        detector.handle(event)
        return super.dispatchTouchEvent(event)
    }
}
