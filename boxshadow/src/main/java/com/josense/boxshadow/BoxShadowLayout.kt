package com.josense.boxshadow

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import kotlin.math.ceil

/** Hosts one child at the top left, inside the padding, shadow insets and child margins. */
class BoxShadowLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {
    private val insets = Rect()
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val tint = Paint()
    private var mask: Bitmap? = null
    private var dirty = true

    private val boxPath = Path()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private var boxWidth = -1
    private var boxHeight = -1
    private var innerDirty = true
    private var innerMask: Bitmap? = null

    var boxFillColor: Int = Color.TRANSPARENT
        set(value) { field = value; invalidate() }

    private val outsideCorners = Path()
    private val strokePath = Path()
    private val innerPath = Path()
    private val innerBounds = RectF()
    private var innerCorners = Corners()
    private val strokeInset get() = ceil(boxStrokeWidth).toInt()
    private val cardWidth get() = width - insets.left - insets.right
    private val cardHeight get() = height - insets.top - insets.bottom

    var boxStrokeWidth: Float = 0f
        set(value) {
            require(value.isFinite() && value >= 0f)
            if (field == value) return
            field = value
            boxWidth = -1
            requestLayout()
            invalidate()
        }

    var boxStrokeColor: Int = Color.TRANSPARENT
        set(value) { field = value; invalidate() }

    var boxBackground: Drawable? = null
        set(value) {
            if (field === value) return
            field?.let {
                unscheduleDrawable(it)
                it.callback = null
            }
            field = value?.mutate()
            field?.let {
                it.callback = this
                if (it.isStateful) it.state = drawableState
            }
            invalidate()
        }

    override fun verifyDrawable(who: Drawable) = who === boxBackground || super.verifyDrawable(who)

    override fun invalidateDrawable(drawable: Drawable) {
        if (drawable === boxBackground) invalidate() else super.invalidateDrawable(drawable)
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        boxBackground?.let { if (it.isStateful) it.state = drawableState }
    }

    var innerShadow: InnerShadow? = null
        set(value) {
            if (value == field) return
            if (value == null || value.copy(color = field?.color ?: value.color) != field) innerDirty = true
            if (value == null) innerMask = null
            field = value
            invalidate()
        }

