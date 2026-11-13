package cn.ppps.forwarder.utils.mail

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import cn.ppps.forwarder.utils.Log
import org.openintents.openpgp.OpenPgpError
import org.openintents.openpgp.util.OpenPgpApi
import org.openintents.openpgp.util.OpenPgpServiceConnection
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * OpenKeychain（OpenPGP API）Envelope
 * https://github.com/open-keychain/open-keychain
 *
 * via org.openintents.openpgp.IOpenPgpService2 with OpenKeychain Communications，
 * Keys All By OpenKeychain Management, this application does not contact any key material.
 *
 * ⚠️Attention.：executeApi Possible Return RESULT_CODE_USER_INTERACTION_REQUIRED（User authorization required/Select Key），
 * This interaction requires a foreground activity（EmailFragment Save/preflight during tests); fail and log if encountered during background forwarding
 */
class OpenKeychainHelper(private val context: Context) {

    companion object {
        private val TAG: String = OpenKeychainHelper::class.java.simpleName

        const val PROVIDER_PACKAGE = "org.sufficientlysecure.keychain"
        private const val BIND_TIMEOUT_SECONDS = 10L

        //Check if it's installedOpenKeychain
        fun isProviderInstalled(context: Context): Boolean {
            return try {
                context.packageManager.getPackageInfo(PROVIDER_PACKAGE, PackageManager.GET_SERVICES)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }
    }

    private var serviceConnection: OpenPgpServiceConnection? = null

    /**
     * Blocking service bind; call off the main thread）
     * @throws Exception Not installedOpenKeychainor bind timed out/Error
     */
    @Synchronized
    private fun bindBlocking(): OpenPgpApi {
        if (!isProviderInstalled(context)) {
            throw Exception("OpenKeychain is not installed: $PROVIDER_PACKAGE")
        }
        serviceConnection?.let { conn ->
            if (conn.isBound) return OpenPgpApi(context, conn.service)
        }

        val latch = CountDownLatch(1)
        var bindError: Exception? = null
        val connection = OpenPgpServiceConnection(context.applicationContext, PROVIDER_PACKAGE, object : OpenPgpServiceConnection.OnBound {
            override fun onBound(service: org.openintents.openpgp.IOpenPgpService2) {
                latch.countDown()
            }

            override fun onError(e: Exception) {
                bindError = e
                latch.countDown()
            }
        })
        connection.bindToService()
        if (!latch.await(BIND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            connection.unbindFromService()
            throw Exception("Timeout binding to OpenKeychain service")
        }
        bindError?.let { throw Exception("Failed to bind OpenKeychain service: ${it.message}", it) }

        serviceConnection = connection
        return OpenPgpApi(context, connection.service)
    }

    /**
     * Execute OpenPGP API Blocking operation; call off the main thread）
     *
     * @return ResultIntent，RESULT_CODE Maybe. SUCCESS / USER_INTERACTION_REQUIRED / ERROR，Assigned by Caller
     */
    fun executeApi(data: Intent, input: InputStream?, output: OutputStream?): Intent {
        val api = bindBlocking()
        return api.executeApi(data, input, output)
    }

    /**
     * Encrypt with optional signing for background sending; call off the main thread）
     *
     * @param recipientEmails Recipient Mailbox（OpenKeychainbyUser IDAutomatically Match Public Keys）
     * @param signKeyId       Sign KeyID，0Means that only encryption does not sign
     * @throws Exception User interaction required (unavailable in background), or encryption error
     */
    fun encrypt(recipientEmails: Array<String>, signKeyId: Long, input: InputStream, output: OutputStream) {
        val data = Intent().apply {
            action = if (signKeyId != 0L) OpenPgpApi.ACTION_SIGN_AND_ENCRYPT else OpenPgpApi.ACTION_ENCRYPT
            putExtra(OpenPgpApi.EXTRA_USER_IDS, recipientEmails)
            if (signKeyId != 0L) putExtra(OpenPgpApi.EXTRA_SIGN_KEY_ID, signKeyId)
            putExtra(OpenPgpApi.EXTRA_REQUEST_ASCII_ARMOR, true)
        }
        val result = executeApi(data, input, output)
        when (result.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)) {
            OpenPgpApi.RESULT_CODE_SUCCESS -> Log.d(TAG, "OpenKeychain encrypt success, recipients=${recipientEmails.joinToString()}")

            OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED -> {
                //Backstage could not pop up the authorization interface: prompt the user to reset the channel edit page to complete the authorization
                throw Exception("OpenKeychain requires user interaction (key selection/authorization). Please re-save the email sender settings to authorize.")
            }

            else -> {
                val error: OpenPgpError? = result.getParcelableExtra(OpenPgpApi.RESULT_ERROR)
                throw Exception("OpenKeychain encrypt failed: ${error?.message ?: "unknown error"}")
            }
        }
    }

    fun unbind() {
        serviceConnection?.unbindFromService()
        serviceConnection = null
    }
}
