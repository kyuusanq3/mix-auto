package com.kyuusanq3.mixauto.data.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

object PoiIconFactory {

    private const val ICON_SIZE_DP = 24f

    private data class CategoryIconSpec(
        val iconId: String,
        val color: Int,
        val glyph: Glyph,
    )

    private enum class Glyph {
        CUP,
        PUMP,
        CROSS,
        BED,
        BANK,
        BAG,
        TREE,
        DOT,
        STAR,
        PLUS,
        ELLIPSIS,
    }

    private val CATEGORY_SPECS = listOf(
        CategoryIconSpec("poi_icon_food", 0xFFFF8C00.toInt(), Glyph.CUP),
        CategoryIconSpec("poi_icon_fuel", 0xFFFFD700.toInt(), Glyph.PUMP),
        CategoryIconSpec("poi_icon_health", 0xFFFF4444.toInt(), Glyph.CROSS),
        CategoryIconSpec("poi_icon_accommodation", 0xFF9C27B0.toInt(), Glyph.BED),
        CategoryIconSpec("poi_icon_finance", 0xFF4CAF50.toInt(), Glyph.BANK),
        CategoryIconSpec("poi_icon_shopping", 0xFFE91E63.toInt(), Glyph.BAG),
        CategoryIconSpec("poi_icon_recreation", 0xFF8BC34A.toInt(), Glyph.TREE),
        CategoryIconSpec("poi_icon_default", 0xFF00E5FF.toInt(), Glyph.DOT),
    )

    private const val STARRED_COLOR = 0xFFFFD700.toInt()

    const val CUSTOM_PIN_PENDING_ICON_ID = "poi_icon_custom_pin_pending"
    const val CUSTOM_PIN_ICON_ID = "poi_icon_custom_pin"

    private const val CUSTOM_PIN_PENDING_COLOR = 0xFF888888.toInt()
    private const val CUSTOM_PIN_COLOR = 0xFF00E5FF.toInt()

    fun spriteNameForCategory(category: String): String = when (category) {
        "food" -> "cafe"
        "fuel" -> "fuel"
        "health" -> "pharmacy"
        "accommodation" -> "lodging"
        "finance" -> "bank"
        "shopping" -> "shop"
        "recreation" -> "park"
        else -> "circle-stroked"
    }

    fun createAllIcons(context: Context): Map<String, Bitmap> =
        createAllIcons(context.resources.displayMetrics.density)

    fun createAllIcons(density: Float): Map<String, Bitmap> {
        val icons = mutableMapOf<String, Bitmap>()
        CATEGORY_SPECS.forEach { spec ->
            icons[spec.iconId] = drawCircleIcon(density, spec.color, spec.glyph)
            icons[starredIconId(spec.iconId)] = drawCircleIcon(density, STARRED_COLOR, Glyph.STAR)
        }
        icons[CUSTOM_PIN_PENDING_ICON_ID] = drawCircleIcon(
            density,
            CUSTOM_PIN_PENDING_COLOR,
            Glyph.ELLIPSIS,
        )
        icons[CUSTOM_PIN_ICON_ID] = drawCircleIcon(density, CUSTOM_PIN_COLOR, Glyph.PLUS)
        return icons
    }

    fun starredIconId(baseIconId: String): String = "poi_icon_starred_${baseIconId.removePrefix("poi_icon_")}"

    private fun drawCircleIcon(density: Float, color: Int, glyph: Glyph): Bitmap {
        val size = (ICON_SIZE_DP * density).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f
        val radius = size * 0.42f

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.color = Color.argb(100, 0, 0, 0)
            strokeWidth = (0.5f * density).coerceAtLeast(0.5f)
        }
        canvas.drawCircle(center, center, radius, fillPaint)
        canvas.drawCircle(center, center, radius, strokePaint)

