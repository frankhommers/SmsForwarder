package cn.ppps.forwarder.utils.mail

import cn.ppps.forwarder.R
import cn.ppps.forwarder.utils.Base64
import jakarta.mail.internet.MimeMessage
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.pgpainless.PGPainless
import org.pgpainless.key.info.KeyRingInfo
import org.pgpainless.key.protection.UnlockSecretKey
import org.pgpainless.util.Passphrase
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.io.InputStream
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * Encryption policy for email targets (S/MIME, OpenPGP, OpenKeychain).
 *
 * - A recipient whose key is missing, unreadable or unusable, or whose message cannot be encrypted, gets nothing
 *   unless [cn.ppps.forwarder.entity.setting.EmailSetting.allowUnencryptedFallback] is on. With it on, that
 *   recipient gets a normal (unencrypted) copy, and the outcome says so.
 * - Fallback only covers encryption preparation. Once an encrypted message is prepared, SMTP failures are retried
 *   as that same encrypted message and are never turned into an unencrypted copy.
 * - A configured signing key that cannot be loaded stops the delivery; it is never silently left out.
 * - Key material, passwords and message text are never put in outcomes or logs (only exception class names).
 */
object EmailCrypto {
    const val PLAIN = "Plain"
    const val SMIME = "S/MIME"
    const val OPENPGP = "OpenPGP"
    const val OPENKEYCHAIN = "OpenKeychain"

    @JvmStatic
    fun isEncryptionMode(protocol: String?): Boolean = protocol == SMIME || protocol == OPENPGP || protocol == OPENKEYCHAIN

    /** Stored key values are Base64 file contents (editor) or, in old settings, an absolute file path. */
    @JvmStatic
    fun openKeyStream(value: String): InputStream =
        if (value.startsWith("/")) FileInputStream(value) else ByteArrayInputStream(Base64.decode(value))
}

enum class EmailFailure {
    NO_RECIPIENT_KEY,
    BAD_RECIPIENT_KEY,
    UNUSABLE_RECIPIENT_KEY,
    ENCRYPTION_FAILED,
    SIGNING_KEY_FAILED,
    SIGNING_PASSWORD_MISSING,
    PREPARE_FAILED,
    SMTP_FAILED,
}

sealed class RecipientKey {
    object None : RecipientKey()
    class Smime(val certificate: X509Certificate) : RecipientKey()
    class Pgp(val publicKeyRing: PGPPublicKeyRing) : RecipientKey()
    class Invalid(val failure: EmailFailure, val detail: String) : RecipientKey()
}

sealed class SigningKey {
    object None : SigningKey()
    class Smime(val privateKey: PrivateKey, val certificate: X509Certificate) : SigningKey()
    class Pgp(val secretKeyRing: PGPSecretKeyRing, val password: String) : SigningKey()
    class Failed(val failure: EmailFailure, val detail: String) : SigningKey()
}

object EmailKeys {

    private fun detail(e: Throwable): String = e.javaClass.simpleName

    /** Recipient key for [protocol]. [keyData] empty = no key configured. Never throws. */
    @JvmStatic
    fun loadRecipientKey(protocol: String, keyData: String, password: String): RecipientKey {
        if (keyData.isEmpty()) return RecipientKey.None
        return try {
            when (protocol) {
                EmailCrypto.SMIME -> if (password.isNotEmpty()) {
                    // PKCS#12 file: use the certificate of its first entry
                    val keyStore = KeyStore.getInstance("PKCS12")
                    EmailCrypto.openKeyStream(keyData).use { keyStore.load(it, password.toCharArray()) }
                    val alias = keyStore.aliases().toList().firstOrNull { keyStore.getCertificate(it) != null }
                    val certificate = alias?.let { keyStore.getCertificate(it) as? X509Certificate }
                    if (certificate == null) RecipientKey.Invalid(EmailFailure.BAD_RECIPIENT_KEY, "no certificate") else RecipientKey.Smime(certificate)
                } else {
                    // Certificate only (PEM or DER): parse the decoded bytes, not the stored text as a file path
                    val certificate = EmailCrypto.openKeyStream(keyData).use {
                        CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
                    }
                    RecipientKey.Smime(certificate)
                }

                EmailCrypto.OPENPGP -> {
                    val ring: PGPPublicKeyRing? = EmailCrypto.openKeyStream(keyData).use { stream ->
                        if (password.isNotEmpty()) PGPainless.readKeyRing().secretKeyRing(stream)?.let { PGPainless.extractCertificate(it) }
                        else PGPainless.readKeyRing().publicKeyRing(stream)
                    }
                    when {
                        ring == null -> RecipientKey.Invalid(EmailFailure.BAD_RECIPIENT_KEY, "no key ring")
                        !KeyRingInfo(ring).isUsableForEncryption -> RecipientKey.Invalid(EmailFailure.UNUSABLE_RECIPIENT_KEY, "not usable for encryption")
                        else -> RecipientKey.Pgp(ring)
                    }
                }

                else -> RecipientKey.None
            }
        } catch (e: Throwable) {
            RecipientKey.Invalid(EmailFailure.BAD_RECIPIENT_KEY, detail(e))
        }
    }

