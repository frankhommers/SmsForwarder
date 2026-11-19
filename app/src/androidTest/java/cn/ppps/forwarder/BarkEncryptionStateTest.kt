package cn.ppps.forwarder
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.fragment.app.FragmentActivity
import cn.ppps.forwarder.activity.MainActivity
import cn.ppps.forwarder.fragment.senders.BarkFragment
import org.junit.Assert.*
import org.junit.Test

class BarkEncryptionStateTest {
 @Test fun inactiveKeysRemainStoredInFields() {
  val ins=InstrumentationRegistry.getInstrumentation()
  val main=ins.startActivitySync(Intent(ins.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
  var page:BarkFragment?=null
  try {
   ins.runOnMainSync{main.openNewPage(BarkFragment::class.java)}
   for(i in 0 until 50){ins.waitForIdleSync();ins.runOnMainSync{page=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<FragmentActivity>().flatMap{it.supportFragmentManager.fragments}.filterIsInstance<BarkFragment>().firstOrNull{it.isResumed&&it.isVisible}};if(page!=null)break;Thread.sleep(100)}
   assertNotNull(page)
   ins.runOnMainSync {
    val b=page!!.binding!!
    fun select(index:Int) {
     val input=b.spEncryptionAlgorithm.editText as android.widget.AutoCompleteTextView
     input.onItemClickListener!!.onItemClick(null,null,index,index.toLong())
    }
    assertEquals(page!!.getString(R.string.bark_encryption_algorithm_none),b.spEncryptionAlgorithm.text.toString())
    assertFalse(b.etEncryptionKey.isEnabled);assertFalse(b.etEncryptionIv.isEnabled)
    select(1)
    assertTrue(b.etEncryptionKey.isEnabled);assertTrue(b.etEncryptionIv.isEnabled)
    b.etEncryptionKey.setText("fixture-key-only");b.etEncryptionIv.setText("fixture-iv-only")
    select(0)
    assertFalse(b.etEncryptionKey.isEnabled);assertFalse(b.etEncryptionIv.isEnabled)
    assertEquals("fixture-key-only",b.etEncryptionKey.text.toString());assertEquals("fixture-iv-only",b.etEncryptionIv.text.toString())
    select(1)
    assertTrue(b.etEncryptionKey.isEnabled);assertEquals("fixture-key-only",b.etEncryptionKey.text.toString())
   }
  } finally {ins.runOnMainSync{page?.activity?.finish();main.finish()}}
 }
}
