package cn.ppps.forwarder

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import cn.ppps.forwarder.activity.MainActivity
import cn.ppps.forwarder.fragment.RulesEditFragment
import cn.ppps.forwarder.utils.ItemRows
import org.junit.Assert.*
import org.junit.Test

/** Only opens the local quiet-hours picker. Never presses transport Test or saves a rule. */
class RulePickerDraftTest {
    @Test fun quietHoursPickerWorksWithAnInvalidReplacementDraft() {
        val ins = InstrumentationRegistry.getInstrumentation()
        val main = ins.startActivitySync(Intent(ins.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        ins.runOnMainSync {
            com.xuexiang.xpage.core.PageOption.to(RulesEditFragment::class.java)
                .putString(cn.ppps.forwarder.utils.KEY_RULE_TYPE, "sms").setNewActivity(true).open(main)
        }
        ins.waitForIdleSync()
        var editor: RulesEditFragment? = null
        for (attempt in 0 until 30) {
            ins.runOnMainSync {
                editor = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                    .filterIsInstance<androidx.fragment.app.FragmentActivity>()
                    .flatMap { it.supportFragmentManager.fragments }.filterIsInstance<RulesEditFragment>()
                    .firstOrNull { it.isResumed && it.isVisible }
            }
            if (editor != null) break
            Thread.sleep(100)
        }
        try {
            assertNotNull(editor)
            ins.runOnMainSync {
                val b = editor!!.binding!!
                b.sbRegexReplace.isChecked = true
                val rows = b.etRegexReplace.getTag(R.id.item_rows_controller) as ItemRows
                fun walk(v: View): List<View> = listOf(v) + if(v is ViewGroup) (0 until v.childCount).flatMap { walk(v.getChildAt(it)) } else emptyList()
                walk(rows.view).filterIsInstance<Button>().first().performClick()
                assertFalse("Draft must actually be invalid", rows.validate(false))
                editor!!.onClick(b.btnSilentPeriod)
            }
            ins.waitForIdleSync()
            Espresso.onView(withText(R.string.select_time_period)).check(matches(isDisplayed()))
            Espresso.pressBack()
        } finally {
            ins.runOnMainSync { editor?.activity?.finish(); main.finish() }
        }
    }
}
