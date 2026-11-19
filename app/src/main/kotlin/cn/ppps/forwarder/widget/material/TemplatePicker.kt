package cn.ppps.forwarder.widget.material

import android.content.Context
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.annotation.StringRes
import androidx.core.widget.TextViewCompat
import cn.ppps.forwarder.R
import cn.ppps.forwarder.utils.CommonUtils
import cn.ppps.forwarder.utils.template.SampleContext
import cn.ppps.forwarder.utils.template.SampleText
import cn.ppps.forwarder.utils.template.TemplateFallback
import cn.ppps.forwarder.utils.template.TemplateRender
import cn.ppps.forwarder.utils.template.TemplateRenderer
import cn.ppps.forwarder.utils.template.TemplateSamples
import cn.ppps.forwarder.utils.template.TemplateTokens
import cn.ppps.forwarder.utils.template.WebhookPreview
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textview.MaterialTextView
import com.google.android.material.R as M

/**
 * Insert data / Preview actions for template fields (replaces the old grid of one button per tag).
 *
 * The host GridLayout (same id as before, still filled by CommonUtils.createTagButtons) now holds two Material
 * buttons. Insert data opens a Material dialog listing each item's name, its exact token and a synthetic
 * example; picking one inserts the unchanged token at the cursor. Preview fills the field's current text with
 * sample values. Both are local: no device data, contacts, location, apps or network are read and nothing is
 * sent. The field text is only changed by an explicit insert.
 */
object TemplatePicker {
    data class Item(val token: String, val label: String)

    private class Section(val title: String?, val items: List<Item>, val example: (String) -> String)

    /** Marks a grid whose messages come from automated tasks (MsgInfo type "task"), for a matching sample. */
    fun useTaskSample(grid: View) = grid.setTag(R.id.template_sample_context, SampleContext.TASK)

    /** One synthetic sample set, localized through resources. */
    fun sampleText(context: Context): SampleText {
        val address = context.getString(R.string.template_sample_address)
        val discharging = context.getString(R.string.battery_discharging)
        val ac = context.getString(R.string.battery_ac)
        return SampleText(
            smsText = context.getString(R.string.template_sample_sms),
            simLabel = context.getString(R.string.template_sample_sim),
            ruleTitle = context.getString(R.string.template_sample_rule),
            deviceName = context.getString(R.string.template_sample_device),
            appTitle = context.getString(R.string.template_sample_app_title),
            appText = context.getString(R.string.template_sample_app_text),
            callDetails = context.getString(R.string.contact) + TemplateSamples.CONTACT + "\n" + context.getString(R.string.mandatory_type) + context.getString(R.string.missed_call),
            callType = context.getString(R.string.missed_call),
            phoneArea = context.getString(R.string.unknown_area),
            location = "\n" + context.getString(R.string.location_longitude, TemplateSamples.LONGITUDE) +
                "\n" + context.getString(R.string.location_latitude, TemplateSamples.LATITUDE) +
                "\n" + context.getString(R.string.location_address, address) + "\n",
            locationAddress = address,
            batteryStatus = discharging,
            batteryPlugged = ac,
            batteryInfo = "\n" + context.getString(R.string.battery_level, "76%") + "\n" + context.getString(R.string.battery_status, discharging) +
                "\n" + context.getString(R.string.battery_health, context.getString(R.string.battery_good)) + "\n" + context.getString(R.string.battery_plugged, ac),
            taskName = context.getString(R.string.template_sample_task),
            taskText = context.getString(R.string.template_sample_task_text),
        )
    }

    private fun sampleContext(grid: View, scene: String) = grid.getTag(R.id.template_sample_context) as? SampleContext ?: when (scene) {
        "call" -> SampleContext.CALL
        "app" -> SampleContext.APP
        else -> SampleContext.SMS
    }

    private fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()

    private fun color(view: View, attr: Int) = MaterialColors.getColor(view, attr)

