package com.kyuusanq3.mixauto.data.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface

object PoiIconFactory {

    private const val PIN_W_DP = 48f
    private const val PIN_H_DP = 64f
    private const val LABEL_TEXT_SP = 18f

    private data class CategoryIconSpec(
        val iconId: String,
        val color: Int,
        val label: String,
    )

    private val CATEGORY_SPECS = listOf(
        CategoryIconSpec("poi_icon_food", 0xFFFF8C00.toInt(), "F"),
        CategoryIconSpec("poi_icon_fuel", 0xFFFFD700.toInt(), "G"),
        CategoryIconSpec("poi_icon_health", 0xFFFF4444.toInt(), "H"),
        CategoryIconSpec("poi_icon_accommodation", 0xFF9C27B0.toInt(), "A"),
        CategoryIconSpec("poi_icon_finance", 0xFF4CAF50.toInt(), "$"),
        CategoryIconSpec("poi_icon_shopping", 0xFFE91E63.toInt(), "S"),
        CategoryIconSpec("poi_icon_recreation", 0xFF8BC34A.toInt(), "R"),
        CategoryIconSpec("poi_icon_default", 0xFF00E5FF.toInt(), "•"),
    )

    fun createAllIcons(density: Float): Map<String, Bitmap> {
        return CATEGORY_SPECS.associate { spec ->
            spec.iconId to drawPinBitmap(
                color = spec.color,
                label = spec.label,
                density = density,
            )
        }
    }

    private fun drawPinBitmap(color: Int, label: String, density: Float): Bitmap {
        val width = (PIN_W_DP * density).toInt().coerceAtLeast(1)
        val height = (PIN_H_DP * density).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val centerX = width / 2f
        val bodyTop = height * 0.05f
        val bodyBottom = height * 0.62f
        val bodyRadius = (bodyBottom - bodyTop) / 2f
        val bodyCenterY = bodyTop + bodyRadius
        val tipY = height * 0.98f

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.color = Color.BLACK
            strokeWidth = (1.5f * density).coerceAtLeast(1f)
        }

        canvas.drawCircle(centerX, bodyCenterY, bodyRadius, fillPaint)
        canvas.drawCircle(centerX, bodyCenterY, bodyRadius, strokePaint)

        val tailPath = Path().apply {
            moveTo(centerX - bodyRadius * 0.42f, bodyBottom - bodyRadius * 0.05f)
            lineTo(centerX + bodyRadius * 0.42f, bodyBottom - bodyRadius * 0.05f)
            lineTo(centerX, tipY)
            close()
        }
        canvas.drawPath(tailPath, fillPaint)
        canvas.drawPath(tailPath, strokePaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = LABEL_TEXT_SP * density
        }
        val textY = bodyCenterY - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(label, centerX, textY, textPaint)

        return bitmap
    }
}
