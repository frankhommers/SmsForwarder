package cn.ppps.forwarder.utils.template

import com.google.gson.Gson
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.GregorianCalendar

/**
 * Pure helpers behind the Insert data picker and the local Preview.
 *
 * Nothing here reads contacts, location, battery, network, installed apps or settings, and nothing is sent:
 * every value comes from the caller's synthetic sample. The rules mirror MsgInfo.replaceTemplate and
 * WebhookUtils.sendMsg, but the preview never calls them (they perform live lookups).
 */
object TemplateTokens {
    /** Same literals as the R.string.tag_* resources (translatable=false); TemplateTextTest pins the two together. */
    val NAMES = listOf(
        "FROM", "SMS", "PACKAGE_NAME", "APP_NAME", "MSG", "CARD_SLOT", "CARD_SUBID", "RECEIVE_TIME", "CURRENT_TIME",
        "DEVICE_NAME", "APP_VERSION", "TITLE", "CALL_TYPE", "LOCATION", "LOCATION_LONGITUDE", "LOCATION_LATITUDE",
        "LOCATION_ADDRESS", "BATTERY_PCT", "BATTERY_STATUS", "BATTERY_PLUGGED", "BATTERY_INFO", "BATTERY_INFO_SIMPLE",
        "UID", "IPV4", "IPV6", "IP_LIST", "NET_TYPE", "CONTACT_NAME", "PHONE_AREA", "RULE_TITLE",
    )

    /** Filled in by the Feishu card builders themselves (FeishuUtils/FeishuAppUtils), not by MsgInfo. */
    val CARD_TOKENS = setOf("MSG_TITLE", "MSG_TIME", "MSG_FROM", "MSG_CONTENT", "MSG_URL")

    fun token(name: String) = "{{$name}}"
    fun name(token: String) = token.removePrefix("{{").removeSuffix("}}")
}

/** Which kind of message the sample pretends to be. MsgInfo fills the same tag differently per kind. */
enum class SampleContext {
    SMS, CALL, APP,
    /** Automated task (MsgInfo type "task"): sender field = task name, text = task description. */
    TASK
}

/** What the forwarder does when the field is left empty (verified against the sender code). */
enum class TemplateFallback {
    /** MsgInfo.getTitleForSend: empty -> {{FROM}}. Titles, subjects, display names. */
    TITLE_FROM,
    /** Rule or task action: empty -> general layout (if switched on and not empty) -> built-in layout. */
    RULE_MESSAGE,
    /** General setting: empty -> built-in layout. */
    GLOBAL_MESSAGE,
    /** Bark auto copy: empty -> a verification code found in the text, otherwise nothing. */
    AUTO_COPY_CODE,
    /** Feishu message card: empty -> the target's own card; filled -> JSON-escaped values. */
    TARGET_CARD;

    companion object {
        fun forScene(scene: String) = when (scene) {
            "sms", "call", "app" -> RULE_MESSAGE
            "all" -> GLOBAL_MESSAGE
            else -> TITLE_FROM
        }
    }
}

/** Localized synthetic sample text supplied by the UI layer (resources), so this file stays pure. */
data class SampleText(
    val smsText: String,
    val simLabel: String,
    val ruleTitle: String,
    val deviceName: String,
    val appTitle: String,
    val appText: String,
    val callDetails: String,
    val callType: String,
    val phoneArea: String,
    val location: String,
    val locationAddress: String,
    val batteryStatus: String,
    val batteryPlugged: String,
    val batteryInfo: String,
    val taskName: String = "",
    val taskText: String = "",
)

object TemplateSamples {
    const val PHONE = "+31612345678"
    const val PACKAGE = "com.example.chat"
    const val APP_NAME = "Example Chat"
    const val CONTACT = "Alex Jansen"
    const val APP_VERSION = "1.0"
    const val RECEIVE_TIME = "2026-03-14 09:26:53"
    const val CURRENT_TIME = "2026-03-14 09:27:00"
    const val LONGITUDE = "4.8952"
    const val LATITUDE = "52.3702"
    const val IPV4 = "203.0.113.7"
    const val IPV6 = "2001:db8::7"
    const val TIMESTAMP = 1773480413000L
    const val SIGN = "a1b2c3%3D%3D"

