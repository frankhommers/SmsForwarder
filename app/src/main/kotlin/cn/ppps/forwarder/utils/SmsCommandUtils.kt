package cn.ppps.forwarder.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import androidx.core.app.ActivityCompat
import cn.ppps.forwarder.App
import cn.ppps.forwarder.core.Core
import cn.ppps.forwarder.server.model.SmsSendData
import cn.ppps.forwarder.service.HttpServerService
import com.google.gson.Gson
import com.xuexiang.xrouter.utils.TextUtils
import com.xuexiang.xutil.XUtil
import com.xuexiang.xutil.security.CipherUtils
import com.xuexiang.xutil.system.DeviceUtils
import frpclib.Frpclib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async

@Suppress("OPT_IN_USAGE", "DeferredResultUnused", "DEPRECATION")
class SmsCommandUtils {

    companion object {

        var TAG = "SmsCommandUtils"

        //Check SMS Commands
        fun check(smsContent: String): Boolean {
            return smsContent.startsWith("smsf#")
        }

        //Execute SMS Command
        fun execute(context: Context, smsCommand: String): Boolean {
            val cmdList = smsCommand.split("#", limit = 3)
            Log.d(TAG, "SMS command received; content omitted")
            if (cmdList.count() < 2) return false

            val function = cmdList[0]
            val action = cmdList[1]
            val param = if (cmdList.count() > 2) cmdList[2] else ""
            when (function) {
                "frpc" -> {
                    if (!App.FrpclibInited) {
                        Log.d(TAG, "Not DownloadedFrpcLibrary")
                        return false
                    }

                    GlobalScope.async(Dispatchers.IO) {
                        val frpcList = if (param.isEmpty()) {
                            Core.frpc.getAutorun()
                        } else {
                            val uids = param.split(",")
                            Core.frpc.getByUids(uids, param)
                        }

                        if (frpcList.isEmpty()) {
                            Log.d(TAG, "There's nothing to operate on.Frpc")
                            return@async
                        }

                        for (frpc in frpcList) {
                            if (action == "start") {
                                if (!Frpclib.isRunning(frpc.uid)) {
                                    val error = Frpclib.runContent(frpc.uid, frpc.config)
                                    if (!TextUtils.isEmpty(error)) {
                                        Log.e(TAG, error)
                                    }
                                }
                            } else if (action == "stop") {
                                if (Frpclib.isRunning(frpc.uid)) {
                                    Frpclib.close(frpc.uid)
                                }
                            }
                        }
                    }
                }

                "httpserver" -> {
                    Intent(context, HttpServerService::class.java).also {
                        if (action == "start") {
                            context.startService(it)
                        } else if (action == "stop") {
                            context.stopService(it)
                        }
                    }
                }

                "system" -> {
                    //To judge if it's gone.root
                    if (!DeviceUtils.isDeviceRooted()) return false

                    // Filter Duplicate Message Mechanism
                    var duplicateMessagesLimits = SettingUtils.duplicateMessagesLimits * 1000L
                    if (duplicateMessagesLimits > 0L) {
                        duplicateMessagesLimits += 10000L //System commands added.10Number of seconds to avoid error
                        val key = CipherUtils.md5(smsCommand)
                        val timestamp: Long = System.currentTimeMillis()
                        var timestampPrev: Long by HistoryUtils(key, timestamp)
                        Log.d(TAG, "duplicateMessagesLimits=$duplicateMessagesLimits, timestamp=$timestamp, timestampPrev=$timestampPrev")
                        if (timestampPrev != timestamp && timestamp - timestampPrev <= duplicateMessagesLimits) {
                            Log.e(TAG, "Filter Duplicate Message Mechanism")
                            timestampPrev = timestamp
                            return false
                        }
                        timestampPrev = timestamp
                    }

                    if (action == "reboot") {
                        DeviceUtils.reboot()
                    } else if (action == "shutdown") {
                        DeviceUtils.shutdown()
                    }
                }

                "wifi" -> {
                    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    if (action == "on") {
                        wifiManager.isWifiEnabled = true
                    } else if (action == "off") {
                        wifiManager.isWifiEnabled = false
                    }
                }

                "sms" -> {
                    if (action == "send") {
                        if (TextUtils.isEmpty(param)) return false

                        try {
                            val gson = Gson()
                            val smsSendData = gson.fromJson(param, SmsSendData::class.java)

                            //Get SIM slot information
                            if (App.SimInfoList.isEmpty()) {
                                App.SimInfoList = PhoneUtils.getSimMultiInfo()
                            }
                            Log.d(TAG, "SIM inventory refreshed; details omitted")

                            //Send card slots: 1=SIM1, 2=SIM2
                            val simSlotIndex = smsSendData.simSlot - 1
                            //TODO：Use the default slot to send if the slot information is not available
                            val mSubscriptionId: Int = App.SimInfoList[simSlotIndex]?.mSubscriptionId ?: -1

                            if (ActivityCompat.checkSelfPermission(XUtil.getContext(), Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                                return false
                            }
                            PhoneUtils.sendSms(mSubscriptionId, smsSendData.phoneNumbers, smsSendData.msgContent)
                        } catch (e: Exception) {
                            Log.e(TAG, "Parsing SMS failed: " + e.message.toString())
                        }
                    }
                }
            }

            return true
        }
    }
}