package cn.ppps.forwarder.utils.mail

import android.text.Html
import android.text.Spanned
import cn.ppps.forwarder.utils.Log
import jakarta.mail.Authenticator
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPSecretKeyRing
import java.io.File
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Properties


@Suppress("PrivatePropertyName", "DEPRECATION")
class EmailSender(
    // SMTPParameters
    private val host: String, // SMTPServer Address
    private val port: String, // SMTPServer Port
    private val from: String, // Sender Mailbox
    private val password: String, // Sender Mailbox Password/Authorisation Code
    // Mail Parameters
    private val fromAlias: String, // Sender Mailbox Alias
    private val nickname: String, // Sender Nickname
    private val subject: String, // Email subject
    private val body: CharSequence, // Email Body
    private val attachFiles: MutableList<File> = mutableListOf(), // Attachment
    // Recipient Arguments
    private val toAddress: MutableList<String> = mutableListOf(), // Recipient Mailbox
    private val ccAddress: MutableList<String> = mutableListOf(), // CC recipient address
    private val bccAddress: MutableList<String> = mutableListOf(), // BCC recipient address
    // Listener
    private val listener: EmailTaskListener? = null,
    // Security Options
    private val openSSL: Boolean = false, //Whether to opensslAuthentication Default Closes
    private val startTls: Boolean = false, //Whether to openstarttlsEncryption by Default Close
    // Email encryption mode: S/MIME,OpenPGP,OpenKeychain,Plain（Do not provide a certificate）
    private val encryptionProtocol: String = "S/MIME",
    // Mail S/MIME Encryption and Signature
    private val recipientX509Cert: X509Certificate? = null, //Recipient public key (for encryption)）
    private val senderPrivateKey: PrivateKey? = null, //Sender's Private Code (for signature)）
    private val senderX509Cert: X509Certificate? = null, //Sender Code (for signature)）
    //Mail PGP Encryption and Signature
    private var recipientPGPPublicKeyRing: PGPPublicKeyRing? = null, // Recipient public key (for encryption)）
    private var senderPGPSecretKeyRing: PGPSecretKeyRing? = null, // Sender Private Key (for Signing)）
    private val senderPGPSecretKeyPassword: String = "", // Sender 's Private Key Password
    //Mail OpenKeychain Encryption and Signature (All Keys By)OpenKeychainManagement, matching public key by recipient mailbox）
    private val openKeychainHelper: OpenKeychainHelper? = null,
    private val openKeychainSignKeyId: Long = 0L, // Sign KeyID，0Means that only encryption does not sign
    // SMTP connect/read/write timeout in milliseconds (see smtpTimeoutMillis)
    private val smtpTimeoutMs: Int = DEFAULT_SMTP_TIMEOUT_MS,
) {

    private val TAG: String = EmailSender::class.java.simpleName

    private val properties: Properties = smtpProperties(host, port, openSSL, startTls, smtpTimeoutMs)

    companion object {
        /** SMTP login for [from]; used by the encryption policy path in EmailUtils. */
        @JvmStatic
        fun authenticator(from: String, password: String): Authenticator = MailAuthenticator(from, password)

        /** SMTP socket timeout used when no valid configured value is passed (matches the request timeout default). */
        const val DEFAULT_SMTP_TIMEOUT_MS = 10_000
        /** Upper bound for SMTP socket timeouts (10 minutes). */
        const val MAX_SMTP_TIMEOUT_MS = 600_000

        /**
         * Converts the configured request timeout in seconds to an SMTP socket timeout in milliseconds:
         * non-positive values fall back to [DEFAULT_SMTP_TIMEOUT_MS]; large values are capped without overflow.
         */
        @JvmStatic
        fun smtpTimeoutMillis(seconds: Int): Int =
            if (seconds <= 0) DEFAULT_SMTP_TIMEOUT_MS else (seconds.toLong() * 1000L).coerceAtMost(MAX_SMTP_TIMEOUT_MS.toLong()).toInt()

        /**
         * SMTP session properties. Certificate chains are validated against the platform trust store (Jakarta Mail
         * uses the default SSLSocketFactory when no custom factory is set) and the certificate must match [host].
         *
         * The former `MailSSLSocketFactory` with `setTrustedHosts("*")` is not used: in Jakarta Mail 2.0.2 a
         * non-null trusted-host list disables chain validation in its trust manager, and the literal "*" then
         * fails `isServerTrusted`, so the fallback default socket connected without any host-name check.
         *
         * [timeoutMs] bounds connect, read and write; non-positive values use the default, large values are capped.
         * Production callers pass `smtpTimeoutMillis(SettingUtils.requestTimeout)`.
         */
        @JvmStatic
        fun smtpProperties(
            host: String, port: String, openSSL: Boolean, startTls: Boolean,
            timeoutMs: Int = DEFAULT_SMTP_TIMEOUT_MS,
        ): Properties = Properties().apply {
            // Set hostname for the mail server
            put("mail.smtp.host", host)
            // Set port number for the mail server
            put("mail.smtp.port", port)
            // Set whether authentication is required
            put("mail.smtp.auth", "true")
            // Bounded socket I/O: Jakarta Mail waits indefinitely by default (connect, greeting/replies and writes).
            val timeout = (if (timeoutMs <= 0) DEFAULT_SMTP_TIMEOUT_MS else timeoutMs.coerceAtMost(MAX_SMTP_TIMEOUT_MS)).toString()
            put("mail.smtp.connectiontimeout", timeout)
            put("mail.smtp.timeout", timeout)
            put("mail.smtp.writetimeout", timeout)
            // Implicit TLS (SMTPS)
            if (openSSL) {
                put("mail.smtp.ssl.enable", "true")
                put("mail.smtp.ssl.protocols", "TLSv1.2")
                put("mail.smtp.ssl.checkserveridentity", "true")
            }
            // STARTTLS: when explicitly enabled, require the upgrade instead of silently continuing in plaintext
            // (with credentials), and verify the server name in the certificate.
            if (startTls) {
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.starttls.required", "true")
                put("mail.smtp.ssl.checkserveridentity", "true")
            }
        }
    }

    suspend fun sendEmail() {
        try {
            val authenticator = MailAuthenticator(from, password)
            // Email Body
            val html = try {
                if (body is Spanned) Html.toHtml(body) else body.toString()
            } catch (e: Exception) {
                body.toString()
            }

            // Send S/MIME Mail
            when (encryptionProtocol) {
                "S/MIME" -> {
                    val smimeUtils = SmimeUtils(
                        properties,
                        authenticator,
                        from,
                        fromAlias,
                        nickname,
                        subject,
                        html,
                        attachFiles,
                        toAddress,
                        ccAddress,
                        bccAddress,
                        recipientX509Cert,
                        senderPrivateKey,
                        senderX509Cert,
                    )
                    val isEncrypt: Boolean = recipientX509Cert != null
                    val isSign: Boolean = senderX509Cert != null && senderPrivateKey != null
                    Log.d(TAG, "isEncrypt=$isEncrypt, isSign=$isSign")
                    val result = when {
                        isEncrypt && isSign -> smimeUtils.sendSignedAndEncryptedEmail()
                        isEncrypt -> smimeUtils.sendEncryptedEmail()
                        isSign -> smimeUtils.sendSignedEmail()
                        else -> smimeUtils.sendPlainEmail()
                    }
                    listener?.onEmailSent(result.first, result.second)
                }

                "OpenPGP" -> {
                    // Send PGP Mail
                    val pgpEmail = PgpUtils(
                        properties = properties,
                        session = Session.getInstance(properties, authenticator),
                        from = from,
                        fromAlias = fromAlias,
                        nickname = nickname,
                        subject = subject,
                        body = html,
                        attachFiles = attachFiles,
                        toAddress = toAddress,
                        ccAddress = ccAddress,
                        bccAddress = bccAddress,
                        recipientPGPPublicKeyRing = recipientPGPPublicKeyRing,
                        senderPGPSecretKeyRing = senderPGPSecretKeyRing,
                        senderPGPSecretKeyPassword = senderPGPSecretKeyPassword
                    )

                    val isEncrypt = recipientPGPPublicKeyRing != null
                    val isSign = senderPGPSecretKeyRing != null
                    val result = when {
                        isEncrypt && isSign -> pgpEmail.sendSignedAndEncryptedEmail()
                        isEncrypt -> pgpEmail.sendEncryptedEmail()
                        isSign -> pgpEmail.sendSignedEmail()
                        else -> pgpEmail.sendPlainEmail()
                    }
                    listener?.onEmailSent(result.first, result.second)
                }

                "OpenKeychain" -> {
                    // via OpenKeychain（OpenPGP API）Encrypted Send. Public Key Automatically Matches Recipient Mailbox
                    if (openKeychainHelper == null) {
                        listener?.onEmailSent(false, cn.ppps.forwarder.App.context.getString(cn.ppps.forwarder.R.string.copy_openkeychain_bind, cn.ppps.forwarder.App.context.getString(cn.ppps.forwarder.R.string.copy_unknown_error)))
                        return
                    }
                    val openKeychainEmail = OpenKeychainMailUtils(
                        session = Session.getInstance(properties, authenticator),
                        fromAlias = fromAlias,
                        nickname = nickname,
                        subject = subject,
                        body = html,
                        attachFiles = attachFiles,
                        toAddress = toAddress,
                        ccAddress = ccAddress,
                        bccAddress = bccAddress,
                        openKeychainHelper = openKeychainHelper,
                        signKeyId = openKeychainSignKeyId,
                    )
                    val result = openKeychainEmail.sendEncryptedEmail()
                    listener?.onEmailSent(result.first, result.second)
                }

                else -> {
                    // Send Normal Email
                    val simpleEmail = SmimeUtils(
                        properties,
                        authenticator,
                        from,
                        fromAlias,
                        nickname,
                        subject,
                        html,
                        attachFiles,
                        toAddress,
                        ccAddress,
                        bccAddress,
                    )
                    val result = simpleEmail.sendPlainEmail()
                    listener?.onEmailSent(result.first, result.second)
                }
            }
        } catch (e: Exception) {
            listener?.onEmailSent(false, cn.ppps.forwarder.App.context.getString(cn.ppps.forwarder.R.string.copy_email_failed, e.message))
        }
    }

    interface EmailTaskListener {
        fun onEmailSent(success: Boolean, message: String)
    }

    /**
     * OutboxauthValidation
     */
    private class MailAuthenticator(username: String, private var password: String) : Authenticator() {
        private var userName: String? = username
        override fun getPasswordAuthentication(): PasswordAuthentication {
            return PasswordAuthentication(userName, password)
        }
    }
}
