package cn.ppps.forwarder

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.fragment.app.FragmentActivity
import cn.ppps.forwarder.activity.MainActivity
import cn.ppps.forwarder.fragment.condition.CronFragment
import com.google.android.material.textfield.TextInputLayout
import org.junit.Assert.*
import org.junit.Test

/** Cron Test only computes local dates; no task or delivery is executed. */
class CronEditorValidationTest {
 @Test fun invalidFieldHasInlineErrorAndValidTestShowsNextTimes() {
  val ins=InstrumentationRegistry.getInstrumentation()
  val main=ins.startActivitySync(Intent(ins.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
  var fragment:CronFragment?=null
  try {
   ins.runOnMainSync { main.openNewPage(CronFragment::class.java) }
   for (attempt in 0 until 50) {
    ins.waitForIdleSync()
    ins.runOnMainSync {
     fragment=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
      .filterIsInstance<FragmentActivity>().flatMap{it.supportFragmentManager.fragments}
      .filterIsInstance<CronFragment>().firstOrNull{it.isResumed&&it.isVisible}
    }
    if(fragment!=null)break
    Thread.sleep(100)
   }
   assertNotNull(fragment)
   ins.runOnMainSync {
    val b=fragment!!.binding!!
    b.etSecond.setText("abc")
    val box=generateSequence(b.etSecond.parent){it.parent}.filterIsInstance<TextInputLayout>().first()
    assertFalse("Invalid input needs inline error",box.error.isNullOrBlank())
    b.etSecond.setText("0")
    b.etMinute.setText("*");b.etHour.setText("*");b.etDay.setText("*");b.etMonth.setText("*");b.etWeek.setText("?");b.etYear.setText("*")
    assertTrue("Corrected input clears error",box.error.isNullOrBlank())
    b.btnTest.performClick()
   }
   var ready=false
   for(attempt in 0 until 100) {
    ins.waitForIdleSync()
    ins.runOnMainSync { ready=fragment!!.binding!!.tvNextTimeList.text.contains(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) }
    if(ready)break
    Thread.sleep(100)
   }
   assertTrue("Test must show computed dates",ready)
  } finally { ins.runOnMainSync { fragment?.activity?.finish();main.finish() } }
 }
}
