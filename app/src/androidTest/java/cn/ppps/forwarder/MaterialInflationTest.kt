package cn.ppps.forwarder

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.EditText
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import cn.ppps.forwarder.utils.ItemRows

/** Device-side inflation: catches unresolved attributes and layout params missed by JVM tests. */
class MaterialInflationTest {
    @Test fun allApplicationLayoutsInflateInLightAndDark() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val failures = mutableListOf<String>()
        val names = R.layout::class.java.fields.map { it.name }.filter {
            it.startsWith("fragment_") || it.startsWith("dialog_") || it.startsWith("adapter_") || it.startsWith("item_") || it == "activity_main"
        }
        for (locale in listOf("en", "nl")) for (width in listOf(360, 800)) for (night in listOf(android.content.res.Configuration.UI_MODE_NIGHT_NO, android.content.res.Configuration.UI_MODE_NIGHT_YES)) {
            instrumentation.runOnMainSync {
                val configuration = android.content.res.Configuration(instrumentation.targetContext.resources.configuration)
                configuration.setLocale(java.util.Locale(locale))
                configuration.screenWidthDp = width
                configuration.smallestScreenWidthDp = width
                configuration.uiMode = (configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or night
                val context = ContextThemeWrapper(instrumentation.targetContext.createConfigurationContext(configuration), R.style.AppTheme)
                for (name in names) {
                    try {
                        val id = R.layout::class.java.getField(name).getInt(null)
                        LayoutInflater.from(context).inflate(id, LinearLayout(context), false)
                    } catch (failure: Throwable) {
                        var cause = failure
                        while (cause.cause != null) cause = cause.cause!!
                        failures.add("$locale/$width/$night/$name: ${cause.javaClass.simpleName}: ${cause.message}")
                    }
                }
            }
        }
        println("Inflated ${names.size} layouts x 2 locales x 2 widths x 2 themes; failures=${failures.size}")
        assertTrue("${names.size} layouts x 2 locales x 2 widths x 2 themes; failures:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun mainNavigationAndReachableEditorsOpen() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val main = instrumentation.startActivitySync(android.content.Intent(context, cn.ppps.forwarder.activity.MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) as cn.ppps.forwarder.activity.MainActivity
        val classes = listOf(
            cn.ppps.forwarder.fragment.SettingsFragment::class.java,
            cn.ppps.forwarder.fragment.RulesEditFragment::class.java,
            cn.ppps.forwarder.fragment.TasksEditFragment::class.java,
            cn.ppps.forwarder.fragment.AboutFragment::class.java,
            cn.ppps.forwarder.fragment.ServerFragment::class.java,
            cn.ppps.forwarder.fragment.ClientFragment::class.java,
            cn.ppps.forwarder.fragment.senders.WebhookFragment::class.java,
            cn.ppps.forwarder.fragment.senders.EmailFragment::class.java,
            cn.ppps.forwarder.fragment.senders.TelegramFragment::class.java,
            cn.ppps.forwarder.fragment.senders.GotifyFragment::class.java,
            cn.ppps.forwarder.fragment.senders.SmsFragment::class.java,
            cn.ppps.forwarder.fragment.senders.BarkFragment::class.java,
            cn.ppps.forwarder.fragment.senders.SocketFragment::class.java,
            cn.ppps.forwarder.fragment.senders.UrlSchemeFragment::class.java,
            cn.ppps.forwarder.fragment.action.WolFragment::class.java,
            cn.ppps.forwarder.fragment.action.CleanerFragment::class.java,
            cn.ppps.forwarder.fragment.action.AlarmFragment::class.java,
            cn.ppps.forwarder.fragment.action.RuleFragment::class.java,
            cn.ppps.forwarder.fragment.action.SettingsFragment::class.java,
            cn.ppps.forwarder.fragment.action.ResendFragment::class.java,
            cn.ppps.forwarder.fragment.action.TaskActionFragment::class.java,
            cn.ppps.forwarder.fragment.action.HttpServerFragment::class.java,
            cn.ppps.forwarder.fragment.action.SendSmsFragment::class.java,
            cn.ppps.forwarder.fragment.action.SenderFragment::class.java,
            cn.ppps.forwarder.fragment.action.NotificationFragment::class.java,
            cn.ppps.forwarder.fragment.condition.LockScreenFragment::class.java,
            cn.ppps.forwarder.fragment.condition.LeaveAddressFragment::class.java,
            cn.ppps.forwarder.fragment.condition.MsgFragment::class.java,
            cn.ppps.forwarder.fragment.condition.BatteryFragment::class.java,
            cn.ppps.forwarder.fragment.condition.BluetoothFragment::class.java,
            cn.ppps.forwarder.fragment.condition.NetworkFragment::class.java,
            cn.ppps.forwarder.fragment.condition.SimFragment::class.java,
            cn.ppps.forwarder.fragment.condition.CronFragment::class.java,
            cn.ppps.forwarder.fragment.condition.ChargeFragment::class.java,
            cn.ppps.forwarder.fragment.condition.ToAddressFragment::class.java
        )
        try {
            for (page in classes) {
                instrumentation.runOnMainSync {
                    if (page == cn.ppps.forwarder.fragment.RulesEditFragment::class.java) {
                        com.xuexiang.xpage.core.PageOption.to(page).putString(cn.ppps.forwarder.utils.KEY_RULE_TYPE, "sms").setNewActivity(true).open(main)
                    } else if (page == cn.ppps.forwarder.fragment.condition.MsgFragment::class.java) {
                        com.xuexiang.xpage.core.PageOption.to(page).putString(cn.ppps.forwarder.utils.KEY_EVENT_PARAMS_CONDITION, "sms").setNewActivity(true).open(main)
                    } else main.openNewPage(page)
                }
                instrumentation.waitForIdleSync()
                var opened: android.app.Activity? = null
                for (attempt in 0 until 30) {
                    instrumentation.runOnMainSync {
                        opened = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).firstOrNull { activity ->
                            activity !== main && !activity.isFinishing &&
                                (activity as? androidx.fragment.app.FragmentActivity)?.supportFragmentManager?.fragments?.any { page.isInstance(it) && it.isResumed && it.isVisible } == true
                        }
                    }
                    if (opened != null) break
                    Thread.sleep(100)
                }
                assertNotNull("No resumed activity for ${page.simpleName}", opened)
                instrumentation.runOnMainSync {
                    val activity = opened!!
                    assertTrue("Opened ${page.simpleName}", activity !== main)
                    assertNotNull(activity.window.decorView)
                    val fragments = (activity as androidx.fragment.app.FragmentActivity).supportFragmentManager.fragments
                    val requested = fragments.firstOrNull { page.isInstance(it) && it.isResumed && it.isVisible }
                    assertNotNull("Requested page ${page.name} not active; attached=${fragments.map { it.javaClass.name }}", requested)
                    assertTrue("Requested screen must be shown", requested!!.requireView().isShown)
                    activity.finish()
                }
                instrumentation.waitForIdleSync()
            }
        } finally { instrumentation.runOnMainSync { main.finish() } }
    }

