package cn.ppps.forwarder.service

import android.annotation.SuppressLint
import android.app.Notification
import android.content.ComponentName
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import cn.ppps.forwarder.core.Core
import cn.ppps.forwarder.database.entity.Rule
import cn.ppps.forwarder.entity.MsgInfo
import cn.ppps.forwarder.utils.Log
import cn.ppps.forwarder.utils.PACKAGE_NAME
import cn.ppps.forwarder.utils.SettingUtils
import cn.ppps.forwarder.utils.Worker
import cn.ppps.forwarder.workers.SendWorker
import com.google.gson.Gson
import com.xuexiang.xrouter.utils.TextUtils
import com.xuexiang.xutil.display.ScreenUtils
import java.util.Date


@Suppress("PrivatePropertyName", "DEPRECATION")
class NotificationService : NotificationListenerService() {

    private val TAG: String = NotificationService::class.java.simpleName

    override fun onListenerConnected() {
        Log.d(TAG, "onListenerConnected")
    }

    override fun onListenerDisconnected() {
        //Pure client mode
        if (SettingUtils.enablePureClientMode) return

        //Total Switches
        if (!SettingUtils.enableAppNotify) return

        Log.d(TAG, "Notification listener disconnected - Request rebinding")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            requestRebind(ComponentName(this, NotificationListenerService::class.java))
        }
    }

    @SuppressLint("DiscouragedPrivateApi")
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        try {
            //Pure client mode
            if (SettingUtils.enablePureClientMode) return

            //Unusual Notification Skipped
            val notification = sbn?.notification ?: return
            val extras = notification.extras ?: return

            //Automatically eliminate extraAPPNotifications
            SettingUtils.cancelExtraAppNotify
                .takeIf { it.isNotEmpty() }
                ?.split("\n")
                ?.forEach { app ->
                    if (sbn.packageName == app.trim()) {
                        Log.d(TAG, "Automatically eliminate extraAPPNotifications：$app")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            cancelNotification(sbn.key)
                        } else {
                            cancelNotification(sbn.packageName, sbn.tag, sbn.id)
                        }
                        return@forEach
                    }
                }


            //Total Switches
            if (!SettingUtils.enableAppNotify) return

            //Lock screen only for forwardingAPPNotifications
            if (SettingUtils.enableNotUserPresent && !ScreenUtils.isScreenLock()) return

            val from = sbn.packageName
            //Skip the application's own notifications
            if (PACKAGE_NAME == sbn.packageName) return
            // Title
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            // Content of notice
            var text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
                if (bigText.isNotEmpty()) {
                    text = bigText
                }
            }
            if (text.isEmpty() && notification.tickerText != null) {
                text = notification.tickerText.toString()
            }

            //Do not process empty messages (titles and contents are empty)）
            if (TextUtils.isEmpty(title) && TextUtils.isEmpty(text)) return

            //One blocked keyword per line; regex supported; skip matching titles or content
            if (isInBlacklist(title, text)) {
                Log.d(TAG, "MatchedAPPNotify key word blacklist, skip forward.title=$title, text=$text")
                return
            }

            val msgInfo = MsgInfo("app", from, text, Date(), title, -1)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Log.d(TAG, "Message.UID====>" + sbn.uid)
                msgInfo.uid = sbn.uid
            }
            //TODO：Automatically dismiss notifications (temporary: repeated queries improve accuracy）
            if (SettingUtils.enableCancelAppNotify) {
                val ruleList: List<Rule> = Core.rule.getRuleList(msgInfo.type, 1, "SIM0")
                for (rule in ruleList) {
                    if (rule.checkMsg(msgInfo)) {
                        Log.d(TAG, "Automatically dismiss notification")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            cancelNotification(sbn.key)
                        } else {
                            cancelNotification(sbn.packageName, sbn.tag, sbn.id)
                        }
                        break
                    }
                }
            }

            val request = OneTimeWorkRequestBuilder<SendWorker>().setInputData(
                workDataOf(
                    Worker.SEND_MSG_INFO to Gson().toJson(msgInfo),
                )
            ).build()
            WorkManager.getInstance(applicationContext).enqueue(request)

        } catch (e: Exception) {
            Log.e(TAG, "Parsing Notification failed: " + e.message.toString())
        }

    }

    //Keyword blacklist matches: a key word in a row supports a regular expression, a hit notification header or any one of its contents is returned true
    private fun isInBlacklist(title: String, text: String): Boolean {
        val blacklist = SettingUtils.appNotifyBlacklist
        if (TextUtils.isEmpty(blacklist)) return false

        for (line in blacklist.split("\n")) {
            val keyword = line.trim()
            if (keyword.isEmpty()) continue
            try {
                val regex = Regex(keyword)
                if (regex.containsMatchIn(title) || regex.containsMatchIn(text)) {
                    return true
                }
            } catch (e: Exception) {
                //When regular expression is illegal, downgrade to normal inclusion match
                Log.w(TAG, "Invalid blocked-keyword regex; fall back to substring matching：$keyword, ${e.message}")
                if (title.contains(keyword) || text.contains(keyword)) {
                    return true
                }
            }
        }
        return false
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        Log.d(TAG, "Removed Package Name : ${sbn?.packageName}")
    }

}