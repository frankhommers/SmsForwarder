package cn.ppps.forwarder.entity.action

import java.io.Serializable

data class SmsSetting(
    var description: String = "", //Description
    var simSlot: Int = 1, //SIM slot
    var phoneNumbers: String = "", //Cell phone number
    var msgContent: String = "", //Text Message Contents
) : Serializable
