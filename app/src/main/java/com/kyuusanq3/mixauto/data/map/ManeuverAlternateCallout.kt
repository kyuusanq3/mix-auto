package com.kyuusanq3.mixauto.data.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng

/**
 * Speech-bubble bitmap for the in-nav grey-fork ETA callout. Text is baked into the icon so the
 * SymbolLayer stays icon-only (no #2788 streak under tilted nav). Tail is at the bottom center;
 * [Property.ICON_ANCHOR_BOTTOM] puts the tip on the fork.
 */
internal object ManeuverAlternateCallout {
    private const val FILL_COLOR = 0xF21C1B1F.toInt()
    private const val STROKE_COLOR = 0xFF00E5FF.toInt()
    private const val TEXT_COLOR = 0xFFE1E1E1.toInt()
    private const val TEXT_SP = 18f
    private const val PAD_H_DP = 14f
    private const val PAD_V_DP = 10f
    private const val TAIL_W_DP = 16f
    private const val TAIL_H_DP = 12f
    private const val CORNER_DP = 10f
    private const val STROKE_DP = 2f

    fun drawBitmap(density: Float, label: String): Bitmap {
        val d = density.coerceAtLeast(1f)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_COLOR
            textSize = TEXT_SP * d
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.LEFT
        }
        val textWidth = textPaint.measureText(label)
        val fontMetrics = textPaint.fontMetrics
        val textHeight = fontMetrics.descent - fontMetrics.ascent
        val padH = PAD_H_DP * d
        val padV = PAD_V_DP * d
        val tailW = TAIL_W_DP * d
        val tailH = TAIL_H_DP * d
        val corner = CORNER_DP * d
        val stroke = STROKE_DP * d
        val bubbleW = textWidth + padH * 2f
        val bubbleH = textHeight + padV * 2f
        val width = kotlin.math.ceil(bubbleW + stroke).toInt().coerceAtLeast(1)
        val height = kotlin.math.ceil(bubbleH + tailH + stroke).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val left = stroke / 2f
        val top = stroke / 2f
        val right = width - stroke / 2f
        val bubbleBottom = top + bubbleH
        val cx = width / 2f
        val path = Path().apply {
            moveTo(left + corner, top)
            lineTo(right - corner, top)
            quadTo(right, top, right, top + corner)
            lineTo(right, bubbleBottom - corner)
            quadTo(right, bubbleBottom, right - corner, bubbleBottom)
            lineTo(cx + tailW / 2f, bubbleBottom)
            lineTo(cx, bubbleBottom + tailH)
            lineTo(cx - tailW / 2f, bubbleBottom)
            lineTo(left + corner, bubbleBottom)
            quadTo(left, bubbleBottom, left, bubbleBottom - corner)
            lineTo(left, top + corner)
            quadTo(left, top, left + corner, top)
            close()
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = FILL_COLOR
            style = Paint.Style.FILL
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = STROKE_COLOR
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, strokePaint)
        val textX = left + padH
        val textY = top + padV - fontMetrics.ascent
        canvas.drawText(label, textX, textY, textPaint)
        return bitmap
    }

    fun pointFeatureJson(anchor: LatLng): String {
        return JSONObject().apply {
            put("type", "Feature")
            put(
                "geometry",
                JSONObject().apply {
                    put("type", "Point")
                    put(
                        "coordinates",
                        JSONArray().apply {
                            put(anchor.longitude)
                            put(anchor.latitude)
                        },
                    )
                },
            )
            put("properties", JSONObject())
        }.toString()
    }
}
