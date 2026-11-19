package cn.ppps.forwarder

import android.app.Activity
import android.content.Intent
import android.view.View
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import cn.ppps.forwarder.activity.MainActivity
import cn.ppps.forwarder.fragment.AboutFragment
import cn.ppps.forwarder.fragment.senders.WebhookFragment
import cn.ppps.forwarder.utils.SettingUtils
import cn.ppps.forwarder.utils.update.UpdateManager
import cn.ppps.forwarder.utils.update.UpdateResult
import org.junit.Assert.*
import org.junit.Test

/** About screen updater states and Webhook help dialogs. Local only: never presses Check now or Test. */
class UpdaterScreenTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private fun settle() { ins.waitForIdleSync(); Thread.sleep(500) }
    private fun main() = ins.startActivitySync(Intent(ins.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
    private fun <T : androidx.fragment.app.Fragment> page(main: MainActivity, klass: Class<T>): Pair<Activity, T> {
        @Suppress("UNCHECKED_CAST")
        ins.runOnMainSync { main.openNewPage(klass as Class<out com.xuexiang.xpage.base.XPageFragment>) }
        repeat(80) {
            var found: Pair<Activity, T>? = null
            ins.runOnMainSync {
                for (a in androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)) {
                    val f = (a as? androidx.fragment.app.FragmentActivity)?.supportFragmentManager?.fragments?.filterIsInstance(klass)?.firstOrNull { it.isVisible && it.isResumed }
                    if (f != null) found = a to f
                }
            }
            found?.let { settle(); return it }
            Thread.sleep(100)
        }
        error("${klass.simpleName} not visible")
    }
    private fun shot(name: String) {
        settle()
        val dir = java.io.File(ins.targetContext.getExternalFilesDir(null), "updater").apply { mkdirs() }
        java.io.FileOutputStream(java.io.File(dir, "$name.png")).use { ins.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun aboutShowsUpdaterStates() {
        val ctx = ins.targetContext
        val prefs = UpdateManager.prefs(ctx)
        val saved = prefs.all.toMap(); val savedAuto = SettingUtils.autoCheckUpdate
        val main = main()
        try {
            prefs.edit().clear().commit()
            SettingUtils.autoCheckUpdate = true
            val (activity, about) = page(main, AboutFragment::class.java)
            val b = about.binding!!
            ins.runOnMainSync {
                assertTrue(b.scbAutoCheckUpdate.isEnabled); assertTrue(b.scbAutoCheckUpdate.isChecked)
                assertTrue("Check now enabled", b.btnUpdate.isEnabled)
                assertEquals(activity.getString(R.string.updater_never_checked), b.tvUpdateLastCheck.text.toString())
                assertEquals(View.GONE, b.menuJoinPreviewProgram.visibility)
            }
            fun state(kind: String, arg: String, expected: String) {
                prefs.edit().putLong(UpdateManager.KEY_LAST_CHECK, System.currentTimeMillis()).putString(UpdateManager.KEY_RESULT, kind).putString(UpdateManager.KEY_RESULT_ARG, arg).commit()
                settle()
                ins.runOnMainSync {
                    assertEquals(expected, b.tvUpdateStatus.text.toString())
                    assertTrue(b.tvUpdateLastCheck.text.toString().startsWith(activity.getString(R.string.updater_last_check, "").trim()))
                }
            }
            state(UpdateResult.UP_TO_DATE, "", activity.getString(R.string.updater_up_to_date))
            shot("about-up-to-date")
            state(UpdateResult.AVAILABLE, "3.5.1.test", activity.getString(R.string.updater_available, "3.5.1.test"))
            state(UpdateResult.SHA256_MISMATCH, "3.5.1.test", activity.getString(R.string.updater_error_sha256))
            shot("about-error-sha256")
            state(UpdateResult.CERT_MISMATCH, "", activity.getString(R.string.updater_error_certificate))
            state(UpdateResult.REDIRECT_REFUSED, "evil.example", activity.getString(R.string.updater_error_redirect, "evil.example"))
            // Off switch: unticking stores false and keeps Check now usable.
            ins.runOnMainSync { b.scbAutoCheckUpdate.performClick() }; settle()
            ins.runOnMainSync { assertFalse(SettingUtils.autoCheckUpdate); assertFalse(b.scbAutoCheckUpdate.isChecked); assertTrue(b.btnUpdate.isEnabled) }
            shot("about-auto-off")
            ins.runOnMainSync { b.scbAutoCheckUpdate.performClick() }; settle()
            ins.runOnMainSync { assertTrue(SettingUtils.autoCheckUpdate) }
            ins.runOnMainSync { activity.finish() }
        } finally {
            ins.runOnMainSync { main.finish() }
            prefs.edit().clear().apply { saved.forEach { (k, v) -> when (v) { is String -> putString(k, v); is Long -> putLong(k, v); is Boolean -> putBoolean(k, v); is Int -> putInt(k, v) } } }.commit()
            SettingUtils.autoCheckUpdate = savedAuto
        }
    }

    @Test fun webhookHelpIconsOpenDialogs() {
        val main = main()
        try {
            val (activity, webhook) = page(main, WebhookFragment::class.java)
            val b = webhook.binding!!
            ins.runOnMainSync { b.btnAdvanced.performClick() }; settle()
            val icons = listOf(b.btnHelpParams to R.string.webhook_params, b.btnHelpSecret to R.string.form_secret_label,
                b.btnHelpResponse to R.string.form_response_label, b.btnHelpHeaders to R.string.headers)
            for ((icon, title) in icons) {
                ins.runOnMainSync {
                    assertEquals(activity.getString(R.string.form_help_content_description, activity.getString(title)), icon.contentDescription.toString())
                    assertTrue("48dp target", icon.width >= (48 * activity.resources.displayMetrics.density).toInt() - 1)
                    icon.performClick()
                }
                settle()
                onView(withText(activity.getString(title))).inRoot(isDialog()).check(matches(isDisplayed()))
                onView(withText(R.string.m3_close)).inRoot(isDialog()).perform(androidx.test.espresso.action.ViewActions.click())
                settle()
            }
            ins.runOnMainSync { activity.finish() }
        } finally { ins.runOnMainSync { main.finish() } }
    }
}
