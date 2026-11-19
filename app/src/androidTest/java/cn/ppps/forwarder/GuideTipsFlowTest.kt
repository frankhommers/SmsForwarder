package cn.ppps.forwarder

import android.content.Intent
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso
import cn.ppps.forwarder.activity.MainActivity
import cn.ppps.forwarder.widget.GuideTipsDialog
import cn.ppps.forwarder.core.http.entity.TipInfo
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.google.android.material.checkbox.MaterialCheckBox
import org.junit.Assert.*
import org.junit.Test

class GuideTipsFlowTest {
 @Test fun pagesCheckboxAndBackWork() {
  val ins=InstrumentationRegistry.getInstrumentation()
  val lang=InstrumentationRegistry.getArguments().getString("language","en")!!
  com.hjq.language.MultiLanguages.setAppLanguage(ins.targetContext,java.util.Locale(lang))
  val main=ins.startActivitySync(Intent(ins.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
  val previous=GuideTipsDialog.isIgnoreTips
  var dialog:GuideTipsDialog?=null
  try {
   val json=ins.targetContext.assets.open(if(lang=="nl")"tips_nl.json" else "tips.json").bufferedReader().use{it.readText()}
   val tips:List<TipInfo> = Gson().fromJson(Gson().fromJson(json,JsonObject::class.java)["Data"],object:TypeToken<List<TipInfo>>(){}.type)
   assertTrue(tips.size>1)
   ins.runOnMainSync {
    dialog=GuideTipsDialog(main,tips);dialog!!.show()
    assertFalse(dialog!!.getButton(-2).isEnabled)
    assertTrue(dialog!!.getButton(-1).isEnabled)
    assertEquals(tips.first().title,dialog!!.findViewById<TextView>(R.id.tv_title)!!.text.toString())
    val checkbox=dialog!!.findViewById<MaterialCheckBox>(R.id.cb_ignore)!!
    checkbox.performClick();assertEquals(!previous,GuideTipsDialog.isIgnoreTips)
   }
   for(i in 1 until tips.size){
    Thread.sleep(350)
    ins.runOnMainSync{dialog!!.getButton(-1).performClick();assertEquals(tips[i].title,dialog!!.findViewById<TextView>(R.id.tv_title)!!.text.toString())}
   }
   ins.runOnMainSync{assertFalse(dialog!!.getButton(-1).isEnabled);assertTrue(dialog!!.getButton(-2).isEnabled)}
   ins.waitForIdleSync()
   val dir=java.io.File(ins.targetContext.getExternalFilesDir(null),"tips-review/$lang").apply{mkdirs()}
   java.io.FileOutputStream(java.io.File(dir,"last-page.png")).use{ins.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
   Thread.sleep(350)
   ins.runOnMainSync{dialog!!.getButton(-2).performClick();assertEquals(tips[tips.size-2].title,dialog!!.findViewById<TextView>(R.id.tv_title)!!.text.toString())}
   Espresso.pressBack();ins.waitForIdleSync()
   ins.runOnMainSync{assertFalse(dialog!!.isShowing)}
  } finally {ins.runOnMainSync{dialog?.dismiss();GuideTipsDialog.isIgnoreTips=previous;main.finish()}}
 }
}
