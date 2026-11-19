package cn.ppps.forwarder.receiver

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.gson.Gson
import cn.ppps.forwarder.App.Companion.CALL_TYPE_MAP
import cn.ppps.forwarder.R
import cn.ppps.forwarder.entity.CallInfo
import cn.ppps.forwarder.entity.MsgInfo
import cn.ppps.forwarder.utils.Log
import cn.ppps.forwarder.utils.PhoneUtils
import cn.ppps.forwarder.utils.SettingUtils
import cn.ppps.forwarder.utils.Worker
import cn.ppps.forwarder.workers.SendWorker
import com.xuexiang.xrouter.utils.TextUtils
import com.xuexiang.xutil.resource.ResUtils.getString
import java.util.Date

open class CallReceiver : PhoneStateReceiver() {

    companion object {
        private val TAG = CallReceiver::class.java.simpleName

        //const val ACTION_IN = "android.intent.action.PHONE_STATE"
        const val ACTION_OUT = "android.intent.action.NEW_OUTGOING_CALL"
        const val EXTRA_PHONE_NUMBER = "android.intent.extra.PHONE_NUMBER"
    }

    //Incoming call alert
    override fun onIncomingCallReceived(context: Context, number: String?, start: Date) {
        Log.d(TAG, "Sensitive event details omitted")
        sendNotice(context, 4, number)
    }

    //Incoming call answered
    override fun onIncomingCallAnswered(context: Context, number: String?, start: Date) {
        Log.d(TAG, "Sensitive event details omitted")
        sendNotice(context, 5, number)
    }

    //Incoming call ended
    override fun onIncomingCallEnded(context: Context, number: String?, start: Date, end: Date) {
        Log.d(TAG, "Sensitive event details omitted")
        sendCallMsg(context, 1, number)
    }

    //Outgoing call dialed
    override fun onOutgoingCallStarted(context: Context, number: String?, start: Date) {
        Log.d(TAG, "Sensitive event details omitted")
        sendNotice(context, 6, number)
    }

    //Outgoing call ended
    override fun onOutgoingCallEnded(context: Context, number: String?, start: Date, end: Date) {
        Log.d(TAG, "Sensitive event details omitted")
        sendCallMsg(context, 2, number)
    }

    //Missed call
    override fun onMissedCall(context: Context, number: String?, start: Date) {
        Log.d(TAG, "Sensitive event details omitted")
        sendCallMsg(context, 3, number)
    }

    //Forward Call Alarm
    private fun sendNotice(context: Context, callType: Int, phoneNumber: String?) {
        if (TextUtils.isEmpty(phoneNumber)) return

        //Judge whether or not to open this type of forward
        if (!callTypeEnabled(callType)) {
            Log.w(TAG, "This type of forwarding is not started，type=$callType")
            return
        }

        val contacts = PhoneUtils.getContactByNumber(phoneNumber)
        val contactName = if (contacts.isNotEmpty()) contacts[0].name else getString(R.string.unknown_number)

        val msg = StringBuilder()
        msg.append(getString(R.string.contact)).append(contactName).append("\n")
        msg.append(getString(R.string.mandatory_type))
        msg.append(CALL_TYPE_MAP[callType.toString()] ?: getString(R.string.unknown_call))

        val msgInfo = MsgInfo("call", phoneNumber.toString(), msg.toString(), Date(), "", -1, 0, callType)
        val request = OneTimeWorkRequestBuilder<SendWorker>().setInputData(
            workDataOf(
                Worker.SEND_MSG_INFO to Gson().toJson(msgInfo)
            )
        ).build()
        WorkManager.getInstance(context).enqueue(request)
    }

    private fun callTypeEnabled(callType: Int) = when (callType) {
        1 -> SettingUtils.enableCallType1
        2 -> SettingUtils.enableCallType2
        3 -> SettingUtils.enableCallType3
        4 -> SettingUtils.enableCallType4
        5 -> SettingUtils.enableCallType5
        6 -> SettingUtils.enableCallType6
        else -> false
    }

    //Transmitting call records
    private fun sendCallMsg(context: Context, callType: Int, phoneNumber: String?) {
        // Nothing to forward when this event is not selected (also covers 'master on, no event selected').
        if (!callTypeEnabled(callType)) {
            Log.w(TAG, "This type of forwarding is not started，type=$callType")
            return
        }
        //You have to go to sleep to get a call record, or you might get the last call.
        Thread.sleep(1000)

        //Get the latter call log.
        Log.d(TAG, "Sensitive event details omitted")
        val callInfo: CallInfo? = PhoneUtils.getLastCallInfo(callType, phoneNumber)
        if (callInfo?.number == null) {
            Log.w(TAG, "No call record found; send the notification directly")
            sendNotice(context, callType, phoneNumber)
            return
        }

        //Judge whether or not to open this type of forward
        if ((callInfo.type == 1 && !SettingUtils.enableCallType1) || (callInfo.type == 2 && !SettingUtils.enableCallType2) || (callInfo.type == 3 && !SettingUtils.enableCallType3)) {
            Log.w(TAG, "This type of forwarding is not started，type=" + callInfo.type)
            return
        }

        //SIM slotid：-1=retrieval failed,0=SIM slot1,1=SIM slot2
        val simSlot = callInfo.simId
        //Get SIM slot information
        val simInfo = when (simSlot) {
            0 -> "SIM1_" + SettingUtils.extraSim1
            1 -> "SIM2_" + SettingUtils.extraSim2
            else -> ""
        }

        //Get contact name
        if (TextUtils.isEmpty(callInfo.name)) {
            val contacts = PhoneUtils.getContactByNumber(phoneNumber)
            callInfo.name = if (contacts.isNotEmpty()) contacts[0].name else getString(R.string.unknown_number)
        }

        val msgInfo = MsgInfo("call", callInfo.number, PhoneUtils.getCallMsg(callInfo), Date(), simInfo, simSlot, callInfo.subId, callType)
        val request = OneTimeWorkRequestBuilder<SendWorker>().setInputData(
            workDataOf(
                Worker.SEND_MSG_INFO to Gson().toJson(msgInfo)
            )
        ).build()
        WorkManager.getInstance(context).enqueue(request)

    }

}