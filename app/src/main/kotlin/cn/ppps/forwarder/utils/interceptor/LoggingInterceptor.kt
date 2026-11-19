package cn.ppps.forwarder.utils.interceptor

import cn.ppps.forwarder.utils.Log
import cn.ppps.forwarder.utils.SendUtils
import com.xuexiang.xhttp2.interceptor.HttpLoggingInterceptor
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * Records a minimal request trace in logcat and in the forwarding log entry ([logId]).
 *
 * Only the method, scheme, host, port, status code, duration and exception type are recorded. Paths, query strings,
 * user info, headers and bodies are never recorded: target URLs embed bot tokens, access tokens, webhook keys and
 * secrets (for example `/bot<token>/sendMessage`, `?access_token=`, `?corpsecret=`), and bodies contain message
 * content and credentials. The response body is not consumed; each target's own callback still stores its result.
 *
 * [recorder] replaces the logcat/forwarding-log sink; it exists for unit tests.
 */
@Suppress("PrivatePropertyName")
class LoggingInterceptor(
    private val logId: Long,
    private val recorder: ((String) -> Unit)? = null,
) : HttpLoggingInterceptor("custom") {

    private val TAG: String = LoggingInterceptor::class.java.simpleName

    init {
        level = Level.BASIC
    }

    override fun log(message: String) {
        recorder?.let { it(message); return }
        Log.d(TAG, message)
        //Status=-1，Do not update current status
        SendUtils.updateLogs(logId, -1, message)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        log("--> ${request.method()} ${origin(request.url())}")
        val startNs = System.nanoTime()
        val response = try {
            chain.proceed(request)
        } catch (e: Exception) {
            // Exception messages can contain the full URL; record only the exception type.
            log("<-- HTTP FAILED: ${e.javaClass.simpleName}")
            throw e
        }
        val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)
        log("<-- ${response.code()} ${response.message()} ${origin(response.request().url())} ($tookMs ms)")
        return response
    }

    companion object {
        /** scheme://host[:port] without user info, path, query or fragment. */
        @JvmStatic
        fun origin(url: HttpUrl): String {
            val host = if (url.host().contains(':')) "[${url.host()}]" else url.host()
            return if (url.port() == HttpUrl.defaultPort(url.scheme())) "${url.scheme()}://$host"
            else "${url.scheme()}://$host:${url.port()}"
        }
    }
}
