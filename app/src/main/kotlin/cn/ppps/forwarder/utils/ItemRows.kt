package cn.ppps.forwarder.utils

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import android.view.ViewTreeObserver
import android.widget.EditText
import android.widget.LinearLayout
import cn.ppps.forwarder.R

/** Individual item controls backed by the original field, so existing listeners and storage stay intact. */
class ItemRows(private val source: EditText, private val delimiter: String, private val pairs: Boolean = false, private val followVisibility: Boolean = false) {
    val view = object : LinearLayout(source.context) {
        init { orientation = VERTICAL; id = source.id xor 0x01000000; isSaveEnabled = true }
        override fun onSaveInstanceState(): android.os.Parcelable = android.os.Bundle().apply {
            putParcelable("super", super.onSaveInstanceState())
            putString("original", original)
            putString("backing", source.text.toString())
            putStringArrayList("items", ArrayList(fields.map { (key, value) -> item(key, value) }))
        }
        override fun onRestoreInstanceState(state: android.os.Parcelable?) {
            if (state !is android.os.Bundle) { super.onRestoreInstanceState(state); return }
            super.onRestoreInstanceState(state.getParcelable("super"))
            syncing = true
            source.setText(state.getString("backing").orEmpty())
            syncing = false
            original = state.getString("original").orEmpty()
            originalItems = ItemListCodec.split(original, delimiter)
            fields.clear(); rows.removeAllViews()
            state.getStringArrayList("items")?.forEach { add(it) }
            sync()
        }
    }
    private val rows = LinearLayout(source.context).apply { orientation = LinearLayout.VERTICAL }
    private val phoneRows = source.id in listOf(R.id.et_safe_phone, R.id.et_phone_numbers, R.id.et_mobiles)
    // Regular-expression replacements: two fields per row, stored as pattern===replacement (unchanged format).
    private val regexRows = source.id == R.id.et_regex_replace
    private val twoFields = pairs || regexRows
    private fun item(key: EditText, value: EditText?): String =
        if (regexRows) ItemListCodec.regexLine(key.text.toString(), value?.text?.toString().orEmpty())
        else key.text.toString() + if (value != null) "=" + value.text.toString() else ""
    private fun split(raw: String): Pair<String, String> = if (regexRows) ItemListCodec.regexPair(raw) else ItemListCodec.parameter(raw)
    private var syncing = false
    private val originalLayoutParams = source.layoutParams
    private var active = true
    private var original = ""
    private var originalItems = emptyList<String>()
    private val fields = mutableListOf<Pair<EditText, EditText?>>()