    var shadow = BoxShadow()
        set(value) {
            if (value == field) return
            if (value.copy(color = field.color) != field) dirty = true
            if (value.corners != field.corners) boxWidth = -1
            field = value
            updateInsets()
            invalidate()
        }

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.BoxShadowLayout, defStyleAttr, 0)
        try {
            val radius = a.getDimension(R.styleable.BoxShadowLayout_boxCornerRadius, 0f)
            shadow = BoxShadow(
                Corners(
                    a.getDimension(R.styleable.BoxShadowLayout_boxTopLeftRadius, radius),
                    a.getDimension(R.styleable.BoxShadowLayout_boxTopRightRadius, radius),
                    a.getDimension(R.styleable.BoxShadowLayout_boxBottomRightRadius, radius),
                    a.getDimension(R.styleable.BoxShadowLayout_boxBottomLeftRadius, radius)
                ),
                a.getDimension(R.styleable.BoxShadowLayout_shadowBlurRadius, 0f),
                a.getDimension(R.styleable.BoxShadowLayout_shadowSpread, 0f),
                a.getDimension(R.styleable.BoxShadowLayout_shadowOffsetX, 0f),
                a.getDimension(R.styleable.BoxShadowLayout_shadowOffsetY, 0f),
                a.getColor(R.styleable.BoxShadowLayout_shadowColor, shadow.color)
            )
            boxBackground = a.getDrawable(R.styleable.BoxShadowLayout_boxBackground)
            boxStrokeWidth = a.getDimension(R.styleable.BoxShadowLayout_boxStrokeWidth, 0f)
            boxStrokeColor = a.getColor(R.styleable.BoxShadowLayout_boxStrokeColor, Color.TRANSPARENT)
            boxFillColor = a.getColor(R.styleable.BoxShadowLayout_boxFillColor, Color.TRANSPARENT)
            if (a.hasValue(R.styleable.BoxShadowLayout_innerShadowColor)) {
                innerShadow = InnerShadow(
                    a.getDimension(R.styleable.BoxShadowLayout_innerShadowBlurRadius, 0f),
                    a.getDimension(R.styleable.BoxShadowLayout_innerShadowSpread, 0f),
                    a.getDimension(R.styleable.BoxShadowLayout_innerShadowOffsetX, 0f),
                    a.getDimension(R.styleable.BoxShadowLayout_innerShadowOffsetY, 0f),
                    a.getColor(R.styleable.BoxShadowLayout_innerShadowColor, 0)
                )
            }
        } finally { a.recycle() }
    }

    private fun updateInsets() {
        // Leave room for the blur kernel on both sides, including fractional offsets.
        val reach = shadow.spread + if (shadow.blur > 0) 2f * shadow.blur + 3f else 0f
        fun side(offset: Float) = ceil(reach + offset).toInt().coerceAtLeast(0)
        val next = Rect(side(-shadow.offsetX), side(-shadow.offsetY), side(shadow.offsetX), side(shadow.offsetY))
        if (next != insets) {
            insets.set(next)
            requestLayout()
        }
    }

    override fun addView(child: View, index: Int, params: LayoutParams) {
        require(childCount == 0) { "BoxShadowLayout accepts one direct child" }
        super.addView(child, index, params)
    }
    override fun generateDefaultLayoutParams() = MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    override fun generateLayoutParams(attrs: AttributeSet) = MarginLayoutParams(context, attrs)
    override fun generateLayoutParams(p: LayoutParams) = MarginLayoutParams(p)
    override fun checkLayoutParams(p: LayoutParams) = p is MarginLayoutParams

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val child = getChildAt(0)
        var w = paddingLeft + paddingRight
        var h = paddingTop + paddingBottom
        if (child != null && child.visibility != View.GONE) {
            val lp = child.layoutParams as MarginLayoutParams
            val x = insets.left + insets.right + 2 * strokeInset
            val y = insets.top + insets.bottom + 2 * strokeInset
            measureChildWithMargins(child, widthMeasureSpec, x, heightMeasureSpec, y)
            w += x + lp.leftMargin + lp.rightMargin + child.measuredWidth
            h += y + lp.topMargin + lp.bottomMargin + child.measuredHeight
        }
        setMeasuredDimension(resolveSize(maxOf(w, suggestedMinimumWidth), widthMeasureSpec),
            resolveSize(maxOf(h, suggestedMinimumHeight), heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val child = getChildAt(0) ?: return
        val lp = child.layoutParams as MarginLayoutParams
        val x = paddingLeft + insets.left + lp.leftMargin + strokeInset
        val y = paddingTop + insets.top + lp.topMargin + strokeInset
        if (changed) dirty = true
        child.layout(x, y, x + child.measuredWidth, y + child.measuredHeight)
    }

    override fun dispatchDraw(canvas: Canvas) {
        val child = getChildAt(0)
        if (child != null && child.visibility == View.VISIBLE && cardWidth > 0 && cardHeight > 0) {
            if (Color.alpha(shadow.color) > 0) {
                if (dirty) {
                    mask = makeMask(cardWidth, cardHeight)
                    dirty = false
                }
                tint.color = shadow.color
                mask?.let { canvas.drawBitmap(it, 0f, 0f, tint) }
            }
            if (boxBackground != null || Color.alpha(boxFillColor) > 0 || innerShadow != null || boxStrokeWidth > 0f) {
                drawSurface(canvas, cardWidth, cardHeight)
            }
        }
        super.dispatchDraw(canvas)
    }

    private fun drawSurface(canvas: Canvas, w: Int, h: Int) {
        if (w != boxWidth || h != boxHeight) {
            val bounds = RectF(0f, 0f, w.toFloat(), h.toFloat())
            boxPath.reset()
            boxPath.addRoundRect(bounds, shadow.corners.radii(bounds), Path.Direction.CW)
            outsideCorners.set(boxPath)
            outsideCorners.addRect(bounds, Path.Direction.CCW)
            innerBounds.set(bounds)
            innerBounds.inset(boxStrokeWidth, boxStrokeWidth)
            innerPath.reset()
            strokePath.set(boxPath)
            if (!innerBounds.isEmpty) {
                innerCorners = spreadCorners(shadow.corners.radii(bounds), -boxStrokeWidth)
                innerPath.addRoundRect(innerBounds, innerCorners.radii(innerBounds), Path.Direction.CCW)
                strokePath.addPath(innerPath)
            }
            boxWidth = w
            boxHeight = h
            innerDirty = true
        }
        val save = canvas.save()
        canvas.translate(insets.left.toFloat(), insets.top.toFloat())
        if (Color.alpha(boxFillColor) > 0) {
            fill.color = boxFillColor
            canvas.drawPath(boxPath, fill)
        }
        boxBackground?.let { drawable ->
            drawable.setBounds(0, 0, w, h)
            // Isolate the drawable so clearing the corners preserves the fill and outer shadow.
            @Suppress("DEPRECATION")
            val layer = canvas.saveLayer(0f, 0f, w.toFloat(), h.toFloat(), null, Canvas.ALL_SAVE_FLAG)
            drawable.draw(canvas)
            canvas.drawPath(outsideCorners, clearPaint)
            canvas.restoreToCount(layer)
        }
        innerShadow?.let { effect ->
            if (Color.alpha(effect.color) > 0) {
                if (innerDirty) {
                    innerMask = makeInnerMask(w, h, effect)
                    innerDirty = false
                }
                tint.color = effect.color
                innerMask?.let { canvas.drawBitmap(it, 0f, 0f, tint) }
            }
        }
        if (boxStrokeWidth > 0f && Color.alpha(boxStrokeColor) > 0) {
            fill.color = boxStrokeColor
            canvas.drawPath(strokePath, fill)
        }
        canvas.restoreToCount(save)
    }

    private fun makeInnerMask(w: Int, h: Int, effect: InnerShadow): Bitmap? {
        if (innerBounds.isEmpty) return null
        if (effect.blur == 0f && effect.spread <= 0f && effect.offsetX == 0f && effect.offsetY == 0f) return null
        val bitmap = innerMask?.takeIf { it.width == w && it.height == h }?.apply {
            eraseColor(Color.TRANSPARENT)
        } ?: Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
        bitmap.density = Bitmap.DENSITY_NONE
        val surface = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        surface.drawPath(innerPath, paint)

        // Alpha = box coverage * (1 - blurred hole coverage).
        val hole = RectF(innerBounds).apply {
            inset(effect.spread, effect.spread)
            offset(effect.offsetX, effect.offsetY)
        }
        if (!hole.isEmpty) {
            val corners = spreadCorners(innerCorners.radii(innerBounds), -effect.spread)
            val path = Path().apply { addRoundRect(hole, corners.radii(hole), Path.Direction.CW) }
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            if (effect.blur > 0) paint.maskFilter = BlurMaskFilter(effect.blur, BlurMaskFilter.Blur.NORMAL)
            surface.drawPath(path, paint)
        }
        return bitmap
    }

    private fun makeMask(w: Int, h: Int): Bitmap? {
        val bounds = RectF(insets.left.toFloat(), insets.top.toFloat(), (insets.left + w).toFloat(), (insets.top + h).toFloat())
        val cast = RectF(bounds).apply {
            inset(-shadow.spread, -shadow.spread)
            offset(shadow.offsetX, shadow.offsetY)
        }
        if (cast.isEmpty || (shadow.spread <= 0 && shadow.blur == 0f && shadow.offsetX == 0f && shadow.offsetY == 0f)) return null
        val original = shadow.corners.radii(bounds)
        val castCorners = spreadCorners(original, shadow.spread)
        val bitmapWidth = w + insets.left + insets.right
        val bitmapHeight = h + insets.top + insets.bottom
        val bitmap = mask?.takeIf { it.width == bitmapWidth && it.height == bitmapHeight }?.apply {
            eraseColor(Color.TRANSPARENT)
        } ?: Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ALPHA_8)
        bitmap.density = Bitmap.DENSITY_NONE
        val surface = Canvas(bitmap)
        val path = Path()
        path.addRoundRect(cast, castCorners.radii(cast), Path.Direction.CW)
        maskPaint.maskFilter = if (shadow.blur > 0) BlurMaskFilter(shadow.blur, BlurMaskFilter.Blur.NORMAL) else null
        surface.drawPath(path, maskPaint)
        path.reset()
        path.addRoundRect(bounds, original, Path.Direction.CW)
        surface.drawPath(path, clearPaint)
        return bitmap
    }
}
