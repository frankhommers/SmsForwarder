package cn.ppps.forwarder.utils

import android.location.Criteria
import cn.ppps.forwarder.R
import com.xuexiang.xutil.resource.ResUtils.getString

class SettingUtils private constructor() {
    companion object {

        //Check for updates on startup
        var autoCheckUpdate: Boolean by SharedPreference(AUTO_CHECK_UPDATE, false)

        //Join or notSmsFPreview Experience
        var joinPreviewProgram: Boolean by SharedPreference(JOIN_PREVIEW_PROGRAM, false)

        //Consent to privacy policy
        var isAgreePrivacy: Boolean by SharedPreference(IS_AGREE_PRIVACY_KEY, false)

        //Whether or not to forward text messages
        var enableSms: Boolean by SharedPreference(SP_ENABLE_SMS, false)

        //Whether or not to forward a call
        var enablePhone: Boolean by SharedPreference(SP_ENABLE_PHONE, false)

        //Forward incoming-call-ended events
        var enableCallType1: Boolean by SharedPreference(SP_ENABLE_CALL_TYPE_1, false)

        //Forward outgoing-call-ended events
        var enableCallType2: Boolean by SharedPreference(SP_ENABLE_CALL_TYPE_2, false)

        //Forward missed calls
        var enableCallType3: Boolean by SharedPreference(SP_ENABLE_CALL_TYPE_3, false)

        //Forward incoming-call alerts
        var enableCallType4: Boolean by SharedPreference(SP_ENABLE_CALL_TYPE_4, false)

        //Forward incoming-call-answered events
        var enableCallType5: Boolean by SharedPreference(SP_ENABLE_CALL_TYPE_5, false)

        //Forward outgoing-call-dialed events
        var enableCallType6: Boolean by SharedPreference(SP_ENABLE_CALL_TYPE_6, false)

        //Whether to forward application notifications
        var enableAppNotify: Boolean by SharedPreference(SP_ENABLE_APP_NOTIFY, false)

        //Accept SMS Commands
        var enableSmsCommand: Boolean by SharedPreference(SP_ENABLE_SMS_COMMAND, false)
        var smsCommandSafePhone: String by SharedPreference(SP_SMS_COMMAND_SAFE_PHONE, "")

        //Turn off screen near earpiece
        var enableCloseToEarpieceTurnOffScreen: Boolean by SharedPreference(SP_ENABLE_CLOSE_TO_EARPIECE_TURN_OFF_SCREEN, false)

        //Whether or not to forward application notifications - automatic elimination notifications
        var enableCancelAppNotify: Boolean by SharedPreference(SP_ENABLE_CANCEL_APP_NOTIFY, false)

        //Whether or not to forward application notifications - automatically eliminate additionalAPPNotifications
        var cancelExtraAppNotify: String by SharedPreference(SP_CANCEL_EXTRA_APP_NOTIFY, "")

        //Whether to forward an application notice - a blacklist of keywords (one line, which supports the regular rule, the hit title or content is not forwarded)）
        var appNotifyBlacklist: String by SharedPreference(SP_APP_NOTIFY_BLACKLIST, "")

        //Whether or not to forward application notifications - only lock screen status
        var enableNotUserPresent: Boolean by SharedPreference(SP_ENABLE_NOT_USER_PRESENT, false)

        //Whether to load the application list
        var enableLoadAppList: Boolean by SharedPreference(ENABLE_LOAD_APP_LIST, false)

        //Whether to load the application list - User application
        var enableLoadUserAppList: Boolean by SharedPreference(ENABLE_LOAD_USER_APP_LIST, false)

        //Whether to load the application list - System application
        var enableLoadSystemAppList: Boolean by SharedPreference(ENABLE_LOAD_SYSTEM_APP_LIST, false)

        //Filter for how long to repeat messages
        var duplicateMessagesLimits: Int by SharedPreference(SP_DUPLICATE_MESSAGES_LIMITS, 0)

        //Do not disturb(Disable forwarding)Time period - beginning
        var silentPeriodStart: Int by SharedPreference(SP_SILENT_PERIOD_START, 0)

        //Do not disturb(Disable forwarding)Period - end
        var silentPeriodEnd: Int by SharedPreference(SP_SILENT_PERIOD_END, 0)

        //Do not disturb(Disable forwarding)Time periods - logs of records
        var enableSilentPeriodLogs: Boolean by SharedPreference(SP_ENABLE_SILENT_PERIOD_LOGS, false)

        //Whether to not show in the recent task list
        var enableExcludeFromRecents: Boolean by SharedPreference(SP_ENABLE_EXCLUDE_FROM_RECENTS, false)

        //EnableCactusEnhanced keep-alive mechanisms
        var enableCactus: Boolean by SharedPreference(SP_ENABLE_CACTUS, false)

        //Whether to play silent music
        var enablePlaySilenceMusic: Boolean by SharedPreference(SP_ENABLE_PLAY_SILENCE_MUSIC, false)

        //Enable1Pixels
        var enableOnePixelActivity: Boolean by SharedPreference(SP_ENABLE_ONE_PIXEL_ACTIVITY, false)

        //Silent music awakening interval (seconds, larger and less electricity)）
        var musicInterval: Int by SharedPreference(SP_MUSIC_INTERVAL, 10)

        //Number of requests for interface failed to retry
        var requestRetryTimes: Int by SharedPreference(SP_REQUEST_RETRY_TIMES, 0)

        //Request for failed retry interval (sec)）
        var requestDelayTime: Int by SharedPreference(SP_REQUEST_DELAY_TIME, 1)

        //Request failed interface timeout (sec)）
        var requestTimeout: Int by SharedPreference(SP_REQUEST_TIMEOUT, 10)

        //Content of notice
        var notifyContent: String by SharedPreference(SP_NOTIFY_CONTENT, getString(R.string.notification_content))

        //Device Name
        var extraDeviceMark: String by SharedPreference(SP_EXTRA_DEVICE_MARK, "")

        //SIM1Primary Key
        var subidSim1: Int by SharedPreference(SP_SUBID_SIM1, 0)

        //SIM2Primary Key
        var subidSim2: Int by SharedPreference(SP_SUBID_SIM2, 0)

        //SIM1Remarks
        var extraSim1: String by SharedPreference(SP_EXTRA_SIM1, "")

        //SIM2Remarks
        var extraSim2: String by SharedPreference(SP_EXTRA_SIM2, "")

        //Whether or not to enable custom templates
        var enableSmsTemplate: Boolean by SharedPreference(SP_ENABLE_SMS_TEMPLATE, false)

        //Custom Template
        var smsTemplate: String by SharedPreference(SP_SMS_TEMPLATE, "")

        //Whether it is a pure client mode
        var enablePureClientMode: Boolean by SharedPreference(SP_PURE_CLIENT_MODE, false)

        //Whether task mode is pure
        var enablePureTaskMode: Boolean by SharedPreference(SP_PURE_TASK_MODE, false)

        //Debug Mode
        var enableDebugMode: Boolean by SharedPreference(SP_DEBUG_MODE, false)

        //Whether or not to enable location
        var enableLocation: Boolean by SharedPreference(SP_LOCATION, false)

        //Set position accuracy: high precision
        var locationAccuracy: Int by SharedPreference(SP_LOCATION_ACCURACY, Criteria.ACCURACY_FINE)

        //Set electricity consumption: low electricity consumption
        var locationPowerRequirement: Int by SharedPreference(SP_LOCATION_POWER_REQUIREMENT, Criteria.POWER_LOW)

        //Set the minimum time interval (in milliseconds); default interval：10000milliseconds, minimum interval：1000milliseconds
        var locationMinInterval: Long by SharedPreference(SP_LOCATION_MIN_INTERVAL, 10000L)

        //Set position to update minimum distance (m); default distance：0meters
        var locationMinDistance: Int by SharedPreference(SP_LOCATION_MIN_DISTANCE, 0)

        //Follow system languages
        //var isFlowSystemLanguage: Boolean by SharedPreference(SP_IS_FLOW_SYSTEM_LANGUAGE, false)

        //Whether Bluetooth Discovery Service is enabled
        var enableBluetooth: Boolean by SharedPreference(SP_BLUETOOTH, false)

        //Scan Bluetooth device interval
        var bluetoothScanInterval: Long by SharedPreference(SP_BLUETOOTH_SCAN_INTERVAL, 10000L)

        //Whether to ignore anonymous devices
        var bluetoothIgnoreAnonymous: Boolean by SharedPreference(SP_BLUETOOTH_IGNORE_ANONYMOUS, true)
    }

    init {
        throw UnsupportedOperationException("u can't instantiate me...")
    }
}