    /** Fixed receive time in the device time zone, so formats such as [receive_time:HH:mm] are deterministic. */
    fun receivedDate(): Date = GregorianCalendar(2026, 2, 14, 9, 26, 53).time

    fun simInfo(t: SampleText) = "SIM1_" + t.simLabel

    /**
     * One synthetic value per token, keyed by token name. Mirrors which MsgInfo field replaceTemplate uses:
     * {{TITLE}} and {{CARD_SLOT}} are simInfo (the SIM label for SMS/calls, the notification title for apps),
     * {{PACKAGE_NAME}} is the sender field, and {{SMS}}/{{MSG}} are both the message text.
     */
    fun values(context: SampleContext, t: SampleText): Map<String, String> {
        val from = when (context) {
            SampleContext.APP -> PACKAGE
            SampleContext.TASK -> t.taskName
            else -> PHONE
        }
        val content = when (context) {
            SampleContext.SMS -> t.smsText
            SampleContext.CALL -> t.callDetails
            SampleContext.APP -> t.appText
            SampleContext.TASK -> t.taskText
        }
        val simInfo = when (context) {
            SampleContext.APP -> t.appTitle
            SampleContext.TASK -> t.taskName
            else -> simInfo(t)
        }
        return mapOf(
            "FROM" to from,
            "PACKAGE_NAME" to from,
            "SMS" to content,
            "MSG" to content,
            "CARD_SLOT" to simInfo,
            "TITLE" to simInfo,
            "CARD_SUBID" to if (context == SampleContext.SMS || context == SampleContext.CALL) "1" else "0",
            "UID" to if (context == SampleContext.APP) "10123" else "0",
            "RULE_TITLE" to t.ruleTitle,
            "RECEIVE_TIME" to RECEIVE_TIME,
            "CURRENT_TIME" to CURRENT_TIME,
            "DEVICE_NAME" to t.deviceName,
            "APP_VERSION" to APP_VERSION,
            "CALL_TYPE" to t.callType,
            "IPV4" to IPV4,
            "IPV6" to IPV6,
            "IP_LIST" to "$IPV4\n$IPV6",
            "BATTERY_PCT" to "76%",
            "BATTERY_STATUS" to t.batteryStatus,
            "BATTERY_PLUGGED" to t.batteryPlugged,
            "BATTERY_INFO" to t.batteryInfo,
            "BATTERY_INFO_SIMPLE" to "76% - " + t.batteryPlugged,
            "NET_TYPE" to "WIFI",
            "APP_NAME" to if (context == SampleContext.APP) APP_NAME else "",
            "LOCATION" to t.location,
            "LOCATION_LONGITUDE" to LONGITUDE,
            "LOCATION_LATITUDE" to LATITUDE,
            "LOCATION_ADDRESS" to t.locationAddress,
            "CONTACT_NAME" to CONTACT,
            "PHONE_AREA" to t.phoneArea,
        )
    }

    /** The context in which a token's example is most meaningful (used for one-line picker examples). */
    fun homeContext(name: String) = when (name) {
        "PACKAGE_NAME", "APP_NAME", "TITLE", "UID", "MSG" -> SampleContext.APP
        "CALL_TYPE" -> SampleContext.CALL
        else -> SampleContext.SMS
    }

    /** Same text as the built-in layout in MsgInfo.getContentForSend (the colon is the full-width one used there). */
    fun builtInMessage(context: SampleContext): String =
        "{{FROM}}\n{{SMS}}\n{{CARD_SLOT}}\n" +
            when (context) {
                SampleContext.SMS, SampleContext.CALL -> "SubId\uFF1A{{CARD_SUBID}}\n"
                SampleContext.APP -> "UID\uFF1A{{UID}}\n"
                SampleContext.TASK -> ""
            } +
            "{{RECEIVE_TIME}}\n{{DEVICE_NAME}}"
}

