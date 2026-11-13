package cn.ppps.forwarder.utils

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL

class PublicIpLookupTest {
    private class Connection(private val body: String, private val status: Int = 200) :
        HttpURLConnection(URL(PublicIpLookup.ENDPOINT)) {
        var disconnected = false
        var closed = false
        override fun getResponseCode() = status
        override fun getInputStream() = object : ByteArrayInputStream(body.toByteArray()) {
            override fun close() { closed = true; super.close() }
        }
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun connect() {}
    }

    @Test fun parsesIpv4() { assertEquals("203.0.113.1", PublicIpLookup.parse("203.0.113.1\n")) }
    @Test fun parsesIpv6() { assertEquals("2001:db8::1", PublicIpLookup.parse("2001:db8::1")) }
    @Test fun rejectsNonLiteralAndMalformedResponses() {
        listOf("example.com", "999.0.0.1", "1.2.3", "::g", "", "<html>error</html>",
            "203.0.113.1\n198.51.100.1", "fe80::1%wlan0").forEach {
            assertEquals(it, "", PublicIpLookup.parse(it))
        }
    }
    @Test fun requestIsBoundedAndClosesResources() {
        val c = Connection("203.0.113.1\n")
        assertEquals("203.0.113.1", PublicIpLookup.fetch { c })
        assertEquals("GET", c.requestMethod)
        assertEquals(5000, c.connectTimeout)
        assertEquals(5000, c.readTimeout)
        assertFalse(c.instanceFollowRedirects)
        assertTrue(c.disconnected)
        assertTrue(c.closed)
    }
    @Test fun rejectsRedirects() {
        val c = Connection("203.0.113.1", 302)
        assertEquals("", PublicIpLookup.fetch { c })
        assertTrue(c.disconnected)
    }
    @Test fun rejectsOversizedResponse() {
        val c = Connection("1".repeat(10000))
        assertEquals("", PublicIpLookup.fetch { c })
        assertTrue(c.closed)
    }
    @Test fun networkFailureIsEmptyResult() {
        assertEquals("", PublicIpLookup.fetch { throw java.io.IOException("offline") })
    }
}
