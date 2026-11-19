package cn.ppps.forwarder.widget.material

import android.os.Build
import android.text.Layout
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.TextView
import androidx.core.view.ViewCompat
import cn.ppps.forwarder.R
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationBarItemView
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.navigationrail.NavigationRailView
import kotlin.math.abs
import com.google.android.material.R as MR

/**
 * Readable primary-navigation labels for the phone bottom bar and the wide-screen rail.
 *
 * Material 1.9 draws navigation labels at an unscaled size on a single ellipsized line, which ignores the user's
 * font scale. Here every label gets the bar's own label text appearance in sp (M3 Label medium, 12sp, so it follows
 * the font scale), wraps at word or hyphenation points instead of being ellipsized, and the bottom bar grows to fit
 * the tallest label below the icon (rail items likewise). Labels are never made smaller than that size.
 *
 * When a label would need more than [MAX_LINES] lines in its item (a very narrow window with a large font), the bar
 * uses Material's selected-label (shifting) mode: only the selected destination shows its complete, wrapped label, in
 * a wider item. Every item keeps its full title as accessible name and tooltip (Material sets both from the title).
 */
class NavigationLabels private constructor(private val bar: NavigationBarView) :
    ViewTreeObserver.OnPreDrawListener, View.OnAttachStateChangeListener {

    /** Material's own height (M3: 80dp bar, 60dp rail item); never made lower than this. */
    private val baseHeight = maxOf(0, if (bar is NavigationRailView) bar.itemMinimumHeight else ViewCompat.getMinimumHeight(bar))
    private val labelSp = labelTextSp(bar)
    private var applied: List<Any>? = null

    /** Label size in pixels for the current configuration (font scale included). */
    val labelSizePx: Float get() = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, labelSp, bar.resources.displayMetrics)

    /**
     * Applies size, wrapping, label mode and height for the current bar width. Returns true when it changed anything
     * that needs a new layout pass; false when everything already matches.
     */
    fun update(): Boolean {
        val items = items(bar)
        if (items.isEmpty()) return false
        val menu = items.first().parent as? View ?: return false
        val horizontal = bar is BottomNavigationView
        val itemPadding = items.maxOf { it.paddingLeft + it.paddingRight }
        // Width of one item while every label shows: the bottom bar shares its width equally (as Material does),
        // rail items use the rail's width.
        val sharedWidth = if (horizontal) minOf(menu.width / items.size, dimen(MR.dimen.design_bottom_navigation_active_item_max_width)) else items.minOf { it.width }
        val available = sharedWidth - itemPadding
        if (available <= 0) return false
        val labels = items.flatMap { labelsOf(it) }
        val sizePx = labelSizePx
        val key = listOf(menu.width, sizePx, labels.map { it.text.toString() })
        if (key == applied && labels.all { abs(it.textSize - sizePx) < 0.5f && it.maxLines == Int.MAX_VALUE }) return false

        labels.forEach { style(it) }
        // Material's label group (BaselineLayout) keeps itemPaddingBottom below the FIRST baseline only, so a wrapped
        // label's last line would end on the bar's bottom edge. Pad every bottom-bar label so its LAST baseline keeps
        // that inset, exactly as a one-line label's does. The descent below the last baseline depends on the font, not
        // the width. Rail labels hang directly below their icon instead (see below), so the group padding suffices.
        labels.forEach { label ->
            val layout = measure(label, available).layout ?: return@forEach
            val descent = layout.height - layout.getLineBaseline(layout.lineCount - 1)
            val bottom = if (horizontal) maxOf(0, bar.itemPaddingBottom - descent) else 0
            if (label.paddingBottom != bottom) label.setPadding(label.paddingLeft, label.paddingTop, label.paddingRight, bottom)
        }
        // A label that must break inside a word without a hyphenation point (a short word wider than its item at
        // a large font) counts as not fitting, like a third line: the selected-label mode gives it a wider item.
        val lines = items.map { item -> labelsOf(item).maxOf { label -> measure(label, available).layout?.let { if (breaksInsideWord(label.text, it, label.paint)) MAX_LINES + 1 else it.lineCount } ?: MAX_LINES + 1 } }
        val mode = visibilityMode(lines)
        val selectedOnly = mode == NavigationBarView.LABEL_VISIBILITY_SELECTED
        // Selected-label mode on the bottom bar is Material's standard shifting bar: the selected item gets the extra
        // width (up to 168dp), so its complete label needs fewer lines.
        if (bar is BottomNavigationView && bar.isItemHorizontalTranslationEnabled != selectedOnly) bar.isItemHorizontalTranslationEnabled = selectedOnly
        if (bar.labelVisibilityMode != mode) bar.labelVisibilityMode = mode

        // Material places the icon at the top and the label group at the bottom of each item, so the item must be
        // tall enough for both. The bottom bar is exactly its minimum height: size it for the tallest label any
        // selection can show, so the bar does not change height when another tab is selected. Rail items size
        // themselves from the 24dp icon rather than the 32dp indicator, so a wrapped label would overlap it: give
        // them the same explicit minimum.
        val tallest = items.maxOf { item ->
            labelsOf(item).maxOf { label ->
                val width = if (horizontal && selectedOnly) selectedItemWidth(menu.width, items.size, label) - itemPadding else available
                measure(label, width).measuredHeight
            }
        }
        val icon = if (bar.isItemActiveIndicatorEnabled) maxOf(bar.itemActiveIndicatorHeight, bar.itemIconSize) else bar.itemIconSize
        val gap = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, if (horizontal) BAR_LABEL_GAP_DP else RAIL_LABEL_GAP_DP, bar.resources.displayMetrics).toInt()
        // The label's own bottom padding (above) carries the inset below a bar label's last line; on the rail the
        // label group's own itemPaddingBottom does.
        val height = requiredHeight(baseHeight, bar.itemPaddingTop, icon, gap, tallest, if (horizontal) 0 else bar.itemPaddingBottom)
        if (bar is NavigationRailView) {
            if (bar.itemMinimumHeight != height) bar.itemMinimumHeight = height
            // Rail items share the tallest item's height. Material bottom-aligns the label group, which leaves a short
            // label far below its own icon and right above the next one at large fonts; hang it directly below its icon.
            val top = bar.itemPaddingTop + icon + gap
            for (item in items) {
                val group = item.findViewById<View>(MR.id.navigation_bar_item_labels_group) ?: continue
                val lp = group.layoutParams as? android.widget.FrameLayout.LayoutParams ?: continue
                val gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                if (lp.gravity != gravity || lp.topMargin != top) {
                    lp.gravity = gravity
                    lp.topMargin = top
                    group.layoutParams = lp
                }
            }
        } else if (ViewCompat.getMinimumHeight(bar) != height) {
            bar.minimumHeight = height
        }
        applied = key
        bar.requestLayout()
        return true
    }

    /** Width Material's shifting bottom bar gives the selected item (BottomNavigationMenuView.onMeasure). */
    private fun selectedItemWidth(menuWidth: Int, count: Int, label: TextView): Int {
        val activeMin = dimen(MR.dimen.design_bottom_navigation_active_item_min_width)
        val activeMax = dimen(MR.dimen.design_bottom_navigation_active_item_max_width)
        val inactiveMin = dimen(MR.dimen.design_bottom_navigation_item_min_width)
        label.measure(View.MeasureSpec.makeMeasureSpec(activeMax, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        return minOf(menuWidth - (count - 1) * inactiveMin, maxOf(activeMin, label.measuredWidth), activeMax)
    }

    private fun dimen(id: Int) = bar.resources.getDimensionPixelSize(id)

    private fun measure(label: TextView, width: Int): TextView {
        label.measure(View.MeasureSpec.makeMeasureSpec(maxOf(1, width), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        return label
    }

    private fun style(label: TextView) {
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, labelSp)
        // M3 Label medium line height (16sp for 12sp), also in sp; reset any spacing computed for Material's dp size.
        label.setLineSpacing(0f, 1f)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            label.lineHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, labelSp * LINE_HEIGHT_RATIO, bar.resources.displayMetrics).toInt()
        }
        label.maxLines = Int.MAX_VALUE
        label.ellipsize = null
        label.setHorizontallyScrolling(false)
        label.gravity = Gravity.CENTER_HORIZONTAL
        label.textAlignment = View.TEXT_ALIGNMENT_CENTER
        val configuration = bar.resources.configuration
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) label.textLocales = configuration.locales else setLegacyLocale(label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Long single words (Bestemmingen, Instellingen) break at a hyphenation point of the app language.
            label.hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NORMAL
            label.breakStrategy = Layout.BREAK_STRATEGY_HIGH_QUALITY
        }
    }

    @Suppress("DEPRECATION")
    private fun setLegacyLocale(label: TextView) {
        label.textLocale = bar.resources.configuration.locale
    }

    override fun onPreDraw(): Boolean = !update()

    override fun onViewAttachedToWindow(v: View) {
        bar.viewTreeObserver.addOnPreDrawListener(this)
    }

    override fun onViewDetachedFromWindow(v: View) {
        bar.viewTreeObserver.removeOnPreDrawListener(this)
    }

    companion object {
        /** Lines one label may use while every label is shown. */
        const val MAX_LINES = 2
        /** Space between the active indicator and the label, as in Material's own one-line layout (bar 80dp, rail 60dp). */
        private const val BAR_LABEL_GAP_DP = 4f
        private const val RAIL_LABEL_GAP_DP = 0f
        private const val DEFAULT_LABEL_SP = 12f
        private const val LINE_HEIGHT_RATIO = 16f / 12f

        /** Pure rule: show every label while each fits in [maxLines] lines; otherwise only the selected one. */
        fun visibilityMode(lineCounts: List<Int>, maxLines: Int = MAX_LINES): Int =
            if (lineCounts.all { it <= maxLines }) NavigationBarView.LABEL_VISIBILITY_LABELED else NavigationBarView.LABEL_VISIBILITY_SELECTED

        /**
         * True when [layout] breaks [text] inside a word without drawing a hyphen there (a forced break). Hyphenated
         * line widths include the hyphen, so a mid-word line whose width is only its own characters was forced.
         */
        fun breaksInsideWord(text: CharSequence, layout: Layout, paint: android.text.TextPaint): Boolean {
            val hyphen = paint.measureText("-")
            for (line in 0 until layout.lineCount - 1) {
                val end = layout.getLineEnd(line)
                if (end <= 0 || end >= text.length || !text[end - 1].isLetterOrDigit() || !text[end].isLetterOrDigit()) continue
                val start = layout.getLineStart(line)
                if (layout.getLineWidth(line) < paint.measureText(text, start, end) + hyphen / 2) return true
            }
            return false
        }

        /** Pure rule: item content height for padding, icon, gap and the tallest label, never below Material's own. */
        fun requiredHeight(base: Int, paddingTop: Int, icon: Int, gap: Int, tallestLabel: Int, paddingBottom: Int): Int =
            maxOf(base, paddingTop + icon + gap + tallestLabel + paddingBottom)

        /** Installs once per bar; call after the menu items have been added. */
        fun install(bar: NavigationBarView): NavigationLabels {
            of(bar)?.let { return it }
            val labels = NavigationLabels(bar)
            bar.setTag(R.id.navigation_labels_controller, labels)
            bar.addOnAttachStateChangeListener(labels)
            if (ViewCompat.isAttachedToWindow(bar)) labels.onViewAttachedToWindow(bar)
            return labels
        }

        fun of(bar: NavigationBarView): NavigationLabels? = bar.getTag(R.id.navigation_labels_controller) as? NavigationLabels

        fun items(bar: NavigationBarView): List<NavigationBarItemView> {
            fun walk(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { walk(v.getChildAt(it)) } else emptyList()
            return walk(bar).filterIsInstance<NavigationBarItemView>()
        }

        /** The small (inactive) and large (active) label of one item; Material shows one of them at a time. */
        fun labelsOf(item: NavigationBarItemView): List<TextView> = listOfNotNull(
            item.findViewById(com.google.android.material.R.id.navigation_bar_item_small_label_view),
            item.findViewById(com.google.android.material.R.id.navigation_bar_item_large_label_view)
        )

        fun labels(bar: NavigationBarView): List<TextView> = items(bar).flatMap { labelsOf(it) }

        /** The sp size of the bar's label text appearance (M3 Label medium); 12sp when it cannot be read. */
        private fun labelTextSp(bar: NavigationBarView): Float {
            val res = bar.itemTextAppearanceActive
            if (res != 0) {
                val a = bar.context.obtainStyledAttributes(res, intArrayOf(android.R.attr.textSize))
                try {
                    val value = TypedValue()
                    if (a.getValue(0, value) && value.type == TypedValue.TYPE_DIMENSION) {
                        val unit = (value.data shr TypedValue.COMPLEX_UNIT_SHIFT) and TypedValue.COMPLEX_UNIT_MASK
                        if (unit == TypedValue.COMPLEX_UNIT_SP || unit == TypedValue.COMPLEX_UNIT_DIP) return TypedValue.complexToFloat(value.data)
                    }
                } finally {
                    a.recycle()
                }
            }
            return DEFAULT_LABEL_SP
        }
    }
}
