package cn.ppps.forwarder.core.editor

import android.view.View

/**
 * Raw keyboard/focus facts for one editor screen, gathered by [EditorActionRow].
 * Kept free of Android objects so the decision and the state machine are JVM-testable.
 */
internal data class ImeSignals(
    /** WindowInsetsCompat ime() visibility (API 30+: platform state; API 23-29: AndroidX heuristic). */
    val imeVisible: Boolean,
    /** Bottom IME inset in pixels; 0 for floating keyboards that do not cover the screen bottom. */
    val imeBottomPx: Int,
    /** A physical keyboard is connected and open (Configuration.keyboard/hardKeyboardHidden). */
    val hardwareKeyboard: Boolean,
    /** TalkBack-style touch exploration is on. */
    val touchExploration: Boolean,
    /** The editor root is visible on screen (not a hidden back-stack fragment). */
    val editorShown: Boolean,
    /** Some view inside the editor root has focus, i.e. the IME is serving this editor. */
    val focusInEditor: Boolean,
    /** Focus is on one of the action buttons; hiding the row would throw that focus away. */
    val focusInActionRow: Boolean,
)

internal object ImeActionRowPolicy {
    /**
     * Hide the fixed action row only while a docked software keyboard serves this editor.
     * Physical-keyboard and TalkBack users keep the row: they reach Save by focus navigation,
     * which cannot land on a GONE view. Unknown insets (null: detached or API < 23) keep the row.
     */
    fun shouldHide(s: ImeSignals?): Boolean =
        s != null && s.imeVisible && s.imeBottomPx > 0 && !s.hardwareKeyboard && !s.touchExploration &&
            s.editorShown && s.focusInEditor && !s.focusInActionRow
}

/**
 * State machine for hiding one editor action row while the software keyboard is open.
 *
 * Writes the row visibility only on real transitions, never touches focus or scroll position,
 * leaves a row that app code made INVISIBLE/GONE alone, and restores the original visibility on
 * keyboard close, detach and release. Changes are applied from a host-provided "before next
 * frame" hook, never from inside the layout pass that reported the window resize.
 */
internal class ImeActionRowController(private val host: Host) {
    interface Host {
        val isAttached: Boolean
        var rowVisibility: Int
        fun signals(): ImeSignals?
        /** Ask for [runScheduled] once before the next frame is drawn; false if impossible now. */
        fun requestFrame(): Boolean
        fun cancelFrame()
    }

    /** True while this controller (not app code) made the row GONE. */
    var hiddenByKeyboard = false
        private set
    var released = false
        private set
    private var originalVisibility = View.VISIBLE
    private var lastDecision = false
    var scheduled = false
        private set

    /**
     * Root layout callback: cheap check, schedules only when the decision flips, or when app code
     * made a previously non-visible row VISIBLE while the keyboard is still open.
     */
    fun onRootLayout() {
        if (released) return
        val hide = ImeActionRowPolicy.shouldHide(host.signals())
        if (hide != lastDecision || (hide && !hiddenByKeyboard && host.rowVisibility == View.VISIBLE)) schedule()
    }

    fun onAttached() = schedule()

    fun onDetached() {
        unschedule()
        restore()
        lastDecision = false
    }

    /** Rotation keeps the view (configChanges); a physical keyboard may have been (dis)connected. */
    fun onConfigurationChanged() = schedule()

    fun release() {
        if (released) return
        unschedule()
        restore()
        released = true
    }

    /** Runs the scheduled evaluation; returns true when the row visibility was changed. */
    fun runScheduled(): Boolean {
        if (!scheduled) return false
        scheduled = false
        return evaluate()
    }

    private fun schedule() {
        if (released || scheduled || !host.isAttached) return
        scheduled = host.requestFrame()
    }

    private fun unschedule() {
        if (!scheduled) return
        scheduled = false
        host.cancelFrame()
    }

    private fun evaluate(): Boolean {
        if (released || !host.isAttached) return false
        val hide = ImeActionRowPolicy.shouldHide(host.signals())
        lastDecision = hide
        if (hide == hiddenByKeyboard) return false
        if (!hide) return restore()
        val current = host.rowVisibility
        if (current != View.VISIBLE) return false // INVISIBLE/GONE by app code: not ours to change.
        originalVisibility = current
        hiddenByKeyboard = true
        host.rowVisibility = View.GONE
        return true
    }

    private fun restore(): Boolean {
        if (!hiddenByKeyboard) return false
        hiddenByKeyboard = false
        // If app code changed the row meanwhile, its choice wins over our remembered value.
        if (host.rowVisibility != View.GONE) return false
        host.rowVisibility = originalVisibility
        return true
    }
}
