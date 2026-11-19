package cn.ppps.forwarder

import android.content.Intent
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Tap
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.platform.app.InstrumentationRegistry
import cn.ppps.forwarder.activity.MainActivity
import cn.ppps.forwarder.fragment.LogsFilterActionBar
import cn.ppps.forwarder.fragment.LogsFragment
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Logs filter dialog (lg-filter) on a device. Local only: opens the Logs page and its filter dialog, never sends,
 * deletes or creates logs. Run with the rest of androidTest; check that `am instrument` reports a nonzero OK count.
 */
class LogsFilterDialogTest {
    private val ins = InstrumentationRegistry.getInstrumentation()

    private fun waitUntil(label: String, condition: () -> Boolean) {
        repeat(100) { var ok = false; ins.runOnMainSync { ok = condition() }; if (ok) return; Thread.sleep(100) }
        val file = java.io.File(ins.targetContext.getExternalFilesDir(null), "logs-filter-timeout.png")
        java.io.FileOutputStream(file).use { ins.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        fail("Timed out: $label")
    }

    private fun logsFragment(main: MainActivity): LogsFragment {
        var found: LogsFragment? = null
        waitUntil("LogsFragment visible") {
            fun all(manager: androidx.fragment.app.FragmentManager): List<androidx.fragment.app.Fragment> =
                manager.fragments.flatMap { listOf(it) + all(it.childFragmentManager) }
            found = all(main.supportFragmentManager).filterIsInstance<LogsFragment>().firstOrNull { it.isResumed && it.isVisible }
            found != null
        }
        return found!!
    }

    private fun popup(fragment: LogsFragment): AlertDialog? =
        LogsFragment::class.java.getDeclaredField("logsFilterPopup").apply { isAccessible = true }.get(fragment) as AlertDialog?

    @Suppress("UNCHECKED_CAST")
    private fun filter(fragment: LogsFragment): Map<String, Any> =
        (LogsFragment::class.java.getDeclaredField("currentFilter").apply { isAccessible = true }.get(fragment) as Map<String, Any>).toMap()

    /**
     * Readiness before tapping the toolbar: the activity window has input focus (no dialog/tooltip window still on
     * top) and the initial pull-to-refresh animation has settled. Measured cause of the flake: right after launch the
     * main thread renders 700-900 ms frames, Espresso's injected tap then lasts longer than long_press_timeout
     * ("MotionEvents: Overslept and turned a tap into a long press") and the toolbar action shows its tooltip
     * instead of performing the click, so the dialog never opens.
     */
    private fun awaitInteractive(fragment: LogsFragment) {
        waitUntil("logs window focused and refresh settled") {
            fragment.requireActivity().hasWindowFocus() &&
                fragment.binding?.refreshLayout?.state == com.scwang.smartrefresh.layout.constant.RefreshState.None
        }
        ins.waitForIdleSync()
    }

    /** Espresso rollback for a tap that became a long press (tooltip only): settle the main thread, then the tap is retried. */
    private val settleAfterLongPress = object : ViewAction {
        override fun getConstraints() = isDisplayed()
        override fun getDescription() = "settle after a tap that became a long press"
        override fun perform(uiController: UiController, view: View) = uiController.loopMainThreadUntilIdle()
    }

    private fun openFilter(fragment: LogsFragment): AlertDialog {
        awaitInteractive(fragment)
        onView(allOf(withContentDescription(R.string.m3_filter), isDisplayed())).perform(click(settleAfterLongPress))
        waitUntil("filter dialog showing") { popup(fragment)?.isShowing == true }
        ins.waitForIdleSync()
        return popup(fragment)!!
    }

    /** Every action is laid out at >= 48dp height (button incl. its touch insets) inside the dialog window. */
    private fun assertActionsVisible(dialog: AlertDialog) {
        ins.runOnMainSync {
            val density = dialog.context.resources.displayMetrics.density
            val decor = dialog.window!!.decorView
            val frame = android.graphics.Rect().also { decor.getWindowVisibleDisplayFrame(it) }
            val decorLoc = IntArray(2).also { decor.getLocationOnScreen(it) }
            for (id in listOf(R.id.btn_logs_filter_reset, R.id.btn_logs_filter_cancel, R.id.btn_logs_filter_apply)) {
                val button = dialog.findViewById<View>(id)!!
                assertTrue("shown $id", button.isShown)
                assertTrue("height ${button.height}px for $id", button.height >= (48 * density - 1))
                val loc = IntArray(2).also { button.getLocationOnScreen(it) }
                assertTrue("inside window $id", loc[1] >= decorLoc[1] && loc[1] + button.height <= decorLoc[1] + decor.height)
                assertTrue("on screen $id", loc[1] + button.height <= frame.bottom)
                assertTrue("enabled/clickable $id", button.isEnabled && button.isClickable)
            }
            assertTrue(dialog.findViewById<View>(android.R.id.button1)?.isShown != true)
        }
    }

    @Test fun cancelBackAndOutsideKeepTheAppliedFilter_applyAndResetChangeIt() {
        // A foreground-service heads-up can cover the toolbar and intercept Espresso's tap.
        val previousHeadsUp = android.provider.Settings.Global.getString(ins.targetContext.contentResolver, "heads_up_notifications_enabled") ?: "1"
        ins.uiAutomation.executeShellCommand("settings put global heads_up_notifications_enabled 0").close()
        ins.uiAutomation.executeShellCommand("cmd statusbar collapse").close()
        val main = ins.startActivitySync(Intent(ins.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        try {
            val fragment = logsFragment(main)
            val before = filter(fragment)

            // Cancel discards edits.
            var dialog = openFilter(fragment)
            assertActionsVisible(dialog)
            onView(withId(R.id.et_from)).inRoot(isDialog()).perform(replaceText("local-fixture"))
            onView(withId(R.id.btn_logs_filter_cancel)).inRoot(isDialog()).perform(click())
            waitUntil("cancel dismissed") { !dialog.isShowing }
            assertEquals(before, filter(fragment))

            // System Back dismisses and discards edits.
            dialog = openFilter(fragment)
            onView(withId(R.id.et_content)).inRoot(isDialog()).perform(replaceText("local-fixture"))
            Espresso.closeSoftKeyboard()
            Espresso.pressBack()
            waitUntil("back dismissed") { !dialog.isShowing }
            assertEquals(before, filter(fragment))

            // Apply stores the selection; reopening shows it; Reset clears it.
            dialog = openFilter(fragment)
            ins.runOnMainSync { assertEquals("", dialog.findViewById<EditText>(R.id.et_content)!!.text.toString()) }
            onView(withId(R.id.et_from)).inRoot(isDialog()).perform(replaceText("local-fixture"))
            ins.runOnMainSync { dialog.findViewById<View>(R.id.scb_forward_status_0)!!.performClick() }
            onView(withId(R.id.btn_logs_filter_apply)).inRoot(isDialog()).perform(click())
            waitUntil("apply dismissed") { !dialog.isShowing }
            assertEquals("local-fixture", filter(fragment)["from"])
            assertEquals(listOf(0), filter(fragment)["forward_status"])
            dialog = openFilter(fragment)
            ins.runOnMainSync { assertEquals("local-fixture", dialog.findViewById<EditText>(R.id.et_from)!!.text.toString()) }
            onView(withId(R.id.btn_logs_filter_reset)).inRoot(isDialog()).perform(click())
            waitUntil("reset dismissed") { !dialog.isShowing }
            assertTrue(filter(fragment).isEmpty())

            // A tap outside the card (in the dialog window's top inset) dismisses and discards edits.
            dialog = openFilter(fragment)
            onView(withId(R.id.et_from)).inRoot(isDialog()).perform(replaceText("local-fixture"))
            Espresso.closeSoftKeyboard()
            onView(isRoot()).inRoot(isDialog()).perform(GeneralClickAction(Tap.SINGLE, { view ->
                val location = IntArray(2).also { view.getLocationOnScreen(it) }
                floatArrayOf(location[0] + view.width / 2f, location[1] + 2f)
            }, Press.FINGER, InputDevice.SOURCE_UNKNOWN, MotionEvent.BUTTON_PRIMARY))
            waitUntil("outside tap dismissed") { !dialog.isShowing }
            assertTrue(filter(fragment).isEmpty())
        } finally {
            ins.runOnMainSync { main.finish() }
            ins.uiAutomation.executeShellCommand("settings put global heads_up_notifications_enabled $previousHeadsUp").close()
        }
    }

    /**
     * Font scale 1.3 in portrait and in a short landscape window: inflate the content with the dialog theme and
     * measure it under the height the dialog would leave it. The action row keeps its natural height; the form
     * shrinks and scrolls.
     */
    @Test fun actionsKeepFullHeightAtLargeFontAndLandscape() {
        val base = ins.targetContext
        for ((widthDp, availableDp) in listOf(312 to 520, 592 to 150, 240 to 300)) for (night in listOf(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES)) {
            ins.runOnMainSync {
                val configuration = Configuration(base.resources.configuration).apply {
                    fontScale = 1.3f
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
                }
                val themed = ContextThemeWrapper(base.createConfigurationContext(configuration), R.style.AppTheme)
                val dialogContext = com.google.android.material.dialog.MaterialAlertDialogBuilder(themed).context
                val density = dialogContext.resources.displayMetrics.density
                val root = LayoutInflater.from(dialogContext).inflate(R.layout.dialog_logs_filter, FrameLayout(dialogContext), false)
                // Inflated as is: every optional section visible, i.e. taller than any real tab.
                val width = (widthDp * density).toInt()
                val available = (availableDp * density).toInt()
                root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(available, View.MeasureSpec.AT_MOST))
                root.layout(0, 0, root.measuredWidth, root.measuredHeight)
                val bar = root.findViewById<LogsFilterActionBar>(R.id.logs_filter_actions)
                val scroll = root.findViewById<View>(R.id.logs_filter_scroll)
                assertTrue("root fits $widthDp/$availableDp", root.measuredHeight <= available)
                assertEquals("bar at bottom $widthDp/$availableDp", root.measuredHeight, bar.bottom)
                for (id in listOf(R.id.btn_logs_filter_reset, R.id.btn_logs_filter_cancel, R.id.btn_logs_filter_apply)) {
                    val button = root.findViewById<View>(id)
                    assertTrue("button height ${button.height} at $widthDp/$availableDp", button.height >= (48 * density - 1))
                    assertTrue("button inside bar", button.top >= 0 && button.bottom <= bar.height && button.left >= 0 && button.right <= bar.width)
                }
                assertTrue("form scrolls when short", scroll.height < (scroll as androidx.core.widget.NestedScrollView).getChildAt(0).height || availableDp >= 520)
                if (widthDp == 240) assertNotNull("narrow window stacks", bar.takeIf { it.isStacked })
                if (widthDp == 592) assertFalse("wide window keeps one row", bar.isStacked)
            }
        }
    }
}
