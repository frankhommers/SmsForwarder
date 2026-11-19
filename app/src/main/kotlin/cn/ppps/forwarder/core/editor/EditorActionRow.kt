package cn.ppps.forwarder.core.editor

import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import cn.ppps.forwarder.R

/**
 * View-owned helper for the fixed Delete/Save/Test row at the bottom of an editor fragment.
 *
 * 1. Sizing: buttons sit side by side, weighted by label width, or stack when they do not fit.
 *    Re-planned when the row's own width or a label changes (rotation, wide-screen padding,
 *    Delete→Discard), measured after layout instead of from a stale pre-rotation width.
 * 2. Keyboard: while a docked software keyboard serves this editor the row is GONE, so the
 *    adjustResize window gives that space to the form instead of lifting the bar onto the
 *    fields. The original visibility returns when the keyboard closes or the view detaches.
 *
 * Insets are read passively with [ViewCompat.getRootWindowInsets] from layout callbacks
 * (adjustResize re-lays the root whenever the keyboard opens or closes). No
 * OnApplyWindowInsetsListener is installed and no insets are consumed or re-requested, so other
 * inset listeners and children are unaffected and the result does not depend on the XPage
 * container dispatching insets to this view. Focus, scroll position, drafts, window flags and
 * soft input mode are never changed.
 *
 * Changes are applied from a one-shot pre-draw hook that is removed before it runs; the frame
 * that would show the intermediate state is skipped once and the next traversal draws the result.
 *
 * Limits: below API 23 root insets are unavailable, so the row stays visible (previous behavior).
 * API 23-29 relies on AndroidX core's adjustResize-based IME detection.
 */
