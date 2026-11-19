package cn.ppps.forwarder.widget.material

import android.content.Context
import android.util.AttributeSet
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import com.google.android.material.search.SearchView

class TargetSearch @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : SearchView(context, attrs) {
    interface OnQueryTextListener {
        fun onQueryTextSubmit(query: String): Boolean
        fun onQueryTextChange(newText: String): Boolean
    }
    interface SearchViewListener {
        fun onSearchViewShown()
        fun onSearchViewClosed()
    }
    val hasOpenSearch: Boolean get() = currentTransitionState == TransitionState.SHOWN || currentTransitionState == TransitionState.SHOWING
    fun showSearch() { show(); editText.requestFocus() }
    fun setEllipsize(value: Boolean) {}
    fun setSubmitOnClick(value: Boolean) {}
    fun setOnQueryTextListener(listener: OnQueryTextListener) {
        editText.imeOptions = EditorInfo.IME_ACTION_SEARCH
        editText.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEARCH) { listener.onQueryTextSubmit(editText.text.toString()); hide(); true } else false
        }
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { listener.onQueryTextChange(s.toString()) }
            override fun afterTextChanged(s: Editable?) {}
        })
    }
    fun setOnSearchViewListener(listener: SearchViewListener) {
        addTransitionListener { _, _, state ->
            if (state == TransitionState.SHOWN) listener.onSearchViewShown()
            if (state == TransitionState.HIDDEN) listener.onSearchViewClosed()
        }
    }
}
