package cn.ppps.forwarder

import cn.ppps.forwarder.service.NotificationService
import cn.ppps.forwarder.utils.SettingUtils
import org.junit.Assert.*
import org.junit.Test

/** Calls only the existing matching predicate; no listener binding or forwarding. */
class NotificationFilterBehaviorTest {
 @Test fun matchesTitleOrBodyWithDocumentedRegexSemantics() {
  val previous=SettingUtils.appNotifyBlacklist
  val service=NotificationService()
  val method=NotificationService::class.java.getDeclaredMethod("isInBlacklist",String::class.java,String::class.java).apply{isAccessible=true}
  fun matches(title:String,body:String)=method.invoke(service,title,body) as Boolean
  try {
   SettingUtils.appNotifyBlacklist="urgent"
   assertTrue(matches("urgent notice","ordinary"))
   assertTrue(matches("ordinary","an urgent notice"))
   assertFalse(matches("URGENT notice","ordinary"))
   SettingUtils.appNotifyBlacklist="never-matches\ncode [0-9]+"
   assertTrue(matches("ordinary","code 1234"))
   assertFalse(matches("ordinary","code abc"))
   SettingUtils.appNotifyBlacklist="/code [0-9]+/"
   assertFalse(matches("ordinary","code 1234"))
   assertTrue(matches("ordinary","/code 1234/"))
   SettingUtils.appNotifyBlacklist=""
   assertFalse(matches("urgent notice","code 1234"))
  } finally {SettingUtils.appNotifyBlacklist=previous;assertEquals(previous,SettingUtils.appNotifyBlacklist)}
 }
}
