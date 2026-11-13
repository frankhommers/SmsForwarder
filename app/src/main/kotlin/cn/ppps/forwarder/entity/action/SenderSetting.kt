package cn.ppps.forwarder.entity.action

import cn.ppps.forwarder.database.entity.Sender
import java.io.Serializable

data class SenderSetting(
    var description: String = "", //Description
    var status: Int = 1, //Status：0-Disable；1-Enable
    var senderList: List<Sender>, //Send Channel List
) : Serializable
