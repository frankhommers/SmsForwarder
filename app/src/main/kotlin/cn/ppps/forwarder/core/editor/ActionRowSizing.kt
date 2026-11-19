package cn.ppps.forwarder.core.editor

/**
 * Side-by-side vs stacked action buttons, weighted by label width (same rule as the former
 * BaseFragment.updateActionRow). A null label width is a GONE button (e.g. the task editor's
 * hidden Test): LinearLayout skips it, so it must not count towards the fit either.
 * Pure so the decision is JVM-testable.
 */
internal object ActionRowSizing {
    data class Plan(val stacked: Boolean, val weights: List<Float>)

    fun plan(availablePx: Int, textWidthsPx: List<Float?>, buttonPaddingPx: Float): Plan {
        val widths = textWidthsPx.map { if (it == null) 0f else it + buttonPaddingPx }
        val stacked = widths.sum() > availablePx
        return Plan(stacked, if (stacked) widths.map { 0f } else widths)
    }
}

/**
 * Re-plan only when the row's usable width or a label width actually changed. Rotation and
 * wide-screen padding change the width; Delete→Discard changes a label. Identical input after
 * our own relayout is a no-op, so the row cannot keep re-laying itself out. A zero width
 * (early measurement, or a GONE row) is ignored instead of permanently stacking the buttons.
 */
internal class ActionRowSizingMemo {
    private var lastWidth = -1
    private var lastTexts: List<Float?>? = null

    fun needsApply(availablePx: Int, textWidthsPx: List<Float?>): Boolean =
        availablePx > 0 && (availablePx != lastWidth || textWidthsPx != lastTexts)

    /** Returns true and records the input when a new plan must be applied. */
    fun shouldApply(availablePx: Int, textWidthsPx: List<Float?>): Boolean {
        if (!needsApply(availablePx, textWidthsPx)) return false
        lastWidth = availablePx
        lastTexts = textWidthsPx
        return true
    }
}
