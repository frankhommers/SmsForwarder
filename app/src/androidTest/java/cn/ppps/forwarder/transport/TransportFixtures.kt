package cn.ppps.forwarder.transport

import android.util.Base64
import androidx.lifecycle.Observer
import androidx.test.platform.app.InstrumentationRegistry
import cn.ppps.forwarder.utils.EVENT_TOAST_ERROR
import cn.ppps.forwarder.utils.EVENT_TOAST_INFO
import cn.ppps.forwarder.utils.EVENT_TOAST_SUCCESS
import cn.ppps.forwarder.utils.EVENT_TOAST_WARNING
import com.jeremyliao.liveeventbus.LiveEventBus
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.Collections
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Loopback-only fixtures for transport integration tests. Every listener binds 127.0.0.1 with an ephemeral port;
 * nothing here opens an outbound connection. All credentials, tokens, addresses and texts are synthetic.
 */
object Fixture {
    val LOOPBACK: InetAddress = InetAddress.getByName("127.0.0.1")
    const val IO_TIMEOUT_MS = 10_000
    const val RESULT_TIMEOUT_S = 25L

    val captureDir: File by lazy {
        File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "transport98/captures").apply { mkdirs() }
    }

    fun save(name: String, json: JSONObject) {
        File(captureDir, "$name.json").writeText(json.toString(2))
    }

    fun formDecode(body: String): Map<String, List<String>> {
        val out = LinkedHashMap<String, MutableList<String>>()
        if (body.isEmpty()) return out
        body.split("&").forEach {
            val i = it.indexOf('=')
            val k = URLDecoder.decode(if (i < 0) it else it.substring(0, i), "UTF-8")
            val v = if (i < 0) "" else URLDecoder.decode(it.substring(i + 1), "UTF-8")
            out.getOrPut(k) { mutableListOf() }.add(v)
        }
        return out
    }

    fun b64(s: String): String = Base64.encodeToString(s.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
}

/** Production callback outcome, observed on the same LiveEventBus channels the Test button uses (logId 0). */
data class Outcome(val kind: String, val message: String)

object Outcomes : Closeable {
    private val queue = LinkedBlockingQueue<Outcome>()
    private val observers = mutableListOf<Pair<String, Observer<String>>>()

    fun register() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            for ((key, kind) in listOf(EVENT_TOAST_SUCCESS to "SUCCESS", EVENT_TOAST_ERROR to "ERROR", EVENT_TOAST_WARNING to "WARNING", EVENT_TOAST_INFO to "INFO")) {
                val o = Observer<String> { queue.add(Outcome(kind, it ?: "")) }
                LiveEventBus.get(key, String::class.java).observeForever(o)
                observers += key to o
            }
        }
    }

    override fun close() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            observers.forEach { (key, o) -> LiveEventBus.get(key, String::class.java).removeObserver(o) }
            observers.clear()
        }
    }

    fun clear() = queue.clear()

    /** First outcome within the bounded timeout plus any further outcome within a short quiet period. */
    fun await(timeoutS: Long = Fixture.RESULT_TIMEOUT_S, quietMs: Long = 1500): List<Outcome> {
        val first = queue.poll(timeoutS, TimeUnit.SECONDS) ?: return emptyList()
        val all = mutableListOf(first)
        while (true) all += queue.poll(quietMs, TimeUnit.MILLISECONDS) ?: break
        return all
    }
}

// ------------------------------------------------------------------------------------------------------------------
// HTTP
// ------------------------------------------------------------------------------------------------------------------

class HttpCapture(
    val method: String,
    val target: String,
    val version: String,
    val headers: List<Pair<String, String>>,
    val body: ByteArray,
) {
    fun header(name: String): String? = headers.firstOrNull { it.first.equals(name, true) }?.second
    val path: String get() = target.substringBefore('?')
    val query: String get() = target.substringAfter('?', "")
    val bodyText: String get() = String(body, Charsets.UTF_8)

    fun toJson(response: HttpLocalServer.Reply?): JSONObject = JSONObject().apply {
        put("method", method); put("target", target); put("version", version)
        put("headers", JSONArray().apply { headers.forEach { put(JSONArray().put(it.first).put(it.second)) } })
        put("bodyUtf8", bodyText)
        put("bodyBase64", Base64.encodeToString(body, Base64.NO_WRAP))
        put("bodyLength", body.size)
        response?.let { put("reply", JSONObject().put("code", it.code).put("reason", it.reason).put("body", it.body)) }
    }
}

class HttpLocalServer(var reply: Reply) : Closeable {
    data class Reply(val code: Int, val reason: String, val body: String, val contentType: String = "application/json; charset=utf-8")

    private val server = ServerSocket(0, 50, Fixture.LOOPBACK)
    val port: Int get() = server.localPort
    val base: String get() = "http://127.0.0.1:$port"
    val captures = LinkedBlockingQueue<HttpCapture>()
    val errors: MutableList<String> = Collections.synchronizedList(mutableListOf())

