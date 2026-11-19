package cn.ppps.forwarder.utils.sender

import cn.ppps.forwarder.App
import cn.ppps.forwarder.R
import cn.ppps.forwarder.database.entity.Rule
import cn.ppps.forwarder.entity.MsgInfo
import cn.ppps.forwarder.entity.setting.EmailSetting
import cn.ppps.forwarder.utils.Log
import cn.ppps.forwarder.utils.EVENT_TOAST_WARNING
import com.jeremyliao.liveeventbus.LiveEventBus
import cn.ppps.forwarder.utils.SendUtils
import cn.ppps.forwarder.utils.SettingUtils
import cn.ppps.forwarder.utils.mail.EmailCrypto
import cn.ppps.forwarder.utils.mail.EmailDeliveryReport
import cn.ppps.forwarder.utils.mail.EmailKeys
import cn.ppps.forwarder.utils.mail.EmailOutcome
import cn.ppps.forwarder.utils.mail.EmailSender
import cn.ppps.forwarder.utils.mail.EncryptedEmailDelivery
import cn.ppps.forwarder.utils.mail.OpenKeychainHelper
import cn.ppps.forwarder.utils.mail.OpenKeychainMailUtils
import cn.ppps.forwarder.utils.mail.PgpUtils
import cn.ppps.forwarder.utils.mail.RecipientKey
import cn.ppps.forwarder.utils.mail.SigningKey
import cn.ppps.forwarder.utils.mail.SmimeUtils
import jakarta.mail.Session
import jakarta.mail.Transport
import com.xuexiang.xutil.resource.ResUtils.getString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.bouncycastle.openpgp.PGPPublicKeyRing
import java.security.cert.X509Certificate

