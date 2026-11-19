package cn.ppps.forwarder.utils.sender

import android.annotation.SuppressLint
import android.text.TextUtils
import android.util.Base64
import cn.ppps.forwarder.database.entity.Rule
import cn.ppps.forwarder.entity.MsgInfo
import cn.ppps.forwarder.entity.setting.WebhookSetting
import cn.ppps.forwarder.utils.AppUtils
import cn.ppps.forwarder.utils.Log
import cn.ppps.forwarder.utils.SendUtils
import cn.ppps.forwarder.utils.SettingUtils
import cn.ppps.forwarder.utils.interceptor.BasicAuthInterceptor
import cn.ppps.forwarder.utils.interceptor.LoggingInterceptor
import cn.ppps.forwarder.utils.interceptor.NoContentInterceptor
import com.google.gson.Gson
import com.xuexiang.xhttp2.XHttp
import com.xuexiang.xhttp2.callback.SimpleCallBack
import com.xuexiang.xhttp2.exception.ApiException
import com.xuexiang.xutil.security.CipherUtils
import okhttp3.Credentials
import okhttp3.Response
import okhttp3.Route
import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec


class WebhookUtils {
    companion object {

        private val TAG: String = WebhookUtils::class.java.simpleName

        fun sendMsg(
            setting: WebhookSetting,
            msgInfo: MsgInfo,
            rule: Rule? = null,
            senderIndex: Int = 0,
            logId: Long = 0L,
            msgId: Long = 0L
        ) {
            val from: String = msgInfo.from
            val content: String = if (rule != null) {
                msgInfo.getContentForSend(rule.smsTemplate, rule.regexReplace, rule.title)
            } else {
                msgInfo.getContentForSend(SettingUtils.smsTemplate)
            }

            var requestUrl: String = setting.webServer //Send Address


            val timestamp = System.currentTimeMillis()
            val orgContent: String = msgInfo.content
            val deviceMark: String = SettingUtils.extraDeviceMark
            val appVersion: String = AppUtils.getAppVersionName()
            val simInfo: String = msgInfo.simInfo
            val receiveTimeTag = Regex("\\[receive_time(:(.*?))?]")

            var sign = ""
            if (!TextUtils.isEmpty(setting.secret)) {
                val stringToSign = "$timestamp\n" + setting.secret
                val mac = Mac.getInstance("HmacSHA256")
                mac.init(
                    SecretKeySpec(
                        setting.secret.toByteArray(StandardCharsets.UTF_8),
                        "HmacSHA256"
                    )
                )
                val signData = mac.doFinal(stringToSign.toByteArray(StandardCharsets.UTF_8))
                sign = URLEncoder.encode(String(Base64.encode(signData, Base64.NO_WRAP)), "UTF-8")
            }

            var webParams = setting.webParams.trim()
            //Extract webParams in md5([Variable Name1]+[Variable Name2]+[Variable Name3]+'Custom content') Replace With md5 Value
            webParams = replaceMd5Template(webParams, msgInfo, from, content, orgContent, deviceMark, appVersion, simInfo, receiveTimeTag, timestamp, sign)

            //SupportHTTPBasic authentication(Basic Authentication)
            val regex = "^(https?://)([^:]+):([^@]+)@(.+)"
            val matches = Regex(regex, RegexOption.IGNORE_CASE).findAll(requestUrl).toList()
                .flatMap(MatchResult::groupValues)

            if (matches.isNotEmpty()) {
                requestUrl = matches[1] + matches[4]

            }

            //via`Content-Type=applicaton/json`Specifies the request body as`json`Format
            var isJson = false
            //via`Content-Type=text/plain,text/html,text/css,text/javascript,text/xml`Specifies the request body as`Text`Format
            var isText = false
            var mediaType = "text/plain"
            for ((key, value) in setting.headers.entries) {
                if (key.equals("Content-Type", ignoreCase = true)) {
                    if (value.contains("application/json")) {
                        isJson = true
                        break
                    } else if (value.startsWith("text/")) {
                        isText = true
                        mediaType = value
                        break
                    }
                }
            }

            val request = if (setting.method == "GET" && TextUtils.isEmpty(webParams)) {
                requestUrl = cn.ppps.forwarder.utils.WebhookEditorModel.defaultGetUrl(requestUrl, from, content, timestamp, sign)
                XHttp.get(requestUrl).keepJson(true)
            } else if (setting.method == "GET" && !TextUtils.isEmpty(webParams)) {
                webParams = msgInfo.replaceTemplate(webParams, "", "URLEncoder", rule?.title ?: "")
                webParams = webParams.replace("[from]", URLEncoder.encode(from, "UTF-8"))
                    .replace("[content]", URLEncoder.encode(content, "UTF-8"))
                    .replace("[msg]", URLEncoder.encode(content, "UTF-8"))
                    .replace("[org_content]", URLEncoder.encode(orgContent, "UTF-8"))
                    .replace("[device_mark]", URLEncoder.encode(deviceMark, "UTF-8"))
                    .replace("[app_version]", URLEncoder.encode(appVersion, "UTF-8"))
                    .replace("[title]", URLEncoder.encode(simInfo, "UTF-8"))
                    .replace("[card_slot]", URLEncoder.encode(simInfo, "UTF-8"))
                    .replace(receiveTimeTag) {
                        val format = it.groups[2]?.value
                        URLEncoder.encode(formatDateTime(msgInfo.date, format), "UTF-8")
                    }
                    .replace("\n", "%0A")
                if (!TextUtils.isEmpty(setting.secret)) {
                    webParams = webParams.replace("[timestamp]", timestamp.toString())
                        .replace("[sign]", URLEncoder.encode(sign, "UTF-8"))
                }
                requestUrl += if (webParams.startsWith("/")) {
                    webParams
                } else {
                    (if (requestUrl.contains("?")) "&" else "?") + webParams
                }

                XHttp.get(requestUrl).keepJson(true)
            } else if (webParams.isNotEmpty() && (isJson || isText || webParams.startsWith("{"))) {
                webParams = msgInfo.replaceTemplate(webParams, "", "Gson", rule?.title ?: "")
                val bodyMsg = webParams.replace("[from]", from)
                    .replace("[content]", escapeJson(content))
                    .replace("[msg]", escapeJson(content))
                    .replace("[org_content]", escapeJson(orgContent))
                    .replace("[device_mark]", escapeJson(deviceMark))
                    .replace("[app_version]", appVersion)
                    .replace("[title]", escapeJson(simInfo))
                    .replace("[card_slot]", escapeJson(simInfo))
                    .replace(receiveTimeTag) {
                        val format = it.groups[2]?.value
                        formatDateTime(msgInfo.date, format)
                    }
                    .replace("[timestamp]", timestamp.toString())
                    .replace("[sign]", sign)

                if (isText) {
                    when (setting.method) {
                        "PUT" -> XHttp.put(requestUrl).keepJson(true).upString(bodyMsg, mediaType)
                        "PATCH" -> XHttp.patch(requestUrl).keepJson(true).upString(bodyMsg, mediaType)
                        else -> XHttp.post(requestUrl).keepJson(true).upString(bodyMsg, mediaType)
                    }
                } else {
                    when (setting.method) {
                        "PUT" -> XHttp.put(requestUrl).keepJson(true).upJson(bodyMsg)
                        "PATCH" -> XHttp.patch(requestUrl).keepJson(true).upJson(bodyMsg)
                        else -> XHttp.post(requestUrl).keepJson(true).upJson(bodyMsg)
                    }
                }
            } else {
                if (webParams.isEmpty()) {
                    webParams = "from=[from]&content=[content]&timestamp=[timestamp]"
                    if (!TextUtils.isEmpty(sign)) webParams += "&sign=[sign]"
                }

                val postRequest = when (setting.method) {
                    "PUT" -> XHttp.put(requestUrl).keepJson(true)
                    "PATCH" -> XHttp.patch(requestUrl).keepJson(true)
                    else -> XHttp.post(requestUrl).keepJson(true)
                }
                webParams = msgInfo.replaceTemplate(webParams, "", "", rule?.title ?: "")
                webParams.trim('&').split("&").forEach {
                    val sepIndex = it.indexOf("=")
                    if (sepIndex != -1) {
                        val key = it.substring(0, sepIndex).trim()
                        val value = it.substring(sepIndex + 1).trim()
                        postRequest.params(
                            key, value.replace("[from]", from)
                                .replace("[content]", content)
                                .replace("[msg]", content)
                                .replace("[org_content]", orgContent)
                                .replace("[device_mark]", deviceMark)
                                .replace("[app_version]", appVersion)
                                .replace("[title]", simInfo)
                                .replace("[card_slot]", simInfo)
                                .replace(receiveTimeTag) { t ->
                                    val format = t.groups[2]?.value
                                    formatDateTime(msgInfo.date, format)
                                }
                                .replace("[timestamp]", timestamp.toString())
                                .replace("[sign]", sign)
                        )
                    }
                }
                postRequest
            }

            //Addheaders
            for ((key, value) in setting.headers.entries) {
                request.headers(key, value)
            }

            //SupportHTTPBasic authentication(Basic Authentication)
            if (matches.isNotEmpty()) {
                request.addInterceptor(BasicAuthInterceptor(matches[2], matches[3]))
            }

            //Setup Proxy
            if ((setting.proxyType == Proxy.Type.HTTP || setting.proxyType == Proxy.Type.SOCKS)
                && !TextUtils.isEmpty(setting.proxyHost) && !TextUtils.isEmpty(setting.proxyPort)
            ) {
                //Proxy.IPand port number
                val proxyPort = setting.proxyPort.toIntOrNull()
                    ?: throw IllegalArgumentException("Invalid proxy port")

                request.okproxy(Proxy(setting.proxyType, InetSocketAddress(setting.proxyHost, proxyPort)))

                //Proxy authentication credentials
                if (setting.proxyAuthenticator && (!TextUtils.isEmpty(setting.proxyUsername) || !TextUtils.isEmpty(setting.proxyPassword))
                ) {


                    if (setting.proxyType == Proxy.Type.HTTP) {
                        request.okproxyAuthenticator { _: Route?, response: Response ->
                            //Set proxy account password
                            val credential = Credentials.basic(setting.proxyUsername, setting.proxyPassword)
                            response.request().newBuilder()
                                .header("Proxy-Authorization", credential)
                                .build()
                        }
                    } else {
                        Authenticator.setDefault(object : Authenticator() {
                            override fun getPasswordAuthentication(): PasswordAuthentication {
                                return PasswordAuthentication(setting.proxyUsername, setting.proxyPassword.toCharArray())
                            }
                        })
                    }
                }
            }

            // HTTP 204/205 arrive in onError (null body); they are evaluated there as an empty response body.
            val noContent = java.util.concurrent.atomic.AtomicBoolean(false)
            request.retryCount(SettingUtils.requestRetryTimes) //Number of overtime retrys
                .retryDelay(SettingUtils.requestDelayTime * 1000) //Timeout retry delay
                .retryIncreaseDelay(SettingUtils.requestDelayTime * 1000) //Incremental timeout retry backoff
                .timeStamp(true) //urlAutomatically add a time stamp to avoid cache
                // No request/header/body logging: URLs and responses can contain credentials.
                .addInterceptor(NoContentInterceptor { noContent.set(true) }) //Detect HTTP 204/205 (empty body)
                .execute(object : SimpleCallBack<String>() {

                    override fun onError(e: ApiException) {
                        //e.printStackTrace()

                        val status = if (noContent.get()) {
                            // Empty body: success only when no response text is required.
                            if (setting.response.isEmpty()) 2 else 0
                        } else if (setting.response.isNotEmpty() && e.detailMessage.contains(setting.response)) 2 else 0
                        SendUtils.updateLogs(logId, status, com.xuexiang.xutil.resource.ResUtils.getString(if (status == 2) cn.ppps.forwarder.R.string.webhook_result_success else cn.ppps.forwarder.R.string.webhook_result_failed))
                        SendUtils.senderLogic(status, msgInfo, rule, senderIndex, msgId)
                    }

                    override fun onSuccess(response: String) {

                        val status = if (setting.response.isNotEmpty() && !response.contains(setting.response)) 0 else 2
                        SendUtils.updateLogs(logId, status, com.xuexiang.xutil.resource.ResUtils.getString(if (status == 2) cn.ppps.forwarder.R.string.webhook_result_success else cn.ppps.forwarder.R.string.webhook_result_failed))
                        SendUtils.senderLogic(status, msgInfo, rule, senderIndex, msgId)
                    }

                })

        }

        //JSONCharacters requiring escaping
        private fun escapeJson(str: String?): String {
            if (str == null) return "null"
            val jsonStr: String = Gson().toJson(str)
            return if (jsonStr.length >= 2) jsonStr.substring(1, jsonStr.length - 1) else jsonStr
        }

        @SuppressLint("SimpleDateFormat")
        fun formatDateTime(currentTime: Date, format: String?): String {
            val actualFormat = format?.removePrefix(":") ?: "yyyy-MM-dd HH:mm:ss"
            val dateFormat = SimpleDateFormat(actualFormat)
            return dateFormat.format(currentTime)
        }

        //Extract webParams in md5([Variable Name1]+[Variable Name2]+[Variable Name3]+'Custom content') Replace With md5 Value
        fun replaceMd5Template(
            webParams: String, msgInfo: MsgInfo,
            from: String, content: String, orgContent: String, deviceMark: String, appVersion: String, simInfo: String,
            receiveTimeTag: Regex, timestamp: Long, sign: String
        ): String {
            val regex = Regex("md5\\((.*?)\\)")
            //Remove concatenation operators + and removes a single quote from the custom content (in quotation marks) + preserve）
            val separatorRegex = Regex("'(.*?)'|\\+")
            return regex.replace(webParams) { matchResult ->
                val md5String = matchResult.groupValues[1].replace(separatorRegex) { it.groupValues[1] }
                var replacedContent = msgInfo.replaceTemplate(md5String, "", "", "")
                replacedContent = replacedContent
                    .replace("[from]", from)
                    .replace("[content]", content)
                    .replace("[msg]", content)
                    .replace("[org_content]", orgContent)
                    .replace("[device_mark]", deviceMark)
                    .replace("[app_version]", appVersion)
                    .replace("[title]", simInfo)
                    .replace("[card_slot]", simInfo)
                    .replace("[timestamp]", timestamp.toString())
                    .replace("[sign]", sign)
                    .replace(receiveTimeTag) {
                        val format = it.groups[2]?.value
                        formatDateTime(msgInfo.date, format)
                    }
                val md5Value = CipherUtils.md5(replacedContent)
                md5Value
            }
        }

    }
}