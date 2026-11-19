package cn.ppps.forwarder.utils.template

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Pure JVM tests for the Insert data / Preview helpers. They prove the local renderer, escaping, syntax
 * separation and cursor insertion; they do not inflate views or observe a device.
 */
class TemplateTextTest {
    private val sample = SampleText(
        smsText = "Your code is 482913", simLabel = "Work", ruleTitle = "Bank", deviceName = "My phone",
        appTitle = "Alex", appText = "See you \"at\" 6\\7", callDetails = "Contact:Alex\nCall Type: Missed Call",
        callType = "Missed Call", phoneArea = "Unknown Area", location = "\nLongitude: 4.8952\n", locationAddress = "Damrak 1",
        batteryStatus = "Discharging", batteryPlugged = "AC", batteryInfo = "\nLevel: 76%", taskName = "Battery check", taskText = "Low",
    )
    private val sms = TemplateSamples.values(SampleContext.SMS, sample)
    private val app = TemplateSamples.values(SampleContext.APP, sample)

    private val module = listOf(File("."), File("app")).first { File(it, "src/main/res/values").isDirectory }

    // ---------- rendering ----------

    @Test fun rendersKnownTagsWithSampleValuesOnly() {
        val r = TemplateRenderer.render("From {{FROM}}: {{SMS}} ({{CARD_SLOT}}) at {{RECEIVE_TIME}}", sms)
        assertEquals("From +31612345678: Your code is 482913 (SIM1_Work) at 2026-03-14 09:26:53", r.text)
        assertTrue(r.unknown.isEmpty() && r.invalid.isEmpty() && r.ignored.isEmpty())
    }

    @Test fun appContextMatchesMsgInfoFieldMapping() {
        // MsgInfo: {{PACKAGE_NAME}} is the sender field, {{TITLE}} and {{CARD_SLOT}} are simInfo (the notification title).
        val r = TemplateRenderer.render("{{PACKAGE_NAME}}|{{TITLE}}|{{CARD_SLOT}}|{{MSG}}|{{APP_NAME}}|{{UID}}", app)
        assertEquals("com.example.chat|Alex|Alex|See you \"at\" 6\\7|Example Chat|10123", r.text)
    }

    @Test fun unknownTokensStayAsTypedAndAreReported() {
        val r = TemplateRenderer.render("{{FROM}} {{NOPE}} {{NOPE}} {{ALSO_NOT}}", sms)
        assertEquals("+31612345678 {{NOPE}} {{NOPE}} {{ALSO_NOT}}", r.text)
        assertEquals(listOf("{{NOPE}}", "{{ALSO_NOT}}"), r.unknown)
    }

    @Test fun plainTagsAreCaseInsensitiveLikeMsgInfo() {
        assertEquals("+31612345678", TemplateRenderer.render("{{from}}", sms).text)
    }

    @Test fun regexFormAppliesAndInvalidPatternIsReportedNotGuessed() {
        val ok = TemplateRenderer.render("{{SMS###[^0-9]===}}", sms)
        assertEquals("482913", ok.text)
        val bad = TemplateRenderer.render("{{SMS###([===x}}", sms)
        assertEquals("{{SMS###([===x}}", bad.text)
        assertEquals(listOf("{{SMS###([===x}}"), bad.invalid)
        val group = TemplateRenderer.render("{{SMS###(\\d+)===\$5}}", sms) // missing group fails in replace, as in MsgInfo
        assertEquals(listOf("{{SMS###(\\d+)===\$5}}"), group.invalid)
    }

    @Test fun regexFormMakesPlainTagOfSameNameIgnoredLikeForwarding() {
        // MsgInfo.replaceTag rebuilds from the original string per match: only the last pattern form is filled.
        val r = TemplateRenderer.render("{{SMS}} / {{SMS###[^0-9]===}}", sms)
        assertEquals("{{SMS}} / 482913", r.text)
        assertEquals(listOf("{{SMS}}"), r.ignored)
    }

    @Test fun lookupTagsNeedExactUpperCase() {
        val r = TemplateRenderer.render("{{contact_name}}", sms)
        assertEquals("{{contact_name}}", r.text)
        assertEquals(listOf("{{contact_name}}"), r.ignored)
        assertEquals("Alex Jansen", TemplateRenderer.render("{{CONTACT_NAME}}", sms).text)
    }

