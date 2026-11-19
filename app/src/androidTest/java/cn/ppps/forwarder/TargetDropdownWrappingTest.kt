package cn.ppps.forwarder

import android.view.ContextThemeWrapper
import android.view.View
import android.widget.LinearLayout
import android.widget.AutoCompleteTextView
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import cn.ppps.forwarder.widget.material.TargetDropdown
import cn.ppps.forwarder.widget.material.TargetForm
import org.junit.Assert.*
import org.junit.Test

class TargetDropdownWrappingTest {
    @Test fun actualBarkAdapterUsesWrappingRows() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val main = inst.startActivitySync(android.content.Intent(inst.targetContext, cn.ppps.forwarder.activity.MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) as cn.ppps.forwarder.activity.MainActivity
        inst.runOnMainSync { main.openNewPage(cn.ppps.forwarder.fragment.senders.BarkFragment::class.java) }
        Thread.sleep(1500)
        inst.waitForIdleSync()
        inst.runOnMainSync {
            val activity = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).first()
            val d = activity.findViewById<TargetDropdown>(R.id.sp_level)
            assertNotNull(d)
            val input = d.editText as AutoCompleteTextView
            val row = input.adapter.getView(2,null,LinearLayout(activity)) as TextView
            input.showDropDown()
            input.postDelayed({
                try {
                    val wm = Class.forName("android.view.WindowManagerGlobal")
                    val instance = wm.getMethod("getInstance").invoke(null)
                    val field = wm.getDeclaredField("mViews").apply { isAccessible = true }
                    val roots = field.get(instance) as List<*>
                    fun inspect(v: View) {
                        if (v is TextView && (v.text.toString().startsWith("Time-sensitive") || v.text.toString().startsWith("Passive:"))) {
                            assertFalse("Visible popup must not horizontally scroll", v.isHorizontallyScrollable)
                            assertTrue("Visible long popup row must wrap", v.lineCount > 1)
                        }
                        if (v is TextView && (v.text.toString().startsWith("Time-sensitive") || v.text.toString().startsWith("Passive:")))
                            android.util.Log.i("PopupProbe", "text=${v.text} width=${v.width} height=${v.height} lines=${v.lineCount} max=${v.maxLines} layoutWidth=${v.layout?.width} paintWidth=${v.paint.measureText(v.text.toString())} horizontal=${v.isHorizontallyScrollable} layoutEnd=${v.layout?.getLineEnd(v.lineCount-1)} len=${v.text.length}")
                        if (v is android.view.ViewGroup) for(i in 0 until v.childCount) inspect(v.getChildAt(i))
                    }
                    roots.filterIsInstance<View>().forEach(::inspect)
                } catch(e: Exception) { android.util.Log.e("PopupProbe","inspect",e) }
            },500)
            row.measure(View.MeasureSpec.makeMeasureSpec(input.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
            println("ACTUAL adapter=${input.adapter.javaClass.name} width=${input.width} row=${row.text} lines=${row.lineCount} max=${row.maxLines}")
            assertTrue("Actual adapter must wrap ${input.adapter.javaClass.name}",row.lineCount>1)
        }
        Thread.sleep(1200)
        inst.runOnMainSync {
            androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).toList().forEach { it.finish() }
            main.finish()
        }
    }

    @Test fun longPopupRowsWrapAtPhoneWidth() {
        val inst = InstrumentationRegistry.getInstrumentation()
        inst.runOnMainSync {
            val context = ContextThemeWrapper(inst.targetContext, R.style.AppTheme)
            val d = TargetDropdown(context)
            d.setItems(listOf("Critical: sound even in silent mode", "Time-sensitive: allow during Focus mode", "Passive: add to notification list without alerting"))
            TargetForm.wrapDropdown(d)
            val width = (328 * context.resources.displayMetrics.density).toInt()
            d.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            d.layout(0,0,width,d.measuredHeight)
            val input = d.editText as AutoCompleteTextView
            val parent = LinearLayout(context)
            for (i in 1..2) {
                val row = input.adapter.getView(i,null,parent) as TextView
                row.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
                row.layout(0,0,width,row.measuredHeight)
                println("POPUP row=$i adapter=${input.adapter.javaClass.name} width=$width lines=${row.lineCount} height=${row.height} max=${row.maxLines}")
                assertTrue("Long option should wrap: ${row.text}",row.lineCount>1)
            }
        }
    }
}
