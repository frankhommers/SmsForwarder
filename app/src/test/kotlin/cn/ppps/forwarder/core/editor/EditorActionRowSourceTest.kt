package cn.ppps.forwarder.core.editor

import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Source/layout contract checks for the shared editor action row. These read files; they do
 * not inflate layouts or observe a keyboard, so they are not device evidence.
 */
class EditorActionRowSourceTest {
    private val module: File = listOf(File("."), File("app")).first { File(it, "src/main/res/layout").isDirectory }
    private fun main(path: String) = File(module, "src/main/$path")
    private val helper = main("kotlin/cn/ppps/forwarder/core/editor/EditorActionRow.kt").readText()
    private val controller = main("kotlin/cn/ppps/forwarder/core/editor/ImeActionRowController.kt").readText()
    private val base = main("kotlin/cn/ppps/forwarder/core/BaseFragment.kt").readText()
    private val ns = "http://schemas.android.com/apk/res/android"

    private fun Element.children() = (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>()

    /** Every layout with btn_save: vertical root whose direct child is a buttons-only row with btn_save. */
    @Test fun everyEditorLayoutMatchesTheStrictRowContract() {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val editors = main("res").listFiles { f -> f.name.startsWith("layout") }!!.flatMap { it.listFiles()!!.toList() }
            .filter { it.readText().contains("@+id/btn_save") }
        assertTrue("expected the known editor family, found ${editors.size}", editors.size >= 40)
        editors.forEach { file ->
            val root = factory.newDocumentBuilder().parse(file).documentElement
            assertEquals(file.name, "LinearLayout", root.tagName)
            assertEquals(file.name, "vertical", root.getAttributeNS(ns, "orientation"))
            val rows = root.children().filter { row -> row.children().any { it.getAttributeNS(ns, "id") == "@+id/btn_save" } }
            assertEquals("${file.name}: btn_save row must be a direct child of the root", 1, rows.size)
            val row = rows.single()
            assertEquals(file.name, "LinearLayout", row.tagName)
            assertTrue("${file.name}: row holds only buttons", row.children().all { it.tagName.endsWith("Button") })
            assertEquals("${file.name}: row is the fixed footer", row, root.children().last())
        }
    }

    @Test fun baseFragmentOwnsOneHelperPerViewAndReleasesIt() {
        assertFalse("old one-shot doOnLayout sizing removed", base.contains("doOnLayout"))
        val update = base.substringAfter("private fun updateActionRow()").substringBefore("protected open fun initTitle")
        assertTrue("previous helper released before installing", update.indexOf("actionRow?.release()") in 0 until update.indexOf("EditorActionRow.install"))
        val destroy = base.substringAfter("override fun onDestroyView()").substringBefore("binding = null")
        assertTrue("released in onDestroyView before binding is cleared", destroy.contains("actionRow?.release()") && destroy.contains("actionRow = null"))
        val config = base.substringAfter("override fun onConfigurationChanged").substringBefore("override fun onDestroyView")
        assertTrue(config.contains("actionRow?.onConfigurationChanged()"))
        assertFalse("rotation must not reinstall listeners", config.contains("updateActionRow()"))
        assertEquals("installed from initPage only", 2, Regex("updateActionRow\\(\\)").findAll(base).count())
    }

    @Test fun helperRemovesEveryListenerItAdds() {
        listOf("OnLayoutChangeListener(rootLayoutListener)", "OnAttachStateChangeListener(attachListener)",
            "OnLayoutChangeListener(rowLayoutListener)", "OnPreDrawListener(preDraw)").forEach {
            assertTrue("adds $it", helper.contains("add$it"))
            assertTrue("removes $it", helper.contains("remove$it"))
        }
        assertTrue("duplicate install guarded by a view tag", helper.contains("getTag(TAG_KEY)") && helper.contains("setTag(TAG_KEY, null)"))
        assertTrue("pre-draw hook is one-shot", helper.substringAfter("OnPreDrawListener {").trimStart().startsWith("removePreDraw()"))
    }

    @Test fun noWindowShortcutsInsetStompingOrLoops() {
        val code = helper + controller
        listOf("setOnApplyWindowInsetsListener", "requestApplyInsets", ".consume", "CONSUMED", "setSoftInputMode", "SOFT_INPUT_ADJUST",
            "FLAG_FULLSCREEN", "addFlags", "setDecorFitsSystemWindows", "OnGlobalLayoutListener", "getWindowVisibleDisplayFrame",
            "onDraw", "requestFocus", "clearFocus", "scrollTo", "scrollBy", "smoothScroll", "postDelayed", "requestLayout()",
        ).forEach { assertFalse("forbidden: $it", code.contains(it)) }
        assertTrue("uses AndroidX insets", helper.contains("ViewCompat.getRootWindowInsets") && helper.contains("WindowInsetsCompat.Type.ime()"))
        assertFalse("helper never edits base fragment content padding/scroll", helper.contains("setPadding"))
    }

    @Test fun manifestKeepsDeclaredSoftInputModes() {
        val manifest = main("AndroidManifest.xml").readText()
        assertFalse(manifest.contains("adjustPan"))
        assertTrue(manifest.contains("adjustResize|stateHidden"))
    }
}
