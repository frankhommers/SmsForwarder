package cn.ppps.forwarder.utils
import org.junit.Assert.*
import org.junit.Test
class EmailProviderChoicesTest {
 @Test fun freshListOnlyContainsSupportedPresets() { assertEquals(listOf("@gmail.com","@icloud.com","other"),EmailProviderChoices.tokens()) }
 @Test fun savedLegacyTokenRemainsUnchangedAndExplicit() {
  val tokens=EmailProviderChoices.tokens("@qq.com")
  assertEquals("@qq.com",tokens.first())
  assertEquals("@qq.com (legacy)",EmailProviderChoices.labels(tokens,"Other") { "$it (legacy)" }.first())
  assertFalse(EmailProviderChoices.tokens().contains("@qq.com"))
 }
 @Test fun translatedCustomLabelDoesNotChangeToken() {
  val tokens=EmailProviderChoices.tokens()
  assertEquals("Overige",EmailProviderChoices.labels(tokens,"Overige") { it }.last())
  assertEquals("other",tokens.last())
 }
}
