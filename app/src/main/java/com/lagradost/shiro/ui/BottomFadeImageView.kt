package com.lagradost.shiro.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

/**
 * ImageView whose content alpha-fades to transparent toward the bottom edge, so hero/banner
 * art dissolves into whatever is drawn behind it (the app wallpaper) instead of ending in a
 * hard cut or a flat-color gradient band like a plain overlay produces.
 */
class BottomFadeImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    /** Fraction of the height where the fade starts (0 = top, 1 = bottom). */
    var fadeStartFraction = 0.45f
        set(value) {
            field = value.coerceIn(0f, 1f)
            updateShader()
        }

    private val fadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }

    private fun updateShader() {
        if (height <= 0) return
        fadePaint.shader = LinearGradient(
            0f, height * fadeStartFraction, 0f, height.toFloat(),
            Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP
        )
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateShader()
    }

    override fun draw(canvas: Canvas) {
        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        super.draw(canvas)
        if (fadePaint.shader != null) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fadePaint)
        }
        canvas.restoreToCount(layer)
    }
}
