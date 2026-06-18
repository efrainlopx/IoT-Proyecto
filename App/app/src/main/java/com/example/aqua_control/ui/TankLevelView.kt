package com.example.aqua_control.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min

class TankLevelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(7, 94, 99)
        style = Paint.Style.STROKE
        strokeWidth = 7f
    }
    private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(55, 255, 255, 255)
        style = Paint.Style.FILL
    }
    private val waterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(70, 7, 94, 99)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(11, 31, 37)
        textAlign = Paint.Align.CENTER
        textSize = 42f
        isFakeBoldText = true
    }

    private var levelPercent = 0f

    fun setLevelPercent(value: Float?) {
        levelPercent = value?.coerceIn(0f, 100f) ?: 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val padding = 24f
        val tank = RectF(
            padding,
            padding,
            width.toFloat() - padding,
            height.toFloat() - padding,
        )
        val radius = min(tank.width(), tank.height()) * 0.12f
        val fillHeight = tank.height() * (levelPercent / 100f)
        val waterTop = tank.bottom - fillHeight

        val tankPath = Path().apply {
            addRoundRect(tank, radius, radius, Path.Direction.CW)
        }

        canvas.drawRoundRect(tank, radius, radius, glassPaint)

        canvas.save()
        canvas.clipPath(tankPath)
        if (fillHeight > 0f) {
            waterPaint.shader = LinearGradient(
                0f,
                waterTop,
                0f,
                tank.bottom,
                Color.rgb(33, 198, 184),
                Color.rgb(5, 132, 153),
                Shader.TileMode.CLAMP,
            )
            val waveHeight = max(8f, tank.height() * 0.025f)
            val waterPath = Path().apply {
                moveTo(tank.left, waterTop + waveHeight)
                cubicTo(
                    tank.left + tank.width() * 0.20f,
                    waterTop - waveHeight,
                    tank.left + tank.width() * 0.35f,
                    waterTop + waveHeight * 2,
                    tank.left + tank.width() * 0.55f,
                    waterTop + waveHeight,
                )
                cubicTo(
                    tank.left + tank.width() * 0.75f,
                    waterTop,
                    tank.left + tank.width() * 0.82f,
                    waterTop - waveHeight,
                    tank.right,
                    waterTop + waveHeight,
                )
                lineTo(tank.right, tank.bottom)
                lineTo(tank.left, tank.bottom)
                close()
            }
            canvas.drawPath(waterPath, waterPaint)
            waterPaint.shader = null
        }
        canvas.restore()

        for (mark in listOf(25f, 50f, 75f)) {
            val y = tank.bottom - tank.height() * (mark / 100f)
            canvas.drawLine(tank.left + 18f, y, tank.right - 18f, y, linePaint)
        }

        canvas.drawRoundRect(tank, radius, radius, borderPaint)
        canvas.drawText("${levelPercent.toInt()}%", tank.centerX(), tank.centerY() + textPaint.textSize / 3f, textPaint)
    }
}
