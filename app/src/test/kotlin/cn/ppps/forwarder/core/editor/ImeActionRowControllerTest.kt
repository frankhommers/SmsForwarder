package cn.ppps.forwarder.core.editor

import android.view.View
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * JVM state-machine tests for hiding the editor action row while the software keyboard is open.
 * A fake host replaces the View: these prove the transition logic and lifecycle bookkeeping,
 * not real IME/inset behavior on a device.
 */
class ImeActionRowControllerTest {
    private class FakeHost : ImeActionRowController.Host {
        override var isAttached = true
        var visibility = View.VISIBLE
        var visibilityWrites = 0
        override var rowVisibility: Int
            get() = visibility
            set(value) { visibility = value; visibilityWrites++ }
        var signals: ImeSignals? = closed()
        var frameRequests = 0
        var frameCancels = 0
        var frameAvailable = true
        override fun signals() = signals
        override fun requestFrame(): Boolean { frameRequests++; return frameAvailable }
        override fun cancelFrame() { frameCancels++ }
    }

    private lateinit var host: FakeHost
    private lateinit var controller: ImeActionRowController

    @Before fun setUp() {
        host = FakeHost()
        controller = ImeActionRowController(host)
    }

    /** One layout callback followed by the frame hook, as EditorActionRow wires them. */
    private fun layoutAndFrame() { controller.onRootLayout(); controller.runScheduled() }

    @Test fun keyboardOpenHidesRowAndCloseRestoresIt() {
        host.signals = open()
        layoutAndFrame()
        assertEquals(View.GONE, host.visibility)
        assertTrue(controller.hiddenByKeyboard)

        host.signals = closed()
        layoutAndFrame()
        assertEquals(View.VISIBLE, host.visibility)
        assertFalse(controller.hiddenByKeyboard)
        assertEquals("exactly one hide and one restore", 2, host.visibilityWrites)
    }

    @Test fun repeatedLayoutPassesDoNotRescheduleOrRewrite() {
        host.signals = open()
        repeat(5) { controller.onRootLayout() }
        assertEquals("one pending frame for a burst of layouts", 1, host.frameRequests)
        controller.runScheduled()
        // Our own visibility change re-lays the root: same decision, nothing new to do.
        repeat(5) { controller.onRootLayout() }
        assertEquals(1, host.frameRequests)
        assertFalse(controller.runScheduled())
        assertEquals(1, host.visibilityWrites)
    }

    @Test fun originallyGoneOrInvisibleRowIsNeverTouched() {
        listOf(View.GONE, View.INVISIBLE).forEach { original ->
            setUp()
            host.visibility = original
            host.signals = open()
            layoutAndFrame()
            host.signals = closed()
            layoutAndFrame()
            assertEquals(original, host.visibility)
            assertEquals(0, host.visibilityWrites)
            assertFalse(controller.hiddenByKeyboard)
        }
    }

    @Test fun rowShownByAppWhileKeyboardOpenIsHiddenOnceThenRestoredVisible() {
        host.visibility = View.GONE
        host.signals = open()
        layoutAndFrame()
        assertEquals(View.GONE, host.visibility)
        host.visibility = View.VISIBLE // app reveals the row; its visibility change re-lays the root
        layoutAndFrame()
        assertEquals(View.GONE, host.visibility)
        assertTrue(controller.hiddenByKeyboard)
        repeat(3) { controller.onRootLayout() }
        assertFalse("no further work once hidden", controller.scheduled)
        host.signals = closed()
        layoutAndFrame()
        assertEquals("restores the VISIBLE the app chose", View.VISIBLE, host.visibility)
    }

    @Test fun appMadeRowVisibleWhileHiddenKeepsAppChoice() {
        host.signals = open()
        layoutAndFrame()
        host.visibility = View.INVISIBLE // App code changes the row while the keyboard is up.
        host.signals = closed()
        layoutAndFrame()
        assertEquals(View.INVISIBLE, host.visibility)
        assertFalse(controller.hiddenByKeyboard)
    }

    @Test fun detachRestoresAndCancelsPendingWork() {
        host.signals = open()
        layoutAndFrame()
        controller.onRootLayout() // no flip: nothing pending
        host.signals = closed()
        controller.onRootLayout() // flip pending
        host.isAttached = false
        controller.onDetached()
        assertEquals(View.VISIBLE, host.visibility)
        assertEquals(1, host.frameCancels)
        assertFalse("cancelled work does not run", controller.runScheduled())
    }