    init {
        source.setTag(R.id.item_rows_controller, this)
        val container = generateSequence(source.parent as? View) { it.parent as? View }.filterIsInstance<TextInputLayout>().firstOrNull()
        val anchor = container ?: source
        val parent = anchor.parent as ViewGroup
        if (phoneRows && parent is LinearLayout && parent.orientation == LinearLayout.HORIZONTAL) {
            parent.orientation = LinearLayout.VERTICAL
            for (i in 0 until parent.childCount) {
                parent.getChildAt(i).layoutParams = LinearLayout.LayoutParams(-1, -2)
            }
        }
        parent.addView(view, parent.indexOfChild(anchor) + 1, if (parent is LinearLayout && parent.orientation == LinearLayout.HORIZONTAL) LinearLayout.LayoutParams(0, -2, 1f) else ViewGroup.LayoutParams(-1, -2))
        if (followVisibility) {
            source.layoutParams = LinearLayout.LayoutParams(0, 0)
            source.isFocusable = false
            source.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            view.visibility = source.visibility
            observeLayout(source) { view.visibility = source.visibility }
        } else source.visibility = View.GONE
        view.addView(MaterialButton(source.context).apply {
            backgroundTintList = android.content.res.ColorStateList.valueOf(com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface))
            setTextColor(com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary))
            strokeColor = android.content.res.ColorStateList.valueOf(com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline))
            strokeWidth = resources.displayMetrics.density.toInt().coerceAtLeast(1)
            setText(if (source.id == R.id.et_safe_phone) R.string.review_add_allowed_sender else if (phoneRows) R.string.copy_add_recipient else if (regexRows) R.string.regex_add_replacement else if (pairs) R.string.add_parameter else R.string.add_item)
            setOnClickListener {
                add("")
                val input = fields.last().first
                input.requestFocus()
                input.post { (source.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT) }
            }
        })
        view.addView(rows)
        load(source.text.toString())
        source.addTextChangedListener(watcher {
            if (!syncing) load(source.text.toString())
        })
    }

    fun setActive(enabled: Boolean) {
        if (enabled == active) return
        active = enabled
        view.visibility = if (enabled) View.VISIBLE else View.GONE
        source.visibility = if (enabled) View.GONE else View.VISIBLE
        source.layoutParams = originalLayoutParams
    }

    private fun outlined(input: EditText) = TextInputLayout(source.context).apply {
        boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        input.background = null
        // Removing the legacy background also removes its content insets.
        // Keep text and the floating label away from the outlined border on every row.
        val inset = (16 * resources.displayMetrics.density).toInt()
        input.setPaddingRelative(inset, inset, inset, inset)
        input.minimumHeight = (56 * resources.displayMetrics.density).toInt()
        hint = input.hint
        input.hint = null
        addView(input, LinearLayout.LayoutParams(-1, -2))
    }

    // Listeners are view-owned, removed on detach and reattached exactly once.
    private fun observeLayout(target: View, changed: () -> Unit) {
        val listener = ViewTreeObserver.OnGlobalLayoutListener { changed() }
        var observer: ViewTreeObserver? = null
        fun attach() {
            if (observer?.isAlive == true) observer?.removeOnGlobalLayoutListener(listener)
            observer = target.viewTreeObserver
            observer?.addOnGlobalLayoutListener(listener)
        }
        target.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = attach()
            override fun onViewDetachedFromWindow(v: View) {
                if (observer?.isAlive == true) observer?.removeOnGlobalLayoutListener(listener)
                observer = null
            }
        })
        if (target.windowToken != null) attach()
    }

    private fun load(raw: String) {
        original = raw
        originalItems = ItemListCodec.split(if (source.id == R.id.et_safe_phone) raw.replace(";", ",").replace('；', ',').replace('，', ',') else raw, delimiter)
        fields.clear(); rows.removeAllViews()
        originalItems.forEach { add(it) }
    }

    private fun add(raw: String) {
        val row = LinearLayout(source.context).apply { orientation = LinearLayout.VERTICAL }
        val controls = LinearLayout(source.context).apply { orientation = LinearLayout.HORIZONTAL }
        val key = TextInputEditText(source.context).apply {
            inputType = if (phoneRows && !raw.startsWith("{{")) android.text.InputType.TYPE_CLASS_PHONE else source.inputType and android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE.inv()
            isSingleLine = true
            hint = context.getString(if (phoneRows) R.string.phone_number else if (regexRows) R.string.regex_find_label else if (pairs) R.string.item_key else R.string.item_value)
            setText(if (twoFields) split(raw).first else raw)
        }
        val value = if (twoFields) TextInputEditText(source.context).apply {
            isSingleLine = true; setHint(if (regexRows) R.string.regex_replace_label else R.string.item_value); setText(split(raw).second)
        } else null
        controls.addView(outlined(key), LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(androidx.appcompat.widget.AppCompatImageButton(source.context).apply {
            setImageResource(R.drawable.ic_delete)
            contentDescription = context.getString(R.string.remove_item)
            val color = com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant)
            imageTintList = android.content.res.ColorStateList.valueOf(color)
            val background = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, background, true)
            setBackgroundResource(background.resourceId)
            setOnClickListener { fields.removeAll { it.first === key }; rows.removeView(row); sync() }
        }, LinearLayout.LayoutParams((48 * source.resources.displayMetrics.density).toInt(), (48 * source.resources.displayMetrics.density).toInt()))
        row.addView(controls)
        if (value != null) row.addView(outlined(value))
        if (phoneRows) row.addView(com.google.android.material.checkbox.MaterialCheckBox(source.context).apply {
            setText(if (source.id == R.id.et_safe_phone) R.string.review_text_keyboard else R.string.copy_phone_template)
            isChecked = raw.startsWith("{{")
            setOnCheckedChangeListener { _, checked ->
                val text = key.text.toString()
                key.inputType = if (checked) android.text.InputType.TYPE_CLASS_TEXT else android.text.InputType.TYPE_CLASS_PHONE
                key.setText(text); key.setSelection(text.length)
            }
        })
        fields.add(key to value)
        rows.addView(row)
        // Leaving a newly added, untouched row is not a submission. Save/Test still
        // validate every row, but ordinary navigation must not manufacture an error.
        var edited = raw.isNotEmpty()
        key.setOnFocusChangeListener { _, focused -> if (!focused && edited) validate() }
        value?.setOnFocusChangeListener { _, focused -> if (!focused && edited) validate() }
        key.addTextChangedListener(watcher { edited = true; sync() })
        value?.addTextChangedListener(watcher { edited = true; sync() })
    }

    fun appendItem(text: String) {
        val empty = fields.firstOrNull { it.first.text.isNullOrBlank() }?.first
        if (empty != null) empty.setText(text) else add(text)
        sync()
    }

    fun hasItems(): Boolean = fields.isNotEmpty()

    fun validate(showErrors: Boolean = true): Boolean {
        var valid = true
        fields.forEach { (key, value) ->
            val text = key.text.toString()
            if (regexRows) {
                val problem = ItemListCodec.regexProblem(text, value?.text?.toString().orEmpty())
                val keyField = key.parent.parent as? TextInputLayout
                val valueField = value?.parent?.parent as? TextInputLayout
                val keyError = when (problem) {
                    ItemListCodec.RegexProblem.PATTERN_REQUIRED -> R.string.regex_pattern_required
                    ItemListCodec.RegexProblem.PATTERN_SEPARATOR -> R.string.regex_separator_forbidden
                    ItemListCodec.RegexProblem.PATTERN_TRAILING_EQUALS -> R.string.regex_trailing_equals
                    ItemListCodec.RegexProblem.PATTERN_LINE_BREAK -> R.string.item_invalid
                    ItemListCodec.RegexProblem.PATTERN_INVALID -> R.string.regex_invalid_pattern
                    else -> 0
                }
                val valueError = when (problem) {
                    ItemListCodec.RegexProblem.REPLACEMENT_SEPARATOR -> R.string.regex_separator_forbidden
                    ItemListCodec.RegexProblem.REPLACEMENT_LINE_BREAK -> R.string.item_invalid
                    else -> 0
                }
                if (showErrors || keyError == 0) keyField?.error = if (keyError != 0) source.context.getString(keyError) else null
                if (showErrors || valueError == 0) valueField?.error = if (valueError != 0) source.context.getString(valueError) else null
                if (problem != null) valid = false
                return@forEach
            }
            val invalidFormat = when(source.id) {
                R.id.et_app_list -> !text.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))
                R.id.et_safe_phone, R.id.et_phone_numbers, R.id.et_mobiles -> !(text.matches(Regex("\\+?[0-9]{3,20}")) || (text.startsWith("{{") && text.endsWith("}}")))
                R.id.et_value -> !Regex(source.context.getString(R.string.regex_multi_match)).matches(text)
                R.id.et_regex_replace -> !text.contains("===") || try { text.substringBefore("===").toRegex(); false } catch (_: Exception) { true }
                else -> false
            }
            val bad = invalidFormat || text.isBlank() || text.contains(delimiter) || (pairs && text.contains('=')) ||
                (value != null && value.text.toString().contains(delimiter))
            val field = key.parent.parent as? TextInputLayout
            if (showErrors || !bad) field?.error = if (bad) source.context.getString(
                if (phoneRows && text.isBlank()) R.string.copy_phone_required else if (phoneRows) R.string.copy_phone_invalid else R.string.item_invalid
            ) else null
            if (bad) valid = false
        }
        return valid
    }

    private fun sync() {
        val items = fields.map { (key, value) -> item(key, value) }
        // Opening an old list must never rewrite whitespace, empty entries, encoding or trailing separators.
        val raw = if (items == originalItems) original else ItemListCodec.join(items, delimiter)
        if (validate(showErrors = false)) { syncing = true; source.setText(raw); syncing = false }
    }

    companion object {
        private fun controllers(root: View): List<ItemRows> {
            val own = listOfNotNull(root.getTag(R.id.item_rows_controller) as? ItemRows)
            return own + if (root is ViewGroup) (0 until root.childCount).flatMap { controllers(root.getChildAt(it)) } else emptyList()
        }
        fun validateAll(root: View): Boolean {
            var valid = true
            controllers(root).filter { it.view.isShown && it.view.isEnabled }.forEach {
                if (!it.validate()) valid = false
            }

            return valid
        }
        private fun watcher(changed: () -> Unit) = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = changed()
        }
        fun installKnownLists(root: View) {
            val conditionField = root.findViewById<EditText>(R.id.et_value)
            val conditionGroup = root.findViewById<android.widget.RadioGroup>(R.id.rg_filed)
            if (conditionField != null && conditionGroup != null && conditionField.getTag(R.id.item_rows_controller) == null) {
                val editor = ItemRows(conditionField, "\n")
                editor.setActive(conditionGroup.checkedRadioButtonId == R.id.rb_multi_match)
                editor.observeLayout(root) {
                    editor.setActive(conditionGroup.checkedRadioButtonId == R.id.rb_multi_match)
                }
            }
            listOf(R.id.et_phone_numbers to ";", R.id.et_mobiles to ";", R.id.et_app_list to "\n", R.id.et_app_notify_blacklist to "\n", R.id.et_regex_replace to "\n", R.id.et_safe_phone to ",").forEach { (id, delimiter) ->
                root.findViewById<EditText>(id)?.takeIf { it.getTag(R.id.item_rows_controller) == null }?.let { ItemRows(it, delimiter, followVisibility = true) }
            }
        }
    }
}
