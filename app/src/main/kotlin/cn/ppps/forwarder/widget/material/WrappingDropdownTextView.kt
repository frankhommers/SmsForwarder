package cn.ppps.forwarder.widget.material

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

/** Dropdown lists can force TextView rows into horizontal scrolling after adapter binding.
 * These rows deliberately wrap so long choices remain readable within the popup width.
 */
class WrappingDropdownTextView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : AppCompatTextView(context, attrs) {
    override fun setHorizontallyScrolling(whether: Boolean) {
        super.setHorizontallyScrolling(false)
    }
}
