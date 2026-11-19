package cn.ppps.forwarder.fragment

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import kotlin.math.max

/**
 * Action row of the Logs filter dialog (children in order: Reset, Cancel, Apply).
 *
 * Side by side when they fit: the first child at the start edge, the others at the end edge with the last child
 * (the confirming action) outermost. When they do not fit (large font, long translation, narrow window) every
 * button gets its own full-height row, end-aligned, confirming action on top, as Material stacked dialog actions.
 * Buttons are always measured at their full height, never squeezed: only the scrolling form above gives way.
 */
class LogsFilterActionBar @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ViewGroup(context, attrs) {

    private val gap = (8 * resources.displayMetrics.density).toInt()

    /** True after the last measure pass stacked the buttons. */
    var isStacked = false
        private set

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val available = max(0, width - paddingLeft - paddingRight)
        val buttons = visibleChildren()
        val wrap = MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST)
        val unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        buttons.forEach { it.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), unspecified) }
        isStacked = !fitsInRow(buttons.map { it.measuredWidth }, gap, available)
        val contentHeight = if (isStacked) {
            buttons.forEach { it.measure(wrap, unspecified) }
            buttons.sumOf { it.measuredHeight }
        } else {
            buttons.maxOfOrNull { it.measuredHeight } ?: 0
        }
        val height = contentHeight + paddingTop + paddingBottom
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), max(height, suggestedMinimumHeight))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val buttons = visibleChildren()
        if (buttons.isEmpty()) return
        val rtl = ViewCompat.getLayoutDirection(this) == ViewCompat.LAYOUT_DIRECTION_RTL
        val width = r - l
        // x of a child whose start edge is at `start` (measured from the start side), mirrored for RTL.
        fun place(child: View, start: Int, top: Int) {
            val left = if (rtl) width - start - child.measuredWidth else start
            child.layout(left, top, left + child.measuredWidth, top + child.measuredHeight)
        }
        val startEdge = if (rtl) paddingRight else paddingLeft
        val endEdge = width - (if (rtl) paddingLeft else paddingRight)
        if (isStacked) {
            var top = paddingTop
            for (child in buttons.asReversed()) {
                place(child, endEdge - child.measuredWidth, top)
                top += child.measuredHeight
            }
        } else {
            val rowHeight = buttons.maxOf { it.measuredHeight }
            fun topOf(child: View) = paddingTop + (rowHeight - child.measuredHeight) / 2
            place(buttons.first(), startEdge, topOf(buttons.first()))
            var end = endEdge
            for (child in buttons.drop(1).asReversed()) {
                place(child, end - child.measuredWidth, topOf(child))
                end -= child.measuredWidth + gap
            }
        }
    }

    private fun visibleChildren() = (0 until childCount).map { getChildAt(it) }.filter { it.visibility != View.GONE }

    companion object {
        /** Whether buttons of [widths] fit on one line of [available] px with [gap] px between them. */
        fun fitsInRow(widths: List<Int>, gap: Int, available: Int): Boolean {
            if (widths.isEmpty()) return true
            return widths.sum() + gap * (widths.size - 1) <= available
        }
    }
}
