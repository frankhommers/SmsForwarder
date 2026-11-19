package cn.ppps.forwarder.utils.mail

import cn.ppps.forwarder.R
import cn.ppps.forwarder.entity.setting.EmailSetting
import cn.ppps.forwarder.utils.Base64
import cn.ppps.forwarder.utils.sender.EmailUtils
import com.google.gson.Gson
import com.google.gson.JsonParser
import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.pgpainless.PGPainless
import java.io.ByteArrayOutputStream
import java.util.Properties

/**
 * Email encryption policy (owner decision: fallback toggle, default off). Local only: keys are fixtures generated
 * for this test (src/test/resources/email) or generated in memory; SMTP is a fake that never opens a socket.
 */
class EmailEncryptionPolicyTest {

    private fun fixture(name: String): String = Base64.encode(javaClass.getResourceAsStream("/email/$name")!!.readBytes())
    private val session: Session = Session.getInstance(Properties())
    private fun msg(tag: String) = MimeMessage(session).apply { subject = tag }

    /** Fake SMTP: records what was sent; fails the first [failFirst] attempts or always when [alwaysFail]. */
    private class FakeSmtp(var failFirst: Int = 0, val alwaysFail: Boolean = false) {
        val sent = mutableListOf<String>()
        var attempts = 0
        fun send(m: MimeMessage) {
            attempts++
            if (alwaysFail || attempts <= failFirst) throw jakarta.mail.MessagingException("fixture connection refused")
            sent += m.subject
        }
    }

    private fun delivery(
        protocol: String = EmailCrypto.SMIME,
        fallback: Boolean,
        smtp: FakeSmtp,
        signing: SigningKey = SigningKey.None,
        retries: Int = 0,
        encrypt: (List<String>, RecipientKey) -> MimeMessage = { e, _ -> msg("ENC:" + e.joinToString()) },
        plain: (List<String>) -> MimeMessage = { e -> msg("PLAIN:" + e.joinToString()) },
    ) = EncryptedEmailDelivery(protocol, fallback, signing, retries, 0L, encrypt, plain, smtp::send, pause = {})

    private val goodSmime get() = EmailKeys.loadRecipientKey(EmailCrypto.SMIME, fixture("rsa-recipient.pem"), "")

    // ---- Stored setting: absent / false / true, save and reopen ----

    @Test fun absentPropertyInOldJsonDefaultsOff() {
        val old = """{"mailType":"other","fromEmail":"a@example.invalid","recipients":{"r@example.invalid":{"first":"","second":""}},"encryptionProtocol":"S/MIME"}"""
        val setting = Gson().fromJson(old, EmailSetting::class.java)
        assertFalse(setting.allowUnencryptedFallback)
        assertEquals("S/MIME", setting.encryptionProtocol)
        assertFalse(EmailSetting().allowUnencryptedFallback)
    }

    @Test fun explicitFalseAndTrueRoundTripAndKeepUnknownData() {
        for (value in listOf(false, true)) {
            val stored = """{"encryptionProtocol":"OpenPGP","allowUnencryptedFallback":$value,"futureOption":{"x":1},"keystore":"","toEmail":""}"""
            val reopened = Gson().fromJson(stored, EmailSetting::class.java)
            assertEquals(value, reopened.allowUnencryptedFallback)
            // Save (editor) then reopen: value kept, existing field names unchanged, unknown property kept
            val saved = EmailSetting.mergeJson(stored, reopened.copy(title = "edited"))
            val json = JsonParser.parseString(saved).asJsonObject
            assertEquals(value, json["allowUnencryptedFallback"].asBoolean)
            assertEquals(1, json["futureOption"].asJsonObject["x"].asInt)
            for (name in listOf("mailType", "fromEmail", "recipients", "encryptionProtocol", "keystore", "password", "openKeychainSignKeyId")) assertTrue(name, json.has(name))
            val again = Gson().fromJson(saved, EmailSetting::class.java)
            assertEquals(value, again.allowUnencryptedFallback)
            assertEquals("edited", again.title)
        }
        // New target and unreadable previous JSON still produce the plain serialization
        val fresh = JsonParser.parseString(EmailSetting.mergeJson(null, EmailSetting(allowUnencryptedFallback = true))).asJsonObject
        assertTrue(fresh["allowUnencryptedFallback"].asBoolean)
        assertFalse(JsonParser.parseString(EmailSetting.mergeJson("not json", EmailSetting())).asJsonObject["allowUnencryptedFallback"].asBoolean)
    }

