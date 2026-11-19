package cn.ppps.forwarder.utils

/** Display choices are separate from persisted provider tokens; old targets remain editable. */
object EmailProviderChoices {
    const val CUSTOM = "other"
    private val supported = listOf("@gmail.com", "@icloud.com", CUSTOM)
    fun tokens(stored: String = CUSTOM): List<String> =
        if (stored.isNotBlank() && stored !in supported) listOf(stored) + supported else supported
    fun labels(tokens: List<String>, customLabel: String, legacyLabel: (String) -> String): List<String> =
        tokens.map { if (it == CUSTOM) customLabel else if (it in supported) it else legacyLabel(it) }
}