    @Test fun stripsLiteralNullLikeGetContentForSend() {
        assertEquals("ab", TemplateRenderer.render("anullb", sms).text)
        assertEquals("anullb", TemplateRenderer.render("anullb", sms, stripNull = false).text)
    }

    @Test fun sampleValuesAreNeverRescanned() {
        val values = sms + ("SMS" to "{{FROM}} [msg]")
        val r = TemplateRenderer.render("{{SMS}}", values)
        assertEquals("{{FROM}} [msg]", r.text)
        assertTrue(r.unknown.isEmpty() && r.otherSyntax.isEmpty())
    }

    @Test fun notOfferedAndTargetSpecificAreReported() {
        val r = TemplateRenderer.render("{{BATTERY_PCT}} {{MSG_TITLE}}", sms, offered = setOf("FROM"), targetSpecific = TemplateTokens.CARD_TOKENS)
        assertEquals("76% {{MSG_TITLE}}", r.text)
        assertEquals(listOf("{{BATTERY_PCT}}"), r.notOffered)
        assertEquals(listOf("{{MSG_TITLE}}"), r.targetSpecific)
    }

    @Test fun builtInLayoutsMatchMsgInfo() {
        val src = File(module, "src/main/kotlin/cn/ppps/forwarder/entity/MsgInfo.kt").readText()
        assertTrue(src.contains("\"sms\", \"call\" -> \"SubId\uFF1A\${getString(R.string.tag_card_subid)}\\n\""))
        assertTrue(src.contains("\"app\" -> \"UID\uFF1A\${getString(R.string.tag_uid)}\\n\""))
        assertEquals("+31612345678\nYour code is 482913\nSIM1_Work\nSubId\uFF1A1\n2026-03-14 09:26:53\nMy phone",
            TemplateRenderer.render(TemplateSamples.builtInMessage(SampleContext.SMS), sms).text)
        assertFalse(TemplateSamples.builtInMessage(SampleContext.TASK).contains("SubId"))
    }