    @Test fun onlyEncryptionModesUseThePolicy() {
        assertTrue(EmailCrypto.isEncryptionMode("S/MIME") && EmailCrypto.isEncryptionMode("OpenPGP") && EmailCrypto.isEncryptionMode("OpenKeychain"))
        assertFalse(EmailCrypto.isEncryptionMode("Plain") || EmailCrypto.isEncryptionMode("") || EmailCrypto.isEncryptionMode(null))
    }

    @Test fun explicitlyUnencryptedModeDoesNotSkipRecipientsWithRetainedKeys() {
        // Keys are retained for switching back to encryption, but do not filter recipient selection.
        val r = linkedMapOf("a@x.invalid" to Pair("", ""), "b@x.invalid" to Pair(fixture("rsa-recipient.pem"), ""), "c@x.invalid" to Pair("/nonexistent/fixture.pem", ""))
        assertEquals(listOf("a@x.invalid", "b@x.invalid", "c@x.invalid"), EmailUtils.plainModeRecipients(r))
        assertTrue(EmailUtils.plainModeRecipients(emptyMap()).isEmpty())
    }

    // ---- Recipient keys: missing / bad / valid; certificate-only S/MIME bug ----

    @Test fun certificateOnlySmimeUsesDecodedStream() {
        val pem = goodSmime
        assertTrue("PEM certificate (Base64 of file) must load, not be treated as a path", pem is RecipientKey.Smime)
        val der = EmailKeys.loadRecipientKey(EmailCrypto.SMIME, fixture("rsa-recipient.der"), "")
        assertTrue(der is RecipientKey.Smime)
        assertEquals((pem as RecipientKey.Smime).certificate, (der as RecipientKey.Smime).certificate)
        val p12 = EmailKeys.loadRecipientKey(EmailCrypto.SMIME, fixture("rsa-recipient.p12"), "fixture-pass")
        assertEquals(pem.certificate, (p12 as RecipientKey.Smime).certificate)
    }

    @Test fun missingBadAndWrongPasswordKeys() {
        assertTrue(EmailKeys.loadRecipientKey(EmailCrypto.SMIME, "", "") is RecipientKey.None)
        val bad = EmailKeys.loadRecipientKey(EmailCrypto.SMIME, Base64.encode("not a certificate".toByteArray()), "")
        assertEquals(EmailFailure.BAD_RECIPIENT_KEY, (bad as RecipientKey.Invalid).failure)
        val wrongPassword = EmailKeys.loadRecipientKey(EmailCrypto.SMIME, fixture("rsa-recipient.p12"), "wrong")
        assertEquals(EmailFailure.BAD_RECIPIENT_KEY, (wrongPassword as RecipientKey.Invalid).failure)
        val badPgp = EmailKeys.loadRecipientKey(EmailCrypto.OPENPGP, Base64.encode("garbage".toByteArray()), "")
        assertTrue(badPgp is RecipientKey.Invalid)
        // Outcome detail is a class name or fixed text, never the key data
        assertFalse(wrongPassword.detail.contains("fixture-pass"))
    }

    @Test fun openPgpValidAndUnusableKeys() {
        val secret = PGPainless.generateKeyRing().modernKeyRing("Fixture <pgp@example.invalid>")
        val armored = PGPainless.asciiArmor(PGPainless.extractCertificate(secret))
        val valid = EmailKeys.loadRecipientKey(EmailCrypto.OPENPGP, Base64.encode(armored.toByteArray()), "")
        assertTrue(valid is RecipientKey.Pgp)
        // Signing-only key: parses, but cannot be used for encryption
        val signOnly = PGPainless.buildKeyRing()
            .setPrimaryKey(org.pgpainless.key.generation.KeySpec.getBuilder(org.pgpainless.key.generation.type.KeyType.EDDSA(org.pgpainless.key.generation.type.eddsa.EdDSACurve._Ed25519), org.pgpainless.algorithm.KeyFlag.CERTIFY_OTHER, org.pgpainless.algorithm.KeyFlag.SIGN_DATA))
            .addUserId("Signer <sign@example.invalid>").build()
        val unusable = EmailKeys.loadRecipientKey(EmailCrypto.OPENPGP, Base64.encode(PGPainless.asciiArmor(PGPainless.extractCertificate(signOnly)).toByteArray()), "")
        assertEquals(EmailFailure.UNUSABLE_RECIPIENT_KEY, (unusable as RecipientKey.Invalid).failure)
    }

