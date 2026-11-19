package cn.ppps.forwarder

import android.app.Activity
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.webkit.WebView
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.*
import org.hamcrest.Matchers.allOf
import org.junit.Test
import org.junit.Assert.*
import cn.ppps.forwarder.activity.MainActivity
import cn.ppps.forwarder.widget.material.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Bounded, local-only behavioral acceptance; never executes forwarding actions. */
class MaterialAcceptanceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private fun main() = ins.startActivitySync(Intent(ins.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
    private fun walk(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { walk(view.getChildAt(it)) } else emptyList()
    private fun waitUntil(label: String, condition: () -> Boolean) {
        repeat(100) { if (condition()) return; Thread.sleep(100) }
        fail("Timed out: $label")
    }
    private fun page(main: MainActivity, klass: Class<out com.xuexiang.xpage.base.XPageFragment>): Activity {
        ins.runOnMainSync { main.openNewPage(klass) }
        var result: Activity? = null
        waitUntil(klass.simpleName) { ins.runOnMainSync { result = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).firstOrNull { a -> a !== main && !a.isFinishing && (a as? androidx.fragment.app.FragmentActivity)?.supportFragmentManager?.fragments?.any { klass.isInstance(it) && it.isVisible && it.isResumed } == true } }; result != null }
        ins.waitForIdleSync(); Thread.sleep(400)
        ins.runOnMainSync {
            assertTrue("Requested fragment ${klass.name} active", (result as androidx.fragment.app.FragmentActivity).supportFragmentManager.fragments.any { klass.isInstance(it) && it.isVisible && it.isResumed })
        }
        return result!!
    }
    private fun shot(name: String) {
        ins.waitForIdleSync(); Thread.sleep(500)
        val dir = java.io.File(ins.targetContext.getExternalFilesDir(null), "acceptance").apply { mkdirs() }
        java.io.FileOutputStream(java.io.File(dir, "$name.png")).use { ins.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun text(activity: Activity, id: Int) = activity.findViewById<TextView>(id).text.toString()

    @Test fun localWebViewJavaScriptDialogsComplete() {
        val activity = ins.startActivitySync(Intent(ins.targetContext, cn.ppps.forwarder.core.webview.AgentWebActivity::class.java)
            .putExtra(cn.ppps.forwarder.core.webview.AgentWebFragment.KEY_URL, "about:blank").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        var web: WebView? = null
        waitUntil("WebView") { ins.runOnMainSync { web = walk(activity.window.decorView).filterIsInstance<WebView>().firstOrNull() }; web != null }
        fun eval(script: String): String {
            val latch = CountDownLatch(1); var answer = ""
            ins.runOnMainSync { web!!.evaluateJavascript(script) { answer = it; latch.countDown() } }
            assertTrue("JavaScript callback: $script", latch.await(10, TimeUnit.SECONDS)); return answer
        }
        try {
            ins.runOnMainSync { web!!.settings.blockNetworkLoads = true; web!!.loadDataWithBaseURL(null, "<html><body>Local synthetic fixture</body></html>", "text/html", "UTF-8", null) }
            Thread.sleep(700)
            ins.runOnMainSync { web!!.evaluateJavascript("window.alertDone=false;alert('Fixture alert');window.alertDone=true", null) }
            waitUntil("JavaScript alert window") { ins.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText("Fixture alert")?.isNotEmpty() == true }
            onView(withText("Fixture alert")).check(androidx.test.espresso.assertion.ViewAssertions.matches(isDisplayed()))
            shot("web-alert")
            onView(withId(android.R.id.button1)).perform(click())
            assertEquals("true", eval("window.alertDone"))
            for (accept in listOf(true, false)) {
                ins.runOnMainSync { web!!.evaluateJavascript("window.confirmResult=confirm('Fixture confirm')", null) }
                Thread.sleep(500)
                onView(withText("Fixture confirm")).check(androidx.test.espresso.assertion.ViewAssertions.matches(isDisplayed()))
                onView(withId(if (accept) android.R.id.button1 else android.R.id.button2)).perform(click())
                assertEquals(accept.toString(), eval("window.confirmResult"))
            }
            ins.runOnMainSync { web!!.evaluateJavascript("window.promptResult=prompt('Fixture prompt','seed')", null) }
            Thread.sleep(500)
            onView(isAssignableFrom(EditText::class.java)).perform(androidx.test.espresso.action.ViewActions.replaceText("local-answer"))
            shot("web-prompt")
            onView(withId(android.R.id.button1)).perform(click())
            assertEquals("\"local-answer\"", eval("window.promptResult"))
            ins.runOnMainSync { web!!.evaluateJavascript("window.promptResult=prompt('Fixture cancel','seed')", null) }
            Thread.sleep(500)
            onView(withId(android.R.id.button2)).perform(click())
            assertEquals("null", eval("window.promptResult"))
            ins.runOnMainSync { web!!.evaluateJavascript("window.confirmResult=confirm('Fixture back')", null) }
            Thread.sleep(500)
            androidx.test.espresso.Espresso.pressBack()
            assertEquals("false", eval("window.confirmResult"))
        } finally { ins.runOnMainSync { activity.finish() } }
    }

    @Test fun cronChipsAndTaskPickerReturnLocalDraft() {
        val main = main()
        val task = page(main, cn.ppps.forwarder.fragment.TasksEditFragment::class.java)
        try {
            ins.runOnMainSync { task.findViewById<View>(R.id.layout_add_condition).performClick() }
            Thread.sleep(500); shot("task-picker")
            onView(withId(R.id.bottom_sheet_close_button)).check(androidx.test.espresso.assertion.ViewAssertions.matches(isDisplayed())).perform(click())
            Thread.sleep(1100)
            ins.runOnMainSync { task.findViewById<View>(R.id.layout_add_condition).performClick() }
            Thread.sleep(500)
            onView(allOf(withText(task.getString(R.string.task_cron)), isDisplayed())).perform(click())
            Thread.sleep(600)
            var cron: Activity? = null
            ins.runOnMainSync { cron = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).firstOrNull() }
            val activity = cron!!
            lateinit var root: View
            ins.runOnMainSync { root = (activity as androidx.fragment.app.FragmentActivity).supportFragmentManager.fragments.filterIsInstance<cn.ppps.forwarder.fragment.condition.CronFragment>().first { it.isVisible }.binding!!.root }
            ins.runOnMainSync {
                root.findViewById<EditText>(R.id.et_second).requestFocus()
                val chips = root.findViewById<TargetChips>(R.id.flowlayout_multi_select_second)
                chips.getChildAt(5).performClick()
                assertEquals("05", text(activity, R.id.et_second))
                chips.getChildAt(7).performClick()
                assertEquals("05,07", text(activity, R.id.et_second))
                chips.getChildAt(5).performClick()
                assertEquals("07", text(activity, R.id.et_second))
            }
            shot("cron-chips")
            ins.runOnMainSync { root.findViewById<View>(R.id.btn_test).performClick() }
            waitUntil("local cron preview") { var ok = false; ins.runOnMainSync { ok = root.findViewById<TextView>(R.id.tv_next_time_list).text.isNotEmpty() }; ok }
            ins.runOnMainSync {
                assertEquals(activity.getString(R.string.m3_apply), root.findViewById<TextView>(R.id.btn_save).text.toString())
                if (activity.resources.configuration.locale.language == "nl") {
                    assertTrue("Actual Dutch descriptor", root.findViewById<TextView>(R.id.tv_description).text.toString().contains("seconden"))
                    assertFalse(root.findViewById<TextView>(R.id.tv_description).text.toString().contains("seconds"))
                }
                val dates = Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}""").findAll(text(activity, R.id.tv_next_time_list)).map { it.value }.toList()
                assertEquals(10, dates.size)
                val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                val parser = gatewayapps.crondroid.CronExpression("07 * * * * ? *")
                dates.zipWithNext().forEach { (before, after) ->
                    assertEquals("Preview uses unchanged execution parser", format.parse(after), parser.getNextValidTimeAfter(format.parse(before)))
                }
                val helper = root.findViewById<TextView>(R.id.tv_next_time_list)
                helper.requestRectangleOnScreen(android.graphics.Rect(0, helper.height - 40, helper.width, helper.height), true)
            }
            Thread.sleep(500)
            ins.runOnMainSync {
                val helper = root.findViewById<TextView>(R.id.tv_next_time_list)
                val visible = android.graphics.Rect(); assertTrue(helper.getGlobalVisibleRect(visible))
                val pos = IntArray(2); helper.getLocationOnScreen(pos)
                val footer = root.findViewById<View>(R.id.btn_save).parent as View
                val footerPos = IntArray(2); footer.getLocationOnScreen(footerPos)
                assertEquals("Last helper line scrolls into viewport", pos[1] + helper.height, visible.bottom)
                assertTrue("Last helper line above footer", visible.bottom <= footerPos[1])
            }
            shot("cron-preview")
            Thread.sleep(1100)
            ins.runOnMainSync { root.findViewById<View>(R.id.btn_save).performClick() }
            Thread.sleep(500)
            ins.runOnMainSync { assertTrue("Cron condition returned to task draft", walk(task.window.decorView).filterIsInstance<TextView>().any { it.text.contains("07") || it.text.contains("7 seconds") || it.text.contains("7 seconden") }) }
            shot("task-draft")
        } finally { ins.runOnMainSync { task.finish(); main.finish() } }
    }

    @Test fun advancedControlsAndAppLocaleCron() {
        val main = main()
        val oldLocale = java.util.Locale.getDefault()
        try {
            val activity = page(main, cn.ppps.forwarder.fragment.condition.CronFragment::class.java)
            // The device/default locale must not override an explicitly selected app language.
            java.util.Locale.setDefault(java.util.Locale.US)
            ins.runOnMainSync {
                val fragment = (activity as androidx.fragment.app.FragmentActivity).supportFragmentManager.fragments.filterIsInstance<cn.ppps.forwarder.fragment.condition.CronFragment>().first()
                val check = fragment.javaClass.getDeclaredMethod("checkSetting").apply { isAccessible = true }
                val setting = check.invoke(fragment) as cn.ppps.forwarder.entity.condition.CronSetting
                if (activity.resources.configuration.locale.language == "nl") assertEquals("Elke seconde", setting.description)
                assertEquals("* * * * * ? *", setting.expression)
                assertEquals(activity.getString(R.string.m3_apply), text(activity, R.id.btn_save))
            }
            shot("cron-localized-summary")
            ins.runOnMainSync { activity.finish() }
            val network = page(main, cn.ppps.forwarder.fragment.condition.NetworkFragment::class.java)
            ins.runOnMainSync {
                network.findViewById<View>(R.id.rb_net_mobile).performClick()
                network.findViewById<View>(R.id.rb_data_sim_slot_2).requestRectangleOnScreen(android.graphics.Rect(0,0,200,100), true)
            }
            Thread.sleep(500)
            ins.runOnMainSync {
                val radio = network.findViewById<View>(R.id.rb_data_sim_slot_2)
                radio.requestRectangleOnScreen(android.graphics.Rect(0,0,radio.width,radio.height), true)
            }
            Thread.sleep(500)
            ins.runOnMainSync {
                val last = network.findViewById<View>(R.id.rb_data_sim_slot_2)
                val rect = android.graphics.Rect()
                assertTrue(last.getGlobalVisibleRect(rect))
                assertEquals("Final SIM choice fully scroll-reachable", last.height, rect.height())
                for (id in listOf(R.id.rb_data_sim_slot_0, R.id.rb_data_sim_slot_1, R.id.rb_data_sim_slot_2)) {
                    val radio = network.findViewById<View>(id)
                    assertTrue("SIM option 48dp", radio.height >= 48 * network.resources.displayMetrics.density)
                }
                val parent = android.widget.FrameLayout(network)
                for (layout in listOf(R.layout.adapter_sender_list_item, R.layout.adapter_rule_list_item, R.layout.adapter_task_list_item, R.layout.adapter_bluetooth_list_item, R.layout.item_add_recipient)) {
                    val row = network.layoutInflater.inflate(layout, parent, false)
                    for (id in listOf(R.id.iv_remove, R.id.iv_drag, R.id.iv_del)) {
                        val control = row.findViewById<View>(id) ?: continue
                        assertTrue("Row action >=48dp", control.layoutParams.width >= 48 * network.resources.displayMetrics.density)
                        assertFalse("Named row action", control.contentDescription.isNullOrEmpty())
                    }
                }
            }
            shot("network-sim-choices")
            ins.runOnMainSync { network.finish() }
            val sms = page(main, cn.ppps.forwarder.fragment.action.SendSmsFragment::class.java)
            ins.runOnMainSync { assertEquals(sms.getString(R.string.m3_apply), text(sms, R.id.btn_save)) }
            shot("sms-action-definition")
            ins.runOnMainSync { sms.finish() }
        } finally { java.util.Locale.setDefault(oldLocale); ins.runOnMainSync { main.finish() } }
    }

    @Test fun denseFormsAndDropdown() {
        val main = main()
        try {
            for ((name, klass) in listOf("settings" to cn.ppps.forwarder.fragment.SettingsFragment::class.java, "proxy" to cn.ppps.forwarder.fragment.senders.TelegramFragment::class.java, "cron" to cn.ppps.forwarder.fragment.condition.CronFragment::class.java)) {
                val activity = page(main, klass)
                shot("dense-$name-top")
                if (name == "cron") ins.runOnMainSync { activity.findViewById<EditText>(R.id.et_second).requestFocus() }
                if (name == "proxy") {
                    ins.runOnMainSync { activity.findViewById<View>(R.id.rb_proxyHttp).performClick(); activity.findViewById<EditText>(R.id.et_proxyHost).setText("127.0.0.1"); activity.findViewById<EditText>(R.id.et_proxyPort).setText("8080") }
                }
                ins.runOnMainSync { walk(activity.window.decorView).filterIsInstance<androidx.core.widget.NestedScrollView>().firstOrNull()?.smoothScrollTo(0, 600) }
                Thread.sleep(700)
                if (name == "proxy") ins.runOnMainSync { activity.findViewById<View>(R.id.et_proxyHost).requestRectangleOnScreen(android.graphics.Rect(0, 0, 100, 350), true) }
                if (name == "cron") ins.runOnMainSync { activity.findViewById<View>(R.id.rb_second_type_cyclic).requestRectangleOnScreen(android.graphics.Rect(0, 0, 100, 500), true) }
                shot("dense-$name-detail")
                if (name == "cron") {
                    ins.runOnMainSync { activity.findViewById<View>(R.id.et_year).requestRectangleOnScreen(android.graphics.Rect(0,0,200,120), true) }
                    Thread.sleep(500)
                    ins.runOnMainSync { val field = activity.findViewById<View>(R.id.et_year); val rect = android.graphics.Rect(); assertTrue(field.getGlobalVisibleRect(rect)); assertTrue("Year input fully reachable above footer", rect.height() >= field.height) }
                }
                ins.runOnMainSync { activity.finish() }; ins.waitForIdleSync()
            }
            val activity = page(main, cn.ppps.forwarder.fragment.senders.SocketFragment::class.java)
            var dropdown: TargetDropdown? = null
            var selected = -1
            ins.runOnMainSync { dropdown = walk(activity.window.decorView).filterIsInstance<TargetDropdown>().first(); dropdown!!.setItems(listOf("Fixture A", "Fixture B")); dropdown!!.setOnItemSelectedListener { _, position, _, _ -> selected = position }; dropdown!!.requestRectangleOnScreen(android.graphics.Rect(0,0,dropdown!!.width,dropdown!!.height), true) }
            Thread.sleep(600)
            ins.runOnMainSync { dropdown!!.editText!!.requestFocus(); (dropdown!!.editText as android.widget.AutoCompleteTextView).showDropDown() }
            Thread.sleep(500)
            shot("dropdown-open")
            onView(withText("Fixture B")).inRoot(androidx.test.espresso.matcher.RootMatchers.isPlatformPopup()).perform(click())
            assertEquals("Selection callback", 1, selected)
            ins.runOnMainSync { assertEquals(1, dropdown!!.selectedIndex); assertEquals("Fixture B", dropdown!!.text.toString()); activity.finish() }
        } finally { ins.runOnMainSync { main.finish() } }
    }

    @Test fun webhookReadableWidthAndSecondaryAction() {
        val main = main(); val activity = page(main, cn.ppps.forwarder.fragment.senders.WebhookFragment::class.java)
        try {
            ins.runOnMainSync {
                val fragment = (activity as androidx.fragment.app.FragmentActivity).supportFragmentManager.fragments.filterIsInstance<cn.ppps.forwarder.fragment.senders.WebhookFragment>().first()
                val root = fragment.binding!!.root
                if (activity.resources.configuration.screenWidthDp >= 600) assertTrue("Form max 640dp", (root.width - root.paddingLeft - root.paddingRight) / activity.resources.displayMetrics.density <= 641)
                val add = walk(root).filterIsInstance<com.google.android.material.button.MaterialButton>().first { it.text.toString() == activity.getString(R.string.add_parameter) }
                assertEquals(com.google.android.material.color.MaterialColors.getColor(add, com.google.android.material.R.attr.colorSurface), add.backgroundTintList!!.defaultColor)
                assertTrue("Secondary button must retain readable text", add.currentTextColor != add.backgroundTintList!!.defaultColor)
            }
            shot("webhook-width")
            ins.runOnMainSync { walk(activity.window.decorView).filterIsInstance<TextView>().first { it.text.toString() == activity.getString(R.string.add_parameter) }.requestRectangleOnScreen(android.graphics.Rect(0,0,500,120),true) }
            shot("webhook-add-row")
        } finally { ins.runOnMainSync { activity.finish(); main.finish() } }
    }

    @Test fun choiceDialogAndRequiredWebhookFields() {
        val oldAddress = cn.ppps.forwarder.utils.HttpServerUtils.serverAddress
        val oldHistory = cn.ppps.forwarder.utils.HttpServerUtils.serverHistory
        val oldMode = cn.ppps.forwarder.utils.HttpServerUtils.clientSafetyMeasures
        val oldKey = cn.ppps.forwarder.utils.HttpServerUtils.clientSignKey
        cn.ppps.forwarder.utils.HttpServerUtils.serverAddress = ""
        cn.ppps.forwarder.utils.HttpServerUtils.serverHistory = "{\"http://127.0.0.1:19001\":\"##0\",\"http://127.0.0.1:19002\":\"##0\"}"
        val main = main(); val activity = page(main, cn.ppps.forwarder.fragment.ClientFragment::class.java)
        try {
            ins.runOnMainSync { activity.findViewById<View>(R.id.btn_server_history).performClick() }
            Thread.sleep(400)
            onView(withText("http://127.0.0.1:19002")).perform(click())
            onView(withId(android.R.id.button2)).perform(click())
            assertEquals("Cancel does not apply choice", "", cn.ppps.forwarder.utils.HttpServerUtils.serverAddress)
            Thread.sleep(1100)
            ins.runOnMainSync { activity.findViewById<View>(R.id.btn_server_history).performClick() }
            Thread.sleep(400)
            onView(withText("http://127.0.0.1:19002")).perform(click())
            onView(withId(android.R.id.button1)).perform(click())
            assertEquals("Confirmed non-default choice", "http://127.0.0.1:19002", cn.ppps.forwarder.utils.HttpServerUtils.serverAddress)
            ins.runOnMainSync { activity.finish() }; ins.waitForIdleSync()
            val webhook = page(main, cn.ppps.forwarder.fragment.senders.WebhookFragment::class.java)
            ins.runOnMainSync { webhook.findViewById<View>(R.id.btn_save).performClick() }
            ins.runOnMainSync {
                val input = webhook.findViewById<EditText>(R.id.et_name)
                val box = generateSequence(input.parent) { it.parent }
                    .filterIsInstance<com.google.android.material.textfield.TextInputLayout>().first()
                assertEquals(webhook.getString(R.string.invalid_name), box.error.toString())
                assertNull("No legacy error popup", input.error)
            }
            Thread.sleep(1100)
            ins.runOnMainSync { webhook.findViewById<View>(R.id.btn_test).performClick() }
            ins.runOnMainSync {
                val input = webhook.findViewById<EditText>(R.id.et_webServer)
                val box = generateSequence(input.parent) { it.parent }
                    .filterIsInstance<com.google.android.material.textfield.TextInputLayout>().first()
                assertEquals(webhook.getString(R.string.webhook_url_invalid), box.error.toString())
                assertNull("No legacy URL error popup", input.error)
            }
            shot("webhook-required-errors")
            ins.runOnMainSync { webhook.finish() }
        } finally {
            ins.runOnMainSync { activity.finish(); main.finish() }
            cn.ppps.forwarder.utils.HttpServerUtils.serverAddress = oldAddress
            cn.ppps.forwarder.utils.HttpServerUtils.serverHistory = oldHistory
            cn.ppps.forwarder.utils.HttpServerUtils.clientSafetyMeasures = oldMode
            cn.ppps.forwarder.utils.HttpServerUtils.clientSignKey = oldKey
        }
    }

    @Test fun clientSearchUsesOnlyLoopbackSyntheticContacts() {
        val server = java.net.ServerSocket(0, 10, java.net.InetAddress.getByName("127.0.0.1"))
        val requests = java.util.concurrent.CopyOnWriteArrayList<String>()
        val worker = Thread {
            try { while (!server.isClosed) server.accept().use { socket ->
                val reader = socket.getInputStream().bufferedReader(); val first = reader.readLine(); var length = 0
                while (true) { val line = reader.readLine() ?: break; if (line.isEmpty()) break; if (line.startsWith("Content-Length:", true)) length = line.substringAfter(':').trim().toInt() }
                val body = CharArray(length); var offset = 0; while(offset < length) { val n = reader.read(body, offset, length-offset); if(n < 0) break; offset += n }
                requests.add(first + " " + String(body))
                val json = "{\"code\":200,\"msg\":\"fixture\",\"data\":[{\"name\":\"Fixture Alice\",\"phone_number\":\"+31000000000\"}]}".toByteArray()
                socket.getOutputStream().apply { write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${json.size}\r\nConnection: close\r\n\r\n".toByteArray()); write(json); flush() }
            } } catch (_: java.net.SocketException) { }
        }.apply { start() }
        val oldAddress = cn.ppps.forwarder.utils.HttpServerUtils.serverAddress
        val oldMode = cn.ppps.forwarder.utils.HttpServerUtils.clientSafetyMeasures
        val oldKey = cn.ppps.forwarder.utils.HttpServerUtils.clientSignKey
        cn.ppps.forwarder.utils.HttpServerUtils.serverAddress = "http://127.0.0.1:${server.localPort}"
        cn.ppps.forwarder.utils.HttpServerUtils.clientSafetyMeasures = 0
        cn.ppps.forwarder.utils.HttpServerUtils.clientSignKey = ""
        val main = main(); val activity = page(main, cn.ppps.forwarder.fragment.client.ContactQueryFragment::class.java)
        try {
            waitUntil("initial loopback contact request") { requests.isNotEmpty() }
            ins.runOnMainSync { activity.findViewById<TargetSearch>(R.id.search_view).showSearch() }
            Thread.sleep(700); shot("client-search-open")
            ins.runOnMainSync { activity.findViewById<TargetSearch>(R.id.search_view).editText.apply { setText("Fixture Alice"); onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) } }
            waitUntil("query callback sent to loopback") { requests.any { it.contains("Fixture Alice") } }
            waitUntil("visible contact result after overlay closes") {
                var visible = false
                ins.runOnMainSync { visible = walk(activity.window.decorView).filterIsInstance<TextView>().any { it.text.toString() == "Fixture Alice" && it.isShown && it.getGlobalVisibleRect(android.graphics.Rect()) } }
                visible
            }
            ins.runOnMainSync { assertTrue("Toolbar retained above search/results", walk(activity.window.decorView).filterIsInstance<com.google.android.material.appbar.MaterialToolbar>().any { it.isShown && it.height > 0 }) }
            Thread.sleep(700); shot("client-search-results")
            assertTrue(requests.all { it.startsWith("POST /contact/query") })
            println("Loopback contact requests=${requests.size}; search callback and result rendering verified")
        } finally {
            ins.runOnMainSync { activity.finish(); main.finish() }; Thread.sleep(500)
            server.close(); worker.join(2000)
            cn.ppps.forwarder.utils.HttpServerUtils.serverAddress = oldAddress
            cn.ppps.forwarder.utils.HttpServerUtils.clientSafetyMeasures = oldMode
            cn.ppps.forwarder.utils.HttpServerUtils.clientSignKey = oldKey
        }
    }
}
