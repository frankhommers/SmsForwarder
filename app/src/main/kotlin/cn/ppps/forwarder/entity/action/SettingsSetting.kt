package cn.ppps.forwarder.entity.action

import cn.ppps.forwarder.utils.SettingUtils
import java.io.Serializable

data class SettingsSetting(
    var description: String = "", //Description
    var enableSms: Boolean = SettingUtils.enableSms, //Whether or not to forward text messages

    var enablePhone: Boolean = SettingUtils.enablePhone, //Whether or not to forward a call
    var enableCallType1: Boolean = SettingUtils.enableCallType1, //Forward incoming-call-ended events
    var enableCallType2: Boolean = SettingUtils.enableCallType2, //Forward outgoing-call-ended events
    var enableCallType3: Boolean = SettingUtils.enableCallType3, //Forward missed calls
    var enableCallType4: Boolean = SettingUtils.enableCallType4, //Forward incoming-call alerts
    var enableCallType5: Boolean = SettingUtils.enableCallType5, //Forward incoming-call-answered events
    var enableCallType6: Boolean = SettingUtils.enableCallType6, //Forward outgoing-call-dialed events

    var enableAppNotify: Boolean = SettingUtils.enableAppNotify, //Whether to forward application notifications
    var enableCancelAppNotify: Boolean = SettingUtils.enableCancelAppNotify, //Whether or not to forward application notifications - automatic elimination notifications
    var enableNotUserPresent: Boolean = SettingUtils.enableNotUserPresent, //Whether or not to forward application notifications - only lock screen status

    var enableLocation: Boolean = SettingUtils.enableLocation, //Whether or not to enable location
    var locationAccuracy: Int = SettingUtils.locationAccuracy, //Set Location Precision
    var locationPowerRequirement: Int = SettingUtils.locationPowerRequirement, //Set electricity consumption
    var locationMinInterval: Long = SettingUtils.locationMinInterval, //Set minimum update interval
    var locationMinDistance: Int = SettingUtils.locationMinDistance, //Set minimum update distance

    var enableSmsCommand: Boolean = SettingUtils.enableSmsCommand, //Accept SMS Commands
    var smsCommandSafePhone: String = SettingUtils.smsCommandSafePhone, //SMS security cell number.

    var enableLoadAppList: Boolean = SettingUtils.enableLoadAppList, //Whether to load the application list
    var enableLoadUserAppList: Boolean = SettingUtils.enableLoadUserAppList, //Whether to load the application list - User application
    var enableLoadSystemAppList: Boolean = SettingUtils.enableLoadSystemAppList, //Whether to load the application list - System application

    var cancelExtraAppNotify: String = SettingUtils.cancelExtraAppNotify, //Whether or not to forward application notifications - automatically eliminate additionalAPPNotifications

    var duplicateMessagesLimits: Int = SettingUtils.duplicateMessagesLimits, //Duplicate-message suppression interval
) : Serializable