    // ---- Signing key: configured but unusable is never silently dropped ----

    @Test fun signingKeyFailuresAreReportedNotStripped() {
        assertTrue(EmailKeys.loadSigningKey(EmailCrypto.SMIME, "", "") is SigningKey.None)
        assertTrue(EmailKeys.loadSigningKey(EmailCrypto.SMIME, fixture("sender-signing.p12"), "fixture-pass") is SigningKey.Smime)
        val wrong = EmailKeys.loadSigningKey(EmailCrypto.SMIME, fixture("sender-signing.p12"), "wrong")
        assertEquals(EmailFailure.SIGNING_KEY_FAILED, (wrong as SigningKey.Failed).failure)
        val certOnly = EmailKeys.loadSigningKey(EmailCrypto.SMIME, fixture("rsa-recipient.pem"), "x")
        assertTrue(certOnly is SigningKey.Failed)

        val pgp = PGPainless.generateKeyRing().modernKeyRing("Sender <s@example.invalid>", "pgp-fixture")
        val pgpData = Base64.encode(PGPainless.asciiArmor(pgp).toByteArray())
        assertTrue(EmailKeys.loadSigningKey(EmailCrypto.OPENPGP, pgpData, "pgp-fixture") is SigningKey.Pgp)
        assertEquals(EmailFailure.SIGNING_KEY_FAILED, (EmailKeys.loadSigningKey(EmailCrypto.OPENPGP, pgpData, "wrong") as SigningKey.Failed).failure)
        assertEquals(EmailFailure.SIGNING_PASSWORD_MISSING, (EmailKeys.loadSigningKey(EmailCrypto.OPENPGP, pgpData, "") as SigningKey.Failed).failure)

        // Delivery with a failed signing key sends nothing, with fallback on or off
        for (fallback in listOf(false, true)) {
            val smtp = FakeSmtp()
            val out = runBlocking { delivery(fallback = fallback, smtp = smtp, signing = wrong).deliver(listOf("a@x.invalid" to goodSmime, "b@x.invalid" to RecipientKey.None)) }
            assertEquals(0, smtp.attempts)
            assertTrue(out.all { it is EmailOutcome.Failed && it.reason == EmailFailure.SIGNING_KEY_FAILED })
            assertEquals(0, EmailDeliveryReport.status(out))
        }
    }

    // ---- Policy: OFF / ON, mixed recipients, aggregation ----

    @Test fun fallbackOffSendsNothingUnencryptedAndKeepsValidRecipients() {
        val smtp = FakeSmtp()
        val out = runBlocking {
            delivery(fallback = false, smtp = smtp).deliver(listOf(
                "good@x.invalid" to goodSmime,
                "nokey@x.invalid" to RecipientKey.None,
                "bad@x.invalid" to RecipientKey.Invalid(EmailFailure.BAD_RECIPIENT_KEY, "IOException"),
            ))
        }
        assertEquals(listOf("ENC:good@x.invalid"), smtp.sent)
        assertTrue(smtp.sent.none { it.startsWith("PLAIN") })
        assertTrue(out[0] is EmailOutcome.Encrypted)
        assertEquals(EmailFailure.NO_RECIPIENT_KEY, (out[1] as EmailOutcome.Failed).reason)
        assertEquals(EmailFailure.BAD_RECIPIENT_KEY, (out[2] as EmailOutcome.Failed).reason)
        assertEquals("one recipient not reached => failed status", 0, EmailDeliveryReport.status(out))
    }

    @Test fun fallbackOnSendsLabelledUnencryptedCopyOnlyForFailedPreparation() {
        val smtp = FakeSmtp()
        val out = runBlocking {
            delivery(fallback = true, smtp = smtp).deliver(listOf(
                "good@x.invalid" to goodSmime,
                "nokey@x.invalid" to RecipientKey.None,
                "bad@x.invalid" to RecipientKey.Invalid(EmailFailure.BAD_RECIPIENT_KEY, "IOException"),
            ))
        }
        assertEquals(listOf("ENC:good@x.invalid", "PLAIN:nokey@x.invalid, bad@x.invalid"), smtp.sent)
        assertTrue(out[0] is EmailOutcome.Encrypted)
        assertTrue(out[1] is EmailOutcome.Unencrypted && out[2] is EmailOutcome.Unencrypted)
        assertEquals(2, EmailDeliveryReport.status(out))
        // Log text: explicit, never "encrypted" for the fallback recipients
        val text = EmailDeliveryReport.describe(out) { id, _ -> id.toString() }.lines()
        assertEquals(R.string.email_outcome_encrypted.toString(), text[0])
        assertEquals(R.string.email_outcome_unencrypted.toString(), text[1])
        assertEquals(R.string.email_outcome_unencrypted.toString(), text[2])
    }

