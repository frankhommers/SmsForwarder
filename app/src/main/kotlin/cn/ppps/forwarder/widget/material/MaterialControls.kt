package cn.ppps.forwarder.widget.material

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.checkbox.MaterialCheckBox

/** Real Material components; adapters preserve callers while removing XUI rendering. */
class TargetSwitch @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : MaterialSwitch(context, attrs) {
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!text.isNullOrEmpty() || !contentDescription.isNullOrEmpty()) return
        fun label(view: android.view.View): CharSequence? {
            if (view is android.widget.TextView && !view.text.isNullOrEmpty()) return view.text
            if (view is android.view.ViewGroup) for (i in 0 until view.childCount) label(view.getChildAt(i))?.let { return it }
            return null
        }
        (parent as? android.view.ViewGroup)?.let { group ->
            for (i in 0 until group.childCount) {
                val child = group.getChildAt(i)
                if (child !== this) label(child)?.let { contentDescription = it; return }
            }
        }
    }
    var dependencyStateChanged: (() -> Unit)? = null
    override fun setChecked(checked: Boolean) {
        super.setChecked(checked)
        dependencyStateChanged?.invoke()
    }
    fun setCheckedImmediately(checked: Boolean) { isChecked = checked; jumpDrawablesToCurrentState() }
}

class TargetCheckBox @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : MaterialCheckBox(context, attrs) {
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!text.isNullOrEmpty()) return
        val group = parent as? android.view.ViewGroup ?: return
        val label = group.getChildAt(group.indexOfChild(this) + 1) as? android.widget.TextView
        if (contentDescription.isNullOrEmpty()) contentDescription = label?.text
        if (label != null && !label.hasOnClickListeners()) label.setOnClickListener { if (isEnabled) performClick() }
    }
}
