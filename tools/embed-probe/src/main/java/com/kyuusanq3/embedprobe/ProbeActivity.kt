package com.kyuusanq3.embedprobe

import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Full-screen probe for the headunit Map Source virtual-display path on AOSP emulators
 * that lack Google Maps. Shows "PROBE" and the last touch coordinates.
 */
class ProbeActivity : AppCompatActivity() {
    private lateinit var label: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        label = TextView(this).apply {
            text = "PROBE\n(touch me)"
            textSize = 28f
            setTextColor(Color.BLACK)
            setPadding(48, 48, 48, 48)
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#00E5FF"))
            addView(
                label,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    android.view.Gravity.CENTER,
                ),
            )
        }
        setContentView(root)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        label.text = "PROBE\nx=${event.x.toInt()} y=${event.y.toInt()}\naction=${event.actionMasked}"
        return true
    }
}
