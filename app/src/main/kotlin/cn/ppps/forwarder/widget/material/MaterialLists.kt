package cn.ppps.forwarder.widget.material

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import android.widget.ArrayAdapter
import com.google.android.material.textview.MaterialTextView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import cn.ppps.forwarder.R

class TargetInfoRow @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    private val label = MaterialTextView(context)
    init {
        orientation = VERTICAL
        val padding = (16 * resources.displayMetrics.density).toInt()
        setPadding(padding, padding, padding, padding)
        minimumHeight = (56 * resources.displayMetrics.density).toInt()
        val id = attrs?.getAttributeResourceValue("http://schemas.android.com/apk/res-auto", "sLeftTextString", 0) ?: 0
        if (id != 0) label.setText(id)
        addView(label, LayoutParams(-1, -2))
    }
    fun setLeftString(value: String) { label.text = value }
    fun interface OnSuperTextViewClickListener { fun onClick(v: TargetInfoRow) }
    fun setOnSuperTextViewClickListener(listener: OnSuperTextViewClickListener) { setOnClickListener { listener.onClick(this) } }
}

class TargetInfoList @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    init { orientation = VERTICAL }
    fun createItemView(text: String) = MaterialTextView(context).apply {
        this.text = text
        val padding = (16 * resources.displayMetrics.density).toInt()
        setPadding(padding, padding, padding, padding)
        minimumHeight = (56 * resources.displayMetrics.density).toInt()
    }
    class Section {
        private val items = mutableListOf<View>()
        fun addItemView(view: View, click: View.OnClickListener) = apply { items.add(view); view.setOnClickListener(click) }
        fun addTo(list: TargetInfoList) { items.forEach { list.addView(it, LayoutParams(-1, -2)) } }
    }
    companion object { fun newSection(context: Context?) = Section() }
}

class TargetChips @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ChipGroup(context, attrs) {
    var adapter = ArrayAdapter<String>(context, android.R.layout.simple_list_item_1)
        private set
    private var callback: ((TargetChips, Int, List<Int>) -> Unit)? = null
    fun setTagCheckedMode(mode: Int) { isSingleSelection = false }
    fun setItems(items: List<String>) {
        removeAllViews()
        adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, items)
        items.forEachIndexed { index, value ->
            addView(Chip(context).apply {
                id = View.generateViewId()
                text = value
                isCheckable = true
                setEnsureMinTouchTargetSize(true)
                setOnCheckedChangeListener { _, _ ->
                    callback?.invoke(this@TargetChips, index, (0 until childCount).filter { (getChildAt(it) as Chip).isChecked })
                }
            })
        }
    }
    fun setSelectedItems(item: String) = setSelectedItems(listOf(item))
    fun setSelectedItems(items: List<String>) {
        val saved = callback
        callback = null
        for (i in 0 until childCount) (getChildAt(i) as Chip).isChecked = adapter.getItem(i) in items
        callback = saved
    }
    fun setOnTagSelectListener(listener: (TargetChips, Int, List<Int>) -> Unit) { callback = listener }
    companion object { const val FLOW_TAG_CHECKED_MULTI = 2 }
}
