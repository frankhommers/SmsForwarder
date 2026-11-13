package cn.ppps.forwarder.utils.sender

/** Conservative English/Dutch OTP detection. Never copy the entire message on a miss. */
internal object VerificationCode {
    private const val KEYWORD = "(?:verification\\s+code|security\\s+code|one-time\\s+(?:code|password)|verificatiecode|beveiligingscode|inlogcode|code|otp)"
    private const val TOKEN = "([0-9]{3} [0-9]{3}|[A-Z0-9]{4,8})"
    private val after = Regex("\\b$KEYWORD\\b\\s*(?:(?:is|:|=)\\s*)?(?:G-)?$TOKEN\\b", RegexOption.IGNORE_CASE)
    private val before = Regex("\\b$TOKEN\\s+(?:is\\s+(?:your|uw|je)\\s+)?$KEYWORD\\b", RegexOption.IGNORE_CASE)

    fun extract(content: String): String? {
        val match = after.find(content) ?: before.find(content) ?: return null
        return match.groupValues[1].replace(" ", "").takeIf { token -> token.any { it.isDigit() } }
    }
}
