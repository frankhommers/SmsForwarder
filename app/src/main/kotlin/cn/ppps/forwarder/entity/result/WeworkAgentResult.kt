package cn.ppps.forwarder.entity.result

@Suppress("PropertyName")
data class WeworkAgentResult(
    var errcode: Long,
    var errmsg: String,
    //Accessaccess_tokenBack
    var access_token: String?,
    var expires_in: Long?,
    //Send interface back
    var msgid: String?,
)