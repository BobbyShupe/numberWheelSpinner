package com.example.numberChooser

import android.view.View
import android.os.Bundle
import android.text.InputType
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
import com.google.android.material.textview.MaterialTextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class MainActivity : AppCompatActivity() {

    private lateinit var wheel: SpinWheelView
    private lateinit var resultText: MaterialTextView
    private lateinit var previewText: MaterialTextView
    private lateinit var historyText: MaterialTextView
    private lateinit var segmentInput: TextInputEditText

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
                topMargin = 120
            }
        }

        resultText = MaterialTextView(this).apply {
            id = View.generateViewId()
            text = "Swipe to spin!"
            textSize = 32f
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                topToBottom = wheel.id
                topMargin = 30
                startToStart = LayoutParams.PARENT_ID
                endToEnd = LayoutParams.PARENT_ID
            }
        }

        previewText = MaterialTextView(this).apply {
            id = View.generateViewId()
            textSize = 36f
            setTextColor(0xFFDDDDDD.toInt())
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                topToBottom = resultText.id
                topMargin = 10
                startToStart = LayoutParams.PARENT_ID
                endToEnd = LayoutParams.PARENT_ID
            }
        }

        historyText = MaterialTextView(this).apply {
            id = View.generateViewId()
            text = "History: —"
            textSize = 18f
            setTextColor(0xFF777777.toInt())
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                topToBottom = previewText.id
                topMargin = 20
                startToStart = LayoutParams.PARENT_ID
                endToEnd = LayoutParams.PARENT_ID
            }
        }

        val inputLayout = TextInputLayout(this).apply {
            id = View.generateViewId()
            hint = "Number of Segments"
            layoutParams = LayoutParams(
                500,
                LayoutParams.WRAP_CONTENT
            ).apply {
                topToBottom = historyText.id
                topMargin = 40
                startToStart = LayoutParams.PARENT_ID
                endToEnd = LayoutParams.PARENT_ID
            }
        }

        segmentInput = TextInputEditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
        }

        inputLayout.addView(segmentInput)

        val applyButton = MaterialButton(this).apply {
            text = "Set Segments"
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                topToBottom = inputLayout.id
                topMargin = 20
                startToStart = LayoutParams.PARENT_ID
                endToEnd = LayoutParams.PARENT_ID
            }

            setOnClickListener {

                val value = segmentInput.text.toString().toIntOrNull()

                if (value != null && value > 1) {

                    wheel.setSegmentCount(value)

                    resultText.text = "Segments set to $value"
                    previewText.text = ""
                    updateHistoryDisplay()
                }
            }
        }

        wheel.setOnResultListener { number ->
            resultText.text = "Landed on: $number"
            updateHistoryDisplay()
        }

        wheel.setOnCurrentNumberListener { number ->
            previewText.text = "$number"
        }

        root.addView(wheel)
        root.addView(resultText)
        root.addView(previewText)
        root.addView(historyText)
        root.addView(inputLayout)
        root.addView(applyButton)

        setContentView(root)
    }

    private fun updateHistoryDisplay() {
        val historyStr = wheel.getHistory()
        historyText.text = if (historyStr.isEmpty())
            "History: —"
        else
            "History: $historyStr"
    }
}