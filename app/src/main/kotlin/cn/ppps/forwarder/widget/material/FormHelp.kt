package cn.ppps.forwarder.widget.material

import android.view.View
import androidx.annotation.StringRes
import cn.ppps.forwarder.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Question-mark help for a form field (style Form.HelpIcon inside a Form.HelpRow). Opens a local Material 3
 * dialog: title = the field name, body = a plain-language explanation, one Close button. No network.
 */
object FormHelp {
    fun install(button: View, @StringRes title: Int, @StringRes body: Int) {
        val context = button.context
        button.contentDescription = context.getString(R.string.form_help_content_description, context.getString(title))
        button.setOnClickListener {
            MaterialAlertDialogBuilder(it.context)
                .setTitle(title)
                .setMessage(body)
                .setPositiveButton(R.string.m3_close, null)
                .show()
        }
    }
}
