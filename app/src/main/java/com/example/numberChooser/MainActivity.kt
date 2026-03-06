package com.example.numberChooser

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textview.MaterialTextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams

class MainActivity : AppCompatActivity() {

    private lateinit var wheel: SpinWheelView
    private lateinit var resultText: MaterialTextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = ConstraintLayout(this).apply {
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0xFF000000.toInt()) // pure black
        }

        wheel = SpinWheelView(this).apply {
            id = android.R.id.custom
            layoutParams = LayoutParams(800, 800).apply {
                topToTop = LayoutParams.PARENT_ID
                startToStart = LayoutParams.PARENT_ID
                endToEnd = LayoutParams.PARENT_ID
                topMargin = 140
            }
        }

        resultText = MaterialTextView(this).apply {
            text = "Swipe to spin"
            textSize = 28f
            setTextColor(0xFFFFFFFF.toInt()) // white text on black
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                topToBottom = wheel.id
                topMargin = 60
                startToStart = LayoutParams.PARENT_ID
                endToEnd = LayoutParams.PARENT_ID
            }
        }

        wheel.setOnResultListener { number ->
            resultText.text = "Landed on: $number"
        }

        root.addView(wheel)
        root.addView(resultText)

        setContentView(root)
    }
}