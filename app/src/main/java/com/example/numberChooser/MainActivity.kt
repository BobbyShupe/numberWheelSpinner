package com.example.numberChooser

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textview.MaterialTextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams

class MainActivity : AppCompatActivity() {

    private lateinit var wheel: SpinWheelView
    private lateinit var resultText: MaterialTextView
    private lateinit var previewText: MaterialTextView
    private lateinit var historyText: MaterialTextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = ConstraintLayout(this).apply {
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0xFF000000.toInt())
        }

        wheel = SpinWheelView(this).apply {
            id = android.R.id.custom
            layoutParams = LayoutParams(800, 800).apply {
                topToTop = LayoutParams.PARENT_ID
                startToStart = LayoutParams.PARENT_ID
                endToEnd = LayoutParams.PARENT_ID
                topMargin = 140  // ← back closer to original position
            }
        }

        resultText = MaterialTextView(this).apply {
            text = "Swipe to spin!"
            textSize = 32f
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                topToBottom = wheel.id
                topMargin = 40
                startToStart = LayoutParams.PARENT_ID
                endToEnd = LayoutParams.PARENT_ID
            }
        }

        previewText = MaterialTextView(this).apply {
            text = ""
            textSize = 36f                // bigger number for visibility
            setTextColor(0xFFDDDDDD.toInt())
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                topToBottom = resultText.id
                topMargin = 16
                startToStart = LayoutParams.PARENT_ID
                endToEnd = LayoutParams.PARENT_ID
            }
        }

        historyText = MaterialTextView(this).apply {
            text = "History: —"
            textSize = 18f
            setTextColor(0xFF777777.toInt())
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                topToBottom = previewText.id
                topMargin = 80           // more space before history
                bottomToBottom = LayoutParams.PARENT_ID
                bottomMargin = 40
                startToStart = LayoutParams.PARENT_ID
                endToEnd = LayoutParams.PARENT_ID
            }
        }

        wheel.setOnResultListener { number ->
            resultText.text = "Landed on: $number"
            updateHistoryDisplay()
        }

        wheel.setOnCurrentNumberListener { number ->
            previewText.text = "$number"           // ← just the number
        }

        root.addView(wheel)
        root.addView(resultText)
        root.addView(previewText)
        root.addView(historyText)

        setContentView(root)
    }

    private fun updateHistoryDisplay() {
        val historyStr = wheel.getHistory()
        historyText.text = if (historyStr.isEmpty()) "History: —" else "History: $historyStr"
    }
}