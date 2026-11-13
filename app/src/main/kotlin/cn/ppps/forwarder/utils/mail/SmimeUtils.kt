package cn.ppps.forwarder.utils.mail

import cn.ppps.forwarder.utils.Log
import jakarta.activation.DataHandler
import jakarta.activation.FileDataSource
import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import jakarta.mail.internet.MimeUtility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bouncycastle.cert.jcajce.JcaCertStore
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder
import org.bouncycastle.cms.CMSAlgorithm
import org.bouncycastle.cms.CMSEnvelopedDataGenerator
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.cms.jcajce.JceCMSContentEncryptorBuilder
import org.bouncycastle.cms.jcajce.JceKeyTransRecipientInfoGenerator
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.OutputEncryptor
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.PrivateKey
import java.security.Security
import java.security.cert.X509Certificate
import java.util.Date
import java.util.Properties


@Suppress("PrivatePropertyName")
class SmimeUtils(
    private val properties: Properties,
    private val authenticator: Authenticator,
    // Mail Parameters
    private val from: String, // Sender Mailbox
    private val fromAlias: String, // Sender Mailbox Alias
    private val nickname: String, // Sender Nickname
    private val subject: String, // Email subject
    private val body: String, // Email Body
    private val attachFiles: MutableList<File> = mutableListOf(), // Attachment
    // Recipient Arguments
    private val toAddress: MutableList<String> = mutableListOf(), // Recipient Mailbox
    private val ccAddress: MutableList<String> = mutableListOf(), // CC recipient address
    private val bccAddress: MutableList<String> = mutableListOf(), // BCC recipient address
    // Mail S/MIME Encryption and Signature
    private val recipientX509Cert: X509Certificate? = null, //Recipient public key (for encryption)）
    private val senderPrivateKey: PrivateKey? = null, //Sender's Private Code (for signature)）
    private val senderX509Cert: X509Certificate? = null, //Sender Code (for signature)）
) {

    private val TAG: String = SmimeUtils::class.java.simpleName

    init {
        Security.addProvider(BouncyCastleProvider())
    }

    // Send plaintext email
    suspend fun sendPlainEmail(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        Log.d(TAG, "sendPlainEmail")
        try {
            val originalMessage = getOriginalMessage()
            Transport.send(originalMessage)
            Pair(true, "Email sent successfully")
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, "Failed to send email: ${e.message}")
        }
    }

    // Mail after sending signature
    suspend fun sendSignedEmail(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        Log.d(TAG, "sendSignedEmail")
        try {
            val originalMessage = getOriginalMessage()
            val signedMessage = getSignedMessage(originalMessage)
            Transport.send(signedMessage)
            Pair(true, "Email signed and sent successfully")
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, "Failed to sign and send email: ${e.message}")
        }
    }

    // Send Encrypted Email
    suspend fun sendEncryptedEmail(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        Log.d(TAG, "sendEncryptedEmail")
        try {
            val originalMessage = getOriginalMessage()
            val encryptedMessage = getEncryptedMessage(originalMessage)
            Transport.send(encryptedMessage)
            Pair(true, "Encrypted email sent successfully")
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, "Failed to send encrypted email: ${e.message}")
        }
    }

    // Send signed encrypted mail
    suspend fun sendSignedAndEncryptedEmail(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        Log.d(TAG, "sendSignedAndEncryptedEmail")
        try {
            val originalMessage = getOriginalMessage()
            val signedMessage = getSignedMessage(originalMessage)
            val encryptedMessage = getEncryptedMessage(signedMessage)
            Transport.send(encryptedMessage)
            Pair(true, "Signed and encrypted email sent successfully")
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, "Failed to send signed and encrypted email: ${e.message}")
        }
    }

    // Get Original Mail
    private fun getOriginalMessage(): MimeMessage {
        val session = Session.getInstance(properties, authenticator)
        session.debug = true
        val message = MimeMessage(session)
        // Set direct receiver inbox
        val toAddress = toAddress.map { InternetAddress(it) }.toTypedArray()
        message.setRecipients(Message.RecipientType.TO, toAddress)
        // Set inbox for copy-receivers
        val ccAddress = ccAddress.map { InternetAddress(it) }.toTypedArray()
        message.setRecipients(Message.RecipientType.CC, ccAddress)
        // Set BCC recipients
        val bccAddress = bccAddress.map { InternetAddress(it) }.toTypedArray()
        message.setRecipients(Message.RecipientType.BCC, bccAddress)
        // Set Outbox
        when {
            nickname.isEmpty() -> message.setFrom(InternetAddress(fromAlias))
            else -> try {
                var name = nickname.replace(":", "-").replace("\n", "-")
                name = MimeUtility.encodeText(name)
                message.setFrom(InternetAddress("$name <$fromAlias>"))
            } catch (e: Exception) {
                e.printStackTrace()
                message.setFrom(InternetAddress(fromAlias))
            }
        }
        // Email subject
        try {
            message.subject = MimeUtility.encodeText(subject.replace(":", "-").replace("\n", "-"))
        } catch (e: Exception) {
            e.printStackTrace()
            message.subject = subject
        }

        // Email Contents
        val contentPart = MimeMultipart("mixed")

        // Email Body
        val textBodyPart = MimeBodyPart()
        textBodyPart.setContent(body, "text/html;charset=UTF-8")
        contentPart.addBodyPart(textBodyPart)

        // Mail Attachments
        attachFiles.forEach {
            val fileBodyPart = MimeBodyPart()
            val ds = FileDataSource(it)
            val dh = DataHandler(ds)
            fileBodyPart.dataHandler = dh
            fileBodyPart.fileName = MimeUtility.encodeText(dh.name)
            contentPart.addBodyPart(fileBodyPart)
        }

        message.setContent(contentPart)
        message.sentDate = Date()
        message.saveChanges()
        return message
    }

    // Get signed mail
    private fun getSignedMessage(originalMessage: MimeMessage): MimeMessage {
        // Create Signer Information Generator
        val contentSigner = JcaContentSignerBuilder("SHA256withRSA").build(senderPrivateKey)
        val certificateHolder = JcaX509CertificateHolder(senderX509Cert)
        val signerInfoGenerator = JcaSignerInfoGeneratorBuilder(
            JcaDigestCalculatorProviderBuilder().setProvider(BouncyCastleProvider()).build()
        ).build(contentSigner, certificateHolder)

        // Create CMSSignedDataGenerator and add signer information and certificates
        val generator = CMSSignedDataGenerator()
        generator.addSignerInfoGenerator(signerInfoGenerator)
        val certStore = JcaCertStore(listOf(senderX509Cert))
        generator.addCertificates(certStore)

        // Convert email content to CMSSignedData
        val outputStream = ByteArrayOutputStream()
        originalMessage.writeTo(outputStream)
        val contentData = CMSProcessableByteArray(outputStream.toByteArray())
        val signedData = generator.generate(contentData, true)

        // Create MimeMessage and set up the contents after signing
        val signedMessage = MimeMessage(originalMessage.session, ByteArrayInputStream(signedData.encoded))
        /*
        //TODO: Why don't you have to set these up again?？
        signedMessage.setRecipients(Message.RecipientType.TO, originalMessage.getRecipients(Message.RecipientType.TO))
        signedMessage.setRecipients(Message.RecipientType.CC, originalMessage.getRecipients(Message.RecipientType.CC))
        signedMessage.setRecipients(Message.RecipientType.BCC, originalMessage.getRecipients(Message.RecipientType.BCC))
        signedMessage.addFrom(originalMessage.from)
        signedMessage.subject = originalMessage.subject
        signedMessage.sentDate = originalMessage.sentDate
        */
        signedMessage.setContent(signedData.encoded, "application/pkcs7-mime; name=smime.p7m; smime-type=signed-data")
        signedMessage.saveChanges()

        return signedMessage
    }

    // Get Encrypted Mail
    private fun getEncryptedMessage(originalMessage: MimeMessage): MimeMessage {
        // Encrypt with recipient 's certificate
        val cmsEnvelopedDataGenerator = CMSEnvelopedDataGenerator()
        val recipientInfoGenerator = JceKeyTransRecipientInfoGenerator(recipientX509Cert)
        cmsEnvelopedDataGenerator.addRecipientInfoGenerator(recipientInfoGenerator)

        // Use 3DES Encryption
        val outputEncryptor: OutputEncryptor = JceCMSContentEncryptorBuilder(CMSAlgorithm.DES_EDE3_CBC).build()
        val originalContent = ByteArrayOutputStream()
        originalMessage.writeTo(originalContent)
        val inputStream = originalContent.toByteArray()
        val cmsEnvelopedData = cmsEnvelopedDataGenerator.generate(
            CMSProcessableByteArray(inputStream),
            outputEncryptor
        )

        // Create encrypted email
        val encryptedMessage = MimeMessage(originalMessage.session)
        encryptedMessage.setRecipients(Message.RecipientType.TO, originalMessage.getRecipients(Message.RecipientType.TO))
        encryptedMessage.setRecipients(Message.RecipientType.CC, originalMessage.getRecipients(Message.RecipientType.CC))
        encryptedMessage.setRecipients(Message.RecipientType.BCC, originalMessage.getRecipients(Message.RecipientType.BCC))
        encryptedMessage.addFrom(originalMessage.from)
        encryptedMessage.subject = originalMessage.subject
        encryptedMessage.sentDate = originalMessage.sentDate
        encryptedMessage.setContent(cmsEnvelopedData.encoded, "application/pkcs7-mime; name=smime.p7m; smime-type=enveloped-data")
        encryptedMessage.setHeader("Content-Type", "application/pkcs7-mime; name=smime.p7m; smime-type=enveloped-data")
        encryptedMessage.setHeader("Content-Disposition", "attachment; filename=smime.p7m")
        encryptedMessage.setHeader("Content-Description", "S/MIME Encrypted Message")
        encryptedMessage.addHeader("Content-Transfer-Encoding", "base64")
        encryptedMessage.saveChanges()

        return encryptedMessage
    }

}
