package cn.ppps.forwarder.widget.material

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.Filter
import android.widget.Filterable
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import com.xuexiang.xui.widget.spinner.editspinner.BaseEditSpinnerAdapter

/** Existing provider IDs/filter indices remain in the data adapter, never in the renderer. */
class TargetEditDropdown @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : TextInputLayout(context, attrs) {
    private val input = MaterialAutoCompleteTextView(context).apply { threshold = 0; isSingleLine = true }
    init {
        boxBackgroundMode = BOX_BACKGROUND_OUTLINE
        endIconMode = END_ICON_DROPDOWN_MENU
        val label = attrs?.getAttributeResourceValue("http://schemas.android.com/apk/res-auto", "es_hint", 0) ?: 0
        if (label != 0) hint = context.getString(label)
        addView(input, LayoutParams(-1, -2))
    }
    fun setOnItemClickListener(listener: AdapterView.OnItemClickListener) { input.onItemClickListener = listener }
    fun setAdapter(source: BaseEditSpinnerAdapter<*>) {
        input.setAdapter(object : BaseAdapter(), Filterable {
            override fun getCount() = source.count
            override fun getItem(position: Int): Any = source.getItem(position)
            override fun getItemId(position: Int) = source.getItemId(position)
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View = source.getView(position, convertView, parent)
            override fun getFilter() = object : Filter() {
                override fun performFiltering(constraint: CharSequence?) = FilterResults().apply { values = constraint?.toString().orEmpty() }
                override fun publishResults(constraint: CharSequence?, results: FilterResults) {
                    source.editSpinnerFilter.onFilter(results.values.toString())
                    notifyDataSetChanged()
                }
                override fun convertResultToString(resultValue: Any?) = resultValue?.toString().orEmpty()
            }
        })
    }
}
