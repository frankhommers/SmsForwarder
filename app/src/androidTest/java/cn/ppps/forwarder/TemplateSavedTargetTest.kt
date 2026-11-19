package cn.ppps.forwarder
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.fragment.app.FragmentActivity
import cn.ppps.forwarder.activity.MainActivity
import cn.ppps.forwarder.fragment.senders.GotifyFragment
import cn.ppps.forwarder.core.Core
import cn.ppps.forwarder.database.entity.Sender
import cn.ppps.forwarder.utils.*
import org.junit.Assert.*
import org.junit.Test
class TemplateSavedTargetTest {
 @Test fun insertIntoLoadedUntouchedTitle() {
  val ins=InstrumentationRegistry.getInstrumentation()
  val name="LOCAL saved template fixture"
  val json="""{"webServer":"https://example.invalid/message?token=fixture","title":"Existing title","priority":"1"}"""
  Core.sender.insert(Sender(0,TYPE_GOTIFY,name,json,0))
  val id=Core.sender.getAll().blockingGet().first{it.name==name}.id
  val main=ins.startActivitySync(Intent(ins.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
  var page:GotifyFragment?=null
  try {
   ins.runOnMainSync{com.xuexiang.xpage.core.PageOption.to(GotifyFragment::class.java).putLong(KEY_SENDER_ID,id).putInt(KEY_SENDER_TYPE,TYPE_GOTIFY).setNewActivity(true).open(main)}
   var loaded=false
   for(i in 0 until 70){ins.waitForIdleSync();ins.runOnMainSync{
    page=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<FragmentActivity>().flatMap{it.supportFragmentManager.fragments}.filterIsInstance<GotifyFragment>().firstOrNull{it.isResumed&&it.isVisible}
    loaded=page?.binding?.etTitleTemplate?.text?.toString()=="Existing title"
   };if(loaded)break;Thread.sleep(100)}
   assertTrue("Saved title loaded",loaded);Thread.sleep(1500)
   onView(withText(R.string.template_insert_data)).check{v,e->if(e!=null)throw e;v.requestRectangleOnScreen(android.graphics.Rect(0,0,v.width,v.height),true)}
   ins.waitForIdleSync();Thread.sleep(500)
   ins.runOnMainSync{println("BEFORE focus=${page!!.binding!!.etTitleTemplate.hasFocus()} selection=${page!!.binding!!.etTitleTemplate.selectionStart}");assertFalse(page!!.binding!!.etTitleTemplate.hasFocus())}
   onView(withText(R.string.template_insert_data)).perform(click())
   onView(withText("{{FROM}}")).inRoot(isDialog()).perform(click())
   ins.runOnMainSync{val result=page!!.binding!!.etTitleTemplate.text.toString();println("RESULT=$result");assertEquals("Existing title{{FROM}}",result)}
   assertEquals("Draft edit does not overwrite stored target",json,Core.sender.get(id).blockingGet().jsonSetting)
  } finally {ins.runOnMainSync{page?.activity?.finish();main.finish()};Core.sender.delete(id);assertFalse(Core.sender.getAll().blockingGet().any{it.id==id})}
 }
}
