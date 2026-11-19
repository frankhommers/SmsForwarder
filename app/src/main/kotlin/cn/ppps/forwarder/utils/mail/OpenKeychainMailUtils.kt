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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Date

/**
 * Use OpenKeychain（OpenPGP API）Encryption/Sign and send mail
 *
 * MimeMessage Build with PGP/MIME Cover Logic and PgpUtils consistent，
 * The difference is encryption./Signature by OpenKeychain Completed (this application does not touch key materials)），
 * To Public Keys OpenKeychain by email address（User ID）Automatically match.
 */
@Suppress("PrivatePropertyName")
class OpenKeychainMailUtils(
    private val session: Session,
    // Mail Parameters
    private val fromAlias: String, // Sender Mailbox Alias(⚠️Attention.: If you need to encrypt/Signature, suggest real mailboxes to avoid the recipient not being able to verify the signature)
    private val nickname: String, // Sender Nickname
    private val subject: String, // Email subject
    private val body: String, // Email Body
    private val attachFiles: List<File> = emptyList(), // Attachment
    // Recipient Arguments
    private val toAddress: List<String> = emptyList(), // Recipient Mailbox
    private val ccAddress: List<String> = emptyList(), // CC recipient address
    private val bccAddress: List<String> = emptyList(), // BCC recipient address
    // OpenKeychain
    private val openKeychainHelper: OpenKeychainHelper,
    private val signKeyId: Long = 0L, // Sign KeyID，0Means that only encryption does not sign
) {
    private val TAG: String = OpenKeychainMailUtils::class.java.simpleName

    /** Send encrypted (optional) mail with public key byOpenKeychainAutomatic Matching */
    fun sendEncryptedEmail(): Pair<Boolean, String> = try {
        if (toAddress.isEmpty()) throw IllegalArgumentException(cn.ppps.forwarder.App.context.getString(cn.ppps.forwarder.R.string.copy_recipient_required))
        val message = buildOriginalMessage()
        applyOpenKeychainToOriginalMessage(message)
        Transport.send(message)
        val signed = if (signKeyId != 0L) "signed and " else ""
        Pair(true, cn.ppps.forwarder.App.context.getString(cn.ppps.forwarder.R.string.copy_email_sent))
    } catch (e: Exception) {
        e.printStackTrace()
        Pair(false, cn.ppps.forwarder.App.context.getString(cn.ppps.forwarder.R.string.copy_email_failed, e.message ?: e.toString()))
    }

    /** Build (without sending) the message encrypted by OpenKeychain for all recipients. Throws on failure. */
    fun prepareMessage(): MimeMessage {
        if (toAddress.isEmpty()) throw IllegalArgumentException(cn.ppps.forwarder.App.context.getString(cn.ppps.forwarder.R.string.copy_recipient_required))
        val message = buildOriginalMessage()
        applyOpenKeychainToOriginalMessage(message)
        return message
    }

    /** Build Original MimeMessage（with PgpUtils.buildOriginalMessage consistent） */
    private fun buildOriginalMessage(): MimeMessage {
        val message = MimeMessage(session)
        message.setFrom(InternetAddress(fromAlias, nickname, "UTF-8"))
        if (toAddress.isNotEmpty()) {
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toAddress.joinToString(",")))
        }
        if (ccAddress.isNotEmpty()) {
            message.setRecipients(Message.RecipientType.CC, InternetAddress.parse(ccAddress.joinToString(",")))
        }
        if (bccAddress.isNotEmpty()) {
            message.setRecipients(Message.RecipientType.BCC, InternetAddress.parse(bccAddress.joinToString(",")))
        }
        message.subject = MimeUtility.encodeText(subject, "UTF-8", null)
        message.sentDate = Date()

        val multipart = MimeMultipart("mixed")

        val bodyPart = MimeBodyPart()
        bodyPart.setContent(body, "text/html;charset=UTF-8")
        multipart.addBodyPart(bodyPart)

        attachFiles.forEach { file ->
            val filePart = MimeBodyPart()
            val fileDs = FileDataSource(file)
            filePart.dataHandler = DataHandler(fileDs)
            filePart.fileName = MimeUtility.encodeText(file.name, "UTF-8", "B")
            multipart.addBodyPart(filePart)
        }

        message.setContent(multipart)
        message.saveChanges()
        return message
    }

    /**
     * Original MimeMessage delegate to OpenKeychain Encryption/Signature, replace with PGP/MIME(multipart/encrypted) Structure，
     * Keep all original headers (with) PgpUtils.applyPgpToOriginalMessage Same cover code.）
     */
    private fun applyOpenKeychainToOriginalMessage(message: MimeMessage) {
        // 1. Read OriginalmessageFull bytestream
        val originalBaos = ByteArrayOutputStream()
        message.writeTo(originalBaos)
        val originalInput = ByteArrayInputStream(originalBaos.toByteArray())

        // 2. delegate to OpenKeychain Encryption (optional signature), matching public key by recipient mailbox
        val pgpBaos = ByteArrayOutputStream()
        openKeychainHelper.encrypt(toAddress.toTypedArray(), signKeyId, originalInput, pgpBaos)

        // 3. BuildPGP/MIMEStandardmultipart/encryptedStructure
        val pgpMultipart = MimeMultipart("encrypted; protocol=\"application/pgp-encrypted\"")

        val versionPart = MimeBodyPart().apply {
            setText("Version: 1")
            setHeader("Content-Type", "application/pgp-encrypted")
            setHeader("Content-Description", "PGP/MIME version identification")
        }

        val contentPart = MimeBodyPart().apply {
            val dataSource = ByteArrayDataSource(pgpBaos.toByteArray(), "application/octet-stream")
            dataHandler = DataHandler(dataSource)
            setHeader("Content-Type", "application/octet-stream; name=\"encrypted.asc\"")
            setHeader("Content-Description", "OpenPGP encrypted/signed message")
            setHeader("Content-Disposition", "inline; filename=\"encrypted.asc\"")
        }

        pgpMultipart.addBodyPart(versionPart)
        pgpMultipart.addBodyPart(contentPart)

        // 4. Replace Originalmessage, keep all original headers
        message.setContent(pgpMultipart)
        message.saveChanges()
    }
}
