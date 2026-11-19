package cn.ppps.forwarder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataInputStream

/**
 * Source-rebuilt dependency classes must keep symbolic R references (getstatic R$type.name), as their
 * pinned upstream classes do. Inlined 0x7f resource IDs would go stale after any app resource edit.
 */
class EnglishDependencyResourceLinkageTest {
    private class Pool(val ints: List<Int>, val rFields: Set<String>)

    private fun pool(className: String): Pool {
        val stream = javaClass.classLoader!!.getResourceAsStream(className.replace('.', '/') + ".class")
            ?: error("missing $className on the unit-test classpath")
        DataInputStream(stream.buffered()).use { input ->
            check(input.readInt() == 0xCAFEBABE.toInt())
            input.readUnsignedShort(); input.readUnsignedShort()
            val count = input.readUnsignedShort()
            val utf = HashMap<Int, String>(); val classes = HashMap<Int, Int>(); val nameTypes = HashMap<Int, Int>()
            val fields = ArrayList<Pair<Int, Int>>(); val ints = ArrayList<Int>()
            var i = 1
            while (i < count) {
                when (val tag = input.readUnsignedByte()) {
                    1 -> utf[i] = input.readUTF()
                    3 -> ints += input.readInt()
                    4 -> input.readInt()
                    5, 6 -> { input.readLong(); i++ }
                    7 -> classes[i] = input.readUnsignedShort()
                    8, 16, 19, 20 -> input.readUnsignedShort()
                    9 -> fields += input.readUnsignedShort() to input.readUnsignedShort()
                    10, 11, 17, 18 -> input.readInt()
                    12 -> { nameTypes[i] = input.readUnsignedShort(); input.readUnsignedShort() }
                    15 -> { input.readUnsignedByte(); input.readUnsignedShort() }
                    else -> error("constant tag $tag")
                }
                i++
            }
            val rFields = fields.map { (c, nt) -> utf[classes[c]!!]!! + "." + utf[nameTypes[nt]!!]!! }
                .filter { Regex("""(^|/)R\$[A-Za-z]+\.""").containsMatchIn(it) }.toSet()
            return Pool(ints, rFields)
        }
    }

    private val expected = mapOf(
        "com.xuexiang.xui.utils.SnackbarUtils" to setOf("com/xuexiang/xui/R\$id.snackbar_text", "com/xuexiang/xui/R\$id.snackbar_action"),
        "com.xuexiang.xui.XUI" to setOf("com/xuexiang/xui/R\$style.XUITheme_Phone", "com/xuexiang/xui/R\$style.XUITheme_Tablet_Small",
            "com/xuexiang/xui/R\$style.XUITheme_Tablet_Big", "com/xuexiang/xui/R\$attr.fontPath"),
        "com.xuexiang.xui.widget.tabbar.XUIItemViewsAdapter" to setOf("com/xuexiang/xui/R\$id.xui_view_can_not_cache_tag"),
        "com.scwang.smartrefresh.layout.SmartRefreshLayout" to setOf("com/scwang/smartrefresh/layout/R\$string.srl_content_empty"),
        "com.gyf.cactus.ext.NotificationExtKt" to setOf("com/gyf/cactus/R\$drawable.icon_cactus_trans"),
        "com.just.agentweb.download.DefaultDownloadImpl" to setOf("com/just/agentweb/download/R\$string.agentweb_download_task_has_been_exist",
            "com/just/agentweb/download/R\$string.agentweb_coming_soon_download"),
        "com.xuexiang.xui.widget.picker.wheelview.WheelView" to setOf("com/xuexiang/xui/R\$dimen.default_wheel_view_text_size"),
        "com.xuexiang.xui.widget.imageview.RadiusImageView" to setOf("com/xuexiang/xui/R\$attr.RadiusImageViewStyle"),
    )

    @Test fun rebuiltClassesReferenceResourcesSymbolically() {
        for ((name, refs) in expected) {
            val pool = pool(name)
            val inlined = pool.ints.filter { it ushr 24 == 0x7f && (it ushr 16 and 0xff) in 1..0x3f }
            assertEquals("$name inlines app resource IDs ${inlined.map { Integer.toHexString(it) }}", emptyList<Int>(), inlined)
            assertTrue("$name lacks ${refs - pool.rFields}", pool.rFields.containsAll(refs))
        }
    }

    @Test fun libraryResourceSymbolsResolveToTheAppResources() {
        assertEquals(R.id.snackbar_text, com.xuexiang.xui.R.id.snackbar_text)
        assertEquals(R.style.XUITheme_Phone, com.xuexiang.xui.R.style.XUITheme_Phone)
        assertEquals(R.string.srl_content_empty, com.scwang.smartrefresh.layout.R.string.srl_content_empty)
        assertEquals(R.drawable.icon_cactus_trans, com.gyf.cactus.R.drawable.icon_cactus_trans)
    }
}
