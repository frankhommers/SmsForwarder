package cn.ppps.forwarder.entity.action

class WolSetting(
    var description: String = "",
    var mac: String = "",
    var ip: String = "",
    var port: String = "",
    var wakeMethod: Int = 0 // 0: Through local servicesAPI, 1: Send a Wake-on-LAN magic packet directly
)