    init {
        thread(isDaemon = true, name = "http-fixture-$port") {
            while (!server.isClosed) {
                val s = try { server.accept() } catch (_: Exception) { break }
                thread(isDaemon = true) { handle(s) }
            }
        }
    }

    private fun handle(s: Socket) = s.use {
        try {
            s.soTimeout = Fixture.IO_TIMEOUT_MS
            val input = s.getInputStream()
            val head = readHead(input) ?: return
            val lines = head.split("\r\n")
            val (method, target, version) = lines[0].split(" ", limit = 3).let { Triple(it[0], it[1], it.getOrElse(2) { "" }) }
            val headers = lines.drop(1).filter { it.isNotEmpty() }.map { it.substringBefore(':').trim() to it.substringAfter(':').trim() }
            val len = headers.firstOrNull { it.first.equals("Content-Length", true) }?.second?.toIntOrNull()
            val chunked = headers.any { it.first.equals("Transfer-Encoding", true) && it.second.contains("chunked", true) }
            val body = when {
                len != null -> readExactly(input, len)
                chunked -> readChunked(input)
                else -> ByteArray(0)
            }
            captures.add(HttpCapture(method, target, version, headers, body))
            val r = reply
            val payload = r.body.toByteArray(Charsets.UTF_8)
            val out = s.getOutputStream()
            out.write(("HTTP/1.1 ${r.code} ${r.reason}\r\nContent-Type: ${r.contentType}\r\nContent-Length: ${payload.size}\r\nConnection: close\r\n\r\n").toByteArray(Charsets.ISO_8859_1))
            out.write(payload)
            out.flush()
        } catch (e: Exception) {
            errors += e.javaClass.simpleName
        }
    }

    private fun readHead(input: InputStream): String? {
        val buf = ByteArrayOutputStream()
        var state = 0
        while (true) {
            val b = input.read()
            if (b < 0) return null
            buf.write(b)
            state = when {
                state == 0 && b == '\r'.code -> 1
                state == 1 && b == '\n'.code -> 2
                state == 2 && b == '\r'.code -> 3
                state == 3 && b == '\n'.code -> return String(buf.toByteArray(), Charsets.ISO_8859_1).removeSuffix("\r\n\r\n")
                b == '\r'.code -> 1
                else -> 0
            }
            if (buf.size() > 65536) return null
        }
    }

    private fun readExactly(input: InputStream, n: Int): ByteArray {
        val out = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = input.read(out, off, n - off)
            if (r < 0) break
            off += r
        }
        return out.copyOf(off)
    }

    private fun readLine(input: InputStream): String {
        val b = ByteArrayOutputStream()
        while (true) {
            val c = input.read()
            if (c < 0 || c == '\n'.code) break
            if (c != '\r'.code) b.write(c)
        }
        return String(b.toByteArray(), Charsets.ISO_8859_1)
    }

    private fun readChunked(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        while (true) {
            val size = readLine(input).substringBefore(';').trim().toInt(16)
            if (size == 0) { readLine(input); break }
            out.write(readExactly(input, size)); readLine(input)
        }
        return out.toByteArray()
    }

    fun next(timeoutS: Long = Fixture.RESULT_TIMEOUT_S): HttpCapture? = captures.poll(timeoutS, TimeUnit.SECONDS)

    override fun close() = server.close()
}

// ------------------------------------------------------------------------------------------------------------------
// SMTP
// ------------------------------------------------------------------------------------------------------------------

/**
 * Minimal SMTP server: EHLO/HELO, optional STARTTLS advertisement (always refused, there is no TLS here),
 * AUTH LOGIN/PLAIN, MAIL, RCPT, DATA, RSET, NOOP, QUIT. It never relays anything.
 */
