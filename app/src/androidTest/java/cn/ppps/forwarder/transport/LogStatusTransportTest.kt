package cn.ppps.forwarder.transport

import androidx.test.platform.app.InstrumentationRegistry
import cn.ppps.forwarder.database.AppDatabase
import cn.ppps.forwarder.database.entity.Rule
import cn.ppps.forwarder.database.entity.Sender
import cn.ppps.forwarder.entity.MsgInfo
import cn.ppps.forwarder.entity.setting.WebhookSetting
import cn.ppps.forwarder.utils.SENDER_LOGIC_ALL
import cn.ppps.forwarder.utils.SendUtils
import cn.ppps.forwarder.utils.SharedPreference
import cn.ppps.forwarder.utils.TYPE_WEBHOOK
import com.google.gson.Gson
import org.json.JSONArray
import org.json.JSONObject
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.util.Date

/**
 * The real forwarding path (logId > 0): the result is written to the Logs row by UpdateLogsWorker. Temporary
 * Sender/Rule/Msg/Logs rows with ids 98020x are created and removed afterwards, and sqlite_sequence is restored
 * exactly, so the fixture database returns to its previous logical content.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class LogStatusTransportTest {
    companion object {
        const val SENDER_ID = 980201L
        const val RULE_ID = 980202L
        const val MSG_ID = 980203L
        private val savedPrefs = LinkedHashMap<String, Any?>()
        private val savedSeq = mutableListOf<Pair<String, Long>>()
        private val db get() = AppDatabase.getInstance(InstrumentationRegistry.getInstrumentation().targetContext).openHelper.writableDatabase

        @BeforeClass @JvmStatic
        fun setUp() {
            val p = SharedPreference.preference
            for (k in listOf("request_retry_times", "request_delay_time")) savedPrefs[k] = if (p.contains(k)) p.all[k] else null
            p.edit().putInt("request_retry_times", 0).putInt("request_delay_time", 1).commit()
            db.query("SELECT name, seq FROM sqlite_sequence").use { c -> while (c.moveToNext()) savedSeq += c.getString(0) to c.getLong(1) }
            for (t in listOf("Logs", "Msg", "Rule", "Sender")) db.query("SELECT count(*) FROM `$t` WHERE id BETWEEN 980200 AND 980299").use { c -> c.moveToFirst(); assertEquals("pre-existing fixture ids in $t", 0, c.getInt(0)) }
            val now = System.currentTimeMillis()
            db.beginTransaction()
            try {
                db.execSQL("INSERT INTO Sender (id,type,name,json_setting,status,time) VALUES (?,?,?,?,?,?)", arrayOf<Any>(SENDER_ID, TYPE_WEBHOOK, "SMSF98 temp", "{}", 1, now))
                db.execSQL("INSERT INTO Rule (id,type,filed,`check`,value,sender_id,sms_template,regex_replace,sim_slot,status,time,sender_list,sender_logic,silent_period_start,silent_period_end,silent_day_of_week,title) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    arrayOf<Any>(RULE_ID, "sms", "transpond_all", "is", "", SENDER_ID, "{{SMS}}", "", "ALL", 0, now, SENDER_ID.toString(), "ALL", 0, 0, "", ""))
                db.execSQL("INSERT INTO Msg (id,type,`from`,content,sim_slot,sim_info,sub_id,call_type,time) VALUES (?,?,?,?,?,?,?,?,?)",
                    arrayOf<Any>(MSG_ID, "sms", "+31600000098", "Synthetic SMSF98 log-status probe", 0, "SIM1_FixtureCarrier", 1, 0, now))
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        }

        @AfterClass @JvmStatic
        fun tearDown() {
            Thread.sleep(2000) // let any delayed UpdateLogsWorker finish before rows disappear
            db.beginTransaction()
            try {
                db.execSQL("DELETE FROM Logs WHERE msg_id=$MSG_ID OR rule_id=$RULE_ID OR sender_id=$SENDER_ID")
                db.execSQL("DELETE FROM Msg WHERE id=$MSG_ID")
                db.execSQL("DELETE FROM Rule WHERE id=$RULE_ID")
                db.execSQL("DELETE FROM Sender WHERE id=$SENDER_ID")
                db.execSQL("DELETE FROM sqlite_sequence")
                savedSeq.forEach { (n, s) -> db.execSQL("INSERT INTO sqlite_sequence (name, seq) VALUES (?, ?)", arrayOf<Any>(n, s)) }
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
            val after = mutableListOf<Pair<String, Long>>()
            db.query("SELECT name, seq FROM sqlite_sequence").use { c -> while (c.moveToNext()) after += c.getString(0) to c.getLong(1) }
            val e = SharedPreference.preference.edit()
            savedPrefs.forEach { (k, v) -> if (v == null) e.remove(k) else e.putInt(k, v as Int) }
            assertTrue(e.commit())
            Fixture.save("logstatus-cleanup", JSONObject().put("sqliteSequenceBefore", JSONArray(savedSeq.map { "${it.first}=${it.second}" }))
                .put("sqliteSequenceAfter", JSONArray(after.map { "${it.first}=${it.second}" })))
            assertEquals(savedSeq.toSet(), after.toSet())
        }
    }

    private var lastFirstSettled = -1
    private var lastFinal = -1

    private fun newLog(): Long {
        // insert() returns the row id on the same connection (a separate last_insert_rowid() query may use another pooled connection).
        val v = android.content.ContentValues().apply {
            put("type", "sms"); put("msg_id", MSG_ID); put("rule_id", RULE_ID); put("sender_id", SENDER_ID)
            put("forward_status", 1); put("forward_response", ""); put("time", System.currentTimeMillis())
        }
        val id = db.insert("Logs", android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT, v)
        assertTrue("Logs insert failed", id > 0)
        return id
    }

    private fun row(id: Long): Pair<Int, String> = db.query("SELECT forward_status, forward_response FROM Logs WHERE id=$id").use {
        assertTrue("Logs row $id missing", it.moveToFirst()); it.getInt(0) to it.getString(1)
    }

    /** Sends through the production entry point with a real Logs id and returns the final row state. */
    private fun send(name: String, reply: HttpLocalServer.Reply, response: String): Pair<Int, String> = HttpLocalServer(reply).use { srv ->
        val logId = newLog()
        val setting = WebhookSetting(method = "POST", webServer = "http://127.0.0.1:${srv.port}/log", response = response, webParams = "{\"t\":\"[msg]\"}")
        val sender = Sender(SENDER_ID, TYPE_WEBHOOK, "SMSF98 temp", Gson().toJson(setting), 1)
        val rule = Rule(id = RULE_ID, type = "sms", filed = "transpond_all", check = "is", value = "", senderId = SENDER_ID, smsTemplate = "{{SMS}}",
            simSlot = "ALL", status = 1, senderList = listOf(sender), senderLogic = SENDER_LOGIC_ALL)
        SendUtils.sendMsgSender(MsgInfo("sms", "+31600000098", "Synthetic SMSF98 log-status probe", Date(), "SIM1_FixtureCarrier", 0, 1), rule, 0, logId, MSG_ID)
        assertNotNull("$name: request did not reach fixture", srv.next())
        // Final state: wait until the row has left 'processing' (1), then a fixed settle period for delayed workers.
        val deadline = System.currentTimeMillis() + Fixture.RESULT_TIMEOUT_S * 1000
        while (row(logId).first == 1 && System.currentTimeMillis() < deadline) Thread.sleep(200)
        val first = row(logId)
        Thread.sleep(4000)
        val final = row(logId)
        lastFirstSettled = first.first; lastFinal = final.first
        Fixture.save(name, JSONObject().put("reply", JSONObject().put("code", reply.code).put("body", reply.body)).put("responseMustContain", response)
            .put("firstSettledStatus", first.first).put("finalStatus", final.first).put("finalResponseText", final.second))
        final
    }

    @Test fun log01_http200MatchingResponseIsSuccess() {
        val (status, _) = send("logstatus01-200-match", HttpLocalServer.Reply(200, "OK", "{\"status\":\"accepted-98\"}"), "accepted-98")
        assertEquals(2, status)
    }

    @Test fun log02_http200MismatchIsFailure() {
        val (status, _) = send("logstatus02-200-mismatch", HttpLocalServer.Reply(200, "OK", "{\"status\":\"rejected\"}"), "accepted-98")
        assertEquals(0, status)
    }

    /** Must pass after fix 99: NoContentInterceptor no longer schedules a delayed status-2 update that overrides the check. */
    @Test fun log03_http202MismatchMustStayFailure() {
        val (status, text) = send("logstatus03-202-mismatch", HttpLocalServer.Reply(202, "Accepted", "{\"status\":\"rejected\"}"), "accepted-98")
        assertEquals("202 with a non-matching body was finally logged as status $status: $text", 0, status)
        assertNoCompetingWriter(text)
    }

    @Test fun log04_http500IsFailure() {
        val (status, _) = send("logstatus04-500", HttpLocalServer.Reply(500, "Internal Server Error", "{}"), "")
        assertEquals(0, status)
    }

    /** Control for the interceptor's intended purpose: an empty 204 without a response check is a success. */
    @Test fun log05_http204EmptyNoCheckIsSuccess() {
        val (status, text) = send("logstatus05-204-nocheck", HttpLocalServer.Reply(204, "No Content", ""), "")
        assertEquals(2, status)
        // The success must come from the request callback itself, not from a separate status writer.
        assertNoCompetingWriter(text)
        assertEquals("callback must settle directly on success", 2, lastFirstSettled)
    }

    /** Fix 99 regression: an empty 204 cannot contain the required text, so it is a failure. */
    @Test fun log06_http204EmptyWithResponseCheckIsFailure() {
        val (status, text) = send("logstatus06-204-check", HttpLocalServer.Reply(204, "No Content", ""), "accepted-98")
        assertEquals("204 without the required text was finally logged as status $status: $text", 0, status)
        assertNoCompetingWriter(text)
    }

    /** Fix 99 regression: other 2xx codes still succeed through the callback once the interceptor no longer forces it. */
    @Test fun log07_http201EmptyNoCheckIsSuccess() {
        val (status, text) = send("logstatus07-201-empty-nocheck", HttpLocalServer.Reply(201, "Created", ""), "")
        assertEquals(2, status)
        assertNoCompetingWriter(text)
    }

    private fun assertNoCompetingWriter(text: String) {
        assertFalse("a second status writer appended to the log: $text", text.contains("HTTP Status"))
        assertEquals("first settled and final status differ (competing writer)", lastFirstSettled, lastFinal)
    }
}
