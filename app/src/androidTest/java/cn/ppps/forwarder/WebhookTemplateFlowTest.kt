package cn.ppps.forwarder
import android.content.Intent
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.fragment.app.FragmentActivity
import cn.ppps.forwarder.activity.MainActivity
import cn.ppps.forwarder.fragment.senders.WebhookFragment
import org.junit.Assert.*
import org.junit.Test
class WebhookTemplateFlowTest {
 @Test fun insertIntoJsonValueAndPreviewWithoutSending() {
  val ins=InstrumentationRegistry.getInstrumentation()
  val lang=InstrumentationRegistry.getArguments().getString("language","en")!!
  com.hjq.language.MultiLanguages.setAppLanguage(ins.targetContext,java.util.Locale(lang))
  val main=ins.startActivitySync(Intent(ins.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
  var page:WebhookFragment?=null
  val dir=java.io.File(ins.targetContext.getExternalFilesDir(null),"webhook-template-flow/$lang").apply{mkdirs()}
  fun shot(name:String){ins.waitForIdleSync();Thread.sleep(500);java.io.FileOutputStream(java.io.File(dir,name+".png")).use{ins.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}
  try {
   ins.runOnMainSync{main.openNewPage(WebhookFragment::class.java)}
   for(i in 0 until 50){ins.waitForIdleSync();ins.runOnMainSync{page=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<FragmentActivity>().flatMap{it.supportFragmentManager.fragments}.filterIsInstance<WebhookFragment>().firstOrNull{it.isResumed&&it.isVisible}};if(page!=null)break;Thread.sleep(100)}
   assertNotNull(page);Thread.sleep(1200)
   val b=page!!.binding!!
   ins.runOnMainSync{b.rbPayloadJson.performClick();b.etWebParams.setText("{\"text\":\"\"}");b.etWebParams.requestFocus();b.etWebParams.setSelection(9)}
   fun action(res:Int){var v:View?=null;onView(withText(res)).check{view,e->if(e!=null)throw e;v=view};ins.runOnMainSync{v!!.requestRectangleOnScreen(android.graphics.Rect(0,0,v!!.width,v!!.height),true)};ins.waitForIdleSync();Thread.sleep(300);onView(withText(res)).perform(click())}
   action(R.string.template_insert_data)
   onView(withText("[org_content]")).inRoot(isDialog()).perform(click())
   val expected="{\"text\":\"[org_content]\"}"
   ins.runOnMainSync{assertEquals(expected,b.etWebParams.text.toString());assertEquals("[org_content]",org.json.JSONObject(expected).getString("text"))}
   action(R.string.template_preview)
   onView(withText(R.string.template_preview_sample_notice)).inRoot(isDialog()).check(matches(isDisplayed()))
   val rendered="{\"text\":\""+page!!.getString(R.string.template_sample_sms)+"\"}"
   onView(withText(rendered)).inRoot(isDialog()).check(matches(isDisplayed()))
   shot("json-preview")
   Espresso.pressBack();ins.waitForIdleSync()
   ins.runOnMainSync{assertEquals(expected,b.etWebParams.text.toString());assertTrue(page!!.isResumed && page!!.isVisible);b.etWebParams.setText("{\"app\":\"{{APP_NAME}}\"}")}
   action(R.string.template_preview)
   onView(withText(R.string.template_preview_app_name_requirement)).inRoot(isDialog()).check(matches(isDisplayed()))
   shot("app-name-warning")
   Espresso.pressBack();ins.waitForIdleSync()
   ins.runOnMainSync{assertTrue(page!!.isResumed && page!!.isVisible);assertEquals("{\"app\":\"{{APP_NAME}}\"}",b.etWebParams.text.toString())}
  } finally {ins.runOnMainSync{page?.activity?.finish();main.finish()}}
 }
}