    @Test fun everySampleKeyIsAKnownTagResource() {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(module, "src/main/res/values/strings.xml"))
        val nodes = doc.getElementsByTagName("string")
        val tags = (0 until nodes.length).map { nodes.item(it) }
            .filter { it.attributes.getNamedItem("name").nodeValue.startsWith("tag_") }
            .map { it.textContent }.filter { it.startsWith("{{") }.toSet()
        assertEquals(tags, TemplateTokens.NAMES.map(TemplateTokens::token).toSet())
        for (c in SampleContext.values()) assertEquals(TemplateTokens.NAMES.toSet(), TemplateSamples.values(c, sample).keys)
    }

    // ---------- syntax separation and escaping ----------

    @Test fun bracketFieldsAreNotFilledInCommonFieldsButReported() {
        val r = TemplateRenderer.render("[msg] {{FROM}} [receive_time:HH:mm]", sms)
        assertEquals("[msg] +31612345678 [receive_time:HH:mm]", r.text)
        assertEquals(listOf("[msg]", "[receive_time:HH:mm]"), r.otherSyntax)
    }

    private val hook = WebhookPreview.Fields(msg = "line1\n\"q\" & ü", orgContent = "orig", from = "+31612345678", title = "SIM1_Work",
        deviceMark = "My \"phone\"", appVersion = "1.0")

    @Test fun webhookJsonEscapesBracketAndCommonValues() {
        val r = WebhookPreview.render("{\"t\":\"[msg]\",\"o\":\"[org_content]\",\"d\":\"[device_mark]\",\"a\":\"{{SMS}}\",\"at\":\"[receive_time:HH:mm]\"}", "POST", false, false, app, hook)
        assertEquals("{\"t\":\"line1\\n\\\"q\\\" \\u0026 ü\",\"o\":\"orig\",\"d\":\"My \\\"phone\\\"\",\"a\":\"See you \\\"at\\\" 6\\\\7\",\"at\":\"09:26\"}", r.text)
    }

    @Test fun webhookQueryUrlEncodesAndKeepsSignLiteralWithoutSecret() {
        val r = WebhookPreview.render("m=[msg]&f={{FROM}}&s=[sign]&t=[timestamp]", "GET", false, false, sms, hook)
        assertEquals("m=line1%0A%22q%22+%26+%C3%BC&f=%2B31612345678&s=[sign]&t=[timestamp]", r.text)
        val signed = WebhookPreview.render("s=[sign]&t=[timestamp]", "GET", false, true, sms, hook)
        assertEquals("s=a1b2c3%253D%253D&t=1773480413000", signed.text)
    }

    @Test fun webhookDefaultsMatchWebhookUtils() {
        assertEquals("from=%2B31612345678&content=line1%0A%22q%22+%26+%C3%BC", WebhookPreview.render("", "GET", false, false, sms, hook).text)
        assertEquals("from=+31612345678\ncontent=line1\n\"q\" & ü\ntimestamp=1773480413000", WebhookPreview.render("", "POST", false, false, sms, hook).text)
        assertEquals("from=+31612345678\ncontent=line1\n\"q\" & ü\ntimestamp=1773480413000\nsign=a1b2c3%3D%3D", WebhookPreview.render("", "POST", false, true, sms, hook).text)
    }

    @Test fun webhookBodySelectionMatchesWebhookUtils() {
        assertEquals(WebhookPreview.Body.QUERY, WebhookPreview.body("GET", "{\"a\":1}", true))
        assertEquals(WebhookPreview.Body.JSON_OR_TEXT, WebhookPreview.body("POST", "{\"a\":1}", false))
        assertEquals(WebhookPreview.Body.JSON_OR_TEXT, WebhookPreview.body("PUT", "a=b", true))
        assertEquals(WebhookPreview.Body.FORM, WebhookPreview.body("POST", "a=b", false))
        assertEquals(WebhookPreview.Body.FORM, WebhookPreview.body("POST", "", true))
    }

    @Test fun webhookFieldListOnlyContainsTokensWebhookUtilsReplaces() {
        val src = File(module, "src/main/kotlin/cn/ppps/forwarder/utils/sender/WebhookUtils.kt").readText()
        for (field in WebhookPreview.FIELDS.filter { it != "[receive_time]" }) assertTrue(field, src.contains(".replace(\"$field\""))
        assertTrue(src.contains("Regex(\"\\\\[receive_time(:(.*?))?]\")"))
        // Common tags are filled first (MsgInfo.replaceTemplate) in every branch.
        assertEquals(3, Regex("msgInfo\\.replaceTemplate\\(webParams").findAll(src).count())
    }

    @Test fun webhookMd5IsMaskedAndInvalidTimeFormatReported() {
        val r = WebhookPreview.render("{\"h\":\"md5([from]+'x')\",\"t\":\"[receive_time:QQQ]\"}", "POST", false, false, sms, hook)
        assertTrue(r.md5)
        assertEquals("{\"h\":\"${TemplateRenderer.MD5_MARKER}\",\"t\":\"[receive_time:QQQ]\"}", r.text)
        assertEquals(listOf("[receive_time:QQQ]"), r.invalid)
    }

    // ---------- insertion ----------

    @Test fun insertAtCursorKeepsSurroundingText() {
        assertEquals("Hi {{FROM}}there" to 11, TemplateInsertion.apply("Hi there", 3, "{{FROM}}"))
    }

    @Test fun insertWithoutCursorOrOutOfRangeAppends() {
        assertEquals("abc{{SMS}}" to 10, TemplateInsertion.apply("abc", -1, "{{SMS}}"))
        assertEquals("abc{{SMS}}" to 10, TemplateInsertion.apply("abc", 99, "{{SMS}}"))
        assertEquals("{{SMS}}" to 7, TemplateInsertion.apply("", 0, "{{SMS}}"))
    }

    @Test fun insertNothingLeavesExistingTextByteIdentical() {
        val legacy = "{{SMS###(?=x)===y}}\r\n  null\t"
        assertEquals(legacy to 2, TemplateInsertion.apply(legacy, 2, ""))
    }

    @Test fun insertedTokenIsExactlyTheStoredToken() {
        val (text, _) = TemplateInsertion.apply("x", 1, "[receive_time]")
        assertEquals("x[receive_time]", text)
    }

    @Test fun fallbackPerScene() {
        assertEquals(TemplateFallback.RULE_MESSAGE, TemplateFallback.forScene("sms"))
        assertEquals(TemplateFallback.RULE_MESSAGE, TemplateFallback.forScene("app"))
        assertEquals(TemplateFallback.GLOBAL_MESSAGE, TemplateFallback.forScene("all"))
        assertEquals(TemplateFallback.TITLE_FROM, TemplateFallback.forScene("basic"))
    }
}
