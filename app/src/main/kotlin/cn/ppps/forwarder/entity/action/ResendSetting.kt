package cn.ppps.forwarder.entity.action

import java.io.Serializable

data class ResendSetting(
    var description: String = "", //Description
    var hours: Int = 1, //Automatically resendNforwarding records from the last specified hours，0=Unlimited
    var statusList: List<Int> = listOf(0), //Status list, default only failed to reissue
) : Serializable
