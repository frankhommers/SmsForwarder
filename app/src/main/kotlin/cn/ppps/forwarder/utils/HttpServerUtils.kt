package cn.ppps.forwarder.utils


import android.text.TextUtils
import android.util.Base64
import cn.ppps.forwarder.R
import cn.ppps.forwarder.core.Core
import cn.ppps.forwarder.entity.CloneInfo
import cn.ppps.forwarder.entity.LocationInfo
import cn.ppps.forwarder.server.model.BaseRequest
import com.google.gson.Gson
import com.xuexiang.xutil.resource.ResUtils.getString
import com.yanzhenjie.andserver.error.HttpException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HttpServerTool Class
 */
@Suppress("UselessCallOnNotNull")
class HttpServerUtils private constructor() {

    companion object {

        // Local Version -> Scope of the permissible request version
        private val VERSION_COMPAT_MAP: Map<Int, IntRange> = mapOf(
            55 to (54..55)
        )

        //EnableHttpServerStart on boot
        var enableServerAutorun: Boolean by SharedPreference(SP_ENABLE_SERVER_AUTORUN, false)

        //Service Signing Key
        var serverSignKey: String by SharedPreference(SP_SERVER_SIGN_KEY, "")

        //Service-end security settings, default enabled"Verify Signature"（SecurityDefaults Migration to make sure signature key is not empty）
        var safetyMeasures: Int by SharedPreference(SP_SERVER_SAFETY_MEASURES, 1)

        //ServerSM4Key
        var serverSm4Key: String by SharedPreference(SP_SERVER_SM4_KEY, "")

        //ServerRSAPublic Keys
        var serverPublicKey: String by SharedPreference(SP_SERVER_PUBLIC_KEY, "")

        //ServerRSAPrivate Key
        var serverPrivateKey: String by SharedPreference(SP_SERVER_PRIVATE_KEY, "")

        //Timestamp tolerance
        var timeTolerance: Int by SharedPreference(SP_SERVER_TIME_TOLERANCE, 600)

        //CustomwebClient Directory
        var serverWebPath: String by SharedPreference(SP_SERVER_WEB_PATH, "")

        //Service-end listening port
        var serverPort: Int by SharedPreference(SP_SERVER_PORT, HTTP_SERVER_PORT)

        //Listen on loopback only by default to avoid exposing an unauthenticated service
        var serverLocalIpOnly: Boolean by SharedPreference(SP_SERVER_LOCAL_IP_ONLY, true)

        //Service address
        var serverAddress: String by SharedPreference(SP_SERVER_ADDRESS, "http://127.0.0.1:$serverPort")

        //Service address history
        var serverHistory: String by SharedPreference(SP_SERVER_HISTORY, "")

        //Service-end Configuration
        var serverConfig: String by SharedPreference(SP_SERVER_CONFIG, "")

        //Client Signing Key/RSAPublic Keys
        var clientSignKey: String by SharedPreference(SP_CLIENT_SIGN_KEY, "")

        //Service-end security settings
        var clientSafetyMeasures: Int by SharedPreference(SP_CLIENT_SAFETY_MEASURES, if (TextUtils.isEmpty(clientSignKey)) 0 else 1)

        //Whether to enable a one-click clone
        var enableApiClone: Boolean by SharedPreference(SP_ENABLE_API_CLONE, false)

        //Enable remote texting
        var enableApiSmsSend: Boolean by SharedPreference(SP_ENABLE_API_SMS_SEND, false)

        //Enable remote checking of text messages
        var enableApiSmsQuery: Boolean by SharedPreference(SP_ENABLE_API_SMS_QUERY, false)

        //Enable remote checking of calls
        var enableApiCallQuery: Boolean by SharedPreference(SP_ENABLE_API_CALL_QUERY, false)

        //Enable remote contact queries
        var enableApiContactQuery: Boolean by SharedPreference(SP_ENABLE_API_CONTACT_QUERY, false)

        //Enable remote contact creation
        var enableApiContactAdd: Boolean by SharedPreference(SP_ENABLE_API_CONTACT_ADD, false)

        //Enable remote battery queries
        var enableApiBatteryQuery: Boolean by SharedPreference(SP_ENABLE_API_BATTERY_QUERY, false)

        //Enable RemoteWOL
        var enableApiWol: Boolean by SharedPreference(SP_ENABLE_API_WOL, false)

        //Whether or not to enable remote check configuration
        var enableApiConfigQuery: Boolean by SharedPreference(SP_ENABLE_API_CONFIG_QUERY, false)

        //Whether or not to enable remote search cell phones
        var enableApiLocation: Boolean by SharedPreference(SP_ENABLE_API_LOCATION, false)

        //Remote cell location cache
        var apiLocationCache: LocationInfo by SharedPreference(SP_API_LOCATION_CACHE, LocationInfo())

        //WOLHistory
        var wolHistory: String by SharedPreference(SP_WOL_HISTORY, "")

        //Compute Signature
        fun calcSign(timestamp: String, signSecret: String): String {
            val stringToSign = "$timestamp\n" + signSecret
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(signSecret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
            val signData = mac.doFinal(stringToSign.toByteArray(StandardCharsets.UTF_8))
            return URLEncoder.encode(String(Base64.encode(signData, Base64.NO_WRAP)), "UTF-8")
        }

        fun generateServerSignKey(): String {
            val bytes = ByteArray(24)
            SecureRandom().nextBytes(bytes)
            return String(Base64.encode(bytes, Base64.URL_SAFE or Base64.NO_WRAP), StandardCharsets.US_ASCII)
        }

        fun isCloneEncryptionEnabled(): Boolean = safetyMeasures == 2 || safetyMeasures == 3

        //"Verify Signature"Mode key is configured and service startup is not allowed when the key is empty
        fun isSignKeyConfigured(): Boolean = !TextUtils.isEmpty(serverSignKey)

        //Validate all credentials required by the current security mode before any server startup
        //Measures=0 No additional requirements (interface has been forced to listen back to the ring)）；1 Signing key required，2 requiresRSAPrivate Key，3 requiresSM4Key
        fun isServerSecretsConfigured(): Boolean = when (safetyMeasures) {
            1 -> isSignKeyConfigured()
            2 -> !TextUtils.isEmpty(serverPrivateKey)
            3 -> !TextUtils.isEmpty(serverSm4Key)
            else -> true
        }

        //Has the client key been configured and must be filled with a key that corresponds to the service 's before connecting
        fun isClientSignKeyConfigured(): Boolean = !TextUtils.isEmpty(clientSignKey)

        //Verify Signature
        @Throws(HttpException::class)
        fun checkSign(req: BaseRequest<*>) {
            //Direct refusal when key is missing, avoid"signature verification configured without a key"must not allow all requests
            if (!isSignKeyConfigured()) throw HttpException(500, getString(R.string.sign_key_required))

            val signSecret = serverSignKey
            if (TextUtils.isEmpty(req.sign)) throw HttpException(HTTP_UNAUTHORIZED_CODE, getString(R.string.sign_required))
            if (req.timestamp == 0L) throw HttpException(HTTP_UNAUTHORIZED_CODE, getString(R.string.timestamp_required))

            val timestamp = System.currentTimeMillis()
            val diffTime = kotlin.math.abs(timestamp - req.timestamp)
            val tolerance = timeTolerance * 1000L
            if (diffTime > tolerance) {
                throw HttpException(HTTP_UNAUTHORIZED_CODE, String.format(getString(R.string.timestamp_verify_failed), timestamp, timeTolerance, diffTime))
            }

            val sign = calcSign(req.timestamp.toString(), signSecret)
            if (sign != req.sign) {
                Log.e("calcSign", sign)
                Log.e("reqSign", req.sign.toString())
                throw HttpException(HTTP_UNAUTHORIZED_CODE, getString(R.string.sign_verify_failed))
            }
        }

        //Judge whether the version is consistent
        @Throws(HttpException::class)
        fun compareVersion(cloneInfo: CloneInfo) {
            val versionCode = cloneInfo.versionCode
            if (versionCode == 0) throw HttpException(500, getString(R.string.version_code_required))

            val requestVersion = versionCode.toString().substring(1).toInt()
            val localVersion = AppUtils.getAppVersionCode().toString().substring(1).toInt()
            val compatibleRange = VERSION_COMPAT_MAP[localVersion]
            Log.d("HttpServerUtils", "compareVersion: localVersion=$localVersion, requestVersion=$requestVersion, compatibleRange=$compatibleRange")
            val isCompatible = if (compatibleRange != null) {
                requestVersion in compatibleRange
            } else {
                requestVersion == localVersion
            }
            if (!isCompatible) {
                throw HttpException(500, getString(R.string.inconsistent_version))
            }
        }

        //Export Settings
        fun exportSettings(): CloneInfo {
            val cloneInfo = CloneInfo()
            cloneInfo.versionCode = AppUtils.getAppVersionCode()
            cloneInfo.versionName = AppUtils.getAppVersionName()
            cloneInfo.settings = SharedPreference.exportPreference()
            cloneInfo.senderList = Core.sender.getAllNonCache()
            cloneInfo.ruleList = Core.rule.getAllNonCache()
            cloneInfo.frpcList = Core.frpc.getAllNonCache()
            cloneInfo.taskList = Core.task.getAllNonCache()
            return cloneInfo
        }

        //Revert Settings
        fun restoreSettings(cloneInfo: CloneInfo): Boolean {
            return try {
                //Retain device name,SIMCard Master Key/Remarks
                val extraDeviceMark = SettingUtils.extraDeviceMark
                val subidSim1 = SettingUtils.subidSim1
                val extraSim1 = SettingUtils.extraSim1
                val subidSim2 = SettingUtils.subidSim2
                val extraSim2 = SettingUtils.extraSim2
                //Apply Configuration
                SharedPreference.clearPreference()
                SharedPreference.importPreference(cloneInfo.settings)
                //Configuration to be excluded
                SettingUtils.extraDeviceMark = extraDeviceMark
                SettingUtils.subidSim1 = subidSim1
                SettingUtils.extraSim1 = extraSim1
                SettingUtils.subidSim2 = subidSim2
                SettingUtils.extraSim2 = extraSim2
                //Delete Message & Forward Log
                Core.logs.deleteAll()
                Core.msg.deleteAll()
                //Forwarding channels
                Core.sender.deleteAll()
                if (!cloneInfo.senderList.isNullOrEmpty()) {
                    for (sender in cloneInfo.senderList!!) {
                        Core.sender.insert(sender)
                    }
                }
                //Forwarding rules
                Core.rule.deleteAll()
                if (!cloneInfo.ruleList.isNullOrEmpty()) {
                    for (rule in cloneInfo.ruleList!!) {
                        if (rule.title.isNullOrEmpty()) rule.title = "" //Compatible with old versions
                        Core.rule.insert(rule)
                    }
                }
                //FrpcConfigure
                Core.frpc.deleteAll()
                if (!cloneInfo.frpcList.isNullOrEmpty()) {
                    for (frpc in cloneInfo.frpcList!!) {
                        Core.frpc.insert(frpc)
                    }
                }
                //TaskConfigure
                Core.task.deleteAll()
                if (!cloneInfo.taskList.isNullOrEmpty()) {
                    for (task in cloneInfo.taskList!!) {
                        Core.task.insert(task)
                    }
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("restoreSettings", e.message.toString())
                throw HttpException(500, e.message)
                //false
            }
        }

        //Return to Unified Structure Message，failureCode Allow the exception resolver to include 401/403 The error code outside the business
        fun response(output: Any?, failureCode: Int = HTTP_FAILURE_CODE): String {
            val resp: MutableMap<String, Any> = mutableMapOf()
            val timestamp = System.currentTimeMillis()
            resp["timestamp"] = timestamp
            if (output is String && output != "success") {
                resp["code"] = failureCode
                resp["msg"] = output
            } else {
                resp["code"] = HTTP_SUCCESS_CODE
                resp["msg"] = "success"
                if (output != null) {
                    resp["data"] = output
                }
                if (safetyMeasures == 1) {
                    resp["sign"] = calcSign(timestamp.toString(), serverSignKey)
                }
            }

            return Gson().toJson(resp)
        }
    }
}