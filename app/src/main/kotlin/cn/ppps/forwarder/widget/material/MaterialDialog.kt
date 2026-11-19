package cn.ppps.forwarder.widget.material

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Legacy call contract, backed exclusively by Material 3 alert dialogs. */
class MaterialDialog private constructor(context: Context, val alert: AlertDialog) : android.app.Dialog(context) {
    /**
     * Message slot of a customView dialog, placed above the custom view inside the same body. AppCompat's
     * AlertDialogLayout reserves room for the actions only while at most one of contentPanel/customPanel is
     * visible, so custom dialogs never use the alert's own message panel.
     */
    private var bodyMessage: TextView? = null
    val contentView: TextView? get() = bodyMessage ?: alert.findViewById(android.R.id.message)
    private var bindActions: (() -> Unit)? = null
    override fun show() { alert.show(); bindActions?.invoke() }
    override fun dismiss() = alert.dismiss()
    override fun isShowing(): Boolean = alert.isShowing
    fun setContent(text: CharSequence) {
        val inBody = bodyMessage
        if (inBody == null) alert.setMessage(text) else { inBody.text = text; inBody.visibility = View.VISIBLE }
    }
    fun interface SingleButtonCallback { fun onClick(dialog: MaterialDialog, which: DialogAction) }

    class Builder(private val context: Context) {
        private val builder = MaterialAlertDialogBuilder(context)
        private var message: CharSequence? = null
        private var heading: CharSequence? = null
        private var dismissOnClick = true
        private var positive: CharSequence? = null
        private var negative: CharSequence? = null
        private var neutral: CharSequence? = null
        private var positiveCallback: SingleButtonCallback? = null
        private var negativeCallback: SingleButtonCallback? = null
        private var neutralCallback: SingleButtonCallback? = null
        private var custom: View? = null
        private var customScrolls = false
        private var choices = emptyList<CharSequence>()
        private var selected = -1
        private var selection: ((MaterialDialog, View?, Int, CharSequence) -> Boolean)? = null
        fun title(id: Int) = title(context.getText(id))
        fun title(text: CharSequence) = apply { heading = text; builder.setTitle(text) }
        fun content(id: Int) = content(context.getText(id))
        fun content(text: CharSequence) = apply { message = text }
        fun iconRes(id: Int) = apply { builder.setIcon(id) }
        fun cancelable(value: Boolean) = apply { builder.setCancelable(value) }
        fun autoDismiss(value: Boolean) = apply { dismissOnClick = value }
        fun positiveText(id: Int) = apply { positive = context.getText(id) }
        fun negativeText(id: Int) = apply { negative = context.getText(id) }
        fun neutralText(id: Int) = apply { neutral = context.getText(id) }
        fun positiveText(text: CharSequence) = apply { positive = text }
        fun negativeText(text: CharSequence) = apply { negative = text }
        fun neutralText(text: CharSequence) = apply { neutral = text }
        // Semantic Material action colors intentionally replace old hardcoded colors.
        fun neutralColor(color: android.content.res.ColorStateList) = this
        fun negativeColor(color: android.content.res.ColorStateList) = this
        fun onPositive(callback: SingleButtonCallback) = apply { positiveCallback = callback }
        fun onNegative(callback: SingleButtonCallback) = apply { negativeCallback = callback }
        fun onNeutral(callback: SingleButtonCallback) = apply { neutralCallback = callback }
        fun customView(view: View, wrapInScrollView: Boolean) = apply { custom = view; customScrolls = wrapInScrollView }
        fun items(values: Collection<CharSequence>) = apply { choices = values.toList() }
        fun items(values: Array<out CharSequence>) = items(values.toList())
        fun itemsCallbackSingleChoice(index: Int, callback: (MaterialDialog, View?, Int, CharSequence) -> Boolean) = apply { selected = index; selection = callback }

        private fun scrolling(view: View): View =
            if (customScrolls) androidx.core.widget.NestedScrollView(context).apply { addView(view) } else view

        /** Styled like the alert's own message (M3 body text, dialog side padding); hidden until it has text. */
        private fun bodyMessage(text: CharSequence?): TextView {
            val themed = builder.context
            val value = TypedValue()
            val side = if (themed.theme.resolveAttribute(androidx.appcompat.R.attr.dialogPreferredPadding, value, true)) {
                TypedValue.complexToDimensionPixelSize(value.data, themed.resources.displayMetrics)
            } else 0
            return com.google.android.material.textview.MaterialTextView(themed, null, com.google.android.material.R.attr.materialAlertDialogBodyTextStyle).apply {
                setPadding(side, 0, side, themed.resources.getDimensionPixelSize(androidx.appcompat.R.dimen.abc_dialog_padding_top_material))
                if (text.isNullOrEmpty()) visibility = View.GONE else this.text = text
            }
        }

        fun build(): MaterialDialog {
            if (positive != null) builder.setPositiveButton(positive, null)
            if (negative != null) builder.setNegativeButton(negative, null)
            if (neutral != null) builder.setNeutralButton(neutral, null)
            val view = custom
            var inBody: TextView? = null
            if (choices.isNotEmpty()) {
                if (message != null) builder.setTitle(listOfNotNull(heading, message).joinToString("\n"))
                builder.setSingleChoiceItems(choices.toTypedArray(), selected) { _, index -> selected = index }
                if (view != null) builder.setView(scrolling(view))
            } else if (view != null) {
                // Single middle panel: an optional message sits above the custom view in the same (scrolling) body.
                inBody = bodyMessage(message)
                val params = view.layoutParams?.let { LinearLayout.LayoutParams(it) }
                    ?: LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                builder.setView(scrolling(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(inBody, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
                    addView(view, params)
                }))
            } else {
                // Plain dialogs keep a message slot so callers can setContent()/configure contentView before show().
                builder.setMessage(message ?: " ")
            }
            val result = MaterialDialog(context, builder.create())
            result.bodyMessage = inBody
            // Create now so callers can configure message links before show().
            result.alert.create()
            result.bindActions = {
                fun bind(button: Int, action: DialogAction, callback: SingleButtonCallback?) {
                    result.alert.getButton(button)?.setOnClickListener {
                        var accepted = true
                        if (action == DialogAction.POSITIVE && selected in choices.indices) {
                            accepted = selection?.invoke(result, null, selected, choices[selected]) ?: true
                        }
                        callback?.onClick(result, action)
                        if (dismissOnClick && accepted) result.dismiss()
                    }
                }
                bind(AlertDialog.BUTTON_POSITIVE, DialogAction.POSITIVE, positiveCallback)
                bind(AlertDialog.BUTTON_NEGATIVE, DialogAction.NEGATIVE, negativeCallback)
                bind(AlertDialog.BUTTON_NEUTRAL, DialogAction.NEUTRAL, neutralCallback)
            }
            return result
        }
        fun show() = build().also { it.show() }
    }
}
enum class DialogAction { POSITIVE, NEGATIVE, NEUTRAL }
