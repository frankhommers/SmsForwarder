package cn.ppps.forwarder

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.view.View
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import cn.ppps.forwarder.activity.MainActivity
import cn.ppps.forwarder.core.Core
import cn.ppps.forwarder.database.entity.Task
import cn.ppps.forwarder.entity.LocationInfo
import cn.ppps.forwarder.entity.TaskSetting
import cn.ppps.forwarder.entity.condition.LocationSetting
import cn.ppps.forwarder.fragment.TasksEditFragment
import cn.ppps.forwarder.fragment.condition.LeaveAddressFragment
import cn.ppps.forwarder.fragment.condition.LocationConditionEditor
import cn.ppps.forwarder.fragment.condition.ToAddressFragment
import cn.ppps.forwarder.service.LocationService
import cn.ppps.forwarder.utils.KEY_BACK_DATA_CONDITION
import cn.ppps.forwarder.utils.KEY_EVENT_DATA_CONDITION
import cn.ppps.forwarder.utils.KEY_TASK_ID
import cn.ppps.forwarder.utils.SettingUtils
import cn.ppps.forwarder.utils.TASK_CONDITION_CRON
import cn.ppps.forwarder.utils.TASK_CONDITION_LEAVE_ADDRESS
import cn.ppps.forwarder.utils.TASK_CONDITION_TO_ADDRESS
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.Gson
import com.xuexiang.xpage.core.PageOption
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Arrive/Leave Location editors as local drafts. Never enables the location service, never requests a fix,
 * never sends anything. A legacy task fixture is stored disabled and deleted afterwards.
 */
class LocationConditionEditorTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val lang = InstrumentationRegistry.getArguments().getString("language", "en")!!
    private lateinit var main: MainActivity
    private var enableLocationBefore = false

    private val legacyJson = """{"description":"Old keyword condition","type":"to","calcType":"address","longitude":0.0,"latitude":0.0,"distance":0.0,"address":"Main Street"}"""

    private fun settle(ms: Long = 400) { ins.waitForIdleSync(); Thread.sleep(ms) }
    private fun shot(name: String) {
        settle(700)
        val dir = java.io.File(ins.targetContext.getExternalFilesDir(null), "location-review/$lang").apply { mkdirs() }
        java.io.FileOutputStream(java.io.File(dir, "$name.png")).use { ins.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun box(e: EditText) = generateSequence(e.parent) { it.parent }.filterIsInstance<TextInputLayout>().first()
    private fun err(e: EditText): String? = box(e).error?.toString()
    private fun reveal(v: View) = ins.runOnMainSync { v.requestRectangleOnScreen(Rect(0, 0, v.width, v.height), true) }

    private fun serviceRunning(): Boolean {
        @Suppress("DEPRECATION")
        return (ins.targetContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .getRunningServices(200).any { it.service.className == LocationService::class.java.name }
    }
    private fun assertNoLocationActivation() {
        assertEquals("Location setting unchanged", enableLocationBefore, SettingUtils.enableLocation)
        assertFalse("Location service must not run", serviceRunning())
        var started = true
        ins.runOnMainSync { started = App.LocationClient.isStarted() }
        assertFalse("Location client must not start", started)
    }

    private inline fun <reified F : Fragment> await(): F? {
        var found: F? = null
        repeat(50) {
            ins.waitForIdleSync()
            ins.runOnMainSync {
                found = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<FragmentActivity>().flatMap { it.supportFragmentManager.fragments }
                    .filterIsInstance<F>().firstOrNull { it.isResumed && it.isVisible }
            }
            if (found != null) return found
            Thread.sleep(100)
        }
        return null
    }

    /** Opens an editor as a task child and records every Apply result synchronously. */
    private inline fun <reified F : Fragment> open(data: String?, results: MutableList<String>): F {
        val key = "location.test." + java.util.UUID.randomUUID()
        ins.runOnMainSync {
            @Suppress("UNCHECKED_CAST")
            PageOption.to(F::class.java as Class<com.xuexiang.xpage.base.XPageFragment>).putString("task.result.receiver", key).putInt("task.result.request", 1)
                .apply { if (data != null) putString(KEY_EVENT_DATA_CONDITION, data) }
                .setNewActivity(true).open(main)
        }
        val f = await<F>() ?: error("${F::class.java.simpleName} did not open")
        ins.runOnMainSync {
            f.parentFragmentManager.setFragmentResultListener(key, f.requireActivity()) { _, bundle ->
                results.add(bundle.getParcelable<Intent>("data")!!.getStringExtra(KEY_BACK_DATA_CONDITION)!!)
            }
        }
        return f
    }
    private fun editorOf(f: Fragment): LocationConditionEditor = when (f) {
        is ToAddressFragment -> f.editor!!
        is LeaveAddressFragment -> f.editor!!
        else -> error("not a location editor")
    }
    private fun closed(f: Fragment): Boolean {
        var gone = false
        repeat(30) {
            ins.runOnMainSync { gone = !f.isResumed || f.activity?.isFinishing != false }
            if (gone) return true
            Thread.sleep(100)
        }
        return gone
    }

    @Before fun setUp() {
        com.hjq.language.MultiLanguages.setAppLanguage(ins.targetContext, java.util.Locale(lang))
        enableLocationBefore = SettingUtils.enableLocation
        assertFalse("Precondition: location off on the test emulator", enableLocationBefore)
        main = ins.startActivitySync(Intent(ins.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        settle()
    }

    @After fun tearDown() {
        ins.runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).forEach { it.finish() }
            main.finish()
        }
        settle()
    }

    private fun expectedOption() = if (lang == "nl") "Adres op trefwoorden controleren (niet beschikbaar)" else "Match address keywords (unavailable)"

    @Test fun newArriveDefaultInvalidApplyAndEquatorMeridian() = newConditionFlow<ToAddressFragment>("to", "to")

    @Test fun newLeaveDefaultInvalidApplyAndEquatorMeridian() = newConditionFlow<LeaveAddressFragment>("leave", "leave")

    private inline fun <reified F : Fragment> newConditionFlow(type: String, prefix: String) {
        val results = mutableListOf<String>()
        val f = open<F>(null, results)
        val v = f.requireView()
        val lon = v.findViewById<EditText>(R.id.et_longitude)
        val lat = v.findViewById<EditText>(R.id.et_latitude)
        val rad = v.findViewById<EditText>(R.id.et_distance)
        val addressOption = v.findViewById<android.widget.RadioButton>(R.id.rb_calc_type_address)
        val distanceOption = v.findViewById<android.widget.RadioButton>(R.id.rb_calc_type_distance)
        ins.runOnMainSync {
            assertEquals("Actual app language", expectedOption(), addressOption.text.toString())
            assertTrue(distanceOption.isChecked)
            assertFalse("New address mode visibly disabled", addressOption.isEnabled)
            assertTrue(v.findViewById<View>(R.id.condition_form_location_address_unavailable).isShown)
            assertEquals(View.GONE, v.findViewById<View>(R.id.condition_form_location_legacy_notice).visibility)
            for (e in listOf(lon, lat, rad)) { assertEquals("", e.text.toString()); assertNull(err(e)) }
            // Pristine blur: moving through the empty fields shows no error and invents no value.
            lon.requestFocus(); lat.requestFocus(); rad.requestFocus(); rad.clearFocus()
        }
        settle()
        ins.runOnMainSync { for (e in listOf(lon, lat, rad)) { assertNull("No pristine blur error", err(e)); assertEquals("", e.text.toString()) } }
        shot("$prefix-01-new-default")

        // An injected tap on the disabled address option must not select it.
        val explanation = v.findViewById<View>(R.id.condition_form_location_address_unavailable)
        reveal(explanation); settle()
        Espresso.onView(withId(R.id.rb_calc_type_address)).perform(click()); settle()
        ins.runOnMainSync {
            assertTrue(distanceOption.isChecked); assertFalse(addressOption.isChecked)
            val r = Rect(); assertTrue("Explanation on screen", explanation.getLocalVisibleRect(r) && r.height() == explanation.height)
        }
        shot("$prefix-02-address-option-disabled")

        // Empty Apply: refused, three inline errors, first invalid field focused.
        ins.runOnMainSync { v.findViewById<View>(R.id.btn_save).performClick() }; settle()
        val r = f.resources
        ins.runOnMainSync {
            assertTrue("Refused", results.isEmpty()); assertTrue(f.isResumed)
            assertEquals(r.getString(R.string.location_validation_longitude_error), err(lon))
            assertEquals(r.getString(R.string.location_validation_latitude_error), err(lat))
            assertEquals(r.getString(R.string.location_validation_radius_error), err(rad))
            assertTrue("First invalid field focused", lon.isFocused)
            assertNull("No legacy popup", lon.error)
        }
        shot("$prefix-03-invalid-empty")

        // Out of range, zero radius.
        ins.runOnMainSync { lon.setText("180.5"); lat.setText("-90.01"); rad.setText("0") }
        Thread.sleep(1100)
        ins.runOnMainSync { v.findViewById<View>(R.id.btn_save).performClick() }; settle()
        ins.runOnMainSync {
            assertTrue(results.isEmpty())
            assertNotNull(err(lon)); assertNotNull(err(lat)); assertNotNull(err(rad))
        }
        reveal(lon); shot("$prefix-04-invalid-range")

        // Correction clears errors while typing; equator/prime meridian is a valid point.
        ins.runOnMainSync { lon.setText("0"); lat.setText("0"); rad.setText("100") }
        ins.runOnMainSync { assertNull(err(lon)); assertNull(err(lat)); assertNull(err(rad)) }
        shot("$prefix-05-valid-equator-meridian")
        Thread.sleep(1100)
        ins.runOnMainSync { v.findViewById<View>(R.id.btn_save).performClick() }; settle()
        assertEquals(1, results.size)
        val saved = Gson().fromJson(results[0], LocationSetting::class.java)
        assertEquals(type, saved.type); assertEquals("distance", saved.calcType)
        assertEquals(0.0, saved.longitude, 0.0); assertEquals(0.0, saved.latitude, 0.0); assertEquals(100.0, saved.distance, 0.0)
        assertTrue("Editor closed after Apply", closed(f))
        assertNoLocationActivation()

        // Back with an unfinished draft delivers nothing.
        val back = mutableListOf<String>()
        val g = open<F>(null, back)
        ins.runOnMainSync { g.requireView().findViewById<EditText>(R.id.et_longitude).setText("5") }
        settle()
        Espresso.closeSoftKeyboard()
        Espresso.pressBack(); settle()
        assertTrue("Back closes editor", closed(g)); assertTrue("Back applies nothing", back.isEmpty())
        assertNoLocationActivation()
    }

    @Test fun legacyAddressConditionPreservedUnlessGpsChosen() {
        // Discard and Back: nothing returned.
        for (exit in listOf("discard", "back")) {
            val results = mutableListOf<String>()
            val f = open<ToAddressFragment>(legacyJson, results)
            val v = f.requireView()
            ins.runOnMainSync {
                assertTrue(v.findViewById<View>(R.id.condition_form_location_legacy_notice).isShown)
                assertTrue(v.findViewById<android.widget.RadioButton>(R.id.rb_calc_type_address).isChecked)
                assertFalse(v.findViewById<View>(R.id.rb_calc_type_address).isEnabled)
                val keyword = v.findViewById<EditText>(R.id.et_address)
                assertEquals("Main Street", keyword.text.toString()); assertFalse("Keyword read-only", keyword.isEnabled)
                assertEquals(View.GONE, v.findViewById<View>(R.id.layout_calc_type_distance).visibility)
                assertEquals("Old keyword condition", v.findViewById<android.widget.TextView>(R.id.tv_description).text.toString())
            }
            if (exit == "discard") { shot("legacy-01-opened"); ins.runOnMainSync { v.findViewById<View>(R.id.btn_del).performClick() } }
            else { Espresso.pressBack() }
            settle()
            assertTrue(closed(f)); assertTrue("$exit returns nothing", results.isEmpty())
        }

        // Apply without choosing GPS: identical stored JSON.
        val same = mutableListOf<String>()
        val a = open<ToAddressFragment>(legacyJson, same)
        ins.runOnMainSync { a.requireView().findViewById<View>(R.id.btn_save).performClick() }; settle()
        assertEquals(listOf(legacyJson), same)
        assertTrue(closed(a))

        // Explicit GPS selection: invalid stored 0 radius is refused, then a valid point changes the mode.
        val changed = mutableListOf<String>()
        val c = open<ToAddressFragment>(legacyJson, changed)
        val v = c.requireView()
        val distance = v.findViewById<View>(R.id.rb_calc_type_distance)
        reveal(distance); settle()
        Espresso.onView(withId(R.id.rb_calc_type_distance)).perform(click()); settle()
        val rad = v.findViewById<EditText>(R.id.et_distance)
        val lon = v.findViewById<EditText>(R.id.et_longitude)
        val lat = v.findViewById<EditText>(R.id.et_latitude)
        ins.runOnMainSync {
            assertEquals(View.VISIBLE, v.findViewById<View>(R.id.layout_calc_type_distance).visibility)
            assertEquals("Unused legacy defaults are not shown as a point", "", lon.text.toString())
            assertEquals("", rad.text.toString())
            assertNull("No error before Apply", err(rad))
            v.findViewById<View>(R.id.btn_save).performClick()
        }
        settle()
        ins.runOnMainSync {
            assertTrue(changed.isEmpty())
            assertEquals(c.getString(R.string.location_validation_longitude_error), err(lon))
            assertEquals(c.getString(R.string.location_validation_radius_error), err(rad))
        }
        shot("legacy-02-gps-chosen-invalid")
        ins.runOnMainSync { lon.setText("0"); lat.setText("0"); rad.setText("50") }
        Thread.sleep(1100)
        ins.runOnMainSync { v.findViewById<View>(R.id.btn_save).performClick() }; settle()
        assertEquals(1, changed.size)
        val s = Gson().fromJson(changed[0], LocationSetting::class.java)
        assertEquals("distance", s.calcType); assertEquals(50.0, s.distance, 0.0); assertEquals(0.0, s.longitude, 0.0); assertEquals(0.0, s.latitude, 0.0); assertEquals("Main Street", s.address); assertEquals("to", s.type)
        assertNoLocationActivation()
    }

    @Test fun savedEquatorConditionReopensWithItsValues() {
        val json = """{"description":"Zero","type":"leave","calcType":"distance","longitude":0.0,"latitude":0.0,"distance":25.0,"address":""}"""
        val results = mutableListOf<String>()
        val f = open<LeaveAddressFragment>(json, results)
        val v = f.requireView()
        ins.runOnMainSync {
            assertEquals("0.0", v.findViewById<EditText>(R.id.et_longitude).text.toString())
            assertEquals("0.0", v.findViewById<EditText>(R.id.et_latitude).text.toString())
            assertEquals("25.0", v.findViewById<EditText>(R.id.et_distance).text.toString())
            assertEquals(View.GONE, v.findViewById<View>(R.id.condition_form_location_legacy_notice).visibility)
            v.findViewById<View>(R.id.btn_save).performClick()
        }
        settle()
        assertEquals(1, results.size)
        val s = Gson().fromJson(results[0], LocationSetting::class.java)
        assertEquals("leave", s.type); assertEquals(0.0, s.longitude, 0.0); assertEquals(0.0, s.latitude, 0.0); assertEquals(25.0, s.distance, 0.0)
        assertNoLocationActivation()
    }

    @Test fun currentLocationNeedsActualFixAndDeclineStartsNothing() {
        val results = mutableListOf<String>()
        val f = open<LeaveAddressFragment>(null, results)
        val v = f.requireView()
        // Location client is not started: the button only asks. Declining activates nothing.
        val btn = v.findViewById<View>(R.id.btn_current_coordinates)
        ins.runOnMainSync { f.onClick(btn) }; settle()
        shot("leave-06-enable-location-prompt")
        Espresso.onView(withText(R.string.lab_no)).perform(click()); settle()
        assertNoLocationActivation()
        // Helper semantics: cleared cache is not a fix; an actual 0/0 fix fills 0/0.
        ins.runOnMainSync {
            val e = editorOf(f)
            assertFalse(e.fillFrom(LocationInfo()))
            assertEquals("", v.findViewById<EditText>(R.id.et_longitude).text.toString())
            assertTrue(e.fillFrom(LocationInfo(0.0, 0.0, "", "2026-11-18 10:00:00", "gps")))
            assertEquals("0.0", v.findViewById<EditText>(R.id.et_longitude).text.toString())
            assertEquals("0.0", v.findViewById<EditText>(R.id.et_latitude).text.toString())
        }
        assertTrue(results.isEmpty())
    }

    @Test fun savedLegacyTaskRecognizableAndUnchangedAfterOpeningCondition() {
        val legacy = TaskSetting(TASK_CONDITION_TO_ADDRESS, "Arrive", "Old keyword condition", legacyJson, 0)
        val normal = TaskSetting(TASK_CONDITION_CRON, "Cron", "Every day", """{"expression":"0 0 8 * * ? *","description":"Every day"}""", 1)
        val conditions = Gson().toJson(listOf(legacy, normal))
        val task = Task(0, TASK_CONDITION_TO_ADDRESS, "Location legacy fixture", "fixture", conditions, "[]", 0)
        var id = 0L
        val t = Thread { id = Core.task.insert(task) }; t.start(); t.join()
        try {
            ins.runOnMainSync { PageOption.to(TasksEditFragment::class.java).putLong(KEY_TASK_ID, id).setNewActivity(true).open(main) }
            val edit = await<TasksEditFragment>() ?: error("Task editor did not open")
            var rows: List<String> = emptyList()
            repeat(30) {
                ins.runOnMainSync {
                    val rv = edit.requireView().findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler_conditions)
                    rows = (0 until rv.childCount).map { rv.getChildAt(it).findViewById<android.widget.TextView>(R.id.tv_description).text.toString() }
                }
                if (rows.size == 2) return@repeat
                Thread.sleep(100)
            }
            assertEquals(2, rows.size)
            assertEquals(edit.getString(R.string.location_validation_legacy_row, "Old keyword condition"), rows[0])
            assertEquals("Every day", rows[1])
            shot("task-01-legacy-row")
            // Open the legacy condition and go Back: the draft row and stored task stay identical.
            ins.runOnMainSync {
                val rv = edit.requireView().findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler_conditions)
                rv.getChildAt(0).findViewById<View>(R.id.iv_edit).performClick()
            }
            val cond = await<ToAddressFragment>() ?: error("Legacy condition did not open")
            shot("task-02-legacy-condition")
            Espresso.pressBack(); settle()
            assertTrue(closed(cond))
            assertNotNull(await<TasksEditFragment>())
            var stored: Task? = null
            val r = Thread { stored = Core.task.get(id).blockingGet() }; r.start(); r.join()
            assertEquals(conditions, stored!!.conditions)
            assertEquals(0, stored!!.status)
            assertEquals("fixture", stored!!.description)
            ins.runOnMainSync { edit.requireActivity().finish() }; settle()

            // Tasks list: the stored task row is marked as unsupported (display only).
            ins.runOnMainSync { main.openRoute(MainActivity.Route.TASKS) }
            val list = await<cn.ppps.forwarder.fragment.TasksFragment>() ?: error("Tasks list did not open")
            val marked = list.getString(R.string.location_validation_legacy_row, "fixture")
            var seen = false
            repeat(50) {
                ins.runOnMainSync {
                    val rv = list.requireView().findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerView)
                    seen = (0 until rv.childCount).any { i ->
                        val row = rv.getChildAt(i)
                        row.findViewById<android.widget.TextView>(R.id.tv_name)?.text?.toString() == "Location legacy fixture" &&
                            row.findViewById<android.widget.TextView>(R.id.tv_description)?.text?.toString() == marked
                    }
                }
                if (seen) return@repeat
                Thread.sleep(100)
            }
            assertTrue("Tasks list marks the legacy task", seen)
            shot("task-03-list-legacy-marker")
            assertNoLocationActivation()
        } finally {
            val d = Thread { Core.task.delete(id) }; d.start(); d.join()
        }
    }
}
