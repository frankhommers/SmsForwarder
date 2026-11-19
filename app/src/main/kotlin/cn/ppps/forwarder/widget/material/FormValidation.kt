package cn.ppps.forwarder.widget.material

import android.widget.EditText
import android.view.View
import android.view.ViewGroup
import com.google.android.material.textfield.TextInputLayout

/** Field-local validation, independent of transport and persisted settings. */
object FormValidation {
    fun show(input: EditText, message: CharSequence) {
        val container = generateSequence(input.parent) { it.parent }
            .filterIsInstance<TextInputLayout>().firstOrNull()
        val alreadyShowingError = !container?.error.isNullOrEmpty() || !input.error.isNullOrEmpty()
        if (container != null) {
            input.error = null
            container.error = message
        } else input.error = message
        if (!alreadyShowingError) input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                container?.error = null
                input.error = null
                input.removeTextChangedListener(this)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        input.requestFocus()
        input.post { input.requestRectangleOnScreen(android.graphics.Rect(0, 0, input.width, input.height), false) }
    }
}
