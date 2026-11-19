package cn.ppps.forwarder.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import com.xuexiang.xui.widget.button.SmoothCheckBox

/** Preserve the existing check/listener API while drawing an unambiguous square checkbox. */
class SquareCheckBox @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : SmoothCheckBox(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    init {
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        val values = context.obtainStyledAttributes(attrs, intArrayOf(android.R.attr.checked))
        isChecked = values.getBoolean(0, false)
        values.recycle()
    }
    override fun onDraw(canvas: Canvas) {
        val size = minOf(width, height).toFloat()
        val left = (width - size) / 2 + 2
        val top = (height - size) / 2 + 2
        val right = left + size - 4
        val bottom = top + size - 4
        paint.color = if (!isEnabled) Color.GRAY else if (isChecked) Color.rgb(25, 118, 210) else Color.rgb(90, 90, 90)
        paint.strokeWidth = 2 * resources.displayMetrics.density
        paint.style = if (isChecked) Paint.Style.FILL else Paint.Style.STROKE
        canvas.drawRoundRect(left, top, right, bottom, 2f, 2f, paint)
        if (isChecked) {
            paint.color = Color.WHITE; paint.style = Paint.Style.STROKE
            val p = Path()
            p.moveTo(left + size * .18f, top + size * .45f)
            p.lineTo(left + size * .36f, top + size * .63f)
            p.lineTo(left + size * .67f, top + size * .22f)
            canvas.drawPath(p, paint)
        }
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val group = parent as? ViewGroup ?: return
        val label = group.getChildAt(group.indexOfChild(this) + 1) as? TextView
        if (contentDescription.isNullOrEmpty()) contentDescription = label?.text
        if (label != null && !label.hasOnClickListeners()) label.setOnClickListener { if (isEnabled) performClick() }
    }
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.CheckBox"
        info.isCheckable = true
        info.isChecked = isChecked
    }
}