    /** Own signing key for S/MIME or OpenPGP. [keystore] empty = no signing. Never throws. */
    @JvmStatic
    fun loadSigningKey(protocol: String, keystore: String, password: String): SigningKey {
        if (keystore.isEmpty() || (protocol != EmailCrypto.SMIME && protocol != EmailCrypto.OPENPGP)) return SigningKey.None
        return try {
            when (protocol) {
                EmailCrypto.SMIME -> {
                    val keyStore = KeyStore.getInstance("PKCS12")
                    EmailCrypto.openKeyStream(keystore).use { keyStore.load(it, password.toCharArray()) }
                    val alias = keyStore.aliases().toList().firstOrNull { keyStore.isKeyEntry(it) }
                        ?: return SigningKey.Failed(EmailFailure.SIGNING_KEY_FAILED, "no private key entry")
                    val privateKey = keyStore.getKey(alias, password.toCharArray()) as? PrivateKey
                    val certificate = keyStore.getCertificate(alias) as? X509Certificate
                    if (privateKey == null || certificate == null) SigningKey.Failed(EmailFailure.SIGNING_KEY_FAILED, "incomplete key entry")
                    else SigningKey.Smime(privateKey, certificate)
                }

                else -> {
                    if (password.isEmpty()) return SigningKey.Failed(EmailFailure.SIGNING_PASSWORD_MISSING, "password missing")
                    val ring = EmailCrypto.openKeyStream(keystore).use { PGPainless.readKeyRing().secretKeyRing(it) }
                        ?: return SigningKey.Failed(EmailFailure.SIGNING_KEY_FAILED, "no secret key ring")
                    val info = KeyRingInfo(ring)
                    if (!info.isUsableForSigning || !ring.secretKey.publicKey.userIDs.hasNext()) {
                        return SigningKey.Failed(EmailFailure.SIGNING_KEY_FAILED, "not usable for signing")
                    }
                    // Check the password now, so a wrong password is reported before anything is sent
                    val passphrase = Passphrase.fromPassword(password)
                    for (subkey in info.signingSubkeys) {
                        UnlockSecretKey.unlockSecretKey(ring.getSecretKey(subkey.keyID), passphrase)
                    }
                    SigningKey.Pgp(ring, password)
                }
            }
        } catch (e: Throwable) {
            SigningKey.Failed(EmailFailure.SIGNING_KEY_FAILED, detail(e))
        }
    }
}

sealed class EmailOutcome(val email: String) {
    class Encrypted(email: String, val protocol: String, val signed: Boolean) : EmailOutcome(email)
    class Unencrypted(email: String, val reason: EmailFailure, val detail: String, val signed: Boolean) : EmailOutcome(email)
    class Failed(email: String, val reason: EmailFailure, val detail: String) : EmailOutcome(email)
}

/**
 * Plans and sends one message for an encryption mode. Message building ([prepareEncrypted], [prepareUnencrypted])
 * and SMTP ([send]) are injected so the policy can be tested without a mail server.
 */