        val strokeGlyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.color = Color.WHITE
            strokeWidth = (1.2f * density).coerceAtLeast(1f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val fillGlyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = Color.WHITE
        }
        drawGlyph(canvas, center, center, radius * 0.55f, glyph, strokeGlyphPaint, fillGlyphPaint)
        return bitmap
    }

    private fun drawGlyph(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        r: Float,
        glyph: Glyph,
        stroke: Paint,
        fill: Paint,
    ) {
        when (glyph) {
            Glyph.CUP -> {
                val cupPath = Path().apply {
                    moveTo(cx - r * 0.55f, cy - r * 0.35f)
                    lineTo(cx - r * 0.45f, cy + r * 0.55f)
                    lineTo(cx + r * 0.45f, cy + r * 0.55f)
                    lineTo(cx + r * 0.55f, cy - r * 0.35f)
                    close()
                }
                canvas.drawPath(cupPath, stroke)
                canvas.drawLine(cx - r * 0.55f, cy - r * 0.35f, cx + r * 0.55f, cy - r * 0.35f, stroke)
                canvas.drawLine(cx + r * 0.55f, cy - r * 0.1f, cx + r * 0.85f, cy - r * 0.05f, stroke)
                canvas.drawLine(cx + r * 0.85f, cy - r * 0.05f, cx + r * 0.85f, cy + r * 0.25f, stroke)
            }
            Glyph.PUMP -> {
                canvas.drawRect(cx - r * 0.15f, cy - r * 0.7f, cx + r * 0.15f, cy + r * 0.5f, fill)
                canvas.drawRect(cx + r * 0.15f, cy - r * 0.35f, cx + r * 0.65f, cy - r * 0.15f, fill)
                canvas.drawRect(cx - r * 0.55f, cy + r * 0.5f, cx + r * 0.55f, cy + r * 0.7f, fill)
            }
            Glyph.CROSS -> {
                canvas.drawLine(cx, cy - r * 0.75f, cx, cy + r * 0.75f, stroke)
                canvas.drawLine(cx - r * 0.75f, cy, cx + r * 0.75f, cy, stroke)
            }
            Glyph.BED -> {
                canvas.drawRect(cx - r * 0.75f, cy - r * 0.15f, cx + r * 0.75f, cy + r * 0.55f, fill)
                canvas.drawCircle(cx - r * 0.45f, cy - r * 0.35f, r * 0.22f, fill)
                canvas.drawRect(cx - r * 0.75f, cy + r * 0.55f, cx + r * 0.75f, cy + r * 0.7f, fill)
            }
            Glyph.BANK -> {
                val colW = r * 0.28f
                canvas.drawRect(cx - r * 0.8f, cy + r * 0.55f, cx + r * 0.8f, cy + r * 0.7f, fill)
                canvas.drawRect(cx - r * 0.65f, cy - r * 0.1f, cx - r * 0.65f + colW, cy + r * 0.55f, fill)
                canvas.drawRect(cx - colW / 2f, cy - r * 0.35f, cx + colW / 2f, cy + r * 0.55f, fill)
                canvas.drawRect(cx + r * 0.65f - colW, cy - r * 0.1f, cx + r * 0.65f, cy + r * 0.55f, fill)
                val roof = Path().apply {
                    moveTo(cx - r * 0.85f, cy - r * 0.1f)
                    lineTo(cx, cy - r * 0.75f)
                    lineTo(cx + r * 0.85f, cy - r * 0.1f)
                    close()
                }
                canvas.drawPath(roof, fill)
            }
            Glyph.BAG -> {
                val bagPath = Path().apply {
                    moveTo(cx - r * 0.55f, cy - r * 0.1f)
                    lineTo(cx - r * 0.45f, cy + r * 0.7f)
                    lineTo(cx + r * 0.45f, cy + r * 0.7f)
                    lineTo(cx + r * 0.55f, cy - r * 0.1f)
                    close()
                }
                canvas.drawPath(bagPath, stroke)
                canvas.drawLine(cx - r * 0.25f, cy - r * 0.1f, cx - r * 0.2f, cy - r * 0.55f, stroke)
                canvas.drawLine(cx + r * 0.25f, cy - r * 0.1f, cx + r * 0.2f, cy - r * 0.55f, stroke)
                canvas.drawLine(cx - r * 0.2f, cy - r * 0.55f, cx + r * 0.2f, cy - r * 0.55f, stroke)
            }
            Glyph.TREE -> {
                val trunk = RectF(cx - r * 0.12f, cy + r * 0.1f, cx + r * 0.12f, cy + r * 0.75f)
                canvas.drawRect(trunk, fill)
                canvas.drawCircle(cx, cy - r * 0.35f, r * 0.55f, fill)
            }
            Glyph.DOT -> canvas.drawCircle(cx, cy, r * 0.35f, fill)
            Glyph.STAR -> {
                val starPath = Path()
                for (i in 0 until 5) {
                    val outerAngle = Math.PI / 2 + i * 2 * Math.PI / 5
                    val innerAngle = outerAngle + Math.PI / 5
                    val ox = cx + r * 0.8f * kotlin.math.cos(outerAngle).toFloat()
                    val oy = cy - r * 0.8f * kotlin.math.sin(outerAngle).toFloat()
                    val ix = cx + r * 0.35f * kotlin.math.cos(innerAngle).toFloat()
                    val iy = cy - r * 0.35f * kotlin.math.sin(innerAngle).toFloat()
                    if (i == 0) starPath.moveTo(ox, oy) else starPath.lineTo(ox, oy)
                    starPath.lineTo(ix, iy)
                }
                starPath.close()
                canvas.drawPath(starPath, fill)
            }
            Glyph.PLUS -> {
                canvas.drawLine(cx, cy - r * 0.65f, cx, cy + r * 0.65f, stroke)
                canvas.drawLine(cx - r * 0.65f, cy, cx + r * 0.65f, cy, stroke)
            }
            Glyph.ELLIPSIS -> {
                val dotR = r * 0.18f
                canvas.drawCircle(cx - r * 0.4f, cy, dotR, fill)
                canvas.drawCircle(cx, cy, dotR, fill)
                canvas.drawCircle(cx + r * 0.4f, cy, dotR, fill)
            }
        }
    }
}
