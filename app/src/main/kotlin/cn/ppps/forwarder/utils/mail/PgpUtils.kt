package cn.ppps.forwarder.utils.mail

import jakarta.activation.DataHandler
import jakarta.activation.FileDataSource
import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import jakarta.mail.internet.MimeUtility
import jakarta.mail.util.ByteArrayDataSource
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.pgpainless.PGPainless
import org.pgpainless.algorithm.DocumentSignatureType
import org.pgpainless.algorithm.HashAlgorithm
import org.pgpainless.encryption_signing.EncryptionOptions
import org.pgpainless.encryption_signing.ProducerOptions
import org.pgpainless.encryption_signing.SigningOptions
import org.pgpainless.key.protection.SecretKeyRingProtector
import org.pgpainless.util.Passphrase
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.Security
import java.util.Date
import java.util.Properties

@Suppress("unused", "PrivatePropertyName")
class PgpUtils(
    private val properties: Properties,
    private val session: Session,
    // Mail Parameters
    private val from: String, // Sender Mailbox
    private val fromAlias: String, // Sender Mailbox Alias(⚠️Attention.: If you need to encrypt/Signature, suggest real mailboxes to avoid the recipient not being able to verify the signature)
    private val nickname: String, // Sender Nickname
    private val subject: String, // Email subject
    private val body: String, // Email Body
    private val attachFiles: List<File> = emptyList(), // Attachment
    // Recipient Arguments
    private val toAddress: List<String> = emptyList(), // Recipient Mailbox
    private val ccAddress: List<String> = emptyList(), // CC recipient address
    private val bccAddress: List<String> = emptyList(), // BCC recipient address
    //Mail PGP Encryption and Signature
    private val recipientPGPPublicKeyRing: PGPPublicKeyRing? = null, // Recipient public key (for encryption)）
    private val senderPGPSecretKeyRing: PGPSecretKeyRing? = null, // Sender Private Key (for Signing)）
    private val senderPGPSecretKeyPassword: String = "" // Sender 's Private Key Password
) {
    private val TAG: String = PgpUtils::class.java.simpleName

    init {
        // EnsuringBouncyCastleAdd only once to avoid adding anomalies
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    /** Send plaintext email */
    fun sendPlainEmail(): Pair<Boolean, String> = try {
        val message = buildOriginalMessage()
        Transport.send(message)
        Pair(true, "Email sent successfully")
    } catch (e: Exception) {
        e.printStackTrace()
        Pair(false, "Failed to send email: ${e.message ?: e.toString()}")
    }

    /** Send signed email */
    fun sendSignedEmail(): Pair<Boolean, String> = try {
        checkPgpSignParams() // Parameters necessary to verify signature
        val message = buildOriginalMessage()
        signMessage(message) // Directly modify originalmessage，Do Not Reuse New
        Transport.send(message)
        Pair(true, "Signed email sent successfully")
    } catch (e: Exception) {
        e.printStackTrace()
        Pair(false, "Failed to send signed email: ${e.message ?: e.toString()}")
    }

    /** Send Encrypted Email */
    fun sendEncryptedEmail(): Pair<Boolean, String> = try {
        checkPgpEncParams() // Validate the parameters necessary for encryption
        val message = buildOriginalMessage()
        encryptMessage(message)
        Transport.send(message)
        Pair(true, "Encrypted email sent successfully")
    } catch (e: Exception) {
        e.printStackTrace()
        Pair(false, "Failed to send encrypted email: ${e.message ?: e.toString()}")
    }

    /** Send signed+encrypted email */
    fun sendSignedAndEncryptedEmail(): Pair<Boolean, String> = try {
        checkPgpSignParams()
        checkPgpEncParams()
        val message = buildOriginalMessage()
        encryptAndSignMessage(message)
        Transport.send(message)
        Pair(true, "Signed and encrypted email sent successfully")
    } catch (e: Exception) {
        e.printStackTrace()
        Pair(false, "Failed to send signed+encrypted email: ${e.message ?: e.toString()}")
    }

    /** Build Original MimeMessage */
    private fun buildOriginalMessage(): MimeMessage {
        val message = MimeMessage(session)
        // From: UsefromAlias（alias), retaining the real underlyingfrom，Encode Unicode display names correctly
        message.setFrom(InternetAddress(fromAlias, nickname, "UTF-8"))
        // To: Handle empty lists, avoid empty string anomalies
        if (toAddress.isNotEmpty()) {
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toAddress.joinToString(",")))
        }
        if (ccAddress.isNotEmpty()) {
            message.setRecipients(Message.RecipientType.CC, InternetAddress.parse(ccAddress.joinToString(",")))
        }
        if (bccAddress.isNotEmpty()) {
            message.setRecipients(Message.RecipientType.BCC, InternetAddress.parse(bccAddress.joinToString(",")))
        }
        // Encode subject to prevent Unicode corruption
        message.subject = MimeUtility.encodeText(subject, "UTF-8", null)
        message.sentDate = Date()

        // Only in Subtype "mixed"，MatchJavaMailConstructor Code
        val multipart = MimeMultipart("mixed")

        val bodyPart = MimeBodyPart()
        bodyPart.setContent(body, "text/html;charset=UTF-8")
        multipart.addBodyPart(bodyPart)

        // Attachment Processing: Optimizing coding to avoid uncoded files
        attachFiles.forEach { file ->
            val filePart = MimeBodyPart()
            val fileDs = FileDataSource(file)
            filePart.dataHandler = DataHandler(fileDs)
            // Fix: useMimeUtility.encodeTextProcess file names, compatible with all clients
            filePart.fileName = MimeUtility.encodeText(file.name, "UTF-8", "B")
            multipart.addBodyPart(filePart)
        }

        message.setContent(multipart)
        message.saveChanges() // Save changes to generate original bytes
        return message
    }

    /** Signed email */
    private fun signMessage(message: MimeMessage) {
        val protector = SecretKeyRingProtector.unlockAnyKeyWith(Passphrase.fromPassword(senderPGPSecretKeyPassword))
        val secretKeyRing = senderPGPSecretKeyRing!!
        // Secure access to first userID，Avoid null pointers
        val userId = secretKeyRing.secretKey.publicKey.userIDs.asSequence().firstOrNull()
            ?: throw IllegalArgumentException("PGPNo user found in private keyringID")

        // UseBINARY_DOCUMENTAdaptive CompositeMIMEContent (binary flow), without text standardization
        val signingOptions = SigningOptions().addInlineSignature(
            protector,
            secretKeyRing,
            userId,
            DocumentSignatureType.BINARY_DOCUMENT
        ).overrideHashAlgorithm(HashAlgorithm.SHA256)

        // Direct to OriginalmessageApplyPGPSign, replace its contents
        applyPgpToOriginalMessage(message, ProducerOptions.sign(signingOptions))
    }

    /** encrypted email */
    private fun encryptMessage(message: MimeMessage) {
        val encryptionOptions = EncryptionOptions.encryptCommunications()
            .addRecipient(recipientPGPPublicKeyRing!!)
        applyPgpToOriginalMessage(message, ProducerOptions.encrypt(encryptionOptions))
    }

    /** Encryption+Signed email */
    private fun encryptAndSignMessage(message: MimeMessage) {
        val protector = SecretKeyRingProtector.unlockAnyKeyWith(Passphrase.fromPassword(senderPGPSecretKeyPassword))
        val secretKeyRing = senderPGPSecretKeyRing!!
        val userId = secretKeyRing.secretKey.publicKey.userIDs.asSequence().firstOrNull()
            ?: throw IllegalArgumentException("PGPNo user found in private keyringID")

        val signingOptions = SigningOptions().addInlineSignature(
            protector,
            secretKeyRing,
            userId,
            DocumentSignatureType.BINARY_DOCUMENT
        ).overrideHashAlgorithm(HashAlgorithm.SHA256)

        val encryptionOptions = EncryptionOptions.encryptCommunications()
            .addRecipient(recipientPGPPublicKeyRing!!)

        applyPgpToOriginalMessage(message, ProducerOptions.signAndEncrypt(encryptionOptions, signingOptions))
    }

    /**
     * ApplyPGPOperation To**OriginalMimeMessage**，Replace content only and keep all original headers
     * Ensure signed bytes exactly match transmitted bytes
     */
    private fun applyPgpToOriginalMessage(message: MimeMessage, options: ProducerOptions) {
        // 1. Read OriginalmessageFull byte stream (signature)/Encryption Original Contents）
        val originalBaos = ByteArrayOutputStream()
        message.writeTo(originalBaos)
        val originalInput = ByteArrayInputStream(originalBaos.toByteArray())

        // 2. ExecutePGPSignature/Encryption Operations, GeneratePGPHandled bytes
        val pgpBaos = ByteArrayOutputStream()
        val pgpStream = PGPainless.encryptAndOrSign().onOutputStream(pgpBaos).withOptions(options)
        originalInput.copyTo(pgpStream)
        pgpStream.close()

        // 3. BuildPGP/MIMEStandardmultipart/encryptedStructure
        val pgpMultipart = MimeMultipart("encrypted; protocol=\"application/pgp-encrypted\"")

        // 3.1 PGPVersion Identification Part（PGP/MIMENormative requirements must）
        val versionPart = MimeBodyPart().apply {
            setText("Version: 1")
            setHeader("Content-Type", "application/pgp-encrypted")
            setHeader("Content-Description", "PGP/MIME version identification")
        }

        // 3.2 PGPEncryption/Signing content section
        val contentPart = MimeBodyPart().apply {
            val dataSource = ByteArrayDataSource(pgpBaos.toByteArray(), "application/octet-stream")
            dataHandler = DataHandler(dataSource)
            setHeader("Content-Type", "application/octet-stream; name=\"encrypted.asc\"")
            setHeader("Content-Description", "OpenPGP encrypted/signed message")
            setHeader("Content-Disposition", "inline; filename=\"encrypted.asc\"")
        }

        pgpMultipart.addBodyPart(versionPart)
        pgpMultipart.addBodyPart(contentPart)

        // 4. Replace OriginalmessageThe content of this article is:PGP/MIMEStructure, keep all original headers
        message.setContent(pgpMultipart)
        message.saveChanges() // Save content changes only, do not recreate header information
    }

    // ValidationPGPValidate signing parameters early to prevent null-pointer errors
    private fun checkPgpSignParams() {
        if (senderPGPSecretKeyRing == null) {
            throw IllegalArgumentException("SenderPGPPrivate Keyring（senderPGPSecretKeyRing）Can't be empty.")
        }
        if (senderPGPSecretKeyPassword.isBlank()) {
            throw IllegalArgumentException("SenderPGPPrivate key password（senderPGPSecretKeyPassword）Can't be empty.")
        }
    }

    // ValidationPGPEncryption Required Parameters
    private fun checkPgpEncParams() {
        if (recipientPGPPublicKeyRing == null) {
            throw IllegalArgumentException("ReceiverPGPPublic Keyring（recipientPGPPublicKeyRing）Can't be empty.")
        }
    }
}