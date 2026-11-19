package cn.ppps.forwarder.transport

import cn.ppps.forwarder.database.entity.Rule
import cn.ppps.forwarder.database.entity.Sender
import cn.ppps.forwarder.entity.MsgInfo
import cn.ppps.forwarder.entity.setting.EmailSetting
import cn.ppps.forwarder.entity.setting.WebhookSetting
import cn.ppps.forwarder.utils.SENDER_LOGIC_ALL
import cn.ppps.forwarder.utils.SendUtils
import cn.ppps.forwarder.utils.SettingUtils
import cn.ppps.forwarder.utils.SharedPreference
import cn.ppps.forwarder.utils.TYPE_EMAIL
import cn.ppps.forwarder.utils.TYPE_WEBHOOK
import cn.ppps.forwarder.utils.mail.EmailSender
import com.google.gson.Gson
import org.json.JSONArray
import org.json.JSONObject
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.Date
import kotlin.concurrent.thread

/**
 * Bounded-timeout behaviour of the production entry point against loopback servers that accept a connection but
 * never answer. Each test has its own hard bound; the fixture sockets are closed afterwards to release any thread
 * still blocked inside the production code.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TransportTimeoutTest {
    companion object {
        private val saved = LinkedHashMap<String, Any?>()

        @BeforeClass @JvmStatic
        fun setUp() {
            val p = SharedPreference.preference
            for (k in listOf("request_retry_times", "request_delay_time")) saved[k] = if (p.contains(k)) p.all[k] else null
            p.edit().putInt("request_retry_times", 0).putInt("request_delay_time", 1).commit()
            Outcomes.register()
        }

        @AfterClass @JvmStatic
        fun tearDown() {
            Outcomes.close()
            val e = SharedPreference.preference.edit()
            saved.forEach { (k, v) -> if (v == null) e.remove(k) else e.putInt(k, v as Int) }
            assertTrue(e.commit())
        }
    }

    @Before fun clearOutcomes() = Outcomes.clear()

    /** Accepts and holds connections without ever writing a byte. */
    private class SilentServer : AutoCloseable {
        val server = ServerSocket(0, 50, Fixture.LOOPBACK)
        val held: MutableList<Socket> = Collections.synchronizedList(mutableListOf())
        init { thread(isDaemon = true) { while (!server.isClosed) held += try { server.accept() } catch (_: Exception) { break } } }
        override fun close() { held.toList().forEach { try { it.close() } catch (_: Exception) {} }; server.close() }
    }

    private fun msg() = MsgInfo("sms", "+31600000098", "Synthetic SMSF98 timeout probe", Date(), "SIM1_FixtureCarrier", 0, 1)
    private fun rule(sender: Sender) = Rule(id = 0, type = "sms", filed = "transpond_all", check = "is", value = "", senderId = sender.id,
        smsTemplate = "{{SMS}}", simSlot = "ALL", status = 1, senderList = listOf(sender), senderLogic = SENDER_LOGIC_ALL)

    @Test fun http01_unresponsiveWebhookFailsWithinConfiguredTimeout() = SilentServer().use { srv ->
        val timeoutS = SettingUtils.requestTimeout.toLong()
        val s = Sender(980_103L, TYPE_WEBHOOK, "SMSF98 timeout", Gson().toJson(WebhookSetting(method = "POST", webServer = "http://127.0.0.1:${srv.server.localPort}/silent", webParams = "{\"t\":\"[msg]\"}")), 1)
        val start = System.nanoTime()
        SendUtils.sendMsgSender(msg(), rule(s), 0, 0L, 0L)
        val o = Outcomes.await(timeoutS + 20)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        Fixture.save("timeout-http01", JSONObject().put("requestTimeoutSetting", timeoutS).put("elapsedMs", elapsedMs).put("connections", srv.held.size)
            .put("outcomes", JSONArray(o.map { JSONObject().put("kind", it.kind).put("message", it.message) })))
        assertEquals(listOf("ERROR"), o.map { it.kind })
        assertTrue("elapsed $elapsedMs ms", elapsedMs < (timeoutS + 20) * 1000)
    }

    /** Fix 99 (replaces the candidate-98 characterization that asserted the keys were absent). */
    @Test fun smtp01_propertiesDeclareConfiguredSocketTimeouts() {
        // Same expression the production callers in EmailUtils use, with the device's actual configured timeout.
        val expected = (SettingUtils.requestTimeout.toLong() * 1000).toString()
        val p = EmailSender.smtpProperties("127.0.0.1", "25", openSSL = false, startTls = true, timeoutMs = EmailSender.smtpTimeoutMillis(SettingUtils.requestTimeout))
        val keys = listOf("mail.smtp.connectiontimeout", "mail.smtp.timeout", "mail.smtp.writetimeout")
        Fixture.save("timeout-smtp01-properties", JSONObject().apply { put("requestTimeoutSetting", SettingUtils.requestTimeout); keys.forEach { put(it, p[it] ?: JSONObject.NULL) } })
        keys.forEach { assertEquals(it, expected, p[it]) }
        // STARTTLS stays fail-closed with the timeouts added.
        assertEquals("true", p["mail.smtp.starttls.required"])
        assertEquals("true", p["mail.smtp.ssl.checkserveridentity"])
    }

    /** Failed on candidate 98 (no SMTP socket timeout: the production send blocked indefinitely); must pass after fix 99. */
    @Test fun smtp02_unresponsiveSmtpServerIsBounded() = SilentServer().use { srv ->
        val bound = SettingUtils.requestTimeout.toLong() + 25
        val setting = EmailSetting(mailType = "other", fromEmail = "sender-98@example.invalid", pwd = "fake-smtp-password-98", host = "127.0.0.1",
            port = srv.server.localPort.toString(), title = "SMSF98", recipients = mutableMapOf("rcpt-a-98@example.invalid" to Pair("", "")), encryptionProtocol = "Plain")
        val s = Sender(980_101L, TYPE_EMAIL, "SMSF98 timeout", Gson().toJson(setting), 1)
        val start = System.nanoTime()
        val t = Thread { try { SendUtils.sendMsgSender(msg(), rule(s), 0, 0L, 0L) } catch (_: Throwable) {} }
        t.start(); t.join(bound * 1000)
        val finished = !t.isAlive
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        val early = Outcomes.await(1, 200)
        srv.close() // release the blocked production thread (EOF on its socket)
        t.join(15_000)
        val late = Outcomes.await(5)
        Fixture.save("timeout-smtp02", JSONObject().put("boundS", bound).put("finishedWithinBound", finished).put("elapsedMs", elapsedMs)
            .put("threadEndedAfterFixtureClose", !t.isAlive).put("connections", srv.held.size)
            .put("outcomesWithinBound", JSONArray(early.map { JSONObject().put("kind", it.kind).put("message", it.message) }))
            .put("outcomesAfterFixtureClose", JSONArray(late.map { JSONObject().put("kind", it.kind).put("message", it.message) })))
        assertTrue("SMTP send still blocked after ${bound}s against a server that never greets (no mail.smtp.*timeout)", finished)
        // The production callback reports the failure within the bound, after roughly the configured read timeout.
        assertEquals("outcomes within bound $early", listOf("ERROR"), early.map { it.kind })
        assertTrue("ended after $elapsedMs ms, before the configured ${SettingUtils.requestTimeout}s read timeout", elapsedMs >= SettingUtils.requestTimeout * 1000L - 1000)
    }
}
