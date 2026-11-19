package cn.ppps.forwarder.widget.material

import android.content.Context
import android.util.AttributeSet
import android.view.View
import com.google.android.material.slider.Slider
import com.google.android.material.slider.RangeSlider
import com.google.android.material.tabs.TabLayout

private const val AUTO = "http://schemas.android.com/apk/res-auto"

class TargetSlider @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : Slider(context, attrs) {
    init {
        valueFrom = attrs?.getAttributeIntValue(AUTO, "xsb_min", 0)?.toFloat() ?: 0f
        valueTo = attrs?.getAttributeIntValue(AUTO, "xsb_max", 100)?.toFloat() ?: 100f
        stepSize = 1f
        value = valueFrom
    }
    val selectedNumber: Int get() = value.toInt()
    fun setDefaultValue(number: Int) { value = number.toFloat().coerceIn(valueFrom, valueTo) }
    fun setOnSeekBarListener(callback: (TargetSlider, Int) -> Unit) {
        clearOnChangeListeners()
        addOnChangeListener { _, value, fromUser -> if (fromUser) callback(this, value.toInt()) }
    }
}

class TargetRangeSlider @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : RangeSlider(context, attrs) {
    init {
        valueFrom = attrs?.getAttributeIntValue(AUTO, "xrs_min", 0)?.toFloat() ?: 0f
        valueTo = attrs?.getAttributeIntValue(AUTO, "xrs_max", 100)?.toFloat() ?: 100f
        stepSize = 1f
        values = listOf(valueFrom, valueTo)
    }
    val selectedMin: Int get() = values.first().toInt()
    val selectedMax: Int get() = values.last().toInt()
    fun setStartingMinMax(min: Int, max: Int) { values = listOf(min.toFloat().coerceIn(valueFrom, valueTo), max.toFloat().coerceIn(valueFrom, valueTo)) }
    interface OnRangeSliderListener {
        fun onMinChanged(slider: TargetRangeSlider, min: Int)
        fun onMaxChanged(slider: TargetRangeSlider, max: Int)
    }
    fun setOnRangeSliderListener(callback: OnRangeSliderListener) {
        clearOnChangeListeners()
        addOnChangeListener { _, _, fromUser ->
            if (fromUser) {
                callback.onMinChanged(this, selectedMin)
                callback.onMaxChanged(this, selectedMax)
            }
        }
    }
}

class TargetTabs @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : TabLayout(context, attrs) {
    init { tabMode = MODE_SCROLLABLE }
    fun setTabTitles(titles: Array<String>) { removeAllTabs(); titles.forEach { addTab(newTab().setText(it)) } }
    fun setOnTabClickListener(callback: (View, Int) -> Unit) {
        addOnTabSelectedListener(object : OnTabSelectedListener {
            override fun onTabSelected(tab: Tab) { callback(this@TargetTabs, tab.position) }
            override fun onTabUnselected(tab: Tab) {}
            override fun onTabReselected(tab: Tab) {}
        })
    }
}
