package com.coding.camerausingcamerax

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View

class FocusCircleView(context: Context, attributeSet: AttributeSet) : View(context, attributeSet) {

    private val paint = Paint()

    var focusCircle: RectF? = null

    private var handler = Handler(Looper.getMainLooper())
    private var removeFocusRunnable = Runnable { }

    init {
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        focusCircle?.let { rect ->
            // Calculate the outer circle radius
            val outerRadius = rect.width() / 1.2f

            // Calculate the inner circle radius
            val innerRadius = outerRadius / 2

            // Draw the outer circle
            canvas.drawCircle(rect.centerX(), rect.centerY(), outerRadius, paint)

            // Draw the inner circle
            canvas.drawCircle(rect.centerX(), rect.centerY(), innerRadius, paint)

            scheduleFocusCircleRemoval()
        }
    }

    private fun scheduleFocusCircleRemoval() {
        handler.removeCallbacks(removeFocusRunnable)
        removeFocusRunnable = Runnable {
            focusCircle = null
            invalidate()
        }
        handler.postDelayed(removeFocusRunnable, 2000)
    }
}