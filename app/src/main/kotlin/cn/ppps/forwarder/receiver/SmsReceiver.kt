package cn.ppps.forwarder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.gson.Gson
import cn.ppps.forwarder.App
import cn.ppps.forwarder.entity.MsgInfo
import cn.ppps.forwarder.utils.Log
import cn.ppps.forwarder.utils.PhoneUtils
import cn.ppps.forwarder.utils.SettingUtils
import cn.ppps.forwarder.utils.SmsCommandUtils
import cn.ppps.forwarder.utils.Worker
import cn.ppps.forwarder.workers.SendWorker
import com.xuexiang.xrouter.utils.TextUtils
import java.util.Date

//SMS.
@Suppress("PrivatePropertyName", "UNUSED_PARAMETER")
class SmsReceiver : BroadcastReceiver() {

    private var TAG = SmsReceiver::class.java.simpleName
    private var from = ""
    private var msg = ""

    override fun onReceive(context: Context, intent: Intent) {
        try {
            //Pure client mode
            if (SettingUtils.enablePureClientMode) return

            //Filter Radio
            if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION
                && intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION
                && intent.action != Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION
                && intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION
            ) return

            if (intent.action == Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION || intent.action == Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) {
                val contentType = intent.type
                if (contentType == "application/vnd.wap.mms-message") {
                    val pduType = intent.getStringExtra("transactionId")
                    if ("mms" == pduType) {
                        val data = intent.getByteArrayExtra("data")
                        if (data != null) {
                            // Processing of received MMS Data
                            handleMmsData(context, data)
                        }
                    }
                }

                from = intent.getStringExtra("address") ?: ""
            } else {
                for (smsMessage in Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
                    from = smsMessage.displayOriginatingAddress
                    msg += smsMessage.messageBody
                }
            }

            //SMS Command
            if (SettingUtils.enableSmsCommand && msg.startsWith("smsf#")) {
                doSmsCommand(context, from, msg)
                return
            }

            //Total Switches
            if (!SettingUtils.enableSms) return

            //TODO：Get accurate SIM slot information; testing found only subscription Relatively reliable.
            val slot = intent.extras?.getInt("slot") ?: -1
            val simId = intent.extras?.getInt("simId") ?: slot
            val subscription = intent.extras?.getInt("subscription") ?: simId
            Log.d(TAG, "slot = $slot, simId = $simId, subscription = $subscription")

            //SIM slotid：-1=retrieval failed,0=SIM slot1,1=SIM slot2
            var simSlot = -1
            //Prefer to custom slot information
            if (SettingUtils.subidSim1 > 0 || SettingUtils.subidSim2 > 0) {
                simSlot = if (subscription == SettingUtils.subidSim1) 0 else 1
            } else {
                //Get SIM slot information
                if (App.SimInfoList.isEmpty()) {
                    App.SimInfoList = PhoneUtils.getSimMultiInfo()
                }
                Log.d(TAG, "SIM inventory refreshed; details omitted")

                if (App.SimInfoList.isNotEmpty()) {
                    for (simInfo in App.SimInfoList.values) {
                        if (simInfo.mSubscriptionId == subscription) {
                            simSlot = simInfo.mSimSlotIndex
                            break
                        }
                    }
                }
            }

            //Get SIM slot information
            val simInfo = when (simSlot) {
                0 -> "SIM1_" + SettingUtils.extraSim1
                1 -> "SIM2_" + SettingUtils.extraSim2
                else -> ""
            }

            val msgInfo = MsgInfo("sms", from, msg, Date(), simInfo, simSlot, subscription)

            val request = OneTimeWorkRequestBuilder<SendWorker>().setInputData(
                workDataOf(
                    Worker.SEND_MSG_INFO to Gson().toJson(msgInfo)
                )
            ).build()
            WorkManager.getInstance(context).enqueue(request)

        } catch (e: Exception) {
            Log.e(TAG, "Parsing SMS failed: " + e.message.toString())
        }
    }

    //Processing SMS Commands
    private fun doSmsCommand(context: Context, from: String, message: String) {
        var safePhone = SettingUtils.smsCommandSafePhone
        Log.d(TAG, "SMS allowlist configured; numbers omitted")

        if (!TextUtils.isEmpty(safePhone)) {
            var isSafePhone = false
            safePhone = safePhone.replace(";", ",").replace("；", ",").replace("，", ",").trim()
            for (phone in safePhone.split(",")) {
                if (!TextUtils.isEmpty(phone.trim()) && from.endsWith(phone.trim())) {
                    isSafePhone = true
                    break
                }
            }
            if (!isSafePhone) {
                Log.d(TAG, "SMS sender rejected by allowlist")
                return
            }
        }

        val smsCommand = message.substring(5)
        SmsCommandUtils.execute(context, smsCommand)
    }

    private fun handleMmsData(context: Context, data: ByteArray) {
        try {
            val mmsClass = Class.forName("android.telephony.gsm.SmsMessage")
            val method = mmsClass.getDeclaredMethod("createFromPdu", ByteArray::class.java)
            val pdus = arrayOf(data)
            val messages = mutableListOf<Any>()

            for (pdu in pdus) {
                val message = method.invoke(null, pdu)
                message?.let { messages.add(it) }
            }

            // Process MMS Part of the report
            for (message in messages) {
                // Access MMS Parts of the report
                val parts = message.javaClass.getMethod("getParts").invoke(message) as? Array<*>

                // Iterate MMS Parts of the report
                parts?.forEach { part ->
                    // Type of content to acquire part
                    val contentType = part?.javaClass?.getMethod("getContentType")?.invoke(part) as? String

                    // Processing of text parts
                    if (contentType?.startsWith("text/plain") == true) {
                        val text = part.javaClass.getMethod("getData").invoke(part) as? String
                        // Processing text information
                        if (text != null) {
                            msg += text
                        }
                    }

                    // Processing Image Component
                    if (contentType?.startsWith("image/") == true) {
                        val imageData = part.javaClass.getMethod("getData").invoke(part) as? ByteArray
                        // Process image information
                        if (imageData != null) {
                            // Here you can save image data or do other processing.
                            Log.d(TAG, "Image data received")
                        }
                    }

                    // The other parts can continue to expand as needed.
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "handleMmsData: $e")
        }
    }

}