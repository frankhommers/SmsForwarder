package cn.ppps.forwarder.entity.action

import cn.ppps.forwarder.database.entity.Rule
import java.io.Serializable

data class RuleSetting(
    var description: String = "", //Description
    var status: Int = 1, //Status：0-Disable；1-Enable
    var ruleList: List<Rule>, //Forward Rule List
) : Serializable