/**
 * Result of a local preview. Lists hold token texts exactly as typed, in order of appearance, without duplicates.
 * unknown: not a known tag. notOffered: a known tag that this field's Insert data list does not offer.
 * invalid: a find/replace pattern that is not a valid regular expression. ignored: a known tag that forwarding
 * leaves as typed in this combination (see TemplateRenderer.render). targetSpecific: filled by the target itself.
 * otherSyntax: [bracket] fields, which this field does not fill.
 */
data class TemplateRender(
    val text: String,
    val unknown: List<String> = emptyList(),
    val notOffered: List<String> = emptyList(),
    val invalid: List<String> = emptyList(),
    val ignored: List<String> = emptyList(),
    val targetSpecific: List<String> = emptyList(),
    val otherSyntax: List<String> = emptyList(),
    val md5: Boolean = false,
)

object TemplateRenderer {
    // {{NAME}} or {{NAME###pattern===replacement}}; same lazy groups as MsgInfo.replaceTag.
    private val TAG = Regex("""\{\{([A-Za-z0-9_]+)(?:###(.+?)===(.*?))?\}\}""")
    private val BRACKET = Regex("""\[(?:msg|content|org_content|from|title|card_slot|device_mark|app_version|timestamp|sign|receive_time(?::[^\]]*)?)\]""")
    private val MD5 = Regex("""md5\((.*?)\)""")

    /** MsgInfo only looks these up when the exact upper-case plain tag occurs (indexOf check before the lookup). */
    private val GATED = setOf("APP_NAME", "CONTACT_NAME", "PHONE_AREA")

    /** Like MsgInfo.replaceTag: a pattern whose replacement fails (e.g. a missing group $5) leaves the tag as typed. */
    private fun replaced(value: String, pattern: String, replacement: String): String? =
        try { value.replace(pattern.toRegex(), replacement) } catch (e: Exception) { null }

    /**
     * One pass over the common {{...}} tags with the rules of MsgInfo.replaceTag:
     * - plain tags match case-insensitively; the {{NAME###pattern===replacement}} form needs the exact upper-case name;
     * - when a name has a working pattern form, forwarding fills only the last one and leaves the plain tag and the
     *   other pattern forms of that name as typed (reported as ignored instead of being shown as working);
     * - {{APP_NAME}}, {{CONTACT_NAME}} and {{PHONE_AREA}} are only filled when the exact upper-case plain tag occurs.
     * Unknown tags and invalid patterns stay as typed, as in forwarding. Values are never re-scanned, so a sample
     * containing a tag cannot expand into another one (forwarding replaces tag by tag; that order is not modelled).
     *
     * @param offered token names the field's Insert data list offers; other known tokens are reported as notOffered.
     * @param stripNull MsgInfo.getTitleForSend/getContentForSend remove the literal text "null" first.
     */
    fun render(
        template: String,
        values: Map<String, String>,
        offered: Set<String>? = null,
        targetSpecific: Set<String> = emptySet(),
        stripNull: Boolean = true,
        reportOtherSyntax: Boolean = true,
        encode: (String) -> String = { it },
    ): TemplateRender {
        val source = if (stripNull) template.replace("null", "") else template
        val matches = TAG.findAll(source).toList()
        // Last working pattern form per (exact) name: the only form of that name forwarding fills.
        val winner = HashMap<String, String>()
        matches.filter { m -> m.groups[2] != null && values[m.groupValues[1]]?.let { replaced(it, m.groupValues[2], m.groupValues[3]) } != null }
            .forEach { winner[it.groupValues[1]] = it.value }
        val unknown = LinkedHashSet<String>()
        val notOffered = LinkedHashSet<String>()
        val invalid = LinkedHashSet<String>()
        val ignored = LinkedHashSet<String>()
        val specific = LinkedHashSet<String>()
        val text = TAG.replace(source) { m ->
            val typed = m.groupValues[1]
            val pattern = m.groups[2]?.value
            val name = if (pattern == null) typed.uppercase() else typed
            val value = values[name]
            val known = value != null || typed.uppercase() in values
            when {
                value == null && typed.uppercase() in targetSpecific -> { specific += m.value; m.value }
                value == null && known -> { ignored += m.value; m.value } // lower-case name in a pattern form
                value == null -> { unknown += m.value; m.value }
                name in GATED && "{{$name}}" !in source -> { ignored += m.value; m.value }
                pattern == null && name in winner -> { ignored += m.value; m.value }
                pattern == null -> {
                    if (offered != null && name !in offered) notOffered += m.value
                    encode(value!!)
                }
                replaced(value!!, pattern, m.groupValues[3]) == null -> { invalid += m.value; m.value }
                !m.value.equals(winner[name], ignoreCase = true) -> { ignored += m.value; m.value }
                else -> {
                    if (offered != null && name !in offered) notOffered += m.value
                    encode(replaced(value, pattern, m.groupValues[3])!!)
                }
            }
        }.trim()
        val other = if (reportOtherSyntax) BRACKET.findAll(source).map { it.value }.distinct().toList() else emptyList()
        return TemplateRender(text, unknown.toList(), notOffered.toList(), invalid.toList(), ignored.toList(), specific.toList(), other)
    }

