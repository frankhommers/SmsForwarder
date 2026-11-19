package cn.ppps.forwarder

import android.content.Intent
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.fragment.app.FragmentActivity
import cn.ppps.forwarder.activity.MainActivity
import cn.ppps.forwarder.fragment.senders.GotifyFragment
import org.junit.Assert.*
import org.junit.Test

/** Synthetic draft only; never saves or invokes transport Test. */
class TemplateInteractionTest {
 @Test fun insertAtCursorCancelAndPreviewPreserveDraft() {
  val ins=InstrumentationRegistry.getInstrumentation()
  val lang=InstrumentationRegistry.getArguments().getString("language","en")!!
  com.hjq.language.MultiLanguages.setAppLanguage(ins.targetContext,java.util.Locale(lang))
  val main=ins.startActivitySync(Intent(ins.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
  var page:GotifyFragment?=null
  val dir=java.io.File(ins.targetContext.getExternalFilesDir(null),"template-flow/$lang").apply{mkdirs()}
  fun shot(name:String){ins.waitForIdleSync();Thread.sleep(500);java.io.FileOutputStream(java.io.File(dir,name+".png")).use{ins.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}
  fun reveal(v:View){ins.runOnMainSync{v.requestRectangleOnScreen(android.graphics.Rect(0,0,v.width,v.height),true)};ins.waitForIdleSync();Thread.sleep(300)}
  try {
   ins.runOnMainSync{main.openNewPage(GotifyFragment::class.java)}
   for(i in 0 until 50){ins.waitForIdleSync();ins.runOnMainSync{page=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<FragmentActivity>().flatMap{it.supportFragmentManager.fragments}.filterIsInstance<GotifyFragment>().firstOrNull{it.isResumed&&it.isVisible}};if(page!=null)break;Thread.sleep(100)}
   assertNotNull(page);Thread.sleep(1200)
   val input=page!!.binding!!.etTitleTemplate
   ins.runOnMainSync{input.setText("Before  after");input.requestFocus();input.setSelection(7)}
   fun action(id:Int){
    var button:View?=null
    onView(withText(id)).check{v,e->if(e!=null)throw e;button=v}
    reveal(button!!);onView(withText(id)).perform(click())
   }
   action(R.string.template_insert_data)
   shot("picker")
   onView(withText("{{FROM}}")).inRoot(isDialog()).perform(click())
   ins.runOnMainSync{assertEquals("Before {{FROM}} after",input.text.toString())}
   action(R.string.template_insert_data)
   onView(withText(R.string.cancel)).inRoot(isDialog()).perform(click())
   ins.runOnMainSync{assertEquals("Before {{FROM}} after",input.text.toString())}
   action(R.string.template_preview)
   onView(withText(R.string.template_preview_sample_notice)).inRoot(isDialog()).check(matches(isDisplayed()))
   onView(withText("Before +31612345678 after")).inRoot(isDialog()).check(matches(isDisplayed()))
   shot("preview")
   onView(withText(R.string.m3_close)).inRoot(isDialog()).perform(click())
   ins.runOnMainSync{assertEquals("Before {{FROM}} after",input.text.toString())}
  } finally {ins.runOnMainSync{page?.activity?.finish();main.finish()}}
 }
}
