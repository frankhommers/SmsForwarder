package cn.ppps.forwarder
import androidx.test.platform.app.InstrumentationRegistry
import android.view.ContextThemeWrapper
import android.widget.EditText
import cn.ppps.forwarder.utils.CommonUtils
import org.junit.Assert.*
import org.junit.Test

class TemplateUnfocusedInsertionTest {
 @Test fun unfocusedLoadedTextAppendsWithoutLoss() {
  val ins=InstrumentationRegistry.getInstrumentation()
  ins.runOnMainSync {
   val input=EditText(ContextThemeWrapper(ins.targetContext,R.style.AppTheme))
   input.setText("Existing title")
   input.setSelection(0)
   assertFalse(input.hasFocus())
   CommonUtils.insertOrReplaceText2Cursor(input,"{{FROM}}")
   assertEquals("Existing title{{FROM}}",input.text.toString())
  }
 }
}