    fun hasMd5(template: String) = MD5.containsMatchIn(template)

    /** WebhookUtils hashes md5(...) before anything else; the preview shows a marker instead of a hash. */
    fun maskMd5(template: String) = MD5.replace(template, MD5_MARKER)

    const val MD5_MARKER = "\u2039md5\u203A"
}

/** Webhook request data ([from], [msg] ...): mirrors the branches and escaping in WebhookUtils.sendMsg. */
object WebhookPreview {
    enum class Body { QUERY, JSON_OR_TEXT, FORM }

    data class Fields(
        val msg: String,
        val orgContent: String,
        val from: String,
        val title: String,
        val deviceMark: String,
        val appVersion: String,
        val timestamp: Long = TemplateSamples.TIMESTAMP,
        val sign: String = TemplateSamples.SIGN,
        val received: Date = TemplateSamples.receivedDate(),
    )

    /** Bracket fields offered by the picker; [content] and [card_slot] are accepted aliases of [msg] and [title]. */
    val FIELDS = listOf("[msg]", "[org_content]", "[from]", "[title]", "[receive_time]", "[device_mark]", "[app_version]", "[timestamp]", "[sign]")

    private val RECEIVE_TIME = Regex("\\[receive_time(:(.*?))?]")

    fun body(method: String, payload: String, jsonOrTextHeader: Boolean): Body {
        val p = payload.trim()
        return when {
            method == "GET" -> Body.QUERY
            p.isNotEmpty() && (jsonOrTextHeader || p.startsWith("{")) -> Body.JSON_OR_TEXT
            else -> Body.FORM
        }
    }

    fun jsonInner(value: String): String {
        val json = Gson().toJson(value)
        return if (json.length >= 2) json.substring(1, json.length - 1) else json
    }

    private fun url(value: String) = URLEncoder.encode(value, "UTF-8")

    private fun time(f: Fields, format: String?): String =
        SimpleDateFormat(format?.removePrefix(":") ?: "yyyy-MM-dd HH:mm:ss").format(f.received)

