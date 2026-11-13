package cn.ppps.forwarder.utils

import android.Manifest.permission
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.SmsManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.text.TextUtils
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import cn.ppps.forwarder.App
import cn.ppps.forwarder.R
import cn.ppps.forwarder.core.Core
import cn.ppps.forwarder.entity.CallInfo
import cn.ppps.forwarder.entity.ContactInfo
import cn.ppps.forwarder.entity.SimInfo
import cn.ppps.forwarder.entity.SmsInfo
import com.xuexiang.xutil.XUtil
import com.xuexiang.xutil.app.IntentUtils
import com.xuexiang.xutil.data.DateUtils
import com.xuexiang.xutil.resource.ResUtils.getString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.min

@Suppress("DEPRECATION")
class PhoneUtils private constructor() {

    companion object {
        const val TAG = "PhoneUtils"

        /** Access sim Number of SIM slots, not sim Number of cards.*/
        fun getSimSlotCount() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            (App.context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager).activeModemCount
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            (App.context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager).phoneCount
        else
            -1

        //Get multi-SIM information
        @SuppressLint("Range")
        fun getSimMultiInfo(): MutableMap<Int, SimInfo> {
            val infoList = HashMap<Int, SimInfo>()
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    Log.d(TAG, "1.Versions Over5.1，Call the system method")
                    val mSubscriptionManager = XUtil.getContext().getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
                    ActivityCompat.checkSelfPermission(
                        XUtil.getContext(), permission.READ_PHONE_STATE
                    )
                    val activeSubscriptionInfoList: List<SubscriptionInfo>? = mSubscriptionManager.activeSubscriptionInfoList
                    if (!activeSubscriptionInfoList.isNullOrEmpty()) {
                        //1.1.1 If you have a card, go through all the cards.
                        for (subscriptionInfo in activeSubscriptionInfoList) {
                            val simInfo = SimInfo()
                            simInfo.mCarrierName = subscriptionInfo.carrierName?.toString()
                            simInfo.mIccId = subscriptionInfo.iccId?.toString()
                            simInfo.mSimSlotIndex = subscriptionInfo.simSlotIndex
                            simInfo.mNumber = subscriptionInfo.number?.toString()
                            simInfo.mCountryIso = subscriptionInfo.countryIso?.toString()
                            simInfo.mSubscriptionId = subscriptionInfo.subscriptionId
                            Log.d(TAG, simInfo.toString())
                            infoList[simInfo.mSimSlotIndex] = simInfo
                        }
                    }
                } else {
                    Log.d(TAG, "2.Version is lower than5.1The system, call the database first, see if it can be accessed.")
                    val uri = Uri.parse("content://telephony/siminfo") //Accessraw_contactsTable
                    val resolver: ContentResolver = XUtil.getContext().contentResolver
                    val cursor = resolver.query(
                        uri, arrayOf(
                            "_id", "icc_id", "sim_id", "display_name", "carrier_name", "name_source", "color", "number", "display_number_format", "data_roaming", "mcc", "mnc"
                        ), null, null, null
                    )
                    if (cursor != null && cursor.moveToFirst()) {
                        do {
                            val simInfo = SimInfo()
                            simInfo.mCarrierName = cursor.getString(cursor.getColumnIndex("carrier_name"))
                            simInfo.mIccId = cursor.getString(cursor.getColumnIndex("icc_id"))
                            simInfo.mSimSlotIndex = cursor.getInt(cursor.getColumnIndex("sim_id"))
                            simInfo.mNumber = cursor.getString(cursor.getColumnIndex("number"))
                            simInfo.mCountryIso = cursor.getString(cursor.getColumnIndex("mcc"))
                            //val id = cursor.getString(cursor.getColumnIndex("_id"))
                            Log.d(TAG, simInfo.toString())
                            infoList[simInfo.mSimSlotIndex] = simInfo
                        } while (cursor.moveToNext())
                        cursor.close()
                    }
                }
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
                Log.e(TAG, "getSimMultiInfo:", e)
            }
            //Still can't get it/Only one->Remove Remarks
            /*if (infoList.isEmpty() || infoList.size == 1) {
                Log.d(TAG, "3.Take directly the data from the comment box as information")
                //It's empty. Neither card has access to information.
                if (infoList.isEmpty()) {
                    //card1Note information is not empty
                    val etExtraSim1 = SettingUtils.extraSim1
                    if (!TextUtils.isEmpty(etExtraSim1)) {
                        val simInfo1 = SimInfo()
                        //card1
                        simInfo1.mSimSlotIndex = 0
                        simInfo1.mNumber = etExtraSim1
                        simInfo1.mSubscriptionId = SettingUtils.subidSim1
                        //Put the card in.
                        infoList[simInfo1.mSimSlotIndex] = simInfo1
                    }
                    //card2Note information is not empty
                    val etExtraSim2 = SettingUtils.extraSim2
                    if (!TextUtils.isEmpty(etExtraSim2)) {
                        val simInfo2 = SimInfo()
                        simInfo2.mSimSlotIndex = 1
                        simInfo2.mNumber = etExtraSim2
                        simInfo2.mSubscriptionId = SettingUtils.subidSim2
                        //Put the card in.
                        infoList[simInfo2.mSimSlotIndex] = simInfo2
                    }

                    //There's a card.,Determine the SIM slot
                } else {
                    var infoListIndex = -1
                    for (obj in infoList) {
                        infoListIndex = obj.key
                    }
                    //Get to Card1，And card2Note information is not empty
                    if (infoListIndex == 0 && !TextUtils.isEmpty(SettingUtils.extraSim2)) {
                        //Get to Card1Information, card2Note is not empty, create card2Entities
                        val simInfo2 = SimInfo()
                        simInfo2.mSimSlotIndex = 1
                        simInfo2.mNumber = SettingUtils.extraSim2
                        simInfo2.mSubscriptionId = SettingUtils.subidSim1
                        infoList[simInfo2.mSimSlotIndex] = simInfo2
                    } else if (infoListIndex == 1 && !TextUtils.isEmpty(SettingUtils.extraSim1)) {
                        //Get to Card2Information, card1Note is not empty, create card1Entities
                        val simInfo1 = SimInfo()
                        simInfo1.mSimSlotIndex = 0
                        simInfo1.mNumber = SettingUtils.extraSim1
                        simInfo1.mSubscriptionId = SettingUtils.subidSim1
                        infoList[simInfo1.mSimSlotIndex] = simInfo1
                    }
                }
            }*/
            Log.i(TAG, infoList.toString())
            return infoList
        }

        //Getting Device Name
        fun getDeviceName(): String {
            return try {
                Settings.Secure.getString(XUtil.getContentResolver(), "bluetooth_name")
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e(TAG, "getDeviceName:", e)
                Build.BRAND + " " + Build.MODEL
            }
        }

        /**
         * Send SMS
         * <p>Permissions to add {@code <uses-permission android:name="android.permission.SEND_SMS" />}</p>
         *
         * @param subId SendersubId，Import -1 then SmsManager.getDefault()
         * @param mobileList List of Receiving Numbers
         * @param message     Text Message Contents
         */
        @Suppress("DEPRECATION")
        @SuppressLint("SoonBlockedPrivateApi", "DiscouragedPrivateApi")
        @RequiresPermission(permission.SEND_SMS)
        fun sendSms(subId: Int, mobileList: String, message: String): String? {
            if (TextUtils.isEmpty(mobileList) || TextUtils.isEmpty(message)) {
                Log.e(TAG, "mobileList or message is empty!")
                return "mobileList or message is empty!"
            }

            val mobiles = mobileList.replace("；", ";").replace("，", ";").replace(",", ";")
            Log.d(TAG, "subId = $subId, mobiles = $mobiles, message = $message")
            val mobileArray = mobiles.split(";".toRegex()).toTypedArray()
            for (mobile in mobileArray) {
                Log.d(TAG, "mobile = $mobile")
                if (!isValidPhoneNumber(mobile)) {
                    Log.e(TAG, "mobile ($mobile) is invalid!")
                    continue
                }

                try {
                    val sendFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_ONE_SHOT
                    val sendPI = PendingIntent.getBroadcast(XUtil.getContext(), 0, Intent(), sendFlags)

                    val smsManager = if (subId > -1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) SmsManager.getSmsManagerForSubscriptionId(
                        subId
                    ) else SmsManager.getDefault()
                    // Android 5.1.1 Use reflection to assign a slot below
                    if (subId > -1 && Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
                        Log.d(TAG, "Android 5.1.1 Use reflection to assign a slot below")
                        val clz = SmsManager::class.java
                        val field = clz.getDeclaredField("mSubId") // The reflection gets the variable.
                        field.isAccessible = true // Modify permissions to read and write
                        field.set(smsManager, subId)
                    }

                    // Cut long text messages.
                    if (message.length >= 70) {
                        val deliverFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) PendingIntent.FLAG_IMMUTABLE else 0
                        val deliverPI = PendingIntent.getBroadcast(
                            XUtil.getContext(), 0, Intent("DELIVERED_SMS_ACTION"), deliverFlags
                        )

                        val sentPendingIntents = ArrayList<PendingIntent>()
                        val deliveredPendingIntents = ArrayList<PendingIntent>()
                        // Android 16/17 Part of ROM（ColorOS 17 / MagicOS Wait!，divideMessage() Internal
                        // It's called. TelephonyManager.getGroupIdLevel1() and throw, preventing long Unicode SMS messages from being sent（issue #773），
                        // It's down here to local cut.
                        val divideContents = try {
                            smsManager.divideMessage(message)
                        } catch (e: Exception) {
                            Log.e(TAG, "divideMessage() failed: ${e.message}, fallback to local split")
                            splitLongMessage(message)
                        }

                        for (i in divideContents.indices) {
                            sentPendingIntents.add(i, sendPI)
                            deliveredPendingIntents.add(i, deliverPI)
                        }
                        smsManager.sendMultipartTextMessage(
                            mobile, null, divideContents, sentPendingIntents, deliveredPendingIntents
                        )
                    } else {
                        smsManager.sendTextMessage(mobile, null, message, sendPI, null)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "sendSms failed: ${e.message}", e)
                    return e.message.toString()
                }
            }

            return null
        }

        // GSM 03.38 characters requiring escape (occupy 2 items septet）or a character that does not exist in the standard table, cannot be pressed when it appears 7bit Accurate Count
        private const val GSM_7BIT_UNSAFE_CHARS = "^{}\\[\\]|~`"

        /**
         * Split long SMS locally（SmsManager.divideMessage() Fallback when unavailable）
         *
         * Android 16/17 Part of ROM（ColorOS 17 / MagicOS etc.) when splittingUCS2 Encoded + multipart messages，
         * Call in the frame TelephonyManager.getGroupIdLevel1() and throw, causing long SMS sending to fail（issue #773）.
         *
         * Single Text Length As GSM 7bit 160 / UCS2 70 characters; each segment after having been split into multiple segments also needs 6 Bytes UDH Head，
         * so multipart limits are respectively 153 / 67 a character.
         * To avoid counting deviations leading to excessive length, if you can't press GSM 7bit The exact number of characters, then press the shorter. UCS2 Length cut.
         */
        private fun splitLongMessage(message: String): ArrayList<String> {
            val result = ArrayList<String>()
            if (message.isEmpty()) return result

            val gsm7Bit = message.none { it > '\u007F' || GSM_7BIT_UNSAFE_CHARS.indexOf(it) >= 0 }
            val singleLimit = if (gsm7Bit) 160 else 70
            val multiLimit = if (gsm7Bit) 153 else 67

            if (message.length <= singleLimit) {
                result.add(message)
                return result
            }

            var start = 0
            while (start < message.length) {
                val end = minOf(start + multiLimit, message.length)
                result.add(message.substring(start, end))
                start = end
            }
            return result
        }

        //Retrieving call log list
        fun getCallInfoList(
            type: Int, limit: Int, offset: Int, phoneNumber: String?
        ): MutableList<CallInfo> {
            val callInfoList: MutableList<CallInfo> = mutableListOf()
            try {
                var selection = "1=1"
                val selectionArgs = ArrayList<String>()
                if (type > 0) {
                    selection += " and " + CallLog.Calls.TYPE + " = ?"
                    selectionArgs.add("$type")
                }
                if (!TextUtils.isEmpty(phoneNumber)) {
                    selection += " and " + CallLog.Calls.NUMBER + " like ?"
                    selectionArgs.add("%$phoneNumber%")
                }
                Log.d(TAG, "selection = $selection")
                Log.d(TAG, "selectionArgs = $selectionArgs")

                //Retrieve all records, then paginate locally for compatibility
                val cursor = Core.app.contentResolver.query(
                    CallLog.Calls.CONTENT_URI, null, selection, selectionArgs.toTypedArray(), CallLog.Calls.DEFAULT_SORT_ORDER // + " limit $limit offset $offset"
                ) ?: return callInfoList
                Log.i(TAG, "cursor count:" + cursor.count)

                // Avoid loop-out after exceeding the total
                if (cursor.count == 0 || offset >= cursor.count) {
                    cursor.close()
                    return callInfoList
                }

                if (cursor.moveToFirst()) {
                    Log.d(TAG, "Call ColumnNames=${cursor.columnNames.contentToString()}")
                    val indexName = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                    val indexNumber = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                    val indexDate = cursor.getColumnIndex(CallLog.Calls.DATE)
                    val indexDuration = cursor.getColumnIndex(CallLog.Calls.DURATION)
                    val indexType = cursor.getColumnIndex(CallLog.Calls.TYPE)
                    val indexViaNumber = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && cursor.getColumnIndex("via_number") != -1) cursor.getColumnIndex("via_number") else -1
                    var indexSimId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) cursor.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID) else -1
                    var indexSubId = indexSimId
                    //All over the list with`forward`Fields
                    val forwardColumns = cursor.columnNames.filter { it.contains("forward", ignoreCase = true) }

                    /**
                     * TODO:Device-specific SIM slot identification
                     * MIUISystem：simid Field actually is subscription_id
                     * EMUISystem：subscription_id Actual sim_id
                     */
                    var isSimId = false
                    val manufacturer = Build.MANUFACTURER.lowercase(Locale.getDefault())
                    Log.i(TAG, "manufacturer = $manufacturer")
                    if (manufacturer.contains(Regex(pattern = "xiaomi|redmi"))) {
                        if (cursor.getColumnIndex("simid") != -1) indexSimId = cursor.getColumnIndex("simid")
                        indexSubId = indexSimId
                    } else if (manufacturer.contains(Regex(pattern = "huawei|honor"))) {
                        indexSubId = -1 //TODO:Huawei is not yet supported
                        isSimId = true
                    }

                    var curOffset = 0
                    do {
                        if (curOffset >= offset) {
                            // Iterate forwardColumns Fields, find appropriate isForwarded Fields
                            var isForwarded = false;
                            for (forwardColumn in forwardColumns) {
                                val forwardIndex = cursor.getColumnIndex(forwardColumn)
                                if (forwardIndex != -1) {
                                    val forwardedValue = cursor.getInt(forwardIndex)
                                    Log.d(TAG, "forwardColumn = $forwardColumn, forwardedValue = $forwardedValue")
                                    if (forwardedValue == 1) {
                                        isForwarded = true
                                        break
                                    }
                                }
                            }

                            val callInfo = CallInfo(
                                cursor.getString(indexName) ?: "",  //Name
                                cursor.getString(indexNumber) ?: "",  //Number
                                cursor.getLong(indexDate),  //Get Call Date
                                cursor.getInt(indexDuration),  //Call duration in seconds
                                cursor.getInt(indexType),  //Retrieving Call Type：1.Call in 2.Outgoing 3.Missed
                                if (indexViaNumber != -1) cursor.getString(indexViaNumber) else "",  //Source number
                                if (indexSimId != -1) getSimId(cursor.getInt(indexSimId), isSimId) else -1,  //SIM slotID： 0=Sim1, 1=Sim2, -1=Failed to fetch
                                if (indexSubId != -1) cursor.getInt(indexSubId) else 0,  //SIM subscription ID
                                isForwarded //Whether the call was forwarded
                            )
                            Log.d(TAG, callInfo.toString())
                            callInfoList.add(callInfo)
                            if (limit == 1) {
                                cursor.close()
                                return callInfoList
                            }
                        }
                        curOffset++
                        if (curOffset >= offset + limit) break
                    } while (cursor.moveToNext())
                    if (!cursor.isClosed) cursor.close()
                }
            } catch (e: java.lang.Exception) {
                Log.e(TAG, "getCallInfoList:", e)
            }

            return callInfoList
        }

        //Get the latter call log.
        @SuppressLint("Range")
        fun getLastCallInfo(callType: Int, phoneNumber: String?): CallInfo? {
            val callInfoList = getCallInfoList(callType, 1, 0, phoneNumber)
            if (callInfoList.isNotEmpty()) return callInfoList[0]
            return null
        }

        //Get contacts
        fun getContactInfoList(
            limit: Int, offset: Int, phoneNumber: String?, name: String?, isFuzzy: Boolean = true
        ): MutableList<ContactInfo> {
            val contactInfoList: MutableList<ContactInfo> = mutableListOf()

            try {
                var selection = "1=1"
                val selectionArgs = ArrayList<String>()
                if (!TextUtils.isEmpty(phoneNumber)) {
                    selection += " and replace(replace(" + ContactsContract.CommonDataKinds.Phone.NUMBER + ",' ',''),'-','') like ?"
                    if (isFuzzy) {
                        selectionArgs.add("%$phoneNumber%")
                    } else {
                        selectionArgs.add("%$phoneNumber")
                    }
                }
                if (!TextUtils.isEmpty(name)) {
                    selection += " and " + ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " like ?"
                    selectionArgs.add("%$name%")
                }
                Log.d(TAG, "selection = $selection")
                Log.d(TAG, "selectionArgs = $selectionArgs")

                val cursor = Core.app.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, selection, selectionArgs.toTypedArray(), ContactsContract.CommonDataKinds.Phone.SORT_KEY_PRIMARY
                ) ?: return contactInfoList
                Log.i(TAG, "cursor count:" + cursor.count)

                // Avoid loop-out after exceeding the total
                if (cursor.count == 0 || offset >= cursor.count) {
                    cursor.close()
                    return contactInfoList
                }

                if (cursor.moveToFirst()) {
                    val displayNameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val mobileNoIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    do {
                        val contactInfo = ContactInfo(
                            cursor.getString(displayNameIndex),  //Name
                            cursor.getString(mobileNoIndex),  //Number
                        )
                        Log.d(TAG, contactInfo.toString())
                        contactInfoList.add(contactInfo)
                        if (limit == 1) {
                            cursor.close()
                            return contactInfoList
                        }
                    } while (cursor.moveToNext())
                    if (!cursor.isClosed) cursor.close()
                }
            } catch (e: java.lang.Exception) {
                Log.e(TAG, "getContactInfoList:", e)
            }

            return contactInfoList
        }

        // Never send phone numbers to an external lookup service.
        @Suppress("UNUSED_PARAMETER")
        fun getPhoneArea(phoneNumber: String): String = getString(R.string.unknown_area)

        //Get contact name
        fun getContactByNumber(phoneNumber: String?): MutableList<ContactInfo> {
            val contactInfoList = mutableListOf<ContactInfo>()
            if (TextUtils.isEmpty(phoneNumber)) return contactInfoList

            // Remove characters such as international area numbers, spaces, brackets, lines
            val normalizedInputNumber = if (phoneNumber!!.startsWith("+") && phoneNumber.length > 4) {
                phoneNumber.substring(4).replace("[^0-9]".toRegex(), "")
            } else {
                phoneNumber.replace("[^0-9]".toRegex(), "")
            }

            contactInfoList.addAll(getContactInfoList(99, 0, normalizedInputNumber, null, false))
            if (contactInfoList.isEmpty() || contactInfoList.size == 1) {
                return contactInfoList
            }

            // Calculates the length and priority of each contact
            val scoredContacts = contactInfoList.map { contact ->
                //Remove characters such as spaces, brackets, lines
                val normalizedContactNumber = contact.phoneNumber.replace("[^0-9]".toRegex(), "")
                val matchLength = calculateMatchLength(normalizedInputNumber, normalizedContactNumber)
                // Priority rule：
                // 1. Full match (entry cell number fully consistent with contact cell number): Priority 2
                // 2. Match length equals input cell number length: priority 1
                // 3. Other cases: priority 0
                val priority = when {
                    normalizedInputNumber == normalizedContactNumber -> 2
                    matchLength == normalizedInputNumber.length -> 1
                    else -> 0
                }
                contact to Pair(matchLength, priority)
            }.sortedWith(compareByDescending<Pair<ContactInfo, Pair<Int, Int>>> { it.second.first } // Sort by descending match length
                .thenByDescending { it.second.second }) // Then by descending priority

            // Returns the list of contacts with the longest length and highest priority
            val maxMatchLength = scoredContacts.first().second.first
            val maxPriority = scoredContacts.first().second.second
            return scoredContacts
                .filter { it.second.first == maxMatchLength && it.second.second == maxPriority }
                .map { it.first }
                .toMutableList()
        }

        // Calculates the right-to-left match length
        private fun calculateMatchLength(number1: String, number2: String): Int {
            var matchLength = 0
            val minLength = min(number1.length, number2.length)

            // Compare right-to-left
            for (i in 1..minLength) {
                if (number1[number1.length - i] == number2[number2.length - i]) {
                    matchLength++
                } else {
                    break // encounters a mismatch character, stop comparing
                }
            }

            return matchLength
        }

        //Get call records forwarding content
        fun getCallMsg(callInfo: CallInfo): String {
            val sb = StringBuilder()
            sb.append(getString(R.string.contact)).append(callInfo.name).append("\n")
            if (!TextUtils.isEmpty(callInfo.viaNumber)) sb.append(getString(R.string.via_number)).append(callInfo.viaNumber).append("\n")
            if (callInfo.dateLong > 0L) sb.append(getString(R.string.call_date)).append(
                DateUtils.millis2String(
                    callInfo.dateLong, SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                )
            ).append("\n")
            if (callInfo.duration > 0) {
                if (callInfo.type == 3) {
                    sb.append(getString(R.string.ring_duration))
                } else {
                    sb.append(getString(R.string.call_duration))
                }
                sb.append(callInfo.duration).append("s\n")
            }
            sb.append(getString(R.string.mandatory_type))
            //Call type：1.Incoming call ended 2.Outgoing call ended 3.Missed call 4.Incoming call alert 5.Incoming call answered 6.Outgoing call dialed
            when (callInfo.type) {
                1 -> sb.append(getString(R.string.incoming_call_ended))
                2 -> sb.append(getString(R.string.outgoing_call_ended))
                3 -> sb.append(getString(R.string.missed_call))
                4 -> sb.append(getString(R.string.incoming_call_received))
                5 -> sb.append(getString(R.string.incoming_call_answered))
                6 -> sb.append(getString(R.string.outgoing_call_started))
                else -> sb.append(getString(R.string.unknown_call))
            }
            if (callInfo.isForwarded) {
                sb.append("\n").append(getString(R.string.forwarding_call)).append(getString(R.string.lab_yes))
            }
            return sb.toString()
        }

        // Get user SMS list
        fun getSmsInfoList(
            type: Int, limit: Int, offset: Int, keyword: String
        ): MutableList<SmsInfo> {
            val smsInfoList: MutableList<SmsInfo> = mutableListOf()
            try {
                var selection = "1=1"
                val selectionArgs = ArrayList<String>()
                if (type > 0) {
                    selection += " and type = ?"
                    selectionArgs.add("$type")
                }
                if (!TextUtils.isEmpty(keyword)) {
                    selection += " and body like ?"
                    selectionArgs.add("%$keyword%")
                }
                Log.d(TAG, "selection = $selection")
                Log.d(TAG, "selectionArgs = $selectionArgs")

                // Avoid loop-out after exceeding the total
                val cursorTotal = Core.app.contentResolver.query(
                    Uri.parse("content://sms/"), null, selection, selectionArgs.toTypedArray(), "date desc"
                ) ?: return smsInfoList
                if (offset >= cursorTotal.count) {
                    cursorTotal.close()
                    return smsInfoList
                }

                val cursor = Core.app.contentResolver.query(
                    Uri.parse("content://sms/"), null, selection, selectionArgs.toTypedArray(), "date desc limit $limit offset $offset"
                ) ?: return smsInfoList

                Log.i(TAG, "cursor count:" + cursor.count)
                if (cursor.count == 0) {
                    cursor.close()
                    return smsInfoList
                }

                if (cursor.moveToFirst()) {
                    Log.d(TAG, "SMS ColumnNames=${cursor.columnNames.contentToString()}")
                    val indexAddress = cursor.getColumnIndex("address")
                    val indexBody = cursor.getColumnIndex("body")
                    val indexDate = cursor.getColumnIndex("date")
                    val indexType = cursor.getColumnIndex("type")
                    var indexSimId = cursor.getColumnIndex("sim_id")
                    var indexSubId = cursor.getColumnIndex("sub_id")

                    /**
                     * TODO:Device-specific SIM slot identification
                     * MIUISystem：sim_id Field actually is subscription_id
                     * EMUISystem：sub_id Actual sim_id
                     */
                    var isSimId = false
                    val manufacturer = Build.MANUFACTURER.lowercase(Locale.getDefault())
                    Log.i(TAG, "manufacturer = $manufacturer")
                    if (manufacturer.contains(Regex(pattern = "xiaomi|redmi"))) {
                        indexSubId = cursor.getColumnIndex("sim_id")
                    } else if (manufacturer.contains(Regex(pattern = "huawei|honor"))) {
                        indexSimId = cursor.getColumnIndex("sub_id")
                        isSimId = true
                    }

                    do {
                        val smsInfo = SmsInfo()
                        val phoneNumber = cursor.getString(indexAddress)
                        // Ask for username by cell phone number
                        val contacts = getContactByNumber(phoneNumber)
                        smsInfo.name = if (contacts.isNotEmpty()) contacts[0].name else getString(R.string.unknown_number)
                        // Contact number
                        smsInfo.number = phoneNumber
                        // Text Message Contents
                        smsInfo.content = cursor.getString(indexBody)
                        // SMS Time
                        smsInfo.date = cursor.getLong(indexDate)
                        // SMS Type: 1=Receive, 2=Send
                        smsInfo.type = cursor.getInt(indexType)
                        // SIM slotID： 0=Sim1, 1=Sim2, -1=Failed to fetch
                        smsInfo.simId = if (indexSimId != -1) getSimId(cursor.getInt(indexSimId), isSimId) else -1
                        // SIM subscription ID
                        smsInfo.subId = if (indexSubId != -1) cursor.getInt(indexSubId) else 0

                        smsInfoList.add(smsInfo)
                    } while (cursor.moveToNext())

                    if (!cursorTotal.isClosed) cursorTotal.close()
                    if (!cursor.isClosed) cursor.close()
                }
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
                Log.e(TAG, "getSmsInfoList:", e)
            }
            return smsInfoList
        }

        /**
         * Go to Dial Interface
         *
         * @param phoneNumber Telephone number
         */
        fun dial(phoneNumber: String?) {
            XUtil.getContext().startActivity(IntentUtils.getDialIntent(phoneNumber, true))
        }

        /**
         * Call.
         *
         * Permissions to add `<uses-permission android:name="android.permission.CALL_PHONE" />`
         *
         * @param phoneNumber Telephone number
         */
        fun call(phoneNumber: String?) {
            XUtil.getContext().startActivity(IntentUtils.getCallIntent(phoneNumber, true))
        }

        /**
         * Move subscription_id Convert to SIM slotID： 0=Sim1, 1=Sim2, -1=Failed to fetch
         *
         * TODO: There's a pit here. Each brand custom system field is different. It doesn't have access to the slots.ID
         * Test passed.：MIUI   Test failed: native Android 11（Google Pixel 2 XL）
         *
         * @param mId SubscriptionId
         * @param isSimId Is it already?SimIdNo conversion required (to-be compatible)）
         */
        private fun getSimId(mId: Int, isSimId: Boolean): Int {
            Log.i(TAG, "mId = $mId, isSimId = $isSimId")
            if (isSimId) return mId

            if (SettingUtils.subidSim1 > 0 || SettingUtils.subidSim2 > 0) {
                return if (mId == SettingUtils.subidSim1) 0 else 1
            } else {
                //Get SIM slot information
                if (App.SimInfoList.isEmpty()) {
                    App.SimInfoList = getSimMultiInfo()
                }
                Log.i(TAG, "SimInfoList = " + App.SimInfoList.toString())

                val simSlot = -1
                if (App.SimInfoList.isEmpty()) return simSlot
                for (simInfo in App.SimInfoList.values) {
                    if (simInfo.mSubscriptionId == mId && simInfo.mSimSlotIndex != -1) {
                        Log.i(TAG, "simInfo = $simInfo")
                        return simInfo.mSimSlotIndex
                    }
                }
                return simSlot
            }
        }

        //It's a cell phone number.(Easy judgment.)
        private fun isValidPhoneNumber(phoneNumber: String): Boolean {
            val regex = Regex("^\\+?\\d{3,20}$")
            return regex.matches(phoneNumber)
        }

    }

    init {
        throw UnsupportedOperationException("u can't instantiate me...")
    }
}
