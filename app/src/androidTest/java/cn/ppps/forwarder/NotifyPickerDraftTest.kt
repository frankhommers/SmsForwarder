package cn.ppps.forwarder

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import cn.ppps.forwarder.activity.MainActivity
import cn.ppps.forwarder.fragment.action.NotificationFragment
import cn.ppps.forwarder.utils.ItemRows
import org.junit.Assert.*
import org.junit.Test

/** Only opens the local quiet-hours picker. Never presses transport Test or saves a rule. */
class NotifyPickerDraftTest {
    private fun shot(name: String) {
        val ins=InstrumentationRegistry.getInstrumentation()
        ins.waitForIdleSync()
        val dir=java.io.File(ins.targetContext.getExternalFilesDir(null), "notify-review").apply{mkdirs()}
        java.io.FileOutputStream(java.io.File(dir,name+".png")).use { ins.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun quietHoursPickerWorksWithAnInvalidReplacementDraft() {
        val ins = InstrumentationRegistry.getInstrumentation()
        val main = ins.startActivitySync(Intent(ins.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        ins.runOnMainSync {
            com.xuexiang.xpage.core.PageOption.to(NotificationFragment::class.java)
                .setNewActivity(true).open(main)
        }
        ins.waitForIdleSync()
        var editor: NotificationFragment? = null
        for (attempt in 0 until 30) {
            ins.runOnMainSync {
                editor = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                    .filterIsInstance<androidx.fragment.app.FragmentActivity>()
                    .flatMap { it.supportFragmentManager.fragments }.filterIsInstance<NotificationFragment>()
                    .firstOrNull { it.isResumed && it.isVisible }
            }
            if (editor != null) break
            Thread.sleep(100)
        }
        try {
            assertNotNull(editor)
            // Window readiness: the editor activity window must own input focus before a field can be served by the
            // IME. Measured cause of the flake: the add-row click ran ~0.3 s after RESUMED, during the open
            // transition, and its implicit showSoftInput() was dropped ("Ignoring showSoftInput() ... is not served").
            fun waitFor(label: String, condition: () -> Boolean) {
                for (attempt in 0 until 50) { var ok = false; ins.runOnMainSync { ok = condition() }; if (ok) return; Thread.sleep(100) }
                fail("Timed out: $label")
            }
            waitFor("editor window focused") { editor!!.requireActivity().hasWindowFocus() }
            ins.waitForIdleSync()
            var rows: ItemRows? = null
            ins.runOnMainSync {
                val b = editor!!.binding!!
                b.sbRegexReplace.isChecked = true
                rows = b.etRegexReplace.getTag(R.id.item_rows_controller) as ItemRows
            }
            ins.waitForIdleSync()
            fun walk(v: View): List<View> = listOf(v) + if(v is ViewGroup) (0 until v.childCount).flatMap { walk(v.getChildAt(it)) } else emptyList()
            // Add an empty replacement row with a real tap, as a user would.
            var addButton: View? = null
            ins.runOnMainSync { addButton = walk(rows!!.view).filterIsInstance<Button>().first() }
            Espresso.onView(org.hamcrest.Matchers.sameInstance<View>(addButton)).perform(scrollTo(), click())
            ins.runOnMainSync { assertFalse("Draft must actually be invalid", rows!!.validate(false)) }
            // The new row's pattern field (Add focuses it) must hold focus and be the IME's served view.
            var input: android.widget.EditText? = null
            waitFor("new row focused and served by the IME") {
                input = walk(rows!!.view).filterIsInstance<android.widget.EditText>().firstOrNull { it.hasFocus() }
                input != null && (input!!.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).isActive(input!!)
            }
            ins.runOnMainSync {
                val b = editor!!.binding!!
                b.btnSilentPeriod.requestRectangleOnScreen(android.graphics.Rect(0,0,b.btnSilentPeriod.width,b.btnSilentPeriod.height),true)
            }
            fun imeVisible(): Boolean {
                var visible = false
                ins.runOnMainSync {
                    visible = androidx.core.view.ViewCompat.getRootWindowInsets(editor!!.binding!!.root)
                        ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
                }
                return visible
            }
            ins.waitForIdleSync()
            for (attempt in 0 until 30) { if (imeVisible()) break; Thread.sleep(100) }
            if (!imeVisible()) {
                // Explicit keyboard request through the user's action: tap the focused, served field.
                android.util.Log.i("NotifyPickerDraftTest", "implicit keyboard after Add not visible within 3 s; tapping the new field")
                Espresso.onView(org.hamcrest.Matchers.sameInstance<View>(input)).perform(scrollTo(), click())
                for (attempt in 0 until 30) { if (imeVisible()) break; Thread.sleep(100) }
                ins.runOnMainSync {
                    val b = editor!!.binding!!
                    b.btnSilentPeriod.requestRectangleOnScreen(android.graphics.Rect(0,0,b.btnSilentPeriod.width,b.btnSilentPeriod.height),true)
                }
                ins.waitForIdleSync()
            }
            assertTrue("Precondition: keyboard is actually open", imeVisible())
            shot("draft-before-picker")
            ins.runOnMainSync {
                editor!!.onClick(editor!!.binding!!.btnSilentPeriod)
            }
            ins.waitForIdleSync()
            Espresso.onView(withText(R.string.select_time_period)).check(matches(isDisplayed()))
            for (attempt in 0 until 30) { if (!imeVisible()) break; Thread.sleep(100) }
            assertFalse("Picker hides the previous field keyboard", imeVisible())
            shot("quiet-hours-picker")
            Espresso.pressBack()
        } finally {
            ins.runOnMainSync { editor?.activity?.finish(); main.finish() }
        }
    }
}
