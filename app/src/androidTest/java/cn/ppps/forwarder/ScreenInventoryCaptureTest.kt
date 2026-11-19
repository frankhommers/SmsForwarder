package cn.ppps.forwarder
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import androidx.test.platform.app.InstrumentationRegistry
import cn.ppps.forwarder.activity.MainActivity
import org.junit.Assert.*
import org.junit.Test

/** Capture only. Never saves or invokes delivery. */
class ScreenInventoryCaptureTest {
 @Test fun captureEditorScrollStates() {
 val ins=InstrumentationRegistry.getInstrumentation()
 val lang=InstrumentationRegistry.getArguments().getString("language","en")!!
 com.hjq.language.MultiLanguages.setAppLanguage(ins.targetContext,java.util.Locale(lang))
 val main=ins.startActivitySync(Intent(ins.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
 val dir=java.io.File(ins.targetContext.getExternalFilesDir(null),"screen-inventory/$lang").apply{mkdirs()}
 fun walk(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{walk(v.getChildAt(it))}else emptyList()
 val ledger=org.json.JSONArray()
        val classes = listOf(
            cn.ppps.forwarder.fragment.SettingsFragment::class.java,
            cn.ppps.forwarder.fragment.RulesEditFragment::class.java,
            cn.ppps.forwarder.fragment.TasksEditFragment::class.java,
            cn.ppps.forwarder.fragment.AboutFragment::class.java,
            cn.ppps.forwarder.fragment.ServerFragment::class.java,
            cn.ppps.forwarder.fragment.ClientFragment::class.java,
            cn.ppps.forwarder.fragment.senders.WebhookFragment::class.java,
            cn.ppps.forwarder.fragment.senders.EmailFragment::class.java,
            cn.ppps.forwarder.fragment.senders.TelegramFragment::class.java,
            cn.ppps.forwarder.fragment.senders.GotifyFragment::class.java,
            cn.ppps.forwarder.fragment.senders.SmsFragment::class.java,
            cn.ppps.forwarder.fragment.senders.BarkFragment::class.java,
            cn.ppps.forwarder.fragment.senders.SocketFragment::class.java,
            cn.ppps.forwarder.fragment.senders.UrlSchemeFragment::class.java,
            cn.ppps.forwarder.fragment.action.WolFragment::class.java,
            cn.ppps.forwarder.fragment.action.CleanerFragment::class.java,
            cn.ppps.forwarder.fragment.action.AlarmFragment::class.java,
            cn.ppps.forwarder.fragment.action.RuleFragment::class.java,
            cn.ppps.forwarder.fragment.action.SettingsFragment::class.java,
            cn.ppps.forwarder.fragment.action.ResendFragment::class.java,
            cn.ppps.forwarder.fragment.action.TaskActionFragment::class.java,
            cn.ppps.forwarder.fragment.action.HttpServerFragment::class.java,
            cn.ppps.forwarder.fragment.action.SendSmsFragment::class.java,
            cn.ppps.forwarder.fragment.action.SenderFragment::class.java,
            cn.ppps.forwarder.fragment.action.NotificationFragment::class.java,
            cn.ppps.forwarder.fragment.condition.LockScreenFragment::class.java,
            cn.ppps.forwarder.fragment.condition.LeaveAddressFragment::class.java,
            cn.ppps.forwarder.fragment.condition.MsgFragment::class.java,
            cn.ppps.forwarder.fragment.condition.BatteryFragment::class.java,
            cn.ppps.forwarder.fragment.condition.BluetoothFragment::class.java,
            cn.ppps.forwarder.fragment.condition.NetworkFragment::class.java,
            cn.ppps.forwarder.fragment.condition.SimFragment::class.java,
            cn.ppps.forwarder.fragment.condition.CronFragment::class.java,
            cn.ppps.forwarder.fragment.condition.ChargeFragment::class.java,
            cn.ppps.forwarder.fragment.condition.ToAddressFragment::class.java
        )

 try { for(page in classes) {
 ins.runOnMainSync {
  val option=com.xuexiang.xpage.core.PageOption.to(page).setNewActivity(true)
  if(page==cn.ppps.forwarder.fragment.RulesEditFragment::class.java)option.putString(cn.ppps.forwarder.utils.KEY_RULE_TYPE,"sms")
  if(page==cn.ppps.forwarder.fragment.condition.MsgFragment::class.java)option.putString(cn.ppps.forwarder.utils.KEY_EVENT_PARAMS_CONDITION,"sms")
  option.open(main)
 }
 var activity:androidx.fragment.app.FragmentActivity?=null
 for(i in 0 until 50){
  ins.waitForIdleSync()
  ins.runOnMainSync{activity=androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<androidx.fragment.app.FragmentActivity>().firstOrNull{a->a.supportFragmentManager.fragments.any{page.isInstance(it)&&it.isResumed&&it.isVisible}}}
  if(activity!=null)break
  Thread.sleep(100)
 }
 assertNotNull(page.name,activity)
 // Resumed does not mean the XPage slide-in animation has finished.
 // Let short/long toasts from the preceding page expire before capture.
 Thread.sleep(4500)
 ins.waitForIdleSync()
 try {
  var root:View?=null
  ins.runOnMainSync{root=activity!!.supportFragmentManager.fragments.first{page.isInstance(it)&&it.isResumed&&it.isVisible}.requireView();activity!!.currentFocus?.let{(activity!!.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(it.windowToken,0)}}
  var scroll:View?=null
  ins.runOnMainSync{scroll=walk(root!!).firstOrNull{it.isShown&&(it is androidx.core.widget.NestedScrollView||it is android.widget.ScrollView)}}
  for(index in 0 until 20){
   ins.waitForIdleSync();Thread.sleep(250)
   val id=page.name.removePrefix("cn.ppps.forwarder.fragment.").replace('.','-')+"-"+index
   java.io.FileOutputStream(java.io.File(dir,id+".png")).use{ins.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
   val controls=org.json.JSONArray()
   var canScroll=false
   ins.runOnMainSync {
    for(v in walk(root!!)){val bounds=android.graphics.Rect();if(v.isShown&&v.getGlobalVisibleRect(bounds))controls.put(org.json.JSONObject().put("class",v.javaClass.name).put("id",v.id).put("text",(v as? android.widget.TextView)?.text?.toString()?:"").put("bounds",bounds.toShortString()).put("enabled",v.isEnabled))}
    canScroll=scroll?.canScrollVertically(1)==true
   }
   ledger.put(org.json.JSONObject().put("id",id).put("page",page.name).put("language",lang).put("at_bottom",!canScroll).put("controls",controls).put("review_status","not-reviewed"))
   java.io.File(dir,"ledger.json").writeText(ledger.toString(2))
   if(!canScroll)break
   ins.runOnMainSync{scroll!!.scrollBy(0,(scroll!!.height*.75).toInt())}
   assertTrue("Scroll did not terminate: "+page.name,index<19)
  }
 } finally {ins.runOnMainSync{activity!!.finish()};ins.waitForIdleSync()}
 }} finally {ins.runOnMainSync{main.finish()}}
 }
}
