package cn.ppps.forwarder.utils

import android.text.TextUtils
import android.util.Log as AndroidLog

/**
 * Service-end security default migration
 *
 * The service end of the historical version fail-open Status: Signing key is empty security 0，Requesting no I.D.，
 * and the remote interface is all defaulted on. Here is the one-time migration at application start-up and the default status is changed to fail-closed.
 * Migration only"not explicitly configured by the user"or"Use old defaults"without overriding explicit user choices
 */
object SecurityDefaults {

    //Move the version, add and sync the new migration entry Constants.SP_SECURITY_DEFAULTS_VERSION Comment
    private const val CURRENT_VERSION = 1

    private const val SAFETY_MEASURES_SIGN = 1

    fun migrate() {
        if (readVersion() >= CURRENT_VERSION) return

        //1. Signing key automatically generated when empty, avoided"Verify Signature"Missing Keys in Mode
        if (TextUtils.isEmpty(HttpServerUtils.serverSignKey)) {
            HttpServerUtils.serverSignKey = HttpServerUtils.generateServerSignKey()
            AndroidLog.i(TAG, "Migration: Service signing key automatically generated")
        }

        //2. Default enabled when the user has no visible security setup"Verify Signature"
        if (!SharedPreference.contains(SP_SERVER_SAFETY_MEASURES)) {
            HttpServerUtils.safetyMeasures = SAFETY_MEASURES_SIGN
            AndroidLog.i(TAG, "Migration: Service-end security default is set to verify signature")
        }

        //3. Disable legacy default-enabled remote interfaces during migration
        disableApiIfEnabled(SP_ENABLE_API_CLONE) { HttpServerUtils.enableApiClone = false }
        disableApiIfEnabled(SP_ENABLE_API_SMS_SEND) { HttpServerUtils.enableApiSmsSend = false }
        disableApiIfEnabled(SP_ENABLE_API_SMS_QUERY) { HttpServerUtils.enableApiSmsQuery = false }
        disableApiIfEnabled(SP_ENABLE_API_CALL_QUERY) { HttpServerUtils.enableApiCallQuery = false }
        disableApiIfEnabled(SP_ENABLE_API_CONTACT_QUERY) { HttpServerUtils.enableApiContactQuery = false }
        disableApiIfEnabled(SP_ENABLE_API_CONTACT_ADD) { HttpServerUtils.enableApiContactAdd = false }
        disableApiIfEnabled(SP_ENABLE_API_BATTERY_QUERY) { HttpServerUtils.enableApiBatteryQuery = false }
        disableApiIfEnabled(SP_ENABLE_API_WOL) { HttpServerUtils.enableApiWol = false }

        //4. Listening only to the loop address to avoid uncertified services being exposed to the network
        if (!SharedPreference.contains(SP_SERVER_LOCAL_IP_ONLY)) {
            HttpServerUtils.serverLocalIpOnly = true
        }

        writeVersion(CURRENT_VERSION)
    }

    /**
     * Only old ones."Open"Status, closed or never set constant
     */
    private fun disableApiIfEnabled(key: String, disable: () -> Unit) {
        if (!SharedPreference.preference.getBoolean(key, false)) return
        disable()
        AndroidLog.i(TAG, "Migration: remote interface closed $key")
    }

    private fun readVersion(): Int = SharedPreference.preference.getInt(SP_SECURITY_DEFAULTS_VERSION, 0)

    private fun writeVersion(version: Int) {
        SharedPreference.preference.edit().putInt(SP_SECURITY_DEFAULTS_VERSION, version).apply()
    }

    private const val TAG: String = "SecurityDefaults"
}
