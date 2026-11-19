package cn.ppps.forwarder

import android.app.Activity
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import cn.ppps.forwarder.activity.MainActivity
import cn.ppps.forwarder.core.BaseActivity
import cn.ppps.forwarder.fragment.SettingsFragment
import cn.ppps.forwarder.fragment.TasksEditFragment
import cn.ppps.forwarder.fragment.condition.CronFragment
import cn.ppps.forwarder.fragment.senders.SmsFragment
import cn.ppps.forwarder.utils.SettingUtils
import cn.ppps.forwarder.utils.ItemRows
import org.junit.Assert.*
import org.junit.Test

/** Local fixtures only. Never clicks transport Test, starts scans, or enables forwarding. */
class CopyNavigationTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private fun settle() { ins.waitForIdleSync(); Thread.sleep(500) }
    private fun walk(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { walk(v.getChildAt(it)) } else emptyList()
    private fun shot(name: String) {
        settle()
        val dir=java.io.File(ins.targetContext.getExternalFilesDir(null),"copy-review/"+InstrumentationRegistry.getArguments().getString("language","en")).apply { mkdirs() }
        java.io.FileOutputStream(java.io.File(dir,"$name.png")).use { ins.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
    }
    private fun prepare() {
        com.hjq.language.MultiLanguages.setAppLanguage(ins.targetContext,java.util.Locale(InstrumentationRegistry.getArguments().getString("language","en")))
        SettingUtils.enableSms=false;SettingUtils.enablePhone=false;SettingUtils.enableAppNotify=false
        SettingUtils.enableLocation=false;SettingUtils.enableBluetooth=false;SettingUtils.enableCactus=false
        SettingUtils.enableLoadAppList=false;SettingUtils.enableSmsCommand=false
        SettingUtils.enablePureClientMode=false;SettingUtils.enablePureTaskMode=false
    }
    private fun top(): BaseActivity<*> {
        var a: BaseActivity<*>?=null
        repeat(25) {
            ins.runOnMainSync { a=androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<BaseActivity<*>>().firstOrNull() }
            if(a!=null) return a!!
            Thread.sleep(100)
        }
        error("No resumed BaseActivity")
    }
    @Test fun realBackTabOwnershipAndRecreation() {
        prepare()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var main:MainActivity?=null
            scenario.onActivity { main=it;it.openRoute(MainActivity.Route.SETTINGS) };settle()
            var settings=main!!.supportFragmentManager.fragments.filterIsInstance<SettingsFragment>().single()
            val original=settings
            ins.runOnMainSync { settings.settingsNavigation!!.open("notifications") };settle()
            assertFalse(settings.binding!!.scbCancelAppNotify.isEnabled)
            assertFalse(settings.binding!!.scbNotUserPresent.isEnabled)
            shot("notifications-disabled")
            Espresso.pressBack();settle()
            assertNull(settings.settingsNavigation!!.current)
            ins.runOnMainSync { settings.settingsNavigation!!.open("calls");main!!.openRoute(MainActivity.Route.RULES) };settle()
            assertTrue(settings.isHidden)
            ins.runOnMainSync { main!!.openRoute(MainActivity.Route.SETTINGS) };settle()
            assertSame(original,main!!.supportFragmentManager.fragments.filterIsInstance<SettingsFragment>().single())
            assertEquals("calls",settings.settingsNavigation!!.current)
            // No side menu exists any more: the shell holds no DrawerLayout that Back could close first.
            ins.runOnMainSync { assertTrue(walk(main!!.window.decorView).none { it.javaClass.name.endsWith("DrawerLayout") }) }
            assertEquals("calls",settings.settingsNavigation!!.current)
            scenario.recreate();settle();scenario.onActivity { main=it }
            settings=main!!.supportFragmentManager.fragments.filterIsInstance<SettingsFragment>().single()
            assertTrue(settings.isVisible);assertEquals("calls",settings.settingsNavigation!!.current)
            shot("calls-restored")
            // Actual toolbar navigation button, not a direct controller call.
            ins.runOnMainSync {
                val toolbar=walk(settings.requireView()).filterIsInstance<com.google.android.material.appbar.MaterialToolbar>().first()
                val up=walk(toolbar).filterIsInstance<android.widget.ImageButton>().first()
                up.performClick()
            };settle()
            assertNull(settings.settingsNavigation!!.current)
            assertEquals("Main stays foreground",main,top())
        }
    }
    @Test fun taskChildRecreationReturnsToDraftAndAppliesOnce() {
        prepare()
        val page=com.xuexiang.xpage.core.CoreSwitchBean(TasksEditFragment::class.java).setAddToBackStack(true)
        val launch=Intent(ins.targetContext,BaseActivity::class.java).putExtra(com.xuexiang.xpage.core.CoreSwitchBean.KEY_SWITCH_BEAN,page)
        ActivityScenario.launch<BaseActivity<*>>(launch).use { scenario ->
            lateinit var activity:BaseActivity<*>
            scenario.onActivity { activity=it };settle()
            var parent=activity.supportFragmentManager.fragments.filterIsInstance<TasksEditFragment>().single()
            ins.runOnMainSync {
                parent.binding!!.etName.setText("Unsaved local draft")
                val list=parent.javaClass.getDeclaredField("TASK_CONDITION_FRAGMENT_LIST").apply { isAccessible=true }.get(parent) as List<*>
                parent.onItemClick(parent.requireView(),list.first() as com.xuexiang.xpage.model.PageInfo,0)
            };settle()
            assertTrue(activity.supportFragmentManager.fragments.filterIsInstance<CronFragment>().single().isVisible)
            scenario.recreate();settle();scenario.onActivity { activity=it }
            val child=activity.supportFragmentManager.fragments.filterIsInstance<CronFragment>().single()
            assertTrue("Child stays on top after recreation",child.isVisible)
            parent=activity.supportFragmentManager.fragments.filterIsInstance<TasksEditFragment>().single()
            assertEquals("Unsaved local draft",parent.binding!!.etName.text.toString())
            shot("task-child-restored")
            ins.runOnMainSync { child.requireView().findViewById<View>(R.id.btn_save).performClick() };settle()
            assertTrue(parent.isVisible)
            val field=parent.javaClass.getDeclaredField("conditionsList").apply { isAccessible=true }
            assertEquals(1,(field.get(parent) as List<*>).size)
            assertEquals("Unsaved local draft",parent.binding!!.etName.text.toString())
            ins.runOnMainSync {
                val edit=parent.javaClass.getDeclaredMethod("editCondition",Int::class.javaPrimitiveType).apply { isAccessible=true }
                edit.invoke(parent,0)
            };settle();Espresso.pressBack();settle()
            assertTrue(parent.isVisible);assertEquals(1,(field.get(parent) as List<*>).size)
            shot("task-draft-returned")
        }
    }
    @Test fun searchBackAndTaskDependenciesDoNotExitOrClear() {
        prepare()
        val server = java.net.ServerSocket(0, 10, java.net.InetAddress.getByName("127.0.0.1"))
        val worker = Thread {
            try { while (!server.isClosed) server.accept().use { socket ->
                val reader=socket.getInputStream().bufferedReader();reader.readLine();var length=0
                while(true) { val line=reader.readLine() ?: break;if(line.isEmpty()) break;if(line.startsWith("Content-Length:",true)) length=line.substringAfter(':').trim().toInt() }
                val body=CharArray(length);var offset=0;while(offset<length) { val n=reader.read(body,offset,length-offset);if(n<0) break;offset+=n }
                val json="{\"code\":200,\"msg\":\"fixture\",\"data\":[]}".toByteArray()
                socket.getOutputStream().apply { write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${json.size}\r\nConnection: close\r\n\r\n".toByteArray());write(json);flush() }
            } } catch (_:java.net.SocketException) { }
        }.apply { start() }
        val oldAddress=cn.ppps.forwarder.utils.HttpServerUtils.serverAddress
        val oldMode=cn.ppps.forwarder.utils.HttpServerUtils.clientSafetyMeasures
        val oldKey=cn.ppps.forwarder.utils.HttpServerUtils.clientSignKey
        cn.ppps.forwarder.utils.HttpServerUtils.serverAddress="http://127.0.0.1:${server.localPort}"
        cn.ppps.forwarder.utils.HttpServerUtils.clientSafetyMeasures=0
        cn.ppps.forwarder.utils.HttpServerUtils.clientSignKey=""
        val page=com.xuexiang.xpage.core.CoreSwitchBean(cn.ppps.forwarder.fragment.client.SmsQueryFragment::class.java).setAddToBackStack(true)
        val launch=Intent(ins.targetContext,BaseActivity::class.java).putExtra(com.xuexiang.xpage.core.CoreSwitchBean.KEY_SWITCH_BEAN,page)
        ActivityScenario.launch<BaseActivity<*>>(launch).use { scenario ->
            lateinit var activity:BaseActivity<*>
            scenario.onActivity { activity=it };settle()
            val fragment=activity.supportFragmentManager.fragments.first { it.isVisible }
            val search=walk(fragment.requireView()).filterIsInstance<cn.ppps.forwarder.widget.material.TargetSearch>().first()
            ins.runOnMainSync { search.showSearch() };settle();Espresso.closeSoftKeyboard();settle()
            assertTrue(search.hasOpenSearch)
            Espresso.pressBack();settle()
            assertFalse(search.hasOpenSearch);assertTrue(fragment.isVisible);assertEquals(activity,top())
            ins.runOnMainSync { search.showSearch() };settle();Espresso.closeSoftKeyboard();settle()
            ins.runOnMainSync { activity.navigateBack() };settle()
            assertFalse(search.hasOpenSearch);assertTrue(fragment.isVisible)
            shot("search-back-stays-on-query")
        }
        server.close();worker.join(2000)
        cn.ppps.forwarder.utils.HttpServerUtils.serverAddress=oldAddress
        cn.ppps.forwarder.utils.HttpServerUtils.clientSafetyMeasures=oldMode
        cn.ppps.forwarder.utils.HttpServerUtils.clientSignKey=oldKey
        val taskPage=com.xuexiang.xpage.core.CoreSwitchBean(cn.ppps.forwarder.fragment.action.SettingsFragment::class.java).setAddToBackStack(true)
        val taskLaunch=Intent(ins.targetContext,BaseActivity::class.java).putExtra(com.xuexiang.xpage.core.CoreSwitchBean.KEY_SWITCH_BEAN,taskPage)
        ActivityScenario.launch<BaseActivity<*>>(taskLaunch).use { scenario ->
            lateinit var activity:BaseActivity<*>
            scenario.onActivity { activity=it };settle()
            val f=activity.supportFragmentManager.fragments.filterIsInstance<cn.ppps.forwarder.fragment.action.SettingsFragment>().single()
            val b=f.binding!!
            val before=cn.ppps.forwarder.utils.SharedPreference.preference.all.toMap()
            ins.runOnMainSync { b.sbEnablePhone.isChecked=true;b.scbCallType1.isChecked=true;b.sbEnablePhone.isChecked=false };settle()
            assertFalse(b.scbCallType1.isEnabled);assertTrue(b.scbCallType1.isChecked)
            ins.runOnMainSync { b.sbEnablePhone.isChecked=true };settle()
            assertTrue(b.scbCallType1.isEnabled);assertTrue(b.scbCallType1.isChecked)
            ins.runOnMainSync { b.sbEnableLocation.isChecked=false;b.sbEnableAppNotify.isChecked=false;b.sbEnableSmsCommand.isChecked=false };settle()
            assertFalse(b.rbAccuracyFine.isEnabled);assertFalse(b.scbCancelAppNotify.isEnabled)
            assertEquals("Task definition must not change live settings",before,cn.ppps.forwarder.utils.SharedPreference.preference.all.toMap())
            shot("task-dependent-controls")
        }
    }

    @Test fun smsRecipientKeyboardInlineValidationAndPickerCancel() {
        prepare()
        val page=com.xuexiang.xpage.core.CoreSwitchBean(SmsFragment::class.java).setAddToBackStack(true)
        val launch=Intent(ins.targetContext,BaseActivity::class.java).putExtra(com.xuexiang.xpage.core.CoreSwitchBean.KEY_SWITCH_BEAN,page)
        ActivityScenario.launch<BaseActivity<*>>(launch).use { scenario ->
            lateinit var activity:BaseActivity<*>
            scenario.onActivity { activity=it };settle()
            val f=activity.supportFragmentManager.fragments.filterIsInstance<SmsFragment>().single()
            val b=f.binding!!
            val rows=b.etMobiles.getTag(R.id.item_rows_controller) as ItemRows
            ins.runOnMainSync { b.etName.setText("Local SMS fixture");b.sbEnable.isChecked=false }
            val add=walk(rows.view).filterIsInstance<android.widget.Button>().first { it.text.toString()==f.getString(R.string.copy_add_recipient) }
            ins.runOnMainSync { add.performClick() };settle()
            val input=walk(rows.view).filterIsInstance<EditText>().first()
            val box=walk(rows.view).filterIsInstance<com.google.android.material.textfield.TextInputLayout>().first()
            assertNull("New blank row must be pristine",box.error)
            assertEquals(android.text.InputType.TYPE_CLASS_PHONE,input.inputType and android.text.InputType.TYPE_MASK_CLASS)
            assertTrue("Phone field is not squeezed",input.width>180*input.resources.displayMetrics.density)
            ins.runOnMainSync { add.requestRectangleOnScreen(android.graphics.Rect(0,0,add.width,add.height),true) };settle()
            assertTrue(add.getGlobalVisibleRect(android.graphics.Rect()))
            shot("sms-pristine-keyboard")
            ins.runOnMainSync { input.setText("+31000000001") };settle();shot("sms-populated-keyboard")
            Espresso.closeSoftKeyboard();settle()
            ins.runOnMainSync { add.performClick() };settle();Espresso.closeSoftKeyboard()
            ins.runOnMainSync { assertFalse(rows.validate()) };settle();shot("sms-inline-error")
            val removes=walk(rows.view).filterIsInstance<android.widget.ImageButton>()
            ins.runOnMainSync { removes.last().performClick() };settle()
            assertEquals("+31000000001",b.etMobiles.text.toString())
            ins.runOnMainSync { b.btPickContact.performClick() };settle();shot("contact-picker")
            ins.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);settle()
            assertTrue(f.isVisible)
            assertEquals("+31000000001",b.etMobiles.text.toString())
            ins.runOnMainSync { walk(f.requireView()).filterIsInstance<androidx.core.widget.NestedScrollView>().first().scrollTo(0,0) };settle()
            shot("sms-after-picker-cancel")
        }
    }
}
