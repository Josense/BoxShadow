package com.josense.boxshadow.demo

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import com.josense.boxshadow.InnerShadow
import android.os.Bundle
import android.widget.*
import com.josense.boxshadow.BoxShadowLayout
import kotlin.math.roundToInt

class DemoActivity : Activity() {
    private lateinit var preview: BoxShadowLayout
    private lateinit var innerEnabled: CheckBox
    private lateinit var panel: LinearLayout
    private val sliders = linkedMapOf<String, SeekBar>()
    private val corners = listOf("左上", "右上", "右下", "左下")
    private var backgroundMode = 2
    private var startColor = 0xFFECF4FF.toInt()
    private var endColor = 0xFFCAD8F8.toInt()
    private val density get() = resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_demo)
        preview = findViewById(R.id.preview)
        panel = findViewById(R.id.controls)
        val initial = preview.shadow
        fun dp(px: Float) = (px / density).roundToInt()
        val radius = dp(initial.corners.topLeft)
        fun cornerSlider(name: String, start: Int) = slider(name, 0, 64, start) { value ->
            val px = value * density
            val c = preview.shadow.corners
            val next = when (name) {
                corners[0] -> c.copy(topLeft = px)
                corners[1] -> c.copy(topRight = px)
                corners[2] -> c.copy(bottomRight = px)
                else -> c.copy(bottomLeft = px)
            }
            preview.shadow = preview.shadow.copy(corners = next)
        }

        surfaceControls(savedInstanceState)
        section("轮廓")
        slider("全部圆角", 0, 64, radius) { value ->
            corners.forEach { sliders[it]?.progress = value }
        }
        corners.forEach { cornerSlider(it, radius) }
        section("内阴影")
        var inner = requireNotNull(preview.innerShadow)
        innerEnabled = CheckBox(this).apply {
            text = "显示内阴影"
            isChecked = savedInstanceState?.getBoolean("innerEnabled", true) ?: true
            setOnCheckedChangeListener { _, enabled -> preview.innerShadow = if (enabled) inner else null }
        }
        panel.addView(innerEnabled)
        if (!innerEnabled.isChecked) preview.innerShadow = null
        effectControls("内", inner) {
            inner = it
            if (innerEnabled.isChecked) preview.innerShadow = it
        }
        section("外阴影")
        effectControls("外", InnerShadow(initial.blur, initial.spread, initial.offsetX, initial.offsetY, initial.color)) {
            preview.shadow = preview.shadow.copy(blur = it.blur, spread = it.spread,
                offsetX = it.offsetX, offsetY = it.offsetY, color = it.color)
        }
        sliders.forEach { (name, bar) -> bar.progress = savedInstanceState?.getInt(name, bar.progress) ?: bar.progress }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("backgroundMode", backgroundMode)
        outState.putInt("startColor", startColor)
        outState.putInt("endColor", endColor)
        outState.putInt("strokeColor", preview.boxStrokeColor)
        outState.putBoolean("innerEnabled", innerEnabled.isChecked)
        sliders.forEach { (name, bar) -> outState.putInt(name, bar.progress) }
    }

    private fun surfaceControls(state: Bundle?) {
        section("卡片背景")
        backgroundMode = state?.getInt("backgroundMode", 2) ?: 2
        startColor = state?.getInt("startColor", startColor) ?: startColor
        endColor = state?.getInt("endColor", endColor) ?: endColor
        val modes = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        listOf("仅底色", "纯色", "渐变", "位图").forEachIndexed { index, name ->
            modes.addView(RadioButton(this).apply {
                id = index + 1
                isSaveEnabled = false
                text = name
            })
        }
        panel.addView(modes)
        modes.check(backgroundMode + 1)
        modes.setOnCheckedChangeListener { _, id ->
            backgroundMode = id - 1
            updateBackground()
        }
        colorField("纯色 / 渐变起点", startColor) { startColor = it; updateBackground() }
        colorField("渐变终点", endColor) { endColor = it; updateBackground() }
        updateBackground()

        section("边框")
        slider("边框宽度", 0, 30, (preview.boxStrokeWidth / density).roundToInt()) {
            preview.boxStrokeWidth = it * density
        }
        preview.boxStrokeColor = state?.getInt("strokeColor", preview.boxStrokeColor) ?: preview.boxStrokeColor
        colorField("边框颜色", preview.boxStrokeColor) { preview.boxStrokeColor = it }
    }

    private val sampleImage by lazy {
        val tile = Bitmap.createBitmap(
            intArrayOf(0xFFD2EBE7.toInt(), 0xFFAFD1D8.toInt(), 0xFFAFD1D8.toInt(), 0xFFD2EBE7.toInt()),
            2, 2, Bitmap.Config.ARGB_8888
        )
        val size = (24 * density).roundToInt()
        BitmapDrawable(resources, Bitmap.createScaledBitmap(tile, size, size, false)).apply {
            setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        }
    }

    private fun updateBackground() {
        preview.boxBackground = when (backgroundMode) {
            0 -> null
            1 -> ColorDrawable(startColor)
            2 -> GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(startColor, endColor))
            else -> sampleImage
        }
    }

    private fun colorField(label: String, color: Int, update: (Int) -> Unit) {
        val row = LinearLayout(this)
        val input = EditText(this).apply {
            setSingleLine()
            contentDescription = "$label ARGB"
            filters = arrayOf(InputFilter.LengthFilter(9))
            setText("#%08X".format(color))
            setSelectAllOnFocus(true)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(value: Editable?) {
                    val hex = value.toString().removePrefix("#")
                    if (hex.length == 8) hex.toLongOrNull(16)?.let { update(it.toInt()) }
                }
            })
        }
        row.addView(TextView(this).apply { text = "$label (ARGB)" })
        row.addView(input)
        panel.addView(row)
    }

    private fun effectControls(prefix: String, initial: InnerShadow, applyEffect: (InnerShadow) -> Unit) {
        var current = initial
        fun update(next: InnerShadow) {
            current = next
            applyEffect(next)
        }
        fun dp(px: Float) = (px / density).roundToInt()
        slider("${prefix}模糊", 0, 24, dp(initial.blur)) { update(current.copy(blur = it * density)) }
        slider("${prefix}扩张", -32, 32, dp(initial.spread)) { update(current.copy(spread = it * density)) }
        slider("${prefix}水平偏移", -24, 24, dp(initial.offsetX)) { update(current.copy(offsetX = it * density)) }
        slider("${prefix}垂直偏移", -24, 24, dp(initial.offsetY)) { update(current.copy(offsetY = it * density)) }
        listOf("透明度" to 24, "红" to 16, "绿" to 8, "蓝" to 0).forEach { (name, shift) ->
            slider(prefix + name, 0, 255, (initial.color ushr shift) and 255, "") {
                update(current.copy(color = (current.color and (255 shl shift).inv()) or (it shl shift)))
            }
        }
    }

    private fun section(title: String) {
        panel.addView(TextView(this).apply {
            text = title
            textSize = 20f
            setTextColor(Color.rgb(32, 52, 82))
        })
    }

    private fun slider(name: String, low: Int, high: Int, start: Int, unit: String = " dp", change: (Int) -> Unit) {
        val label = TextView(this)
        fun show(value: Int) { label.text = "$name  $value$unit" }
        show(start)
        panel.addView(label)
        panel.addView(SeekBar(this).apply {
            max = high - low
            progress = start - low
            contentDescription = name
            sliders[name] = this
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, progress: Int, user: Boolean) {
                    show(progress + low)
                    change(progress + low)
                }
                override fun onStartTrackingTouch(bar: SeekBar) {}
                override fun onStopTrackingTouch(bar: SeekBar) {}
            })
        })
    }
}
