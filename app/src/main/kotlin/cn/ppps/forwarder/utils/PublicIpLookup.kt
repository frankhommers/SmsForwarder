package cn.ppps.forwarder.utils

import java.net.HttpURLConnection
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URL

/** Owner-approved metadata request; never sends message content or identifiers. */
object PublicIpLookup {
    const val ENDPOINT = "https://ip.hommers.it/"

    /** Literal-only parsing prevents malformed responses from triggering DNS lookups. */
    fun parse(response: String): String {
        val value = response.trim()
        if (value.length !in 2..45) return ""
        if (value.matches(Regex("[0-9.]+"))) {
            val parts = value.split('.')
            return if (parts.size == 4 && parts.all {
                it.isNotEmpty() && it.length <= 3 && it.toIntOrNull() in 0..255
            }) value else ""
        }
        if (!value.contains(':') || !value.matches(Regex("[0-9a-fA-F:]+"))) return ""
        return try {
            if (InetAddress.getByName(value) is Inet6Address) value else ""
        } catch (_: Exception) { "" }
    }

    fun fetch(): String = fetch { URL(ENDPOINT).openConnection() as HttpURLConnection }

    internal fun fetch(openConnection: () -> HttpURLConnection): String {
        var connection: HttpURLConnection? = null
        return try {
            connection = openConnection()
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.instanceFollowRedirects = false
            if (connection.responseCode != 200) return ""
            connection.inputStream.bufferedReader().use { reader ->
                // Bound even a malicious or misconfigured server response.
                val chars = CharArray(65)
                var count = 0
                while (count < chars.size) {
                    val n = reader.read(chars, count, chars.size - count)
                    if (n < 0) break
                    count += n
                }
                if (count == chars.size) "" else parse(String(chars, 0, count))
            }
        } catch (_: Exception) {
            ""
        } finally {
            connection?.disconnect()
        }
    }
}
