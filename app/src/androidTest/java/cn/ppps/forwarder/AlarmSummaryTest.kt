package cn.ppps.forwarder
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.fragment.app.FragmentActivity
import cn.ppps.forwarder.activity.MainActivity
import cn.ppps.forwarder.fragment.action.AlarmFragment
import org.junit.Assert.*
import org.junit.Test

/** Reads and edits a draft only. Never presses Apply or Test. */
class AlarmSummaryTest {
 @Test fun summaryIsReadableAndPatternsPreserved() {
  val ins=InstrumentationRegistry.getInstrumentation()
  val lang=InstrumentationRegistry.getArguments().getString("language","en")!!
  com.hjq.language.MultiLanguages.setAppLanguage(ins.targetContext,java.util.Locale(lang))
  val main=ins.startActivitySync(Intent(ins.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
  var page:AlarmFragment?=null
  try {
   ins.runOnMainSync{main.openNewPage(AlarmFragment::class.java)}
   for(i in 0 until 50){ins.waitForIdleSync();ins.runOnMainSync{page=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<FragmentActivity>().flatMap{it.supportFragmentManager.fragments}.filterIsInstance<AlarmFragment>().firstOrNull{it.isResumed&&it.isVisible}};if(page!=null)break;Thread.sleep(100)}
   assertNotNull(page);Thread.sleep(1200)
   ins.runOnMainSync {
    val b=page!!.binding!!
    b.sbEnableMusic.isChecked=true;b.sbEnableVibrate.isChecked=true;b.sbEnableFlash.isChecked=true
    b.etVibrationEffect.setText("---___===___");b.etFlashEffect.setText("XXOOXXOO")
    val method=AlarmFragment::class.java.getDeclaredMethod("checkSetting",Boolean::class.javaPrimitiveType)
    method.isAccessible=true;method.invoke(page,true)
    val text=b.tvDescription.text.toString()
    assertTrue(text.contains(page!!.getString(R.string.review_alarm_vibration)))
    assertTrue(text.contains(page!!.getString(R.string.review_alarm_flash)))
    assertTrue(text.contains("\n"));assertFalse(text.contains("---___"));assertFalse(text.contains("XXOO"))
    assertEquals("---___===___",b.etVibrationEffect.text.toString());assertEquals("XXOOXXOO",b.etFlashEffect.text.toString())
   }
   val dir=java.io.File(ins.targetContext.getExternalFilesDir(null),"alarm-summary/$lang").apply{mkdirs()}
   java.io.FileOutputStream(java.io.File(dir,"summary.png")).use{ins.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
  } finally {ins.runOnMainSync{page?.activity?.finish();main.finish()}}
 }
}
