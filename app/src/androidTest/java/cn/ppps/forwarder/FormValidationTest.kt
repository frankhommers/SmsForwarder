package cn.ppps.forwarder

import android.view.ContextThemeWrapper
import androidx.test.platform.app.InstrumentationRegistry
import cn.ppps.forwarder.widget.material.FormValidation
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textfield.TextInputEditText
import org.junit.Assert.*
import org.junit.Test

class FormValidationTest {
 @Test fun materialErrorClearsWhenCorrectedAndCanBeShownAgain() {
  val ins=InstrumentationRegistry.getInstrumentation()
  ins.runOnMainSync {
   val c=ContextThemeWrapper(ins.targetContext,R.style.AppTheme)
   val box=TextInputLayout(c)
   val field=TextInputEditText(c)
   box.addView(field)
   repeat(5) { FormValidation.show(field,"Required") }
   assertEquals("Required",box.error.toString())
   assertNull("No legacy popup",field.error)
   field.setText("Fixed")
   assertNull(box.error)
   FormValidation.show(field,"Another error")
   assertEquals("Another error",box.error.toString())
   field.setText("Corrected again")
   assertNull(box.error)
  }
 }
}
