package cn.ppps.forwarder.widget.material

import android.content.Context
import android.util.AttributeSet
import android.widget.ArrayAdapter
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout

class TargetDropdown @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : TextInputLayout(context, attrs) {
    private val input = MaterialAutoCompleteTextView(context).apply { inputType = 0; minimumHeight = (48 * resources.displayMetrics.density).toInt() }
    val text: CharSequence get() = input.text
    private var items = emptyList<String>()
    private var selected: ((TargetDropdown, Int, Long, Any) -> Unit)? = null
    private var nothing: ((TargetDropdown) -> Unit)? = null
    var selectedIndex = 0
        set(value) { field = value; if (value in items.indices) input.setText(items[value], false) }
    init {
        boxBackgroundMode = BOX_BACKGROUND_OUTLINE
        endIconMode = END_ICON_DROPDOWN_MENU
        addView(input, LayoutParams(-1, -2))
        val entries = attrs?.getAttributeResourceValue("http://schemas.android.com/apk/res-auto", "ms_entries", 0) ?: 0
        if (entries != 0) setItems(resources.getStringArray(entries).toList())
        input.setOnItemClickListener { _, _, position, id ->
            selectedIndex = position
            selected?.invoke(this, position, id, items[position])
        }
    }
    fun setItems(values: List<*>) { items = values.map { it.toString() }; input.setAdapter(ArrayAdapter(context, android.R.layout.simple_list_item_1, items)); selectedIndex = selectedIndex.coerceAtMost((items.size - 1).coerceAtLeast(0)) }
    fun setSelectedItem(item: Any) { val i = items.indexOf(item.toString()); if (i >= 0) selectedIndex = i }
    fun setOnItemSelectedListener(listener: (TargetDropdown, Int, Long, Any) -> Unit) { selected = listener }
    fun setOnNothingSelectedListener(listener: (TargetDropdown) -> Unit) { nothing = listener }
}
