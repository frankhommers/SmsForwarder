package cn.ppps.forwarder.entity.condition

import java.io.Serializable

data class CronSetting(
    var description: String = "", //Description
    var expression: String = "", //Expression
) : Serializable
