package cn.ppps.forwarder.fragment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Source regression gate for the Logs filter dialog (lg-filter): kept ids/types, visible full-size actions outside
 * the scroll view, no side labels, dismissible dialog built without the message-setting shared wrapper.
 * Runs on the JVM from the app module directory; device behaviour is covered by LogsFilterDialogTest.
 */
class LogsFilterLayoutTest {
    private val android = "http://schemas.android.com/apk/res/android"
    private val res = File("src/main/res")
    private val layout = File(res, "layout/dialog_logs_filter.xml")
    private val fragment = File("src/main/kotlin/cn/ppps/forwarder/fragment/LogsFragment.kt")

    private fun elements(file: File): List<Element> {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val nodes = factory.newDocumentBuilder().parse(file).getElementsByTagName("*")
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun Element.attr(name: String) = getAttributeNS(android, name)
    private fun Element.id() = attr("id").substringAfter('/')

    @Test fun keptIdsKeepTheirTypes() {
        val byId = elements(layout).filter { it.id().isNotEmpty() }.associateBy { it.id() }
        val expected = mapOf(
            "layout_title" to "LinearLayout", "layout_sim_slot" to "LinearLayout", "layout_call_type" to "LinearLayout",
            "rg_sim_slot" to "RadioGroup",
        ) + listOf("et_from", "et_title", "et_content", "et_start_time", "et_end_time").associateWith { "com.google.android.material.textfield.TextInputEditText" } +
            (0..2).associate { "rb_sim_slot_$it" to "com.google.android.material.radiobutton.MaterialRadioButton" } +
            (1..6).associate { "scb_call_type$it" to "cn.ppps.forwarder.widget.material.TargetCheckBox" } +
            (0..2).associate { "scb_forward_status_$it" to "cn.ppps.forwarder.widget.material.TargetCheckBox" }
        for ((id, type) in expected) assertEquals(id, type, byId[id]?.tagName)
        // Radio buttons stay inside rg_sim_slot and in value order (any, SIM 1, SIM 2).
        val group = byId.getValue("rg_sim_slot")
        val radios = (0 until group.childNodes.length).mapNotNull { group.childNodes.item(it) as? Element }.map { it.id() }
        assertEquals(listOf("rb_sim_slot_0", "rb_sim_slot_1", "rb_sim_slot_2"), radios)
        assertEquals("true", byId.getValue("rb_sim_slot_0").attr("checked"))
    }

    @Test fun actionsAreOutsideTheScrollingBodyAndNeverSqueezed() {
        val all = elements(layout)
        val scroll = all.single { it.tagName == "androidx.core.widget.NestedScrollView" }
        val bar = all.single { it.tagName == "cn.ppps.forwarder.fragment.LogsFilterActionBar" }
        assertEquals("logs_filter_scroll", scroll.id())
        assertEquals("1", scroll.attr("layout_weight"))
        assertEquals(scroll.parentNode, bar.parentNode)
        assertEquals("wrap_content", bar.attr("layout_height"))
        assertFalse(bar.hasAttributeNS(android, "layout_weight"))
        val buttons = (0 until bar.childNodes.length).mapNotNull { bar.childNodes.item(it) as? Element }
        assertEquals(listOf("btn_logs_filter_reset", "btn_logs_filter_cancel", "btn_logs_filter_apply"), buttons.map { it.id() })
        assertEquals(listOf("@string/reset", "@string/cancel", "@string/m3_apply"), buttons.map { it.attr("text") })
        for (button in buttons) {
            assertEquals("com.google.android.material.button.MaterialButton", button.tagName)
            assertEquals("wrap_content", button.attr("layout_height"))
        }
    }

    @Test fun fullWidthLabelledFieldsWithoutBoldSideLabels() {
        val all = elements(layout)
        for (e in all) {
            assertFalse("bold ${e.tagName}", e.attr("textStyle").contains("bold"))
            assertTrue("hard-coded text size ${e.id()}", e.attr("textSize").isEmpty())
            if (e.tagName == "TextView") assertTrue("TextView must be a section title or supporting text", e.getAttribute("style") in setOf("@style/Form.SectionTitle", "@style/Form.Supporting"))
            if (e.tagName == "LinearLayout") assertFalse("horizontal label rows are gone: ${e.id()}", e.attr("orientation") == "horizontal")
        }
        val fields = all.filter { it.tagName == "com.google.android.material.textfield.TextInputLayout" }
        assertEquals(5, fields.size)
        for (field in fields) {
            assertTrue(field.getAttribute("style").startsWith("@style/Form.Field"))
            assertTrue("labelled: ${field.id()}", field.attr("hint").startsWith("@string/"))
            assertFalse(field.hasAttributeNS(android, "layout_weight"))
        }
        val body = all.single { it.id() == "logs_filter_body" }
        assertEquals("?attr/dialogPreferredPadding", body.attr("paddingStart"))
        assertEquals("?attr/dialogPreferredPadding", body.attr("paddingEnd"))
        for (id in listOf("et_start_time", "et_end_time")) {
            val time = all.single { it.id() == id }
            assertEquals("none", time.attr("inputType"))
            assertEquals("false", time.attr("focusableInTouchMode"))
        }
        // Every option is a full-width row, never weight-based columns.
        for (e in all.filter { it.tagName.endsWith("RadioButton") || it.tagName.endsWith("TargetCheckBox") }) {
            assertTrue(e.id(), e.getAttribute("style") in setOf("@style/Form.Option", "@style/Form.CheckRow"))
            assertTrue(e.id(), e.attr("text").startsWith("@string/"))
        }
    }

    @Test fun dialogIsDismissibleAndBuiltWithoutTheMessageWrapper() {
        val source = fragment.readText()
        val start = source.indexOf("private fun showLogsFilterDialog")
        assertTrue(start > 0)
        val body = source.substring(start, source.indexOf("\n    }\n", start))
        assertTrue(body.contains("MaterialAlertDialogBuilder("))
        assertFalse(body.contains("MaterialDialog.Builder"))
        assertFalse(body.contains("setMessage"))
        assertFalse(body.contains("setPositiveButton") || body.contains("setNeutralButton") || body.contains("setNegativeButton"))
        assertTrue(body.contains(".setCancelable(true)"))
        assertTrue(body.contains("setCanceledOnTouchOutside(true)"))
        assertTrue(body.contains("LogsFilterSelection.fromFilter(currentFilter)"))
        // Cancel only cancels; only Apply and Reset assign the applied filter.
        assertTrue(body.contains("btn_logs_filter_cancel).setOnClickListener { dialog.cancel() }"))
        assertEquals(2, Regex("currentFilter = ").findAll(body).count())
        assertFalse(source.contains(".cancelable(false)"))
    }

    @Test fun namespacedStringsExistInAllLocales() {
        fun keys(locale: String): Map<String, String> {
            val file = File(res, "$locale/logs_filter_strings.xml")
            assertTrue(file.path, file.isFile)
            return elements(file).filter { it.tagName == "string" }.associate { it.getAttribute("name") to it.textContent }
        }
        val base = keys("values")
        assertTrue(base.keys.all { it.startsWith("logs_filter_") })
        assertEquals(base, keys("values-en"))
        assertEquals(base.keys, keys("values-nl").keys)
        val used = Regex("@string/(logs_filter_\\w+)").findAll(layout.readText()).map { it.groupValues[1] }.toSet() +
            Regex("R\\.string\\.(logs_filter_\\w+)").findAll(fragment.readText()).map { it.groupValues[1] }.toSet()
        assertEquals("unused or missing logs_filter_ strings", base.keys, used)
        assertNotNull(File(res, "drawable/logs_filter_ic_clear.xml").takeIf { it.isFile && !it.readText().contains("?attr/") })
    }
}
