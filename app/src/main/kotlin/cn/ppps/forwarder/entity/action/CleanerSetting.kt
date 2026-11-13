package cn.ppps.forwarder.entity.action

import java.io.Serializable

data class CleanerSetting(
    var description: String = "", //Description
    var days: Int = 0, //Automatic DeleteNforwarding records older than the specified number of days
) : Serializable
