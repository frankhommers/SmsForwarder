package cn.ppps.forwarder

import android.app.Activity
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import androidx.test.platform.app.InstrumentationRegistry
import cn.ppps.forwarder.activity.MainActivity
import cn.ppps.forwarder.core.Core
import cn.ppps.forwarder.database.entity.Sender
import cn.ppps.forwarder.fragment.senders.EmailFragment
import cn.ppps.forwarder.utils.KEY_SENDER_ID
import cn.ppps.forwarder.utils.KEY_SENDER_TYPE
import cn.ppps.forwarder.utils.TYPE_EMAIL
import cn.ppps.forwarder.utils.mail.EmailDeliveryReport
import cn.ppps.forwarder.utils.mail.EmailCrypto
import cn.ppps.forwarder.utils.mail.EmailFailure
import cn.ppps.forwarder.utils.mail.EmailOutcome
import cn.ppps.forwarder.utils.mail.EncryptedEmailDelivery
import cn.ppps.forwarder.utils.mail.RecipientKey
import cn.ppps.forwarder.utils.mail.SigningKey
import com.google.gson.JsonParser
import com.xuexiang.xpage.core.PageOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId

/**
 * Email target "Send unencrypted if encryption fails" toggle: EN/NL copy, enabled state per protocol, retained value,
 * help dialog, actual save to the local database and reopen, and the delivery policy with a fake SMTP.
 * Local only: Save and the policy check never contact a mail server (the Test button is never pressed).
 * Language: instrumentation argument `language` (en or nl).
 */
class EmailFallbackToggleTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val args get() = InstrumentationRegistry.getArguments()
    private val language get() = args.getString("language", "en")!!
    private fun walk(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { walk(v.getChildAt(it)) } else emptyList()
    private fun settle() { ins.waitForIdleSync(); Thread.sleep(500) }
    private fun waitUntil(label: String, condition: () -> Boolean) {
        repeat(100) { var ok = false; ins.runOnMainSync { ok = condition() }; if (ok) return; Thread.sleep(100) }
        fail("Timed out: $label")
    }
    private fun shot(name: String) {
        settle()
        val dir = java.io.File(ins.targetContext.getExternalFilesDir(null), "email-policy/$language").apply { mkdirs() }
        java.io.FileOutputStream(java.io.File(dir, "$name.png")).use { ins.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun reveal(view: View) { ins.runOnMainSync { view.requestRectangleOnScreen(android.graphics.Rect(0, 0, view.width, view.height), true) }; settle() }

    private fun main(): MainActivity {
        com.hjq.language.MultiLanguages.setAppLanguage(ins.targetContext, java.util.Locale(language))
        return ins.startActivitySync(Intent(ins.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
    }

    private fun open(main: MainActivity, id: Long): Pair<Activity, EmailFragment> {
        ins.runOnMainSync {
            PageOption.to(EmailFragment::class.java).putLong(KEY_SENDER_ID, id).putInt(KEY_SENDER_TYPE, TYPE_EMAIL).setNewActivity(true).open(main)
        }
        var activity: Activity? = null
        var fragment: EmailFragment? = null
        waitUntil("EmailFragment resumed") {
            activity = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).firstOrNull { it !== main && !it.isFinishing }
            fragment = (activity as? androidx.fragment.app.FragmentActivity)?.supportFragmentManager?.fragments?.filterIsInstance<EmailFragment>()?.firstOrNull { it.isVisible && it.isResumed }
            fragment != null
        }
        waitUntil("stored target loaded") { fragment!!.binding!!.etName.text.toString() == NAME }
        settle()
        return activity!! to fragment!!
    }

    private fun expected(en: String, nl: String) = if (language == "nl") nl else en

    @Test fun savedLegacyProviderSurvivesEditorSave() {
        val json = """{"mailType":"@qq.com","fromEmail":"localfixture","pwd":"fixture-only","host":"smtp.qq.com","port":"465","ssl":true,"startTls":false,"recipients":{"recipient@example.invalid":{"first":"","second":""}},"encryptionProtocol":"Plain","futureOption":{"kept":true}}"""
        Core.sender.getAll().blockingGet().filter { it.name == NAME }.forEach { Core.sender.delete(it.id) }
        Core.sender.insert(Sender(0, TYPE_EMAIL, NAME, json, 0))
        val id = Core.sender.getAll().blockingGet().first { it.name == NAME }.id
        val activity = main()
        try {
            val (editor, fragment) = open(activity, id)
            ins.runOnMainSync {
                assertEquals(editor.getString(R.string.email_provider_legacy, "@qq.com"), fragment.binding!!.spMailType.text.toString())
                fragment.binding!!.btnSave.performClick()
            }
            waitUntil("legacy editor closed after Save") { editor.isFinishing || editor.isDestroyed }
            val stored = JsonParser.parseString(Core.sender.getAll().blockingGet().first { it.id == id }.jsonSetting).asJsonObject
            assertEquals("@qq.com", stored.get("mailType").asString)
            assertEquals("localfixture", stored.get("fromEmail").asString)
            assertTrue(stored.getAsJsonObject("futureOption").get("kept").asBoolean)
        } finally {
            Core.sender.delete(id)
            ins.runOnMainSync { activity.finish() }
        }
    }

    @Test fun toggleCopyStateSaveAndReopen() {
        // Target saved by an older build: no allowUnencryptedFallback property, plus a property this build does not know.
        val oldJson = """{"mailType":"other","fromEmail":"fixture@example.invalid","pwd":"fixture-only","nickname":"","host":"127.0.0.1","port":"1","ssl":false,"startTls":false,"title":"","recipients":{"recipient@example.invalid":{"first":"","second":""}},"toEmail":"","keystore":"","password":"","encryptionProtocol":"S/MIME","fromEmailAlias":"","openKeychainSignKeyId":0,"openKeychainSignKeyDesc":"","futureOption":{"kept":true}}"""
        Core.sender.getAll().blockingGet().filter { it.name == NAME }.forEach { Core.sender.delete(it.id) }
        Core.sender.insert(Sender(0, TYPE_EMAIL, NAME, oldJson, 0))
        val id = Core.sender.getAll().blockingGet().first { it.name == NAME }.id
        val main = main()
        try {
            var (activity, fragment) = open(main, id)
            val b = fragment.binding!!
            ins.runOnMainSync {
                assertEquals(expected("Send unencrypted if encryption fails", "Onversleuteld verzenden als versleutelen mislukt"), b.sbUnencryptedFallback.text.toString())
                assertFalse("absent property => off", b.sbUnencryptedFallback.isChecked)
                assertTrue("S/MIME => toggle usable", b.sbUnencryptedFallback.isEnabled)
                assertEquals(activity.getString(R.string.target_form_email_fallback_off), b.tvUnencryptedFallbackState.text.toString())
                assertTrue(b.btnHelpUnencryptedFallback.isShown && b.btnHelpUnencryptedFallback.contentDescription.isNotEmpty())
            }
            reveal(b.layoutUnencryptedFallback); shot("01-smime-off-default")

            // Real (injected) tap turns it on: warning line in the error colour
            onView(withId(R.id.sb_unencrypted_fallback)).perform(click())
            settle()
            ins.runOnMainSync {
                assertTrue(b.sbUnencryptedFallback.isChecked)
                assertEquals(activity.getString(R.string.target_form_email_fallback_on), b.tvUnencryptedFallbackState.text.toString())
                assertEquals(com.google.android.material.color.MaterialColors.getColor(b.tvUnencryptedFallbackState, com.google.android.material.R.attr.colorError), b.tvUnencryptedFallbackState.currentTextColor)
            }
            shot("02-smime-on-warning")

            // Unencrypted: disabled, value kept; a tap does nothing
            ins.runOnMainSync { b.rbEncryptionProtocolPlain.performClick() }
            settle()
            ins.runOnMainSync {
                assertFalse(b.sbUnencryptedFallback.isEnabled)
                assertTrue("value retained while disabled", b.sbUnencryptedFallback.isChecked)
                assertEquals(activity.getString(R.string.target_form_email_fallback_na), b.tvUnencryptedFallbackState.text.toString())
            }
            reveal(b.layoutUnencryptedFallback)
            // A real tap on the disabled switch changes nothing (performClick() would bypass the enabled state)
            onView(withId(R.id.sb_unencrypted_fallback)).perform(click())
            settle()
            ins.runOnMainSync {
                assertTrue("disabled switch ignores taps", b.sbUnencryptedFallback.isChecked)
                assertTrue("help stays usable", b.btnHelpUnencryptedFallback.isEnabled && b.btnHelpUnencryptedFallback.alpha == 1f)
            }
            shot("03-plain-disabled-retained")

            // OpenKeychain and OpenPGP: usable again with the kept value
            ins.runOnMainSync { b.rbEncryptionProtocolOpenpgp.performClick() }
            settle()
            ins.runOnMainSync { assertTrue(b.sbUnencryptedFallback.isEnabled && b.sbUnencryptedFallback.isChecked) }

            // Question-mark help opens a local dialog with the plain-language explanation
            ins.runOnMainSync { b.btnHelpUnencryptedFallback.performClick() }
            settle()
            shot("04-help-dialog")
            val dialogText = activity.getString(R.string.target_form_email_fallback_dialog)
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText(dialogText))
                .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check(androidx.test.espresso.assertion.ViewAssertions.matches(androidx.test.espresso.matcher.ViewMatchers.isDisplayed()))
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText(dialogText))
                .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check { view, _ ->
                    val scroll = generateSequence(view.parent) { it.parent }.filterIsInstance<View>().firstOrNull { it.canScrollVertically(1) || it.canScrollVertically(-1) }
                    assertTrue("help text fits without scrolling", scroll == null)
                }
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText(activity.getString(R.string.m3_close)))
                .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(androidx.test.espresso.action.ViewActions.click())
            settle()

            // Save (local database only), read back the exact JSON
            ins.runOnMainSync { b.btnSave.performClick() }
            var stored: Sender? = null
            repeat(50) { stored = Core.sender.getAll().blockingGet().firstOrNull { it.name == NAME && it.jsonSetting.contains("allowUnencryptedFallback\":true") }; if (stored == null) Thread.sleep(100) }
            assertNotNull("saved with fallback on", stored)
            val json = JsonParser.parseString(stored!!.jsonSetting).asJsonObject
            assertTrue(json["allowUnencryptedFallback"].asBoolean)
            assertEquals("OpenPGP", json["encryptionProtocol"].asString)
            assertTrue("unknown property kept", json["futureOption"].asJsonObject["kept"].asBoolean)
            assertEquals("fixture@example.invalid", json["fromEmail"].asString)
            assertTrue(json["recipients"].asJsonObject.has("recipient@example.invalid"))

            // Reopen
            ins.runOnMainSync { if (!activity.isFinishing) activity.finish() }
            settle()
            val reopened = open(main, id)
            activity = reopened.first; fragment = reopened.second
            val r = fragment.binding!!
            ins.runOnMainSync {
                assertTrue(r.rbEncryptionProtocolOpenpgp.isChecked)
                assertTrue(r.sbUnencryptedFallback.isChecked && r.sbUnencryptedFallback.isEnabled)
                assertEquals(activity.getString(R.string.target_form_email_fallback_on), r.tvUnencryptedFallbackState.text.toString())
            }
            Thread.sleep(3500) // let the "saved" toast from the previous screen disappear before the capture
            reveal(r.layoutUnencryptedFallback); shot("05-reopened-openpgp-on")

            // Turn off again and save: explicit false is stored
            reveal(r.layoutUnencryptedFallback)
            onView(withId(R.id.sb_unencrypted_fallback)).perform(click())
            settle()
            ins.runOnMainSync { assertFalse(r.sbUnencryptedFallback.isChecked); r.btnSave.performClick() }
            var off: Sender? = null
            repeat(50) { off = Core.sender.getAll().blockingGet().firstOrNull { it.id == id && it.jsonSetting.contains("allowUnencryptedFallback\":false") }; if (off == null) Thread.sleep(100) }
            assertNotNull("saved with fallback off", off)
            ins.runOnMainSync { if (!activity.isFinishing) activity.finish() }
        } finally {
            Core.sender.getAll().blockingGet().filter { it.name == NAME }.forEach { Core.sender.delete(it.id) }
            ins.runOnMainSync { main.finish() }
        }
    }

    @Test fun deliveryPolicyAndLocalizedLogTextOnDevice() {
        com.hjq.language.MultiLanguages.setAppLanguage(ins.targetContext, java.util.Locale(language))
        val main = main()
        try {
            val context = main
            val sent = mutableListOf<String>()
            var attempts = 0
            val session = jakarta.mail.Session.getInstance(java.util.Properties())
            fun msg(s: String) = jakarta.mail.internet.MimeMessage(session).apply { subject = s }
            val recipients = listOf(
                "nokey@example.invalid" to RecipientKey.None,
                "bad@example.invalid" to RecipientKey.Invalid(EmailFailure.BAD_RECIPIENT_KEY, "IOException"),
            )
            fun run(fallback: Boolean, smtpFails: Boolean = false, key: RecipientKey? = null): List<EmailOutcome> = kotlinx.coroutines.runBlocking {
                EncryptedEmailDelivery(EmailCrypto.SMIME, fallback, SigningKey.None, 1, 0L,
                    prepareEncrypted = { e, _ -> msg("ENC:" + e.joinToString()) },
                    prepareUnencrypted = { e -> msg("PLAIN:" + e.joinToString()) },
                    send = { m -> attempts++; if (smtpFails) throw jakarta.mail.MessagingException("fixture refused"); sent += m.subject },
                    pause = {}).deliver(if (key != null) listOf("enc@example.invalid" to key) else recipients)
            }
            val off = run(false)
            assertTrue(sent.isEmpty()); assertEquals(0, attempts); assertEquals(0, EmailDeliveryReport.status(off))
            val on = run(true)
            assertEquals(listOf("PLAIN:nokey@example.invalid, bad@example.invalid"), sent)
            assertEquals(2, EmailDeliveryReport.status(on))
            val text = EmailDeliveryReport.describe(on) { id, a -> context.getString(id, *a) }
            assertTrue(text, text.contains(expected("UNENCRYPTED", "ONVERSLEUTELD")))
            assertFalse(text, text.contains(expected("sent encrypted", "versleuteld verzonden")))
            val offText = EmailDeliveryReport.describe(off) { id, a -> context.getString(id, *a) }
            assertTrue(offText, offText.contains(expected("not sent", "niet verzonden")))
            // SMTP failure after encryption: retried as the encrypted message, never a plain copy
            sent.clear(); attempts = 0
            val pgpKey = RecipientKey.Pgp(org.pgpainless.PGPainless.extractCertificate(org.pgpainless.PGPainless.generateKeyRing().modernKeyRing("R <r@example.invalid>")))
            val smtp = run(true, smtpFails = true, key = pgpKey)
            assertEquals(2, attempts); assertTrue(sent.isEmpty())
            assertEquals(EmailFailure.SMTP_FAILED, (smtp.single() as EmailOutcome.Failed).reason)
            val dir = java.io.File(ins.targetContext.getExternalFilesDir(null), "email-policy/$language").apply { mkdirs() }
            java.io.File(dir, "log-text.txt").writeText("OFF:\n$offText\n\nON:\n$text\n\nSMTP:\n" + EmailDeliveryReport.describe(smtp) { id, a -> context.getString(id, *a) } + "\n")
        } finally {
            ins.runOnMainSync { main.finish() }
        }
    }

    companion object {
        private const val NAME = "Email policy fixture"
    }
}
