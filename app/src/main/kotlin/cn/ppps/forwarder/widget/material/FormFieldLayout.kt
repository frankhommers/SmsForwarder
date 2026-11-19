package cn.ppps.forwarder.widget.material

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewTreeObserver
import com.google.android.material.textfield.TextInputLayout

/**
 * Outlined form field whose own visibility follows its EditText.
 * Use it for backing fields whose visibility is toggled by Kotlin or ItemRows (for example et_value in the rule editor),
 * so the outline, label and helper text never stay behind as an empty box. Display only; no stored value is touched.
 */
class FormFieldLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : TextInputLayout(context, attrs) {
    private val follow = ViewTreeObserver.OnGlobalLayoutListener { sync() }
    private var observer: ViewTreeObserver? = null

    private fun sync() {
        val input = editText ?: return
        val wanted = if (input.visibility == View.VISIBLE) View.VISIBLE else View.GONE
        if (visibility != wanted) visibility = wanted
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        observer?.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(follow)
        observer = viewTreeObserver.also { it.addOnGlobalLayoutListener(follow) }
        sync()
    }

    override fun onDetachedFromWindow() {
        observer?.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(follow)
        observer = null
        super.onDetachedFromWindow()
    }
}
