package cn.ppps.forwarder
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.fragment.app.FragmentActivity
import cn.ppps.forwarder.activity.MainActivity
import cn.ppps.forwarder.fragment.client.CloneFragment
import org.junit.Assert.*
import org.junit.Test

/** Permission explanation only; never grants access, imports, exports, pushes or pulls. */
class BackupPermissionFlowTest {
 @Test fun explanationCancelAndReopenAreSafe() {
  val ins=InstrumentationRegistry.getInstrumentation()
  val lang=InstrumentationRegistry.getArguments().getString("language","en")!!
  com.hjq.language.MultiLanguages.setAppLanguage(ins.targetContext,java.util.Locale(lang))
  assertFalse("Fixture requires no all-files access",android.os.Environment.isExternalStorageManager())
  val main=ins.startActivitySync(Intent(ins.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
  var page:CloneFragment?=null
  val dir=java.io.File(ins.targetContext.getExternalFilesDir(null),"backup-review/$lang").apply{mkdirs()}
  fun shot(name:String){ins.waitForIdleSync();Thread.sleep(500);ins.waitForIdleSync();java.io.FileOutputStream(java.io.File(dir,name+".png")).use{ins.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}
  try {
   ins.runOnMainSync{com.xuexiang.xpage.core.PageOption.to(CloneFragment::class.java).putInt(cn.ppps.forwarder.utils.KEY_DEFAULT_SELECTION,1).setNewActivity(true).open(main)}
   for(i in 0 until 50){ins.waitForIdleSync();ins.runOnMainSync{page=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<FragmentActivity>().flatMap{it.supportFragmentManager.fragments}.filterIsInstance<CloneFragment>().firstOrNull{it.isResumed&&it.isVisible}};if(page!=null)break;Thread.sleep(100)}
   assertNotNull(page)
   if (InstrumentationRegistry.getArguments().getString("landscape") == "true") {
    ins.runOnMainSync { page!!.requireActivity().requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
    Thread.sleep(2000)
    assertEquals(android.content.res.Configuration.ORIENTATION_LANDSCAPE,page!!.resources.configuration.orientation)
   }
   Thread.sleep(1200)
   onView(withText(R.string.infra_form_storage_title)).check(matches(isDisplayed()))
   onView(withText(R.string.infra_form_storage_clone_message)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
   shot("explanation")
   onView(withText(R.string.infra_form_storage_clone_message)).perform(object: androidx.test.espresso.ViewAction {
    override fun getConstraints()=isAssignableFrom(android.widget.TextView::class.java)
    override fun getDescription()="Scroll permission explanation to its last line"
    override fun perform(controller:androidx.test.espresso.UiController,view:android.view.View){
     val scroll=generateSequence(view.parent){it.parent}.filterIsInstance<androidx.core.widget.NestedScrollView>().first()
     scroll.scrollTo(0,scroll.getChildAt(0).height)
     controller.loopMainThreadUntilIdle()
     val visible=android.graphics.Rect()
     assertTrue(view.getLocalVisibleRect(visible))
     assertTrue("Last explanation line must be visible: $visible / height=${view.height}",visible.bottom>=view.height-view.paddingBottom-2)
    }
   })
   shot("explanation-bottom")
   onView(withText(R.string.cancel)).check { v, error ->
    if(error!=null)throw error
    val details=StringBuilder()
    for(node in generateSequence(v){it.parent as? android.view.View}) {
     val rect=android.graphics.Rect();node.getGlobalVisibleRect(rect)
     val xy=IntArray(2);node.getLocationOnScreen(xy)
     details.append("${node.javaClass.name} size=${node.width}x${node.height} xy=${xy.toList()} visible=$rect scroll=${node.scrollY} lp=${node.layoutParams?.height}\n")
    }
    java.io.File(dir,"cancel-geometry.txt").writeText(details.toString())
   }
   onView(withText(R.string.cancel)).perform(click());ins.waitForIdleSync()
   onView(withId(R.id.btn_storage_permission)).check(matches(isDisplayed()))
   shot("cancelled")
   onView(withId(R.id.btn_storage_permission)).perform(click())
   onView(withText(R.string.infra_form_storage_title)).check(matches(isDisplayed()))
   onView(withText(R.string.cancel)).perform(click())
   assertFalse(android.os.Environment.isExternalStorageManager())
  } finally {ins.runOnMainSync{page?.activity?.finish();main.finish()}}
 }
}