class EmailUtils {
    companion object {

        private val TAG: String = EmailUtils::class.java.simpleName

        fun sendMsg(
            setting: EmailSetting,
            msgInfo: MsgInfo,
            rule: Rule? = null,
            senderIndex: Int = 0,
            logId: Long = 0L,
            msgId: Long = 0L
        ) {
            val title: String = if (rule != null) {
                msgInfo.getTitleForSend(setting.title, rule.regexReplace, rule.title)
            } else {
                msgInfo.getTitleForSend(setting.title)
            }
            val message: String = if (rule != null) {
                msgInfo.getContentForSend(rule.smsTemplate, rule.regexReplace, rule.title)
            } else {
                msgInfo.getContentForSend(SettingUtils.smsTemplate)
            }

            //Conversion of commonly used mailbox types
            when (setting.mailType) {
                "@qq.com", "@foxmail.com" -> {
                    setting.host = "smtp.qq.com"
                    setting.port = "465"
                    setting.ssl = true
                    setting.fromEmail += setting.mailType
                }

                "@exmail.qq.com" -> {
                    setting.host = "smtp.exmail.qq.com"
                    setting.port = "465"
                    setting.ssl = true
                    setting.fromEmail += setting.mailType
                }

                "@gmail.com" -> {
                    setting.host = "smtp.gmail.com"
                    setting.port = "465"
                    setting.ssl = true
                    //setting.startTls = true
                    setting.fromEmail += setting.mailType
                }

                "@yeah.net" -> {
                    setting.host = "smtp.yeah.net"
                    setting.port = "465"
                    setting.ssl = true
                    setting.fromEmail += setting.mailType
                }

                "@163.com" -> {
                    setting.host = "smtp.163.com"
                    setting.port = "465"
                    setting.ssl = true
                    setting.fromEmail += setting.mailType
                }

                "@126.com" -> {
                    setting.host = "smtp.126.com"
                    setting.port = "465"
                    setting.ssl = true
                    setting.fromEmail += setting.mailType
                }

                "@sina.com" -> {
                    setting.host = "smtp.sina.com"
                    setting.port = "465"
                    setting.ssl = true
                    setting.fromEmail += setting.mailType
                }

                "@sina.cn" -> {
                    setting.host = "smtp.sina.cn"
                    setting.port = "465"
                    setting.ssl = true
                    setting.fromEmail += setting.mailType
                }

                "@139.com" -> {
                    setting.host = "smtp.139.com"
                    setting.port = "465"
                    setting.ssl = true
                    setting.fromEmail += setting.mailType
                }

                "@189.cn" -> {
                    setting.host = "smtp.189.cn"
                    setting.port = "465"
                    setting.ssl = true
                    setting.fromEmail += setting.mailType
                }

                "@icloud.com" -> {
                    setting.host = "smtp.mail.me.com"
                    setting.port = "587"
                    setting.ssl = false
                    setting.startTls = true
                    setting.fromEmail += setting.mailType
                }

                else -> {}
            }

            runBlocking {
                val job = launch(Dispatchers.IO) {
                    try {
                        // Set Mail Parameters
                        val host = setting.host
                        val port = setting.port
                        val from = setting.fromEmail
                        val password = setting.pwd
                        val fromAlias = setting.fromEmailAlias.ifEmpty {
                            setting.fromEmail
                        }
                        val nickname = msgInfo.getTitleForSend(setting.nickname, "", rule?.title ?: "")
                        setting.recipients.ifEmpty {
                            //Compatible old settings
                            val emails = setting.toEmail.replace("[,，;；]".toRegex(), ",").trim(',').split(',')
                            emails.forEach {
                                setting.recipients[it] = Pair("", "")
                            }
                        }
                        val content = message.replace("\n", "<br>")
                        val openSSL = setting.ssl
                        val startTls = setting.startTls

                        //Number and interval of failed retries (sets for retries of failed requests interfaces）
                        val retryTimes = SettingUtils.requestRetryTimes
                        val delayTime = SettingUtils.requestDelayTime

                        if (EmailCrypto.isEncryptionMode(setting.encryptionProtocol)) {
                            //S/MIME, OpenPGP, OpenKeychain: explicit per-recipient outcome; unencrypted only by explicit fallback
                            val outcomes = deliverEncrypted(setting, host, port, from, password, fromAlias, nickname, title, content, openSSL, startTls, retryTimes, delayTime)
                            val status = EmailDeliveryReport.status(outcomes)
                            val summary = EmailDeliveryReport.describe(outcomes) { id, args -> App.context.getString(id, *args) }
                            Log.i(TAG, "Email delivery status=$status, recipients=${outcomes.size}, unencrypted=${outcomes.count { it is EmailOutcome.Unencrypted }}, failed=${outcomes.count { it is EmailOutcome.Failed }}")
                            SendUtils.updateLogs(logId, status, if (status == 2) getString(R.string.request_succeeded) + ": " + summary else summary)
                            //Test button (no log entry): a success toast alone would hide that a copy went out unencrypted
                            if ((logId == 0L) && outcomes.any { it is EmailOutcome.Unencrypted }) {
                                LiveEventBus.get(EVENT_TOAST_WARNING, String::class.java).post(summary)
                            }
                            SendUtils.senderLogic(status, msgInfo, rule, senderIndex, msgId)
                            return@launch
                        }

                        // Send a result monitor
                        val listener = object : EmailSender.EmailTaskListener {
                            override fun onEmailSent(success: Boolean, message: String) {
                                if (success) {
                                    SendUtils.updateLogs(logId, 2, getString(R.string.request_succeeded) + ": " + message)
                                    SendUtils.senderLogic(2, msgInfo, rule, senderIndex, msgId)
                                } else {
                                    val status = 0
                                    SendUtils.updateLogs(logId, status, message)
                                    SendUtils.senderLogic(status, msgInfo, rule, senderIndex, msgId)
                                }
                            }
                        }

                        //Intercept each send result; retry after a delay on failure; report only the final result to listener
                        suspend fun sendWithRetry(build: (EmailSender.EmailTaskListener) -> EmailSender) {
                            var attempt = 0
                            var success = false
                            var resultMessage = ""
                            while (true) {
                                val innerListener = object : EmailSender.EmailTaskListener {
                                    override fun onEmailSent(s: Boolean, message: String) {
                                        success = s
                                        resultMessage = message
                                    }
                                }
                                build(innerListener).sendEmail()
                                if (success || attempt >= retryTimes) break
                                attempt++
                                Log.w(TAG, "Email sending failed; preparing retry $attempt/$retryTimes, delay ${delayTime}s")
                                if (delayTime > 0) delay(delayTime * 1000L)
                            }
                            listener.onEmailSent(success, resultMessage)
                        }

                        // Explicitly unencrypted mode uses all configured recipients, regardless of retained keys.
                        val plainRecipients = plainModeRecipients(setting.recipients)
                        if (plainRecipients.isNotEmpty()) sendWithRetry { emailListener ->
                            EmailSender(
                                host,
                                port,
                                from,
                                password,
                                fromAlias,
                                nickname,
                                title,
                                content,
                                toAddress = plainRecipients,
                                listener = emailListener,
                                openSSL = openSSL,
                                startTls = startTls,
                                encryptionProtocol = setting.encryptionProtocol,
                                smtpTimeoutMs = EmailSender.smtpTimeoutMillis(SettingUtils.requestTimeout),
                            )
                        }

                    } catch (e: Exception) {
                        e.printStackTrace()
                        Log.e(TAG, e.message.toString())
                        val status = 0
                        SendUtils.updateLogs(logId, status, e.message.toString())
                        SendUtils.senderLogic(status, msgInfo, rule, senderIndex, msgId)
                    }
                }
                job.join() // Wait for coroutines to finish
            }

        }



        /** Explicit Unencrypted mode addresses every configured recipient. Retained keys are
         * inactive configuration, not a reason to silently omit a recipient. Encrypted modes use
         * deliverEncrypted and its separate, default-off fallback policy instead.
         */
        @JvmStatic
        fun plainModeRecipients(recipients: Map<String, Pair<String, String>>): MutableList<String> =
            recipients.keys.toMutableList()

        /**
         * S/MIME, OpenPGP and OpenKeychain delivery. Keys are loaded and messages built first; an unencrypted copy
         * is only sent for recipients whose encryption could not be prepared and only with
         * [EmailSetting.allowUnencryptedFallback]. SMTP retries resend the same prepared message.
         */
        private suspend fun deliverEncrypted(
            setting: EmailSetting, host: String, port: String, from: String, password: String, fromAlias: String,
            nickname: String, title: String, content: String, openSSL: Boolean, startTls: Boolean, retryTimes: Int, delayTime: Int,
        ): List<EmailOutcome> {
            val protocol = setting.encryptionProtocol
            val properties = EmailSender.smtpProperties(host, port, openSSL, startTls, EmailSender.smtpTimeoutMillis(SettingUtils.requestTimeout))
            val authenticator = EmailSender.authenticator(from, password)
            val signing = EmailKeys.loadSigningKey(protocol, setting.keystore, setting.password)
            if (signing is SigningKey.Failed) Log.w(TAG, "Signing key not usable: ${signing.failure} (${signing.detail})")
            val smimeSigning = signing as? SigningKey.Smime
            val pgpSigning = signing as? SigningKey.Pgp
            val recipients = setting.recipients.map { (email, cert) ->
                val key = if (protocol == EmailCrypto.OPENKEYCHAIN) RecipientKey.None else EmailKeys.loadRecipientKey(protocol, cert.first, cert.second)
                if (key is RecipientKey.Invalid) Log.w(TAG, "Recipient key not usable: ${key.failure} (${key.detail})")
                email to key
            }

            fun smime(emails: List<String>, certificate: X509Certificate?) = SmimeUtils(
                properties, authenticator, from, fromAlias, nickname, title, content,
                toAddress = emails.toMutableList(), recipientX509Cert = certificate,
                senderPrivateKey = smimeSigning?.privateKey, senderX509Cert = smimeSigning?.certificate,
            )

            fun pgp(emails: List<String>, ring: PGPPublicKeyRing?) = PgpUtils(
                properties = properties, session = Session.getInstance(properties, authenticator), from = from,
                fromAlias = fromAlias, nickname = nickname, subject = title, body = content, toAddress = emails,
                recipientPGPPublicKeyRing = ring, senderPGPSecretKeyRing = pgpSigning?.secretKeyRing,
                senderPGPSecretKeyPassword = pgpSigning?.password ?: "",
            )

            val openKeychainHelper = if (protocol == EmailCrypto.OPENKEYCHAIN) OpenKeychainHelper(App.context) else null
            try {
                val delivery = EncryptedEmailDelivery(
                    protocol = protocol,
                    allowUnencryptedFallback = setting.allowUnencryptedFallback,
                    signing = signing,
                    retryTimes = retryTimes,
                    retryDelayMillis = delayTime * 1000L,
                    prepareEncrypted = { emails, key ->
                        when (key) {
                            is RecipientKey.Smime -> smime(emails, key.certificate).prepareMessage(encrypt = true, sign = smimeSigning != null)
                            is RecipientKey.Pgp -> pgp(emails, key.publicKeyRing).prepareMessage(encrypt = true, sign = pgpSigning != null)
                            else -> OpenKeychainMailUtils(
                                session = Session.getInstance(properties, authenticator), fromAlias = fromAlias, nickname = nickname,
                                subject = title, body = content, toAddress = emails,
                                openKeychainHelper = openKeychainHelper ?: throw IllegalStateException("OpenKeychain not bound"),
                                signKeyId = setting.openKeychainSignKeyId,
                            ).prepareMessage()
                        }
                    },
                    prepareUnencrypted = { emails ->
                        when {
                            smimeSigning != null -> smime(emails, null).prepareMessage(encrypt = false, sign = true)
                            pgpSigning != null -> pgp(emails, null).prepareMessage(encrypt = false, sign = true)
                            else -> smime(emails, null).prepareMessage(encrypt = false, sign = false)
                        }
                    },
                    send = { message -> Transport.send(message) },
                    openKeychainSigned = setting.openKeychainSignKeyId != 0L,
                )
                return delivery.deliver(recipients)
            } finally {
                openKeychainHelper?.unbind()
            }
        }

    }
}
