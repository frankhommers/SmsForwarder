package cn.ppps.forwarder.security

import cn.ppps.forwarder.utils.interceptor.LoggingInterceptor
import cn.ppps.forwarder.utils.mail.EmailSender
import com.sun.mail.util.SocketFetcher
import okhttp3.Call
import okhttp3.Connection
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.internal.tls.OkHostnameVerifier
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.BeforeClass
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.InetAddress
import java.security.KeyFactory
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlin.concurrent.thread

/**
 * Loopback-only TLS and logging regression checks. No external network calls are made: every connection goes to
 * a TLS listener on 127.0.0.1 that presents a self-generated certificate for `DNS:localhost` only.
 */
class TransportSecurityTest {

    companion object {
        // Fixtures in src/test/resources/security/: self-signed certificate, SAN DNS:localhost only, generated for
        // these tests (not a real CA, never installed on a device).
        private lateinit var server: SSLServerSocket
        lateinit var fixtureTrust: X509TrustManager
        var port = 0

        private fun pemBody(path: String): ByteArray {
            val text = TransportSecurityTest::class.java.getResource(path)!!.readText()
            return Base64.getMimeDecoder().decode(text.lines().filterNot { it.startsWith("-----") }.joinToString(""))
        }

        @BeforeClass @JvmStatic
        fun startServer() {
            val cert = CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(pemBody("/security/fixture-cert.pem"))) as X509Certificate
            val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(pemBody("/security/fixture-key.pk8.pem")))
            val ks = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null); setKeyEntry("srv", key, CharArray(0), arrayOf(cert)) }
            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(ks, CharArray(0)) }
            val ctx = SSLContext.getInstance("TLS").apply { init(kmf.keyManagers, null, null) }
            server = ctx.serverSocketFactory.createServerSocket(0, 50, InetAddress.getLoopbackAddress()) as SSLServerSocket
            port = server.localPort
            thread(isDaemon = true) {
                while (!server.isClosed) {
                    try {
                        val s = server.accept() as SSLSocket
                        thread(isDaemon = true) {
                            try {
                                s.soTimeout = 5000
                                s.startHandshake()
                                // Minimal HTTP/1.1 response; SMTP probes close after the handshake.
                                s.outputStream.write("HTTP/1.1 204 No Content\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                                s.outputStream.flush()
                            } catch (_: Exception) {
                            } finally {
                                try { s.close() } catch (_: Exception) {}
                            }
                        }
                    } catch (_: Exception) {
                        return@thread
                    }
                }
            }
            val trustStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null); setCertificateEntry("fixture", cert) }
            fixtureTrust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                .apply { init(trustStore) }.trustManagers.filterIsInstance<X509TrustManager>().first()
        }

        @AfterClass @JvmStatic
        fun stopServer() { server.close() }
    }

    // ---------- HTTP (OkHttp, as used by XHttp for every HTTP target) ----------

    private fun client(trust: X509TrustManager?, verifier: javax.net.ssl.HostnameVerifier): OkHttpClient {
        val b = OkHttpClient.Builder().hostnameVerifier(verifier)
            .connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).retryOnConnectionFailure(false)
        if (trust != null) {
            val ctx = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
            b.sslSocketFactory(ctx.socketFactory, trust)
        }
        return b.build()
    }

    private fun get(c: OkHttpClient, host: String) = c.newCall(Request.Builder().url("https://$host:$port/").build()).execute().use { it.code() }

    @Test fun xhttpLibraryDefaultHostnameVerifierAcceptsEverything_documentedReason() {
        // Evidence for the fix: the packaged XHttp 2.0.10 verifier returns true for any host.
        assertTrue(com.xuexiang.xhttp2.https.DefaultHostnameVerifier().verify("attacker.invalid", null))
    }

    @Test fun sharedXhttpClientUsesOkHostnameVerifierAndNoBodyLogging() {
        com.xuexiang.xhttp2.XHttp.init(android.app.Application())
        val before = com.xuexiang.xhttp2.XHttp.getOkHttpClient()
        assertTrue(before.hostnameVerifier() is com.xuexiang.xhttp2.https.DefaultHostnameVerifier)
        cn.ppps.forwarder.utils.sdkinit.HttpTransportSecurity.apply(com.xuexiang.xhttp2.XHttp.getInstance())
        val after = com.xuexiang.xhttp2.XHttp.getOkHttpClient()
        assertTrue(after.hostnameVerifier() === OkHostnameVerifier.INSTANCE)
        assertTrue(after.interceptors().none { it is com.xuexiang.xhttp2.interceptor.HttpLoggingInterceptor })
    }

    @Test fun okHostnameVerifierRejectsCertificateForAnotherName() {
        // Chain is trusted (fixture CA), but the certificate is for localhost, not 127.0.0.1.
        assertEquals(204, get(client(fixtureTrust, OkHostnameVerifier.INSTANCE), "localhost"))
        try {
            get(client(fixtureTrust, OkHostnameVerifier.INSTANCE), "127.0.0.1")
            fail("certificate for another host name must be rejected")
        } catch (e: SSLPeerUnverifiedException) {
            // expected
        }
    }

    @Test fun xhttpDefaultVerifierWouldHaveAcceptedWrongName_regressionContrast() {
        assertEquals(204, get(client(fixtureTrust, com.xuexiang.xhttp2.https.DefaultHostnameVerifier()), "127.0.0.1"))
    }

    @Test fun defaultTrustRejectsUntrustedChain() {
        // No ignoreHttpsCert(): OkHttp uses the platform trust store, which does not contain the fixture.
        try {
            get(client(null, OkHostnameVerifier.INSTANCE), "localhost")
            fail("self-signed chain must be rejected by default trust")
        } catch (e: SSLHandshakeException) {
            // expected
        }
    }

    // ---------- SMTP (Jakarta Mail SocketFetcher, as used by EmailSender) ----------

    private fun smtpConnect(host: String, openSSL: Boolean): String = try {
        val props = EmailSender.smtpProperties(host, port.toString(), openSSL, startTls = false)
        SocketFetcher.getSocket(host, port, props, "mail.smtp", openSSL).close()
        "CONNECTED"
    } catch (e: IOException) {
        "REJECTED"
    }

    @Test fun smtpPropertiesNoLongerBypassTrustAndRequireIdentity() {
        val ssl = EmailSender.smtpProperties("smtp.example.org", "465", openSSL = true, startTls = false)
        assertNull(ssl["mail.smtp.ssl.socketFactory"])
        assertNull(ssl["mail.smtp.ssl.trust"])
        assertEquals("true", ssl["mail.smtp.ssl.enable"])
        assertEquals("true", ssl["mail.smtp.ssl.checkserveridentity"])
        val tls = EmailSender.smtpProperties("smtp.example.org", "587", openSSL = false, startTls = true)
        assertEquals("true", tls["mail.smtp.starttls.enable"])
        assertEquals("true", tls["mail.smtp.starttls.required"])
        assertEquals("true", tls["mail.smtp.ssl.checkserveridentity"])
        val plain = EmailSender.smtpProperties("mail.lan", "25", openSSL = false, startTls = false)
        assertNull(plain["mail.smtp.ssl.enable"])
        assertNull(plain["mail.smtp.starttls.enable"])
        assertNull(plain["mail.smtp.starttls.required"])
    }

    // ---------- SMTP socket timeouts (fix 99) ----------

    private val smtpTimeoutKeys = listOf("mail.smtp.connectiontimeout", "mail.smtp.timeout", "mail.smtp.writetimeout")

    @Test fun smtpPropertiesAlwaysDeclareBoundedSocketTimeouts() {
        // Default (no Android preferences needed): bounded, never absent/infinite.
        for (p in listOf(
            EmailSender.smtpProperties("smtp.example.org", "465", openSSL = true, startTls = false),
            EmailSender.smtpProperties("smtp.example.org", "587", openSSL = false, startTls = true),
            EmailSender.smtpProperties("mail.lan", "25", openSSL = false, startTls = false),
        )) smtpTimeoutKeys.forEach { assertEquals(it, EmailSender.DEFAULT_SMTP_TIMEOUT_MS.toString(), p[it]) }
        // Configured value as production passes it: SettingUtils.requestTimeout seconds -> milliseconds.
        val configured = EmailSender.smtpProperties("smtp.example.org", "587", openSSL = false, startTls = true, timeoutMs = EmailSender.smtpTimeoutMillis(19))
        smtpTimeoutKeys.forEach { assertEquals(it, "19000", configured[it]) }
        // Timeouts do not weaken the TLS policy.
        assertEquals("true", configured["mail.smtp.starttls.required"])
        assertEquals("true", configured["mail.smtp.ssl.checkserveridentity"])
        assertNull(configured["mail.smtp.ssl.trust"])
        assertNull(configured["mail.smtp.ssl.socketFactory"])
    }

    @Test fun smtpTimeoutRejectsInvalidAndOverflowingValues() {
        assertEquals(1_000, EmailSender.smtpTimeoutMillis(1))
        assertEquals(60_000, EmailSender.smtpTimeoutMillis(60))
        assertEquals(EmailSender.DEFAULT_SMTP_TIMEOUT_MS, EmailSender.smtpTimeoutMillis(0))
        assertEquals(EmailSender.DEFAULT_SMTP_TIMEOUT_MS, EmailSender.smtpTimeoutMillis(-5))
        assertEquals(EmailSender.MAX_SMTP_TIMEOUT_MS, EmailSender.smtpTimeoutMillis(Int.MAX_VALUE)) // Int * 1000 would overflow
        assertEquals(EmailSender.MAX_SMTP_TIMEOUT_MS, EmailSender.smtpTimeoutMillis(3_000_000))
        for (bad in listOf(0, -1, Int.MIN_VALUE)) {
            val p = EmailSender.smtpProperties("mail.lan", "25", openSSL = false, startTls = false, timeoutMs = bad)
            smtpTimeoutKeys.forEach { assertEquals("$it for $bad", EmailSender.DEFAULT_SMTP_TIMEOUT_MS.toString(), p[it]) }
        }
        val capped = EmailSender.smtpProperties("mail.lan", "25", openSSL = false, startTls = false, timeoutMs = Int.MAX_VALUE)
        smtpTimeoutKeys.forEach { assertEquals(it, EmailSender.MAX_SMTP_TIMEOUT_MS.toString(), capped[it]) }
    }

    @Test fun smtpConnectToServerThatNeverGreetsFailsWithinTimeout() {
        // Loopback server that accepts but never sends the 220 greeting (Jakarta Mail would otherwise wait forever).
        val silent = java.net.ServerSocket(0, 5, InetAddress.getLoopbackAddress())
        val held = java.util.Collections.synchronizedList(mutableListOf<java.net.Socket>())
        thread(isDaemon = true) { while (!silent.isClosed) try { held += silent.accept() } catch (_: Exception) { break } }
        try {
            val props = EmailSender.smtpProperties("127.0.0.1", silent.localPort.toString(), openSSL = false, startTls = false, timeoutMs = 1500)
            val session = jakarta.mail.Session.getInstance(props, EmailSender.authenticator("s@example.invalid", "unused"))
            val start = System.nanoTime()
            val result = try { session.getTransport("smtp").use { it.connect() }; "CONNECTED" } catch (e: jakarta.mail.MessagingException) { "FAILED" }
            val elapsedMs = (System.nanoTime() - start) / 1_000_000
            assertEquals("FAILED", result)
            assertTrue("elapsed $elapsedMs ms", elapsedMs in 1000..10_000)
        } finally {
            held.toList().forEach { try { it.close() } catch (_: Exception) {} }
            silent.close()
        }
    }

    @Test fun smtpsRejectsUntrustedChainWithPlatformTrust() {
        assertEquals("REJECTED", smtpConnect("localhost", openSSL = true))
    }

    @Test fun smtpsVerifiesServerIdentityWhenChainIsTrusted() {
        // Supply a factory that trusts the fixture chain, so only Jakarta Mail's identity check decides.
        val ctx = SSLContext.getInstance("TLS").apply { init(null, arrayOf(fixtureTrust), null) }
        fun connect(host: String) = try {
            val props = EmailSender.smtpProperties(host, port.toString(), openSSL = true, startTls = false)
            props["mail.smtp.ssl.socketFactory"] = ctx.socketFactory
            SocketFetcher.getSocket(host, port, props, "mail.smtp", true).close(); "CONNECTED"
        } catch (e: IOException) { "REJECTED" }
        assertEquals("CONNECTED", connect("localhost"))
        assertEquals("REJECTED", connect("127.0.0.1"))
    }

    // ---------- Logging ----------

    private class FakeChain(private val request: Request, private val result: () -> Response) : Interceptor.Chain {
        override fun request() = request
        override fun proceed(request: Request) = result()
        override fun connection(): Connection? = null
        override fun call(): Call = throw UnsupportedOperationException()
        override fun connectTimeoutMillis() = 0
        override fun withConnectTimeout(timeout: Int, unit: TimeUnit) = this
        override fun readTimeoutMillis() = 0
        override fun withReadTimeout(timeout: Int, unit: TimeUnit) = this
        override fun writeTimeoutMillis() = 0
        override fun withWriteTimeout(timeout: Int, unit: TimeUnit) = this
    }

    private val secretRequest = Request.Builder()
        .url("https://user:pa55@api.telegram.org/bot123456:SECRET-TOKEN/sendMessage?chat_id=42&text=MESSAGE-BODY")
        .header("Authorization", "Bearer HEADER-SECRET")
        .post(RequestBody.create(MediaType.parse("application/json"), "{\"text\":\"MESSAGE-BODY\",\"password\":\"BODY-SECRET\"}"))
        .build()

    private fun response(req: Request) = Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
        .body(ResponseBody.create(MediaType.parse("application/json"), "{\"access_token\":\"RESPONSE-SECRET\"}")).build()

    @Test fun requestTraceContainsOnlyOriginAndStatus() {
        val lines = mutableListOf<String>()
        val resp = LoggingInterceptor(5L) { lines += it }.intercept(FakeChain(secretRequest) { response(secretRequest) })
        val all = lines.joinToString("\n")
        listOf("SECRET-TOKEN", "MESSAGE-BODY", "HEADER-SECRET", "BODY-SECRET", "RESPONSE-SECRET", "pa55", "user", "chat_id", "/bot")
            .forEach { assertFalse("trace leaked $it: $all", all.contains(it)) }
        assertEquals(listOf("--> POST https://api.telegram.org"), lines.take(1))
        assertTrue(all, lines[1].startsWith("<-- 200 OK https://api.telegram.org ("))
        // Body is left untouched for the target's own callback.
        assertEquals("{\"access_token\":\"RESPONSE-SECRET\"}", resp.body()!!.string())
    }

    @Test fun requestTraceOnFailureRecordsOnlyExceptionType() {
        val lines = mutableListOf<String>()
        try {
            LoggingInterceptor(5L) { lines += it }.intercept(FakeChain(secretRequest) {
                throw IOException("failed to connect to https://api.telegram.org/bot123456:SECRET-TOKEN/")
            })
            fail("exception must propagate")
        } catch (e: IOException) {
            // expected: retry/error handling of the caller is unchanged
        }
        assertEquals(listOf("--> POST https://api.telegram.org", "<-- HTTP FAILED: IOException"), lines)
    }

    @Test fun originKeepsNonDefaultPortAndIpv6AndLocalHttp() {
        assertEquals("http://192.168.1.10:8080", LoggingInterceptor.origin(HttpUrl.parse("http://192.168.1.10:8080/hook?key=SECRET")!!))
        assertEquals("https://[2001:db8::1]", LoggingInterceptor.origin(HttpUrl.parse("https://[2001:db8::1]/x")!!))
        assertEquals("https://example.org:8443", LoggingInterceptor.origin(HttpUrl.parse("https://u:p@example.org:8443/a#f")!!))
    }
}
