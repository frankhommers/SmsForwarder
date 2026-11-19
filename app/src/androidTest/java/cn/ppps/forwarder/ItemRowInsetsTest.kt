package cn.ppps.forwarder

import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.test.platform.app.InstrumentationRegistry
import cn.ppps.forwarder.utils.ItemRows
import com.google.android.material.textfield.TextInputEditText
import org.junit.Assert.*
import org.junit.Test

class ItemRowInsetsTest {
    @Test fun untouchedBlankRowHasNoBlurErrorButSubmitRejectsIt() {
        val ins=InstrumentationRegistry.getInstrumentation()
        ins.runOnMainSync {
            val context=ContextThemeWrapper(ins.targetContext,R.style.AppTheme)
            val root=LinearLayout(context)
            val backing=TextInputEditText(context).apply { id=R.id.et_app_list }
            root.addView(backing)
            val rows=ItemRows(backing,"\n",followVisibility=true)
            (rows.view.getChildAt(0) as android.widget.Button).performClick()
            fun walk(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{walk(v.getChildAt(it))}else emptyList()
            val input=walk(rows.view).filterIsInstance<EditText>().single()
            val box=generateSequence(input.parent){it.parent}.filterIsInstance<com.google.android.material.textfield.TextInputLayout>().first()
            input.onFocusChangeListener.onFocusChange(input,false)
            assertNull("Untouched row must not show error on navigation",box.error)
            assertEquals("",backing.text.toString())
            assertFalse("Submission still rejects blank row",rows.validate())
            assertNotNull(box.error)
        }
    }

    @Test fun generatedInputsHaveOutlinedContentInsets() {
        val ins=InstrumentationRegistry.getInstrumentation()
        ins.runOnMainSync {
            val context=ContextThemeWrapper(ins.targetContext,R.style.AppTheme)
            fun walk(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{walk(v.getChildAt(it))}else emptyList()
            for ((id, raw, delimiter) in listOf(Triple(R.id.et_phone_numbers,"0612345678",";"),Triple(R.id.et_regex_replace,"abc===replacement","\n"),Triple(R.id.et_app_list,"com.example.app","\n"))) {
                val root=LinearLayout(context).apply{orientation=LinearLayout.VERTICAL}
                val backing=TextInputEditText(context).apply{this.id=id;setText(raw)}
                root.addView(backing)
                val rows=ItemRows(backing,delimiter,followVisibility=true)
                val fields=walk(rows.view).filterIsInstance<EditText>()
                assertTrue("Rows exist for $id",fields.isNotEmpty())
                fields.forEach {
                    assertTrue("Start inset for $id",it.paddingStart>=16*context.resources.displayMetrics.density)
                    assertTrue("End inset for $id",it.paddingEnd>=16*context.resources.displayMetrics.density)
                }
                assertEquals("Stored value unchanged",raw,backing.text.toString())
            }
        }
    }
}