    @Test fun materialDialogsPreserveCallbacksAndPrivacyContent() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(android.content.Intent(instrumentation.targetContext, cn.ppps.forwarder.activity.MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.runOnMainSync {
            var clicked = false
            val dialog = cn.ppps.forwarder.widget.material.MaterialDialog.Builder(activity)
                .title(R.string.rule_tester).customView(LinearLayout(activity), true)
                .autoDismiss(false).positiveText(R.string.test).negativeText(R.string.cancel)
                .onPositive { _, _ -> clicked = true }.build()
            dialog.show()
            dialog.alert.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).performClick()
            assertTrue(clicked)
            assertTrue("Validation/test dialogs must stay open", dialog.isShowing)
            dialog.dismiss()
            val privacy = cn.ppps.forwarder.widget.material.MaterialDialog.Builder(activity).positiveText(R.string.confirm).build()
            privacy.setContent("Local privacy policy")
            assertEquals("Local privacy policy", privacy.contentView?.text.toString())
            privacy.show(); privacy.dismiss()
            activity.finish()
        }
    }

    @Test fun itemRowsValidateBeforeUpdatingBackingField() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, R.style.AppTheme)
            val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val source = EditText(context).apply { id = R.id.et_phone_numbers; setText("+31612345678;+31687654321") }
            root.addView(source)
            val rows = ItemRows(source, ";")
            assertTrue(rows.validate())
            val rowContainer = (0 until rows.view.childCount).map { rows.view.getChildAt(it) }.filterIsInstance<LinearLayout>().single()
            val firstRow = rowContainer.getChildAt(0) as LinearLayout
            val controls = firstRow.getChildAt(0) as LinearLayout
            val field = (controls.getChildAt(0) as com.google.android.material.textfield.TextInputLayout).editText!!
            field.setText("invalid-number")
            assertFalse(rows.validate())
            assertEquals("+31612345678;+31687654321", source.text.toString())
            field.setText("+31699999999")
            assertTrue(rows.validate())
            assertEquals("+31699999999;+31687654321", source.text.toString())
            assertSame(rows, source.getTag(R.id.item_rows_controller))
            field.setText("unfinished")
            val state = android.util.SparseArray<android.os.Parcelable>()
            rows.view.saveHierarchyState(state)
            val replacement = LinearLayout(context)
            val backing = EditText(context).apply { id = R.id.et_phone_numbers; setText("+31699999999;+31687654321") }
            replacement.addView(backing)
            val restored = ItemRows(backing, ";")
            restored.view.restoreHierarchyState(state)
            assertFalse("Invalid draft must survive recreation", restored.validate())
            assertEquals("Invalid draft must not autosave", "+31699999999;+31687654321", backing.text.toString())
        }
    }
}
