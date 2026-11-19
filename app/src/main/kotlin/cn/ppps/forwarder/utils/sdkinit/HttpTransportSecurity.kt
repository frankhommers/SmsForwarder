package cn.ppps.forwarder.utils.sdkinit

import com.xuexiang.xhttp2.XHttp
import okhttp3.internal.tls.OkHostnameVerifier

/**
 * Transport hardening for the shared XHttp/OkHttp client used by all HTTP forwarding targets.
 *
 * - XHttp 2.0.10's constructor installs `com.xuexiang.xhttp2.https.DefaultHostnameVerifier`, whose `verify()`
 *   returns `true` unconditionally (see the bytecode in the packaged AAR). Removing per-request
 *   `ignoreHttpsCert()` alone therefore still accepted any certificate host name. OkHttp's own verifier is restored.
 * - `XHttp.debug(true)` adds a BODY-level logging interceptor (URLs incl. tokens, headers, bodies) to every
 *   request, so library debug logging stays off regardless of the build type.
 *
 * Certificate chains are validated by OkHttp's default platform trust manager; plain `http://` targets that the
 * user configured are unaffected.
 */
object HttpTransportSecurity {
    @JvmStatic
    fun apply(xHttp: XHttp): XHttp = xHttp
        .setHostnameVerifier(OkHostnameVerifier.INSTANCE)
        .debug(false)
}