class EncryptedEmailDelivery(
    private val protocol: String,
    private val allowUnencryptedFallback: Boolean,
    private val signing: SigningKey,
    private val retryTimes: Int,
    private val retryDelayMillis: Long,
    /** Build the encrypted message for [emails] (one recipient, or all recipients for OpenKeychain). Throws on crypto errors. */
    private val prepareEncrypted: (emails: List<String>, key: RecipientKey) -> MimeMessage,
    /** Build the unencrypted fallback message (signed when a signing key is loaded). Throws on errors. */
    private val prepareUnencrypted: (emails: List<String>) -> MimeMessage,
    /** SMTP. Throws on delivery errors. */
    private val send: (MimeMessage) -> Unit,
    private val openKeychainSigned: Boolean = false,
    private val pause: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) {
    private class Pending(val email: String, val reason: EmailFailure, val detail: String)

    private val encryptedSigned = signing is SigningKey.Smime || signing is SigningKey.Pgp || (protocol == EmailCrypto.OPENKEYCHAIN && openKeychainSigned)
    private val unencryptedSigned = signing is SigningKey.Smime || signing is SigningKey.Pgp

    /** Number of SMTP attempts made (for tests and diagnostics). */
    var sendAttempts = 0
        private set

    private suspend fun sendWithRetry(message: MimeMessage): Throwable? {
        var attempt = 0
        while (true) {
            sendAttempts++
            val error = try {
                send(message); null
            } catch (e: Throwable) {
                e
            }
            if (error == null || attempt >= retryTimes) return error
            attempt++
            if (retryDelayMillis > 0) pause(retryDelayMillis)
        }
    }

    private fun smtpDetail(e: Throwable) = e.message ?: e.javaClass.simpleName

    suspend fun deliver(recipients: List<Pair<String, RecipientKey>>): List<EmailOutcome> {
        if (signing is SigningKey.Failed) {
            return recipients.map { EmailOutcome.Failed(it.first, signing.failure, signing.detail) }
        }
        val outcomes = linkedMapOf<String, EmailOutcome>()
        val fallback = mutableListOf<Pending>()

        fun encryptionFailed(email: String, reason: EmailFailure, detail: String) {
            if (allowUnencryptedFallback) fallback += Pending(email, reason, detail)
            else outcomes[email] = EmailOutcome.Failed(email, reason, detail)
        }

        suspend fun sendEncrypted(emails: List<String>, message: MimeMessage) {
            val error = sendWithRetry(message)
            for (email in emails) {
                outcomes[email] = if (error == null) EmailOutcome.Encrypted(email, protocol, encryptedSigned)
                else EmailOutcome.Failed(email, EmailFailure.SMTP_FAILED, smtpDetail(error))
            }
        }

        if (protocol == EmailCrypto.OPENKEYCHAIN) {
            // OpenKeychain encrypts to all recipients at once
            val emails = recipients.map { it.first }
            val message = try {
                prepareEncrypted(emails, RecipientKey.None)
            } catch (e: Throwable) {
                // OpenKeychain errors carry this app's own explanatory text (authorize / provider error), no key material
                emails.forEach { encryptionFailed(it, EmailFailure.ENCRYPTION_FAILED, e.message ?: e.javaClass.simpleName) }
                null
            }
            if (message != null) sendEncrypted(emails, message)
        } else {
            for ((email, key) in recipients) {
                when (key) {
                    is RecipientKey.None -> encryptionFailed(email, EmailFailure.NO_RECIPIENT_KEY, "")
                    is RecipientKey.Invalid -> encryptionFailed(email, key.failure, key.detail)
                    else -> {
                        val message = try {
                            prepareEncrypted(listOf(email), key)
                        } catch (e: Throwable) {
                            encryptionFailed(email, EmailFailure.ENCRYPTION_FAILED, e.javaClass.simpleName)
                            null
                        }
                        if (message != null) sendEncrypted(listOf(email), message)
                    }
                }
            }
        }

        if (fallback.isNotEmpty()) {
            val emails = fallback.map { it.email }
            val message = try {
                prepareUnencrypted(emails)
            } catch (e: Throwable) {
                fallback.forEach { outcomes[it.email] = EmailOutcome.Failed(it.email, if (unencryptedSigned) EmailFailure.SIGNING_KEY_FAILED else EmailFailure.PREPARE_FAILED, e.javaClass.simpleName) }
                null
            }
            if (message != null) {
                val error = sendWithRetry(message)
                for (pending in fallback) {
                    outcomes[pending.email] = if (error == null) EmailOutcome.Unencrypted(pending.email, pending.reason, pending.detail, unencryptedSigned)
                    else EmailOutcome.Failed(pending.email, EmailFailure.SMTP_FAILED, smtpDetail(error))
                }
            }
        }
        // Report in the configured recipient order
        return recipients.mapNotNull { outcomes[it.first] }
    }
}

/** Turns outcomes into one delivery-log status and text. */
object EmailDeliveryReport {
    /** 2 = every recipient got the message (encrypted, or unencrypted by explicit fallback); 0 = at least one did not. */
    @JvmStatic
    fun status(outcomes: List<EmailOutcome>): Int = if (outcomes.isNotEmpty() && outcomes.none { it is EmailOutcome.Failed }) 2 else 0

    @JvmStatic
    fun reasonText(reason: EmailFailure, detail: String, text: (Int, Array<out Any>) -> String): String = when (reason) {
        EmailFailure.NO_RECIPIENT_KEY -> text(R.string.email_reason_no_key, emptyArray())
        EmailFailure.BAD_RECIPIENT_KEY -> text(R.string.email_reason_bad_key, emptyArray())
        EmailFailure.UNUSABLE_RECIPIENT_KEY -> text(R.string.email_reason_unusable_key, emptyArray())
        EmailFailure.ENCRYPTION_FAILED -> text(R.string.email_reason_encryption_failed, arrayOf(detail))
        EmailFailure.SIGNING_KEY_FAILED -> text(R.string.email_reason_signing_failed, emptyArray())
        EmailFailure.SIGNING_PASSWORD_MISSING -> text(R.string.email_reason_signing_password, emptyArray())
        EmailFailure.PREPARE_FAILED -> text(R.string.email_reason_prepare_failed, arrayOf(detail))
        EmailFailure.SMTP_FAILED -> text(R.string.email_reason_smtp, arrayOf(detail))
    }

    @JvmStatic
    fun describe(outcomes: List<EmailOutcome>, text: (Int, Array<out Any>) -> String): String = outcomes.joinToString("\n") { outcome ->
        when (outcome) {
            is EmailOutcome.Encrypted -> text(if (outcome.signed) R.string.email_outcome_encrypted_signed else R.string.email_outcome_encrypted, arrayOf(outcome.email, outcome.protocol))
            is EmailOutcome.Unencrypted -> text(if (outcome.signed) R.string.email_outcome_unencrypted_signed else R.string.email_outcome_unencrypted, arrayOf(outcome.email, reasonText(outcome.reason, outcome.detail, text)))
            is EmailOutcome.Failed -> text(R.string.email_outcome_failed, arrayOf(outcome.email, reasonText(outcome.reason, outcome.detail, text)))
        }
    }
}
