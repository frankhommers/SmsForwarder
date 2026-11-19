package cn.ppps.forwarder

import android.graphics.Bitmap
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import cn.ppps.forwarder.activity.MainActivity
import cn.ppps.forwarder.fragment.SendersFragment
import com.hjq.language.MultiLanguages
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Locale

/** Read-only capture of existing emulator fixtures; never creates, edits or sends a target. */
class SettledTargetsCaptureTest {
    @Test fun populatedTargetsAreFullyOpaqueAfterLoading() {
        val ins = InstrumentationRegistry.getInstrumentation()
        val context = ins.targetContext
        val language = InstrumentationRegistry.getArguments().getString("language", "en")!!
        val previous = MultiLanguages.getAppLanguage(context)
        fun walk(v: View): List<View> = listOf(v) + if (v is ViewGroup)
            (0 until v.childCount).flatMap { walk(v.getChildAt(it)) } else emptyList<View>()
        fun effectiveAlpha(v: View): Float {
            var value = v.alpha
            var parent = v.parent
            while (parent is View) {
                val ancestor: View = parent
                value *= ancestor.alpha
                parent = ancestor.parent
            }
            return value
        }
        try {
            MultiLanguages.setAppLanguage(context, Locale(language))
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var activity: MainActivity
                scenario.onActivity { activity = it; it.openRoute(MainActivity.Route.TARGETS) }
                var consecutive = 0
                var lastState = "No target view"
                for (attempt in 0 until 100) {
                    ins.waitForIdleSync()
                    ins.runOnMainSync {
                        val fragment = activity.supportFragmentManager.fragments
                            .filterIsInstance<SendersFragment>().firstOrNull { it.isVisible && it.isResumed }
                        val root = fragment?.view
                        val list = root?.let { walk(it).filterIsInstance<RecyclerView>().firstOrNull() }
                        val count = list?.adapter?.itemCount ?: 0
                        val children = if (list == null) emptyList() else (0 until list.childCount).map { list.getChildAt(it) }
                        val opaque = children.isNotEmpty() && children.all { effectiveAlpha(it) >= 0.99f && it.isShown && it.getGlobalVisibleRect(Rect()) }
                        val idle = list != null && list.scrollState == RecyclerView.SCROLL_STATE_IDLE && !list.isAnimating
                        lastState = "items=$count children=${children.size} opaque=$opaque idle=$idle"
                        consecutive = if (count > 0 && opaque && idle) consecutive + 1 else 0
                    }
                    if (consecutive >= 5) break
                    Thread.sleep(200)
                }
                assertTrue("Existing populated fixtures must settle: $lastState", consecutive >= 5)
                val out = File(context.getExternalFilesDir(null), "settled-targets/$language").apply { mkdirs() }
                File(out, "state.txt").writeText(lastState)
                File(out, "targets.png").outputStream().use {
                    ins.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        } finally {
            MultiLanguages.setAppLanguage(context, previous)
        }
    }
}
