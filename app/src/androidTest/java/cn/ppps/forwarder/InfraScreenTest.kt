package cn.ppps.forwarder

import android.content.Intent
import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.fragment.app.FragmentActivity
import cn.ppps.forwarder.activity.MainActivity
import cn.ppps.forwarder.fragment.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Offline navigation/captures only. Does not enable server or press transport buttons. */
class InfraScreenTest {
 @Test fun infrastructureAndOfflineHelpOpen() {
  val ins=InstrumentationRegistry.getInstrumentation()
  val language=InstrumentationRegistry.getArguments().getString("language","en")!!
  com.hjq.language.MultiLanguages.setAppLanguage(ins.targetContext,java.util.Locale(language))
  val main=ins.startActivitySync(Intent(ins.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
  val dir=java.io.File(ins.targetContext.getExternalFilesDir(null),"infra-review/$language").apply{mkdirs()}
  fun walk(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{walk(v.getChildAt(it))}else emptyList()
  try {
   for(page in listOf(ServerFragment::class.java,ClientFragment::class.java,HelpFragment::class.java,AboutFragment::class.java)) {
    ins.runOnMainSync{main.openNewPage(page)}
    var opened:FragmentActivity?=null
    repeat(50){
     if(opened==null){ins.waitForIdleSync();ins.runOnMainSync{
      opened=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<FragmentActivity>()
       .firstOrNull{it.supportFragmentManager.fragments.any{f->page.isInstance(f)&&f.isResumed&&f.isVisible}}
     };Thread.sleep(100)}
    }
    assertNotNull("Visible ${page.simpleName}",opened)
    try {
     if(page==HelpFragment::class.java){
      var web:WebView?=null
      ins.runOnMainSync{web=walk(opened!!.window.decorView).filterIsInstance<WebView>().single();assertTrue(web!!.settings.blockNetworkLoads)}
      var text=""
      for(i in 0 until 50){
       val latch=CountDownLatch(1)
       ins.runOnMainSync{web!!.evaluateJavascript("document.body.innerText"){text=it;latch.countDown()}}
       assertTrue(latch.await(3,TimeUnit.SECONDS))
       if(text.contains(if(language=="nl") "Aan de slag" else "Getting started"))break
       Thread.sleep(200)
      }
      assertTrue("Bundled help actually rendered: $text",text.contains(if(language=="nl") "Aan de slag" else "Getting started"))
     }
     ins.waitForIdleSync();Thread.sleep(400)
     java.io.FileOutputStream(java.io.File(dir,page.simpleName+".png")).use{ins.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
    } finally {ins.runOnMainSync{opened!!.finish()};ins.waitForIdleSync()}
   }
  } finally {ins.runOnMainSync{main.finish()}}
 }
}
