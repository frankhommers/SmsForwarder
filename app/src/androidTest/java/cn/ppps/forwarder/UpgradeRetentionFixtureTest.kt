package cn.ppps.forwarder

import androidx.test.platform.app.InstrumentationRegistry
import cn.ppps.forwarder.core.Core
import cn.ppps.forwarder.database.entity.Rule
import cn.ppps.forwarder.database.entity.Task
import cn.ppps.forwarder.utils.STATUS_OFF
import org.junit.Assert.*
import org.junit.Test
import java.util.Date

/** Explicit opt-in upgrade fixture. Disabled objects only; never executes actions. */
class UpgradeRetentionFixtureTest {
 @Test fun fixturePhase() {
  val phase=InstrumentationRegistry.getArguments().getString("upgradeFixturePhase") ?: return
  val ruleId=987654321L
  val taskId=987654322L
  when(phase) {
   "seed" -> {
    val sender=Core.sender.getAll().blockingGet().first()
    Core.rule.insert(Rule(id=ruleId,type="sms",filed="phone_num",check="is",value="+31000000000",senderId=sender.id,
     smsTemplate="Upgrade fixture {content}",regexReplace="foo===bar\nbaz===qux",simSlot="ALL",status=STATUS_OFF,
     time=Date(1234567890000L),senderList=listOf(sender),silentPeriodStart=2200,silentPeriodEnd=700,
     silentDayOfWeek="1,3,5",title="LOCAL UPGRADE FIXTURE"))
    Core.task.insert(Task(id=taskId,type=1000,name="LOCAL UPGRADE FIXTURE",description="Disabled retention test",
     conditions="[]",actions="[]",status=STATUS_OFF,lastExecTime=Date(1234567890000L),nextExecTime=Date(1234567890000L)))
   }
   "verify" -> {
    val r=Core.rule.get(ruleId).blockingGet();val t=Core.task.get(taskId).blockingGet()
    assertEquals(STATUS_OFF,r.status);assertEquals("foo===bar\nbaz===qux",r.regexReplace)
    assertEquals("LOCAL UPGRADE FIXTURE",r.title);assertEquals("1,3,5",r.silentDayOfWeek)
    assertEquals(1,r.senderList.size);assertEquals(r.senderId,r.senderList.single().id)
    assertEquals(STATUS_OFF,t.status);assertEquals("LOCAL UPGRADE FIXTURE",t.name)
    assertEquals("[]",t.conditions);assertEquals("[]",t.actions)
    assertEquals(1234567890000L,t.lastExecTime.time)
   }
   "cleanup" -> { Core.rule.delete(ruleId);Core.task.delete(taskId) }
   else -> error("Unknown fixture phase")
  }
 }
}
