package com.josense.boxshadow

import android.graphics.RectF
import kotlin.math.min

/** All lengths are in physical pixels. Corner names are physical, independent of RTL. */
data class Corners(
    val topLeft: Float = 0f, val topRight: Float = 0f,
    val bottomRight: Float = 0f, val bottomLeft: Float = 0f
) {
    constructor(all: Float) : this(all, all, all, all)
    init {
        require(listOf(topLeft, topRight, bottomRight, bottomLeft).all { it.isFinite() && it >= 0 })
    }

    fun radii(bounds: RectF): FloatArray {
        val values = floatArrayOf(topLeft, topRight, bottomRight, bottomLeft)
        var scale = 1f
        for (i in values.indices) {
            val sum = values[i] + values[(i + 1) % 4]
            val side = if (i % 2 == 0) bounds.width() else bounds.height()
            if (sum > side && sum > 0) scale = min(scale, side / sum)
        }
        return FloatArray(8) { values[it / 2] * scale }
    }
}

data class BoxShadow(
    val corners: Corners = Corners(), val blur: Float = 0f, val spread: Float = 0f,
    val offsetX: Float = 0f, val offsetY: Float = 0f, val color: Int = 0x40000000
) {
    init {
        require(blur.isFinite() && blur >= 0 && spread.isFinite() && offsetX.isFinite() && offsetY.isFinite())
    }
}

/** Shares the layout's corners. Positive spread shrinks the clear opening. */
data class InnerShadow(
    val blur: Float = 0f, val spread: Float = 0f,
    val offsetX: Float = 0f, val offsetY: Float = 0f, val color: Int = 0x40000000
) {
    init {
        require(blur.isFinite() && blur >= 0 && spread.isFinite() && offsetX.isFinite() && offsetY.isFinite())
    }
}

/** Adjust the normalized corner radii for a box expanded by spread. */
internal fun spreadCorners(original: FloatArray, spread: Float): Corners {
    fun expanded(i: Int): Float {
        val radius = original[i * 2]
        val ratio = if (spread > 0 && radius < spread) 1f - radius / spread else 0f
        return (radius + spread * (1f - ratio * ratio * ratio)).coerceAtLeast(0f)
    }
    return Corners(expanded(0), expanded(1), expanded(2), expanded(3))
}