class SmtpLocalServer(
    private val advertiseStartTls: Boolean = false,
    private val startTlsReply: String = "454 4.7.0 TLS not available on this fixture",
    private val dataReply: String = "250 2.0.0 Ok: queued as SMSF98",
    /** Accept the TCP connection but never send the 220 greeting (unresponsive server). */
    private val silent: Boolean = false,
) : Closeable {
    private val held: MutableList<Socket> = Collections.synchronizedList(mutableListOf())
    private val server = ServerSocket(0, 50, Fixture.LOOPBACK)
    val port: Int get() = server.localPort
    val transcript: MutableList<String> = Collections.synchronizedList(mutableListOf())
    val commands: MutableList<String> = Collections.synchronizedList(mutableListOf())
    @Volatile var authUser: String? = null
    @Volatile var authPassword: String? = null
    @Volatile var mailFrom: String? = null
    val recipients: MutableList<String> = Collections.synchronizedList(mutableListOf())
    @Volatile var data: ByteArray? = null
    val sessionsClosed = LinkedBlockingQueue<Int>()
    private var sessionCount = 0

    init {
        thread(isDaemon = true, name = "smtp-fixture-$port") {
            while (!server.isClosed) {
                val s = try { server.accept() } catch (_: Exception) { break }
                val id = ++sessionCount
                thread(isDaemon = true) { try { handle(s) } catch (_: Exception) { transcript += "!session $id ended by exception" } finally { sessionsClosed.add(id) } }
            }
        }
    }

    private fun line(input: InputStream): String? {
        val b = ByteArrayOutputStream()
        while (true) {
            val c = input.read()
            if (c < 0) return if (b.size() == 0) null else String(b.toByteArray(), Charsets.ISO_8859_1)
            if (c == '\n'.code) break
            b.write(c)
        }
        return String(b.toByteArray(), Charsets.ISO_8859_1).removeSuffix("\r")
    }

    private fun send(out: OutputStream, text: String) {
        transcript += "S: $text"
        out.write((text + "\r\n").toByteArray(Charsets.ISO_8859_1)); out.flush()
    }

    private fun decode(b64: String) = String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)

    private fun handle(s: Socket) = s.use {
        if (silent) {
            held += s
            transcript += "S: [silent: connection accepted, no greeting]"
            try { s.getInputStream().read() } catch (_: Exception) {}
            return
        }
        s.soTimeout = Fixture.IO_TIMEOUT_MS
        val input = s.getInputStream().buffered()
        val out = s.getOutputStream()
        send(out, "220 127.0.0.1 ESMTP SMSF98 loopback fixture")
        while (true) {
            val raw = line(input) ?: return
            val verb = raw.substringBefore(' ').uppercase()
            commands += verb
            transcript += "C: " + if (verb == "AUTH") raw.substringBefore(' ') + " " + raw.substringAfter(' ').substringBefore(' ') + " [credentials captured separately]" else raw
            when (verb) {
                "EHLO" -> {
                    send(out, "250-127.0.0.1")
                    if (advertiseStartTls) send(out, "250-STARTTLS")
                    send(out, "250-AUTH PLAIN LOGIN")
                    send(out, "250-8BITMIME")
                    send(out, "250 SIZE 1048576")
                }
                "HELO" -> send(out, "250 127.0.0.1")
                "STARTTLS" -> send(out, startTlsReply)
                "AUTH" -> {
                    val parts = raw.split(" ")
                    when (parts.getOrNull(1)?.uppercase()) {
                        "LOGIN" -> {
                            authUser = if (parts.size > 2) decode(parts[2]) else { send(out, "334 VXNlcm5hbWU6"); decode(line(input) ?: return) }
                            send(out, "334 UGFzc3dvcmQ6"); authPassword = decode(line(input) ?: return)
                            transcript += "C: [LOGIN credentials]"
                            send(out, "235 2.7.0 Authentication successful")
                        }
                        "PLAIN" -> {
                            val token = if (parts.size > 2) parts[2] else { send(out, "334 "); line(input) ?: return }
                            val f = decode(token).split('\u0000')
                            authUser = f.getOrNull(1); authPassword = f.getOrNull(2)
                            send(out, "235 2.7.0 Authentication successful")
                        }
                        else -> send(out, "504 5.5.4 Unsupported mechanism")
                    }
                }
                "MAIL" -> { mailFrom = raw.substringAfter('<').substringBefore('>'); send(out, "250 2.1.0 Ok") }
                "RCPT" -> { recipients += raw.substringAfter('<').substringBefore('>'); send(out, "250 2.1.5 Ok") }
                "DATA" -> {
                    send(out, "354 End data with <CR><LF>.<CR><LF>")
                    val body = ByteArrayOutputStream()
                    while (true) {
                        val l = line(input) ?: return
                        if (l == ".") break
                        body.write((if (l.startsWith("..")) l.substring(1) else l).toByteArray(Charsets.ISO_8859_1))
                        body.write("\r\n".toByteArray())
                    }
                    data = body.toByteArray()
                    transcript += "C: [DATA ${body.size()} bytes]"
                    send(out, dataReply)
                }
                "RSET", "NOOP" -> send(out, "250 2.0.0 Ok")
                "QUIT" -> { send(out, "221 2.0.0 Bye"); return }
                else -> send(out, "502 5.5.2 Command not recognized")
            }
        }
    }

    fun awaitSessionEnd(timeoutS: Long = Fixture.RESULT_TIMEOUT_S) = sessionsClosed.poll(timeoutS, TimeUnit.SECONDS)

    fun toJson(): JSONObject = JSONObject().apply {
        put("transcript", JSONArray(transcript.toList()))
        put("commands", JSONArray(commands.toList()))
        put("authUser", authUser ?: JSONObject.NULL)
        put("authPasswordPresent", authPassword != null)
        put("authPasswordIsFixtureValue", authPassword == "fake-smtp-password-98")
        put("mailFrom", mailFrom ?: JSONObject.NULL)
        put("recipients", JSONArray(recipients.toList()))
        put("dataLength", data?.size ?: 0)
        put("dataRaw", data?.let { String(it, Charsets.ISO_8859_1) } ?: JSONObject.NULL)
    }

    override fun close() {
        held.toList().forEach { try { it.close() } catch (_: Exception) {} }
        server.close()
    }
}
