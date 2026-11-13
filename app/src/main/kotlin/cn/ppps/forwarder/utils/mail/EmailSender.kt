package cn.ppps.forwarder.utils.mail

import android.text.Html
import android.text.Spanned
import cn.ppps.forwarder.utils.Log
import com.sun.mail.util.MailSSLSocketFactory
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
) {

    private val TAG: String = EmailSender::class.java.simpleName

    private val properties: Properties = Properties().apply {
        // Set hostname for the mail server
        put("mail.smtp.host", host)
        // Set port number for the mail server
        put("mail.smtp.port", port)
        // Set whether authentication is required
        put("mail.smtp.auth", "true")
        // Settings Enabled SSL Connection
        if (openSSL) {
            put("mail.smtp.ssl.enable", "true")
            // Use TLSv1.2 Agreements & Trust all hosts
            val sf = MailSSLSocketFactory("TLSv1.2")
            sf.setTrustedHosts("*")
            put("mail.smtp.ssl.socketFactory", sf)
            put("mail.smtp.ssl.protocols", "TLSv1.2")
        }
        // Settings Enabled TLS Connection
        if (startTls) {
            put("mail.smtp.starttls.enable", "true")
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
                        listener?.onEmailSent(false, "OpenKeychainHelper is null")
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
            listener?.onEmailSent(false, "Error sending email: ${e.message}")
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
