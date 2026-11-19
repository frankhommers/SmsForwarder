package cn.ppps.forwarder.utils.interceptor

import cn.ppps.forwarder.utils.Log
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Signals an HTTP 204/205 response to [onNoContent]. Retrofit passes these responses on with a null body, which
 * XHttp delivers to `onError` instead of `onSuccess`; the caller evaluates them there as an empty response body.
 *
 * This interceptor writes no log/forwarding status itself: the request callback is the single source of truth,
 * so a configured response check ("response must contain") can never be overridden afterwards.
 */
@Suppress("PrivatePropertyName")
class NoContentInterceptor(private val onNoContent: () -> Unit) : Interceptor {

    private val TAG: String = NoContentInterceptor::class.java.simpleName

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalResponse = chain.proceed(chain.request())

        if (originalResponse.code() == 204 || originalResponse.code() == 205) {
            val response = "HTTP Status " + originalResponse.code() + " " + originalResponse.message()
            Log.d(TAG, response)
            onNoContent()
        }

        return originalResponse
    }
}
