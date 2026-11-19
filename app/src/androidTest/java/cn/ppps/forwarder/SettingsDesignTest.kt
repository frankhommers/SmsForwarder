package cn.ppps.forwarder

import android.app.Activity
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import cn.ppps.forwarder.activity.MainActivity
import cn.ppps.forwarder.fragment.SettingsFragment
import cn.ppps.forwarder.fragment.senders.SocketFragment
import cn.ppps.forwarder.utils.SettingUtils
import cn.ppps.forwarder.widget.material.TargetDropdown
import org.junit.Test
import org.junit.Assert.*

/** Real MainActivity shell; synthetic local preferences, no sends, services or scans. */
class SettingsDesignTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val args get() = InstrumentationRegistry.getArguments()
    private fun walk(v: View): List<View> = listOf(v) + if(v is ViewGroup) (0 until v.childCount).flatMap { walk(v.getChildAt(it)) } else emptyList()
    private fun settle() { ins.waitForIdleSync(); Thread.sleep(550) }
    private fun shot(name: String) {
        settle()
        val dir = java.io.File(ins.targetContext.getExternalFilesDir(null), "design/" + args.getString("folder", "capture")).apply { mkdirs() }
        java.io.FileOutputStream(java.io.File(dir, "$name.png")).use { ins.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun reveal(view: View) { ins.runOnMainSync { view.requestRectangleOnScreen(android.graphics.Rect(0,0,view.width,view.height),true) }; settle() }
    private fun setup(preset: () -> Unit = {}): MainActivity {
        com.hjq.language.MultiLanguages.setAppLanguage(ins.targetContext, java.util.Locale(args.getString("language", "en")))
        SettingUtils.enableSms = false; SettingUtils.enablePhone = false; SettingUtils.enableAppNotify = false
        SettingUtils.enableBluetooth = false; SettingUtils.enableLocation = false; SettingUtils.enableCactus = false
        SettingUtils.enableLoadAppList = false; SettingUtils.enableSmsCommand = false
        SettingUtils.enablePureClientMode = false; SettingUtils.enablePureTaskMode = false
        SettingUtils.enableCallType1 = false; SettingUtils.enableCallType2 = false; SettingUtils.enableCallType3 = true
        SettingUtils.enableCallType4 = false; SettingUtils.enableCallType5 = false; SettingUtils.enableCallType6 = false
        SettingUtils.smsCommandSafePhone = "+31000000001,+31000000002"
        SettingUtils.cancelExtraAppNotify = "org.example.fixture\norg.example.second"
        SettingUtils.appNotifyBlacklist = "Fixture keyword\n/fixture.*/"
        SettingUtils.extraDeviceMark = "Fixture device"
        SettingUtils.silentPeriodStart=0;SettingUtils.silentPeriodEnd=0
        SettingUtils.duplicateMessagesLimits = 23
        SettingUtils.requestRetryTimes = 2; SettingUtils.requestDelayTime = 7; SettingUtils.requestTimeout = 19
        SettingUtils.enableSmsTemplate = true; SettingUtils.smsTemplate = "[from]\n[content]"
        preset()
        val main = ins.startActivitySync(Intent(ins.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        settle()
        ins.runOnMainSync { (main.findViewById<View>(R.id.tabs) as com.google.android.material.navigation.NavigationBarView).selectedItemId = 4 }
        settle()
        return main
    }
    private fun socket(main: MainActivity): Pair<Activity,SocketFragment> {
        ins.runOnMainSync { main.openNewPage(SocketFragment::class.java) }; settle()
        var a: Activity? = null; var f: SocketFragment? = null
        repeat(30) {
            ins.runOnMainSync {
                a = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).firstOrNull { it !== main }
                f = (a as? androidx.fragment.app.FragmentActivity)?.supportFragmentManager?.fragments?.filterIsInstance<SocketFragment>()?.firstOrNull { it.isVisible }
            }
            if (f != null) return a!! to f!!
            Thread.sleep(100)
        }
        error("Socket not visible")
    }
    @Test fun detailsPreservePreferencesAndReturnSummaries() {
        val main = setup()
        try {
            val fragment = main.supportFragmentManager.fragments.filterIsInstance<SettingsFragment>().first { it.isVisible }
            val b = fragment.binding!!
            val nav = fragment.settingsNavigation!!
            val before = cn.ppps.forwarder.utils.SharedPreference.preference.all.toMap()
            shot("settings-overview-top")
            assertEquals(24, nav.entries.size)
            assertEquals(main.getString(R.string.settings_duplicate_interval,23),nav.entries.first { it.key=="duplicates" }.summary())
            assertEquals(main.getString(R.string.settings_off) + " · " + main.getString(R.string.settings_saved_events,main.getString(R.string.missed_call)),nav.entries.first { it.key=="calls" }.summary())
            for (entry in nav.entries) {
                val row = walk(b.settingsOverview).first { it.tag == "settings-row-${entry.key}" }
                reveal(row)
                ins.runOnMainSync { row.performClick() }
                settle()
                ins.runOnMainSync {
                    assertEquals(entry.key,nav.current)
                    assertTrue(b.settingsDetailScroll.isShown)
                    assertTrue(main.findViewById<View>(R.id.tabs).isShown)
                    assertEquals(1,(0 until b.settingsDetails.childCount).count { b.settingsDetails.getChildAt(it).visibility == View.VISIBLE })
                }
                shot("detail-${entry.key}-top")
                ins.runOnMainSync { b.settingsDetailScroll.fullScroll(View.FOCUS_DOWN) }
                shot("detail-${entry.key}-bottom")
                androidx.test.espresso.Espresso.pressBack()
                settle()
                assertNull(nav.current)
            }
            val after = cn.ppps.forwarder.utils.SharedPreference.preference.all.toMap()
            assertEquals("Opening every detail must preserve all preferences",before,after)
            ins.runOnMainSync { nav.open("calls") };settle()
            assertFalse("Inactive dependent event is disabled",b.scbCallType1.isEnabled)
            assertFalse(SettingUtils.enableCallType1)
            assertFalse(SettingUtils.enablePhone)
            assertTrue("Saved event remains checked",b.scbCallType3.isChecked)
            shot("calls-disabled-retained")
            ins.runOnMainSync { nav.overview();nav.open("device");b.etExtraDeviceMark.setText("Reopened fixture") }
            assertEquals("Reopened fixture",SettingUtils.extraDeviceMark)
            ins.runOnMainSync { nav.overview() }
            assertEquals(SettingUtils.extraDeviceMark,nav.entries.first { it.key=="device" }.summary())
            ins.runOnMainSync { nav.open("device") }
            assertEquals("Reopened fixture",b.etExtraDeviceMark.text.toString())
            ins.runOnMainSync { nav.overview();nav.open("apps") }
            val controller = b.etAppList.getTag(R.id.item_rows_controller) as cn.ppps.forwarder.utils.ItemRows
            val field = walk(controller.view).filterIsInstance<EditText>().first()
            val original = SettingUtils.cancelExtraAppNotify
            ins.runOnMainSync { field.setText("invalid package") }
            assertEquals("Invalid list draft not autosaved",original,SettingUtils.cancelExtraAppNotify)
            reveal(field);shot("apps-invalid-row")
            ins.runOnMainSync { field.setText("org.example.changed") }
            assertTrue(SettingUtils.cancelExtraAppNotify.startsWith("org.example.changed"))
            shot("apps-saved-list")
            ins.runOnMainSync { nav.overview();nav.open("quiet");b.btnSilentPeriod.performClick() };settle()
            shot("quiet-hours-dialog")
            val savedStart=SettingUtils.silentPeriodStart;val savedEnd=SettingUtils.silentPeriodEnd
            fun chooseRange() {
                androidx.test.espresso.Espresso.onView(object : org.hamcrest.TypeSafeMatcher<View>() {
                    override fun describeTo(d: org.hamcrest.Description) { d.appendText("quiet-hours start") }
                    override fun matchesSafely(v: View)=v is TargetDropdown && v.hint.toString()==main.getString(R.string.m3_period_start)
                })
                    .perform(object : androidx.test.espresso.ViewAction {
                        override fun getDescription()="Choose local quiet-hours start"
                        override fun getConstraints()=androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom(TargetDropdown::class.java)
                        override fun perform(ui: androidx.test.espresso.UiController, view: View) { (view as TargetDropdown).selectedIndex=12 }
                    })
            }
            chooseRange()
            androidx.test.espresso.Espresso.pressBack();settle()
            assertEquals(savedStart,SettingUtils.silentPeriodStart);assertEquals(savedEnd,SettingUtils.silentPeriodEnd)
            ins.runOnMainSync { b.btnSilentPeriod.performClick() };settle()
            chooseRange()
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(android.R.id.button1)).perform(androidx.test.espresso.action.ViewActions.click())
            assertEquals(12,SettingUtils.silentPeriodStart);assertEquals(savedEnd,SettingUtils.silentPeriodEnd)
            assertEquals(b.tvSilentPeriod.text.toString(),nav.entries.first { it.key=="quiet" }.summary())
            ins.runOnMainSync { nav.overview();b.settingsOverviewScroll.scrollTo(0,0) }
            shot("settings-returned-summaries")
            ins.runOnMainSync { b.settingsOverviewScroll.fullScroll(View.FOCUS_DOWN) }
            shot("settings-overview-bottom")
            ins.runOnMainSync {
                val lastRow = b.settingsOverview.getChildAt(b.settingsOverview.childCount-1)
                val visible = android.graphics.Rect()
                assertTrue("Last overview row is reachable",lastRow.getGlobalVisibleRect(visible))
                assertEquals("Last overview row is fully visible after scrolling",lastRow.height,visible.height())
            }
            println("24 detail routes preserve all preferences; autosave/reopen, call summary, invalid package draft verified")
        } finally { ins.runOnMainSync { main.finish() } }
    }

    /** Regression: with every child option false, the master switch used to refuse to turn on while its children were disabled. */
    @Test fun masterSwitchesWorkFromAllFalseState() {
        val pkg = ins.targetContext.packageName
        // Grant up front so the master switch's own permission request completes without a system dialog.
        for (p in listOf(android.Manifest.permission.READ_PHONE_STATE, android.Manifest.permission.READ_PHONE_NUMBERS, android.Manifest.permission.READ_CALL_LOG, android.Manifest.permission.READ_CONTACTS))
            ins.uiAutomation.grantRuntimePermission(pkg, p)
        // All six call events and both app types false, as on a fresh install (setup() would pre-select one).
        val main = setup {
            SettingUtils.enableCallType1 = false; SettingUtils.enableCallType2 = false; SettingUtils.enableCallType3 = false
            SettingUtils.enableCallType4 = false; SettingUtils.enableCallType5 = false; SettingUtils.enableCallType6 = false
            SettingUtils.enablePhone = false
            SettingUtils.enableLoadAppList = false; SettingUtils.enableLoadUserAppList = false; SettingUtils.enableLoadSystemAppList = false
        }
        try {
            val fragment = main.supportFragmentManager.fragments.filterIsInstance<SettingsFragment>().first { it.isVisible }
            val b = fragment.binding!!
            val nav = fragment.settingsNavigation!!
            val calls = listOf(b.scbCallType1, b.scbCallType2, b.scbCallType3, b.scbCallType4, b.scbCallType5, b.scbCallType6)
            ins.runOnMainSync { nav.open("calls") }; settle()
            ins.runOnMainSync {
                assertFalse(b.sbEnablePhone.isChecked)
                calls.forEach { assertFalse("Dependent disabled while master off", it.isEnabled); assertFalse(it.isChecked) }
                b.sbEnablePhone.performClick()
            }
            settle()
            ins.runOnMainSync {
                assertTrue("Master stays on with no event selected", b.sbEnablePhone.isChecked)
                assertTrue(SettingUtils.enablePhone)
                calls.forEach { assertTrue("Dependent enabled while master on", it.isEnabled) }
                assertFalse("No event auto-selected", SettingUtils.enableCallType1 || SettingUtils.enableCallType2 || SettingUtils.enableCallType3 || SettingUtils.enableCallType4 || SettingUtils.enableCallType5 || SettingUtils.enableCallType6)
                b.scbCallType3.performClick()
            }
            settle()
            ins.runOnMainSync { assertTrue(b.scbCallType3.isChecked); assertTrue(SettingUtils.enableCallType3) }
            // Unchecking the last event keeps the master on and the boxes usable.
            ins.runOnMainSync { b.scbCallType3.performClick() }; settle()
            ins.runOnMainSync { assertFalse(SettingUtils.enableCallType3); assertTrue("Master unchanged after last event unchecked", b.sbEnablePhone.isChecked); assertTrue(SettingUtils.enablePhone); assertTrue(b.scbCallType3.isEnabled) }
            ins.runOnMainSync { b.scbCallType3.performClick(); b.sbEnablePhone.performClick() }; settle()
            ins.runOnMainSync {
                assertFalse(SettingUtils.enablePhone)
                calls.forEach { assertFalse("Dependent disabled again", it.isEnabled) }
                assertTrue("Stored value kept while master off", b.scbCallType3.isChecked); assertTrue(SettingUtils.enableCallType3)
            }
            shot("calls-all-false-regression")
            ins.runOnMainSync { nav.overview(); nav.open("apps") }; settle()
            ins.runOnMainSync {
                assertFalse(b.sbEnableLoadAppList.isChecked); assertFalse(b.scbLoadUserApp.isEnabled)
                b.sbEnableLoadAppList.performClick()
            }
            settle()
            ins.runOnMainSync {
                assertTrue("Installed-apps master stays on", b.sbEnableLoadAppList.isChecked); assertTrue(SettingUtils.enableLoadAppList)
                assertTrue(b.scbLoadUserApp.isEnabled); assertTrue(b.scbLoadSystemApp.isEnabled)
                assertFalse(SettingUtils.enableLoadUserAppList || SettingUtils.enableLoadSystemAppList)
                b.scbLoadUserApp.performClick()
            }
            settle()
            ins.runOnMainSync { assertTrue(SettingUtils.enableLoadUserAppList); b.scbLoadUserApp.performClick() }; settle()
            ins.runOnMainSync { assertTrue("Master unchanged after last app type unchecked", b.sbEnableLoadAppList.isChecked); assertTrue(b.scbLoadUserApp.isEnabled) }
            ins.runOnMainSync { b.scbLoadUserApp.performClick(); b.sbEnableLoadAppList.performClick() }; settle()
            ins.runOnMainSync { assertFalse(SettingUtils.enableLoadAppList); assertFalse(b.scbLoadUserApp.isEnabled); assertTrue(b.scbLoadUserApp.isChecked); assertTrue(SettingUtils.enableLoadUserAppList) }
            shot("apps-all-false-regression")
        } finally {
            ins.runOnMainSync { main.finish() }
            SettingUtils.enablePhone = false; SettingUtils.enableCallType3 = false
            SettingUtils.enableLoadAppList = false; SettingUtils.enableLoadUserAppList = false
        }
    }

    @Test fun socketModesAndRealCharsetDropdown() {
        val main=setup()
        val (activity, fragment)=socket(main)
        try {
            val b=fragment.binding!!
            ins.runOnMainSync {
                b.etName.setText("Local fixture");b.sbEnable.isChecked=false
                b.etAddress.setText("127.0.0.1");b.etPort.setText("1883")
                b.etUsername.setText("fixture-user");b.etPassword.setText("fixture-not-a-secret")
                b.etInMessageTopic.setText("fixture/in");b.etOutMessageTopic.setText("fixture/out")
            }
            shot("socket-mqtt-top")
            reveal(b.etUsername);shot("socket-mqtt-credentials")
            val dropdown=b.etInCharset
            reveal(dropdown)
            ins.runOnMainSync { (dropdown.editText as android.widget.AutoCompleteTextView).showDropDown() }
            shot("socket-open-charset")
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText("UTF-8"))
                .inRoot(androidx.test.espresso.matcher.RootMatchers.isPlatformPopup()).perform(androidx.test.espresso.action.ViewActions.click())
            assertEquals("UTF-8",dropdown.text.toString())
            val method=fragment.javaClass.getDeclaredMethod("checkSetting").apply { isAccessible=true }
            for (mode in listOf(R.id.rb_method_mqtt,R.id.rb_method_tcp,R.id.rb_method_udp)) {
                ins.runOnMainSync {
                    b.rgMethod.check(mode)
                    assertEquals(mode==R.id.rb_method_mqtt,b.layoutMqtt.visibility==View.VISIBLE)
                    val setting=method.invoke(fragment) as cn.ppps.forwarder.entity.setting.SocketSetting
                    assertEquals("fixture-user",setting.username)
                    assertEquals("UTF-8",setting.inCharset)
                    assertEquals("fixture/in",setting.inMessageTopic)
                }
                reveal(b.rgMethod);shot("socket-mode-$mode")
            }
            // Save a disabled local target, read back exact persisted JSON, then reopen it.
            ins.runOnMainSync { fragment.senderType=cn.ppps.forwarder.utils.TYPE_SOCKET;b.btnSave.performClick() }
            var saved: cn.ppps.forwarder.database.entity.Sender?=null
            repeat(40) {
                saved=cn.ppps.forwarder.core.Core.sender.getAll().blockingGet().firstOrNull { it.name=="Local fixture" }
                if(saved==null) Thread.sleep(100)
            }
            assertNotNull(saved)
            val stored=saved!!
            assertEquals(0,stored.status)
            val storedSetting=com.google.gson.Gson().fromJson(stored.jsonSetting,cn.ppps.forwarder.entity.setting.SocketSetting::class.java)
            assertEquals("UDP",storedSetting.method);assertEquals("fixture-user",storedSetting.username)
            assertEquals("UTF-8",storedSetting.inCharset)
            ins.runOnMainSync {
                com.xuexiang.xpage.core.PageOption.to(SocketFragment::class.java)
                    .putLong(cn.ppps.forwarder.utils.KEY_SENDER_ID,stored.id)
                    .putInt(cn.ppps.forwarder.utils.KEY_SENDER_TYPE,cn.ppps.forwarder.utils.TYPE_SOCKET)
                    .setNewActivity(true).open(main)
            }
            settle();Thread.sleep(700)
            var reopened: SocketFragment?=null
            ins.runOnMainSync {
                val active=androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).first { it!==main }
                reopened=(active as androidx.fragment.app.FragmentActivity).supportFragmentManager.fragments.filterIsInstance<SocketFragment>().first { it.isVisible }
                assertEquals("fixture-user",reopened!!.binding!!.etUsername.text.toString())
                assertEquals("UDP",(method.invoke(reopened) as cn.ppps.forwarder.entity.setting.SocketSetting).method)
            }
            shot("socket-saved-reopened")
            ins.runOnMainSync { reopened!!.requireActivity().finish() }
            cn.ppps.forwarder.core.Core.sender.delete(stored.id)
            println("MQTT/TCP/UDP groups, actual save/reopen and real charset selection verified; no transport invoked")
        } finally { ins.runOnMainSync { activity.finish();main.finish() } }
    }

    fun baselineShell() {
        val main = setup()
        try {
            shot("settings-overview")
            ins.runOnMainSync { walk(main.window.decorView).filterIsInstance<androidx.core.widget.NestedScrollView>().first { it.isShown }.scrollTo(0,600) }
            shot("settings-detail")
            val (activity, fragment) = socket(main)
            try {
                shot("socket-top")
                val dropdown = fragment.binding!!.etInCharset
                reveal(dropdown)
                ins.runOnMainSync { (dropdown.editText as android.widget.AutoCompleteTextView).showDropDown() }
                shot("socket-open-charset")
            } finally { ins.runOnMainSync { activity.finish() } }
        } finally { ins.runOnMainSync { main.finish() } }
    }
}
