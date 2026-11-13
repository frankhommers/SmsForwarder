package cn.ppps.forwarder.entity.action

import cn.ppps.forwarder.database.entity.Frpc
import java.io.Serializable

data class FrpcSetting(
    var description: String = "", //Description
    var action: String = "start", //Actions: start=Start, stop=Stop
    var frpcList: List<Frpc>, //FrpcList
) : Serializable
