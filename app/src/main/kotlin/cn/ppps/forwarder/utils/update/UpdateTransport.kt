package cn.ppps.forwarder.utils.update

import android.util.Base64
import cn.ppps.forwarder.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** Failure with a user-facing kind; see UpdateResult for rendering. */
class UpdateException(val kind: String, val arg: String = "") : Exception(kind + if (arg.isEmpty()) "" else ": $arg")

/**
 * The updater's only network client. HTTPS only, normal certificate and hostname validation, no cookies,
 * no cache, no authentication, no extra headers, redirects followed manually and only to GitHub hosts.
 *
 * Test builds may set BuildConfig.UPDATER_FIXTURE_ORIGIN / UPDATER_FIXTURE_CA (Gradle properties) to reach a
 * local fixture. The policy still runs on the logical github.com URLs; the fixture's own CA is the only trust
 * anchor then, so TLS is still fully validated. Both fields are empty in normal builds.
 */
object UpdateTransport {
    private val fixtureOrigin: String get() = BuildConfig.UPDATER_FIXTURE_ORIGIN
    val usesFixture: Boolean get() = fixtureOrigin.isNotEmpty()

    private val client: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.MINUTES)
        if (BuildConfig.UPDATER_FIXTURE_CA.isNotEmpty()) {
            val cert = CertificateFactory.getInstance("X.509").generateCertificate(Base64.decode(BuildConfig.UPDATER_FIXTURE_CA, Base64.DEFAULT).inputStream())
            val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null); setCertificateEntry("fixture", cert) }
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(store) }
            val tm = tmf.trustManagers.filterIsInstance<X509TrustManager>().first()
            val ctx = SSLContext.getInstance("TLS").apply { init(null, arrayOf(tm), null) }
            builder.sslSocketFactory(ctx.socketFactory, tm)
        }
        builder.build()
    }

    /**
     * GET [logicalUrl], following at most MAX_REDIRECTS redirects, each of which must stay on an allowed GitHub
     * host over HTTPS. The returned response is a 2xx; callers must close it.
     */
    private fun open(logicalUrl: String): Response {
        var url = logicalUrl
        for (hop in 0..UpdatePolicy.MAX_REDIRECTS) {
            if (!UpdatePolicy.isAllowedUrl(url)) throw UpdateException(UpdateResult.REDIRECT_REFUSED, UpdatePolicy.hostOf(url))
            val request = Request.Builder().url(UpdatePolicy.transportUrl(url, fixtureOrigin)).get().build()
            val response = try { client.newCall(request).execute() } catch (e: IOException) { throw UpdateException(UpdateResult.NETWORK) }
            val code = response.code()
            if (code in 300..399) {
                val next = UpdatePolicy.resolveRedirect(url, response.header("Location"))
                response.close()
                url = next ?: throw UpdateException(UpdateResult.HTTP, code.toString())
                continue
            }
            if (code == 429 || (code == 403 && response.header("X-RateLimit-Remaining") == "0")) {
                response.close(); throw UpdateException(UpdateResult.RATE_LIMITED)
            }
            if (code !in 200..299) { response.close(); throw UpdateException(UpdateResult.HTTP, code.toString()) }
            return response
        }
        throw UpdateException(UpdateResult.HTTP, "redirects")
    }

    /** Small text response with a hard size cap. */
    fun getText(logicalUrl: String, maxBytes: Long): String = open(logicalUrl).use { response ->
        val body = response.body() ?: throw UpdateException(UpdateResult.BAD_LIST)
        if (body.contentLength() > maxBytes) throw UpdateException(UpdateResult.BAD_LIST)
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        try {
            body.byteStream().use { input ->
                while (true) {
                    val n = input.read(buffer); if (n < 0) break
                    out.write(buffer, 0, n)
                    if (out.size() > maxBytes) throw UpdateException(UpdateResult.BAD_LIST)
                }
            }
        } catch (e: IOException) { throw UpdateException(UpdateResult.NETWORK) }
        String(out.toByteArray(), Charsets.UTF_8)
    }

    /**
     * Streams to [target], never writing more than [expectedSize] bytes; returns the SHA-256 (hex) and size.
     * The file is deleted on any failure.
     */
    fun download(logicalUrl: String, expectedSize: Long, target: File, progress: (Long, Long) -> Unit): Pair<String, Long> {
        try {
            open(logicalUrl).use { response ->
                val body = response.body() ?: throw UpdateException(UpdateResult.NETWORK)
                if (body.contentLength() > expectedSize) throw UpdateException(UpdateResult.TOO_LARGE)
                val digest = MessageDigest.getInstance("SHA-256")
                var total = 0L
                var lastReport = 0L
                FileOutputStream(target).use { out ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = try { input.read(buffer) } catch (e: IOException) { throw UpdateException(UpdateResult.NETWORK) }
                            if (n < 0) break
                            total += n
                            if (total > expectedSize || total > UpdatePolicy.MAX_APK_BYTES) throw UpdateException(UpdateResult.TOO_LARGE)
                            digest.update(buffer, 0, n)
                            out.write(buffer, 0, n)
                            if (total - lastReport >= 256 * 1024) { lastReport = total; progress(total, expectedSize) }
                        }
                    }
                    out.fd.sync()
                }
                progress(total, expectedSize)
                return digest.digest().joinToString("") { "%02x".format(it) } to total
            }
        } catch (e: Exception) {
            target.delete()
            if (e is UpdateException) throw e
            throw UpdateException(UpdateResult.STORAGE)
        }
    }
}