    @Test fun allRecipientsEncryptedGivesSuccessWithoutPlainCopy() {
        for (fallback in listOf(false, true)) {
            val smtp = FakeSmtp()
            val out = runBlocking { delivery(fallback = fallback, smtp = smtp).deliver(listOf("a@x.invalid" to goodSmime, "b@x.invalid" to goodSmime)) }
            assertEquals(listOf("ENC:a@x.invalid", "ENC:b@x.invalid"), smtp.sent)
            assertEquals(2, EmailDeliveryReport.status(out))
        }
    }

    // ---- Runtime encryption error versus SMTP failure ----

    @Test fun runtimeEncryptionErrorFallsBackOnlyWhenAllowed() {
        val failingEncrypt: (List<String>, RecipientKey) -> MimeMessage = { _, _ -> throw org.bouncycastle.cms.CMSException("fixture encryption failure") }
        val off = FakeSmtp()
        val outOff = runBlocking { delivery(fallback = false, smtp = off, encrypt = failingEncrypt).deliver(listOf("a@x.invalid" to goodSmime)) }
        assertEquals(0, off.attempts)
        assertEquals(EmailFailure.ENCRYPTION_FAILED, (outOff[0] as EmailOutcome.Failed).reason)
        assertEquals("CMSException", (outOff[0] as EmailOutcome.Failed).detail)

        val on = FakeSmtp()
        val outOn = runBlocking { delivery(fallback = true, smtp = on, encrypt = failingEncrypt).deliver(listOf("a@x.invalid" to goodSmime)) }
        assertEquals(listOf("PLAIN:a@x.invalid"), on.sent)
        assertEquals(EmailFailure.ENCRYPTION_FAILED, (outOn[0] as EmailOutcome.Unencrypted).reason)
    }

    @Test fun smtpFailureAfterEncryptionNeverProducesUnencryptedCopy() {
        val smtp = FakeSmtp(alwaysFail = true)
        val d = delivery(fallback = true, smtp = smtp, retries = 2)
        val out = runBlocking { d.deliver(listOf("a@x.invalid" to goodSmime)) }
        assertEquals("1 attempt + 2 retries of the same encrypted message", 3, smtp.attempts)
        assertTrue(smtp.sent.isEmpty())
        val failed = out.single() as EmailOutcome.Failed
        assertEquals(EmailFailure.SMTP_FAILED, failed.reason)
        assertEquals(0, EmailDeliveryReport.status(out))
    }

    @Test fun smtpRetryResendsSameEncryptedMessage() {
        val smtp = FakeSmtp(failFirst = 1)
        var prepared = 0
        val out = runBlocking {
            delivery(fallback = true, smtp = smtp, retries = 2, encrypt = { e, _ -> prepared++; msg("ENC:" + e.joinToString()) }).deliver(listOf("a@x.invalid" to goodSmime))
        }
        assertEquals(1, prepared)
        assertEquals(2, smtp.attempts)
        assertEquals(listOf("ENC:a@x.invalid"), smtp.sent)
        assertTrue(out.single() is EmailOutcome.Encrypted)
    }

    @Test fun fallbackSmtpFailureIsReportedAsNotSent() {
        val smtp = FakeSmtp(alwaysFail = true)
        val out = runBlocking { delivery(fallback = true, smtp = smtp).deliver(listOf("a@x.invalid" to RecipientKey.None)) }
        assertEquals(EmailFailure.SMTP_FAILED, (out.single() as EmailOutcome.Failed).reason)
    }