    /**
     * Puts Insert data and Preview into the field's grid. [items] keeps the exact tokens, order, exclusions and
     * per-context availability that createTagButtons computed; only the presentation changes.
     */
    fun install(context: Context, grid: GridLayout, editText: EditText, scene: String, items: List<Item>) {
        grid.removeAllViews()
        // Bark auto copy and the Feishu message card share scene "basic" with titles but fall back differently.
        val fallback = when (editText.id) {
            R.id.et_auto_copy_template -> TemplateFallback.AUTO_COPY_CODE
            R.id.et_message_card -> TemplateFallback.TARGET_CARD
            else -> TemplateFallback.forScene(scene)
        }
        val insert = button(context, R.string.template_insert_data, R.drawable.ic_add, tonal = true)
        insert.contentDescription = context.getString(R.string.template_insert_data_description)
        insert.setOnClickListener { v ->
            val sample = sampleText(v.context)
            // A rule or task field shows examples for its own message type; shared fields use each item's typical one.
            val own = if (scene in setOf("sms", "call", "app") || grid.getTag(R.id.template_sample_context) != null) sampleContext(grid, scene) else null
            showList(v.context, editText, v.context.getString(R.string.template_insert_data_hint), listOf(Section(null, items) { example(v.context, it, sample, own) }))
        }
        val preview = button(context, R.string.template_preview, R.drawable.ic_template_preview, tonal = false)
        preview.contentDescription = context.getString(R.string.template_preview_description)
        preview.setOnClickListener { v -> showPreview(v.context, editText.text?.toString().orEmpty(), sampleContext(grid, scene), items, fallback) }
        addActions(grid, insert, preview)
    }

    /**
     * Side by side when both labels fit on one line in half the width, otherwise stacked (long Dutch labels,
     * large font). Cells are auto-placed, so a host that later changes columnCount cannot break them.
     */
    private fun addActions(grid: GridLayout, vararg buttons: MaterialButton) {
        grid.columnCount = 1
        val weighted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP // column weights need API 21
        buttons.forEach { b ->
            grid.addView(b, GridLayout.LayoutParams().apply {
                height = GridLayout.LayoutParams.WRAP_CONTENT
                if (weighted) {
                    width = 0
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                } else width = GridLayout.LayoutParams.WRAP_CONTENT
                val m = dp(grid.context, 4)
                setMargins(0, m, 0, m)
            })
        }
        if (!weighted) return
        grid.post {
            val half = grid.width / 2 - dp(grid.context, 8)
            val fits = half > 0 && buttons.all { b ->
                b.paint.measureText(b.text.toString()) + b.iconSize + b.iconPadding + b.paddingLeft + b.paddingRight <= half
            }
            if (fits) {
                grid.columnCount = 2
                (buttons.first().layoutParams as GridLayout.LayoutParams).marginEnd = dp(grid.context, 8)
                grid.requestLayout()
            }
        }
    }

    private fun button(context: Context, @StringRes text: Int, icon: Int, tonal: Boolean): MaterialButton {
        // The overlay selects the M3 tonal / outlined icon-button style through materialButtonStyle.
        val themed = androidx.appcompat.view.ContextThemeWrapper(context, if (tonal) R.style.Template_ThemeOverlay_TonalButton else R.style.Template_ThemeOverlay_OutlinedButton)
        return MaterialButton(themed).apply {
            setText(text)
            setIconResource(icon)
            minimumHeight = dp(context, 48)
            maxLines = 3
        }
    }

    // ---------- Insert data ----------

    fun example(context: Context, token: String, sample: SampleText, sampleContext: SampleContext? = null): String {
        val name = TemplateTokens.name(token)
        val value = TemplateSamples.values(sampleContext ?: TemplateSamples.homeContext(name), sample)[name].orEmpty()
        return value.trim().replace('\n', ' ').ifEmpty { context.getString(R.string.template_example_empty) }
    }

