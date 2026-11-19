package cn.ppps.forwarder.widget.material

import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import cn.ppps.forwarder.R
import com.google.android.material.textfield.TextInputLayout

/**
 * Display helpers for the Bark, Email, Telegram, Gotify and URL Scheme target editors (styles TargetForm.*).
 * Display only: no stored value, listener, selection index or validation is touched.
 */
object TargetForm {

    /**
     * The grid now holds just Insert data and Preview (CommonUtils.createTagButtons / TemplatePicker), so the old
     * Show/Hide template tags toggle would only add a step: it is hidden and the two actions are shown directly
     * below the field. Ids and the layout structure stay as they were.
     */
    fun installTagToggle(toggle: TextView, grid: View) {
        toggle.setOnClickListener(null)
        toggle.visibility = View.GONE
        grid.visibility = View.VISIBLE
    }

    /**
     * Lets a read-only dropdown show its whole selection (wrapping up to three lines instead of clipping) and gives
     * popup rows room to wrap. Call after the items are set. Items keep their order, so selection indices and the
     * TargetDropdown listener stay valid.
     */
    fun wrapDropdown(dropdown: TextInputLayout) {
        val input = dropdown.editText as? AutoCompleteTextView ?: return
        input.setSingleLine(false)
        input.setHorizontallyScrolling(false)
        input.maxLines = 3
        val source = input.adapter ?: return
        val items = (0 until source.count).map { source.getItem(it).toString() }
        input.setAdapter(object : ArrayAdapter<String>(dropdown.context, R.layout.target_form_dropdown_item, items) {
            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View =
                getView(position, convertView, parent)

            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                return (super.getView(position, convertView, parent) as TextView).apply {
                    inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    setSingleLine(false)
                    setHorizontallyScrolling(false)
                    transformationMethod = null
                    ellipsize = null
                    maxLines = 6
                    // Popup measurement may supply UNSPECIFIED width; constrain the text before
                    // measuring its height, otherwise a long single line is clipped by the popup.
                    val available = input.width.takeIf { it > 0 } ?: dropdown.width
                    if (available > 0) {
                        width = available
                        layoutParams = android.widget.AbsListView.LayoutParams(available, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
                    }
                }
            }
        })
    }
}
