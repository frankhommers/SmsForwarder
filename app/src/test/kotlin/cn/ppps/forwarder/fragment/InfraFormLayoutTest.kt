package cn.ppps.forwarder.fragment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Source gate for the infrastructure screens (Server, Client, Clone/backup, setup tips, Help, About links).
 * JVM only; rendered behaviour still needs a device run.
 */
class InfraFormLayoutTest {
    private val ns = "http://schemas.android.com/apk/res/android"
    private val res = File("src/main/res")
    private val kt = File("src/main/kotlin/cn/ppps/forwarder")

    private fun elements(name: String): List<Element> {
        val f = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val nodes = f.newDocumentBuilder().parse(File(res, "layout/$name.xml")).getElementsByTagName("*")
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }
    private fun Element.a(n: String) = getAttributeNS(ns, n)
    private fun Element.id() = a("id").substringAfter('/')

    @Test fun keptIdsKeepTheirTypes() {
        val server = elements("fragment_server").associateBy { it.id() }
        for (id in listOf("sb_server_local_ip_only", "scb_server_autorun")) assertEquals(id, "cn.ppps.forwarder.widget.material.TargetCheckBox", server[id]?.tagName)
        for (id in listOf("sb_api_clone", "sb_api_query_sms", "sb_api_send_sms", "sb_api_query_call", "sb_api_query_contacts", "sb_api_add_contacts", "sb_api_wol", "sb_api_location", "sb_api_config_query", "sb_api_query_battery"))
            assertEquals(id, "cn.ppps.forwarder.widget.material.TargetSwitch", server[id]?.tagName)
        assertEquals("cn.ppps.forwarder.widget.material.TargetSlider", server["xsb_time_tolerance"]?.tagName)
        val tips = elements("dialog_guide_tips").associateBy { it.id() }
        assertEquals("com.google.android.material.checkbox.MaterialCheckBox", tips["cb_ignore"]?.tagName)
        assertFalse("square default indicator, no custom round drawable", tips.getValue("cb_ignore").hasAttributeNS(ns, "button"))
        for (id in listOf("iv_top", "iv_close", "tv_title", "tv_content", "tv_previous", "tv_next")) assertTrue(id, tips.containsKey(id))
        val clone = elements("fragment_client_clone").associateBy { it.id() }
        for (id in listOf("btn_push", "btn_pull", "btn_export", "btn_import")) {
            val b = clone.getValue(id)
            assertEquals("com.google.android.material.button.MaterialButton", b.tagName)
            assertFalse("$id: no stretched ellipse drawable", b.hasAttributeNS(ns, "background"))
            assertTrue("$id: no hard-coded size/colour", b.a("textSize").isEmpty() && b.a("textColor").isEmpty())
            assertTrue(b.getAttribute("style").startsWith("@style/InfraForm.Button"))
        }
    }

    @Test fun singleKeylineNoBoldNoFixedSizes() {
        for (name in listOf("fragment_server", "fragment_client", "fragment_client_clone", "dialog_guide_tips")) {
            val all = elements(name)
            for (e in all) {
                assertFalse("$name bold ${e.id()}", e.a("textStyle").contains("bold"))
                assertTrue("$name textSize ${e.id()}", e.a("textSize").isEmpty())
                assertFalse("$name legacy BarStyle", e.getAttribute("style").startsWith("@style/BarStyle"))
                assertTrue("$name 5dp margins", e.a("layout_margin").isEmpty() && e.a("layout_marginStart") != "5dp")
            }
            if (name != "dialog_guide_tips") assertEquals(name, 1, all.count { it.getAttribute("style") == "@style/Form.Content" })
            for (f in all.filter { it.tagName == "com.google.android.material.textfield.TextInputLayout" }) {
                assertTrue("$name field style", f.getAttribute("style").startsWith("@style/Form.Field"))
                assertFalse("$name field never squeezed", f.hasAttributeNS(ns, "layout_weight"))
            }
            for (s in all.filter { it.tagName == "cn.ppps.forwarder.widget.material.TargetSwitch" }) assertEquals("@style/Form.SwitchRow", s.getAttribute("style"))
        }
    }

    @Test fun toleranceReadoutRegisteredAfterSliderListener() {
        val src = File(kt, "fragment/ServerFragment.kt").readText()
        val listener = src.indexOf("xsbTimeTolerance.setOnSeekBarListener")
        val readout = src.indexOf("xsbTimeTolerance.addOnChangeListener")
        assertTrue(listener > 0 && readout > listener)
        assertTrue(src.contains("getString(R.string.infra_form_time_tolerance_value, seconds)"))
        assertTrue("clone switch disabled without encryption", src.contains("binding!!.sbApiClone.isEnabled = available"))
    }

    @Test fun storagePermissionIsExplainedFirst() {
        val clone = File(kt, "fragment/client/CloneFragment.kt").readText()
        val init = clone.substringAfter("override fun initViews()").substringBefore("override fun initListeners()")
        assertFalse("no permission request straight from initViews", init.substringBefore("private fun").contains(".request("))
        assertTrue(clone.contains("R.string.infra_form_storage_clone_message"))
        val server = File(kt, "fragment/ServerFragment.kt").readText()
        assertTrue(server.indexOf("infra_form_storage_server_message") < server.indexOf("private fun pickWebClientFolder"))
    }

    @Test fun helpIsOfflineAndOnlineIsExplicit() {
        val main = File(kt, "activity/MainActivity.kt").readText()
        assertFalse(main.contains("goWeb(this, getString(R.string.url_help))"))
        assertTrue(main.contains("HelpFragment::class.java"))
        val help = File(kt, "fragment/HelpFragment.kt").readText()
        assertTrue(help.indexOf("blockNetworkLoads = true") in 0 until help.indexOf("loadMarkdownFromAsset"))
        assertFalse(help.contains("loadMarkdownFromUrl"))
        assertTrue(help.substringAfter("override fun initListeners()").contains("R.string.url_help"))
        for (f in listOf("help.md", "help_nl.md")) {
            val text = File("src/main/assets/help/$f").readText()
            assertFalse("$f: no remote content", Regex("https?://|<img|!\\[").containsMatchIn(text))
            assertTrue(text.lines().count { it.startsWith("## ") } >= 6)
        }
    }

    @Test fun aboutHasNoGiteeLabel() {
        val about = File(res, "layout/fragment_about.xml").readText()
        assertFalse(about.contains("@string/about_item_gitee"))
        assertTrue(about.contains("@string/infra_form_about_upstream") && about.contains("@string/infra_form_about_fork"))
        assertTrue(File(kt, "fragment/AboutFragment.kt").readText().contains("R.string.infra_form_url_project_fork"))
    }

    @Test fun stringsExistInDefaultEnglishAndDutch() {
        fun keys(dir: String) = Regex("name=\"(infra_form_[a-z_]+)\"").findAll(File(res, "$dir/infra_form_strings.xml").readText()).map { it.groupValues[1] }.toSet()
        val base = keys("values")
        assertTrue(base.size >= 15)
        assertEquals(base, keys("values-en")); assertEquals(base, keys("values-nl"))
    }
}