    private fun showList(context: Context, editText: EditText, hint: String, sections: List<Section>) {
        // Capture before the dialog takes focus. An untouched loaded field has no user cursor.
        val insertionPosition = if (editText.hasFocus()) editText.selectionStart else editText.text.length
        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(context, 4), 0, dp(context, 8)) }
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.template_insert_data)
            .setView(ScrollView(context).apply { addView(list) })
            .setNegativeButton(R.string.cancel, null)
            .create()
        list.addView(supporting(context, hint, dp(context, 24)))
        sections.forEach { section ->
            section.title?.let { title ->
                list.addView(MaterialTextView(context).apply {
                    text = title
                    TextViewCompat.setTextAppearance(this, M.style.TextAppearance_Material3_TitleSmall)
                    setTextColor(color(this, M.attr.colorPrimary))
                    setPadding(dp(context, 24), dp(context, 16), dp(context, 24), dp(context, 4))
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isAccessibilityHeading = true
                })
            }
            section.items.forEach { item ->
                list.addView(row(context, item, section.example(item.token)) {
                    dialog.dismiss()
                    editText.requestFocus()
                    editText.setSelection(insertionPosition.coerceIn(0, editText.text.length))
                    CommonUtils.insertOrReplaceText2Cursor(editText, item.token)
                })
            }
        }
        dialog.show()
    }

    /** List item: name (Body Large), exact token (monospace, primary) and example (Body Medium). */
    private fun row(context: Context, item: Item, example: String, onPick: () -> Unit): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            minimumHeight = dp(context, 56)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 24), dp(context, 8), dp(context, 24), dp(context, 8))
            isClickable = true
            isFocusable = true
            val ripple = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
            setBackgroundResource(ripple.resourceId)
            contentDescription = context.getString(R.string.template_item_description, item.label, item.token, example)
            setOnClickListener { onPick() }
        }
        row.addView(MaterialTextView(context).apply {
            text = item.label
            TextViewCompat.setTextAppearance(this, M.style.TextAppearance_Material3_BodyLarge)
            setTextColor(color(this, M.attr.colorOnSurface))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        })
        row.addView(MaterialTextView(context).apply {
            text = item.token
            TextViewCompat.setTextAppearance(this, M.style.TextAppearance_Material3_BodyMedium)
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(color(this, M.attr.colorPrimary))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        })
        row.addView(MaterialTextView(context).apply {
            text = context.getString(R.string.template_example, example)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            TextViewCompat.setTextAppearance(this, M.style.TextAppearance_Material3_BodyMedium)
            setTextColor(color(this, M.attr.colorOnSurfaceVariant))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        })
        return row
    }

    private fun supporting(context: Context, text: CharSequence, horizontal: Int, top: Int = 0) = MaterialTextView(context).apply {
        this.text = text
        TextViewCompat.setTextAppearance(this, M.style.TextAppearance_Material3_BodyMedium)
        setTextColor(color(this, M.attr.colorOnSurfaceVariant))
        setPadding(horizontal, top, horizontal, dp(context, 8))
    }

    // ---------- Preview ----------

    @StringRes
    private fun fallbackText(fallback: TemplateFallback) = when (fallback) {
        TemplateFallback.TITLE_FROM -> R.string.template_preview_empty_title
        TemplateFallback.RULE_MESSAGE -> R.string.template_preview_empty_rule
        TemplateFallback.GLOBAL_MESSAGE -> R.string.template_preview_empty_global
        TemplateFallback.AUTO_COPY_CODE -> R.string.template_preview_empty_auto_copy
        TemplateFallback.TARGET_CARD -> R.string.template_preview_empty_card
    }

    private fun showPreview(context: Context, template: String, sampleContext: SampleContext, items: List<Item>, fallback: TemplateFallback) {
        val values = TemplateSamples.values(sampleContext, sampleText(context))
        val offered = items.map { TemplateTokens.name(it.token) }.toSet()
        val notes = mutableListOf<CharSequence>()
        if (template.contains("{{APP_NAME")) notes += context.getString(R.string.template_preview_app_name_requirement)
        val source = if (template.trim().isNotEmpty()) template else {
            notes += context.getString(fallbackText(fallback))
            when (fallback) {
                TemplateFallback.TITLE_FROM -> TemplateTokens.token("FROM")
                TemplateFallback.RULE_MESSAGE, TemplateFallback.GLOBAL_MESSAGE -> TemplateSamples.builtInMessage(sampleContext)
                else -> ""
            }
        }
        // The Feishu card is JSON: MsgInfo.getContentFromJson JSON-escapes every value.
        val encode: (String) -> String = if (fallback == TemplateFallback.TARGET_CARD) WebhookPreview::jsonInner else { s -> s }
        val card = if (fallback == TemplateFallback.TARGET_CARD) TemplateTokens.CARD_TOKENS else emptySet()
        val result = TemplateRenderer.render(source, values, offered, card, encode = encode)
        if (fallback == TemplateFallback.RULE_MESSAGE) notes += context.getString(R.string.template_preview_replacements)
        show(context, result.text, notes + notesFor(context, result))
    }

    private fun notesFor(context: Context, r: TemplateRender): List<CharSequence> {
        val notes = mutableListOf<CharSequence>()
        fun add(@StringRes res: Int, list: List<String>) { if (list.isNotEmpty()) notes += context.getString(res, list.joinToString(", ")) }
        add(R.string.template_preview_unknown, r.unknown)
        add(R.string.template_preview_not_offered, r.notOffered)
        add(R.string.template_preview_invalid, r.invalid)
        add(R.string.template_preview_ignored, r.ignored)
        add(R.string.template_preview_target_specific, r.targetSpecific)
        add(R.string.template_preview_other_syntax, r.otherSyntax)
        if (r.md5) notes += context.getString(R.string.template_preview_md5)
        return notes
    }

    /** Material dialog: sample notice, rendered text (selectable) on a filled card, notes; one Close button. */
    private fun show(context: Context, rendered: String, notes: List<CharSequence>) {
        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(context, 24), dp(context, 4), dp(context, 24), dp(context, 8)) }
        body.addView(supporting(context, context.getString(R.string.template_preview_sample_notice), 0))
        val card = MaterialCardView(context, null, M.attr.materialCardViewFilledStyle).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(context, 4) }
        }
        card.addView(MaterialTextView(context).apply {
            text = rendered.ifEmpty { context.getString(R.string.template_preview_nothing) }
            setTextIsSelectable(true)
            TextViewCompat.setTextAppearance(this, M.style.TextAppearance_Material3_BodyLarge)
            setTextColor(color(this, if (rendered.isEmpty()) M.attr.colorOnSurfaceVariant else M.attr.colorOnSurface))
            setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12))
        })
        body.addView(card)
        notes.forEach { body.addView(supporting(context, it, 0, dp(context, 8))) }
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.template_preview)
            .setView(ScrollView(context).apply { addView(body) })
            .setPositiveButton(R.string.m3_close, null)
            .show()
    }

    // ---------- Webhook request data ([from], [msg] ...) ----------

    private val WEBHOOK_LABELS = mapOf(
        "[msg]" to R.string.template_webhook_msg, "[org_content]" to R.string.template_webhook_org_content,
        "[from]" to R.string.template_webhook_from, "[title]" to R.string.template_webhook_title,
        "[receive_time]" to R.string.template_webhook_receive_time, "[device_mark]" to R.string.template_webhook_device_mark,
        "[app_version]" to R.string.template_webhook_app_version, "[timestamp]" to R.string.template_webhook_timestamp,
        "[sign]" to R.string.template_webhook_sign,
    )

    private fun webhookFields(sample: SampleText): WebhookPreview.Fields {
        val values = TemplateSamples.values(SampleContext.SMS, sample)
        val formatted = TemplateRenderer.render(TemplateSamples.builtInMessage(SampleContext.SMS), values).text
        return WebhookPreview.Fields(
            msg = formatted, orgContent = sample.smsText, from = TemplateSamples.PHONE, title = TemplateSamples.simInfo(sample),
            deviceMark = sample.deviceName, appVersion = TemplateSamples.APP_VERSION,
        )
    }

    private fun webhookExample(field: String, f: WebhookPreview.Fields): String = when (field) {
        "[msg]" -> f.msg; "[org_content]" -> f.orgContent; "[from]" -> f.from; "[title]" -> f.title
        "[receive_time]" -> TemplateSamples.RECEIVE_TIME; "[device_mark]" -> f.deviceMark; "[app_version]" -> f.appVersion
        "[timestamp]" -> f.timestamp.toString(); "[sign]" -> f.sign; else -> ""
    }.trim().replace('\n', ' ')

    /**
     * Webhook request data. Insert data lists the [bracket] fields WebhookUtils fills, then the common {{...}}
     * tags it also fills (WebhookUtils calls MsgInfo.replaceTemplate first). Preview shows the request text for
     * the current method and format, escaped as WebhookUtils escapes it. [target] is the field that receives an
     * insert (the focused parameter field or the body); null shows a hint instead.
     */
    fun installWebhook(
        grid: GridLayout,
        target: () -> EditText?,
        payload: () -> String,
        method: () -> String,
        jsonOrTextHeader: () -> Boolean,
        hasSecret: () -> Boolean,
    ) {
        val context = grid.context
        grid.removeAllViews()
        val insert = button(context, R.string.template_insert_data, R.drawable.ic_add, tonal = true)
        insert.contentDescription = context.getString(R.string.template_insert_data_description)
        insert.setOnClickListener { v ->
            val field = target() ?: return@setOnClickListener cn.ppps.forwarder.utils.XToastUtils.info(R.string.template_webhook_pick_field)
            val c = v.context
            val sample = sampleText(c)
            val f = webhookFields(sample)
            val fields = WebhookPreview.FIELDS.map { token ->
                // WebhookUtils: [sign] needs a secret; [timestamp] in a GET query is only filled with a secret.
                val needsSecret = token == "[sign]" || (token == "[timestamp]" && method() == "GET")
                Item(token, c.getString(WEBHOOK_LABELS.getValue(token)) + if (needsSecret && !hasSecret()) " " + c.getString(R.string.template_webhook_needs_secret) else "")
            }
            showList(c, field, c.getString(R.string.template_webhook_insert_hint), listOf(
                Section(c.getString(R.string.template_webhook_section_fields), fields) { webhookExample(it, f) },
                Section(c.getString(R.string.template_webhook_section_common), CommonUtils.templateTags("basic")) { example(c, it, sample) },
            ))
        }
        val preview = button(context, R.string.template_preview, R.drawable.ic_template_preview, tonal = false)
        preview.contentDescription = context.getString(R.string.template_preview_description)
        preview.setOnClickListener { v ->
            val c = v.context
            val sample = sampleText(c)
            val text = payload()
            val m = method()
            val result = WebhookPreview.render(text, m, jsonOrTextHeader(), hasSecret(), TemplateSamples.values(SampleContext.SMS, sample), webhookFields(sample))
            val notes = mutableListOf<CharSequence>()
            if (text.contains("{{APP_NAME")) notes += c.getString(R.string.template_preview_app_name_requirement)
            notes += c.getString(when (WebhookPreview.body(m, text, jsonOrTextHeader())) {
                WebhookPreview.Body.QUERY -> R.string.template_webhook_preview_query
                WebhookPreview.Body.JSON_OR_TEXT -> R.string.template_webhook_preview_json
                WebhookPreview.Body.FORM -> R.string.template_webhook_preview_form
            })
            if (text.trim().isEmpty()) notes += c.getString(R.string.template_webhook_preview_empty)
            notes += c.getString(R.string.template_webhook_preview_msg)
            if (!hasSecret() && text.contains("[sign]")) notes += c.getString(R.string.template_webhook_preview_no_secret)
            show(c, result.text, notes + notesFor(c, result))
        }
        addActions(grid, insert, preview)
    }
}