    @Test fun reattachReevaluatesFromScratch() {
        host.signals = open()
        layoutAndFrame()
        host.isAttached = false
        controller.onDetached()
        assertEquals(View.VISIBLE, host.visibility)

        host.isAttached = true
        controller.onAttached() // e.g. returning to the back-stacked editor with keyboard up
        controller.runScheduled()
        assertEquals(View.GONE, host.visibility)

        host.signals = closed()
        layoutAndFrame()
        assertEquals(View.VISIBLE, host.visibility)
    }

    @Test fun detachedHostSchedulesNothing() {
        host.isAttached = false
        host.signals = open()
        controller.onRootLayout()
        controller.onAttached()
        controller.onConfigurationChanged()
        assertEquals(0, host.frameRequests)
        assertEquals(View.VISIBLE, host.visibility)
    }

    @Test fun configurationChangeWithHardwareKeyboardRestoresRow() {
        host.signals = open()
        layoutAndFrame()
        assertEquals(View.GONE, host.visibility)
        // A physical keyboard slides out/connects: configChanges keeps the view, IME may linger.
        host.signals = open().copy(hardwareKeyboard = true)
        controller.onConfigurationChanged()
        controller.runScheduled()
        assertEquals(View.VISIBLE, host.visibility)
    }

    @Test fun rotationWithKeyboardStillOpenKeepsSingleHiddenState() {
        host.signals = open()
        layoutAndFrame()
        controller.onConfigurationChanged()
        controller.onRootLayout()
        assertEquals("deduplicated across config + layout", 2, host.frameRequests)
        controller.runScheduled()
        assertEquals(View.GONE, host.visibility)
        assertEquals(1, host.visibilityWrites)
    }

    @Test fun failedFrameRequestDoesNotWedgeScheduling() {
        host.frameAvailable = false
        host.signals = open()
        controller.onRootLayout()
        assertFalse(controller.scheduled)
        host.frameAvailable = true
        controller.onRootLayout()
        assertTrue("next layout retries", controller.scheduled)
        controller.runScheduled()
        assertEquals(View.GONE, host.visibility)
    }

    @Test fun releaseRestoresAndIsTerminalAndIdempotent() {
        host.signals = open()
        layoutAndFrame()
        controller.release()
        controller.release()
        assertEquals(View.VISIBLE, host.visibility)
        assertTrue(controller.released)
        controller.onRootLayout()
        controller.onAttached()
        controller.onConfigurationChanged()
        assertFalse(controller.runScheduled())
        assertEquals(View.VISIBLE, host.visibility)
        assertEquals(1, host.frameRequests)
    }

    @Test fun unknownInsetsNeverHide() {
        host.signals = null // API < 23 or root insets not yet available
        controller.onAttached()
        controller.runScheduled()
        controller.onRootLayout()
        assertEquals(View.VISIBLE, host.visibility)
        assertEquals(0, host.visibilityWrites)
    }

    @Test fun policyKeepsRowForEveryNonSoftwareKeyboardCase() {
        assertTrue(ImeActionRowPolicy.shouldHide(open()))
        assertFalse(ImeActionRowPolicy.shouldHide(null))
        assertFalse(ImeActionRowPolicy.shouldHide(closed()))
        assertFalse("floating IME covers no bottom area", ImeActionRowPolicy.shouldHide(open().copy(imeBottomPx = 0)))
        assertFalse("physical keyboard", ImeActionRowPolicy.shouldHide(open().copy(hardwareKeyboard = true)))
        assertFalse("TalkBack", ImeActionRowPolicy.shouldHide(open().copy(touchExploration = true)))
        assertFalse("hidden back-stack editor", ImeActionRowPolicy.shouldHide(open().copy(editorShown = false)))
        assertFalse("keyboard serves another window", ImeActionRowPolicy.shouldHide(open().copy(focusInEditor = false)))
        assertFalse("focus on Save itself", ImeActionRowPolicy.shouldHide(open().copy(focusInActionRow = true)))
    }

    companion object {
        internal fun open() = ImeSignals(imeVisible = true, imeBottomPx = 840, hardwareKeyboard = false, touchExploration = false,
            editorShown = true, focusInEditor = true, focusInActionRow = false)
        internal fun closed() = open().copy(imeVisible = false, imeBottomPx = 0)
    }
}
