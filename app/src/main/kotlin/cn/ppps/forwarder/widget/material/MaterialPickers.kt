package cn.ppps.forwarder.widget.material

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import cn.ppps.forwarder.R

fun interface OnOptionsSelectListener {
    fun onOptionsSelect(view: View?, first: Int, second: Int, third: Int): Boolean
}

/** Two independently selectable time-of-day choices, rendered as accessible M3 dropdowns. */
class OptionsPickerBuilder(private val context: Context?, private val listener: OnOptionsSelectListener) {
    private var title: String = ""
    private var first = 0
    private var second = 0
    fun setTitleText(value: String) = apply { title = value }
    fun setSelectOptions(a: Int, b: Int) = apply { first = a; second = b }
    fun <T> build() = OptionsPicker(requireNotNull(context), title, first, second, listener)
}
class OptionsPicker(context: Context, title: String, first: Int, second: Int, listener: OnOptionsSelectListener) {
    private val start = TargetDropdown(context).apply { hint = context.getString(R.string.m3_period_start); selectedIndex = first }
    private val end = TargetDropdown(context).apply { hint = context.getString(R.string.m3_period_end); selectedIndex = second }
    private val body = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val spacing = (24 * resources.displayMetrics.density).toInt()
        setPadding(spacing, spacing, spacing, spacing)
        addView(start, LinearLayout.LayoutParams(-1, -2))
        addView(end, LinearLayout.LayoutParams(-1, -2))
    }
    private val initialFirst = first
    private val initialSecond = second
    private val dialog = MaterialAlertDialogBuilder(context).setTitle(title).setView(body)
        .setNegativeButton(R.string.cancel, null)
        .setPositiveButton(R.string.select) { _, _ -> listener.onOptionsSelect(body, start.selectedIndex, end.selectedIndex, 0) }
        .create()
    fun setNPicker(a: List<*>, b: List<*>) { start.setItems(a); end.setItems(b); start.selectedIndex = initialFirst; end.selectedIndex = initialSecond }
    fun show() = dialog.show()
}

object DialogLoader {
    fun getInstance() = this
    fun showConfirmDialog(context: Context, content: String, positive: String, yes: android.content.DialogInterface.OnClickListener, negative: String, no: android.content.DialogInterface.OnClickListener) =
        MaterialAlertDialogBuilder(context).setMessage(content).setPositiveButton(positive, yes).setNegativeButton(negative, no).show()
    fun showConfirmDialog(context: Context, title: String, content: String, positive: String, yes: android.content.DialogInterface.OnClickListener, negative: String, no: android.content.DialogInterface.OnClickListener) =
        MaterialAlertDialogBuilder(context).setTitle(title).setMessage(content).setPositiveButton(positive, yes).setNegativeButton(negative, no).show()
}
