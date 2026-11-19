package cn.ppps.forwarder.transport

import android.util.Base64
import cn.ppps.forwarder.database.entity.Rule
import cn.ppps.forwarder.database.entity.Sender
import cn.ppps.forwarder.entity.MsgInfo
import cn.ppps.forwarder.entity.setting.BarkSetting
import cn.ppps.forwarder.entity.setting.EmailSetting
import cn.ppps.forwarder.entity.setting.GotifySetting
import cn.ppps.forwarder.entity.setting.TelegramSetting
import cn.ppps.forwarder.entity.setting.WebhookSetting
import cn.ppps.forwarder.utils.SENDER_LOGIC_ALL
import cn.ppps.forwarder.utils.SendUtils
import cn.ppps.forwarder.utils.SettingUtils
import cn.ppps.forwarder.utils.SharedPreference
import cn.ppps.forwarder.utils.TYPE_BARK
import cn.ppps.forwarder.utils.TYPE_EMAIL
import cn.ppps.forwarder.utils.TYPE_GOTIFY
import cn.ppps.forwarder.utils.TYPE_TELEGRAM
import cn.ppps.forwarder.utils.TYPE_WEBHOOK
import com.google.gson.Gson
import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import org.json.JSONObject
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Date
import java.util.Properties
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Drives the production forwarding entry point [SendUtils.sendMsgSender] (stored Sender JSON -> Gson -> target
 * *Utils.sendMsg -> XHttp/OkHttp or Jakarta Mail) against loopback fixtures and observes the production result
 * callback ([SendUtils.updateLogs] with logId 0, i.e. the Test-button channel) on LiveEventBus.
 *
 * logId 0 is deliberate: no Msg/Logs rows and no WorkManager jobs are created, so the fixture database is untouched.
 * Rules and senders exist only in memory (one sender, logic ALL => senderLogic() never enqueues a follow-up).
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TargetTransportIntegrationTest {

    companion object {
        const val FROM = "+31600000098"
        const val CONTENT = "Synthetic SMSF98 code 482913 — café ✓ <b>&\"q\""
        const val SIM = "SIM1_FixtureCarrier"
        const val TEMPLATE = "{{FROM}}|{{SMS}}"
        val EXPECTED_TEXT = "$FROM|$CONTENT"
        private val saved = LinkedHashMap<String, Any?>()

        @BeforeClass @JvmStatic
        fun setUp() {
            // Only the two retry preferences are changed (finite single attempt); originals are restored with commit().
            val p = SharedPreference.preference
            for (k in listOf("request_retry_times", "request_delay_time")) saved[k] = if (p.contains(k)) p.all[k] else null
            p.edit().putInt("request_retry_times", 0).putInt("request_delay_time", 1).commit()
            assertEquals(0, SettingUtils.requestRetryTimes)
            Outcomes.register()
        }

        @AfterClass @JvmStatic
        fun tearDown() {
            Outcomes.close()
            val e = SharedPreference.preference.edit()
            saved.forEach { (k, v) -> if (v == null) e.remove(k) else e.putInt(k, v as Int) }
            assertTrue(e.commit())
            Fixture.save("prefs-restored", JSONObject().put("restored", JSONObject(saved.mapValues { it.value ?: JSONObject.NULL })))
        }
    }

    @Before fun clearOutcomes() = Outcomes.clear()

    private fun msg() = MsgInfo("sms", FROM, CONTENT, Date(1794_000_000_000L), SIM, 0, 1)

    private fun rule(sender: Sender, template: String = TEMPLATE) = Rule(
        id = 0, type = "sms", filed = "transpond_all", check = "is", value = "", senderId = sender.id,
        smsTemplate = template, regexReplace = "", simSlot = "ALL", status = 1, senderList = listOf(sender),
        senderLogic = SENDER_LOGIC_ALL, title = "",
    )

    private fun sender(type: Int, setting: Any) = Sender(id = 980_000L + type, type = type, name = "SMSF98 fixture $type", jsonSetting = Gson().toJson(setting), status = 1)

    private fun assertLoopback(url: String) = assertEquals("only loopback fixtures are allowed", "127.0.0.1", URI(url.replace(Regex("//[^@/]*@"), "//")).host)

    /** Runs the production entry point and returns (first captured request, production outcomes). */
    private fun runHttp(name: String, server: HttpLocalServer, type: Int, setting: Any, template: String = TEMPLATE): Pair<HttpCapture, List<Outcome>> {
        val s = sender(type, setting)
        SendUtils.sendMsgSender(msg(), rule(s, template), 0, 0L, 0L)
        val cap = server.next()
        assertNotNull("$name: no request reached the loopback fixture within ${Fixture.RESULT_TIMEOUT_S}s", cap)
        val outcomes = Outcomes.await()
        Fixture.save(name, cap!!.toJson(server.reply).put("storedSenderJson", s.jsonSetting)
            .put("outcomes", org.json.JSONArray(outcomes.map { JSONObject().put("kind", it.kind).put("message", it.message) }))
            .put("extraRequests", server.captures.size).put("serverErrors", org.json.JSONArray(server.errors.toList())))
        assertTrue("$name: no production callback within timeout", outcomes.isNotEmpty())
        assertEquals("$name: exactly one request expected (retry=0)", 0, server.captures.size)
        return cap to outcomes
    }

    private fun assertSuccess(name: String, o: List<Outcome>) {
        assertEquals("$name outcomes $o", listOf("SUCCESS"), o.map { it.kind })
    }

    private fun assertFailure(name: String, o: List<Outcome>) {
        assertTrue("$name must not report success: $o", o.none { it.kind == "SUCCESS" })
        assertTrue("$name must report an error: $o", o.any { it.kind == "ERROR" })
    }

    private fun expectedSign(ts: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(secret.toByteArray(), "HmacSHA256")) }
        return Base64.encodeToString(mac.doFinal("$ts\n$secret".toByteArray()), Base64.NO_WRAP)
    }

    // ------------------------------------------------------------------ Webhook

    @Test fun webhook01_jsonPostBasicAuthSignedSuccess() = HttpLocalServer(HttpLocalServer.Reply(200, "OK", "{\"status\":\"accepted-98\"}")).use { srv ->
        val secret = "fixture-webhook-secret-98"
        val url = "http://fixture-user-98:fixture-pass-98@127.0.0.1:${srv.port}/hook/98?route=a"
        assertLoopback(url)
        val setting = WebhookSetting(
            method = "POST", webServer = url, secret = secret, response = "accepted-98",
            webParams = "{\"text\":\"[msg]\",\"from\":\"[from]\",\"device\":\"[device_mark]\",\"ts\":[timestamp],\"sign\":\"[sign]\"}",
            headers = mapOf("Content-Type" to "application/json", "X-Fixture" to "smsf98"),
        )
        val (c, o) = runHttp("webhook01-json-post", srv, TYPE_WEBHOOK, setting)
        assertEquals("POST", c.method)
        assertEquals("/hook/98", c.path)
        assertEquals(listOf("a"), Fixture.formDecode(c.query)["route"])
        assertTrue(c.header("Content-Type")!!.startsWith("application/json"))
        assertEquals("smsf98", c.header("X-Fixture"))
        assertEquals("Basic " + Fixture.b64("fixture-user-98:fixture-pass-98"), c.header("Authorization"))
        val j = JSONObject(c.bodyText)
        assertEquals(EXPECTED_TEXT, j.getString("text"))
        assertEquals(FROM, j.getString("from"))
        assertEquals(SettingUtils.extraDeviceMark, j.getString("device"))
        val ts = j.getLong("ts").toString()
        assertTrue(Math.abs(System.currentTimeMillis() - ts.toLong()) < 120_000)
        // JSON bodies carry the URL-encoded Base64 signature (WebhookUtils encodes once, JSON does not decode).
        assertEquals(URLEncoder.encode(expectedSign(ts, secret), "UTF-8"), j.getString("sign"))
        assertSuccess("webhook01", o)
    }

    @Test fun webhook02_defaultFormPostSuccess() = HttpLocalServer(HttpLocalServer.Reply(200, "OK", "ok", "text/plain")).use { srv ->
        val setting = WebhookSetting(method = "POST", webServer = "http://127.0.0.1:${srv.port}/form", webParams = "")
        val (c, o) = runHttp("webhook02-form-post", srv, TYPE_WEBHOOK, setting)
        assertEquals("POST", c.method)
        assertEquals("/form", c.path)
        assertTrue(c.header("Content-Type")!!.startsWith("application/x-www-form-urlencoded"))
        val f = Fixture.formDecode(c.bodyText)
        assertEquals(listOf(FROM), f["from"])
        assertEquals(listOf(EXPECTED_TEXT), f["content"])
        assertTrue(f["timestamp"]!!.single().toLong() > 0)
        assertNull("no secret => no sign", f["sign"])
        assertSuccess("webhook02", o)
    }

    @Test fun webhook03_defaultSignedGetSuccess() = HttpLocalServer(HttpLocalServer.Reply(200, "OK", "ok", "text/plain")).use { srv ->
        val secret = "fixture-get-secret-98"
        val setting = WebhookSetting(method = "GET", webServer = "http://127.0.0.1:${srv.port}/get", secret = secret, webParams = "")
        val (c, o) = runHttp("webhook03-get", srv, TYPE_WEBHOOK, setting)
        assertEquals("GET", c.method)
        assertEquals("/get", c.path)
        assertEquals(0, c.body.size)
        val q = Fixture.formDecode(c.query)
        assertEquals(listOf(FROM), q["from"])
        assertEquals(listOf(EXPECTED_TEXT), q["content"])
        val ts = q["timestamp"]!!.single()
        assertEquals(listOf(expectedSign(ts, secret)), q["sign"])
        assertSuccess("webhook03", o)
    }

    @Test fun webhook04_responseMismatchIsFailure() = HttpLocalServer(HttpLocalServer.Reply(200, "OK", "{\"status\":\"rejected\"}")).use { srv ->
        val setting = WebhookSetting(method = "POST", webServer = "http://127.0.0.1:${srv.port}/hook", response = "accepted-98", webParams = "{\"text\":\"[msg]\"}")
        val (_, o) = runHttp("webhook04-response-mismatch", srv, TYPE_WEBHOOK, setting)
        assertFailure("webhook04", o)
    }

    @Test fun webhook05_http500IsFailure() = HttpLocalServer(HttpLocalServer.Reply(500, "Internal Server Error", "{\"error\":\"fixture\"}")).use { srv ->
        val setting = WebhookSetting(method = "POST", webServer = "http://127.0.0.1:${srv.port}/hook", webParams = "{\"text\":\"[msg]\"}")
        val (_, o) = runHttp("webhook05-http500", srv, TYPE_WEBHOOK, setting)
        assertFailure("webhook05", o)
    }

    /** Fix 99: an actual empty 204 reaches the production callback and is a success when no response text is required. */
    @Test fun webhook06_http204EmptyNoCheckIsSuccess() = HttpLocalServer(HttpLocalServer.Reply(204, "No Content", "")).use { srv ->
        val setting = WebhookSetting(method = "POST", webServer = "http://127.0.0.1:${srv.port}/hook204", webParams = "{\"text\":\"[msg]\"}")
        val (c, o) = runHttp("webhook06-204-nocheck", srv, TYPE_WEBHOOK, setting)
        assertEquals("/hook204", c.path)
        assertSuccess("webhook06", o)
    }

    /** Fix 99: an empty 204 cannot contain the required response text, so it must be reported as a failure. */
    @Test fun webhook07_http204EmptyWithResponseCheckIsFailure() = HttpLocalServer(HttpLocalServer.Reply(204, "No Content", "")).use { srv ->
        val setting = WebhookSetting(method = "POST", webServer = "http://127.0.0.1:${srv.port}/hook204", response = "accepted-98", webParams = "{\"text\":\"[msg]\"}")
        val (_, o) = runHttp("webhook07-204-check", srv, TYPE_WEBHOOK, setting)
        assertFailure("webhook07", o)
    }

    // ------------------------------------------------------------------ Gotify

    @Test fun gotify01_formPostSuccess() = HttpLocalServer(HttpLocalServer.Reply(200, "OK",
        "{\"id\":98,\"appid\":1,\"message\":\"m\",\"title\":\"t\",\"priority\":5,\"date\":\"2026-11-18T00:00:00Z\"}")).use { srv ->
        val setting = GotifySetting(webServer = "http://127.0.0.1:${srv.port}/message?token=fake-gotify-token-98", title = "SMSF98 {{FROM}}", priority = "5")
        val (c, o) = runHttp("gotify01-success", srv, TYPE_GOTIFY, setting)
        assertEquals("POST", c.method)
        assertEquals("/message", c.path)
        assertEquals(listOf("fake-gotify-token-98"), Fixture.formDecode(c.query)["token"])
        assertTrue(c.header("Content-Type")!!.startsWith("application/x-www-form-urlencoded"))
        val f = Fixture.formDecode(c.bodyText)
        assertEquals(listOf("SMSF98 $FROM"), f["title"])
        assertEquals(listOf(EXPECTED_TEXT), f["message"])
        assertEquals(listOf("5"), f["priority"])
        assertSuccess("gotify01", o)
    }

    @Test fun gotify02_unauthorizedIsFailure() = HttpLocalServer(HttpLocalServer.Reply(401, "Unauthorized",
        "{\"error\":\"Unauthorized\",\"errorCode\":401,\"errorDescription\":\"you need to provide a valid access token\"}")).use { srv ->
        val setting = GotifySetting(webServer = "http://127.0.0.1:${srv.port}/message?token=wrong-98", title = "", priority = "5")
        val (_, o) = runHttp("gotify02-401", srv, TYPE_GOTIFY, setting)
        assertFailure("gotify02", o)
    }

    // ------------------------------------------------------------------ Bark

    @Test fun bark01_jsonPostSuccess() = HttpLocalServer(HttpLocalServer.Reply(200, "OK", "{\"code\":200,\"message\":\"success\",\"timestamp\":1794000000}")).use { srv ->
        val setting = BarkSetting(server = "http://127.0.0.1:${srv.port}/fake-bark-key-98", group = "smsf98", level = "active", title = "SMSF98 {{FROM}}")
        val (c, o) = runHttp("bark01-success", srv, TYPE_BARK, setting)
        assertEquals("POST", c.method)
        assertEquals("/fake-bark-key-98", c.path)
        assertTrue(c.header("Content-Type")!!.startsWith("application/json"))
        val j = JSONObject(c.bodyText)
        assertEquals("SMSF98 $FROM", j.getString("title"))
        assertEquals(EXPECTED_TEXT, j.getString("body"))
        assertEquals(1, j.getInt("isArchive"))
        assertEquals("smsf98", j.getString("group"))
        assertEquals("active", j.getString("level"))
        assertEquals("482913", j.getString("copy"))
        assertEquals(1, j.getInt("autoCopy"))
        assertSuccess("bark01", o)
    }

    @Test fun bark02_aesCbcCiphertextSuccess() = HttpLocalServer(HttpLocalServer.Reply(200, "OK", "{\"code\":200,\"message\":\"success\",\"timestamp\":1794000000}")).use { srv ->
        val key = "fixtureKey98AES!"; val iv = "fixtureIv98-0016"
        val setting = BarkSetting(server = "http://127.0.0.1:${srv.port}/fake-bark-key-98", title = "SMSF98 {{FROM}}", transformation = "AES128/CBC/PKCS7Padding", key = key, iv = iv)
        val (c, o) = runHttp("bark02-aes-cbc", srv, TYPE_BARK, setting)
        assertEquals("POST", c.method)
        assertTrue(c.header("Content-Type")!!.startsWith("application/x-www-form-urlencoded"))
        val f = Fixture.formDecode(c.bodyText)
        assertNull("configured IV must not be transmitted", f["iv"])
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding").apply { init(Cipher.DECRYPT_MODE, SecretKeySpec(key.toByteArray(), "AES"), IvParameterSpec(iv.toByteArray())) }
        val plain = String(cipher.doFinal(Base64.decode(f["ciphertext"]!!.single(), Base64.DEFAULT)), Charsets.UTF_8)
        val j = JSONObject(plain)
        assertEquals(EXPECTED_TEXT, j.getString("body"))
        assertEquals("SMSF98 $FROM", j.getString("title"))
        assertFalse("plaintext must not leak into the request", c.bodyText.contains("482913") || c.bodyText.contains("Synthetic"))
        assertSuccess("bark02", o)
    }

    @Test fun bark03_apiErrorCodeIsFailure() = HttpLocalServer(HttpLocalServer.Reply(200, "OK", "{\"code\":400,\"message\":\"failed to push to device\",\"timestamp\":1794000000}")).use { srv ->
        val setting = BarkSetting(server = "http://127.0.0.1:${srv.port}/fake-bark-key-98")
        val (_, o) = runHttp("bark03-code400", srv, TYPE_BARK, setting)
        assertFailure("bark03", o)
    }

    // ------------------------------------------------------------------ Telegram (custom API URL)

    @Test fun telegram01_customUrlPostHtmlSuccess() = HttpLocalServer(HttpLocalServer.Reply(200, "OK", "{\"ok\":true,\"result\":{\"message_id\":98}}")).use { srv ->
        val url = "http://127.0.0.1:${srv.port}/botFAKE98:fixture-token/sendMessage"
        assertLoopback(url)
        val setting = TelegramSetting(method = "POST", apiToken = url, chatId = "-1009800098", parseMode = "HTML")
        val (c, o) = runHttp("telegram01-post-html", srv, TYPE_TELEGRAM, setting)
        assertEquals("POST", c.method)
        assertEquals("/botFAKE98:fixture-token/sendMessage", c.path)
        assertTrue(c.header("Content-Type")!!.startsWith("application/json"))
        val j = JSONObject(c.bodyText)
        assertEquals("-1009800098", j.getString("chat_id"))
        assertEquals("HTML", j.getString("parse_mode"))
        assertEquals("$FROM|Synthetic SMSF98 code 482913 — café ✓ &lt;b&gt;&amp;&quot;q&quot;", j.getString("text"))
        assertEquals("true", j.getString("disable_web_page_preview"))
        assertSuccess("telegram01", o)
    }

    @Test fun telegram02_customUrlGetSuccess() = HttpLocalServer(HttpLocalServer.Reply(200, "OK", "{\"ok\":true,\"result\":{\"message_id\":99}}")).use { srv ->
        val setting = TelegramSetting(method = "GET", apiToken = "http://127.0.0.1:${srv.port}/botFAKE98:fixture-token/sendMessage", chatId = "4298", parseMode = "TEXT", messageThreadId = "7")
        val (c, o) = runHttp("telegram02-get", srv, TYPE_TELEGRAM, setting)
        assertEquals("GET", c.method)
        val q = Fixture.formDecode(c.query)
        assertEquals(listOf("4298"), q["chat_id"])
        assertEquals(listOf(EXPECTED_TEXT), q["text"])
        assertNull("TEXT mode sends no parse_mode", q["parse_mode"])
        assertEquals(listOf("7"), q["message_thread_id"])
        assertSuccess("telegram02", o)
    }

    @Test fun telegram03_badRequestIsFailure() = HttpLocalServer(HttpLocalServer.Reply(400, "Bad Request", "{\"ok\":false,\"error_code\":400,\"description\":\"Bad Request: chat not found\"}")).use { srv ->
        val setting = TelegramSetting(method = "POST", apiToken = "http://127.0.0.1:${srv.port}/botFAKE98:fixture-token/sendMessage", chatId = "0")
        val (_, o) = runHttp("telegram03-400", srv, TYPE_TELEGRAM, setting)
        assertFailure("telegram03", o)
    }

    @Test fun telegram04_okFalseWith200IsFailure() = HttpLocalServer(HttpLocalServer.Reply(200, "OK", "{\"ok\":false,\"description\":\"fixture refusal\"}")).use { srv ->
        val setting = TelegramSetting(method = "POST", apiToken = "http://127.0.0.1:${srv.port}/botFAKE98:fixture-token/sendMessage", chatId = "0")
        val (_, o) = runHttp("telegram04-okfalse", srv, TYPE_TELEGRAM, setting)
        assertFailure("telegram04", o)
    }

    // ------------------------------------------------------------------ Email (SMTP)

    private val recipients = listOf("rcpt-a-98@example.invalid", "rcpt-b-98@example.invalid")

    private fun emailSetting(port: Int, startTls: Boolean) = EmailSetting(
        mailType = "other", fromEmail = "sender-98@example.invalid", pwd = "fake-smtp-password-98", nickname = "SMSF98 Fixture",
        host = "127.0.0.1", port = port.toString(), ssl = false, startTls = startTls, title = "SMSF98 {{FROM}}",
        recipients = recipients.associateWith { Pair("", "") }.toMutableMap(), encryptionProtocol = "Plain",
    )

    /** EmailUtils.sendMsg blocks (runBlocking); run the production entry point on a worker thread with a hard bound. */
    private fun runEmail(name: String, srv: SmtpLocalServer, setting: EmailSetting): List<Outcome> {
        val s = sender(TYPE_EMAIL, setting)
        var error: Throwable? = null
        val t = Thread { try { SendUtils.sendMsgSender(msg(), rule(s, "{{FROM}}\n{{SMS}}"), 0, 0L, 0L) } catch (e: Throwable) { error = e } }
        t.start(); t.join(Fixture.RESULT_TIMEOUT_S * 1000)
        val finished = !t.isAlive
        if (!finished) t.interrupt()
        val outcomes = Outcomes.await(5)
        srv.awaitSessionEnd(5)
        Fixture.save(name, srv.toJson().put("storedSenderJsonRedacted", s.jsonSetting.replace("fake-smtp-password-98", "<fixture-password>"))
            .put("entryPointFinished", finished).put("entryPointException", error?.javaClass?.name ?: JSONObject.NULL)
            .put("outcomes", org.json.JSONArray(outcomes.map { JSONObject().put("kind", it.kind).put("message", it.message) })))
        assertTrue("$name: EmailUtils.sendMsg did not finish within ${Fixture.RESULT_TIMEOUT_S}s", finished)
        assertNull("$name: entry point threw", error)
        return outcomes
    }

    @Test fun email01_plainSmtpDeliversEnvelopeAndBody() = SmtpLocalServer().use { srv ->
        val o = runEmail("email01-plain-success", srv, emailSetting(srv.port, startTls = false))
        assertSuccess("email01", o)
        assertEquals("sender-98@example.invalid", srv.authUser)
        assertEquals("fake-smtp-password-98", srv.authPassword)
        assertEquals("sender-98@example.invalid", srv.mailFrom)
        assertEquals(recipients.sorted(), srv.recipients.sorted())
        assertTrue("only reserved .invalid recipients", srv.recipients.all { it.endsWith("@example.invalid") })
        val m = MimeMessage(Session.getInstance(Properties()), ByteArrayInputStream(srv.data!!))
        assertEquals("SMSF98 $FROM", m.subject)
        assertEquals(recipients.sorted(), m.getRecipients(jakarta.mail.Message.RecipientType.TO).map { it.toString() }.sorted())
        assertTrue(m.from.single().toString().contains("sender-98@example.invalid"))
        val part = (m.content as MimeMultipart).getBodyPart(0)
        assertTrue(part.contentType, part.contentType.lowercase().contains("text/html") && part.contentType.lowercase().contains("utf-8"))
        assertEquals("$FROM<br>$CONTENT", part.content as String)
        assertTrue(srv.commands.containsAll(listOf("EHLO", "AUTH", "MAIL", "RCPT", "DATA", "QUIT")))
    }

    @Test fun email02_startTlsRefusedFailsBeforeCredentialsAndBody() = SmtpLocalServer(advertiseStartTls = true).use { srv ->
        val o = runEmail("email02-starttls-refused", srv, emailSetting(srv.port, startTls = true))
        assertFailure("email02", o)
        assertTrue(srv.commands.contains("STARTTLS"))
        assertFalse("no AUTH after failed upgrade: ${srv.commands}", srv.commands.contains("AUTH"))
        assertNull(srv.authPassword)
        assertFalse(srv.commands.any { it in listOf("MAIL", "RCPT", "DATA") })
        assertNull(srv.data)
    }

    @Test fun email03_startTlsNotOfferedFailsBeforeCredentialsAndBody() = SmtpLocalServer(advertiseStartTls = false).use { srv ->
        val o = runEmail("email03-starttls-not-offered", srv, emailSetting(srv.port, startTls = true))
        assertFailure("email03", o)
        assertFalse(srv.commands.contains("STARTTLS"))
        assertFalse("no AUTH without TLS: ${srv.commands}", srv.commands.contains("AUTH"))
        assertNull(srv.authPassword)
        assertFalse(srv.commands.any { it in listOf("MAIL", "RCPT", "DATA") })
        assertNull(srv.data)
    }

    @Test fun email04_dataRejectedIsFailure() = SmtpLocalServer(dataReply = "554 5.7.1 Fixture rejects message").use { srv ->
        val o = runEmail("email04-data-554", srv, emailSetting(srv.port, startTls = false))
        assertFailure("email04", o)
        assertTrue(srv.commands.contains("DATA"))
    }
}
