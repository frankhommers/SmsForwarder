package cn.ppps.forwarder

import android.content.Intent
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.fragment.app.FragmentActivity
import cn.ppps.forwarder.activity.MainActivity
import cn.ppps.forwarder.fragment.ServerFragment
import cn.ppps.forwarder.utils.SharedPreference
import cn.ppps.forwarder.utils.HttpServerUtils
import com.xuexiang.xutil.app.ServiceUtils
import org.junit.Assert.*
import org.junit.Test

class ServerOptionsTest {
 @Test fun cloneAvailabilityAndToleranceReadout() {
  val ins=InstrumentationRegistry.getInstrumentation()
  val prefs=SharedPreference.preference
  val saved=prefs.all.toMap()
  assertFalse(ServiceUtils.isServiceRunning("cn.ppps.forwarder.service.HttpServerService"))
  val main=ins.startActivitySync(Intent(ins.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
  var page:ServerFragment?=null
  try {
   ins.runOnMainSync{main.openNewPage(ServerFragment::class.java)}
   for(i in 0 until 50){ins.waitForIdleSync();ins.runOnMainSync{
    page=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<FragmentActivity>().flatMap{it.supportFragmentManager.fragments}.filterIsInstance<ServerFragment>().firstOrNull{it.isVisible&&it.isResumed}
   };if(page!=null)break;Thread.sleep(100)}
   assertNotNull(page);Thread.sleep(1200)
   fun reveal(v:View){ins.runOnMainSync{v.requestRectangleOnScreen(android.graphics.Rect(0,0,v.width,v.height),true)};ins.waitForIdleSync();Thread.sleep(250)}
   val b=page!!.binding!!
   for((id,available) in listOf(R.id.rb_safety_measures_rsa to true,R.id.rb_safety_measures_sm4 to true,R.id.rb_safety_measures_sign to false,R.id.rb_safety_measures_none to false)){
    ins.runOnMainSync{b.rgSafetyMeasures.check(id)};ins.waitForIdleSync()
    ins.runOnMainSync{assertEquals(available,b.sbApiClone.isEnabled);assertEquals(if(available)View.GONE else View.VISIBLE,b.tvCloneEncryptionRequirement.visibility)}
    if(!available){reveal(b.sbApiClone);onView(withId(R.id.sb_api_clone)).perform(click());ins.runOnMainSync{assertFalse(b.sbApiClone.isChecked);assertFalse(HttpServerUtils.enableApiClone)}}
   }
   ins.runOnMainSync{
    b.rgSafetyMeasures.check(R.id.rb_safety_measures_sign)
   }
   reveal(b.xsbTimeTolerance)
   ins.runOnMainSync {
    val slider=b.xsbTimeTolerance
    slider.value=299f
    slider.requestFocus()
   }
   onView(withId(R.id.xsb_time_tolerance)).perform(androidx.test.espresso.action.ViewActions.swipeRight())
   ins.waitForIdleSync()
   ins.runOnMainSync {
    val selected=b.xsbTimeTolerance.selectedNumber
    assertNotEquals("Gesture must change value",299,selected)
    assertEquals(selected,HttpServerUtils.timeTolerance)
    assertEquals(page!!.getString(R.string.infra_form_time_tolerance_value,selected),b.tvTimeToleranceValue.text.toString())
   }
   reveal(b.tvTimeToleranceValue)
   val dir=java.io.File(ins.targetContext.getExternalFilesDir(null),"server-options").apply{mkdirs()}
   java.io.FileOutputStream(java.io.File(dir,"tolerance.png")).use{ins.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
   assertFalse(ServiceUtils.isServiceRunning("cn.ppps.forwarder.service.HttpServerService"))
  } finally {
   ins.runOnMainSync{page?.activity?.finish();main.finish()};ins.waitForIdleSync()
   val edit=prefs.edit().clear()
   for((k,v) in saved)when(v){is String->edit.putString(k,v);is Boolean->edit.putBoolean(k,v);is Int->edit.putInt(k,v);is Long->edit.putLong(k,v);is Float->edit.putFloat(k,v);is Set<*>->edit.putStringSet(k,v.filterIsInstance<String>().toSet())}
   assertTrue(edit.commit());assertEquals(saved,prefs.all)
  }
 }
}