    /**
     * @return the text that would go into the query, JSON/text body or form fields (one name=value per line);
     * invalid [receive_time:...] formats are reported in invalid and left as typed.
     */
    fun render(
        payload: String,
        method: String,
        jsonOrTextHeader: Boolean,
        hasSecret: Boolean,
        common: Map<String, String>,
        f: Fields,
    ): TemplateRender {
        val sign = if (hasSecret) f.sign else ""
        val invalid = LinkedHashSet<String>()
        fun receive(text: String, enc: (String) -> String) = RECEIVE_TIME.replace(text) {
            try { enc(time(f, it.groups[2]?.value)) } catch (e: Exception) { invalid += it.value; it.value }
        }
        val md5 = TemplateRenderer.hasMd5(payload)
        val webParams = TemplateRenderer.maskMd5(payload.trim())
        val kind = body(method, webParams, jsonOrTextHeader)
        fun common(text: String, enc: (String) -> String) =
            TemplateRenderer.render(text, common, stripNull = false, reportOtherSyntax = false, encode = enc)

        if (kind == Body.QUERY && webParams.isEmpty()) {
            val text = "from=" + url(f.from) + "&content=" + url(f.msg) + if (sign.isEmpty()) "" else "&timestamp=${f.timestamp}&sign=$sign"
            return TemplateRender(text)
        }
        if (kind == Body.QUERY) {
            val c = common(webParams, ::url)
            var text = c.text.replace("[from]", url(f.from)).replace("[content]", url(f.msg)).replace("[msg]", url(f.msg))
                .replace("[org_content]", url(f.orgContent)).replace("[device_mark]", url(f.deviceMark))
                .replace("[app_version]", url(f.appVersion)).replace("[title]", url(f.title)).replace("[card_slot]", url(f.title))
            text = receive(text, ::url).replace("\n", "%0A")
            if (hasSecret) text = text.replace("[timestamp]", f.timestamp.toString()).replace("[sign]", url(sign))
            return c.copy(text = text, invalid = c.invalid + invalid, md5 = md5)
        }
        if (kind == Body.JSON_OR_TEXT) {
            val c = common(webParams, ::jsonInner)
            var text = c.text.replace("[from]", f.from).replace("[content]", jsonInner(f.msg)).replace("[msg]", jsonInner(f.msg))
                .replace("[org_content]", jsonInner(f.orgContent)).replace("[device_mark]", jsonInner(f.deviceMark))
                .replace("[app_version]", f.appVersion).replace("[title]", jsonInner(f.title)).replace("[card_slot]", jsonInner(f.title))
            text = receive(text) { it }.replace("[timestamp]", f.timestamp.toString()).replace("[sign]", sign)
            return c.copy(text = text, invalid = c.invalid + invalid, md5 = md5)
        }
        var form = webParams
        if (form.isEmpty()) {
            form = "from=[from]&content=[content]&timestamp=[timestamp]"
            if (sign.isNotEmpty()) form += "&sign=[sign]"
        }
        val c = common(form) { it }
        val lines = c.text.trim('&').split("&").mapNotNull {
            val sep = it.indexOf("=")
            if (sep == -1) return@mapNotNull null
            val key = it.substring(0, sep).trim()
            val value = it.substring(sep + 1).trim().replace("[from]", f.from).replace("[content]", f.msg).replace("[msg]", f.msg)
                .replace("[org_content]", f.orgContent).replace("[device_mark]", f.deviceMark).replace("[app_version]", f.appVersion)
                .replace("[title]", f.title).replace("[card_slot]", f.title)
            "$key=" + receive(value) { it }.replace("[timestamp]", f.timestamp.toString()).replace("[sign]", sign)
        }
        return c.copy(text = lines.joinToString("\n"), invalid = c.invalid + invalid, md5 = md5)
    }
}

/** Cursor insertion used by CommonUtils.insertOrReplaceText2Cursor (unchanged behaviour, now unit-testable). */
object TemplateInsertion {
    /** Inserts at the selection start; an unknown or out-of-range cursor appends. The cursor ends after the insert. */
    fun position(selectionStart: Int, length: Int) = if (selectionStart < 0 || selectionStart > length) length else selectionStart

    fun apply(text: String, selectionStart: Int, insert: String): Pair<String, Int> {
        if (insert.isEmpty()) return text to selectionStart
        val at = position(selectionStart, text.length)
        val result = text.substring(0, at) + insert + text.substring(at)
        return result to (at + insert.length).coerceAtMost(result.length)
    }
}