class EditorActionRow private constructor(
    private val root: ViewGroup,
    private val row: LinearLayout,
    private val buttons: List<Button>,
) {
    private val density = root.resources.displayMetrics.density
    private val sizingMemo = ActionRowSizingMemo()
    private var sizingPending = false
    private var preDrawObserver: ViewTreeObserver? = null
    private var released = false

    private val controller = ImeActionRowController(object : ImeActionRowController.Host {
        override val isAttached get() = ViewCompat.isAttachedToWindow(root)
        override var rowVisibility: Int
            get() = row.visibility
            set(value) { row.visibility = value }
        override fun signals(): ImeSignals? = readSignals()
        override fun requestFrame(): Boolean = addPreDraw()
        override fun cancelFrame() { if (!sizingPending) removePreDraw() }
    })

    private val preDraw = ViewTreeObserver.OnPreDrawListener {
        removePreDraw()
        var changed = controller.runScheduled()
        if (sizingPending) { sizingPending = false; changed = applySizing() || changed }
        !changed // Skip only the frame that would show the pre-change layout.
    }
    private val rootLayoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        controller.onRootLayout()
    }
    private val rowLayoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        if (!sizingPending && sizingMemo.needsApply(availableWidth(), labelWidths())) sizingPending = addPreDraw()
    }
    private val attachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) { controller.onAttached() }
        override fun onViewDetachedFromWindow(v: View) {
            sizingPending = false
            removePreDraw()
            controller.onDetached()
        }
    }

    init {
        root.addOnLayoutChangeListener(rootLayoutListener)
        root.addOnAttachStateChangeListener(attachListener)
        if (buttons.size >= 2) row.addOnLayoutChangeListener(rowLayoutListener)
        if (ViewCompat.isAttachedToWindow(root)) controller.onAttached()
    }

    /** True while the keyboard (not app code) has hidden the row; for diagnostics. */
    val hiddenByKeyboard get() = controller.hiddenByKeyboard

    /** Fragment.onConfigurationChanged: keyboard type may have changed. Width follows by layout. */
    fun onConfigurationChanged() {
        if (!released) controller.onConfigurationChanged()
    }

    /** Call from onDestroyView: removes every listener/callback and restores the row. */
    fun release() {
        if (released) return
        released = true
        root.removeOnLayoutChangeListener(rootLayoutListener)
        root.removeOnAttachStateChangeListener(attachListener)
        row.removeOnLayoutChangeListener(rowLayoutListener)
        sizingPending = false
        removePreDraw()
        controller.release()
        if (root.getTag(TAG_KEY) === this) root.setTag(TAG_KEY, null)
    }

    /** One shared, self-removing pre-draw hook for both jobs; true when it is (now) installed. */
    private fun addPreDraw(): Boolean {
        if (preDrawObserver != null) return true
        if (released || !ViewCompat.isAttachedToWindow(root)) return false
        preDrawObserver = root.viewTreeObserver.also { it.addOnPreDrawListener(preDraw) }
        root.invalidate() // Make sure a traversal (and so the pre-draw hook) actually happens.
        return true
    }

    private fun removePreDraw() {
        val observer = preDrawObserver ?: return
        preDrawObserver = null
        (if (observer.isAlive) observer else root.viewTreeObserver).removeOnPreDrawListener(preDraw)
    }

    private fun readSignals(): ImeSignals? {
        val insets = ViewCompat.getRootWindowInsets(root) ?: return null
        val config = root.resources.configuration
        val focused = root.findFocus()
        val a11y = root.context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        return ImeSignals(
            imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime()),
            imeBottomPx = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom,
            hardwareKeyboard = config.keyboard != Configuration.KEYBOARD_NOKEYS &&
                config.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO,
            touchExploration = a11y?.isTouchExplorationEnabled == true,
            editorShown = root.isShown,
            focusInEditor = focused != null,
            focusInActionRow = focused != null && isInside(focused, row),
        )
    }

    private fun availableWidth() = row.width - row.paddingLeft - row.paddingRight
    private fun labelWidths() = buttons.map { if (it.visibility == View.GONE) null else it.paint.measureText(it.text.toString()) }

    private fun applySizing(): Boolean {
        if (released || row.visibility == View.GONE) return false
        val texts = labelWidths()
        val available = availableWidth()
        if (!sizingMemo.shouldApply(available, texts)) return false
        val plan = ActionRowSizing.plan(available, texts, 32 * density)
        val gap = (4 * density).toInt()
        row.orientation = if (plan.stacked) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        buttons.forEachIndexed { index, button ->
            button.isSingleLine = false
            button.maxLines = 2
            button.layoutParams = LinearLayout.LayoutParams(if (plan.stacked) -1 else 0, -2, plan.weights[index]).apply {
                setMargins(gap, 0, gap, 0)
            }
        }
        return true
    }

    companion object {
        /** Any app id works as a tag key; btn_save is the id this helper is defined by. */
        private val TAG_KEY = R.id.btn_save

        /**
         * Installs on an editor only: btn_save inside a LinearLayout that holds nothing but
         * buttons and is a direct child of the fragment root. Other screens get null and stay
         * untouched. Installing twice on the same root returns the existing helper.
         */
        fun install(root: View): EditorActionRow? {
            val group = root as? ViewGroup ?: return null
            (group.getTag(TAG_KEY) as? EditorActionRow)?.let { if (!it.released) return it }
            val row = findRow(group) ?: return null
            val buttons = (0 until row.childCount).map { row.getChildAt(it) as Button }
            return EditorActionRow(group, row, buttons).also { group.setTag(TAG_KEY, it) }
        }

        internal fun findRow(root: ViewGroup): LinearLayout? {
            val save = root.findViewById<View>(R.id.btn_save) as? Button ?: return null
            val row = save.parent as? LinearLayout ?: return null
            if (row.parent !== root) return null
            if ((0 until row.childCount).any { row.getChildAt(it) !is Button }) return null
            return row
        }

        private fun isInside(view: View, ancestor: View): Boolean {
            var current: View? = view
            while (current != null) {
                if (current === ancestor) return true
                current = current.parent as? View
            }
            return false
        }
    }
}