    @Test fun openKeychainFailureFollowsPolicy() {
        val failing: (List<String>, RecipientKey) -> MimeMessage = { _, _ -> throw IllegalStateException("Authorize in the target editor") }
        val off = FakeSmtp()
        val outOff = runBlocking { delivery(EmailCrypto.OPENKEYCHAIN, false, off, encrypt = failing).deliver(listOf("a@x.invalid" to RecipientKey.None, "b@x.invalid" to RecipientKey.None)) }
        assertEquals(0, off.attempts)
        assertTrue(outOff.all { it is EmailOutcome.Failed && it.reason == EmailFailure.ENCRYPTION_FAILED })
        val on = FakeSmtp()
        val outOn = runBlocking { delivery(EmailCrypto.OPENKEYCHAIN, true, on, encrypt = failing).deliver(listOf("a@x.invalid" to RecipientKey.None, "b@x.invalid" to RecipientKey.None)) }
        assertEquals(listOf("PLAIN:a@x.invalid, b@x.invalid"), on.sent)
        assertTrue(outOn.all { it is EmailOutcome.Unencrypted && it.reason == EmailFailure.ENCRYPTION_FAILED })
        val ok = FakeSmtp()
        val outOk = runBlocking { delivery(EmailCrypto.OPENKEYCHAIN, true, ok).deliver(listOf("a@x.invalid" to RecipientKey.None, "b@x.invalid" to RecipientKey.None)) }
        assertEquals(listOf("ENC:a@x.invalid, b@x.invalid"), ok.sent)
        assertTrue(outOk.all { it is EmailOutcome.Encrypted })
    }

    // ---- Real local crypto (no SMTP): S/MIME and OpenPGP messages are actually encrypted ----

    @Test fun realSmimeAndPgpPreparationProducesEncryptedContent() {
        val props = EmailSender.smtpProperties("127.0.0.1", "1", false, false)
        val auth = EmailSender.authenticator("s@example.invalid", "unused")
        val cert = (goodSmime as RecipientKey.Smime).certificate
        val signer = EmailKeys.loadSigningKey(EmailCrypto.SMIME, fixture("sender-signing.p12"), "fixture-pass") as SigningKey.Smime
        val smime = SmimeUtils(props, auth, "s@example.invalid", "s@example.invalid", "", "Fixture subject", "secret-body-123",
            toAddress = mutableListOf("r@example.invalid"), recipientX509Cert = cert, senderPrivateKey = signer.privateKey, senderX509Cert = signer.certificate)
            .prepareMessage(encrypt = true, sign = true)
        assertTrue(smime.contentType.contains("enveloped-data"))
        assertFalse(bytes(smime).contains("secret-body-123"))

        val secret = PGPainless.generateKeyRing().modernKeyRing("R <r@example.invalid>")
        val pgp = PgpUtils(props, Session.getInstance(props, auth), "s@example.invalid", "s@example.invalid", "", "Fixture", "secret-body-456",
            toAddress = listOf("r@example.invalid"), recipientPGPPublicKeyRing = PGPainless.extractCertificate(secret)).prepareMessage(encrypt = true, sign = false)
        assertTrue(pgp.contentType.startsWith("multipart/encrypted"))
        assertFalse(bytes(pgp).contains("secret-body-456"))
    }

    @Test fun realSmimeRuntimeErrorIsAnEncryptionFailure() {
        // An EC certificate parses, but S/MIME key transport (RSA) cannot encrypt to it: a genuine runtime crypto error
        val ec = EmailKeys.loadRecipientKey(EmailCrypto.SMIME, fixture("ec-recipient.pem"), "")
        assertTrue(ec is RecipientKey.Smime)
        val props = EmailSender.smtpProperties("127.0.0.1", "1", false, false)
        val auth = EmailSender.authenticator("s@example.invalid", "unused")
        val prepare: (List<String>, RecipientKey) -> MimeMessage = { e, k ->
            SmimeUtils(props, auth, "s@example.invalid", "s@example.invalid", "", "Fixture", "body", toAddress = e.toMutableList(),
                recipientX509Cert = (k as RecipientKey.Smime).certificate).prepareMessage(encrypt = true, sign = false)
        }
        try {
            prepare(listOf("r@example.invalid"), ec)
            fail("EC certificate unexpectedly accepted for RSA key transport")
        } catch (expected: Exception) {
            assertNotNull(expected)
        }
        val smtp = FakeSmtp()
        val out = runBlocking { delivery(fallback = false, smtp = smtp, encrypt = prepare).deliver(listOf("r@example.invalid" to ec)) }
        assertEquals(0, smtp.attempts)
        assertEquals(EmailFailure.ENCRYPTION_FAILED, (out.single() as EmailOutcome.Failed).reason)
    }

    private fun bytes(m: MimeMessage) = ByteArrayOutputStream().also { m.writeTo(it) }.toString("ISO-8859-1")
}
