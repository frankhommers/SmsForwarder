package cn.ppps.forwarder.utils

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Mirrors the existing transport; mode is UI state, never a new wire/storage field. */
object WebhookEditorModel {
    enum class Mode { PARAMETERS, JSON, LEGACY }
    fun infer(method: String, payload: String, headers: Map<String, String>): Mode {
        val clean = payload.trim()
        val parameterCompatible = clean.isEmpty() || clean.split("&").all { it.contains("=") && it.substringBefore("=").isNotBlank() }
        val contentType = headers.entries.firstOrNull { it.key.equals("Content-Type", true) }?.value.orEmpty()
        if (method == "GET") return if (payload.trim().startsWith("{") || payload.trim().startsWith("/") || payload.trim().startsWith("[") || !parameterCompatible) Mode.LEGACY else Mode.PARAMETERS
        if (contentType.startsWith("text/")) return Mode.LEGACY
        if (contentType.contains("application/json") || payload.trim().startsWith("{")) return Mode.JSON
        return if (parameterCompatible) Mode.PARAMETERS else Mode.LEGACY
    }
    fun defaultGetUrl(url: String, from: String, content: String, timestamp: Long, sign: String): String {
        val separator = if (url.contains("?")) "&" else "?"
        return url + separator + "from=" + java.net.URLEncoder.encode(from, "UTF-8") +
            "&content=" + java.net.URLEncoder.encode(content, "UTF-8") +
            if (sign.isEmpty()) "" else "&timestamp=$timestamp&sign=$sign"
    }
    fun merge(original: String?, edited: JsonObject): String {
        val result = if (original.isNullOrEmpty()) JsonObject() else JsonParser.parseString(original).asJsonObject.deepCopy()
        edited.entrySet().forEach { result.add(it.key, it.value) }
        return result.toString()
    }
    fun validJson(value: String): Boolean = try {
        val json = JsonParser.parseString(value)
        value.isNotBlank() && (json.isJsonObject || json.isJsonArray)
    } catch (_: Exception) { false }
}

/** Delimiters are transport syntax, not UI formatting. Never decode or trim values here. */
object ItemListCodec {
    fun split(raw: String, delimiter: String): List<String> = if (raw.isEmpty()) emptyList() else raw.split(delimiter)
    fun join(items: List<String>, delimiter: String): String = items.joinToString(delimiter)
    fun parameter(raw: String): Pair<String, String> = raw.substringBefore('=') to raw.substringAfter('=', "")

    /** Stored regular-expression replacement line: pattern===replacement (entity/MsgInfo.regexReplace). */
    const val REGEX_SEPARATOR = "==="
    fun regexPair(raw: String): Pair<String, String> = raw.substringBefore(REGEX_SEPARATOR) to raw.substringAfter(REGEX_SEPARATOR, "")
    fun regexLine(pattern: String, replacement: String): String = pattern + REGEX_SEPARATOR + replacement

    /** Null when the row is valid, otherwise which field is wrong and why. */
    enum class RegexProblem { PATTERN_REQUIRED, PATTERN_SEPARATOR, PATTERN_TRAILING_EQUALS, PATTERN_LINE_BREAK, PATTERN_INVALID, REPLACEMENT_SEPARATOR, REPLACEMENT_LINE_BREAK }
    fun regexProblem(pattern: String, replacement: String): RegexProblem? = when {
        pattern.isBlank() -> RegexProblem.PATTERN_REQUIRED
        pattern.contains(REGEX_SEPARATOR) -> RegexProblem.PATTERN_SEPARATOR
        // MsgInfo splits at the first ===, so a pattern ending in = would lose characters when stored.
        pattern.endsWith("=") -> RegexProblem.PATTERN_TRAILING_EQUALS
        pattern.contains('\n') || pattern.contains('\r') -> RegexProblem.PATTERN_LINE_BREAK
        !compiles(pattern) -> RegexProblem.PATTERN_INVALID
        replacement.contains(REGEX_SEPARATOR) -> RegexProblem.REPLACEMENT_SEPARATOR
        replacement.contains('\n') || replacement.contains('\r') -> RegexProblem.REPLACEMENT_LINE_BREAK
        else -> null
    }
    private fun compiles(pattern: String) = try { pattern.toRegex(); true } catch (_: Exception) { false }
}
