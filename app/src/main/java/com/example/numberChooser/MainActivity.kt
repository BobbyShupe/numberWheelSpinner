package com.example.numberChooser

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.View
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

        val prefs =
            getSharedPreferences("wheelPrefs", Context.MODE_PRIVATE)

        val root = ConstraintLayout(this)

        root.layoutParams =
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

        root.setBackgroundColor(0xFF000000.toInt())

        wheel = SpinWheelView(this)

        wheel.id = View.generateViewId()

        wheel.layoutParams =
            LayoutParams(800, 800).apply {

                topToTop = LayoutParams.PARENT_ID
                startToStart = LayoutParams.PARENT_ID
                endToEnd = LayoutParams.PARENT_ID
                topMargin = 120
            }

        wheel.attachPreferences(prefs)

        resultText = MaterialTextView(this)

        resultText.id = View.generateViewId()

        resultText.text = "Swipe to spin!"

        resultText.textSize = 32f

        resultText.setTextColor(0xFFFFFFFF.toInt())

        resultText.layoutParams =
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                .apply {

                    topToBottom = wheel.id
                    topMargin = 30
                    startToStart = LayoutParams.PARENT_ID
                    endToEnd = LayoutParams.PARENT_ID
                }

        previewText = MaterialTextView(this)

        previewText.id = View.generateViewId()

        previewText.textSize = 36f

        previewText.setTextColor(0xFFDDDDDD.toInt())

        previewText.layoutParams =
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                .apply {

                    topToBottom = resultText.id
                    topMargin = 10
                    startToStart = LayoutParams.PARENT_ID
                    endToEnd = LayoutParams.PARENT_ID
                }

        historyText = MaterialTextView(this)

        historyText.id = View.generateViewId()

        historyText.text = "History: —"

        historyText.textSize = 18f

        historyText.setTextColor(0xFF777777.toInt())

        historyText.layoutParams =
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                .apply {

                    topToBottom = previewText.id
                    topMargin = 20
                    startToStart = LayoutParams.PARENT_ID
                    endToEnd = LayoutParams.PARENT_ID
                }

        val inputLayout = TextInputLayout(this)

        inputLayout.id = View.generateViewId()

        inputLayout.hint = "Number of Segments"

        inputLayout.layoutParams =
            LayoutParams(500, LayoutParams.WRAP_CONTENT).apply {

                topToBottom = historyText.id
                topMargin = 40
                startToStart = LayoutParams.PARENT_ID
                endToEnd = LayoutParams.PARENT_ID
            }

        segmentInput = TextInputEditText(this)

        segmentInput.inputType = InputType.TYPE_CLASS_NUMBER

        inputLayout.addView(segmentInput)

        val applyButton = MaterialButton(this)

        applyButton.id = View.generateViewId()

        applyButton.text = "Set Segments"

        applyButton.layoutParams =
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                .apply {

                    topToBottom = inputLayout.id
                    topMargin = 20
                    startToStart = LayoutParams.PARENT_ID
                    endToEnd = LayoutParams.PARENT_ID
                }

        applyButton.setOnClickListener {

            val value = segmentInput.text.toString().toIntOrNull()

            if (value != null && value > 1) {

                wheel.setSegmentCount(value)

                resultText.text = "Segments set to $value"

                previewText.text = ""

                updateHistoryDisplay()
            }
        }

        val resetButton = MaterialButton(this)

        resetButton.text = "Reset History"

        resetButton.layoutParams =
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                .apply {

                    topToBottom = applyButton.id
                    topMargin = 20
                    startToStart = LayoutParams.PARENT_ID
                    endToEnd = LayoutParams.PARENT_ID
                }

        resetButton.setOnClickListener {

            wheel.clearHistory()

            updateHistoryDisplay()

            resultText.text = "History Cleared"
        }

        wheel.setOnResultListener {

            resultText.text = "Landed on: $it"

            updateHistoryDisplay()
        }

        wheel.setOnCurrentNumberListener {

            previewText.text = "$it"
        }

        root.addView(wheel)
        root.addView(resultText)
        root.addView(previewText)
        root.addView(historyText)
        root.addView(inputLayout)
        root.addView(applyButton)
        root.addView(resetButton)

        setContentView(root)

        updateHistoryDisplay()
    }

    private fun updateHistoryDisplay() {

        val historyStr = wheel.getHistory()

        historyText.text =
            if (historyStr.isEmpty()) "History: —"
            else "History: $historyStr"
    }
}